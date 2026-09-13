import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';

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
}
