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
const invoiceId = 'a4444444-4444-4444-8444-444444444444';
const documentId = 'a5555555-5555-4555-8555-555555555555';
const versionId = 'a6666666-6666-4666-8666-666666666666';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };
const authHeaders = { 'x-workspace-id': workspaceId, 'x-user-id': userId };

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name)
    VALUES (${userId}::uuid, 'professional-document-ci@example.test', 'Professional Document CI')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO workspaces (id, name, type)
    VALUES (${workspaceId}::uuid, 'Professional Document CI', 'professional')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status)
    VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active')
    ON CONFLICT (workspace_id, user_id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, 'a7777777-7777-4777-8777-777777777777'::uuid, 'person', 'Cliente Documento CI', ARRAY['customer'])
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO professional_invoices (
      id, workspace_id, customer_party_id, client_operation_id, invoice_number, issued_on,
      status, subtotal_eur, tax_eur, total_eur, created_by
    ) VALUES (
      ${invoiceId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
      'a8888888-8888-4888-8888-888888888888'::uuid, 'DOC-CI-001', CURRENT_DATE,
      'issued', 100, 21, 121, ${userId}::uuid
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  const checksum = '0'.repeat(64);
  const create = await app.inject({
    method: 'POST',
    url: '/api/v1/documents',
    headers,
    payload: {
      client_operation_id: 'a9999999-9999-4999-8999-999999999999',
      entity_id: documentId,
      version_id: versionId,
      domain_type: 'professional_invoice',
      domain_record_id: invoiceId,
      relation: 'issued_invoice_pdf',
      kind: 'sales_invoice',
      title: 'Factura emitida DOC-CI-001',
      original_filename: 'DOC-CI-001.pdf',
      mime_type: 'application/pdf',
      byte_size: 1234,
      sha256: checksum,
    },
  });
  if (create.statusCode !== 201) throw new Error(`Document create failed: ${create.statusCode} ${create.body}`);
  const created = create.json();
  if (created.link.field_id !== null) throw new Error('Professional invoice document must not require field_id');
  if (created.link.domain_type !== 'professional_invoice' || created.link.domain_record_id !== invoiceId) {
    throw new Error('Professional invoice domain link incorrect');
  }

  const complete = await app.inject({
    method: 'POST',
    url: `/api/v1/documents/${documentId}/versions/${versionId}/complete`,
    headers: authHeaders,
  });
  if (complete.statusCode !== 200) throw new Error(`Document complete failed: ${complete.statusCode} ${complete.body}`);

  const detail = await app.inject({ method: 'GET', url: `/api/v1/professional/customers/${customerId}`, headers: authHeaders });
  if (detail.statusCode !== 200) throw new Error(`Customer detail failed: ${detail.statusCode} ${detail.body}`);
  const linked = detail.json().documents.find((item: { id: string }) => item.id === documentId);
  if (
    !linked
    || linked.domain_type !== 'professional_invoice'
    || linked.domain_record_id !== invoiceId
    || linked.relation !== 'issued_invoice_pdf'
    || linked.kind !== 'sales_invoice'
  ) {
    throw new Error(`Professional invoice document missing or incorrectly linked in customer detail: ${JSON.stringify(linked ?? null)}`);
  }

  console.log('Professional invoice document smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }