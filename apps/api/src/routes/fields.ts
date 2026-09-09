import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { createFieldSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerFieldRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/fields', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const input = parseBody(createFieldSchema, request.body, reply);
    if (!input) return;

    const existing = await database.selectFrom('fields')
      .selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();

    if (existing) {
      return reply.code(200).send({ replayed: true, field: existing });
    }

    const id = input.entity_id ?? randomUUID();
    const created = await database.insertInto('fields').values({
      id,
      workspace_id: context.workspaceId,
      client_operation_id: input.client_operation_id,
      name: input.name,
      description: null,
      municipality: input.municipality ?? null,
      province: input.province ?? null,
      calculated_area_ha: null,
      tree_count: input.tree_count ?? null,
      crop: 'olivar',
      variety: input.variety ?? null,
      water_regime: input.water_regime ?? null,
      planting_year: null,
      tenure_type: null,
      status: 'active',
    }).returningAll().executeTakeFirstOrThrow();

    return reply.code(201).send({ replayed: false, field: created });
  });

  app.get('/api/v1/fields', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const fields = await database.selectFrom('fields')
      .selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .orderBy('created_at', 'desc')
      .execute();

    return { fields };
  });
}
