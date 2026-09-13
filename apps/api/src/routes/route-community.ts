import { createHash, randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';
import type { StoragePort } from '../storage/port.js';
import { StorageNotConfiguredError } from '../storage/port.js';

const routeIdParams = z.object({ id: z.string().uuid() });
const routeSlugParams = z.object({ slug: z.string().trim().min(1).max(180) });
const reviewIdParams = z.object({ id: z.string().uuid(), reviewId: z.string().uuid() });
const mediaParams = z.object({ id: z.string().uuid(), reviewId: z.string().uuid(), mediaId: z.string().uuid() });

const reviewSchema = z.object({
  rating: z.number().int().min(1).max(5),
  title: z.string().trim().max(180).nullable().optional(),
  body: z.string().trim().max(8000).nullable().optional(),
  visited_on: z.string().date().nullable().optional(),
  completed: z.boolean().default(true),
  difficulty_vote: z.enum(['easy','moderate','hard','very_hard']).nullable().optional(),
});

const conditionSchema = z.object({
  condition_kind: z.enum(['clear','muddy','wet','snow','ice','blocked','damaged','closed','fire_risk','flooded','other']),
  severity: z.enum(['info','caution','warning','critical']).default('info'),
  note: z.string().trim().max(4000).nullable().optional(),
  observed_at: z.string().datetime(),
  expires_at: z.string().datetime().nullable().optional(),
  latitude: z.number().min(-90).max(90).nullable().optional(),
  longitude: z.number().min(-180).max(180).nullable().optional(),
}).superRefine((value, ctx) => {
  const hasLat = value.latitude != null;
  const hasLon = value.longitude != null;
  if (hasLat !== hasLon) ctx.addIssue({ code: 'custom', message: 'latitude_and_longitude_required_together' });
  if (value.expires_at && new Date(value.expires_at) <= new Date(value.observed_at)) {
    ctx.addIssue({ code: 'custom', message: 'expires_at_must_follow_observed_at' });
  }
});

const reportSchema = z.object({
  target_type: z.enum(['review','review_media','condition_report']),
  target_id: z.string().uuid(),
  reason: z.enum(['spam','abuse','unsafe','privacy','copyright','false_information','other']),
  details: z.string().trim().max(3000).nullable().optional(),
});

const completionSchema = z.object({
  completed_at: z.string().datetime().default(() => new Date().toISOString()),
  source: z.enum(['manual','recorded_gpx','garmin','suunto','coros','apple_watch','other']).default('manual'),
  activity_external_id: z.string().trim().max(500).nullable().optional(),
});

const MAX_PHOTO_BYTES = 12 * 1024 * 1024;
const photoMimeSchema = z.enum(['image/jpeg','image/png','image/webp','image/avif']);
const sha256Schema = z.string().regex(/^[0-9a-fA-F]{64}$/).transform((value) => value.toLowerCase());
const reservePhotoSchema = z.object({
  original_filename: z.string().trim().min(1).max(255),
  mime_type: photoMimeSchema,
  byte_size: z.number().int().positive().max(MAX_PHOTO_BYTES),
  sha256: sha256Schema,
  caption: z.string().trim().max(1500).nullable().optional(),
  captured_at: z.string().datetime().nullable().optional(),
  latitude: z.number().min(-90).max(90).nullable().optional(),
  longitude: z.number().min(-180).max(180).nullable().optional(),
}).superRefine((value, ctx) => {
  if ((value.latitude != null) !== (value.longitude != null)) {
    ctx.addIssue({ code: 'custom', message: 'latitude_and_longitude_required_together' });
  }
});

const sponsorshipEventSchema = z.object({
  event_type: z.enum(['impression','click','website','call','whatsapp','directions','booking']),
  session_key: z.string().trim().max(200).nullable().optional(),
  metadata: z.record(z.string(), z.unknown()).default({}),
});

function requireUser(request: FastifyRequest, reply: any) {
  const userId = readAuthenticatedUserId(request);
  if (!userId) {
    reply.code(401).send({ error: 'authentication_required' });
    return null;
  }
  return userId;
}

async function routeExists(db: DatabaseClient, routeId: string, publishedOnly = false) {
  const result = await sql<{ id: string }>`
    SELECT id FROM routes
    WHERE id = ${routeId}::uuid ${publishedOnly ? sql`AND status = 'published' AND track_status = 'validated'` : sql``}
    LIMIT 1
  `.execute(db);
  return Boolean(result.rows[0]);
}

function publicMediaPath(assetId: string) {
  return `/api/v1/public/media/${assetId}`;
}

export function registerRouteCommunityRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  app.get('/api/v1/public/routes/:slug/community', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const params = routeSlugParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_slug' });

    const routeResult = await sql<{ id: string; name: string }>`
      SELECT id, name FROM routes
      WHERE slug = ${params.data.slug} AND status = 'published' AND track_status = 'validated'
      LIMIT 1
    `.execute(db);
    const route = routeResult.rows[0];
    if (!route) return reply.code(404).send({ error: 'route_not_found' });

    const [summary, reviews, conditions, photos, sponsorships] = await Promise.all([
      sql<Record<string, unknown>>`
        SELECT COUNT(*)::int AS review_count,
               COALESCE(ROUND(AVG(rating)::numeric, 2), 0) AS rating_average,
               COUNT(*) FILTER (WHERE completed)::int AS completed_reviews
        FROM route_reviews
        WHERE route_id = ${route.id}::uuid AND moderation_status = 'approved'
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT rr.id, rr.rating, rr.title, rr.body, rr.visited_on, rr.completed, rr.difficulty_vote,
               rr.helpful_count, rr.published_at, rr.created_at,
               u.display_name
        FROM route_reviews rr
        JOIN users u ON u.id = rr.user_id
        WHERE rr.route_id = ${route.id}::uuid AND rr.moderation_status = 'approved'
        ORDER BY rr.helpful_count DESC, COALESCE(rr.published_at, rr.created_at) DESC
        LIMIT 50
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, condition_kind, severity, note, observed_at, expires_at,
               CASE WHEN location IS NULL THEN NULL ELSE ST_Y(location) END AS latitude,
               CASE WHEN location IS NULL THEN NULL ELSE ST_X(location) END AS longitude
        FROM route_condition_reports
        WHERE route_id = ${route.id}::uuid AND moderation_status = 'approved'
          AND (expires_at IS NULL OR expires_at > now())
        ORDER BY observed_at DESC
        LIMIT 30
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT rrm.id, rrm.review_id, rrm.media_asset_id, rrm.caption, rrm.captured_at,
               CASE WHEN rrm.location IS NULL THEN NULL ELSE ST_Y(rrm.location) END AS latitude,
               CASE WHEN rrm.location IS NULL THEN NULL ELSE ST_X(rrm.location) END AS longitude,
               pma.mime_type
        FROM route_review_media rrm
        JOIN route_reviews rr ON rr.id = rrm.review_id
        JOIN platform_media_assets pma ON pma.id = rrm.media_asset_id AND pma.status = 'uploaded'
        WHERE rr.route_id = ${route.id}::uuid
          AND rr.moderation_status = 'approved' AND rrm.moderation_status = 'approved'
        ORDER BY COALESCE(rrm.captured_at, rrm.created_at) DESC
        LIMIT 80
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, sponsor_name, sponsor_logo_url, sponsor_url, headline, description,
               cta_label, cta_url, promo_code, placement, billing_model, disclosure
        FROM route_sponsorships
        WHERE (route_id = ${route.id}::uuid OR route_id IS NULL)
          AND status = 'active'
          AND (starts_at IS NULL OR starts_at <= now())
          AND (ends_at IS NULL OR ends_at > now())
        ORDER BY priority DESC, created_at DESC
        LIMIT 8
      `.execute(db),
    ]);

    return {
      route,
      summary: summary.rows[0] ?? { review_count: 0, rating_average: 0, completed_reviews: 0 },
      reviews: reviews.rows,
      conditions: conditions.rows,
      photos: photos.rows.map((photo) => ({ ...photo, url: publicMediaPath(String(photo.media_asset_id)) })),
      sponsorships: sponsorships.rows,
      notices: {
        community_conditions: 'Los avisos de la comunidad no sustituyen cierres ni restricciones oficiales.',
        sponsored_content: 'Los espacios comerciales se muestran siempre identificados como patrocinados.',
      },
    };
  });

  app.put('/api/v1/routes/:id/review', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(reviewSchema, request.body, reply); if (!input) return;
    if (!await routeExists(db, params.data.id, true)) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_reviews (
        route_id, user_id, rating, title, body, visited_on, completed, difficulty_vote,
        moderation_status, moderation_note, moderated_by, moderated_at, published_at
      ) VALUES (
        ${params.data.id}::uuid, ${userId}::uuid, ${input.rating}, ${input.title ?? null}, ${input.body ?? null},
        ${input.visited_on ?? null}::date, ${input.completed}, ${input.difficulty_vote ?? null},
        'pending', NULL, NULL, NULL, NULL
      )
      ON CONFLICT (route_id, user_id) DO UPDATE SET
        rating = EXCLUDED.rating,
        title = EXCLUDED.title,
        body = EXCLUDED.body,
        visited_on = EXCLUDED.visited_on,
        completed = EXCLUDED.completed,
        difficulty_vote = EXCLUDED.difficulty_vote,
        moderation_status = 'pending', moderation_note = NULL, moderated_by = NULL, moderated_at = NULL,
        published_at = NULL, updated_at = now()
      RETURNING id, route_id, rating, title, body, visited_on, completed, difficulty_vote, moderation_status, created_at, updated_at
    `.execute(db);
    return { review: result.rows[0], moderation: 'pending' };
  });

  app.post('/api/v1/routes/:id/conditions', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(conditionSchema, request.body, reply); if (!input) return;
    if (!await routeExists(db, params.data.id, true)) return reply.code(404).send({ error: 'route_not_found' });

    const location = input.latitude != null && input.longitude != null
      ? sql`ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326)`
      : sql`NULL`;
    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_condition_reports (
        route_id, user_id, condition_kind, severity, note, observed_at, location, expires_at
      ) VALUES (
        ${params.data.id}::uuid, ${userId}::uuid, ${input.condition_kind}, ${input.severity}, ${input.note ?? null},
        ${input.observed_at}::timestamptz, ${location}, ${input.expires_at ?? null}::timestamptz
      ) RETURNING id, route_id, condition_kind, severity, note, observed_at, expires_at, moderation_status, created_at
    `.execute(db);
    return reply.code(201).send({ condition: result.rows[0], moderation: 'pending' });
  });

  app.post('/api/v1/routes/community/report', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const input = parseBody(reportSchema, request.body, reply); if (!input) return;
    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_community_reports (reporter_user_id, target_type, target_id, reason, details)
      VALUES (${userId}::uuid, ${input.target_type}, ${input.target_id}::uuid, ${input.reason}, ${input.details ?? null})
      ON CONFLICT (reporter_user_id, target_type, target_id)
      DO UPDATE SET reason = EXCLUDED.reason, details = EXCLUDED.details, status = 'open', resolved_by = NULL, resolved_at = NULL
      RETURNING id, target_type, target_id, reason, status, created_at
    `.execute(db);
    return reply.code(201).send({ report: result.rows[0] });
  });

  app.put('/api/v1/routes/:id/favorite', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    if (!await routeExists(db, params.data.id, true)) return reply.code(404).send({ error: 'route_not_found' });
    await sql`INSERT INTO route_favorites(route_id, user_id) VALUES (${params.data.id}::uuid, ${userId}::uuid) ON CONFLICT DO NOTHING`.execute(db);
    return { favorite: true };
  });

  app.delete('/api/v1/routes/:id/favorite', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    await sql`DELETE FROM route_favorites WHERE route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid`.execute(db);
    return reply.code(204).send();
  });

  app.post('/api/v1/routes/:id/completions', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(completionSchema, request.body, reply); if (!input) return;
    if (!await routeExists(db, params.data.id, true)) return reply.code(404).send({ error: 'route_not_found' });
    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_completions(route_id, user_id, completed_at, source, activity_external_id)
      VALUES (${params.data.id}::uuid, ${userId}::uuid, ${input.completed_at}::timestamptz, ${input.source}, ${input.activity_external_id ?? null})
      RETURNING id, route_id, completed_at, source, activity_external_id
    `.execute(db);
    return reply.code(201).send({ completion: result.rows[0] });
  });

  app.post('/api/v1/routes/:id/reviews/:reviewId/photos/reserve', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = reviewIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_review_id' });
    const input = parseBody(reservePhotoSchema, request.body, reply); if (!input) return;

    const ownedReview = await sql<{ id: string }>`
      SELECT id FROM route_reviews WHERE id = ${params.data.reviewId}::uuid AND route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
    `.execute(db);
    if (!ownedReview.rows[0]) return reply.code(404).send({ error: 'review_not_found' });

    const assetId = randomUUID();
    const versionId = randomUUID();
    let upload;
    try {
      upload = await storage.reserveUpload({
        workspaceId: 'route-community', documentId: assetId, versionId,
        originalFilename: input.original_filename, mimeType: input.mime_type,
        byteSize: input.byte_size, sha256: input.sha256,
      });
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }

    await sql`
      INSERT INTO platform_media_assets(id, storage_key, original_filename, mime_type, byte_size, sha256, status, created_by)
      VALUES (${assetId}::uuid, ${upload.storageKey}, ${input.original_filename}, ${input.mime_type}, ${input.byte_size}, ${input.sha256}, 'reserved', ${userId}::uuid)
    `.execute(db);

    const location = input.latitude != null && input.longitude != null
      ? sql`ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326)` : sql`NULL`;
    const mediaResult = await sql<Record<string, unknown>>`
      INSERT INTO route_review_media(review_id, media_asset_id, user_id, kind, caption, captured_at, location)
      VALUES (${params.data.reviewId}::uuid, ${assetId}::uuid, ${userId}::uuid, 'photo', ${input.caption ?? null}, ${input.captured_at ?? null}::timestamptz, ${location})
      RETURNING id, review_id, media_asset_id, caption, captured_at, moderation_status, created_at
    `.execute(db);

    return reply.code(201).send({ media: mediaResult.rows[0], upload });
  });

  app.post('/api/v1/routes/:id/reviews/:reviewId/photos/:mediaId/complete', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = mediaParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_media_id' });
    const mediaResult = await sql<{ media_asset_id: string; storage_key: string; byte_size: string | number; mime_type: string; sha256: string; status: string }>`
      SELECT rrm.media_asset_id, pma.storage_key, pma.byte_size, pma.mime_type, pma.sha256, pma.status
      FROM route_review_media rrm
      JOIN route_reviews rr ON rr.id = rrm.review_id
      JOIN platform_media_assets pma ON pma.id = rrm.media_asset_id
      WHERE rrm.id = ${params.data.mediaId}::uuid AND rrm.review_id = ${params.data.reviewId}::uuid
        AND rr.route_id = ${params.data.id}::uuid AND rrm.user_id = ${userId}::uuid
      LIMIT 1
    `.execute(db);
    const media = mediaResult.rows[0];
    if (!media) return reply.code(404).send({ error: 'media_not_found' });
    if (media.status === 'uploaded') return { replayed: true, media: { id: params.data.mediaId, url: publicMediaPath(media.media_asset_id) } };

    let object;
    try { object = await storage.headObject(media.storage_key); }
    catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }
    if (!object.exists) return reply.code(409).send({ error: 'upload_not_found' });
    if (object.byteSize != null && Number(object.byteSize) !== Number(media.byte_size)) return reply.code(422).send({ error: 'upload_size_mismatch' });
    if (object.mimeType && object.mimeType !== media.mime_type) return reply.code(422).send({ error: 'upload_mime_mismatch' });
    const expectedChecksum = Buffer.from(media.sha256, 'hex').toString('base64');
    if (object.checksumSha256 && object.checksumSha256 !== expectedChecksum) return reply.code(422).send({ error: 'upload_checksum_mismatch' });

    await sql`
      UPDATE platform_media_assets
      SET status = 'uploaded', uploaded_at = now(), storage_etag = ${object.etag ?? null}, storage_checksum_sha256 = ${object.checksumSha256 ?? null}
      WHERE id = ${media.media_asset_id}::uuid
    `.execute(db);
    return { replayed: false, media: { id: params.data.mediaId, url: publicMediaPath(media.media_asset_id), moderation: 'pending' } };
  });

  app.post('/api/v1/public/route-sponsorships/:id/events', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_sponsorship_id' });
    const input = parseBody(sponsorshipEventSchema, request.body, reply); if (!input) return;
    const sponsor = await sql<{ id: string; route_id: string | null }>`
      SELECT id, route_id FROM route_sponsorships
      WHERE id = ${params.data.id}::uuid AND status = 'active'
        AND (starts_at IS NULL OR starts_at <= now()) AND (ends_at IS NULL OR ends_at > now())
      LIMIT 1
    `.execute(db);
    if (!sponsor.rows[0]) return reply.code(404).send({ error: 'sponsorship_not_found' });
    const userId = readAuthenticatedUserId(request);
    const sessionKey = input.session_key ? createHash('sha256').update(input.session_key).digest('hex') : null;
    await sql`
      INSERT INTO route_sponsorship_events(sponsorship_id, route_id, user_id, event_type, session_key, metadata)
      VALUES (${params.data.id}::uuid, ${sponsor.rows[0].route_id}::uuid, ${userId}::uuid, ${input.event_type}, ${sessionKey}, ${JSON.stringify(input.metadata)}::jsonb)
    `.execute(db);
    return reply.code(202).send({ accepted: true });
  });
}