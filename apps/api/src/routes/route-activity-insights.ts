import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { readAuthenticatedUserId } from '../request-context.js';

const activityParams = z.object({ id: z.string().uuid() });

type ActivityInsightRow = {
  route_id: string | null;
  route_slug: string | null;
  route_name: string | null;
  recorded_distance_m: number | string;
  active_seconds: number;
  official_distance_m: number | string | null;
  official_elevation_gain_m: number | string | null;
  official_duration_minutes: number | string | null;
};

type QualityRow = {
  average_accuracy_m: number | string | null;
  altitude_samples: number;
  min_altitude_m: number | string | null;
  max_altitude_m: number | string | null;
  elevation_gain_m: number | string | null;
  elevation_loss_m: number | string | null;
};

function numberOrNull(value: number | string | null) {
  if (value == null) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

export function registerRouteActivityInsightRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/activities/:id/insights', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });
    const params = activityParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_activity_id' });

    const activityResult = await sql<ActivityInsightRow>`
      SELECT s.route_id,
             r.slug AS route_slug,
             r.name AS route_name,
             s.distance_m AS recorded_distance_m,
             s.active_seconds,
             r.distance_m AS official_distance_m,
             r.elevation_gain_m AS official_elevation_gain_m,
             r.duration_minutes AS official_duration_minutes
      FROM route_activity_sessions s
      LEFT JOIN routes r ON r.id = s.route_id
      WHERE s.id = ${params.data.id}::uuid
        AND s.user_id = ${userId}::uuid
      LIMIT 1
    `.execute(db);
    const activity = activityResult.rows[0];
    if (!activity) return reply.code(404).send({ error: 'activity_not_found' });

    const qualityResult = await sql<QualityRow>`
      WITH ordered AS (
        SELECT recorded_at,
               id,
               accuracy_m::double precision AS accuracy_m,
               altitude_m::double precision AS altitude_m,
               segment_distance_m::double precision AS segment_distance_m,
               lag(altitude_m::double precision) OVER (ORDER BY recorded_at, id) AS previous_altitude_m
        FROM route_activity_points
        WHERE session_id = ${params.data.id}::uuid
      )
      SELECT AVG(accuracy_m) AS average_accuracy_m,
             COUNT(altitude_m)::int AS altitude_samples,
             MIN(altitude_m) AS min_altitude_m,
             MAX(altitude_m) AS max_altitude_m,
             COALESCE(SUM(
               CASE
                 WHEN segment_distance_m > 0
                   AND altitude_m IS NOT NULL
                   AND previous_altitude_m IS NOT NULL
                   AND altitude_m - previous_altitude_m BETWEEN 3 AND 80
                 THEN altitude_m - previous_altitude_m
                 ELSE 0
               END
             ), 0) AS elevation_gain_m,
             COALESCE(SUM(
               CASE
                 WHEN segment_distance_m > 0
                   AND altitude_m IS NOT NULL
                   AND previous_altitude_m IS NOT NULL
                   AND previous_altitude_m - altitude_m BETWEEN 3 AND 80
                 THEN previous_altitude_m - altitude_m
                 ELSE 0
               END
             ), 0) AS elevation_loss_m
      FROM ordered
    `.execute(db);

    const qualityRaw = qualityResult.rows[0] ?? {
      average_accuracy_m: null,
      altitude_samples: 0,
      min_altitude_m: null,
      max_altitude_m: null,
      elevation_gain_m: 0,
      elevation_loss_m: 0,
    };
    const recordedDistance = Number(activity.recorded_distance_m);
    const activeSeconds = Number(activity.active_seconds);
    const officialDistance = numberOrNull(activity.official_distance_m);
    const officialElevation = numberOrNull(activity.official_elevation_gain_m);
    const officialDuration = numberOrNull(activity.official_duration_minutes);
    const elevationGain = numberOrNull(qualityRaw.elevation_gain_m) ?? 0;

    return {
      activity_id: params.data.id,
      recorded: {
        distance_m: recordedDistance,
        active_seconds: activeSeconds,
        average_speed_kmh: activeSeconds > 0 ? (recordedDistance / 1000) / (activeSeconds / 3600) : 0,
      },
      quality: {
        average_accuracy_m: numberOrNull(qualityRaw.average_accuracy_m),
        altitude_samples: Number(qualityRaw.altitude_samples),
        min_altitude_m: numberOrNull(qualityRaw.min_altitude_m),
        max_altitude_m: numberOrNull(qualityRaw.max_altitude_m),
        elevation_gain_m: elevationGain,
        elevation_loss_m: numberOrNull(qualityRaw.elevation_loss_m) ?? 0,
        elevation_method: 'gps_filtered',
      },
      official_route: activity.route_id ? {
        id: activity.route_id,
        slug: activity.route_slug,
        name: activity.route_name,
        distance_m: officialDistance,
        elevation_gain_m: officialElevation,
        duration_minutes: officialDuration,
        distance_delta_m: officialDistance == null ? null : recordedDistance - officialDistance,
        distance_delta_percent: officialDistance && officialDistance > 0
          ? ((recordedDistance - officialDistance) / officialDistance) * 100
          : null,
        elevation_delta_m: officialElevation == null ? null : elevationGain - officialElevation,
        active_time_delta_minutes: officialDuration == null ? null : (activeSeconds / 60) - officialDuration,
      } : null,
      notice: 'El desnivel se estima con altitud GPS filtrada y puede ser menos preciso que el perfil oficial. Comparar métricas no certifica que el usuario haya seguido exactamente el trazado oficial.',
    };
  });
}
