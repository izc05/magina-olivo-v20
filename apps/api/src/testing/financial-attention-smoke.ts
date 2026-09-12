import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '55555555-5555-4555-8555-555555555555';
const campaignId = '22222222-2222-4222-8222-222222222222';
const deliveryId = '99999999-9999-4999-8999-999999999999';
const settlementId = '13131313-1313-4131-8131-131313131313';
const collectionId = '14141414-1414-4141-8141-141414141414';
const documentId = '15151515-1515-4151-8151-151515151515';

const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
};

async function main() {
  await sql`INSERT INTO users (id, primary_email, display_name) VALUES (${userId}::uuid, 'financial-attention-ci@example.test', 'Financial Attention CI') ON CONFLICT (id) DO NOTHING`.execute(db);
  await sql`
    INSERT INTO harvest_settlements (
      id, workspace_id, campaign_id, client_operation_id, counterparty_name, settlement_number,
      settled_on, basis, gross_eur, deductions_eur, net_eur, status, created_by
    ) VALUES (
      ${settlementId}::uuid, ${workspaceId}::uuid, ${campaignId}::uuid,
      '16161616-1616-4161-8161-161616161616'::uuid, 'SCA CI', 'LIQ-CI-001',
      '2026-12-20'::date, 'olive_kg', 1200, 100, 1100, 'confirmed', ${userId}::uuid
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_settlement_deliveries (settlement_id, delivery_id)
    VALUES (${settlementId}::uuid, ${deliveryId}::uuid)
    ON CONFLICT DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_collections (
      id, workspace_id, settlement_id, client_operation_id, collected_on, amount_eur, method, reference, created_by
    ) VALUES (
      ${collectionId}::uuid, ${workspaceId}::uuid, ${settlementId}::uuid,
      '17171717-1717-4171-8171-171717171717'::uuid, '2026-12-22'::date, 400, 'bank', 'CI-PAGO', ${userId}::uuid
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await db.insertInto('documents').values({
    id: documentId,
    workspace_id: workspaceId,
    client_operation_id: '18181818-1818-4181-8181-181818181818',
    kind: 'collection_receipt',
    title: 'Justificante CI pendiente de OCR',
    status: 'active',
    created_by: userId,
    archived_at: null,
  }).onConflict((oc) => oc.column('id').doNothing()).execute();

  const existingLink = await db.selectFrom('attachment_links').select('id')
    .where('workspace_id', '=', workspaceId)
    .where('document_id', '=', documentId)
    .where('field_id', '=', fieldId)
    .executeTakeFirst();
  if (!existingLink) {
    await db.insertInto('attachment_links').values({
      workspace_id: workspaceId,
      document_id: documentId,
      field_id: fieldId,
      domain_type: null,
      domain_record_id: null,
      relation: 'attachment',
    }).execute();
  }

  const response = await app.inject({
    method: 'GET',
    url: `/api/v1/financial-attention?fieldId=${fieldId}`,
    headers,
  });
  if (response.statusCode !== 200) throw new Error(`Financial attention failed: ${response.statusCode} ${response.body}`);
  const body = response.json();
  if (Math.abs(Number(body.pending_collection_eur) - 700) > 0.001) throw new Error(`Unexpected pending collection: ${body.pending_collection_eur}`);
  if (!body.settlements.some((item: { id: string }) => item.id === settlementId)) throw new Error('Pending settlement missing');
  const document = body.documents.find((item: { id: string }) => item.id === documentId);
  if (!document) throw new Error('Pending document missing');
  if (document.status !== 'needs_ocr') throw new Error(`Unexpected document attention status: ${document.status}`);

  console.log('Financial attention smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
