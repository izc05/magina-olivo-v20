import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { createObservationSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { writeDomainEffects } from '../domain/effects.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerObservationRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/fields/:fieldId/observations', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const fieldId = parsedFieldId.data;

    const input = parseBody(createObservationSchema, request.body, reply);
    if (!input) return;

    const replay = await database.selectFrom('observation_records').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, observation: replay });

    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const campaign = await database.selectFrom('campaigns').select('id')
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .orderBy('start_date', 'desc')
      .executeTakeFirst();

    const result = await database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const observation = await trx.insertInto('observation_records').values({
        id,
        workspace_id: context.workspaceId,
        field_id: fieldId,
        campaign_id: campaign?.id ?? null,
        occurred_at: input.occurred_at,
        observation_type: input.observation_type,
        notes: input.notes,
        severity: input.severity ?? null,
        client_operation_id: input.client_operation_id,
        created_by: context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const projections = await writeDomainEffects(trx, {
        workspaceId: context.workspaceId,
        fieldId,
        campaignId: campaign?.id ?? null,
        domainType: 'observation',
        domainRecordId: id,
        occurredAt: input.occurred_at,
        title: `Observación · ${input.observation_type}`,
        summary: [input.severity ? `Severidad ${input.severity}` : null, input.notes].filter(Boolean).join(' · '),
        iconKey: 'observation',
        followUp: input.follow_up ? { scheduledAt: input.follow_up.scheduled_at, title: `Revisar ${input.observation_type.toLowerCase()} · ${field.name}` } : undefined,
      });

      return { observation, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.get('/api/v1/fields/:fieldId/observations', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const field = await fieldBelongsToWorkspace(database, parsedFieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const observations = await database.selectFrom('observation_records').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('field_id', '=', parsedFieldId.data)
      .orderBy('occurred_at', 'desc')
      .limit(200)
      .execute();

    return { observations };
  });
}
