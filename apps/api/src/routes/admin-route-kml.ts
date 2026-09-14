import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';
import { parseKmlTrack } from './kml.js';

const routeParams = z.object({ id: z.string().uuid() });
const kmlUploadSchema = z.object({
  kml: z.string().min(1),
  source_name: z.string().trim().min(1).max(200).nullable().optional(),
  source_url: z.string().url().max(2000).nullable().optional(),
});

function profileSamples(points: ReturnType<typeof parseKmlTrack>['points']) {
  const maxSamples = 2000;
  if (points.length <= maxSamples) return points;
  const stride = Math.ceil((points.length - 1) / (maxSamples - 1));
  const sampled = points.filter((_, index) => index % stride === 0);
  const last = points.at(-1)!;
  if (sampled.at(-1) !== last) sampled.push(last);
  return sampled;
}

export function registerAdminRouteKmlRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/admin/routes/:id/kml', { bodyLimit: 5 * 1024 * 1024 + 256 * 1024 }, async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(kmlUploadSchema, request.body, reply);
    if (!input) return;

    const routeResult = await sql<{
      id: string;
      slug: string;
      name: string;
      status: 'draft' | 'review' | 'published' | 'archived';
      distance_m: number | null;
    }>`
      SELECT id, slug, name, status, distance_m
      FROM routes
      WHERE id = ${params.data.id}::uuid
      LIMIT 1
    `.execute(auth.database);
    const route = routeResult.rows[0];
    if (!route) return reply.code(404).send({ error: 'route_not_found' });

    let parsed: ReturnType<typeof parseKmlTrack>;
    try {
      parsed = parseKmlTrack(input.kml);
    } catch (error) {
      const code = error instanceof Error ? error.message : 'kml_invalid';
      return reply.code(400).send({ error: code });
    }

    const previousDistance = route.distance_m == null ? null : Number(route.distance_m);
    const distanceDeltaM = previousDistance == null ? null : parsed.distanceM - previousDistance;
    const distanceDeltaPercent = previousDistance && previousDistance > 0
      ? Math.round((Math.abs(distanceDeltaM ?? 0) / previousDistance) * 10_000) / 100
      : null;
    const samples = profileSamples(parsed.points);

    const track = await auth.database.transaction().execute(async (trx) => {
      const versionResult = await sql<{ version: number }>`
        SELECT coalesce(max(version), 0)::int + 1 AS version
        FROM route_tracks
        WHERE route_id = ${route.id}::uuid
      `.execute(trx);
      const version = versionResult.rows[0]?.version ?? 1;

      const inserted = await sql<{ id: string; version: number }>`
        INSERT INTO route_tracks (
          route_id, version, geometry, geometry_type, original_format, original_asset_url,
          checksum, bbox, distance_m, source_name, source_url, validation_status
        ) VALUES (
          ${route.id}::uuid,
          ${version},
          ST_SetSRID(ST_GeomFromGeoJSON(${JSON.stringify(parsed.geojson)}), 4326),
          'LineString',
          'kml',
          ${input.source_url ?? null},
          ${parsed.checksum},
          ${JSON.stringify(parsed.bbox)}::jsonb,
          ${parsed.distanceM},
          ${input.source_name ?? parsed.name},
          ${input.source_url ?? null},
          'uploaded'
        )
        RETURNING id, version
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
            ${trackRow.id}::uuid,
            ${index},
            ${point.distanceM},
            ${point.elevationM},
            ST_SetSRID(ST_MakePoint(${point.longitude}, ${point.latitude}), 4326),
            ${grade}
          )
        `.execute(trx);
      }

      await sql`
        UPDATE routes
        SET track_status = 'uploaded',
            status = CASE WHEN status = 'published' THEN 'review' ELSE status END,
            published_at = CASE WHEN status = 'published' THEN NULL ELSE published_at END,
            distance_m = ${parsed.distanceM},
            elevation_gain_m = ${parsed.elevationGainM},
            elevation_loss_m = ${parsed.elevationLossM},
            min_altitude_m = ${parsed.minAltitudeM},
            max_altitude_m = ${parsed.maxAltitudeM},
            updated_by = ${auth.access.userId}::uuid,
            updated_at = now()
        WHERE id = ${route.id}::uuid
      `.execute(trx);

      return trackRow;
    });

    await auditAdminAction(auth.database, auth.access, 'route.kml_uploaded', 'route', route.id, {
      track_id: track.id,
      version: track.version,
      checksum: parsed.checksum,
      point_count: parsed.points.length,
      profile_sample_count: samples.filter((point) => point.elevationM !== null).length,
      distance_m: parsed.distanceM,
      previous_distance_m: previousDistance,
      distance_delta_m: distanceDeltaM,
      distance_delta_percent: distanceDeltaPercent,
      previous_status: route.status,
      source_name: input.source_name ?? parsed.name,
      source_url: input.source_url ?? null,
    });

    return reply.code(201).send({
      route: { id: route.id, slug: route.slug, name: route.name },
      track,
      validation_status: 'uploaded',
      requires_editor_validation: true,
      route_moved_to_review: route.status === 'published',
      comparison: {
        previous_distance_m: previousDistance,
        imported_distance_m: parsed.distanceM,
        distance_delta_m: distanceDeltaM,
        distance_delta_percent: distanceDeltaPercent,
      },
      metrics: {
        point_count: parsed.points.length,
        distance_m: parsed.distanceM,
        elevation_gain_m: parsed.elevationGainM,
        elevation_loss_m: parsed.elevationLossM,
        min_altitude_m: parsed.minAltitudeM,
        max_altitude_m: parsed.maxAltitudeM,
        bbox: parsed.bbox,
      },
      notice: 'El KML se ha importado como track subido. Si la ruta estaba publicada vuelve a revisión. Debe validarse explícitamente antes de poder publicarse o activar Mágina Aventura.',
    });
  });
}
