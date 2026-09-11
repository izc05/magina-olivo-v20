import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

type InvoicePrintRow = {
  id: string;
  invoice_number: string | null;
  issued_on: string | null;
  due_on: string | null;
  status: string;
  subtotal_eur: number | string;
  tax_eur: number | string;
  total_eur: number | string;
  notes: string | null;
  issuer_snapshot_json: Record<string, unknown> | null;
  customer_snapshot_json: Record<string, unknown> | null;
  customer_id: string;
  customer_name: string;
  customer_legal_name: string | null;
  customer_tax_id: string | null;
  customer_phone: string | null;
  customer_email: string | null;
};

type InvoiceLineRow = {
  work_id: string;
  occurred_on: string;
  title: string;
  site_name: string | null;
  amount_eur: number | string;
};

type QuotePrintRow = {
  id: string;
  quote_number: string | null;
  title: string;
  issued_on: string | null;
  valid_until: string | null;
  status: string;
  subtotal_eur: number | string;
  tax_eur: number | string;
  total_eur: number | string;
  notes: string | null;
  issuer_snapshot_json: Record<string, unknown> | null;
  customer_snapshot_json: Record<string, unknown> | null;
  customer_id: string;
  customer_name: string;
  customer_legal_name: string | null;
  customer_tax_id: string | null;
  customer_phone: string | null;
  customer_email: string | null;
  site_name: string | null;
};

type QuoteLineRow = {
  description: string;
  quantity: number | string;
  unit: string | null;
  unit_price_eur: number | string;
  line_total_eur: number | string;
  sort_order: number;
};

async function issuerForWorkspace(database: DatabaseClient, workspaceId: string) {
  const result = await sql<Record<string, unknown>>`
    SELECT w.id AS workspace_id, w.name AS workspace_name,
           COALESCE(NULLIF(pbp.legal_name, ''), w.name) AS legal_name,
           pbp.tax_id, pbp.address, pbp.postal_code, pbp.municipality, pbp.province,
           pbp.email, pbp.phone, pbp.payment_terms, pbp.footer_note
    FROM workspaces w
    LEFT JOIN professional_business_profiles pbp ON pbp.workspace_id = w.id
    WHERE w.id = ${workspaceId}::uuid
  `.execute(database);
  return result.rows[0] ?? null;
}

export function registerProfessionalPrintRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/print/invoice/:invoiceId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = uuidSchema.safeParse((request.params as { invoiceId?: string }).invoiceId);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_invoice_id' });

    const invoiceResult = await sql<InvoicePrintRow>`
      SELECT pi.id, pi.invoice_number, pi.issued_on::text, pi.due_on::text, pi.status,
             pi.subtotal_eur::double precision, pi.tax_eur::double precision, pi.total_eur::double precision,
             pi.notes, pi.issuer_snapshot_json, pi.customer_snapshot_json,
             p.id AS customer_id, p.display_name AS customer_name, p.legal_name AS customer_legal_name,
             p.tax_id AS customer_tax_id, p.phone AS customer_phone, p.email AS customer_email
      FROM professional_invoices pi
      JOIN parties p ON p.id = pi.customer_party_id
      WHERE pi.id = ${parsed.data}::uuid AND pi.workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const invoice = invoiceResult.rows[0];
    if (!invoice) return reply.code(404).send({ error: 'invoice_not_found' });

    const lines = await sql<InvoiceLineRow>`
      SELECT wr.id AS work_id, wr.occurred_on::text, wr.title,
             cs.name AS site_name,
             piw.amount_eur::double precision AS amount_eur
      FROM professional_invoice_works piw
      JOIN work_records wr ON wr.id = piw.work_id
      LEFT JOIN customer_sites cs ON cs.id = wr.customer_site_id
      WHERE piw.invoice_id = ${parsed.data}::uuid
      ORDER BY wr.occurred_on, wr.created_at
    `.execute(database);

    const fallbackIssuer = await issuerForWorkspace(database, context.workspaceId);
    const fallbackCustomer = {
      id: invoice.customer_id,
      display_name: invoice.customer_name,
      legal_name: invoice.customer_legal_name,
      tax_id: invoice.customer_tax_id,
      phone: invoice.customer_phone,
      email: invoice.customer_email,
    };
    return {
      document_type: 'invoice',
      issuer: invoice.issuer_snapshot_json ?? fallbackIssuer,
      customer: invoice.customer_snapshot_json ?? fallbackCustomer,
      document: {
        id: invoice.id,
        number: invoice.invoice_number,
        issued_on: invoice.issued_on,
        due_on: invoice.due_on,
        status: invoice.status,
        subtotal_eur: Number(invoice.subtotal_eur),
        tax_eur: Number(invoice.tax_eur),
        total_eur: Number(invoice.total_eur),
        notes: invoice.notes,
      },
      lines: lines.rows.map((line) => ({ ...line, amount_eur: Number(line.amount_eur) })),
      semantics: {
        line_amounts: 'gross_amounts_matching_invoice_total',
        totals: 'stored_invoice_totals_no_client_recalculation',
        identity: invoice.issuer_snapshot_json && invoice.customer_snapshot_json ? 'captured_at_creation' : 'legacy_fallback',
      },
    };
  });

  app.get('/api/v1/professional/print/quote/:quoteId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = uuidSchema.safeParse((request.params as { quoteId?: string }).quoteId);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_quote_id' });

    const quoteResult = await sql<QuotePrintRow>`
      SELECT pq.id, pq.quote_number, pq.title, pq.issued_on::text, pq.valid_until::text, pq.status,
             pq.subtotal_eur::double precision, pq.tax_eur::double precision, pq.total_eur::double precision,
             pq.notes, pq.issuer_snapshot_json, pq.customer_snapshot_json,
             p.id AS customer_id, p.display_name AS customer_name, p.legal_name AS customer_legal_name,
             p.tax_id AS customer_tax_id, p.phone AS customer_phone, p.email AS customer_email,
             cs.name AS site_name
      FROM professional_quotes pq
      JOIN parties p ON p.id = pq.customer_party_id
      LEFT JOIN customer_sites cs ON cs.id = pq.customer_site_id
      WHERE pq.id = ${parsed.data}::uuid AND pq.workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const quote = quoteResult.rows[0];
    if (!quote) return reply.code(404).send({ error: 'quote_not_found' });

    const lines = await sql<QuoteLineRow>`
      SELECT description, quantity::double precision, unit,
             unit_price_eur::double precision, line_total_eur::double precision, sort_order
      FROM professional_quote_lines
      WHERE quote_id = ${parsed.data}::uuid
      ORDER BY sort_order, id
    `.execute(database);

    const fallbackIssuer = await issuerForWorkspace(database, context.workspaceId);
    const fallbackCustomer = {
      id: quote.customer_id,
      display_name: quote.customer_name,
      legal_name: quote.customer_legal_name,
      tax_id: quote.customer_tax_id,
      phone: quote.customer_phone,
      email: quote.customer_email,
    };
    return {
      document_type: 'quote',
      issuer: quote.issuer_snapshot_json ?? fallbackIssuer,
      customer: quote.customer_snapshot_json ?? fallbackCustomer,
      document: {
        id: quote.id,
        number: quote.quote_number,
        title: quote.title,
        issued_on: quote.issued_on,
        valid_until: quote.valid_until,
        status: quote.status,
        subtotal_eur: Number(quote.subtotal_eur),
        tax_eur: Number(quote.tax_eur),
        total_eur: Number(quote.total_eur),
        notes: quote.notes,
        site_name: quote.site_name,
      },
      lines: lines.rows.map((line) => ({
        ...line,
        quantity: Number(line.quantity),
        unit_price_eur: Number(line.unit_price_eur),
        line_total_eur: Number(line.line_total_eur),
      })),
      semantics: {
        line_amounts: 'net_before_tax',
        totals: 'stored_quote_totals_no_client_recalculation',
        identity: quote.issuer_snapshot_json && quote.customer_snapshot_json ? 'captured_at_creation' : 'legacy_fallback',
      },
    };
  });
}
