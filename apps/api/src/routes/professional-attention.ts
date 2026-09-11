import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerProfessionalAttentionRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/attention', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { limit?: string };
    const limit = Math.min(Math.max(Number(query.limit ?? 8) || 8, 1), 50);

    const overdueInvoices = await sql<{
      id: string;
      invoice_number: string | null;
      customer_id: string;
      customer_name: string;
      due_on: string;
      total_eur: number | string;
      collected_eur: number | string;
      pending_eur: number | string;
      overdue_days: number;
    }>`
      SELECT
        pi.id,
        pi.invoice_number,
        pi.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        pi.due_on::text,
        pi.total_eur::double precision,
        COALESCE((
          SELECT SUM(wc.amount_eur)
          FROM professional_invoice_works piw
          JOIN work_collections wc ON wc.work_id = piw.work_id
          WHERE piw.invoice_id = pi.id
        ), 0)::double precision AS collected_eur,
        GREATEST(
          pi.total_eur - COALESCE((
            SELECT SUM(wc.amount_eur)
            FROM professional_invoice_works piw
            JOIN work_collections wc ON wc.work_id = piw.work_id
            WHERE piw.invoice_id = pi.id
          ), 0),
          0
        )::double precision AS pending_eur,
        GREATEST((CURRENT_DATE - pi.due_on), 0)::int AS overdue_days
      FROM professional_invoices pi
      JOIN parties p ON p.id = pi.customer_party_id
      WHERE pi.workspace_id = ${context.workspaceId}::uuid
        AND pi.status = 'issued'
        AND pi.due_on IS NOT NULL
        AND pi.due_on < CURRENT_DATE
        AND pi.total_eur > COALESCE((
          SELECT SUM(wc.amount_eur)
          FROM professional_invoice_works piw
          JOIN work_collections wc ON wc.work_id = piw.work_id
          WHERE piw.invoice_id = pi.id
        ), 0)
      ORDER BY overdue_days DESC, pending_eur DESC
      LIMIT ${limit}
    `.execute(database);

    const unbilledWorks = await sql<{
      id: string;
      occurred_on: string;
      title: string;
      customer_id: string;
      customer_name: string;
      charge_eur: number | string;
      age_days: number;
    }>`
      SELECT
        wr.id,
        wr.occurred_on::text,
        wr.title,
        wr.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        COALESCE(wr.charge_eur, 0)::double precision AS charge_eur,
        GREATEST((CURRENT_DATE - wr.occurred_on), 0)::int AS age_days
      FROM work_records wr
      JOIN parties p ON p.id = wr.customer_party_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
        AND COALESCE(wr.charge_eur, 0) > 0
        AND NOT EXISTS (
          SELECT 1
          FROM professional_invoice_works piw
          JOIN professional_invoices pi ON pi.id = piw.invoice_id
          WHERE piw.work_id = wr.id AND pi.status <> 'void'
        )
      ORDER BY age_days DESC, wr.charge_eur DESC
      LIMIT ${limit}
    `.execute(database);

    const agedCustomers = await sql<{
      customer_id: string;
      customer_name: string;
      pending_eur: number | string;
      oldest_unpaid_on: string;
      age_days: number;
      work_count: number;
    }>`
      WITH receivables AS (
        SELECT
          wr.id,
          wr.customer_party_id,
          wr.occurred_on,
          GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE((
            SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id
          ), 0), 0)::double precision AS pending_eur
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid
          AND wr.performed_for = 'third-party'
      )
      SELECT
        r.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        SUM(r.pending_eur)::double precision AS pending_eur,
        MIN(r.occurred_on)::text AS oldest_unpaid_on,
        GREATEST((CURRENT_DATE - MIN(r.occurred_on)), 0)::int AS age_days,
        COUNT(*) FILTER (WHERE r.pending_eur > 0)::int AS work_count
      FROM receivables r
      JOIN parties p ON p.id = r.customer_party_id
      WHERE r.pending_eur > 0
      GROUP BY r.customer_party_id, p.display_name
      HAVING CURRENT_DATE - MIN(r.occurred_on) >= 30
      ORDER BY age_days DESC, pending_eur DESC
      LIMIT ${limit}
    `.execute(database);

    const expiredQuotes = await sql<{
      id: string;
      quote_number: string | null;
      title: string;
      customer_id: string;
      customer_name: string;
      valid_until: string;
      total_eur: number | string;
      overdue_days: number;
    }>`
      SELECT
        pq.id,
        pq.quote_number,
        pq.title,
        pq.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        pq.valid_until::text,
        pq.total_eur::double precision,
        GREATEST((CURRENT_DATE - pq.valid_until), 0)::int AS overdue_days
      FROM professional_quotes pq
      JOIN parties p ON p.id = pq.customer_party_id
      WHERE pq.workspace_id = ${context.workspaceId}::uuid
        AND pq.status IN ('sent', 'expired')
        AND pq.valid_until IS NOT NULL
        AND pq.valid_until < CURRENT_DATE
      ORDER BY overdue_days DESC, pq.total_eur DESC
      LIMIT ${limit}
    `.execute(database);

    const quoteFollowups = await sql<{
      id: string;
      quote_number: string | null;
      title: string;
      customer_id: string;
      customer_name: string;
      issued_on: string | null;
      valid_until: string | null;
      total_eur: number | string;
      age_days: number;
    }>`
      SELECT
        pq.id,
        pq.quote_number,
        pq.title,
        pq.customer_party_id AS customer_id,
        p.display_name AS customer_name,
        pq.issued_on::text,
        pq.valid_until::text,
        pq.total_eur::double precision,
        GREATEST((CURRENT_DATE - COALESCE(pq.issued_on, pq.created_at::date)), 0)::int AS age_days
      FROM professional_quotes pq
      JOIN parties p ON p.id = pq.customer_party_id
      WHERE pq.workspace_id = ${context.workspaceId}::uuid
        AND pq.status = 'sent'
        AND (pq.valid_until IS NULL OR pq.valid_until >= CURRENT_DATE)
        AND CURRENT_DATE - COALESCE(pq.issued_on, pq.created_at::date) >= 7
      ORDER BY age_days DESC, pq.total_eur DESC
      LIMIT ${limit}
    `.execute(database);

    const overdue = overdueInvoices.rows.map((item) => ({
      ...item,
      total_eur: Number(item.total_eur),
      collected_eur: Number(item.collected_eur),
      pending_eur: Number(item.pending_eur),
    }));
    const unbilled = unbilledWorks.rows.map((item) => ({ ...item, charge_eur: Number(item.charge_eur) }));
    const aged = agedCustomers.rows.map((item) => ({ ...item, pending_eur: Number(item.pending_eur) }));
    const expired = expiredQuotes.rows.map((item) => ({ ...item, total_eur: Number(item.total_eur) }));
    const followups = quoteFollowups.rows.map((item) => ({ ...item, total_eur: Number(item.total_eur) }));

    return {
      summary: {
        overdue_invoice_count: overdue.length,
        overdue_invoice_eur: overdue.reduce((sum, item) => sum + item.pending_eur, 0),
        unbilled_work_count: unbilled.length,
        unbilled_work_eur: unbilled.reduce((sum, item) => sum + item.charge_eur, 0),
        aged_customer_count: aged.length,
        aged_receivable_eur: aged.reduce((sum, item) => sum + item.pending_eur, 0),
        expired_quote_count: expired.length,
        expired_quote_eur: expired.reduce((sum, item) => sum + item.total_eur, 0),
        quote_followup_count: followups.length,
        quote_followup_eur: followups.reduce((sum, item) => sum + item.total_eur, 0),
      },
      overdue_invoices: overdue,
      unbilled_works: unbilled,
      aged_customers: aged,
      expired_quotes: expired,
      quote_followups: followups,
      semantics: {
        overdue_invoice: 'issued invoice with due_on before current date and remaining balance',
        unbilled_work: 'third-party work with charge_eur and no active invoice',
        aged_receivable: 'customer with unpaid third-party work at least 30 days old',
        expired_quote: 'sent or manually expired quote whose valid_until is before current date',
        quote_followup: 'sent quote with no decision at least 7 days after issue/creation and not yet expired',
      },
    };
  });
}
