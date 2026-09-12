import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { createIrrigationSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { writeDomainEffects } from '../domain/effects.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerIrrigationRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/fields/:fieldId/irrigations', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const fieldId = parsedFieldId.data;

    const input = parseBody(createIrrigationSchema, request.body, reply);
    if (!input) return;

    const replay = await database.selectFrom('irrigation_records')
      .selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, irrigation: replay });

    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const campaign = await database.selectFrom('campaigns')
      .select('id')
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .orderBy('start_date', 'desc')
      .executeTakeFirst();

    const result = await database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const irrigation = await trx.insertInto('irrigation_records').values({
        id,
        workspace_id: context.workspaceId,
        field_id: fieldId,
        campaign_id: campaign?.id ?? null,
        occurred_at: input.occurred_at,
        duration_hours: input.duration_hours ?? null,
        water_m3: input.water_m3 ?? null,
        cost_eur: input.cost_eur ?? null,
        notes: input.notes ?? null,
        client_operation_id: input.client_operation_id,
        created_by: context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const summaryParts = [
        input.duration_hours ? `${input.duration_hours} h` : null,
        input.water_m3 !== undefined ? `${input.water_m3} m³` : null,
        input.cost_eur !== undefined ? `${input.cost_eur.toFixed(2)} €` : null,
      ].filter(Boolean);

      const projections = await writeDomainEffects(trx, {
        workspaceId: context.workspaceId,
        fieldId,
        campaignId: campaign?.id ?? null,
        domainType: 'irrigation',
        domainRecordId: id,
        occurredAt: input.occurred_at,
        title: 'Riego',
        summary: summaryParts.join(' · ') || input.notes || null,
        iconKey: 'water',
        cost: input.cost_eur !== undefined ? { amountEur: input.cost_eur, category: 'irrigation' } : undefined,
        followUp: input.follow_up ? { scheduledAt: input.follow_up.scheduled_at, title: `Próximo riego · ${field.name}` } : undefined,
      });

      return { irrigation, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.get('/api/v1/fields/:fieldId/irrigations', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });

    const field = await fieldBelongsToWorkspace(database, parsedFieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const irrigations = await database.selectFrom('irrigation_records')
      .selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('field_id', '=', parsedFieldId.data)
      .orderBy('occurred_at', 'desc')
      .limit(100)
      .execute();

    return { irrigations };
  });
}
