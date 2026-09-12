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
const documentId = 'c8888888-8888-4888-8888-888888888888';
const versionId = 'c9999999-9999-4999-8999-999999999999';
const documentOperationId = 'caaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function seed() {
  await sql`INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'delivery-ci@example.test', 'Delivery CI') ON CONFLICT (id) DO NOTHING`.execute(db);
  await sql`INSERT INTO workspaces (id, name, type) VALUES (${workspaceId}::uuid, 'Delivery CI Workspace', 'professional') ON CONFLICT (id) DO NOTHING`.execute(db);
  await sql`INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING`.execute(db);
  await sql`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, legal_name, tax_id, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, 'c6666666-6666-4666-8666-666666666666'::uuid, 'person', 'Cliente Envío CI', 'Cliente Envío CI', '12345678Z', ARRAY['customer'])
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO professional_quotes (
      id, workspace_id, customer_party_id, client_operation_id, quote_number, title,
      issued_on, valid_until, status, subtotal_eur, tax_eur, total_eur,
      issuer_snapshot_json, customer_snapshot_json, created_by
    ) VALUES (
      ${quoteId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
      'c7777777-7777-4777-8777-777777777777'::uuid, 'P-DEL-001', 'Trabajo de prueba',
      CURRENT_DATE, CURRENT_DATE + 15, 'sent', 100, 21, 121,
      jsonb_build_object('legal_name','Delivery CI Workspace','tax_id','A11111111'),
      jsonb_build_object('display_name','Cliente Envío CI','legal_name','Cliente Envío CI','tax_id','12345678Z'),
      ${userId}::uuid
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO documents (id, workspace_id, client_operation_id, kind, title, status, created_by)
    VALUES (${documentId}::uuid, ${workspaceId}::uuid, ${documentOperationId}::uuid, 'sales_quote', 'Presupuesto P-DEL-001', 'active', ${userId}::uuid)
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO document_versions (
      id, document_id, version_no, storage_key, original_filename, mime_type, byte_size, sha256,
      created_by, upload_status, integrity_status, uploaded_at
    ) VALUES (
      ${versionId}::uuid, ${documentId}::uuid, 1, 'ci/p-del-001.pdf', 'P-DEL-001.pdf', 'application/pdf', 123,
      repeat('a',64), ${userId}::uuid, 'uploaded', 'verified', now()
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO attachment_links (workspace_id, document_id, domain_type, domain_record_id, relation)
    SELECT ${workspaceId}::uuid, ${documentId}::uuid, 'professional_quote', ${quoteId}::uuid, 'generated_pdf'
    WHERE NOT EXISTS (
      SELECT 1 FROM attachment_links WHERE document_id=${documentId}::uuid AND domain_type='professional_quote' AND domain_record_id=${quoteId}::uuid
    )
  `.execute(db);
}

async function main() {
  await seed();

  const prepared = await app.inject({ method: 'POST', url: '/api/v1/professional/deliveries', headers, payload: {
    client_operation_id: operationId,
    entity_type: 'professional_quote',
    entity_id: quoteId,
    document_id: documentId,
    channel: 'whatsapp',
    recipient: '600000000',
  }});
  if (prepared.statusCode !== 201) throw new Error(`Prepare delivery failed: ${prepared.statusCode} ${prepared.body}`);
  const deliveryId = prepared.json().delivery.id as string;
  if (prepared.json().delivery.status !== 'prepared') throw new Error('Delivery must start prepared');

  const replay = await app.inject({ method: 'POST', url: '/api/v1/professional/deliveries', headers, payload: {
    client_operation_id: operationId,
    entity_type: 'professional_quote', entity_id: quoteId, document_id: documentId, channel: 'whatsapp', recipient: '600000000',
  }});
  if (replay.statusCode !== 200 || replay.json().replayed !== true) throw new Error('Delivery idempotency failed');

  const shareResponse = await app.inject({ method: 'POST', url: '/api/v1/professional/share-links', headers, payload: {
    entity_type: 'professional_quote', entity_id: quoteId, document_id: documentId, delivery_id: deliveryId, expires_in_days: 7,
  }});
  if (shareResponse.statusCode !== 201) throw new Error(`Share link failed: ${shareResponse.statusCode} ${shareResponse.body}`);
  const token = shareResponse.json().token as string;

  const infoBefore = await app.inject({ method: 'GET', url: `/api/public/v1/professional/share/${token}/info` });
  if (infoBefore.statusCode !== 200) throw new Error(`Public info failed: ${infoBefore.statusCode} ${infoBefore.body}`);
  if (infoBefore.json().share.can_decide !== false) throw new Error('Prepared delivery must not allow a public decision');
  if (infoBefore.json().share.number !== 'P-DEL-001' || Number(infoBefore.json().share.total_eur) !== 121) throw new Error('Public quote summary mismatch');

  const publicPremature = await app.inject({ method: 'POST', url: `/api/public/v1/professional/share/${token}/decision`, headers: { 'content-type': 'application/json', 'user-agent': 'Magina-Smoke/1.0' }, payload: {
    decision: 'accepted', customer_name: 'Cliente Envío CI',
  }});
  if (publicPremature.statusCode !== 409 || publicPremature.json().error !== 'delivery_not_confirmed') throw new Error(`Expected public decision to be blocked before confirmation, got ${publicPremature.statusCode} ${publicPremature.body}`);

  const confirm = await app.inject({ method: 'PATCH', url: `/api/v1/professional/deliveries/${deliveryId}/confirm`, headers, payload: { confirmed_sent: true } });
  if (confirm.statusCode !== 200 || confirm.json().delivery.status !== 'confirmed_sent') throw new Error('Delivery confirmation failed');

  const infoAfterConfirm = await app.inject({ method: 'GET', url: `/api/public/v1/professional/share/${token}/info` });
  if (infoAfterConfirm.statusCode !== 200 || infoAfterConfirm.json().share.can_decide !== true) throw new Error('Confirmed delivery should enable public quote decision');

  const publicDecision = await app.inject({ method: 'POST', url: `/api/public/v1/professional/share/${token}/decision`, headers: { 'content-type': 'application/json', 'user-agent': 'Magina-Smoke/1.0' }, payload: {
    decision: 'accepted', customer_name: 'Cliente Envío CI', note: 'Aceptado desde enlace público',
  }});
  if (publicDecision.statusCode !== 201 || publicDecision.json().decision.decision !== 'accepted') throw new Error(`Public quote decision failed: ${publicDecision.statusCode} ${publicDecision.body}`);

  const replayDecision = await app.inject({ method: 'POST', url: `/api/public/v1/professional/share/${token}/decision`, headers: { 'content-type': 'application/json', 'user-agent': 'Magina-Smoke/2.0' }, payload: {
    decision: 'rejected', customer_name: 'Otro intento',
  }});
  if (replayDecision.statusCode !== 200 || replayDecision.json().replayed !== true || replayDecision.json().decision.decision !== 'accepted') throw new Error('Public decision replay must preserve the first decision');

  const quote = await sql<{ status: string }>`SELECT status FROM professional_quotes WHERE id=${quoteId}::uuid`.execute(db);
  if (quote.rows[0]?.status !== 'accepted') throw new Error('Quote status was not updated from public decision');
  const audit = await sql<{ delivery_id: string | null; share_link_id: string | null; decision: string; decision_source: string; user_agent: string | null; created_by: string | null }>`
    SELECT delivery_id, share_link_id, decision, decision_source, user_agent, created_by
    FROM professional_quote_decisions
    WHERE quote_id=${quoteId}::uuid AND decision_source='public_link'
    ORDER BY created_at DESC LIMIT 1
  `.execute(db);
  if (audit.rows[0]?.delivery_id !== deliveryId || !audit.rows[0]?.share_link_id || audit.rows[0]?.decision !== 'accepted') throw new Error('Public decision was not tied to share/delivery');
  if (audit.rows[0]?.decision_source !== 'public_link' || audit.rows[0]?.user_agent !== 'Magina-Smoke/1.0' || audit.rows[0]?.created_by !== null) throw new Error('Public decision audit metadata mismatch');

  const infoAfterDecision = await app.inject({ method: 'GET', url: `/api/public/v1/professional/share/${token}/info` });
  if (infoAfterDecision.json().share.decision !== 'accepted' || infoAfterDecision.json().share.can_decide !== false) throw new Error('Public share did not expose final decision');

  const history = await app.inject({ method: 'GET', url: `/api/v1/professional/deliveries?entityType=professional_quote&entityId=${quoteId}`, headers });
  if (history.statusCode !== 200 || history.json().deliveries.length !== 1) throw new Error('Delivery history failed');
  if (history.json().deliveries[0].status !== 'confirmed_sent') throw new Error('Confirmed delivery missing from history');

  console.log('Professional delivery + public quote decision smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }
