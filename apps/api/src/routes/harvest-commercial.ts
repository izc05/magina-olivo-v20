import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { createHarvestCollectionSchema, createHarvestSettlementSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerHarvestCommercialRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/harvest-settlements', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createHarvestSettlementSchema, request.body, reply);
    if (!input) return;

    const replay = await sql`SELECT * FROM harvest_settlements WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, settlement: replay.rows[0] });

    for (const deliveryId of input.delivery_ids) {
      const delivery = await sql<{ id: string }>`SELECT id FROM harvest_deliveries WHERE id = ${deliveryId}::uuid AND workspace_id = ${context.workspaceId}::uuid`.execute(database);
      if (!delivery.rows[0]) return reply.code(404).send({ error: 'delivery_not_found', delivery_id: deliveryId });
    }

    if (input.campaign_id) {
      const campaign = await database.selectFrom('campaigns').select('id').where('id', '=', input.campaign_id).where('workspace_id', '=', context.workspaceId).executeTakeFirst();
      if (!campaign) return reply.code(404).send({ error: 'campaign_not_found' });
    }

    const id = input.entity_id ?? randomUUID();
    const saved = await database.transaction().execute(async (trx) => {
      const settlement = await sql`
        INSERT INTO harvest_settlements (
          id, workspace_id, campaign_id, client_operation_id, counterparty_name, settlement_number,
          settled_on, basis, unit_price_eur, gross_eur, deductions_eur, net_eur, notes, created_by
        ) VALUES (
          ${id}::uuid, ${context.workspaceId}::uuid, ${input.campaign_id ?? null}::uuid, ${input.client_operation_id}::uuid,
          ${input.counterparty_name ?? null}, ${input.settlement_number ?? null}, ${input.settled_on}::date, ${input.basis},
          ${input.unit_price_eur ?? null}, ${input.gross_eur}, ${input.deductions_eur}, ${input.net_eur}, ${input.notes ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);

      for (const deliveryId of input.delivery_ids) {
        await sql`INSERT INTO harvest_settlement_deliveries (settlement_id, delivery_id) VALUES (${id}::uuid, ${deliveryId}::uuid)`.execute(trx);
      }

      return settlement.rows[0];
    });

    return reply.code(201).send({ replayed: false, settlement: saved });
  });

  app.post('/api/v1/harvest-settlements/:settlementId/collections', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsed = uuidSchema.safeParse((request.params as { settlementId?: string }).settlementId);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_settlement_id' });
    const input = parseBody(createHarvestCollectionSchema, request.body, reply);
    if (!input) return;

    const settlement = await sql<{ id: string; net_eur: number }>`
      SELECT id, net_eur::double precision FROM harvest_settlements
      WHERE id = ${parsed.data}::uuid AND workspace_id = ${context.workspaceId}::uuid AND status = 'confirmed'
    `.execute(database);
    const row = settlement.rows[0];
    if (!row) return reply.code(404).send({ error: 'settlement_not_found' });

    const replay = await sql`SELECT * FROM harvest_collections WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, collection: replay.rows[0] });

    const previous = await sql<{ total: number }>`SELECT COALESCE(SUM(amount_eur), 0)::double precision AS total FROM harvest_collections WHERE settlement_id = ${parsed.data}::uuid`.execute(database);
    const alreadyCollected = previous.rows[0]?.total ?? 0;
    if (alreadyCollected + input.amount_eur > row.net_eur + 0.01) {
      return reply.code(400).send({ error: 'collection_exceeds_settlement_net', net_eur: row.net_eur, already_collected_eur: alreadyCollected });
    }

    const id = input.entity_id ?? randomUUID();
    const collection = await sql`
      INSERT INTO harvest_collections (id, workspace_id, settlement_id, client_operation_id, collected_on, amount_eur, method, reference, notes, created_by)
      VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${parsed.data}::uuid, ${input.client_operation_id}::uuid, ${input.collected_on}::date, ${input.amount_eur}, ${input.method ?? null}, ${input.reference ?? null}, ${input.notes ?? null}, ${context.userId}::uuid)
      RETURNING *
    `.execute(database);

    return reply.code(201).send({ replayed: false, collection: collection.rows[0] });
  });

  app.get('/api/v1/harvest-settlements', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const query = request.query as { campaignId?: string };
    let campaignFilter = sql``;
    if (query.campaignId) {
      const parsed = uuidSchema.safeParse(query.campaignId);
      if (!parsed.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
      campaignFilter = sql`AND hs.campaign_id = ${parsed.data}::uuid`;
    }

    const result = await sql`
      SELECT hs.*,
             COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0)::double precision AS collected_eur,
             GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0)::double precision AS pending_eur,
             (SELECT COUNT(*)::int FROM harvest_settlement_deliveries hsd WHERE hsd.settlement_id = hs.id) AS delivery_count
      FROM harvest_settlements hs
      WHERE hs.workspace_id = ${context.workspaceId}::uuid
        AND hs.status = 'confirmed'
        ${campaignFilter}
      ORDER BY hs.settled_on DESC, hs.created_at DESC
    `.execute(database);

    return { settlements: result.rows };
  });
}
