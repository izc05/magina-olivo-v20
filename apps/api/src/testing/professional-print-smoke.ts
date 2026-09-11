import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = 'b1111111-1111-4111-8111-111111111111';
const userId = 'b2222222-2222-4222-8222-222222222222';
const customerId = 'b3333333-3333-4333-8333-333333333333';
const siteId = 'b4444444-4444-4444-8444-444444444444';
const quoteId = 'b5555555-5555-4555-8555-555555555555';
const workId = 'b6666666-6666-4666-8666-666666666666';
const invoiceId = 'b7777777-7777-4777-8777-777777777777';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function seed() {
  await sql`INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'print-ci@example.test', 'Print CI') ON CONFLICT (id) DO NOTHING`.execute(db);
  await sql`INSERT INTO workspaces (id, name, type) VALUES (${workspaceId}::uuid, 'Print CI Workspace', 'professional') ON CONFLICT (id) DO NOTHING`.execute(db);
  await sql`INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING`.execute(db);
  await sql`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, legal_name, tax_id, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, 'b8888888-8888-4888-8888-888888888888'::uuid, 'organization', 'Cliente Print CI', 'Cliente Print SL', 'B12345678', ARRAY['customer'])
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO customer_sites (id, workspace_id, client_operation_id, customer_party_id, name, active)
    VALUES (${siteId}::uuid, ${workspaceId}::uuid, 'b9999999-9999-4999-8999-999999999999'::uuid, ${customerId}::uuid, 'Finca Cliente Print', TRUE)
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
}

async function main() {
  await seed();

  const profile = await app.inject({ method: 'PUT', url: '/api/v1/professional/business-profile', headers, payload: {
    legal_name: 'Servicios Mágina SL', tax_id: 'A12345678', address: 'Calle Olivo 1', postal_code: '23537', municipality: 'Bedmar', province: 'Jaén', email: 'facturacion@example.test', phone: '600000000', payment_terms: 'Transferencia a 30 días', footer_note: 'Gracias por confiar en nosotros.',
  }});
  if (profile.statusCode !== 200) throw new Error(`Profile failed: ${profile.statusCode} ${profile.body}`);

  const quote = await app.inject({ method: 'POST', url: '/api/v1/professional/quotes', headers, payload: {
    client_operation_id: 'baaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', entity_id: quoteId,
    customer_party_id: customerId, customer_site_id: siteId, quote_number: 'P-PRINT-001', title: 'Poda y trituración',
    issued_on: '2026-09-11', valid_until: '2026-10-11', status: 'sent', subtotal_eur: 1000, tax_eur: 210, total_eur: 1210,
    lines: [{ description: 'Poda y trituración', quantity: 1, unit: 'servicio', unit_price_eur: 1000, line_total_eur: 1000 }],
  }});
  if (quote.statusCode !== 201) throw new Error(`Quote failed: ${quote.statusCode} ${quote.body}`);

  await sql`
    INSERT INTO work_records (
      id, workspace_id, customer_site_id, client_operation_id, professional_quote_id, type, occurred_on, title,
      performed_for, customer_party_id, quoted_amount_eur, charge_eur, collected_eur, payment_status, created_by
    ) VALUES (
      ${workId}::uuid, ${workspaceId}::uuid, ${siteId}::uuid, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid,
      ${quoteId}::uuid, 'pruning', '2026-09-20', 'Poda y trituración', 'third-party', ${customerId}::uuid,
      1210, 1210, 0, 'pending', ${userId}::uuid
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);

  const invoice = await app.inject({ method: 'POST', url: '/api/v1/professional/invoices', headers, payload: {
    client_operation_id: 'bccccccc-cccc-4ccc-8ccc-cccccccccccc', entity_id: invoiceId,
    customer_party_id: customerId, invoice_number: 'F-PRINT-001', issued_on: '2026-09-21', due_on: '2026-10-21', status: 'issued',
    subtotal_eur: 1000, tax_eur: 210, total_eur: 1210,
    works: [{ work_id: workId, amount_eur: 1210 }],
  }});
  if (invoice.statusCode !== 201) throw new Error(`Invoice failed: ${invoice.statusCode} ${invoice.body}`);

  const changedProfile = await app.inject({ method: 'PUT', url: '/api/v1/professional/business-profile', headers, payload: {
    legal_name: 'Servicios Mágina NUEVO SL', tax_id: 'A99999999', address: 'Otra calle 9', municipality: 'Jimena', province: 'Jaén', payment_terms: 'Contado',
  }});
  if (changedProfile.statusCode !== 200) throw new Error(`Profile update failed: ${changedProfile.statusCode}`);
  await sql`UPDATE parties SET legal_name = 'Cliente Modificado SL', tax_id = 'B99999999' WHERE id = ${customerId}::uuid`.execute(db);
  await sql`UPDATE professional_quotes SET issuer_snapshot_json = '{"legal_name":"Manipulado"}'::jsonb, customer_snapshot_json = '{"legal_name":"Manipulado"}'::jsonb WHERE id = ${quoteId}::uuid`.execute(db);
  await sql`UPDATE professional_invoices SET issuer_snapshot_json = '{"legal_name":"Manipulado"}'::jsonb, customer_snapshot_json = '{"legal_name":"Manipulado"}'::jsonb WHERE id = ${invoiceId}::uuid`.execute(db);

  const quotePrint = await app.inject({ method: 'GET', url: `/api/v1/professional/print/quote/${quoteId}`, headers });
  if (quotePrint.statusCode !== 200) throw new Error(`Quote print failed: ${quotePrint.statusCode} ${quotePrint.body}`);
  const q = quotePrint.json();
  if (q.document_type !== 'quote' || q.document.number !== 'P-PRINT-001') throw new Error('Unexpected quote print identity');
  if (q.issuer.legal_name !== 'Servicios Mágina SL' || q.issuer.tax_id !== 'A12345678') throw new Error('Quote issuer snapshot was rewritten');
  if (q.customer.legal_name !== 'Cliente Print SL' || q.customer.tax_id !== 'B12345678') throw new Error('Quote customer snapshot was rewritten');
  if (q.lines.length !== 1 || Math.abs(Number(q.lines[0].line_total_eur) - 1000) > 0.001) throw new Error('Quote line mismatch');
  if (Math.abs(Number(q.document.total_eur) - 1210) > 0.001) throw new Error('Quote total mismatch');
  if (q.semantics.identity !== 'captured_at_creation') throw new Error('Quote did not report captured identity semantics');

  const invoicePrint = await app.inject({ method: 'GET', url: `/api/v1/professional/print/invoice/${invoiceId}`, headers });
  if (invoicePrint.statusCode !== 200) throw new Error(`Invoice print failed: ${invoicePrint.statusCode} ${invoicePrint.body}`);
  const i = invoicePrint.json();
  if (i.document_type !== 'invoice' || i.document.number !== 'F-PRINT-001') throw new Error('Unexpected invoice print identity');
  if (i.issuer.legal_name !== 'Servicios Mágina SL' || i.issuer.tax_id !== 'A12345678') throw new Error('Invoice issuer snapshot was rewritten');
  if (i.customer.legal_name !== 'Cliente Print SL' || i.customer.tax_id !== 'B12345678') throw new Error('Invoice customer snapshot was rewritten');
  if (i.lines.length !== 1 || Math.abs(Number(i.lines[0].amount_eur) - 1210) > 0.001) throw new Error('Invoice line mismatch');
  if (Math.abs(Number(i.document.subtotal_eur) - 1000) > 0.001 || Math.abs(Number(i.document.tax_eur) - 210) > 0.001 || Math.abs(Number(i.document.total_eur) - 1210) > 0.001) throw new Error('Invoice totals mismatch');
  if (i.semantics.identity !== 'captured_at_creation') throw new Error('Invoice did not report captured identity semantics');

  console.log('Professional print smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
