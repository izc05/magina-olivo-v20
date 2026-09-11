import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerProfessionalRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/summary', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const totals = await sql<{
      work_count: number;
      customer_count: number;
      direct_cost_eur: number;
      charged_eur: number;
      collected_eur: number;
      pending_eur: number;
    }>`
      WITH work_costs AS (
        SELECT wr.id,
               COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) +
               COALESCE((SELECT SUM(COALESCE(wrsc.cost_eur, 0)) FROM work_resources wrsc WHERE wrsc.work_id = wr.id), 0) AS direct_cost_eur
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid
          AND wr.performed_for = 'third-party'
      )
      SELECT
        COUNT(wr.id)::int AS work_count,
        COUNT(DISTINCT wr.customer_party_id)::int AS customer_count,
        COALESCE(SUM(wc.direct_cost_eur), 0)::double precision AS direct_cost_eur,
        COALESCE(SUM(COALESCE(wr.charge_eur, 0)), 0)::double precision AS charged_eur,
        COALESCE(SUM(COALESCE(wr.collected_eur, 0)), 0)::double precision AS collected_eur,
        COALESCE(SUM(GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE(wr.collected_eur, 0), 0)), 0)::double precision AS pending_eur
      FROM work_records wr
      JOIN work_costs wc ON wc.id = wr.id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
    `.execute(database);

    const recent = await sql<{
      id: string;
      occurred_on: string;
      title: string;
      customer_id: string | null;
      customer_name: string | null;
      site_name: string | null;
      charge_eur: number | null;
      collected_eur: number | null;
      payment_status: string;
      direct_cost_eur: number;
      accrued_margin_eur: number;
    }>`
      SELECT
        wr.id,
        wr.occurred_on::text,
        wr.title,
        wr.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        cs.name AS site_name,
        wr.charge_eur::double precision,
        wr.collected_eur::double precision,
        wr.payment_status,
        (
          COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) +
          COALESCE((SELECT SUM(COALESCE(wrsc.cost_eur, 0)) FROM work_resources wrsc WHERE wrsc.work_id = wr.id), 0)
        )::double precision AS direct_cost_eur,
        (
          COALESCE(wr.charge_eur, 0) -
          COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) -
          COALESCE((SELECT SUM(COALESCE(wrsc.cost_eur, 0)) FROM work_resources wrsc WHERE wrsc.work_id = wr.id), 0)
        )::double precision AS accrued_margin_eur
      FROM work_records wr
      LEFT JOIN parties p ON p.id = wr.customer_party_id
      LEFT JOIN customer_sites cs ON cs.id = wr.customer_site_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
      LIMIT 50
    `.execute(database);

    const customers = await sql<{
      customer_id: string;
      customer_name: string;
      work_count: number;
      direct_cost_eur: number;
      charged_eur: number;
      collected_eur: number;
      pending_eur: number;
      accrued_margin_eur: number;
    }>`
      WITH work_costs AS (
        SELECT
          wr.id,
          wr.customer_party_id,
          COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) +
          COALESCE((SELECT SUM(COALESCE(wrsc.cost_eur, 0)) FROM work_resources wrsc WHERE wrsc.work_id = wr.id), 0) AS direct_cost_eur
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid
          AND wr.performed_for = 'third-party'
          AND wr.customer_party_id IS NOT NULL
      )
      SELECT
        p.id AS customer_id,
        p.display_name AS customer_name,
        COUNT(wr.id)::int AS work_count,
        COALESCE(SUM(wc.direct_cost_eur), 0)::double precision AS direct_cost_eur,
        COALESCE(SUM(COALESCE(wr.charge_eur, 0)), 0)::double precision AS charged_eur,
        COALESCE(SUM(COALESCE(wr.collected_eur, 0)), 0)::double precision AS collected_eur,
        COALESCE(SUM(GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE(wr.collected_eur, 0), 0)), 0)::double precision AS pending_eur,
        COALESCE(SUM(COALESCE(wr.charge_eur, 0) - wc.direct_cost_eur), 0)::double precision AS accrued_margin_eur
      FROM work_records wr
      JOIN work_costs wc ON wc.id = wr.id
      JOIN parties p ON p.id = wr.customer_party_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
      GROUP BY p.id, p.display_name
      ORDER BY pending_eur DESC, charged_eur DESC, p.display_name
    `.execute(database);

    const row = totals.rows[0] ?? {
      work_count: 0,
      customer_count: 0,
      direct_cost_eur: 0,
      charged_eur: 0,
      collected_eur: 0,
      pending_eur: 0,
    };

    return {
      summary: {
        ...row,
        accrued_margin_eur: row.charged_eur - row.direct_cost_eur,
        collected_less_direct_costs_eur: row.collected_eur - row.direct_cost_eur,
      },
      customers: customers.rows,
      recent_work: recent.rows,
      semantics: {
        accrued_margin: 'amount charged minus direct work costs',
        collected_less_direct_costs: 'amount collected minus direct work costs; not cash flow because expense payments are not modeled',
        customer_pending: 'charged amount minus collected amount, never below zero',
      },
    };
  });
}
