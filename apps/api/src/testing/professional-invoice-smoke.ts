import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '81111111-1111-4111-8111-111111111111';
const userId = '82222222-2222-4222-8222-222222222222';
const campaignId = '83333333-3333-4333-8333-333333333333';
const fieldId = '84444444-4444-4444-8444-444444444444';
const customerId = '85555555-5555-4555-8555-555555555555';
const workId = '86666666-6666-4666-8666-666666666666';
const invoiceId = '87777777-7777-4777-8777-777777777777';
const replacementInvoiceId = '88888888-8888-4888-8888-888888888881';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'invoice-ci@example.test', 'Invoice CI') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspaces (id, name, type) VALUES (${workspaceId}::uuid, 'Invoice CI Workspace', 'professional') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING;
    INSERT INTO campaigns (id, workspace_id, name, start_date, status) VALUES (${campaignId}::uuid, ${workspaceId}::uuid, 'Invoice CI 2026/27', '2026-09-01', 'active') ON CONFLICT (id) DO NOTHING;
    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status) VALUES (${fieldId}::uuid, ${workspaceId}::uuid, '89999999-9999-4999-8999-999999999999'::uuid, 'Invoice Field CI', 'olivar', 'active') ON CONFLICT (id) DO NOTHING;
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles) VALUES (${customerId}::uuid, ${workspaceId}::uuid, '8aaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid, 'person', 'Cliente Factura CI', ARRAY['customer']) ON CONFLICT (id) DO NOTHING;
  `.execute(db);

  const workResponse = await app.inject({ method: 'POST', url: '/api/v1/works', headers, payload: {
    client_operation_id: '8bbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', entity_id: workId, field_id: fieldId, campaign_id: campaignId,
    type: 'manual-work', occurred_on: '2026-10-10', title: 'Trabajo facturable CI', performed_for: 'third-party',
    customer_party_id: customerId, charge_eur: 121, collected_eur: 21,
    participants: [{ display_name: 'Operario CI', cost_eur: 40 }], resources: [],
  }});
  if (workResponse.statusCode !== 201) throw new Error(`Work create failed: ${workResponse.statusCode} ${workResponse.body}`);

  const createPayload = {
    client_operation_id: '8ccccccc-cccc-4ccc-8ccc-cccccccccccc', entity_id: invoiceId,
    customer_party_id: customerId, invoice_number: 'F-CI-001', issued_on: '2026-10-12', due_on: '2026-11-12', status: 'issued',
    subtotal_eur: 100, tax_eur: 21, total_eur: 121,
    works: [{ work_id: workId, amount_eur: 121 }],
  };
  const invoiceResponse = await app.inject({ method: 'POST', url: '/api/v1/professional/invoices', headers, payload: createPayload });
  if (invoiceResponse.statusCode !== 201) throw new Error(`Invoice create failed: ${invoiceResponse.statusCode} ${invoiceResponse.body}`);

  const duplicate = await app.inject({ method: 'POST', url: '/api/v1/professional/invoices', headers, payload: {
    ...createPayload, client_operation_id: '8ddddddd-dddd-4ddd-8ddd-dddddddddddd', entity_id: '8eeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', invoice_number: 'F-CI-002',
  }});
  if (duplicate.statusCode !== 409) throw new Error(`Expected duplicate work invoice conflict, got ${duplicate.statusCode} ${duplicate.body}`);

  const listBeforeVoid = await app.inject({ method: 'GET', url: `/api/v1/professional/invoices?customerId=${customerId}`, headers });
  if (listBeforeVoid.statusCode !== 200) throw new Error(`Invoice list failed: ${listBeforeVoid.statusCode}`);
  const first = listBeforeVoid.json().invoices.find((item: { id: string }) => item.id === invoiceId);
  if (!first) throw new Error('Created invoice missing from list');
  if (Math.abs(Number(first.pending_eur) - 100) > 0.001) throw new Error(`Expected 100 pending, got ${first.pending_eur}`);

  const voidResponse = await app.inject({ method: 'PATCH', url: `/api/v1/professional/invoices/${invoiceId}`, headers, payload: { status: 'void' } });
  if (voidResponse.statusCode !== 200) throw new Error(`Invoice void failed: ${voidResponse.statusCode} ${voidResponse.body}`);

  const replacement = await app.inject({ method: 'POST', url: '/api/v1/professional/invoices', headers, payload: {
    client_operation_id: '8fffffff-ffff-4fff-8fff-ffffffffffff', entity_id: replacementInvoiceId,
    customer_party_id: customerId, invoice_number: 'F-CI-003', issued_on: '2026-10-13', status: 'issued',
    subtotal_eur: 100, tax_eur: 21, total_eur: 121, works: [{ work_id: workId, amount_eur: 121 }],
  }});
  if (replacement.statusCode !== 201) throw new Error(`Replacement invoice failed: ${replacement.statusCode} ${replacement.body}`);

  const customer = await app.inject({ method: 'GET', url: `/api/v1/professional/customers/${customerId}`, headers });
  if (customer.statusCode !== 200) throw new Error(`Customer detail failed: ${customer.statusCode} ${customer.body}`);
  const detail = customer.json();
  if (detail.summary.work_count !== 1) throw new Error(`Unexpected customer work count ${detail.summary.work_count}`);
  if (detail.summary.invoice_count !== 2) throw new Error(`Unexpected customer invoice count ${detail.summary.invoice_count}`);
  if (detail.collections.length !== 1 || Math.abs(Number(detail.collections[0].amount_eur) - 21) > 0.001) throw new Error('Initial work collection missing from customer history');

  const candidates = await app.inject({ method: 'GET', url: `/api/v1/professional/invoice-candidates?customerId=${customerId}`, headers });
  if (candidates.statusCode !== 200) throw new Error(`Candidates failed: ${candidates.statusCode}`);
  if (candidates.json().works.some((item: { id: string }) => item.id === workId)) throw new Error('Actively invoiced work must not remain a candidate');

  console.log('Professional invoice smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
