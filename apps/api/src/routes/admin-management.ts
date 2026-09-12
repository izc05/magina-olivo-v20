import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const workspaceTypeSchema = z.enum(['family', 'professional', 'organization']);
const membershipRoleSchema = z.enum(['owner', 'admin', 'manager', 'member', 'worker', 'viewer']);
const membershipStatusSchema = z.enum(['invited', 'active', 'suspended', 'revoked']);
const fieldStatusSchema = z.enum(['active', 'archived']);
const waterRegimeSchema = z.enum(['secano', 'regadio', 'mixto']);

const workspaceUpdateSchema = z.object({
  name: z.string().trim().min(1).max(160).optional(),
  type: workspaceTypeSchema.optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

const membershipUpdateSchema = z.object({
  role: membershipRoleSchema,
  status: membershipStatusSchema,
});

const nullableText = (max: number) => z.string().trim().max(max).nullable().optional();
const fieldUpdateSchema = z.object({
  name: z.string().trim().min(1).max(160).optional(),
  description: nullableText(2000),
  tree_count: z.number().int().min(1).max(1_000_000).nullable().optional(),
  crop: z.string().trim().min(1).max(120).optional(),
  variety: nullableText(160),
  water_regime: waterRegimeSchema.nullable().optional(),
  planting_year: z.number().int().min(1800).max(2200).nullable().optional(),
  tenure_type: nullableText(120),
  status: fieldStatusSchema.optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

function changes<T extends Record<string, unknown>>(before: T, after: T) {
  const result: Record<string, { from: unknown; to: unknown }> = {};
  for (const [key, value] of Object.entries(after)) {
    if (value !== undefined && before[key] !== value) result[key] = { from: before[key], to: value };
  }
  return result;
}

export function registerAdminManagementRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/workspaces', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = z.object({ q: z.string().trim().max(120).optional() }).safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });

    let query = auth.database.selectFrom('workspaces')
      .select(['id', 'name', 'type', 'created_at', 'updated_at'])
      .orderBy('updated_at', 'desc')
      .limit(100);
    if (parsed.data.q) query = query.where('name', 'ilike', `%${parsed.data.q}%`);
    const workspaces = await query.execute();
    const ids = workspaces.map((workspace) => workspace.id);

    if (!ids.length) return { workspaces: [] };
    const [members, fields, plans] = await Promise.all([
      auth.database.selectFrom('workspace_memberships')
        .select([
          'workspace_id',
          sql<number>`count(*) filter (where status = 'active')::int`.as('active_members'),
          sql<number>`count(*) filter (where status = 'active' and role = 'owner')::int`.as('active_owners'),
        ])
        .where('workspace_id', 'in', ids)
        .groupBy('workspace_id')
        .execute(),
      auth.database.selectFrom('fields')
        .select([
          'workspace_id',
          sql<number>`count(*) filter (where status = 'active')::int`.as('active_fields'),
          sql<number>`count(*)::int`.as('fields_total'),
        ])
        .where('workspace_id', 'in', ids)
        .groupBy('workspace_id')
        .execute(),
      sql<{ workspace_id: string; plan_code: string; status: string }>`
        SELECT workspace_id, plan_code, status
        FROM workspace_plan_subscriptions
        WHERE workspace_id = ANY(${sql.val(ids)}::uuid[])
      `.execute(auth.database),
    ]);

    const memberMap = new Map(members.map((item) => [item.workspace_id, item]));
    const fieldMap = new Map(fields.map((item) => [item.workspace_id, item]));
    const planMap = new Map(plans.rows.map((item) => [item.workspace_id, item]));
    return {
      workspaces: workspaces.map((workspace) => ({
        ...workspace,
        active_members: memberMap.get(workspace.id)?.active_members ?? 0,
        active_owners: memberMap.get(workspace.id)?.active_owners ?? 0,
        active_fields: fieldMap.get(workspace.id)?.active_fields ?? 0,
        fields_total: fieldMap.get(workspace.id)?.fields_total ?? 0,
        plan: planMap.get(workspace.id) ?? null,
      })),
    };
  });

  app.patch('/api/v1/admin/workspaces/:workspaceId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ workspaceId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_workspace_id' });
    const input = parseBody(workspaceUpdateSchema, request.body, reply);
    if (!input) return;

    const existing = await auth.database.selectFrom('workspaces')
      .select(['id', 'name', 'type', 'created_at', 'updated_at'])
      .where('id', '=', params.data.workspaceId)
      .executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'workspace_not_found' });

    const patch = { ...input, updated_at: new Date() };
    const workspace = await auth.database.updateTable('workspaces')
      .set(patch)
      .where('id', '=', existing.id)
      .returning(['id', 'name', 'type', 'created_at', 'updated_at'])
      .executeTakeFirstOrThrow();
    await auditAdminAction(auth.database, auth.access, 'workspace.updated', 'workspace', existing.id, {
      changes: changes(existing as unknown as Record<string, unknown>, input as unknown as Record<string, unknown>),
    });
    return { workspace };
  });

  app.get('/api/v1/admin/workspaces/:workspaceId/members', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const params = z.object({ workspaceId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_workspace_id' });
    const workspace = await auth.database.selectFrom('workspaces').select(['id', 'name', 'type']).where('id', '=', params.data.workspaceId).executeTakeFirst();
    if (!workspace) return reply.code(404).send({ error: 'workspace_not_found' });

    const members = await auth.database.selectFrom('workspace_memberships as wm')
      .innerJoin('users as u', 'u.id', 'wm.user_id')
      .select([
        'wm.id', 'wm.workspace_id', 'wm.user_id', 'wm.role', 'wm.status', 'wm.created_at', 'wm.updated_at',
        'u.display_name', 'u.primary_email', 'u.status as user_status',
      ])
      .where('wm.workspace_id', '=', workspace.id)
      .orderBy(sql`case when wm.role = 'owner' then 0 when wm.role = 'admin' then 1 else 2 end`)
      .orderBy('u.display_name', 'asc')
      .execute();
    return { workspace, members };
  });

  app.put('/api/v1/admin/workspaces/:workspaceId/members/:userId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ workspaceId: z.string().uuid(), userId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_membership_target' });
    const input = parseBody(membershipUpdateSchema, request.body, reply);
    if (!input) return;

    const [workspace, user, existing] = await Promise.all([
      auth.database.selectFrom('workspaces').select(['id', 'name']).where('id', '=', params.data.workspaceId).executeTakeFirst(),
      auth.database.selectFrom('users').select(['id', 'status']).where('id', '=', params.data.userId).executeTakeFirst(),
      auth.database.selectFrom('workspace_memberships').selectAll()
        .where('workspace_id', '=', params.data.workspaceId)
        .where('user_id', '=', params.data.userId)
        .executeTakeFirst(),
    ]);
    if (!workspace) return reply.code(404).send({ error: 'workspace_not_found' });
    if (!user || user.status === 'deleted') return reply.code(404).send({ error: 'user_not_found' });

    const wouldRemoveActiveOwner = existing?.role === 'owner' && existing.status === 'active'
      && (input.role !== 'owner' || input.status !== 'active');
    if (wouldRemoveActiveOwner) {
      const owners = await auth.database.selectFrom('workspace_memberships')
        .select(sql<number>`count(*)::int`.as('count'))
        .where('workspace_id', '=', workspace.id)
        .where('role', '=', 'owner')
        .where('status', '=', 'active')
        .executeTakeFirstOrThrow();
      if (owners.count <= 1) return reply.code(409).send({ error: 'workspace_requires_active_owner' });
    }

    const now = new Date();
    const membership = await auth.database.insertInto('workspace_memberships').values({
      workspace_id: workspace.id,
      user_id: user.id,
      role: input.role,
      status: input.status,
      invited_by: auth.access.userId,
      updated_at: now,
    }).onConflict((oc) => oc.columns(['workspace_id', 'user_id']).doUpdateSet({
      role: input.role,
      status: input.status,
      invited_by: auth.access.userId,
      updated_at: now,
    })).returningAll().executeTakeFirstOrThrow();

    await auditAdminAction(auth.database, auth.access, existing ? 'workspace_member.updated' : 'workspace_member.added', 'workspace_membership', membership.id, {
      workspace_id: workspace.id,
      user_id: user.id,
      from: existing ? { role: existing.role, status: existing.status } : null,
      to: { role: input.role, status: input.status },
    });
    return { membership };
  });

  app.get('/api/v1/admin/fields', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = z.object({
      q: z.string().trim().max(120).optional(),
      workspace_id: z.string().uuid().optional(),
      status: fieldStatusSchema.optional(),
    }).safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });

    let query = auth.database.selectFrom('fields as f')
      .innerJoin('workspaces as w', 'w.id', 'f.workspace_id')
      .select([
        'f.id', 'f.workspace_id', 'w.name as workspace_name', 'f.name', 'f.description', 'f.municipality', 'f.province',
        'f.calculated_area_ha', 'f.geometry_source', 'f.geometry_status', 'f.tree_count', 'f.crop', 'f.variety',
        'f.water_regime', 'f.planting_year', 'f.tenure_type', 'f.status', 'f.created_at', 'f.updated_at',
      ])
      .orderBy('f.updated_at', 'desc')
      .limit(150);
    if (parsed.data.q) {
      const term = `%${parsed.data.q}%`;
      query = query.where((eb) => eb.or([eb('f.name', 'ilike', term), eb('w.name', 'ilike', term)]));
    }
    if (parsed.data.workspace_id) query = query.where('f.workspace_id', '=', parsed.data.workspace_id);
    if (parsed.data.status) query = query.where('f.status', '=', parsed.data.status);
    return { fields: await query.execute() };
  });

  app.patch('/api/v1/admin/fields/:fieldId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ fieldId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const input = parseBody(fieldUpdateSchema, request.body, reply);
    if (!input) return;

    const existing = await auth.database.selectFrom('fields')
      .select(['id', 'workspace_id', 'name', 'description', 'tree_count', 'crop', 'variety', 'water_regime', 'planting_year', 'tenure_type', 'status'])
      .where('id', '=', params.data.fieldId)
      .executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'field_not_found' });

    const field = await auth.database.updateTable('fields')
      .set({ ...input, updated_at: new Date() })
      .where('id', '=', existing.id)
      .returning(['id', 'workspace_id', 'name', 'description', 'municipality', 'province', 'calculated_area_ha', 'geometry_source', 'geometry_status', 'tree_count', 'crop', 'variety', 'water_regime', 'planting_year', 'tenure_type', 'status', 'created_at', 'updated_at'])
      .executeTakeFirstOrThrow();
    await auditAdminAction(auth.database, auth.access, 'field.updated', 'field', existing.id, {
      workspace_id: existing.workspace_id,
      changes: changes(existing as unknown as Record<string, unknown>, input as unknown as Record<string, unknown>),
    });
    return { field };
  });
}
