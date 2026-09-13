import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const routeParams = z.object({ id: z.string().uuid() });
const childParams = z.object({ id: z.string().uuid(), childId: z.string().uuid() });

const pointSchema = z.object({
  name: z.string().trim().min(1).max(180),
  kind: z.enum(['start','finish','viewpoint','water','parking','recreation_area','heritage','cave','bridge','rest','photo_spot','warning','other']),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  distance_m: z.number().int().min(0).nullable().optional(),
  elevation_m: z.number().min(-500).max(9000).nullable().optional(),
  description: z.string().trim().max(4000).nullable().optional(),
  safety_note: z.string().trim().max(4000).nullable().optional(),
  sort_order: z.number().int().min(-10_000).max(10_000).default(0),
});

const mediaSchema = z.object({
  route_point_id: z.string().uuid().nullable().optional(),
  kind: z.enum(['photo','hero_image','real_video','drone_video','ai_image','ai_video','map_animation','elevation_animation','thumbnail']),
  origin: z.enum(['real','official','licensed','ai_generated']),
  url: z.string().url().max(3000),
  poster_url: z.string().url().max(3000).nullable().optional(),
  alt_text: z.string().trim().max(500).nullable().optional(),
  caption: z.string().trim().max(2000).nullable().optional(),
  credit: z.string().trim().max(500).nullable().optional(),
  source_url: z.string().url().max(3000).nullable().optional(),
  license_notes: z.string().trim().max(2000).nullable().optional(),
  ai_provider: z.string().trim().max(120).nullable().optional(),
  ai_model: z.string().trim().max(120).nullable().optional(),
  ai_disclosure: z.string().trim().max(1000).nullable().optional(),
  sort_order: z.number().int().min(-10_000).max(10_000).default(0),
}).superRefine((value, ctx) => {
  const aiKind = value.kind === 'ai_image' || value.kind === 'ai_video';
  const aiOrigin = value.origin === 'ai_generated';
  if (aiKind !== aiOrigin) ctx.addIssue({ code: 'custom', message: 'ai_media_requires_ai_generated_origin' });
  if (aiOrigin && !value.ai_disclosure?.trim()) ctx.addIssue({ code: 'custom', message: 'ai_disclosure_required' });
});

const sourceSchema = z.object({
  source_name: z.string().trim().min(1).max(250),
  external_id: z.string().trim().max(500).nullable().optional(),
  source_url: z.string().url().max(3000),
  source_kind: z.enum(['official','reference','track','media','editorial']).default('reference'),
  license_notes: z.string().trim().max(2000).nullable().optional(),
  retrieved_at: z.string().datetime().nullable().optional(),
  raw_metadata: z.record(z.string(), z.unknown()).default({}),
});

async function routeExists(database: DatabaseClient, id: string) {
  const result = await sql<{ id: string }>`SELECT id FROM routes WHERE id = ${id}::uuid LIMIT 1`.execute(database);
  return Boolean(result.rows[0]);
}

export function registerAdminRouteContentRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/admin/routes/:id/points', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(pointSchema, request.body, reply);
    if (!input) return;
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_points (
        route_id, name, kind, location, distance_m, elevation_m, description, safety_note, sort_order
      ) VALUES (
        ${params.data.id}::uuid, ${input.name}, ${input.kind},
        ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326),
        ${input.distance_m ?? null}, ${input.elevation_m ?? null}, ${input.description ?? null},
        ${input.safety_note ?? null}, ${input.sort_order}
      )
      RETURNING id, route_id, name, kind, distance_m, elevation_m, description, safety_note, sort_order,
                ST_Y(location) AS latitude, ST_X(location) AS longitude
    `.execute(auth.database);
    const point = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.point_created', 'route_point', String(point.id), { route_id: params.data.id, kind: input.kind });
    return reply.code(201).send({ point });
  });

  app.delete('/api/v1/admin/routes/:id/points/:childId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = childParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_point_id' });
    const result = await sql<{ id: string }>`DELETE FROM route_points WHERE id = ${params.data.childId}::uuid AND route_id = ${params.data.id}::uuid RETURNING id`.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'route_point_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.point_deleted', 'route_point', params.data.childId, { route_id: params.data.id });
    return reply.code(204).send();
  });

  app.post('/api/v1/admin/routes/:id/media', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(mediaSchema, request.body, reply);
    if (!input) return;
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });

    if (input.route_point_id) {
      const point = await sql<{ id: string }>`SELECT id FROM route_points WHERE id = ${input.route_point_id}::uuid AND route_id = ${params.data.id}::uuid`.execute(auth.database);
      if (!point.rows[0]) return reply.code(400).send({ error: 'route_point_not_in_route' });
    }
    const aiGenerated = input.origin === 'ai_generated';
    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_media (
        route_id, route_point_id, kind, origin, url, poster_url, alt_text, caption, credit,
        source_url, license_notes, ai_generated, ai_provider, ai_model, ai_disclosure, sort_order
      ) VALUES (
        ${params.data.id}::uuid, ${input.route_point_id ?? null}::uuid, ${input.kind}, ${input.origin},
        ${input.url}, ${input.poster_url ?? null}, ${input.alt_text ?? null}, ${input.caption ?? null},
        ${input.credit ?? null}, ${input.source_url ?? null}, ${input.license_notes ?? null}, ${aiGenerated},
        ${input.ai_provider ?? null}, ${input.ai_model ?? null}, ${input.ai_disclosure ?? null}, ${input.sort_order}
      ) RETURNING *
    `.execute(auth.database);
    const media = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.media_created', 'route_media', String(media.id), { route_id: params.data.id, origin: input.origin, kind: input.kind });
    return reply.code(201).send({ media });
  });

  app.delete('/api/v1/admin/routes/:id/media/:childId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = childParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_media_id' });
    const result = await sql<{ id: string }>`DELETE FROM route_media WHERE id = ${params.data.childId}::uuid AND route_id = ${params.data.id}::uuid RETURNING id`.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'route_media_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.media_deleted', 'route_media', params.data.childId, { route_id: params.data.id });
    return reply.code(204).send();
  });

  app.post('/api/v1/admin/routes/:id/sources', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(sourceSchema, request.body, reply);
    if (!input) return;
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_sources (
        route_id, source_name, external_id, source_url, source_kind, license_notes, retrieved_at, raw_metadata
      ) VALUES (
        ${params.data.id}::uuid, ${input.source_name}, ${input.external_id ?? null}, ${input.source_url},
        ${input.source_kind}, ${input.license_notes ?? null}, ${input.retrieved_at ?? null}, ${JSON.stringify(input.raw_metadata)}::jsonb
      ) RETURNING *
    `.execute(auth.database);
    const source = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.source_created', 'route_source', String(source.id), { route_id: params.data.id, source_kind: input.source_kind });
    return reply.code(201).send({ source });
  });

  app.delete('/api/v1/admin/routes/:id/sources/:childId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = childParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_source_id' });
    const result = await sql<{ id: string }>`DELETE FROM route_sources WHERE id = ${params.data.childId}::uuid AND route_id = ${params.data.id}::uuid RETURNING id`.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'route_source_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.source_deleted', 'route_source', params.data.childId, { route_id: params.data.id });
    return reply.code(204).send();
  });
}
