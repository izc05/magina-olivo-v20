import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import {
  createExpenseSchema,
  createFertilizationSchema,
  createPruningSchema,
  createTreatmentSchema,
  uuidSchema,
} from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { writeDomainEffects } from '../domain/effects.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

async function resolveScope(request: FastifyRequest, reply: FastifyReply, db: DatabaseClient | null) {
  const context = requireContext(request, reply);
  if (!context) return null;
  const database = requireDatabase(db, reply);
  if (!database) return null;
  const rawFieldId = (request.params as { fieldId?: string }).fieldId;
  const parsedFieldId = uuidSchema.safeParse(rawFieldId);
  if (!parsedFieldId.success) {
    await reply.code(400).send({ error: 'invalid_field_id' });
    return null;
  }
  const field = await fieldBelongsToWorkspace(database, parsedFieldId.data, context.workspaceId);
  if (!field) {
    await reply.code(404).send({ error: 'field_not_found' });
    return null;
  }
  const campaign = await database.selectFrom('campaigns')
    .select('id')
    .where('workspace_id', '=', context.workspaceId)
    .where('status', '=', 'active')
    .orderBy('start_date', 'desc')
    .executeTakeFirst();
  return { context, database, fieldId: parsedFieldId.data, field, campaignId: campaign?.id ?? null };
}

export function registerDomainRecordRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/fields/:fieldId/treatments', async (request, reply) => {
    const scope = await resolveScope(request, reply, db);
    if (!scope) return;
    const input = parseBody(createTreatmentSchema, request.body, reply);
    if (!input) return;

    const replay = await scope.database.selectFrom('treatment_records').selectAll()
      .where('workspace_id', '=', scope.context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, treatment: replay });

    const result = await scope.database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const treatment = await trx.insertInto('treatment_records').values({
        id,
        workspace_id: scope.context.workspaceId,
        field_id: scope.fieldId,
        campaign_id: scope.campaignId,
        occurred_at: input.occurred_at,
        reason: input.reason,
        product_name: input.product_name,
        dose: input.dose ?? null,
        quantity: input.quantity ?? null,
        applicator: input.applicator ?? null,
        equipment: input.equipment ?? null,
        cost_eur: input.cost_eur ?? null,
        notes: input.notes ?? null,
        client_operation_id: input.client_operation_id,
        created_by: scope.context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const projections = await writeDomainEffects(trx, {
        workspaceId: scope.context.workspaceId,
        fieldId: scope.fieldId,
        campaignId: scope.campaignId,
        domainType: 'treatment',
        domainRecordId: id,
        occurredAt: input.occurred_at,
        title: `Tratamiento · ${input.reason}`,
        summary: [input.product_name, input.dose, input.quantity].filter(Boolean).join(' · ') || null,
        iconKey: 'treatment',
        cost: input.cost_eur !== undefined ? { amountEur: input.cost_eur, category: 'treatment' } : undefined,
        followUp: input.follow_up ? { scheduledAt: input.follow_up.scheduled_at, title: `Revisar tratamiento · ${scope.field.name}` } : undefined,
      });
      return { treatment, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.post('/api/v1/fields/:fieldId/fertilizations', async (request, reply) => {
    const scope = await resolveScope(request, reply, db);
    if (!scope) return;
    const input = parseBody(createFertilizationSchema, request.body, reply);
    if (!input) return;

    const replay = await scope.database.selectFrom('fertilization_records').selectAll()
      .where('workspace_id', '=', scope.context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, fertilization: replay });

    const result = await scope.database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const fertilization = await trx.insertInto('fertilization_records').values({
        id,
        workspace_id: scope.context.workspaceId,
        field_id: scope.fieldId,
        campaign_id: scope.campaignId,
        occurred_at: input.occurred_at,
        product_name: input.product_name,
        quantity_kg: input.quantity_kg ?? null,
        application_method: input.application_method ?? null,
        composition: input.composition ?? null,
        cost_eur: input.cost_eur ?? null,
        supplier: input.supplier ?? null,
        notes: input.notes ?? null,
        client_operation_id: input.client_operation_id,
        created_by: scope.context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const projections = await writeDomainEffects(trx, {
        workspaceId: scope.context.workspaceId,
        fieldId: scope.fieldId,
        campaignId: scope.campaignId,
        domainType: 'fertilization',
        domainRecordId: id,
        occurredAt: input.occurred_at,
        title: 'Abonado',
        summary: [input.product_name, input.quantity_kg !== undefined ? `${input.quantity_kg} kg` : null].filter(Boolean).join(' · '),
        iconKey: 'fertilization',
        cost: input.cost_eur !== undefined ? { amountEur: input.cost_eur, category: 'fertilization' } : undefined,
      });
      return { fertilization, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.post('/api/v1/fields/:fieldId/prunings', async (request, reply) => {
    const scope = await resolveScope(request, reply, db);
    if (!scope) return;
    const input = parseBody(createPruningSchema, request.body, reply);
    if (!input) return;

    const replay = await scope.database.selectFrom('pruning_records').selectAll()
      .where('workspace_id', '=', scope.context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, pruning: replay });

    const result = await scope.database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const pruning = await trx.insertInto('pruning_records').values({
        id,
        workspace_id: scope.context.workspaceId,
        field_id: scope.fieldId,
        campaign_id: scope.campaignId,
        occurred_at: input.occurred_at,
        pruning_type: input.pruning_type,
        workers: input.workers ?? null,
        hours: input.hours ?? null,
        cost_eur: input.cost_eur ?? null,
        notes: input.notes ?? null,
        client_operation_id: input.client_operation_id,
        created_by: scope.context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const projections = await writeDomainEffects(trx, {
        workspaceId: scope.context.workspaceId,
        fieldId: scope.fieldId,
        campaignId: scope.campaignId,
        domainType: 'pruning',
        domainRecordId: id,
        occurredAt: input.occurred_at,
        title: 'Poda',
        summary: [input.pruning_type, input.workers ? `${input.workers} personas` : null, input.hours ? `${input.hours} h` : null].filter(Boolean).join(' · '),
        iconKey: 'pruning',
        cost: input.cost_eur !== undefined ? { amountEur: input.cost_eur, category: 'pruning' } : undefined,
        followUp: input.follow_up ? { scheduledAt: input.follow_up.scheduled_at, title: `Revisar poda · ${scope.field.name}` } : undefined,
      });
      return { pruning, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.post('/api/v1/fields/:fieldId/expenses', async (request, reply) => {
    const scope = await resolveScope(request, reply, db);
    if (!scope) return;
    const input = parseBody(createExpenseSchema, request.body, reply);
    if (!input) return;

    const replay = await scope.database.selectFrom('expense_records').selectAll()
      .where('workspace_id', '=', scope.context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, expense: replay });

    const result = await scope.database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const expense = await trx.insertInto('expense_records').values({
        id,
        workspace_id: scope.context.workspaceId,
        field_id: scope.fieldId,
        campaign_id: scope.campaignId,
        occurred_on: input.occurred_on,
        category: input.category,
        concept: input.concept,
        amount_eur: input.amount_eur,
        notes: input.notes ?? null,
        client_operation_id: input.client_operation_id,
        created_by: scope.context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const occurredAt = `${input.occurred_on}T12:00:00.000Z`;
      const projections = await writeDomainEffects(trx, {
        workspaceId: scope.context.workspaceId,
        fieldId: scope.fieldId,
        campaignId: scope.campaignId,
        domainType: 'expense',
        domainRecordId: id,
        occurredAt,
        title: `Gasto · ${input.concept}`,
        summary: `${input.amount_eur.toFixed(2)} € · ${input.category}`,
        iconKey: 'expense',
        cost: { amountEur: input.amount_eur, category: input.category },
      });
      return { expense, projections };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });
}
