import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const idParams = z.object({ id: z.string().uuid() });
const moderationParams = z.object({ kind: z.enum(['review','review_media','condition_report']), id: z.string().uuid() });
const moderationSchema = z.object({
  status: z.enum(['approved','rejected','hidden']),
  note: z.string().trim().max(3000).nullable().optional(),
});
const reportStatusSchema = z.object({ status: z.enum(['reviewing','resolved','dismissed']) });
const sponsorshipSchema = z.object({
  route_id: z.string().uuid().nullable().optional(),
  sponsor_name: z.string().trim().min(1).max(250),
  sponsor_logo_url: z.string().trim().max(3000).nullable().optional(),
  sponsor_url: z.string().url().max(3000).nullable().optional(),
  headline: z.string().trim().max(250).nullable().optional(),
  description: z.string().trim().max(3000).nullable().optional(),
  cta_label: z.string().trim().max(120).nullable().optional(),
  cta_url: z.string().url().max(3000).nullable().optional(),
  promo_code: z.string().trim().max(120).nullable().optional(),
  placement: z.enum(['route_hero','route_sidebar','after_map','nearby_services','route_download','collection']).default('route_sidebar'),
  billing_model: z.enum(['flat','cpm','cpc','affiliate']).default('flat'),
  price_cents: z.number().int().min(0).nullable().optional(),
  currency: z.string().length(3).transform((value) => value.toUpperCase()).default('EUR'),
  priority: z.number().int().min(-10000).max(10000).default(0),
  status: z.enum(['draft','scheduled','active','paused','ended']).default('draft'),
  starts_at: z.string().datetime().nullable().optional(),
  ends_at: z.string().datetime().nullable().optional(),
  disclosure: z.string().trim().min(1).max(120).default('Patrocinado'),
}).superRefine((value, ctx) => {
  if (value.starts_at && value.ends_at && new Date(value.ends_at) <= new Date(value.starts_at)) {
    ctx.addIssue({ code: 'custom', message: 'ends_at_must_follow_starts_at' });
  }
});

function targetTable(kind: z.infer<typeof moderationParams>['kind']) {
  if (kind === 'review') return 'route_reviews';
  if (kind === 'review_media') return 'route_review_media';
  return 'route_condition_reports';
}

export function registerAdminRouteCommunityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/routes/community/moderation', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const [reviews, media, conditions, reports] = await Promise.all([
      sql<Record<string, unknown>>`
        SELECT rr.id, rr.route_id, r.name AS route_name, rr.rating, rr.title, rr.body, rr.visited_on,
               rr.completed, rr.difficulty_vote, rr.moderation_status, rr.created_at, u.display_name
        FROM route_reviews rr JOIN routes r ON r.id = rr.route_id JOIN users u ON u.id = rr.user_id
        WHERE rr.moderation_status = 'pending' ORDER BY rr.created_at ASC LIMIT 200
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT rrm.id, rr.route_id, r.name AS route_name, rrm.review_id, rrm.media_asset_id,
               rrm.caption, rrm.captured_at, rrm.moderation_status, rrm.created_at, u.display_name,
               '/api/v1/public/media/' || rrm.media_asset_id::text AS url
        FROM route_review_media rrm
        JOIN route_reviews rr ON rr.id = rrm.review_id
        JOIN routes r ON r.id = rr.route_id
        JOIN users u ON u.id = rrm.user_id
        WHERE rrm.moderation_status = 'pending' ORDER BY rrm.created_at ASC LIMIT 200
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT rc.id, rc.route_id, r.name AS route_name, rc.condition_kind, rc.severity, rc.note,
               rc.observed_at, rc.expires_at, rc.moderation_status, rc.created_at, u.display_name
        FROM route_condition_reports rc JOIN routes r ON r.id = rc.route_id JOIN users u ON u.id = rc.user_id
        WHERE rc.moderation_status = 'pending' ORDER BY rc.created_at ASC LIMIT 200
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT id, reporter_user_id, target_type, target_id, reason, details, status, created_at
        FROM route_community_reports WHERE status IN ('open','reviewing') ORDER BY created_at ASC LIMIT 200
      `.execute(auth.database),
    ]);
    return { reviews: reviews.rows, media: media.rows, conditions: conditions.rows, reports: reports.rows };
  });

  app.patch('/api/v1/admin/routes/community/:kind/:id/moderate', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = moderationParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_moderation_target' });
    const input = parseBody(moderationSchema, request.body, reply); if (!input) return;
    const table = targetTable(params.data.kind);
    const publishedAt = params.data.kind === 'review' && input.status === 'approved' ? sql`now()` : sql`NULL`;
    const query = params.data.kind === 'review'
      ? sql<Record<string, unknown>>`UPDATE route_reviews SET moderation_status = ${input.status}, moderation_note = ${input.note ?? null}, moderated_by = ${auth.access.userId}::uuid, moderated_at = now(), published_at = ${publishedAt}, updated_at = now() WHERE id = ${params.data.id}::uuid RETURNING id, moderation_status`
      : params.data.kind === 'review_media'
        ? sql<Record<string, unknown>>`UPDATE route_review_media SET moderation_status = ${input.status}, moderation_note = ${input.note ?? null}, moderated_by = ${auth.access.userId}::uuid, moderated_at = now() WHERE id = ${params.data.id}::uuid RETURNING id, moderation_status`
        : sql<Record<string, unknown>>`UPDATE route_condition_reports SET moderation_status = ${input.status}, moderation_note = ${input.note ?? null}, moderated_by = ${auth.access.userId}::uuid, moderated_at = now() WHERE id = ${params.data.id}::uuid RETURNING id, moderation_status`;
    const result = await query.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'moderation_target_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.community_moderated', table, params.data.id, { status: input.status, kind: params.data.kind });
    return { item: result.rows[0] };
  });

  app.patch('/api/v1/admin/routes/community/reports/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const params = idParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_report_id' });
    const input = parseBody(reportStatusSchema, request.body, reply); if (!input) return;
    const result = await sql<Record<string, unknown>>`
      UPDATE route_community_reports SET status = ${input.status},
        resolved_by = CASE WHEN ${input.status} IN ('resolved','dismissed') THEN ${auth.access.userId}::uuid ELSE NULL END,
        resolved_at = CASE WHEN ${input.status} IN ('resolved','dismissed') THEN now() ELSE NULL END
      WHERE id = ${params.data.id}::uuid RETURNING id, status, resolved_at
    `.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'report_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.community_report_updated', 'route_community_report', params.data.id, { status: input.status });
    return { report: result.rows[0] };
  });

  app.get('/api/v1/admin/routes/sponsorships', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const result = await sql<Record<string, unknown>>`
      SELECT s.*, r.name AS route_name,
        COALESCE(a.impressions, 0)::int AS impressions,
        COALESCE(a.clicks, 0)::int AS clicks,
        COALESCE(a.actions, 0)::int AS actions
      FROM route_sponsorships s
      LEFT JOIN routes r ON r.id = s.route_id
      LEFT JOIN LATERAL (
        SELECT COUNT(*) FILTER (WHERE event_type = 'impression') AS impressions,
               COUNT(*) FILTER (WHERE event_type = 'click') AS clicks,
               COUNT(*) FILTER (WHERE event_type IN ('website','call','whatsapp','directions','booking','affiliate_conversion')) AS actions
        FROM route_sponsorship_events e WHERE e.sponsorship_id = s.id
      ) a ON true
      ORDER BY s.status, s.priority DESC, s.created_at DESC
    `.execute(auth.database);
    return { sponsorships: result.rows };
  });

  app.post('/api/v1/admin/routes/sponsorships', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'manager');
    if (!auth) return;
    const input = parseBody(sponsorshipSchema, request.body, reply); if (!input) return;
    if (input.route_id) {
      const route = await sql<{ id: string }>`SELECT id FROM routes WHERE id = ${input.route_id}::uuid`.execute(auth.database);
      if (!route.rows[0]) return reply.code(400).send({ error: 'route_not_found' });
    }
    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_sponsorships(
        route_id, sponsor_name, sponsor_logo_url, sponsor_url, headline, description, cta_label, cta_url,
        promo_code, placement, billing_model, price_cents, currency, priority, status, starts_at, ends_at,
        disclosure, created_by, updated_by
      ) VALUES (
        ${input.route_id ?? null}::uuid, ${input.sponsor_name}, ${input.sponsor_logo_url ?? null}, ${input.sponsor_url ?? null},
        ${input.headline ?? null}, ${input.description ?? null}, ${input.cta_label ?? null}, ${input.cta_url ?? null},
        ${input.promo_code ?? null}, ${input.placement}, ${input.billing_model}, ${input.price_cents ?? null}, ${input.currency},
        ${input.priority}, ${input.status}, ${input.starts_at ?? null}::timestamptz, ${input.ends_at ?? null}::timestamptz,
        ${input.disclosure}, ${auth.access.userId}::uuid, ${auth.access.userId}::uuid
      ) RETURNING *
    `.execute(auth.database);
    const sponsorship = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.sponsorship_created', 'route_sponsorship', String(sponsorship.id), { route_id: input.route_id ?? null, placement: input.placement, billing_model: input.billing_model });
    return reply.code(201).send({ sponsorship });
  });

  app.put('/api/v1/admin/routes/sponsorships/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'manager');
    if (!auth) return;
    const params = idParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_sponsorship_id' });
    const input = parseBody(sponsorshipSchema, request.body, reply); if (!input) return;
    const result = await sql<Record<string, unknown>>`
      UPDATE route_sponsorships SET
        route_id = ${input.route_id ?? null}::uuid, sponsor_name = ${input.sponsor_name}, sponsor_logo_url = ${input.sponsor_logo_url ?? null},
        sponsor_url = ${input.sponsor_url ?? null}, headline = ${input.headline ?? null}, description = ${input.description ?? null},
        cta_label = ${input.cta_label ?? null}, cta_url = ${input.cta_url ?? null}, promo_code = ${input.promo_code ?? null},
        placement = ${input.placement}, billing_model = ${input.billing_model}, price_cents = ${input.price_cents ?? null}, currency = ${input.currency},
        priority = ${input.priority}, status = ${input.status}, starts_at = ${input.starts_at ?? null}::timestamptz,
        ends_at = ${input.ends_at ?? null}::timestamptz, disclosure = ${input.disclosure}, updated_by = ${auth.access.userId}::uuid, updated_at = now()
      WHERE id = ${params.data.id}::uuid RETURNING *
    `.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'sponsorship_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.sponsorship_updated', 'route_sponsorship', params.data.id, { status: input.status, placement: input.placement });
    return { sponsorship: result.rows[0] };
  });

  app.get('/api/v1/admin/routes/sponsorships/:id/analytics', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const params = idParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_sponsorship_id' });
    const summary = await sql<Record<string, unknown>>`
      SELECT event_type, COUNT(*)::int AS count,
             COUNT(DISTINCT session_key)::int AS unique_sessions,
             MIN(occurred_at) AS first_event, MAX(occurred_at) AS last_event
      FROM route_sponsorship_events WHERE sponsorship_id = ${params.data.id}::uuid
      GROUP BY event_type ORDER BY event_type
    `.execute(auth.database);
    return { events: summary.rows };
  });
}