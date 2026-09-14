import type { FastifyInstance, FastifyRequest } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';

const routeParams = z.object({ id: z.string().uuid() });
const activityParams = z.object({ id: z.string().uuid() });
const historyQuery = z.object({ limit: z.coerce.number().int().min(1).max(100).default(20) });
const pointSchema = z.object({
  sequence: z.number().int().min(1),
  recorded_at: z.string().datetime({ offset: true }),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  altitude_m: z.number().min(-1000).max(10000).nullable().optional(),
  horizontal_accuracy_m: z.number().min(0).max(5000).nullable().optional(),
  vertical_accuracy_m: z.number().min(0).max(5000).nullable().optional(),
});
const pointsSchema = z.object({ points: z.array(pointSchema).min(1).max(25) });

type ActivityRow = {
  id: string;
  user_id: string;
  route_id: string | null;
  route_slug: string | null;
  route_name: string | null;
  status: 'recording' | 'paused' | 'completed';
  visibility: 'private';
  current_segment: number;
  started_at: string;
  completed_at: string | null;
  distance_m: number | string;
  elevation_gain_m: number | string | null;
  duration_seconds: number;
  points_count: number;
  updated_at: string;
};

function requireUser(request: FastifyRequest, reply: any) {
  const userId = readAuthenticatedUserId(request);
  if (!userId) {
    reply.code(401).send({ error: 'authentication_required' });
    return null;
  }
  return userId;
}

function serializeActivity(row: ActivityRow) {
  return {
    ...row,
    current_segment: Number(row.current_segment),
    distance_m: Number(row.distance_m),
    elevation_gain_m: row.elevation_gain_m === null ? null : Number(row.elevation_gain_m),
    duration_seconds: Number(row.duration_seconds),
    points_count: Number(row.points_count),
  };
}

async function readActivity(db: DatabaseClient, activityId: string, userId: string) {
  const result = await sql<ActivityRow>`
    SELECT a.id, a.user_id, a.route_id, r.slug AS route_slug, r.name AS route_name,
           a.status, a.visibility, a.current_segment, a.started_at, a.completed_at,
           a.distance_m, a.elevation_gain_m, a.duration_seconds, a.points_count, a.updated_at
    FROM route_activity_recordings a
    LEFT JOIN routes r ON r.id = a.route_id
    WHERE a.id = ${activityId}::uuid AND a.user_id = ${userId}::uuid
    LIMIT 1
  `.execute(db);
  return result.rows[0] ?? null;
}

async function recalculateMetrics(db: DatabaseClient, activityId: string, userId: string) {
  const result = await sql<ActivityRow>`
    WITH ordered AS (
      SELECT p.segment, p.sequence, p.recorded_at, p.location, p.altitude_m,
             p.horizontal_accuracy_m, p.vertical_accuracy_m,
             lag(p.recorded_at) OVER (PARTITION BY p.segment ORDER BY p.sequence) AS previous_at,
             lag(p.location) OVER (PARTITION BY p.segment ORDER BY p.sequence) AS previous_location,
             lag(p.altitude_m) OVER (PARTITION BY p.segment ORDER BY p.sequence) AS previous_altitude_m,
             lag(p.horizontal_accuracy_m) OVER (PARTITION BY p.segment ORDER BY p.sequence) AS previous_horizontal_accuracy_m,
             lag(p.vertical_accuracy_m) OVER (PARTITION BY p.segment ORDER BY p.sequence) AS previous_vertical_accuracy_m
      FROM route_activity_points p
      WHERE p.recording_id = ${activityId}::uuid
    ), legs AS (
      SELECT *,
             EXTRACT(EPOCH FROM (recorded_at - previous_at)) AS delta_seconds,
             CASE WHEN previous_location IS NULL THEN NULL ELSE ST_DistanceSphere(location, previous_location) END AS leg_m,
             CASE WHEN altitude_m IS NULL OR previous_altitude_m IS NULL THEN NULL ELSE altitude_m - previous_altitude_m END AS elevation_delta_m
      FROM ordered
    ), metrics AS (
      SELECT
        COALESCE(SUM(
          CASE WHEN previous_at IS NOT NULL AND delta_seconds > 0 AND delta_seconds <= 120
               THEN delta_seconds ELSE 0 END
        ), 0)::int AS duration_seconds,
        COALESCE(SUM(
          CASE WHEN leg_m IS NOT NULL
                 AND delta_seconds > 0 AND delta_seconds <= 120
                 AND leg_m >= 2
                 AND leg_m / delta_seconds <= 15
                 AND (horizontal_accuracy_m IS NULL OR horizontal_accuracy_m <= 100)
                 AND (previous_horizontal_accuracy_m IS NULL OR previous_horizontal_accuracy_m <= 100)
               THEN leg_m ELSE 0 END
        ), 0) AS distance_m,
        SUM(
          CASE WHEN elevation_delta_m > 3 AND elevation_delta_m <= 50
                 AND vertical_accuracy_m IS NOT NULL AND vertical_accuracy_m <= 50
                 AND previous_vertical_accuracy_m IS NOT NULL AND previous_vertical_accuracy_m <= 50
               THEN elevation_delta_m ELSE NULL END
        ) AS elevation_gain_m,
        COUNT(*)::int AS points_count
      FROM legs
    ), updated AS (
      UPDATE route_activity_recordings a
      SET duration_seconds = metrics.duration_seconds,
          distance_m = ROUND(metrics.distance_m::numeric, 2),
          elevation_gain_m = CASE WHEN metrics.elevation_gain_m IS NULL THEN NULL ELSE ROUND(metrics.elevation_gain_m::numeric, 2) END,
          points_count = metrics.points_count,
          updated_at = now()
      FROM metrics
      WHERE a.id = ${activityId}::uuid AND a.user_id = ${userId}::uuid
      RETURNING a.*
    )
    SELECT u.id, u.user_id, u.route_id, r.slug AS route_slug, r.name AS route_name,
           u.status, u.visibility, u.current_segment, u.started_at, u.completed_at,
           u.distance_m, u.elevation_gain_m, u.duration_seconds, u.points_count, u.updated_at
    FROM updated u
    LEFT JOIN routes r ON r.id = u.route_id
  `.execute(db);
  return result.rows[0] ?? null;
}

function xml(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');
}

export function registerRouteActivityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/activities/active', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const result = await sql<ActivityRow>`
      SELECT a.id, a.user_id, a.route_id, r.slug AS route_slug, r.name AS route_name,
             a.status, a.visibility, a.current_segment, a.started_at, a.completed_at,
             a.distance_m, a.elevation_gain_m, a.duration_seconds, a.points_count, a.updated_at
      FROM route_activity_recordings a
      LEFT JOIN routes r ON r.id = a.route_id
      WHERE a.user_id = ${userId}::uuid AND a.status IN ('recording','paused')
      ORDER BY a.started_at DESC
      LIMIT 1
    `.execute(db);
    return { activity: result.rows[0] ? serializeActivity(result.rows[0]) : null };
  });

  app.post('/api/v1/routes/:id/activities/start', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });

    const routeResult = await sql<{ id: string }>`
      SELECT r.id
      FROM routes r
      WHERE r.id = ${params.data.id}::uuid
        AND r.status = 'published'
        AND r.track_status = 'validated'
        AND EXISTS (
          SELECT 1 FROM route_tracks rt
          WHERE rt.route_id = r.id AND rt.validation_status = 'validated'
        )
      LIMIT 1
    `.execute(db);
    if (!routeResult.rows[0]) return reply.code(404).send({ error: 'recordable_route_not_found' });

    const activeResult = await sql<ActivityRow>`
      SELECT a.id, a.user_id, a.route_id, r.slug AS route_slug, r.name AS route_name,
             a.status, a.visibility, a.current_segment, a.started_at, a.completed_at,
             a.distance_m, a.elevation_gain_m, a.duration_seconds, a.points_count, a.updated_at
      FROM route_activity_recordings a
      LEFT JOIN routes r ON r.id = a.route_id
      WHERE a.user_id = ${userId}::uuid AND a.status IN ('recording','paused')
      LIMIT 1
    `.execute(db);
    const active = activeResult.rows[0];
    if (active) {
      if (active.route_id === params.data.id) return { activity: serializeActivity(active), resumed_existing: true };
      return reply.code(409).send({ error: 'another_activity_active', activity: serializeActivity(active) });
    }

    const inserted = await sql<ActivityRow>`
      WITH created AS (
        INSERT INTO route_activity_recordings(user_id, route_id)
        VALUES (${userId}::uuid, ${params.data.id}::uuid)
        RETURNING *
      )
      SELECT c.id, c.user_id, c.route_id, r.slug AS route_slug, r.name AS route_name,
             c.status, c.visibility, c.current_segment, c.started_at, c.completed_at,
             c.distance_m, c.elevation_gain_m, c.duration_seconds, c.points_count, c.updated_at
      FROM created c
      LEFT JOIN routes r ON r.id = c.route_id
    `.execute(db);
    return reply.code(201).send({ activity: serializeActivity(inserted.rows[0]) });
  });

  app.post('/api/v1/activities/:id/points', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const input = parseBody(pointsSchema, request.body, reply); if (!input) return;

    const activityResult = await sql<{ id: string; status: string; current_segment: number }>`
      SELECT id, status, current_segment
      FROM route_activity_recordings
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
      LIMIT 1
    `.execute(db);
    const activity = activityResult.rows[0];
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });
    if (activity.status !== 'recording') return reply.code(409).send({ error: 'activity_not_recording' });

    let accepted = 0;
    for (const point of input.points) {
      const result = await sql<{ id: number }>`
        INSERT INTO route_activity_points(
          recording_id, segment, sequence, recorded_at, location, altitude_m,
          horizontal_accuracy_m, vertical_accuracy_m
        ) VALUES (
          ${activity.id}::uuid, ${activity.current_segment}, ${point.sequence}, ${point.recorded_at}::timestamptz,
          ST_SetSRID(ST_MakePoint(${point.longitude}, ${point.latitude}), 4326),
          ${point.altitude_m ?? null}, ${point.horizontal_accuracy_m ?? null}, ${point.vertical_accuracy_m ?? null}
        )
        ON CONFLICT (recording_id, sequence) DO NOTHING
        RETURNING id
      `.execute(db);
      if (result.rows[0]) accepted += 1;
    }

    await sql`
      UPDATE route_activity_recordings a
      SET points_count = (SELECT COUNT(*)::int FROM route_activity_points p WHERE p.recording_id = a.id),
          updated_at = now()
      WHERE a.id = ${activity.id}::uuid AND a.user_id = ${userId}::uuid
    `.execute(db);
    return { accepted, ignored_duplicates: input.points.length - accepted };
  });

  app.post('/api/v1/activities/:id/pause', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const updated = await sql<{ id: string }>`
      UPDATE route_activity_recordings
      SET status = 'paused', updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'recording'
      RETURNING id
    `.execute(db);
    if (!updated.rows[0]) return reply.code(409).send({ error: 'activity_not_recording' });
    const activity = await readActivity(db, params.data.id, userId);
    return { activity: activity ? serializeActivity(activity) : null };
  });

  app.post('/api/v1/activities/:id/resume', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const updated = await sql<{ id: string }>`
      UPDATE route_activity_recordings
      SET status = 'recording', current_segment = current_segment + 1, updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'paused'
      RETURNING id
    `.execute(db);
    if (!updated.rows[0]) return reply.code(409).send({ error: 'activity_not_paused' });
    const activity = await readActivity(db, params.data.id, userId);
    return { activity: activity ? serializeActivity(activity) : null };
  });

  app.post('/api/v1/activities/:id/finish', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const current = await readActivity(db, params.data.id, userId);
    if (!current) return reply.code(404).send({ error: 'activity_not_found' });
    if (current.status === 'completed') return { activity: serializeActivity(current), already_completed: true };

    await sql`
      UPDATE route_activity_recordings
      SET status = 'completed', completed_at = now(), updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
        AND status IN ('recording','paused')
    `.execute(db);
    const activity = await recalculateMetrics(db, params.data.id, userId);
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });
    return { activity: serializeActivity(activity) };
  });

  app.get('/api/v1/activities/me', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const query = historyQuery.safeParse(request.query);
    if (!query.success) return reply.code(400).send({ error: 'invalid_activity_query' });
    const result = await sql<ActivityRow>`
      SELECT a.id, a.user_id, a.route_id, r.slug AS route_slug, r.name AS route_name,
             a.status, a.visibility, a.current_segment, a.started_at, a.completed_at,
             a.distance_m, a.elevation_gain_m, a.duration_seconds, a.points_count, a.updated_at
      FROM route_activity_recordings a
      LEFT JOIN routes r ON r.id = a.route_id
      WHERE a.user_id = ${userId}::uuid
      ORDER BY a.started_at DESC
      LIMIT ${query.data.limit}
    `.execute(db);
    return { activities: result.rows.map(serializeActivity) };
  });

  app.get('/api/v1/activities/:id', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const activity = await readActivity(db, params.data.id, userId);
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });
    return { activity: serializeActivity(activity) };
  });

  app.delete('/api/v1/activities/:id', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const deleted = await sql<{ id: string }>`
      DELETE FROM route_activity_recordings
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
      RETURNING id
    `.execute(db);
    if (!deleted.rows[0]) return reply.code(404).send({ error: 'activity_not_found' });
    return reply.code(204).send();
  });

  app.get('/api/v1/activities/:id/gpx', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });
    const activity = await readActivity(db, params.data.id, userId);
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });

    const points = await sql<{
      segment: number;
      sequence: number;
      recorded_at: string;
      latitude: number | string;
      longitude: number | string;
      altitude_m: number | string | null;
    }>`
      SELECT segment, sequence, recorded_at,
             ST_Y(location) AS latitude, ST_X(location) AS longitude, altitude_m
      FROM route_activity_points
      WHERE recording_id = ${activity.id}::uuid
      ORDER BY segment, sequence
    `.execute(db);
    if (points.rows.length < 2) return reply.code(409).send({ error: 'activity_has_insufficient_points' });

    const segments = new Map<number, typeof points.rows>();
    for (const point of points.rows) {
      const list = segments.get(point.segment) ?? [];
      list.push(point);
      segments.set(point.segment, list);
    }
    const trkseg = [...segments.values()].map((list) =>
      `<trkseg>${list.map((point) => `<trkpt lat="${Number(point.latitude).toFixed(7)}" lon="${Number(point.longitude).toFixed(7)}">${point.altitude_m === null ? '' : `<ele>${Number(point.altitude_m).toFixed(1)}</ele>`}<time>${new Date(point.recorded_at).toISOString()}</time></trkpt>`).join('')}</trkseg>`
    ).join('');
    const name = activity.route_name ?? 'Recorrido Mágina Olivo';
    const gpx = `<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Mágina Olivo V20" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>${xml(name)}</name></metadata><trk><name>${xml(name)}</name>${trkseg}</trk></gpx>`;
    return reply
      .header('content-type', 'application/gpx+xml; charset=utf-8')
      .header('content-disposition', `attachment; filename="magina-actividad-${activity.id}.gpx"`)
      .header('cache-control', 'private, no-store')
      .send(gpx);
  });
}
