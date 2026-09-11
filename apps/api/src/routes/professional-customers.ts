import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerProfessionalCustomerRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/customers/:customerId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = uuidSchema.safeParse((request.params as { customerId?: string }).customerId);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_customer_id' });

    const customer = await sql`
      SELECT id, display_name, legal_name, tax_id, phone, email, notes
      FROM parties
      WHERE id = ${parsed.data}::uuid
        AND workspace_id = ${context.workspaceId}::uuid
        AND active = TRUE
        AND 'customer' = ANY(roles)
    `.execute(database);
    if (!customer.rows[0]) return reply.code(404).send({ error: 'customer_not_found' });

    const works = await sql`
      SELECT wr.id, wr.occurred_on::text, wr.title, wr.charge_eur::double precision,
             wr.payment_status, wr.professional_quote_id, cs.name AS site_name,
             COALESCE((SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id), 0)::double precision AS collected_eur,
             (
               COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) +
               COALESCE((SELECT SUM(COALESCE(wres.cost_eur, 0)) FROM work_resources wres WHERE wres.work_id = wr.id), 0)
             )::double precision AS direct_cost_eur,
             pi.id AS invoice_id,
             pi.invoice_number
      FROM work_records wr
      LEFT JOIN customer_sites cs ON cs.id = wr.customer_site_id
      LEFT JOIN professional_invoice_works piw ON piw.work_id = wr.id
      LEFT JOIN professional_invoices pi ON pi.id = piw.invoice_id AND pi.status <> 'void'
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.customer_party_id = ${parsed.data}::uuid
        AND wr.performed_for = 'third-party'
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
    `.execute(database);

    const invoices = await sql`
      SELECT pi.id, pi.invoice_number, pi.issued_on::text, pi.due_on::text, pi.status,
             pi.subtotal_eur::double precision, pi.tax_eur::double precision, pi.total_eur::double precision,
             COALESCE((
               SELECT SUM(wc.amount_eur)
               FROM professional_invoice_works piw
               JOIN work_collections wc ON wc.work_id = piw.work_id
               WHERE piw.invoice_id = pi.id
             ), 0)::double precision AS collected_eur,
             COALESCE((SELECT COUNT(*) FROM professional_invoice_works piw WHERE piw.invoice_id = pi.id), 0)::int AS work_count
      FROM professional_invoices pi
      WHERE pi.workspace_id = ${context.workspaceId}::uuid
        AND pi.customer_party_id = ${parsed.data}::uuid
      ORDER BY pi.issued_on DESC NULLS LAST, pi.created_at DESC
    `.execute(database);

    const quotes = await sql`
      SELECT pq.id, pq.quote_number, pq.title, pq.issued_on::text, pq.valid_until::text, pq.status,
             pq.subtotal_eur::double precision, pq.tax_eur::double precision, pq.total_eur::double precision,
             pq.accepted_at, pq.rejected_at, pq.converted_at,
             cs.name AS site_name,
             wr.id AS work_id, wr.title AS work_title, wr.charge_eur::double precision AS work_charge_eur,
             (
               COALESCE((SELECT SUM(COALESCE(wp.cost_eur, 0)) FROM work_participants wp WHERE wp.work_id = wr.id), 0) +
               COALESCE((SELECT SUM(COALESCE(wres.cost_eur, 0)) FROM work_resources wres WHERE wres.work_id = wr.id), 0)
             )::double precision AS real_cost_eur,
             pi.id AS invoice_id, pi.invoice_number,
             COALESCE(piw.amount_eur, 0)::double precision AS invoiced_eur
      FROM professional_quotes pq
      LEFT JOIN customer_sites cs ON cs.id = pq.customer_site_id
      LEFT JOIN work_records wr ON wr.professional_quote_id = pq.id
      LEFT JOIN professional_invoice_works piw ON piw.work_id = wr.id
      LEFT JOIN professional_invoices pi ON pi.id = piw.invoice_id AND pi.status <> 'void'
      WHERE pq.workspace_id = ${context.workspaceId}::uuid
        AND pq.customer_party_id = ${parsed.data}::uuid
      ORDER BY pq.created_at DESC
    `.execute(database);

    const invoiceIds = invoices.rows.map((row) => (row as { id: string }).id);
    const quoteIds = quotes.rows.map((row) => (row as { id: string }).id);
    const documents = (invoiceIds.length || quoteIds.length) ? await sql`
      SELECT d.id, d.kind, d.title, d.created_at,
             al.domain_type,
             al.domain_record_id,
             al.relation
      FROM attachment_links al
      JOIN documents d ON d.id = al.document_id
      WHERE al.workspace_id = ${context.workspaceId}::uuid
        AND (
          (al.domain_type = 'professional_invoice' AND al.domain_record_id = ANY(${invoiceIds}::uuid[]))
          OR
          (al.domain_type = 'professional_quote' AND al.domain_record_id = ANY(${quoteIds}::uuid[]))
        )
        AND d.status = 'active'
      ORDER BY d.created_at DESC
    `.execute(database) : { rows: [] as unknown[] };

    const collections = await sql`
      SELECT wc.id, wc.collected_on::text, wc.amount_eur::double precision, wc.method, wc.reference,
             wr.id AS work_id, wr.title AS work_title
      FROM work_collections wc
      JOIN work_records wr ON wr.id = wc.work_id
      WHERE wc.workspace_id = ${context.workspaceId}::uuid
        AND wr.customer_party_id = ${parsed.data}::uuid
      ORDER BY wc.collected_on DESC, wc.created_at DESC
      LIMIT 200
    `.execute(database);

    const normalizedWorks = works.rows.map((row) => {
      const value = row as Record<string, unknown> & { charge_eur: number | null; collected_eur: number; direct_cost_eur: number };
      const charge = Number(value.charge_eur ?? 0);
      const collected = Number(value.collected_eur ?? 0);
      const cost = Number(value.direct_cost_eur ?? 0);
      return { ...value, pending_eur: Math.max(charge - collected, 0), accrued_margin_eur: charge - cost };
    });
    const normalizedInvoices = invoices.rows.map((row) => {
      const value = row as Record<string, unknown> & { total_eur: number; collected_eur: number };
      return { ...value, pending_eur: Math.max(Number(value.total_eur) - Number(value.collected_eur), 0) };
    });
    const normalizedQuotes = quotes.rows.map((row) => {
      const value = row as Record<string, unknown> & { status: string; total_eur: number; real_cost_eur: number; invoiced_eur: number };
      const realCost = Number(value.real_cost_eur ?? 0);
      const invoiced = Number(value.invoiced_eur ?? 0);
      return { ...value, invoiced_less_real_cost_eur: invoiced - realCost };
    });

    const totals = normalizedWorks.reduce((acc, item) => ({
      charged_eur: acc.charged_eur + Number(item.charge_eur ?? 0),
      collected_eur: acc.collected_eur + Number(item.collected_eur ?? 0),
      pending_eur: acc.pending_eur + Number(item.pending_eur ?? 0),
      direct_cost_eur: acc.direct_cost_eur + Number(item.direct_cost_eur ?? 0),
      accrued_margin_eur: acc.accrued_margin_eur + Number(item.accrued_margin_eur ?? 0),
    }), { charged_eur: 0, collected_eur: 0, pending_eur: 0, direct_cost_eur: 0, accrued_margin_eur: 0 });

    const acceptedStatuses = new Set(['accepted', 'converted']);
    const acceptedQuotes = normalizedQuotes.filter((item) => acceptedStatuses.has(String(item.status)));
    const rejectedQuotes = normalizedQuotes.filter((item) => item.status === 'rejected');
    const decidedCount = acceptedQuotes.length + rejectedQuotes.length;
    const convertedQuotes = normalizedQuotes.filter((item) => item.status === 'converted');
    const quoteSummary = {
      quote_count: normalizedQuotes.length,
      accepted_quote_count: acceptedQuotes.length,
      rejected_quote_count: rejectedQuotes.length,
      conversion_count: convertedQuotes.length,
      acceptance_rate_percent: decidedCount ? (acceptedQuotes.length / decidedCount) * 100 : null,
      accepted_quoted_eur: acceptedQuotes.reduce((sum, item) => sum + Number(item.total_eur ?? 0), 0),
      converted_quoted_eur: convertedQuotes.reduce((sum, item) => sum + Number(item.total_eur ?? 0), 0),
      converted_real_cost_eur: convertedQuotes.reduce((sum, item) => sum + Number(item.real_cost_eur ?? 0), 0),
      converted_invoiced_eur: convertedQuotes.reduce((sum, item) => sum + Number(item.invoiced_eur ?? 0), 0),
      converted_invoiced_less_real_cost_eur: convertedQuotes.reduce((sum, item) => sum + Number(item.invoiced_less_real_cost_eur ?? 0), 0),
    };

    return {
      customer: customer.rows[0],
      summary: { work_count: normalizedWorks.length, invoice_count: normalizedInvoices.length, ...totals },
      quote_summary: quoteSummary,
      works: normalizedWorks,
      invoices: normalizedInvoices,
      quotes: normalizedQuotes,
      collections: collections.rows,
      documents: documents.rows,
    };
  });
}
