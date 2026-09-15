import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import type { CmsEntryStatus, CmsEntryType, PlatformAdminRole } from '../db/types.js';
import { parseBody, requireDatabase } from '../http/helpers.js';
import { auditAdminAction, requirePlatformAccess, resolvePlatformAccess, roleAtLeast } from '../admin/access.js';

const platformRoleSchema = z.enum(['super_admin', 'admin', 'editor', 'support']);
const userStatusSchema = z.enum(['active', 'suspended', 'deleted']);
const cmsTypeSchema = z.enum(['page', 'news', 'event', 'place', 'mill', 'directory', 'promotion', 'alert']);
const cmsStatusSchema = z.enum(['draft', 'published', 'archived']);
const slugSchema = z.string().min(1).max(120).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/);
const nullableUrl = z.string().url().max(2000).nullable().optional();
const nullableDateTime = z.string().datetime().nullable().optional();
const eventDateFieldsSchema = z.object({
  event_start: nullableDateTime,
  event_end: nullableDateTime,
}).passthrough();

type CmsDateValue = string | Date | null | undefined;

const contentCreateSchema = z.object({
  type: cmsTypeSchema,
  slug: slugSchema,
  title: z.string().trim().min(1).max(180),
  summary: z.string().trim().max(600).nullable().optional(),
  content_json: z.unknown().optional(),
  status: cmsStatusSchema.optional(),
  featured: z.boolean().optional(),
  starts_at: nullableDateTime,
  ends_at: nullableDateTime,
  media_url: nullableUrl,
  external_url: nullableUrl,
  sort_order: z.number().int().min(-10000).max(10000).optional(),
});

const contentUpdateSchema = contentCreateSchema.partial();
const siteSettingSchema = z.object({
  value_json: z.unknown(),
  description: z.string().trim().max(500).nullable().optional(),
  is_public: z.boolean().optional(),
});

function publicContentQuery(input: unknown) {
  return z.object({ type: cmsTypeSchema.optional(), featured: z.enum(['true', 'false']).optional() }).safeParse(input);
}

function dateValueMs(value: Exclude<CmsDateValue, null | undefined>) {
  return value instanceof Date ? value.getTime() : new Date(value).getTime();
}

function isOrderedDateRange(start: CmsDateValue, end: CmsDateValue) {
  if (!start || !end) return true;
  return dateValueMs(end) >= dateValueMs(start);
}

function contentTimingError(type: CmsEntryType, contentJson: unknown, startsAt: CmsDateValue, endsAt: CmsDateValue) {
  if (!isOrderedDateRange(startsAt, endsAt)) return 'invalid_publication_window';
  if (type !== 'event') return null;

  const eventDates = eventDateFieldsSchema.safeParse(contentJson ?? {});
  if (!eventDates.success) return 'invalid_event_dates';
  if (!isOrderedDateRange(eventDates.data.event_start, eventDates.data.event_end)) return 'invalid_event_window';
  return null;
}

async function adminOverview(db: DatabaseClient) {
  const [users, workspaces, fields, content, published] = await Promise.all([
    db.selectFrom('users').select(sql<number>`count(*)::int`.as('count')).where('status', '<>', 'deleted').executeTakeFirstOrThrow(),
    db.selectFrom('workspaces').select(sql<number>`count(*)::int`.as('count')).executeTakeFirstOrThrow(),
    db.selectFrom('fields').select(sql<number>`count(*)::int`.as('count')).where('status', '=', 'active').executeTakeFirstOrThrow(),
    db.selectFrom('cms_entries').select(sql<number>`count(*)::int`.as('count')).where('status', '<>', 'archived').executeTakeFirstOrThrow(),
    db.selectFrom('cms_entries').select(sql<number>`count(*)::int`.as('count')).where('status', '=', 'published').executeTakeFirstOrThrow(),
  ]);
  return {
    users: users.count,
    workspaces: workspaces.count,
    active_fields: fields.count,
    content_entries: content.count,
    published_entries: published.count,
  };
}

export function registerAdminRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/session', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const user = await auth.database.selectFrom('users')
      .select(['id', 'display_name', 'primary_email', 'avatar_url', 'status'])
      .where('id', '=', auth.access.userId)
      .executeTakeFirstOrThrow();
    return { user, platform_access: auth.access };
  });

  app.get('/api/v1/admin/overview', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const recentAudit = await auth.database.selectFrom('admin_audit_log')
      .select(['id', 'actor_user_id', 'actor_role', 'action', 'target_type', 'target_id', 'created_at'])
      .orderBy('created_at', 'desc')
      .limit(8)
      .execute();
    return { metrics: await adminOverview(auth.database), recent_audit: recentAudit };
  });

  app.get('/api/v1/admin/users', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = z.object({ q: z.string().trim().max(120).optional() }).safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });

    let query = auth.database.selectFrom('users')
      .select(['id', 'primary_email', 'display_name', 'avatar_url', 'status', 'created_at', 'last_login_at'])
      .where('status', '<>', 'deleted')
      .orderBy('created_at', 'desc')
      .limit(100);

    if (parsed.data.q) {
      const term = `%${parsed.data.q}%`;
      query = query.where((eb) => eb.or([
        eb('display_name', 'ilike', term),
        eb('primary_email', 'ilike', term),
      ]));
    }

    const users = await query.execute();
    const ids = users.map((user) => user.id);
    const [grants, memberships] = ids.length ? await Promise.all([
      auth.database.selectFrom('platform_admins')
        .select(['user_id', 'role', 'status'])
        .where('user_id', 'in', ids)
        .execute(),
      auth.database.selectFrom('workspace_memberships')
        .select(['user_id', sql<number>`count(*)::int`.as('count')])
        .where('user_id', 'in', ids)
        .where('status', '=', 'active')
        .groupBy('user_id')
        .execute(),
    ]) : [[], []];

    const grantByUser = new Map(grants.map((grant) => [grant.user_id, grant]));
    const membershipByUser = new Map(memberships.map((membership) => [membership.user_id, membership.count]));
    return {
      users: users.map((user) => ({
        ...user,
        workspace_count: membershipByUser.get(user.id) ?? 0,
        platform_access: grantByUser.get(user.id) ?? null,
      })),
    };
  });

  app.patch('/api/v1/admin/users/:userId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ userId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_user_id' });
    const input = parseBody(z.object({ status: userStatusSchema }), request.body, reply);
    if (!input) return;
    if (params.data.userId === auth.access.userId && input.status !== 'active') {
      return reply.code(409).send({ error: 'cannot_disable_current_admin' });
    }

    const existing = await auth.database.selectFrom('users').select(['id', 'status']).where('id', '=', params.data.userId).executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'user_not_found' });

    const targetAccess = await resolvePlatformAccess(auth.database, existing.id);
    if (targetAccess && !roleAtLeast(auth.access.role, targetAccess.role)) {
      return reply.code(403).send({ error: 'platform_admin_role_required', minimum_role: targetAccess.role });
    }

    await auth.database.updateTable('users').set({ status: input.status, updated_at: new Date() }).where('id', '=', existing.id).execute();
    await auditAdminAction(auth.database, auth.access, 'user.status_changed', 'user', existing.id, { from: existing.status, to: input.status });
    return { id: existing.id, status: input.status };
  });

  app.put('/api/v1/admin/platform-access/:userId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'super_admin');
    if (!auth) return;
    const params = z.object({ userId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_user_id' });
    const input = parseBody(z.object({ role: platformRoleSchema, status: z.enum(['active', 'revoked']).default('active') }), request.body, reply);
    if (!input) return;
    if (params.data.userId === auth.access.userId && input.status === 'revoked') {
      return reply.code(409).send({ error: 'cannot_revoke_current_admin' });
    }
    const target = await auth.database.selectFrom('users').select(['id']).where('id', '=', params.data.userId).executeTakeFirst();
    if (!target) return reply.code(404).send({ error: 'user_not_found' });

    const now = new Date();
    await auth.database.insertInto('platform_admins').values({
      user_id: target.id,
      role: input.role,
      status: input.status,
      granted_by: auth.access.userId,
      updated_at: now,
    }).onConflict((oc) => oc.column('user_id').doUpdateSet({
      role: input.role,
      status: input.status,
      granted_by: auth.access.userId,
      updated_at: now,
    })).execute();
    await auditAdminAction(auth.database, auth.access, 'platform_access.changed', 'user', target.id, input);
    return { user_id: target.id, ...input };
  });

  app.get('/api/v1/admin/content', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = z.object({ type: cmsTypeSchema.optional(), status: cmsStatusSchema.optional() }).safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });
    let query = auth.database.selectFrom('cms_entries').selectAll().orderBy('updated_at', 'desc').limit(250);
    if (parsed.data.type) query = query.where('type', '=', parsed.data.type);
    if (parsed.data.status) query = query.where('status', '=', parsed.data.status);
    return { entries: await query.execute() };
  });

  app.post('/api/v1/admin/content', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const input = parseBody(contentCreateSchema, request.body, reply);
    if (!input) return;
    const timingError = contentTimingError(input.type as CmsEntryType, input.content_json, input.starts_at, input.ends_at);
    if (timingError) return reply.code(400).send({ error: timingError });
    const now = new Date();
    try {
      const entry = await auth.database.insertInto('cms_entries').values({
        type: input.type,
        slug: input.slug,
        title: input.title,
        summary: input.summary ?? null,
        content_json: input.content_json ?? {},
        status: input.status ?? 'draft',
        featured: input.featured ?? false,
        starts_at: input.starts_at ? new Date(input.starts_at) : null,
        ends_at: input.ends_at ? new Date(input.ends_at) : null,
        media_url: input.media_url ?? null,
        external_url: input.external_url ?? null,
        sort_order: input.sort_order ?? 0,
        created_by: auth.access.userId,
        updated_by: auth.access.userId,
        updated_at: now,
        published_at: input.status === 'published' ? now : null,
      }).returningAll().executeTakeFirstOrThrow();
      await auditAdminAction(auth.database, auth.access, 'content.created', 'cms_entry', entry.id, { type: entry.type, slug: entry.slug, status: entry.status });
      return reply.code(201).send({ entry });
    } catch (error) {
      request.log.warn({ err: error }, 'Unable to create CMS entry');
      return reply.code(409).send({ error: 'content_slug_conflict' });
    }
  });

  app.put('/api/v1/admin/content/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_content_id' });
    const input = parseBody(contentUpdateSchema, request.body, reply);
    if (!input) return;
    const existing = await auth.database.selectFrom('cms_entries').selectAll().where('id', '=', params.data.id).executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'content_not_found' });

    const nextType = (input.type ?? existing.type) as CmsEntryType;
    const nextContentJson = input.content_json === undefined ? existing.content_json : input.content_json;
    const nextStartsAt = input.starts_at === undefined ? existing.starts_at : input.starts_at;
    const nextEndsAt = input.ends_at === undefined ? existing.ends_at : input.ends_at;
    const timingError = contentTimingError(nextType, nextContentJson, nextStartsAt, nextEndsAt);
    if (timingError) return reply.code(400).send({ error: timingError });

    const status = (input.status ?? existing.status) as CmsEntryStatus;
    const now = new Date();
    const entry = await auth.database.updateTable('cms_entries').set({
      type: nextType,
      slug: input.slug ?? existing.slug,
      title: input.title ?? existing.title,
      summary: input.summary === undefined ? existing.summary : input.summary,
      content_json: nextContentJson,
      status,
      featured: input.featured ?? existing.featured,
      starts_at: input.starts_at === undefined ? existing.starts_at : (input.starts_at ? new Date(input.starts_at) : null),
      ends_at: input.ends_at === undefined ? existing.ends_at : (input.ends_at ? new Date(input.ends_at) : null),
      media_url: input.media_url === undefined ? existing.media_url : input.media_url,
      external_url: input.external_url === undefined ? existing.external_url : input.external_url,
      sort_order: input.sort_order ?? existing.sort_order,
      updated_by: auth.access.userId,
      updated_at: now,
      published_at: status === 'published' ? (existing.published_at ?? now) : existing.published_at,
    }).where('id', '=', existing.id).returningAll().executeTakeFirstOrThrow();
    await auditAdminAction(auth.database, auth.access, 'content.updated', 'cms_entry', entry.id, { status: entry.status, type: entry.type, slug: entry.slug });
    return { entry };
  });

  app.delete('/api/v1/admin/content/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_content_id' });
    const existing = await auth.database.selectFrom('cms_entries').select(['id', 'status']).where('id', '=', params.data.id).executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'content_not_found' });
    await auth.database.updateTable('cms_entries').set({ status: 'archived', updated_by: auth.access.userId, updated_at: new Date() }).where('id', '=', existing.id).execute();
    await auditAdminAction(auth.database, auth.access, 'content.archived', 'cms_entry', existing.id, { from: existing.status });
    return reply.code(204).send();
  });

  app.get('/api/v1/admin/settings', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const result = await sql<{ key: string; value_json: unknown; description: string | null; is_public: boolean; updated_by: string; updated_at: Date }>`
      SELECT key, value_json, description, is_public, updated_by, updated_at
      FROM site_settings
      ORDER BY key ASC
    `.execute(auth.database);
    return { settings: result.rows };
  });

  app.put('/api/v1/admin/settings/:key', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ key: z.string().min(1).max(120).regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_setting_key' });
    const input = parseBody(siteSettingSchema, request.body, reply);
    if (!input) return;
    const isPublic = input.is_public ?? false;
    const description = input.description ?? null;
    const now = new Date();
    await sql`
      INSERT INTO site_settings (key, value_json, description, is_public, updated_by, updated_at)
      VALUES (${params.data.key}, ${JSON.stringify(input.value_json)}::jsonb, ${description}, ${isPublic}, ${auth.access.userId}::uuid, ${now})
      ON CONFLICT (key) DO UPDATE SET
        value_json = EXCLUDED.value_json,
        description = EXCLUDED.description,
        is_public = EXCLUDED.is_public,
        updated_by = EXCLUDED.updated_by,
        updated_at = EXCLUDED.updated_at
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'setting.updated', 'site_setting', params.data.key, { is_public: isPublic });
    return { key: params.data.key, value_json: input.value_json, description, is_public: isPublic, updated_at: now };
  });

  app.get('/api/v1/admin/audit', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const entries = await auth.database.selectFrom('admin_audit_log')
      .selectAll()
      .orderBy('created_at', 'desc')
      .limit(200)
      .execute();
    return { entries };
  });

  app.get('/api/v1/public/content', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const parsed = publicContentQuery(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error' });
    const now = new Date();
    let query = database.selectFrom('cms_entries')
      .select(['id', 'type', 'slug', 'title', 'summary', 'content_json', 'featured', 'starts_at', 'ends_at', 'media_url', 'external_url', 'sort_order', 'published_at'])
      .where('status', '=', 'published')
      .where((eb) => eb.or([eb('starts_at', 'is', null), eb('starts_at', '<=', now)]))
      .where((eb) => eb.or([eb('ends_at', 'is', null), eb('ends_at', '>=', now)]))
      .orderBy('featured', 'desc')
      .orderBy('sort_order', 'asc')
      .orderBy('published_at', 'desc')
      .limit(100);
    if (parsed.data.type) query = query.where('type', '=', parsed.data.type);
    if (parsed.data.featured) query = query.where('featured', '=', parsed.data.featured === 'true');
    return { entries: await query.execute() };
  });

  app.get('/api/v1/public/site-settings', async (_request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const result = await sql<{ key: string; value_json: unknown }>`
      SELECT key, value_json FROM site_settings WHERE is_public = true ORDER BY key ASC
    `.execute(database);
    return { settings: Object.fromEntries(result.rows.map((row) => [row.key, row.value_json])) };
  });
}

export async function platformAccessForSession(db: DatabaseClient, userId: string) {
  return resolvePlatformAccess(db, userId);
}
