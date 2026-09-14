import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { readAuthenticatedUserId } from '../request-context.js';

type RewardRow = {
  sponsorship_id: string;
  route_id: string;
  route_slug: string;
  route_name: string;
  adventure_completed_at: string;
  sponsor_name: string;
  sponsor_logo_url: string | null;
  sponsor_url: string | null;
  headline: string | null;
  description: string | null;
  cta_label: string | null;
  cta_url: string | null;
  promo_code: string | null;
  disclosure: string;
  ends_at: string | null;
};

export function registerRouteAdventureRewardRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/adventure/rewards', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const userId = readAuthenticatedUserId(request);
    if (!userId) return reply.code(401).send({ error: 'authentication_required' });

    const result = await sql<RewardRow>`
      WITH completed_routes AS (
        SELECT route_id, MAX(completed_at) AS completed_at
        FROM route_adventure_runs
        WHERE user_id = ${userId}::uuid
          AND status = 'completed'
          AND completed_at IS NOT NULL
        GROUP BY route_id
      )
      SELECT s.id AS sponsorship_id,
             s.route_id,
             r.slug AS route_slug,
             r.name AS route_name,
             cr.completed_at AS adventure_completed_at,
             s.sponsor_name,
             s.sponsor_logo_url,
             s.sponsor_url,
             s.headline,
             s.description,
             s.cta_label,
             s.cta_url,
             s.promo_code,
             s.disclosure,
             s.ends_at
      FROM completed_routes cr
      JOIN routes r ON r.id = cr.route_id
      JOIN route_sponsorships s ON s.route_id = cr.route_id
      WHERE s.placement = 'collection'
        AND s.status = 'active'
        AND (s.starts_at IS NULL OR s.starts_at <= now())
        AND (s.ends_at IS NULL OR s.ends_at > now())
      ORDER BY cr.completed_at DESC, s.priority DESC, s.created_at DESC
      LIMIT 24
    `.execute(db);

    return {
      rewards: result.rows,
      notice: 'Las recompensas comerciales aparecen siempre como patrocinadas. Se desbloquean por completar una aventura, nunca por velocidad, posición en rankings ni cantidad de GPS compartido.',
    };
  });
}
