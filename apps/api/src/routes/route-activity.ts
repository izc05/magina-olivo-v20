import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { readAuthenticatedUserId } from '../request-context.js';

const activityParams = z.object({ id: z.string().uuid() });
const startBody = z.object({ route_id: z.string().uuid().nullable().optional() });
const pointBody = z.object({
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  accuracy_m: z.number().min(0).max(200),
  altitude_m: z.number().min(-500).max(10000).nullable().optional(),
  recorded_at: z.string().datetime({ offset: true }),
});

type ActivityRow = {
  id: string;
  route_id: string | null;
  route_slug: string | null;
  route_name: string | null;
  status: 'active' | 'paused' | 'completed';
  distance_m: number | string;
  active_seconds: number;
  active_started_at: string | null;
  point_count: number;
  started_at: string;
  ended_at: string | null;
  updated_at: string;
};

function normalizeActivity(row: ActivityRow) {
  return {
    ...row,
    distance_m: Number(row.distance_m),
    active_seconds: Number(row.active_seconds),
    point_count: Number(row.point_count),
  };
}

async function ownedActivity(db: DatabaseClient, userId: string, id: string) {
  const result = await sql<ActivityRow>`
    SELECT s.id, s.route_id, r.slug AS route_slug, r.name AS route_name,
           s.status, s.distance_m, s.active_seconds, s.active_started_at,
           s.point_count, s.started_at, s.ended_at, s.updated_at
    FROM route_activity_sessions s
    LEFT JOIN routes r ON r.id = s.route_id
    WHERE s.id = ${id}::uuid AND s.user_id = ${userId}::uuid
    LIMIT 1
  `.execute(db);
  return result.rows[0] ?? null;
}

async function touchActivityHeartbeat(db: DatabaseClient, userId: string, id: string) {
  await sql`
    UPDATE route_activity_sessions
    SET updated_at = now()
    WHERE id = ${id}::uuid AND user_id = ${userId}::uuid AND status = 'active'
  `.execute(db);
}

async function autoPauseStaleActivity(db: DatabaseClient, userId: string) {
  await sql`
    UPDATE route_activity_sessions
    SET active_seconds = active_seconds + GREATEST(
          0,
          EXTRACT(EPOCH FROM (updated_at - active_started_at))::int
        ),
        active_started_at = NULL,
        status = 'paused',
        updated_at = now()
    WHERE user_id = ${userId}::uuid
      AND status = 'active'
      AND active_started_at IS NOT NULL
      AND updated_at < now() - interval '2 minutes'
  `.execute(db);
}

export function registerRouteActivityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/activities/current', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });

    await autoPauseStaleActivity(db, userId);
    const result = await sql<ActivityRow>`
      SELECT s.id, s.route_id, r.slug AS route_slug, r.name AS route_name,
             s.status, s.distance_m, s.active_seconds, s.active_started_at,
             s.point_count, s.started_at, s.ended_at, s.updated_at
      FROM route_activity_sessions s
      LEFT JOIN routes r ON r.id = s.route_id
      WHERE s.user_id = ${userId}::uuid AND s.status IN ('active','paused')
      ORDER BY s.started_at DESC
      LIMIT 1
    `.execute(db);

    return { activity: result.rows[0] ? normalizeActivity(result.rows[0]) : null };
  });

  app.get('/api/v1/activities/me', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });

    const [summaryResult, recentResult] = await Promise.all([
      sql<{
        completed_activities: number;
        recorded_distance_m: number | string;
        recorded_active_seconds: number | string;
        longest_activity_m: number | string;
      }>`
        SELECT COUNT(*)::int AS completed_activities,
               COALESCE(SUM(distance_m), 0) AS recorded_distance_m,
               COALESCE(SUM(active_seconds), 0) AS recorded_active_seconds,
               COALESCE(MAX(distance_m), 0) AS longest_activity_m
        FROM route_activity_sessions
        WHERE user_id = ${userId}::uuid AND status = 'completed'
      `.execute(db),
      sql<ActivityRow>`
        SELECT s.id, s.route_id, r.slug AS route_slug, r.name AS route_name,
               s.status, s.distance_m, s.active_seconds, s.active_started_at,
               s.point_count, s.started_at, s.ended_at, s.updated_at
        FROM route_activity_sessions s
        LEFT JOIN routes r ON r.id = s.route_id
        WHERE s.user_id = ${userId}::uuid AND s.status = 'completed'
        ORDER BY s.ended_at DESC NULLS LAST, s.started_at DESC
        LIMIT 12
      `.execute(db),
    ]);

    const summary = summaryResult.rows[0] ?? {
      completed_activities: 0,
      recorded_distance_m: 0,
      recorded_active_seconds: 0,
      longest_activity_m: 0,
    };

    return {
      summary: {
        completed_activities: Number(summary.completed_activities),
        recorded_distance_m: Number(summary.recorded_distance_m),
        recorded_active_seconds: Number(summary.recorded_active_seconds),
        longest_activity_m: Number(summary.longest_activity_m),
      },
      recent: recentResult.rows.map(normalizeActivity),
      privacy: 'Los tracks GPS son privados y solo existen cuando el usuario inicia expresamente una grabación. No se publican ni se usan para rankings de velocidad.',
    };
  });

  app.get('/api/v1/activities/:id/track', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const activity = await ownedActivity(db, userId, params.data.id);
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });

    const points = await sql<{
      recorded_at: string;
      latitude: number | string;
      longitude: number | string;
      accuracy_m: number | string;
      altitude_m: number | string | null;
      segment_distance_m: number | string;
    }>`
      SELECT recorded_at,
             ST_Y(location) AS latitude,
             ST_X(location) AS longitude,
             accuracy_m, altitude_m, segment_distance_m
      FROM route_activity_points
      WHERE session_id = ${params.data.id}::uuid
      ORDER BY recorded_at, id
    `.execute(db);

    return {
      activity: normalizeActivity(activity),
      points: points.rows.map((point) => ({
        ...point,
        latitude: Number(point.latitude),
        longitude: Number(point.longitude),
        accuracy_m: Number(point.accuracy_m),
        altitude_m: point.altitude_m == null ? null : Number(point.altitude_m),
        segment_distance_m: Number(point.segment_distance_m),
      })),
    };
  });

  app.post('/api/v1/activities/start', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const parsed = startBody.safeParse(request.body ?? {});
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_activity_payload' });

    const routeId = parsed.data.route_id ?? null;
    if (routeId) {
      const routeResult = await sql<{ id: string }>`
        SELECT id FROM routes
        WHERE id = ${routeId}::uuid AND status = 'published' AND track_status = 'validated'
        LIMIT 1
      `.execute(db);
      if (!routeResult.rows[0]) return reply.code(409).send({ error: 'route_not_recordable' });
    }

    const inserted = await sql<{ id: string }>`
      INSERT INTO route_activity_sessions(user_id, route_id, status, active_started_at)
      VALUES (${userId}::uuid, ${routeId}::uuid, 'active', now())
      ON CONFLICT (user_id) WHERE status IN ('active','paused') DO NOTHING
      RETURNING id
    `.execute(db);
    if (!inserted.rows[0]) {
      const existing = await sql<{ id: string }>`
        SELECT id FROM route_activity_sessions
        WHERE user_id = ${userId}::uuid AND status IN ('active','paused')
        ORDER BY started_at DESC
        LIMIT 1
      `.execute(db);
      return reply.code(409).send({ error: 'activity_already_open', activity_id: existing.rows[0]?.id ?? null });
    }
    const activity = await ownedActivity(db, userId, inserted.rows[0].id);
    return reply.code(201).send({ activity: normalizeActivity(activity!) });
  });

  app.post('/api/v1/activities/:id/points', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    const parsed = pointBody.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_activity_point' });

    const activity = await ownedActivity(db, userId, params.data.id);
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });
    if (activity.status !== 'active') return reply.code(409).send({ error: 'activity_not_active' });

    const recordedAt = new Date(parsed.data.recorded_at);
    const now = Date.now();
    if (recordedAt.getTime() < now - 10 * 60_000 || recordedAt.getTime() > now + 2 * 60_000) {
      return reply.code(400).send({ error: 'activity_point_time_out_of_range' });
    }
    if (parsed.data.accuracy_m > 80) {
      await touchActivityHeartbeat(db, userId, params.data.id);
      const current = await ownedActivity(db, userId, params.data.id);
      return { accepted: false, reason: 'low_accuracy', activity: normalizeActivity(current!) };
    }

    const previousResult = await sql<{
      recorded_at: string;
      latitude: number | string;
      longitude: number | string;
    }>`
      SELECT recorded_at, ST_Y(location) AS latitude, ST_X(location) AS longitude
      FROM route_activity_points
      WHERE session_id = ${params.data.id}::uuid
      ORDER BY recorded_at DESC, id DESC
      LIMIT 1
    `.execute(db);
    const previous = previousResult.rows[0] ?? null;

    let segmentDistance = 0;
    if (previous) {
      const previousAt = new Date(previous.recorded_at).getTime();
      const deltaSeconds = (recordedAt.getTime() - previousAt) / 1000;
      const resumedAfterPrevious = activity.active_started_at
        ? previousAt < new Date(activity.active_started_at).getTime()
        : false;
      if (!resumedAfterPrevious && deltaSeconds < 5) {
        await touchActivityHeartbeat(db, userId, params.data.id);
        const current = await ownedActivity(db, userId, params.data.id);
        return { accepted: false, reason: 'sample_too_soon', activity: normalizeActivity(current!) };
      }

      const distanceResult = await sql<{ distance_m: number | string }>`
        SELECT ST_DistanceSphere(
          ST_SetSRID(ST_MakePoint(${Number(previous.longitude)}, ${Number(previous.latitude)}), 4326),
          ST_SetSRID(ST_MakePoint(${parsed.data.longitude}, ${parsed.data.latitude}), 4326)
        ) AS distance_m
      `.execute(db);
      const measured = Number(distanceResult.rows[0]?.distance_m ?? 0);
      if (!resumedAfterPrevious && deltaSeconds <= 180) {
        if (measured < 3 || measured > 1000) {
          await touchActivityHeartbeat(db, userId, params.data.id);
          const current = await ownedActivity(db, userId, params.data.id);
          return {
            accepted: false,
            reason: measured < 3 ? 'stationary' : 'implausible_jump',
            activity: normalizeActivity(current!),
          };
        }
        segmentDistance = measured;
      }
    }

    await sql`
      INSERT INTO route_activity_points(
        session_id, recorded_at, location, accuracy_m, altitude_m, segment_distance_m
      ) VALUES (
        ${params.data.id}::uuid,
        ${parsed.data.recorded_at}::timestamptz,
        ST_SetSRID(ST_MakePoint(${parsed.data.longitude}, ${parsed.data.latitude}), 4326),
        ${parsed.data.accuracy_m},
        ${parsed.data.altitude_m ?? null},
        ${segmentDistance}
      )
    `.execute(db);
    await sql`
      UPDATE route_activity_sessions
      SET distance_m = distance_m + ${segmentDistance},
          point_count = point_count + 1,
          updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
    `.execute(db);

    const updated = await ownedActivity(db, userId, params.data.id);
    return { accepted: true, segment_distance_m: segmentDistance, activity: normalizeActivity(updated!) };
  });

  app.post('/api/v1/activities/:id/pause', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const result = await sql<{ id: string }>`
      UPDATE route_activity_sessions
      SET active_seconds = active_seconds + GREATEST(0, EXTRACT(EPOCH FROM (now() - active_started_at))::int),
          active_started_at = NULL,
          status = 'paused',
          updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'active'
      RETURNING id
    `.execute(db);
    if (!result.rows[0]) return reply.code(409).send({ error: 'activity_not_active' });
    const updated = await ownedActivity(db, userId, params.data.id);
    return { activity: normalizeActivity(updated!) };
  });

  app.post('/api/v1/activities/:id/resume', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const result = await sql<{ id: string }>`
      UPDATE route_activity_sessions
      SET active_started_at = now(), status = 'active', updated_at = now()
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'paused'
      RETURNING id
    `.execute(db);
    if (!result.rows[0]) return reply.code(409).send({ error: 'activity_not_paused' });
    const updated = await ownedActivity(db, userId, params.data.id);
    return { activity: normalizeActivity(updated!) };
  });

  app.post('/api/v1/activities/:id/finish', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const result = await sql<{ id: string }>`
      UPDATE route_activity_sessions
      SET active_seconds = active_seconds + CASE
            WHEN status = 'active' AND active_started_at IS NOT NULL
              THEN GREATEST(0, EXTRACT(EPOCH FROM (now() - active_started_at))::int)
            ELSE 0
          END,
          active_started_at = NULL,
          status = 'completed',
          ended_at = now(),
          updated_at = now()
      WHERE id = ${params.data.id}::uuid
        AND user_id = ${userId}::uuid
        AND status IN ('active','paused')
      RETURNING id
    `.execute(db);
    if (!result.rows[0]) return reply.code(409).send({ error: 'activity_not_open' });
    const updated = await ownedActivity(db, userId, params.data.id);
    return { activity: normalizeActivity(updated!) };
  });

  app.delete('/api/v1/activities/:id', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const result = await sql<{ id: string }>`
      DELETE FROM route_activity_sessions
      WHERE id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
      RETURNING id
    `.execute(db);
    if (!result.rows[0]) return reply.code(404).send({ error: 'activity_not_found' });
    return reply.code(204).send();
  });
}
