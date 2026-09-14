import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const routeParams = z.object({ id: z.string().uuid() });
const progressionSchema = z.object({ progression_mode: z.enum(['free', 'linear']) });

export function registerAdminRouteAdventureProgressionRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/routes/:id/adventure/progression', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });

    const route = await sql<{ id: string }>`SELECT id FROM routes WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    if (!route.rows[0]) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<{ progression_mode: 'free' | 'linear' }>`
      SELECT progression_mode FROM route_adventures WHERE route_id = ${params.data.id}::uuid LIMIT 1
    `.execute(auth.database);
    return { progression_mode: result.rows[0]?.progression_mode ?? 'free' };
  });

  app.patch('/api/v1/admin/routes/:id/adventure/progression', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(progressionSchema, request.body, reply);
    if (!input) return;

    const route = await sql<{ id: string }>`SELECT id FROM routes WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    if (!route.rows[0]) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<{ progression_mode: 'free' | 'linear' }>`
      INSERT INTO route_adventures(route_id, enabled, title, progression_mode, created_by, updated_by)
      VALUES (${params.data.id}::uuid, false, 'Modo Aventura', ${input.progression_mode}, ${auth.access.userId}::uuid, ${auth.access.userId}::uuid)
      ON CONFLICT (route_id) DO UPDATE SET
        progression_mode = EXCLUDED.progression_mode,
        updated_by = EXCLUDED.updated_by,
        updated_at = now()
      RETURNING progression_mode
    `.execute(auth.database);

    await auditAdminAction(auth.database, auth.access, 'route.adventure_progression_updated', 'route', params.data.id, { progression_mode: input.progression_mode });
    return result.rows[0];
  });
}
