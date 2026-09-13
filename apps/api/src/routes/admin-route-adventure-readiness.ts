import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requirePlatformAccess } from '../admin/access.js';

const routeParams = z.object({ id: z.string().uuid() });

type ReadinessRow = {
  route_status: string;
  track_status: string;
  validated_track_count: number;
  active_checkpoint_count: number;
  required_checkpoint_count: number;
};

export type AdventureReadiness = {
  ready: boolean;
  blockers: string[];
  route_status: string;
  track_status: string;
  validated_track_count: number;
  active_checkpoint_count: number;
  required_checkpoint_count: number;
};

export async function loadAdventureReadiness(database: DatabaseClient, routeId: string): Promise<AdventureReadiness | null> {
  const result = await sql<ReadinessRow>`
    SELECT
      r.status AS route_status,
      r.track_status,
      (SELECT COUNT(*)::int FROM route_tracks rt
        WHERE rt.route_id = r.id AND rt.validation_status = 'validated') AS validated_track_count,
      (SELECT COUNT(*)::int FROM route_adventure_checkpoints cp
        WHERE cp.route_id = r.id AND cp.active = true) AS active_checkpoint_count,
      (SELECT COUNT(*)::int FROM route_adventure_checkpoints cp
        WHERE cp.route_id = r.id AND cp.active = true AND cp.is_required = true) AS required_checkpoint_count
    FROM routes r
    WHERE r.id = ${routeId}::uuid
    LIMIT 1
  `.execute(database);

  const row = result.rows[0];
  if (!row) return null;

  const blockers: string[] = [];
  if (row.route_status !== 'published') blockers.push('route_not_published');
  if (row.track_status !== 'validated' || Number(row.validated_track_count) < 1) blockers.push('validated_track_missing');
  if (Number(row.active_checkpoint_count) < 1) blockers.push('active_checkpoint_missing');
  if (Number(row.required_checkpoint_count) < 1) blockers.push('required_checkpoint_missing');

  return {
    ready: blockers.length === 0,
    blockers,
    route_status: row.route_status,
    track_status: row.track_status,
    validated_track_count: Number(row.validated_track_count),
    active_checkpoint_count: Number(row.active_checkpoint_count),
    required_checkpoint_count: Number(row.required_checkpoint_count),
  };
}

export function registerAdminRouteAdventureReadinessRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/routes/:id/adventure/readiness', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;

    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });

    const readiness = await loadAdventureReadiness(auth.database, params.data.id);
    if (!readiness) return reply.code(404).send({ error: 'route_not_found' });
    return readiness;
  });
}
