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
      const campaign = await database.selectFrom('campaigns').select('id')
        .where('id', '=', parsedCampaign.data)
        .where('workspace_id', '=', context.workspaceId)
        .executeTakeFirst();
      if (!campaign) return reply.code(404).send({ error: 'campaign_not_found' });
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
    let campaignWorkFilter = sql``;
    if (campaignId) {
      campaignCostFilter = sql`AND clp.campaign_id = ${campaignId}::uuid`;
      campaignSettlementFilter = sql`AND hs.campaign_id = ${campaignId}::uuid`;
      campaignWorkFilter = sql`AND wr.campaign_id = ${campaignId}::uuid`;
    }

    const costs = await sql<{ total_cost_eur: number }>`
      SELECT COALESCE(SUM(clp.amount_eur), 0)::double precision AS total_cost_eur
      FROM cost_ledger_projection clp
      WHERE clp.workspace_id = ${context.workspaceId}::uuid
        AND clp.field_id = ${fieldId}::uuid
        ${campaignCostFilter}
    `.execute(database);

    const workBreakdown = await sql<{
      labor_eur: number;
      machinery_eur: number;
      materials_eur: number;
      services_eur: number;
      total_work_eur: number;
    }>`
      WITH scoped_work AS (
        SELECT wr.id
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid
          AND wr.field_id = ${fieldId}::uuid
          ${campaignWorkFilter}
      ), participant_cost AS (
        SELECT COALESCE(SUM(wp.cost_eur), 0)::double precision AS labor_eur
        FROM work_participants wp
        JOIN scoped_work sw ON sw.id = wp.work_id
      ), resource_cost AS (
        SELECT
          COALESCE(SUM(wr.cost_eur) FILTER (WHERE wr.kind = 'machinery'), 0)::double precision AS machinery_eur,
          COALESCE(SUM(wr.cost_eur) FILTER (WHERE wr.kind = 'material'), 0)::double precision AS materials_eur,
          COALESCE(SUM(wr.cost_eur) FILTER (WHERE wr.kind = 'service'), 0)::double precision AS services_eur
        FROM work_resources wr
        JOIN scoped_work sw ON sw.id = wr.work_id
      )
      SELECT
        participant_cost.labor_eur,
        resource_cost.machinery_eur,
        resource_cost.materials_eur,
        resource_cost.services_eur,
        (participant_cost.labor_eur + resource_cost.machinery_eur + resource_cost.materials_eur + resource_cost.services_eur)::double precision AS total_work_eur
      FROM participant_cost CROSS JOIN resource_cost
    `.execute(database);

    const professionalWork = await sql<{
      charged_eur: number;
      collected_eur: number;
      pending_eur: number;
      direct_cost_eur: number;
    }>`
      WITH scoped AS (
        SELECT
          wr.id,
          COALESCE(wr.charge_eur, 0)::double precision AS charge_eur,
          COALESCE(wr.collected_eur, 0)::double precision AS collected_eur,
          (
            COALESCE((SELECT SUM(wp.cost_eur) FROM work_participants wp WHERE wp.work_id = wr.id), 0)
            + COALESCE((SELECT SUM(wres.cost_eur) FROM work_resources wres WHERE wres.work_id = wr.id), 0)
          )::double precision AS direct_cost_eur
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid
          AND wr.field_id = ${fieldId}::uuid
          AND wr.performed_for = 'third-party'
          ${campaignWorkFilter}
      )
      SELECT
        COALESCE(SUM(charge_eur), 0)::double precision AS charged_eur,
        COALESCE(SUM(collected_eur), 0)::double precision AS collected_eur,
        COALESCE(SUM(GREATEST(charge_eur - collected_eur, 0)), 0)::double precision AS pending_eur,
        COALESCE(SUM(direct_cost_eur), 0)::double precision AS direct_cost_eur
      FROM scoped
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
    const work = workBreakdown.rows[0] ?? { labor_eur: 0, machinery_eur: 0, materials_eur: 0, services_eur: 0, total_work_eur: 0 };
    const professional = professionalWork.rows[0] ?? { charged_eur: 0, collected_eur: 0, pending_eur: 0, direct_cost_eur: 0 };
    const productionCost = Math.max(totalCost - professional.direct_cost_eur, 0);
    const agriculturalMargin = accrued - productionCost;
    const professionalMargin = professional.charged_eur - professional.direct_cost_eur;
    const combinedAccruedMargin = accrued + professional.charged_eur - totalCost;

    return {
      field: { id: field.id, name: field.name },
      campaign_id: campaignId,
      total_cost_eur: totalCost,
      production_cost_eur: productionCost,
      professional_cost_eur: professional.direct_cost_eur,
      accrued_income_eur: accrued,
      collected_income_eur: collected,
      pending_collection_eur: Math.max(accrued - collected, 0),
      accrued_margin_eur: agriculturalMargin,
      combined_accrued_margin_eur: combinedAccruedMargin,
      collected_less_registered_costs_eur: collected + professional.collected_eur - totalCost,
      delivered_kg: deliveredKg,
      cost_per_delivered_kg_eur: deliveredKg > 0 ? productionCost / deliveredKg : null,
      work_cost_breakdown: {
        labor_eur: work.labor_eur,
        machinery_eur: work.machinery_eur,
        materials_eur: work.materials_eur,
        services_eur: work.services_eur,
        total_work_eur: work.total_work_eur,
      },
      professional_work: {
        charged_eur: professional.charged_eur,
        collected_eur: professional.collected_eur,
        pending_eur: professional.pending_eur,
        direct_cost_eur: professional.direct_cost_eur,
        accrued_margin_eur: professionalMargin,
      },
      attribution_status: 'derived_from_cost_ledger_harvest_share_and_work_scope',
      semantics: {
        total_cost: 'all canonical registered costs from the field cost ledger projection',
        production_cost: 'registered field costs excluding direct costs attributed to third-party professional work',
        professional_cost: 'participant and resource costs of third-party work on this field',
        work_cost_breakdown: 'informational decomposition of work participant and resource costs; already included in total_cost_eur',
        accrued_income: 'confirmed harvest settlement amount attributed to the field',
        collected_income: 'collections received against attributed harvest settlements',
        accrued_margin: 'harvest accrued income minus production_cost_eur only',
        combined_accrued_margin: 'harvest accrued income plus third-party charges minus all registered costs',
        collected_less_registered_costs: 'harvest collections plus professional collections minus registered costs; not cash flow because expense payments are not modeled yet',
        professional_work: 'third-party work commercial figures kept separate from harvest income',
      },
    };
  });
}
