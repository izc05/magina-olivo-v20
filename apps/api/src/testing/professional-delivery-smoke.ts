import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = 'c1111111-1111-4111-8111-111111111111';
const userId = 'c2222222-2222-4222-8222-222222222222';
const customerId = 'c3333333-3333-4333-8333-333333333333';
const quoteId = 'c4444444-4444-4444-8444-444444444444';
const operationId = 'c5555555-5555-4555-8555-555555555555';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'delivery-ci@example.test', 'Delivery CI') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspaces (id, name, type) VALUES (${workspaceId}::uuid, 'Delivery CI Workspace', 'professional') ON CONFLICT (id) DO NOTHING;
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING;
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, 'c6666666-6666-4666-8666-666666666666'::uuid, 'person', 'Cliente Envío CI', ARRAY['customer']) ON CONFLICT (id) DO NOTHING;
    INSERT INTO professional_quotes (
      id, workspace_id, customer_party_id, client_operation_id, quote_number, title,
      issued_on, valid_until, status, subtotal_eur, tax_eur, total_eur, created_by
    ) VALUES (
      ${quoteId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
      'c7777777-7777-4777-8777-777777777777'::uuid, 'P-DEL-001', 'Trabajo de prueba',
      CURRENT_DATE, CURRENT_DATE + 15, 'sent', 100, 21, 121, ${userId}::uuid
    ) ON CONFLICT (id) DO NOTHING;
  `.execute(db);

  const prepared = await app.inject({ method: 'POST', url: '/api/v1/professional/deliveries', headers, payload: {
    client_operation_id: operationId,
    entity_type: 'professional_quote',
    entity_id: quoteId,
    channel: 'whatsapp',
    recipient: '600000000',
  }});
  if (prepared.statusCode !== 201) throw new Error(`Prepare delivery failed: ${prepared.statusCode} ${prepared.body}`);
  const deliveryId = prepared.json().delivery.id as string;
  if (prepared.json().delivery.status !== 'prepared') throw new Error('Delivery must start prepared');

  const replay = await app.inject({ method: 'POST', url: '/api/v1/professional/deliveries', headers, payload: {
    client_operation_id: operationId,
    entity_type: 'professional_quote', entity_id: quoteId, channel: 'whatsapp', recipient: '600000000',
  }});
  if (replay.statusCode !== 200 || replay.json().replayed !== true) throw new Error('Delivery idempotency failed');

  const premature = await app.inject({ method: 'POST', url: `/api/v1/professional/quotes/${quoteId}/decision`, headers, payload: {
    decision: 'accepted', delivery_id: deliveryId,
  }});
  if (premature.statusCode !== 409) throw new Error(`Expected prepared delivery decision rejection, got ${premature.statusCode}`);

  const confirm = await app.inject({ method: 'PATCH', url: `/api/v1/professional/deliveries/${deliveryId}/confirm`, headers, payload: { confirmed_sent: true } });
  if (confirm.statusCode !== 200 || confirm.json().delivery.status !== 'confirmed_sent') throw new Error('Delivery confirmation failed');

  const decision = await app.inject({ method: 'POST', url: `/api/v1/professional/quotes/${quoteId}/decision`, headers, payload: {
    decision: 'accepted', delivery_id: deliveryId, note: 'Aceptado por cliente tras recibir P-DEL-001',
  }});
  if (decision.statusCode !== 201) throw new Error(`Quote decision failed: ${decision.statusCode} ${decision.body}`);

  const quote = await sql<{ status: string }>`SELECT status FROM professional_quotes WHERE id=${quoteId}::uuid`.execute(db);
  if (quote.rows[0]?.status !== 'accepted') throw new Error('Quote status was not updated from decision event');
  const audit = await sql<{ delivery_id: string | null; decision: string }>`SELECT delivery_id,decision FROM professional_quote_decisions WHERE quote_id=${quoteId}::uuid ORDER BY created_at DESC LIMIT 1`.execute(db);
  if (audit.rows[0]?.delivery_id !== deliveryId || audit.rows[0]?.decision !== 'accepted') throw new Error('Decision was not tied to confirmed delivery');

  const history = await app.inject({ method: 'GET', url: `/api/v1/professional/deliveries?entityType=professional_quote&entityId=${quoteId}`, headers });
  if (history.statusCode !== 200 || history.json().deliveries.length !== 1) throw new Error('Delivery history failed');
  if (history.json().deliveries[0].status !== 'confirmed_sent') throw new Error('Confirmed delivery missing from history');

  console.log('Professional delivery smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
