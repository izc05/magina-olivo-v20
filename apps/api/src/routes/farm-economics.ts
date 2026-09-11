import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, requireContext, requireDatabase } from '../http/helpers.js';

export function registerFarmEconomicsRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/fields/:fieldId/economics-summary', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsedFieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
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

    let campaignCostFilter = sql``;
    let campaignSettlementFilter = sql``;
    if (campaignId) {
      campaignCostFilter = sql`AND clp.campaign_id = ${campaignId}::uuid`;
      campaignSettlementFilter = sql`AND hs.campaign_id = ${campaignId}::uuid`;
    }

    const costs = await sql<{ total_cost_eur: number }>`
      SELECT COALESCE(SUM(clp.amount_eur), 0)::double precision AS total_cost_eur
      FROM cost_ledger_projection clp
      WHERE clp.workspace_id = ${context.workspaceId}::uuid
        AND clp.field_id = ${fieldId}::uuid
        ${campaignCostFilter}
    `.execute(database);

    const harvest = await sql<{
      accrued_eur: number;
      collected_eur: number;
      delivered_kg: number;
    }>`
      WITH settlement_field_share AS (
        SELECT
          hs.id AS settlement_id,
          hs.net_eur::double precision AS net_eur,
          COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0)::double precision AS collected_eur,
          COALESCE(SUM(hdf_all.kg), 0)::double precision AS total_linked_kg,
          COALESCE(SUM(CASE WHEN hdf_all.field_id = ${fieldId}::uuid THEN hdf_all.kg ELSE 0 END), 0)::double precision AS field_linked_kg
        FROM harvest_settlements hs
        JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
        JOIN harvest_delivery_fields hdf_all ON hdf_all.delivery_id = hsd.delivery_id
        WHERE hs.workspace_id = ${context.workspaceId}::uuid
          AND hs.status = 'confirmed'
          ${campaignSettlementFilter}
        GROUP BY hs.id
      ), delivery_total AS (
        SELECT COALESCE(SUM(hdf.kg), 0)::double precision AS delivered_kg
        FROM harvest_delivery_fields hdf
        JOIN harvest_deliveries hd ON hd.id = hdf.delivery_id
        WHERE hdf.field_id = ${fieldId}::uuid
          AND hd.workspace_id = ${context.workspaceId}::uuid
          ${campaignId ? sql`AND hd.campaign_id = ${campaignId}::uuid` : sql``}
      )
      SELECT
        COALESCE(SUM(CASE WHEN s.total_linked_kg > 0 THEN s.net_eur * (s.field_linked_kg / s.total_linked_kg) ELSE 0 END), 0)::double precision AS accrued_eur,
        COALESCE(SUM(CASE WHEN s.total_linked_kg > 0 THEN s.collected_eur * (s.field_linked_kg / s.total_linked_kg) ELSE 0 END), 0)::double precision AS collected_eur,
        (SELECT delivered_kg FROM delivery_total)::double precision AS delivered_kg
      FROM settlement_field_share s
    `.execute(database);

    const totalCost = costs.rows[0]?.total_cost_eur ?? 0;
    const accrued = harvest.rows[0]?.accrued_eur ?? 0;
    const collected = harvest.rows[0]?.collected_eur ?? 0;
    const deliveredKg = harvest.rows[0]?.delivered_kg ?? 0;

    return {
      field: { id: field.id, name: field.name },
      campaign_id: campaignId,
      total_cost_eur: totalCost,
      accrued_income_eur: accrued,
      collected_income_eur: collected,
      pending_collection_eur: Math.max(accrued - collected, 0),
      accrued_margin_eur: accrued - totalCost,
      collected_less_registered_costs_eur: collected - totalCost,
      delivered_kg: deliveredKg,
      cost_per_delivered_kg_eur: deliveredKg > 0 ? totalCost / deliveredKg : null,
      attribution_status: 'derived_from_cost_ledger_and_harvest_delivery_share',
      semantics: {
        accrued_income: 'confirmed settlement amount attributed to the field',
        collected_income: 'collections received against attributed settlements',
        accrued_margin: 'accrued income minus registered costs',
        collected_less_registered_costs: 'collected income minus registered costs; not cash flow because expense payments are not modeled yet',
      },
    };
  });
}
