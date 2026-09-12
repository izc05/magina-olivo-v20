import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { createFieldSchema, updateFieldSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

type ResolvedPlace = {
  place_id: string;
  place_name: string;
  municipality_id: string;
  municipality_name: string;
  province_name: string;
};

async function resolvePlace(database: DatabaseClient, placeId: string): Promise<ResolvedPlace | null> {
  const row = await database.selectFrom('territory_places as p')
    .innerJoin('territory_municipalities as m', 'm.id', 'p.municipality_id')
    .select([
      'p.id as place_id',
      'p.name as place_name',
      'm.id as municipality_id',
      'm.name as municipality_name',
      'm.province_name',
    ])
    .where('p.id', '=', placeId)
    .where('p.public_enabled', '=', true)
    .where('m.active', '=', true)
    .executeTakeFirst();
  return row ?? null;
}

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

    let resolvedPlace: ResolvedPlace | null = null;
    if (input.place_id) {
      resolvedPlace = await resolvePlace(database, input.place_id);
      if (!resolvedPlace) return reply.code(400).send({ error: 'invalid_territory_place' });
    }

    const id = input.entity_id ?? randomUUID();
    const created = await database.insertInto('fields').values({
      id,
      workspace_id: context.workspaceId,
      client_operation_id: input.client_operation_id,
      name: input.name,
      description: null,
      municipality: resolvedPlace?.place_name ?? input.municipality ?? null,
      province: resolvedPlace?.province_name ?? input.province ?? null,
      municipality_id: resolvedPlace?.municipality_id ?? null,
      place_id: resolvedPlace?.place_id ?? null,
      calculated_area_ha: null,
      geometry_source: null,
      geometry_status: 'unlocated',
      geometry_checked_at: null,
      tree_count: input.tree_count ?? null,
      crop: 'olivar',
      variety: input.variety ?? null,
      water_regime: input.water_regime ?? null,
      planting_year: null,
      tenure_type: null,
      status: 'active',
    }).returningAll().executeTakeFirstOrThrow();

    return reply.code(201).send({
      replayed: false,
      field: created,
      territory: resolvedPlace ? {
        place_id: resolvedPlace.place_id,
        place_name: resolvedPlace.place_name,
        municipality_id: resolvedPlace.municipality_id,
        municipality_name: resolvedPlace.municipality_name,
      } : null,
    });
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

  app.get('/api/v1/fields/:fieldId', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const parsedFieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });

    const field = await database.selectFrom('fields')
      .selectAll()
      .where('id', '=', parsedFieldId.data)
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .executeTakeFirst();
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    return { field };
  });

  app.patch('/api/v1/fields/:fieldId', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const parsedFieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const input = parseBody(updateFieldSchema, request.body, reply);
    if (!input) return;

    const current = await database.selectFrom('fields')
      .selectAll()
      .where('id', '=', parsedFieldId.data)
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .executeTakeFirst();
    if (!current) return reply.code(404).send({ error: 'field_not_found' });

    let resolvedPlace: ResolvedPlace | null = null;
    if (typeof input.place_id === 'string') {
      resolvedPlace = await resolvePlace(database, input.place_id);
      if (!resolvedPlace) return reply.code(400).send({ error: 'invalid_territory_place' });
    }

    const placeWasCleared = input.place_id === null;
    const updated = await database.updateTable('fields')
      .set({
        name: input.name ?? current.name,
        tree_count: input.tree_count !== undefined ? input.tree_count : current.tree_count,
        variety: input.variety !== undefined ? input.variety : current.variety,
        water_regime: input.water_regime !== undefined ? input.water_regime : current.water_regime,
        place_id: resolvedPlace?.place_id ?? (placeWasCleared ? null : current.place_id),
        municipality_id: resolvedPlace?.municipality_id ?? (placeWasCleared ? null : current.municipality_id),
        municipality: resolvedPlace?.place_name ?? (input.municipality !== undefined ? input.municipality : (placeWasCleared ? null : current.municipality)),
        province: resolvedPlace?.province_name ?? (input.province !== undefined ? input.province : (placeWasCleared ? null : current.province)),
        updated_at: new Date(),
      })
      .where('id', '=', parsedFieldId.data)
      .where('workspace_id', '=', context.workspaceId)
      .returningAll()
      .executeTakeFirstOrThrow();

    return { field: updated };
  });

  app.get('/api/v1/fields/:fieldId/activity', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const parsedFieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const field = await fieldBelongsToWorkspace(database, parsedFieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const items = await database.selectFrom('farm_timeline_projection')
      .select(['id', 'occurred_at', 'domain_type', 'domain_record_id', 'title', 'summary', 'icon_key'])
      .where('workspace_id', '=', context.workspaceId)
      .where('field_id', '=', parsedFieldId.data)
      .orderBy('occurred_at', 'desc')
      .limit(100)
      .execute();

    return {
      field: { id: field.id, name: field.name },
      items,
    };
  });
}
