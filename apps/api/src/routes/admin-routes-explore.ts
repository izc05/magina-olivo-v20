import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';
import { parseGpxTrack } from './gpx.js';

const routeType = z.enum(['hiking', 'mtb', 'cycling', 'trail', 'family', 'mixed']);
const difficulty = z.enum(['easy', 'moderate', 'hard', 'very_hard']);
const routeStatus = z.enum(['draft', 'review', 'published', 'archived']);
const validationStatus = z.enum(['unverified', 'editorial', 'official']);
const shadeLevel = z.enum(['none', 'low', 'medium', 'high']);
const mobileCoverage = z.enum(['unknown', 'none', 'partial', 'good']);

const createRouteSchema = z.object({
  slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/).max(120),
  name: z.string().trim().min(2).max(180),
  municipality_id: z.string().uuid().nullable().optional(),
  place_id: z.string().uuid().nullable().optional(),
  route_type: routeType.default('hiking'),
  tags: z.array(z.string().trim().min(1).max(50)).max(30).default([]),
  short_description: z.string().trim().max(500).nullable().optional(),
  description: z.string().trim().max(20_000).nullable().optional(),
  difficulty: difficulty.nullable().optional(),
  circular: z.boolean().default(false),
  family_friendly: z.boolean().default(false),
  access_notes: z.string().trim().max(4000).nullable().optional(),
  safety_notes: z.string().trim().max(4000).nullable().optional(),
  water_notes: z.string().trim().max(4000).nullable().optional(),
  shade_level: shadeLevel.nullable().optional(),
  mobile_coverage: mobileCoverage.nullable().optional(),
  recommended_seasons: z.array(z.string().trim().min(1).max(40)).max(12).default([]),
  restrictions: z.string().trim().max(4000).nullable().optional(),
  source_summary: z.string().trim().max(4000).nullable().optional(),
});

const updateRouteSchema = createRouteSchema.partial().extend({
  status: routeStatus.optional(),
  validation_status: validationStatus.optional(),
  duration_minutes: z.number().int().min(0).max(20_000).nullable().optional(),
  last_verified_at: z.string().datetime().nullable().optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'at_least_one_change_required' });

const gpxUploadSchema = z.object({
  gpx: z.string().min(1),
  source_name: z.string().trim().min(1).max(200).nullable().optional(),
  source_url: z.string().url().max(2000).nullable().optional(),
});

const validateTrackSchema = z.object({
  track_id: z.string().uuid(),
  validation_notes: z.string().trim().max(4000).nullable().optional(),
  validation_status: z.enum(['validated', 'rejected']),
});

type RouteRow = {
  id: string;
  slug: string;
  name: string;
  municipality_id: string | null;
  place_id: string | null;
  route_type: 'hiking' | 'mtb' | 'cycling' | 'trail' | 'family' | 'mixed';
  tags: string[];
  short_description: string | null;
  description: string | null;
  distance_m: number | null;
  duration_minutes: number | null;
  elevation_gain_m: number | null;
  elevation_loss_m: number | null;
  min_altitude_m: number | null;
  max_altitude_m: number | null;
  difficulty: 'easy' | 'moderate' | 'hard' | 'very_hard' | null;
  circular: boolean;
  family_friendly: boolean;
  status: 'draft' | 'review' | 'published' | 'archived';
  validation_status: 'unverified' | 'editorial' | 'official';
  track_status: 'missing' | 'uploaded' | 'validated' | 'rejected';
  access_notes: string | null;
  safety_notes: string | null;
  water_notes: string | null;
  shade_level: 'none' | 'low' | 'medium' | 'high' | null;
  mobile_coverage: 'unknown' | 'none' | 'partial' | 'good' | null;
  recommended_seasons: string[];
  restrictions: string | null;
  source_summary: string | null;
  last_verified_at: Date | null;
  published_at: Date | null;
};

async function getRoute(database: DatabaseClient, id: string) {
  const result = await sql<RouteRow>`SELECT * FROM routes WHERE id = ${id}::uuid LIMIT 1`.execute(database);
  return result.rows[0] ?? null;
}

function profileSamples(points: ReturnType<typeof parseGpxTrack>['points']) {
  const maxSamples = 2000;
  if (points.length <= maxSamples) return points;
  const stride = Math.ceil((points.length - 1) / (maxSamples - 1));
  const sampled = points.filter((_, index) => index % stride === 0);
  const last = points.at(-1)!;
  if (sampled.at(-1) !== last) sampled.push(last);
  return sampled;
}

export function registerAdminRoutesExploreRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/routes', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const result = await sql<RouteRow>`
      SELECT * FROM routes
      ORDER BY updated_at DESC, name
      LIMIT 500
    `.execute(auth.database);
    return { routes: result.rows };
  });

  app.get('/api/v1/admin/routes/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const route = await getRoute(auth.database, params.data.id);
    if (!route) return reply.code(404).send({ error: 'route_not_found' });
    const tracks = await sql<Record<string, unknown>>`
      SELECT id, version, original_format, checksum, bbox, distance_m, source_name, source_url,
             validation_status, validation_notes, validated_at, created_at,
             ST_AsGeoJSON(geometry)::json AS geometry
      FROM route_tracks
      WHERE route_id = ${route.id}::uuid
      ORDER BY version DESC
    `.execute(auth.database);
    return { route, tracks: tracks.rows };
  });

  app.post('/api/v1/admin/routes', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const input = parseBody(createRouteSchema, request.body, reply);
    if (!input) return;

    const inserted = await sql<RouteRow>`
      INSERT INTO routes (
        slug, name, municipality_id, place_id, route_type, tags, short_description, description,
        difficulty, circular, family_friendly, access_notes, safety_notes, water_notes,
        shade_level, mobile_coverage, recommended_seasons, restrictions, source_summary,
        created_by, updated_by
      ) VALUES (
        ${input.slug}, ${input.name}, ${input.municipality_id ?? null}::uuid, ${input.place_id ?? null}::uuid,
        ${input.route_type}, ${input.tags}, ${input.short_description ?? null}, ${input.description ?? null},
        ${input.difficulty ?? null}, ${input.circular}, ${input.family_friendly}, ${input.access_notes ?? null},
        ${input.safety_notes ?? null}, ${input.water_notes ?? null}, ${input.shade_level ?? null},
        ${input.mobile_coverage ?? null}, ${input.recommended_seasons}, ${input.restrictions ?? null},
        ${input.source_summary ?? null}, ${auth.access.userId}::uuid, ${auth.access.userId}::uuid
      ) RETURNING *
    `.execute(auth.database);
    const route = inserted.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.created', 'route', route.id, { slug: route.slug, name: route.name });
    return reply.code(201).send({ route });
  });

  app.patch('/api/v1/admin/routes/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(updateRouteSchema, request.body, reply);
    if (!input) return;
    const existing = await getRoute(auth.database, params.data.id);
    if (!existing) return reply.code(404).send({ error: 'route_not_found' });

    const next = { ...existing, ...input };
    if (next.status === 'published' && existing.track_status !== 'validated') {
      return reply.code(409).send({ error: 'validated_track_required_for_publish' });
    }

    const result = await sql<RouteRow>`
      UPDATE routes SET
        slug = ${next.slug}, name = ${next.name}, municipality_id = ${next.municipality_id}::uuid,
        place_id = ${next.place_id}::uuid, route_type = ${next.route_type}, tags = ${next.tags},
        short_description = ${next.short_description}, description = ${next.description},
        duration_minutes = ${next.duration_minutes}, difficulty = ${next.difficulty}, circular = ${next.circular},
        family_friendly = ${next.family_friendly}, status = ${next.status}, validation_status = ${next.validation_status},
        access_notes = ${next.access_notes}, safety_notes = ${next.safety_notes}, water_notes = ${next.water_notes},
        shade_level = ${next.shade_level}, mobile_coverage = ${next.mobile_coverage},
        recommended_seasons = ${next.recommended_seasons}, restrictions = ${next.restrictions},
        source_summary = ${next.source_summary}, last_verified_at = ${next.last_verified_at},
        published_at = CASE
          WHEN ${next.status} = 'published' AND published_at IS NULL THEN now()
          WHEN ${next.status} <> 'published' THEN NULL
          ELSE published_at
        END,
        updated_by = ${auth.access.userId}::uuid, updated_at = now()
      WHERE id = ${existing.id}::uuid
      RETURNING *
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'route.updated', 'route', existing.id, { changes: Object.keys(input) });
    return { route: result.rows[0] };
  });

  app.post('/api/v1/admin/routes/:id/gpx', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(gpxUploadSchema, request.body, reply);
    if (!input) return;
    const route = await getRoute(auth.database, params.data.id);
    if (!route) return reply.code(404).send({ error: 'route_not_found' });

    let parsed;
    try {
      parsed = parseGpxTrack(input.gpx);
    } catch (error) {
      const code = error instanceof Error ? error.message : 'gpx_invalid';
      return reply.code(400).send({ error: code });
    }

    const samples = profileSamples(parsed.points);
    const track = await auth.database.transaction().execute(async (trx) => {
      const versionResult = await sql<{ version: number }>`
        SELECT coalesce(max(version), 0)::int + 1 AS version FROM route_tracks WHERE route_id = ${route.id}::uuid
      `.execute(trx);
      const version = versionResult.rows[0]?.version ?? 1;
      const inserted = await sql<{ id: string; version: number }>`
        INSERT INTO route_tracks (
          route_id, version, geometry, geometry_type, original_format, checksum, bbox,
          distance_m, source_name, source_url, validation_status
        ) VALUES (
          ${route.id}::uuid, ${version},
          ST_SetSRID(ST_GeomFromGeoJSON(${JSON.stringify(parsed.geojson)}), 4326),
          'LineString', 'gpx', ${parsed.checksum}, ${JSON.stringify(parsed.bbox)}::jsonb,
          ${parsed.distanceM}, ${input.source_name ?? parsed.name}, ${input.source_url ?? null}, 'uploaded'
        ) RETURNING id, version
      `.execute(trx);
      const trackRow = inserted.rows[0];

      for (let index = 0; index < samples.length; index += 1) {
        const point = samples[index];
        if (point.elevationM === null) continue;
        const previous = samples[index - 1];
        const distanceDelta = previous ? point.distanceM - previous.distanceM : 0;
        const elevationDelta = previous?.elevationM === null || previous?.elevationM === undefined
          ? null
          : point.elevationM - previous.elevationM;
        const grade = elevationDelta === null || distanceDelta <= 0 ? null : (elevationDelta / distanceDelta) * 100;
        await sql`
          INSERT INTO route_elevation_samples (
            route_track_id, sample_order, distance_m, elevation_m, location, grade_percent
          ) VALUES (
            ${trackRow.id}::uuid, ${index}, ${point.distanceM}, ${point.elevationM},
            ST_SetSRID(ST_MakePoint(${point.longitude}, ${point.latitude}), 4326), ${grade}
          )
        `.execute(trx);
      }

      await sql`
        UPDATE routes SET
          track_status = 'uploaded', distance_m = ${parsed.distanceM},
          elevation_gain_m = ${parsed.elevationGainM}, elevation_loss_m = ${parsed.elevationLossM},
          min_altitude_m = ${parsed.minAltitudeM}, max_altitude_m = ${parsed.maxAltitudeM},
          updated_by = ${auth.access.userId}::uuid, updated_at = now()
        WHERE id = ${route.id}::uuid
      `.execute(trx);
      return trackRow;
    });

    await auditAdminAction(auth.database, auth.access, 'route.gpx_uploaded', 'route', route.id, {
      track_id: track.id,
      version: track.version,
      checksum: parsed.checksum,
      point_count: parsed.points.length,
      profile_sample_count: samples.filter((point) => point.elevationM !== null).length,
      distance_m: parsed.distanceM,
    });

    return reply.code(201).send({
      track,
      metrics: {
        point_count: parsed.points.length,
        distance_m: parsed.distanceM,
        elevation_gain_m: parsed.elevationGainM,
        elevation_loss_m: parsed.elevationLossM,
        min_altitude_m: parsed.minAltitudeM,
        max_altitude_m: parsed.maxAltitudeM,
        bbox: parsed.bbox,
      },
    });
  });

  app.post('/api/v1/admin/routes/:id/track-validation', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(validateTrackSchema, request.body, reply);
    if (!input) return;
    const route = await getRoute(auth.database, params.data.id);
    if (!route) return reply.code(404).send({ error: 'route_not_found' });

    const targetResult = await sql<{ id: string; validation_status: string }>`
      SELECT id, validation_status FROM route_tracks
      WHERE id = ${input.track_id}::uuid AND route_id = ${route.id}::uuid
      LIMIT 1
    `.execute(auth.database);
    if (!targetResult.rows[0]) return reply.code(404).send({ error: 'route_track_not_found' });

    await auth.database.transaction().execute(async (trx) => {
      if (input.validation_status === 'validated') {
        await sql`
          UPDATE route_tracks
          SET validation_status = 'uploaded', validation_notes = NULL, validated_by = NULL, validated_at = NULL
          WHERE route_id = ${route.id}::uuid AND validation_status = 'validated' AND id <> ${input.track_id}::uuid
        `.execute(trx);
      }
      await sql`
        UPDATE route_tracks
        SET validation_status = ${input.validation_status}, validation_notes = ${input.validation_notes ?? null},
            validated_by = ${auth.access.userId}::uuid, validated_at = now()
        WHERE id = ${input.track_id}::uuid AND route_id = ${route.id}::uuid
      `.execute(trx);
      await sql`
        UPDATE routes
        SET track_status = ${input.validation_status === 'validated' ? 'validated' : 'rejected'},
            status = CASE WHEN ${input.validation_status} = 'rejected' AND status = 'published' THEN 'review' ELSE status END,
            published_at = CASE WHEN ${input.validation_status} = 'rejected' THEN NULL ELSE published_at END,
            updated_by = ${auth.access.userId}::uuid, updated_at = now()
        WHERE id = ${route.id}::uuid
      `.execute(trx);
    });

    await auditAdminAction(auth.database, auth.access, 'route.track_validation_changed', 'route', route.id, {
      track_id: input.track_id,
      validation_status: input.validation_status,
      notes: input.validation_notes ?? null,
    });
    return { route: await getRoute(auth.database, route.id) };
  });
}
