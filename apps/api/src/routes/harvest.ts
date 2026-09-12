import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import {
  createDeliveryResultSchema,
  createDeliverySchema,
  uuidSchema,
} from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { writeDomainEffects } from '../domain/effects.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

function normalizeAllocations(input: {
  total_kg: number;
  fields: Array<{ field_id: string; kg?: number }>;
}) {
  const unique = new Set(input.fields.map((item) => item.field_id));
  if (unique.size !== input.fields.length) {
    return { ok: false as const, error: 'duplicate_field_allocation' };
  }

  if (input.fields.length === 1) {
    return {
      ok: true as const,
      allocations: [{ field_id: input.fields[0].field_id, kg: input.fields[0].kg ?? input.total_kg }],
    };
  }

  if (input.fields.some((item) => item.kg === undefined)) {
    return { ok: false as const, error: 'kg_required_for_multiple_fields' };
  }

  const allocations = input.fields.map((item) => ({ field_id: item.field_id, kg: item.kg! }));
  const sum = allocations.reduce((total, item) => total + item.kg, 0);
  if (Math.abs(sum - input.total_kg) > 0.01) {
    return { ok: false as const, error: 'allocation_total_mismatch', allocated_kg: sum };
  }

  return { ok: true as const, allocations };
}

export function registerHarvestRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/deliveries', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const input = parseBody(createDeliverySchema, request.body, reply);
    if (!input) return;

    const replay = await database.selectFrom('harvest_deliveries').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, delivery: replay });

    const normalized = normalizeAllocations(input);
    if (!normalized.ok) return reply.code(400).send(normalized);

    for (const allocation of normalized.allocations) {
      const field = await fieldBelongsToWorkspace(database, allocation.field_id, context.workspaceId);
      if (!field) return reply.code(404).send({ error: 'field_not_found', field_id: allocation.field_id });
    }

    let campaignId: string | null = null;
    if (input.campaign_id) {
      const campaign = await database.selectFrom('campaigns').select('id')
        .where('id', '=', input.campaign_id)
        .where('workspace_id', '=', context.workspaceId)
        .executeTakeFirst();
      if (!campaign) return reply.code(400).send({ error: 'invalid_campaign_id' });
      campaignId = campaign.id;
    } else {
      const active = await database.selectFrom('campaigns').select('id')
        .where('workspace_id', '=', context.workspaceId)
        .where('status', '=', 'active')
        .orderBy('start_date', 'desc')
        .executeTakeFirst();
      campaignId = active?.id ?? null;
    }

    const result = await database.transaction().execute(async (trx) => {
      const id = input.entity_id ?? randomUUID();
      const delivery = await trx.insertInto('harvest_deliveries').values({
        id,
        workspace_id: context.workspaceId,
        campaign_id: campaignId,
        cooperative_or_mill: input.cooperative_or_mill ?? null,
        delivery_at: input.delivery_at,
        ticket_number: input.ticket_number ?? null,
        total_kg: input.total_kg,
        source: input.source,
        client_operation_id: input.client_operation_id,
        created_by: context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      await trx.insertInto('harvest_delivery_fields').values(
        normalized.allocations.map((allocation) => ({
          delivery_id: id,
          field_id: allocation.field_id,
          kg: allocation.kg,
        })),
      ).execute();

      const timelineIds: string[] = [];
      for (const allocation of normalized.allocations) {
        const projections = await writeDomainEffects(trx, {
          workspaceId: context.workspaceId,
          fieldId: allocation.field_id,
          campaignId,
          domainType: 'harvest_delivery',
          domainRecordId: id,
          occurredAt: input.delivery_at,
          title: 'Entrega de cosecha',
          summary: [
            `${allocation.kg.toLocaleString('es-ES')} kg`,
            input.cooperative_or_mill,
            input.ticket_number ? `Albarán ${input.ticket_number}` : null,
          ].filter(Boolean).join(' · '),
          iconKey: 'harvest',
        });
        timelineIds.push(projections.timelineId);
      }

      return { delivery, allocations: normalized.allocations, timelineIds };
    });

    return reply.code(201).send({ replayed: false, ...result });
  });

  app.post('/api/v1/deliveries/:deliveryId/results', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const rawDeliveryId = (request.params as { deliveryId?: string }).deliveryId;
    const parsedDeliveryId = uuidSchema.safeParse(rawDeliveryId);
    if (!parsedDeliveryId.success) return reply.code(400).send({ error: 'invalid_delivery_id' });
    const deliveryId = parsedDeliveryId.data;

    const input = parseBody(createDeliveryResultSchema, request.body, reply);
    if (!input) return;

    const replay = await database.selectFrom('delivery_results').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (replay) return reply.code(200).send({ replayed: true, result: replay });

    const delivery = await database.selectFrom('harvest_deliveries').selectAll()
      .where('id', '=', deliveryId)
      .where('workspace_id', '=', context.workspaceId)
      .executeTakeFirst();
    if (!delivery) return reply.code(404).send({ error: 'delivery_not_found' });

    const allocations = await database.selectFrom('harvest_delivery_fields')
      .selectAll()
      .where('delivery_id', '=', deliveryId)
      .execute();

    const saved = await database.transaction().execute(async (trx) => {
      const previous = await trx.selectFrom('delivery_results').selectAll()
        .where('delivery_id', '=', deliveryId)
        .where('status', '=', 'confirmed')
        .executeTakeFirst();

      if (previous) {
        await trx.updateTable('delivery_results')
          .set({ status: 'superseded' })
          .where('id', '=', previous.id)
          .execute();
      }

      const id = input.entity_id ?? randomUUID();
      const result = await trx.insertInto('delivery_results').values({
        id,
        workspace_id: context.workspaceId,
        delivery_id: deliveryId,
        result_date: input.result_date,
        yield_percent: input.yield_percent,
        moisture_percent: input.moisture_percent ?? null,
        acidity_percent: input.acidity_percent ?? null,
        status: 'confirmed',
        supersedes_id: previous?.id ?? null,
        client_operation_id: input.client_operation_id,
        created_by: context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const timelineIds: string[] = [];
      for (const allocation of allocations) {
        const projections = await writeDomainEffects(trx, {
          workspaceId: context.workspaceId,
          fieldId: allocation.field_id,
          campaignId: delivery.campaign_id,
          domainType: 'harvest_result',
          domainRecordId: id,
          occurredAt: `${input.result_date}T12:00:00.000Z`,
          title: 'Rendimiento de cosecha',
          summary: `${Number(input.yield_percent).toLocaleString('es-ES')} % · entrega ${Number(allocation.kg).toLocaleString('es-ES')} kg`,
          iconKey: 'harvest_result',
        });
        timelineIds.push(projections.timelineId);
      }

      return { result, superseded_result_id: previous?.id ?? null, timelineIds };
    });

    return reply.code(201).send({ replayed: false, ...saved });
  });

  app.get('/api/v1/fields/:fieldId/harvest-summary', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const fieldId = parsedFieldId.data;
    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const query = request.query as { campaignId?: string };
    let campaignId: string | null = null;
    if (query.campaignId) {
      const parsedCampaign = uuidSchema.safeParse(query.campaignId);
      if (!parsedCampaign.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
      campaignId = parsedCampaign.data;
    } else {
      const active = await database.selectFrom('campaigns').select('id')
        .where('workspace_id', '=', context.workspaceId)
        .where('status', '=', 'active')
        .orderBy('start_date', 'desc')
        .executeTakeFirst();
      campaignId = active?.id ?? null;
    }

    let builder = database.selectFrom('harvest_delivery_fields as hdf')
      .innerJoin('harvest_deliveries as hd', 'hd.id', 'hdf.delivery_id')
      .leftJoin('delivery_results as dr', (join) => join
        .onRef('dr.delivery_id', '=', 'hd.id')
        .on('dr.status', '=', 'confirmed'))
      .select([
        'hd.id as delivery_id',
        'hd.delivery_at',
        'hd.cooperative_or_mill',
        'hd.ticket_number',
        'hdf.kg as field_kg',
        'dr.yield_percent',
        'dr.result_date',
      ])
      .where('hdf.field_id', '=', fieldId)
      .where('hd.workspace_id', '=', context.workspaceId);

    if (campaignId) builder = builder.where('hd.campaign_id', '=', campaignId);
    const rows = await builder.orderBy('hd.delivery_at', 'desc').execute();

    let totalKg = 0;
    let yieldKg = 0;
    let weightedNumerator = 0;
    let pendingResults = 0;
    const deliveries = rows.map((row) => {
      const kg = Number(row.field_kg);
      const yieldPercent = row.yield_percent == null ? null : Number(row.yield_percent);
      totalKg += kg;
      if (yieldPercent == null) pendingResults += 1;
      else {
        yieldKg += kg;
        weightedNumerator += kg * yieldPercent;
      }
      return {
        delivery_id: row.delivery_id,
        delivery_at: row.delivery_at,
        cooperative_or_mill: row.cooperative_or_mill,
        ticket_number: row.ticket_number,
        kg,
        yield_percent: yieldPercent,
        result_date: row.result_date,
      };
    });

    return {
      field: { id: fieldId, name: field.name },
      campaign_id: campaignId,
      total_kg: totalKg,
      weighted_yield_percent: yieldKg > 0 ? weightedNumerator / yieldKg : null,
      kg_with_result: yieldKg,
      pending_results: pendingResults,
      deliveries,
    };
  });
}
