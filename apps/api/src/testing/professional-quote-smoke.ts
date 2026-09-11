import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import { FakeOcrQueue, FakeStorage } from './fakes.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const storage = new FakeStorage();
const app = buildApp({ db, storage, ocrQueue: new FakeOcrQueue() });

const workspaceId = 'a1111111-1111-4111-8111-111111111111';
const userId = 'a2222222-2222-4222-8222-222222222222';
const customerId = 'a3333333-3333-4333-8333-333333333333';
const siteId = 'a4444444-4444-4444-8444-444444444444';
const quoteId = 'a5555555-5555-4555-8555-555555555555';
const workId = 'a6666666-6666-4666-8666-666666666666';
const rejectedQuoteId = 'a6777777-7777-4777-8777-777777777777';
const invoiceId = 'a6888888-8888-4888-8888-888888888888';
const documentId = 'a6999999-9999-4999-8999-999999999999';
const versionId = 'a6aaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'quote-ci@example.test', 'Quote CI') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspaces (id, name, type) VALUES (${workspaceId}::uuid, 'Quote CI Workspace', 'professional') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING;
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, 'a7777777-7777-4777-8777-777777777777'::uuid, 'person', 'Cliente Presupuesto CI', ARRAY['customer']) ON CONFLICT (id) DO NOTHING;
    INSERT INTO customer_sites (id, workspace_id, client_operation_id, customer_party_id, name, active)
    VALUES (${siteId}::uuid, ${workspaceId}::uuid, 'a8888888-8888-4888-8888-888888888888'::uuid, ${customerId}::uuid, 'Finca Cliente CI', TRUE) ON CONFLICT (id) DO NOTHING;
  `.execute(db);

  const created = await app.inject({ method: 'POST', url: '/api/v1/professional/quotes', headers, payload: {
    client_operation_id: 'a9999999-9999-4999-8999-999999999999', entity_id: quoteId,
    customer_party_id: customerId, customer_site_id: siteId, quote_number: 'P-CI-001', title: 'Poda completa',
    issued_on: '2026-09-10', valid_until: '2026-10-10', status: 'sent', subtotal_eur: 1000, tax_eur: 210, total_eur: 1210,
    lines: [{ description: 'Poda completa', quantity: 1, unit: 'servicio', unit_price_eur: 1000, line_total_eur: 1000 }],
  }});
  if (created.statusCode !== 201) throw new Error(`Quote create failed: ${created.statusCode} ${created.body}`);

  const rejectedCreated = await app.inject({ method: 'POST', url: '/api/v1/professional/quotes', headers, payload: {
    client_operation_id: 'a6bbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', entity_id: rejectedQuoteId,
    customer_party_id: customerId, customer_site_id: siteId, quote_number: 'P-CI-002', title: 'Desbroce rechazado',
    issued_on: '2026-09-11', status: 'sent', subtotal_eur: 500, tax_eur: 105, total_eur: 605,
    lines: [{ description: 'Desbroce', quantity: 1, unit: 'servicio', unit_price_eur: 500, line_total_eur: 500 }],
  }});
  if (rejectedCreated.statusCode !== 201) throw new Error(`Rejected quote create failed: ${rejectedCreated.statusCode}`);
  const rejected = await app.inject({ method: 'PATCH', url: `/api/v1/professional/quotes/${rejectedQuoteId}/status`, headers, payload: { status: 'rejected' } });
  if (rejected.statusCode !== 200) throw new Error(`Quote reject failed: ${rejected.statusCode}`);

  const accepted = await app.inject({ method: 'PATCH', url: `/api/v1/professional/quotes/${quoteId}/status`, headers, payload: { status: 'accepted' } });
  if (accepted.statusCode !== 200) throw new Error(`Quote accept failed: ${accepted.statusCode} ${accepted.body}`);

  const converted = await app.inject({ method: 'POST', url: `/api/v1/professional/quotes/${quoteId}/convert`, headers, payload: {
    client_operation_id: 'abbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', entity_id: workId,
    customer_site_id: siteId, type: 'pruning', occurred_on: '2026-10-20',
    participants: [{ display_name: 'Cuadrilla CI', cost_eur: 300 }],
    resources: [{ kind: 'machinery', name: 'Trituradora CI', cost_eur: 100 }],
  }});
  if (converted.statusCode !== 201) throw new Error(`Quote conversion failed: ${converted.statusCode} ${converted.body}`);
  if (Math.abs(Number(converted.json().total_cost_eur) - 400) > 0.001) throw new Error('Unexpected converted work cost');

  const second = await app.inject({ method: 'POST', url: `/api/v1/professional/quotes/${quoteId}/convert`, headers, payload: {
    client_operation_id: 'accccccc-cccc-4ccc-8ccc-cccccccccccc', entity_id: 'addddddd-dddd-4ddd-8ddd-dddddddddddd',
    customer_site_id: siteId, type: 'pruning', occurred_on: '2026-10-21', participants: [], resources: [],
  }});
  if (second.statusCode !== 409) throw new Error(`Expected second conversion conflict, got ${second.statusCode} ${second.body}`);

  const invoice = await app.inject({ method: 'POST', url: '/api/v1/professional/invoices', headers, payload: {
    client_operation_id: 'a6cccccc-cccc-4ccc-8ccc-cccccccccccc', entity_id: invoiceId,
    customer_party_id: customerId, invoice_number: 'F-Q-CI-001', issued_on: '2026-10-25', status: 'issued',
    subtotal_eur: 1000, tax_eur: 210, total_eur: 1210, works: [{ work_id: workId, amount_eur: 1210 }],
  }});
  if (invoice.statusCode !== 201) throw new Error(`Quote invoice failed: ${invoice.statusCode} ${invoice.body}`);

  const checksum = '11'.repeat(32);
  const document = await app.inject({ method: 'POST', url: '/api/v1/documents', headers, payload: {
    client_operation_id: 'a6dddddd-dddd-4ddd-8ddd-dddddddddddd', entity_id: documentId, version_id: versionId,
    domain_type: 'professional_quote', domain_record_id: quoteId, relation: 'issued_quote_pdf', kind: 'sales_quote',
    title: 'Presupuesto emitido P-CI-001', original_filename: 'P-CI-001.pdf', mime_type: 'application/pdf', byte_size: 128, sha256: checksum,
  }});
  if (document.statusCode !== 201) throw new Error(`Quote document reserve failed: ${document.statusCode} ${document.body}`);
  const completed = await app.inject({ method: 'POST', url: `/api/v1/documents/${documentId}/versions/${versionId}/complete`, headers });
  if (completed.statusCode !== 200) throw new Error(`Quote document complete failed: ${completed.statusCode} ${completed.body}`);

  const list = await app.inject({ method: 'GET', url: `/api/v1/professional/quotes?customerId=${customerId}`, headers });
  if (list.statusCode !== 200) throw new Error(`Quote list failed: ${list.statusCode}`);
  const quote = list.json().quotes.find((item: { id: string }) => item.id === quoteId);
  if (!quote || quote.status !== 'converted' || quote.work_id !== workId) throw new Error('Converted quote linkage missing');
  if (Math.abs(Number(quote.actual_cost_eur) - 400) > 0.001) throw new Error(`Expected 400 actual cost, got ${quote.actual_cost_eur}`);
  if (Math.abs(Number(quote.work_charge_eur) - 1210) > 0.001) throw new Error(`Expected work charge 1210, got ${quote.work_charge_eur}`);

  const detailResponse = await app.inject({ method: 'GET', url: `/api/v1/professional/customers/${customerId}`, headers });
  if (detailResponse.statusCode !== 200) throw new Error(`Customer quote detail failed: ${detailResponse.statusCode} ${detailResponse.body}`);
  const detail = detailResponse.json();
  if (detail.quote_summary.quote_count !== 2) throw new Error(`Expected 2 quotes, got ${detail.quote_summary.quote_count}`);
  if (detail.quote_summary.accepted_quote_count !== 1 || detail.quote_summary.rejected_quote_count !== 1) throw new Error('Unexpected quote decisions');
  if (Math.abs(Number(detail.quote_summary.acceptance_rate_percent) - 50) > 0.001) throw new Error(`Expected 50% acceptance, got ${detail.quote_summary.acceptance_rate_percent}`);
  if (Math.abs(Number(detail.quote_summary.converted_real_cost_eur) - 400) > 0.001) throw new Error('Unexpected converted real cost');
  if (Math.abs(Number(detail.quote_summary.converted_invoiced_eur) - 1210) > 0.001) throw new Error('Unexpected converted invoiced amount');
  if (Math.abs(Number(detail.quote_summary.converted_invoiced_less_real_cost_eur) - 810) > 0.001) throw new Error('Unexpected converted margin indicator');
  if (!detail.documents.some((item: { domain_type: string; domain_record_id: string; kind: string }) => item.domain_type === 'professional_quote' && item.domain_record_id === quoteId && item.kind === 'sales_quote')) throw new Error('Quote PDF missing from customer detail');

  console.log('Professional quote smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
