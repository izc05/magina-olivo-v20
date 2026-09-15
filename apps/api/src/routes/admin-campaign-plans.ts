import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const campaignStatusSchema = z.enum(['planned', 'active', 'closed']);
const planCodeSchema = z.enum(['free', 'pro', 'professional']);
const planStatusSchema = z.enum(['active', 'trialing', 'paused', 'cancelled']);
const interestStatusSchema = z.enum(['pending', 'contacted', 'converted', 'cancelled']);
const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

const campaignUpdateSchema = z.object({
  name: z.string().trim().min(1).max(160).optional(),
  start_date: dateSchema.optional(),
  end_date: dateSchema.nullable().optional(),
  status: campaignStatusSchema.optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

const planUpdateSchema = z.object({
  plan_code: planCodeSchema,
  status: planStatusSchema,
  current_period_end: z.string().datetime().nullable().optional(),
});

const interestUpdateSchema = z.object({ status: interestStatusSchema });

function day(value: Date | string | null | undefined) {
  if (!value) return null;
  if (value instanceof Date) return value.toISOString().slice(0, 10);
  return String(value).slice(0, 10);
}

function validRange(start: string, end: string | null) {
  return !end || end >= start;
}

export function registerAdminCampaignPlanRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/campaigns', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = z.object({
      q: z.string().trim().max(120).optional(),
      workspace_id: z.string().uuid().optional(),
      status: campaignStatusSchema.optional(),
    }).safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });

    let query = auth.database.selectFrom('campaigns as c')
      .innerJoin('workspaces as w', 'w.id', 'c.workspace_id')
      .select(['c.id', 'c.workspace_id', 'w.name as workspace_name', 'c.name', 'c.start_date', 'c.end_date', 'c.status', 'c.created_at'])
      .orderBy('c.start_date', 'desc')
      .limit(150);
    if (parsed.data.q) {
      const term = `%${parsed.data.q}%`;
      query = query.where((eb) => eb.or([eb('c.name', 'ilike', term), eb('w.name', 'ilike', term)]));
    }
    if (parsed.data.workspace_id) query = query.where('c.workspace_id', '=', parsed.data.workspace_id);
    if (parsed.data.status) query = query.where('c.status', '=', parsed.data.status);
    return { campaigns: await query.execute() };
  });

  app.patch('/api/v1/admin/campaigns/:campaignId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ campaignId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
    const input = parseBody(campaignUpdateSchema, request.body, reply);
    if (!input) return;

    const existing = await auth.database.selectFrom('campaigns').selectAll().where('id', '=', params.data.campaignId).executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'campaign_not_found' });
    const nextStart = input.start_date ?? day(existing.start_date)!;
    const nextEnd = input.end_date === undefined ? day(existing.end_date) : input.end_date;
    if (!validRange(nextStart, nextEnd)) return reply.code(400).send({ error: 'invalid_campaign_window' });

    const campaign = await auth.database.updateTable('campaigns').set({
      name: input.name ?? existing.name,
      start_date: input.start_date ?? existing.start_date,
      end_date: input.end_date === undefined ? existing.end_date : input.end_date,
      status: input.status ?? existing.status,
    }).where('id', '=', existing.id).returningAll().executeTakeFirstOrThrow();

    await auditAdminAction(auth.database, auth.access, 'campaign.updated', 'campaign', existing.id, {
      workspace_id: existing.workspace_id,
      from: { name: existing.name, start_date: day(existing.start_date), end_date: day(existing.end_date), status: existing.status },
      to: { name: campaign.name, start_date: day(campaign.start_date), end_date: day(campaign.end_date), status: campaign.status },
    });
    return { campaign };
  });

  app.get('/api/v1/admin/plans', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const subscriptions = await sql<{
      workspace_id: string;
      workspace_name: string;
      workspace_type: string;
      plan_code: 'free' | 'pro' | 'professional' | null;
      status: 'active' | 'trialing' | 'paused' | 'cancelled' | null;
      source: 'internal' | 'manual' | 'billing' | null;
      started_at: Date | string | null;
      current_period_end: Date | string | null;
      updated_at: Date | string | null;
    }>`
      SELECT w.id AS workspace_id, w.name AS workspace_name, w.type AS workspace_type,
             s.plan_code, s.status, s.source, s.started_at, s.current_period_end, s.updated_at
      FROM workspaces w
      LEFT JOIN workspace_plan_subscriptions s ON s.workspace_id = w.id
      ORDER BY COALESCE(s.updated_at, w.updated_at) DESC
      LIMIT 150
    `.execute(auth.database);

    const interests = await sql<{
      id: string;
      workspace_id: string;
      workspace_name: string;
      target_plan: 'pro' | 'professional';
      status: 'pending' | 'contacted' | 'converted' | 'cancelled';
      requested_by: string | null;
      requested_by_name: string | null;
      created_at: Date | string;
      updated_at: Date | string;
    }>`
      SELECT i.id::text, i.workspace_id::text, w.name AS workspace_name, i.target_plan, i.status,
             i.requested_by::text, u.display_name AS requested_by_name, i.created_at, i.updated_at
      FROM plan_interest_requests i
      JOIN workspaces w ON w.id = i.workspace_id
      LEFT JOIN users u ON u.id = i.requested_by
      ORDER BY i.updated_at DESC
      LIMIT 150
    `.execute(auth.database);

    return { subscriptions: subscriptions.rows, interests: interests.rows, billing_enabled: false };
  });

  app.put('/api/v1/admin/workspaces/:workspaceId/plan', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'super_admin');
    if (!auth) return;
    const params = z.object({ workspaceId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_workspace_id' });
    const input = parseBody(planUpdateSchema, request.body, reply);
    if (!input) return;

    const workspace = await auth.database.selectFrom('workspaces').select(['id', 'name']).where('id', '=', params.data.workspaceId).executeTakeFirst();
    if (!workspace) return reply.code(404).send({ error: 'workspace_not_found' });
    const previous = await sql<{ plan_code: string; status: string; source: string; current_period_end: Date | string | null }>`
      SELECT plan_code, status, source, current_period_end FROM workspace_plan_subscriptions WHERE workspace_id = ${workspace.id}::uuid
    `.execute(auth.database);
    const now = new Date();
    const currentPeriodEnd = input.current_period_end ? new Date(input.current_period_end) : null;
    const result = await sql<{
      workspace_id: string; plan_code: string; status: string; source: string; started_at: Date | string; current_period_end: Date | string | null; updated_at: Date | string;
    }>`
      INSERT INTO workspace_plan_subscriptions (workspace_id, plan_code, status, source, started_at, current_period_end, updated_at)
      VALUES (${workspace.id}::uuid, ${input.plan_code}, ${input.status}, 'manual', ${now}, ${currentPeriodEnd}, ${now})
      ON CONFLICT (workspace_id) DO UPDATE SET
        plan_code = EXCLUDED.plan_code,
        status = EXCLUDED.status,
        source = 'manual',
        current_period_end = EXCLUDED.current_period_end,
        updated_at = EXCLUDED.updated_at
      RETURNING workspace_id::text, plan_code, status, source, started_at, current_period_end, updated_at
    `.execute(auth.database);
    const subscription = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'workspace_plan.updated', 'workspace', workspace.id, {
      billing_enabled: false,
      from: previous.rows[0] ?? null,
      to: subscription,
    });
    return { subscription, billing_enabled: false, checkout_available: false };
  });

  app.patch('/api/v1/admin/plan-interests/:interestId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ interestId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_interest_id' });
    const input = parseBody(interestUpdateSchema, request.body, reply);
    if (!input) return;

    const existing = await sql<{ id: string; workspace_id: string; target_plan: string; status: string }>`
      SELECT id::text, workspace_id::text, target_plan, status FROM plan_interest_requests WHERE id = ${params.data.interestId}::uuid
    `.execute(auth.database);
    const current = existing.rows[0];
    if (!current) return reply.code(404).send({ error: 'plan_interest_not_found' });
    const updated = await sql<{ id: string; workspace_id: string; target_plan: string; status: string; updated_at: Date | string }>`
      UPDATE plan_interest_requests SET status = ${input.status}, updated_at = now()
      WHERE id = ${current.id}::uuid
      RETURNING id::text, workspace_id::text, target_plan, status, updated_at
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'plan_interest.updated', 'plan_interest', current.id, {
      workspace_id: current.workspace_id,
      target_plan: current.target_plan,
      from: current.status,
      to: input.status,
    });
    return { interest: updated.rows[0] };
  });
}
