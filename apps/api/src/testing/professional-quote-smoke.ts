import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = 'a1111111-1111-4111-8111-111111111111';
const userId = 'a2222222-2222-4222-8222-222222222222';
const customerId = 'a3333333-3333-4333-8333-333333333333';
const siteId = 'a4444444-4444-4444-8444-444444444444';
const quoteId = 'a5555555-5555-4555-8555-555555555555';
const workId = 'a6666666-6666-4666-8666-666666666666';
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

  const list = await app.inject({ method: 'GET', url: `/api/v1/professional/quotes?customerId=${customerId}`, headers });
  if (list.statusCode !== 200) throw new Error(`Quote list failed: ${list.statusCode}`);
  const quote = list.json().quotes.find((item: { id: string }) => item.id === quoteId);
  if (!quote || quote.status !== 'converted' || quote.work_id !== workId) throw new Error('Converted quote linkage missing');
  if (Math.abs(Number(quote.actual_cost_eur) - 400) > 0.001) throw new Error(`Expected 400 actual cost, got ${quote.actual_cost_eur}`);
  if (Math.abs(Number(quote.work_charge_eur) - 1210) > 0.001) throw new Error(`Expected work charge 1210, got ${quote.work_charge_eur}`);

  console.log('Professional quote smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
