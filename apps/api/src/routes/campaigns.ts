import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerCampaignRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/campaigns', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const campaigns = await database.selectFrom('campaigns')
      .selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .orderBy('start_date', 'desc')
      .execute();

    return { campaigns };
  });

  app.get('/api/v1/campaigns/:campaignId/summary', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsed = uuidSchema.safeParse((request.params as { campaignId?: string }).campaignId);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_campaign_id' });

    const campaign = await database.selectFrom('campaigns').selectAll()
      .where('id', '=', parsed.data)
      .where('workspace_id', '=', context.workspaceId)
      .executeTakeFirst();
    if (!campaign) return reply.code(404).send({ error: 'campaign_not_found' });

    const totals = await sql<{
      delivered_kg: number;
      kg_with_result: number;
      weighted_yield_numerator: number;
      total_cost_eur: number;
      accrued_income_eur: number;
      collected_income_eur: number;
      settlement_count: number;
      delivery_count: number;
      field_count: number;
    }>`
      WITH delivery_data AS (
        SELECT
          hd.id AS delivery_id,
          COALESCE(SUM(hdf.kg), 0)::double precision AS kg,
          MAX(CASE WHEN dr.status = 'confirmed' THEN dr.yield_percent::double precision ELSE NULL END) AS yield_percent
        FROM harvest_deliveries hd
        JOIN harvest_delivery_fields hdf ON hdf.delivery_id = hd.id
        LEFT JOIN delivery_results dr ON dr.delivery_id = hd.id AND dr.status = 'confirmed'
        WHERE hd.workspace_id = ${context.workspaceId}::uuid
          AND hd.campaign_id = ${campaign.id}::uuid
        GROUP BY hd.id
      ), settlement_data AS (
        SELECT
          hs.id,
          hs.net_eur::double precision AS net_eur,
          COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0)::double precision AS collected_eur
        FROM harvest_settlements hs
        WHERE hs.workspace_id = ${context.workspaceId}::uuid
          AND hs.campaign_id = ${campaign.id}::uuid
          AND hs.status = 'confirmed'
      )
      SELECT
        COALESCE((SELECT SUM(kg) FROM delivery_data), 0)::double precision AS delivered_kg,
        COALESCE((SELECT SUM(kg) FROM delivery_data WHERE yield_percent IS NOT NULL), 0)::double precision AS kg_with_result,
        COALESCE((SELECT SUM(kg * yield_percent) FROM delivery_data WHERE yield_percent IS NOT NULL), 0)::double precision AS weighted_yield_numerator,
        COALESCE((SELECT SUM(amount_eur) FROM cost_ledger_projection WHERE workspace_id = ${context.workspaceId}::uuid AND campaign_id = ${campaign.id}::uuid), 0)::double precision AS total_cost_eur,
        COALESCE((SELECT SUM(net_eur) FROM settlement_data), 0)::double precision AS accrued_income_eur,
        COALESCE((SELECT SUM(collected_eur) FROM settlement_data), 0)::double precision AS collected_income_eur,
        COALESCE((SELECT COUNT(*) FROM settlement_data), 0)::int AS settlement_count,
        COALESCE((SELECT COUNT(*) FROM delivery_data), 0)::int AS delivery_count,
        COALESCE((SELECT COUNT(DISTINCT hdf.field_id) FROM harvest_delivery_fields hdf JOIN harvest_deliveries hd ON hd.id = hdf.delivery_id WHERE hd.workspace_id = ${context.workspaceId}::uuid AND hd.campaign_id = ${campaign.id}::uuid), 0)::int AS field_count
    `.execute(database);

    const fieldRows = await sql<{
      field_id: string;
      field_name: string;
      delivered_kg: number;
      total_cost_eur: number;
      accrued_income_eur: number;
      collected_income_eur: number;
    }>`
      WITH field_delivery AS (
        SELECT hdf.field_id, COALESCE(SUM(hdf.kg), 0)::double precision AS delivered_kg
        FROM harvest_delivery_fields hdf
        JOIN harvest_deliveries hd ON hd.id = hdf.delivery_id
        WHERE hd.workspace_id = ${context.workspaceId}::uuid
          AND hd.campaign_id = ${campaign.id}::uuid
        GROUP BY hdf.field_id
      ), settlement_field AS (
        SELECT
          hs.id AS settlement_id,
          f.id AS field_id,
          hs.net_eur::double precision AS net_eur,
          COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0)::double precision AS collected_eur,
          COALESCE(SUM(hdf_all.kg), 0)::double precision AS total_kg,
          COALESCE(SUM(CASE WHEN hdf_all.field_id = f.id THEN hdf_all.kg ELSE 0 END), 0)::double precision AS field_kg
        FROM harvest_settlements hs
        JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
        JOIN harvest_delivery_fields hdf_all ON hdf_all.delivery_id = hsd.delivery_id
        CROSS JOIN fields f
        WHERE hs.workspace_id = ${context.workspaceId}::uuid
          AND hs.campaign_id = ${campaign.id}::uuid
          AND hs.status = 'confirmed'
          AND f.workspace_id = ${context.workspaceId}::uuid
          AND f.status = 'active'
        GROUP BY hs.id, f.id
      ), settlement_by_field AS (
        SELECT
          field_id,
          COALESCE(SUM(CASE WHEN total_kg > 0 THEN net_eur * (field_kg / total_kg) ELSE 0 END), 0)::double precision AS accrued_income_eur,
          COALESCE(SUM(CASE WHEN total_kg > 0 THEN collected_eur * (field_kg / total_kg) ELSE 0 END), 0)::double precision AS collected_income_eur
        FROM settlement_field
        GROUP BY field_id
      ), costs AS (
        SELECT field_id, COALESCE(SUM(amount_eur), 0)::double precision AS total_cost_eur
        FROM cost_ledger_projection
        WHERE workspace_id = ${context.workspaceId}::uuid
          AND campaign_id = ${campaign.id}::uuid
        GROUP BY field_id
      )
      SELECT
        f.id AS field_id,
        f.name AS field_name,
        COALESCE(fd.delivered_kg, 0)::double precision AS delivered_kg,
        COALESCE(c.total_cost_eur, 0)::double precision AS total_cost_eur,
        COALESCE(sbf.accrued_income_eur, 0)::double precision AS accrued_income_eur,
        COALESCE(sbf.collected_income_eur, 0)::double precision AS collected_income_eur
      FROM fields f
      LEFT JOIN field_delivery fd ON fd.field_id = f.id
      LEFT JOIN costs c ON c.field_id = f.id
      LEFT JOIN settlement_by_field sbf ON sbf.field_id = f.id
      WHERE f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
        AND (COALESCE(fd.delivered_kg, 0) > 0 OR COALESCE(c.total_cost_eur, 0) > 0 OR COALESCE(sbf.accrued_income_eur, 0) > 0)
      ORDER BY f.name
    `.execute(database);

    const row = totals.rows[0];
    const deliveredKg = row?.delivered_kg ?? 0;
    const kgWithResult = row?.kg_with_result ?? 0;
    const totalCost = row?.total_cost_eur ?? 0;
    const accrued = row?.accrued_income_eur ?? 0;
    const collected = row?.collected_income_eur ?? 0;

    return {
      campaign,
      delivered_kg: deliveredKg,
      weighted_yield_percent: kgWithResult > 0 ? (row?.weighted_yield_numerator ?? 0) / kgWithResult : null,
      kg_with_result: kgWithResult,
      pending_result_kg: Math.max(deliveredKg - kgWithResult, 0),
      total_cost_eur: totalCost,
      accrued_income_eur: accrued,
      collected_income_eur: collected,
      pending_collection_eur: Math.max(accrued - collected, 0),
      accrued_margin_eur: accrued - totalCost,
      cash_margin_eur: collected - totalCost,
      cost_per_delivered_kg_eur: deliveredKg > 0 ? totalCost / deliveredKg : null,
      delivery_count: row?.delivery_count ?? 0,
      settlement_count: row?.settlement_count ?? 0,
      field_count: row?.field_count ?? 0,
      fields: fieldRows.rows.map((field) => ({
        ...field,
        pending_collection_eur: Math.max(field.accrued_income_eur - field.collected_income_eur, 0),
        accrued_margin_eur: field.accrued_income_eur - field.total_cost_eur,
        cash_margin_eur: field.collected_income_eur - field.total_cost_eur,
        cost_per_delivered_kg_eur: field.delivered_kg > 0 ? field.total_cost_eur / field.delivered_kg : null,
      })),
      attribution_status: 'harvest_income_allocated_by_linked_delivery_kg_share',
    };
  });
}
