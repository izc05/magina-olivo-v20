import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { readAuthenticatedUserId } from '../request-context.js';

export function registerRouteAdventureHubRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/adventures', async (_request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });

    const result = await sql<{
      route_id: string;
      slug: string;
      route_name: string;
      route_type: string;
      difficulty: string | null;
      distance_m: number | null;
      duration_minutes: number | null;
      municipality_name: string | null;
      place_name: string | null;
      short_description: string | null;
      title: string;
      intro: string | null;
      checkpoint_count: number;
      required_count: number;
      total_points: number;
      hero_url: string | null;
    }>`
      SELECT r.id AS route_id,
             r.slug,
             r.name AS route_name,
             r.route_type,
             r.difficulty,
             r.distance_m,
             r.duration_minutes,
             m.name AS municipality_name,
             p.name AS place_name,
             r.short_description,
             a.title,
             a.intro,
             COUNT(cp.id)::int AS checkpoint_count,
             COUNT(cp.id) FILTER (WHERE cp.is_required)::int AS required_count,
             COALESCE(SUM(cp.points), 0)::int AS total_points,
             (
               SELECT rm.url
               FROM route_media rm
               WHERE rm.route_id = r.id
                 AND rm.active = true
                 AND rm.kind IN ('hero_image', 'photo', 'thumbnail')
               ORDER BY CASE rm.kind WHEN 'hero_image' THEN 0 WHEN 'thumbnail' THEN 1 ELSE 2 END,
                        rm.sort_order, rm.created_at
               LIMIT 1
             ) AS hero_url
      FROM route_adventures a
      JOIN routes r ON r.id = a.route_id
      LEFT JOIN route_adventure_checkpoints cp ON cp.route_id = r.id AND cp.active = true
      LEFT JOIN territory_municipalities m ON m.id = r.municipality_id
      LEFT JOIN territory_places p ON p.id = r.place_id
      WHERE a.enabled = true
        AND r.status = 'published'
        AND r.track_status = 'validated'
      GROUP BY r.id, r.slug, r.name, r.route_type, r.difficulty, r.distance_m,
               r.duration_minutes, m.name, p.name, r.short_description,
               a.title, a.intro, r.published_at
      HAVING COUNT(cp.id) > 0
      ORDER BY r.published_at DESC NULLS LAST, r.name
    `.execute(db);

    return {
      adventures: result.rows,
      notice: 'Mágina Aventura es una capa lúdica sobre rutas publicadas y tracks validados. No sustituye la navegación, la señalización ni los avisos oficiales.',
    };
  });

  app.get('/api/v1/adventures/me', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });

    const [runsResult, discoveriesResult, collectionsResult, territoryResult, recentResult] = await Promise.all([
      sql<{ adventures_started: number; adventures_completed: number }>`
        SELECT COUNT(DISTINCT route_id)::int AS adventures_started,
               COUNT(DISTINCT route_id) FILTER (WHERE status = 'completed')::int AS adventures_completed
        FROM route_adventure_runs
        WHERE user_id = ${userId}::uuid
      `.execute(db),
      sql<{ discoveries: number; total_score: number }>`
        WITH user_checkpoint AS (
          SELECT u.checkpoint_id, MAX(u.awarded_points)::int AS points
          FROM route_adventure_unlocks u
          JOIN route_adventure_runs ar ON ar.id = u.run_id
          WHERE ar.user_id = ${userId}::uuid
          GROUP BY u.checkpoint_id
        )
        SELECT COUNT(*)::int AS discoveries,
               COALESCE(SUM(points), 0)::int AS total_score
        FROM user_checkpoint
      `.execute(db),
      sql<{ kind: string; available: number; unlocked: number }>`
        WITH user_checkpoint AS (
          SELECT DISTINCT u.checkpoint_id
          FROM route_adventure_unlocks u
          JOIN route_adventure_runs ar ON ar.id = u.run_id
          WHERE ar.user_id = ${userId}::uuid
        )
        SELECT cp.kind,
               COUNT(*)::int AS available,
               COUNT(uc.checkpoint_id)::int AS unlocked
        FROM route_adventure_checkpoints cp
        JOIN route_adventures a ON a.route_id = cp.route_id AND a.enabled = true
        JOIN routes r ON r.id = cp.route_id AND r.status = 'published' AND r.track_status = 'validated'
        LEFT JOIN user_checkpoint uc ON uc.checkpoint_id = cp.id
        WHERE cp.active = true
        GROUP BY cp.kind
        ORDER BY cp.kind
      `.execute(db),
      sql<{
        municipality_id: string;
        municipality_name: string;
        municipality_slug: string;
        adventure_count: number;
        available: number;
        unlocked: number;
      }>`
        WITH user_checkpoint AS (
          SELECT DISTINCT u.checkpoint_id
          FROM route_adventure_unlocks u
          JOIN route_adventure_runs ar ON ar.id = u.run_id
          WHERE ar.user_id = ${userId}::uuid
        )
        SELECT m.id AS municipality_id,
               m.name AS municipality_name,
               m.slug AS municipality_slug,
               COUNT(DISTINCT r.id)::int AS adventure_count,
               COUNT(cp.id)::int AS available,
               COUNT(uc.checkpoint_id)::int AS unlocked
        FROM route_adventure_checkpoints cp
        JOIN route_adventures a ON a.route_id = cp.route_id AND a.enabled = true
        JOIN routes r ON r.id = cp.route_id AND r.status = 'published' AND r.track_status = 'validated'
        JOIN territory_municipalities m ON m.id = r.municipality_id
        LEFT JOIN user_checkpoint uc ON uc.checkpoint_id = cp.id
        WHERE cp.active = true
        GROUP BY m.id, m.name, m.slug
        ORDER BY unlocked DESC, available DESC, m.name
      `.execute(db),
      sql<{
        id: string;
        route_id: string;
        slug: string;
        route_name: string;
        adventure_title: string;
        status: string;
        score: number;
        started_at: string;
        completed_at: string | null;
        unlocked_checkpoints: number;
        total_checkpoints: number;
      }>`
        SELECT ar.id, ar.route_id, r.slug, r.name AS route_name, a.title AS adventure_title,
               ar.status, ar.score, ar.started_at, ar.completed_at,
               COUNT(DISTINCT u.checkpoint_id)::int AS unlocked_checkpoints,
               (SELECT COUNT(*)::int FROM route_adventure_checkpoints cp WHERE cp.route_id = ar.route_id AND cp.active = true) AS total_checkpoints
        FROM route_adventure_runs ar
        JOIN routes r ON r.id = ar.route_id
        JOIN route_adventures a ON a.route_id = ar.route_id
        LEFT JOIN route_adventure_unlocks u ON u.run_id = ar.id
        WHERE ar.user_id = ${userId}::uuid
        GROUP BY ar.id, r.slug, r.name, a.title
        ORDER BY ar.started_at DESC
        LIMIT 6
      `.execute(db),
    ]);

    const runs = runsResult.rows[0] ?? { adventures_started: 0, adventures_completed: 0 };
    const discoveries = discoveriesResult.rows[0] ?? { discoveries: 0, total_score: 0 };
    const badges: string[] = [];
    if (discoveries.discoveries > 0) badges.push('primer_descubrimiento');
    if (runs.adventures_completed > 0) badges.push('aventurero_magina');
    if (runs.adventures_completed >= 3) badges.push('caminante_de_la_sierra');
    if (discoveries.total_score >= 1000) badges.push('mil_puntos');

    const territory = territoryResult.rows.map((row) => ({
      ...row,
      adventure_count: Number(row.adventure_count),
      available: Number(row.available),
      unlocked: Number(row.unlocked),
      percent: Number(row.available) > 0 ? Math.round((Number(row.unlocked) / Number(row.available)) * 100) : 0,
    }));
    const territoryAvailable = territory.reduce((sum, row) => sum + row.available, 0);
    const territoryUnlocked = territory.reduce((sum, row) => sum + row.unlocked, 0);

    return {
      summary: { ...runs, ...discoveries },
      collections: collectionsResult.rows,
      territory: {
        available_checkpoints: territoryAvailable,
        unlocked_checkpoints: territoryUnlocked,
        explored_percent: territoryAvailable > 0 ? Math.round((territoryUnlocked / territoryAvailable) * 100) : 0,
        municipalities_available: territory.length,
        municipalities_discovered: territory.filter((row) => row.unlocked > 0).length,
        municipalities: territory,
      },
      recent_runs: recentResult.rows,
      badges,
      privacy: 'El perfil guarda progreso de juego, no un historial de coordenadas GPS.',
    };
  });
}
