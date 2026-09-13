import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const routeParams = z.object({ id: z.string().uuid() });
const bulkImportSchema = z.object({
  unlock_radius_m: z.number().int().min(10).max(500).default(60),
  points: z.number().int().min(0).max(10000).default(100),
  is_required: z.boolean().default(true),
});

export function registerAdminRouteAdventureBulkRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/admin/routes/:id/adventure/import-route-points', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;

    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(bulkImportSchema, request.body ?? {}, reply);
    if (!input) return;

    const route = await sql<{ id: string }>`
      SELECT id FROM routes WHERE id = ${params.data.id}::uuid LIMIT 1
    `.execute(auth.database);
    if (!route.rows[0]) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<{ eligible_count: number; created_count: number }>`
      WITH eligible AS (
        SELECT rp.id, rp.route_id, rp.name, rp.description, rp.location, rp.distance_m, rp.sort_order
        FROM route_points rp
        WHERE rp.route_id = ${params.data.id}::uuid
          AND rp.active = true
          AND rp.location IS NOT NULL
      ), inserted AS (
        INSERT INTO route_adventure_checkpoints(
          route_id, route_point_id, title, description, kind, location, distance_m,
          unlock_radius_m, points, is_required, answer_options, sort_order, active
        )
        SELECT
          e.route_id, e.id, e.name, e.description, 'landmark', e.location, e.distance_m,
          ${input.unlock_radius_m}, ${input.points}, ${input.is_required}, '[]'::jsonb,
          e.sort_order, true
        FROM eligible e
        WHERE NOT EXISTS (
          SELECT 1
          FROM route_adventure_checkpoints cp
          WHERE cp.route_id = e.route_id AND cp.route_point_id = e.id
        )
        ON CONFLICT DO NOTHING
        RETURNING id
      )
      SELECT
        (SELECT COUNT(*) FROM eligible)::int AS eligible_count,
        (SELECT COUNT(*) FROM inserted)::int AS created_count
    `.execute(auth.database);

    const summary = result.rows[0] ?? { eligible_count: 0, created_count: 0 };
    const skippedCount = Math.max(0, Number(summary.eligible_count) - Number(summary.created_count));

    await auditAdminAction(
      auth.database,
      auth.access,
      'route.adventure_points_imported',
      'route',
      params.data.id,
      {
        eligible_count: Number(summary.eligible_count),
        created_count: Number(summary.created_count),
        skipped_count: skippedCount,
        unlock_radius_m: input.unlock_radius_m,
        points: input.points,
        is_required: input.is_required,
      },
    );

    return {
      eligible_count: Number(summary.eligible_count),
      created_count: Number(summary.created_count),
      skipped_count: skippedCount,
    };
  });
}
