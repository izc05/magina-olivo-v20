import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { readAuthenticatedUserId } from '../request-context.js';

type SummaryRow = {
  completed_activities: number;
  recorded_distance_m: number | string;
  recorded_active_seconds: number | string;
  longest_activity_m: number | string;
  recorded_elevation_gain_m: number | string;
};

type Milestone = {
  id: string;
  label: string;
  description: string;
  metric: 'activities' | 'distance_m' | 'elevation_gain_m' | 'longest_activity_m';
  target: number;
};

const milestones: Milestone[] = [
  { id: 'primera_salida', label: 'Primera salida', description: 'Guarda tu primer recorrido real.', metric: 'activities', target: 1 },
  { id: 'cinco_salidas', label: 'Ritmo constante', description: 'Completa 5 recorridos registrados.', metric: 'activities', target: 5 },
  { id: 'veinticinco_km_registrados', label: '25 km registrados', description: 'Acumula 25 km de actividad real.', metric: 'distance_m', target: 25_000 },
  { id: 'cien_km_registrados', label: '100 km registrados', description: 'Acumula 100 km de actividad real.', metric: 'distance_m', target: 100_000 },
  { id: 'mil_metros_positivos', label: '1.000 m positivos', description: 'Acumula 1.000 m de desnivel positivo GPS estimado.', metric: 'elevation_gain_m', target: 1_000 },
  { id: 'cinco_mil_metros_positivos', label: '5.000 m positivos', description: 'Acumula 5.000 m de desnivel positivo GPS estimado.', metric: 'elevation_gain_m', target: 5_000 },
  { id: 'gran_travesia', label: 'Gran travesía', description: 'Completa un recorrido registrado de al menos 20 km.', metric: 'longest_activity_m', target: 20_000 },
];

function numeric(value: number | string | null | undefined) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function registerRouteActivityAchievementRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/activities/me/achievements', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });

    const result = await sql<SummaryRow>`
      WITH completed AS (
        SELECT id, distance_m, active_seconds
        FROM route_activity_sessions
        WHERE user_id = ${userId}::uuid AND status = 'completed'
      ),
      ordered_altitude AS (
        SELECT p.session_id,
               p.recorded_at,
               p.id,
               p.altitude_m::double precision AS altitude_m,
               p.segment_distance_m::double precision AS segment_distance_m,
               lag(p.altitude_m::double precision) OVER (
                 PARTITION BY p.session_id ORDER BY p.recorded_at, p.id
               ) AS previous_altitude_m
        FROM route_activity_points p
        JOIN completed c ON c.id = p.session_id
      ),
      elevation AS (
        SELECT COALESCE(SUM(
          CASE
            WHEN segment_distance_m > 0
              AND altitude_m IS NOT NULL
              AND previous_altitude_m IS NOT NULL
              AND altitude_m - previous_altitude_m BETWEEN 3 AND 80
            THEN altitude_m - previous_altitude_m
            ELSE 0
          END
        ), 0) AS recorded_elevation_gain_m
        FROM ordered_altitude
      )
      SELECT COUNT(*)::int AS completed_activities,
             COALESCE(SUM(c.distance_m), 0) AS recorded_distance_m,
             COALESCE(SUM(c.active_seconds), 0) AS recorded_active_seconds,
             COALESCE(MAX(c.distance_m), 0) AS longest_activity_m,
             COALESCE((SELECT recorded_elevation_gain_m FROM elevation), 0) AS recorded_elevation_gain_m
      FROM completed c
    `.execute(db);

    const row = result.rows[0] ?? {
      completed_activities: 0,
      recorded_distance_m: 0,
      recorded_active_seconds: 0,
      longest_activity_m: 0,
      recorded_elevation_gain_m: 0,
    };

    const summary = {
      completed_activities: numeric(row.completed_activities),
      recorded_distance_m: numeric(row.recorded_distance_m),
      recorded_active_seconds: numeric(row.recorded_active_seconds),
      longest_activity_m: numeric(row.longest_activity_m),
      recorded_elevation_gain_m: numeric(row.recorded_elevation_gain_m),
    };

    const values = {
      activities: summary.completed_activities,
      distance_m: summary.recorded_distance_m,
      elevation_gain_m: summary.recorded_elevation_gain_m,
      longest_activity_m: summary.longest_activity_m,
    } as const;

    return {
      summary,
      achievements: milestones.map((milestone) => {
        const value = values[milestone.metric];
        return {
          ...milestone,
          value,
          unlocked: value >= milestone.target,
          progress_percent: Math.min(100, Math.max(0, Math.round((value / milestone.target) * 100))),
        };
      }),
      notice: 'Los logros deportivos se derivan de recorridos grabados voluntariamente. No alteran los km conquistados ni el porcentaje de Sierra Mágina explorado.',
    };
  });
}
