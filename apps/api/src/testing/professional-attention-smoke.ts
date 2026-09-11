import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '91111111-1111-4111-8111-111111111111';
const userId = '92222222-2222-4222-8222-222222222222';
const campaignId = '93333333-3333-4333-8333-333333333333';
const fieldId = '94444444-4444-4444-8444-444444444444';
const customerId = '95555555-5555-4555-8555-555555555555';
const invoicedWorkId = '96666666-6666-4666-8666-666666666666';
const unbilledWorkId = '97777777-7777-4777-8777-777777777777';
const invoiceId = '98888888-8888-4888-8888-888888888888';
const expiredQuoteId = '98999999-9999-4999-8999-999999999999';
const followupQuoteId = '98aaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const collectionId = '98dddddd-dddd-4ddd-8ddd-dddddddddddd';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name)
    VALUES (${userId}::uuid, 'professional-attention-ci@example.test', 'Professional Attention CI')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO workspaces (id, name, type)
    VALUES (${workspaceId}::uuid, 'Professional Attention CI', 'professional')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status)
    VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active')
    ON CONFLICT (workspace_id, user_id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO campaigns (id, workspace_id, name, start_date, status)
    VALUES (${campaignId}::uuid, ${workspaceId}::uuid, 'Professional Attention CI', CURRENT_DATE - 120, 'active')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status)
    VALUES (${fieldId}::uuid, ${workspaceId}::uuid, '99999999-9999-4999-8999-999999999991'::uuid, 'Professional Attention Field', 'olivar', 'active')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
    VALUES (${customerId}::uuid, ${workspaceId}::uuid, '99999999-9999-4999-8999-999999999992'::uuid, 'person', 'Cliente Seguimiento CI', ARRAY['customer'])
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO work_records (
      id, workspace_id, field_id, campaign_id, client_operation_id, type, occurred_on, title,
      performed_for, customer_party_id, charge_eur, collected_eur, payment_status, created_by
    ) VALUES
      (${invoicedWorkId}::uuid, ${workspaceId}::uuid, ${fieldId}::uuid, ${campaignId}::uuid,
       '99999999-9999-4999-8999-999999999993'::uuid, 'manual-work', CURRENT_DATE - 45, 'Trabajo facturado vencido CI',
       'third-party', ${customerId}::uuid, 500, 100, 'partial', ${userId}::uuid),
      (${unbilledWorkId}::uuid, ${workspaceId}::uuid, ${fieldId}::uuid, ${campaignId}::uuid,
       '99999999-9999-4999-8999-999999999994'::uuid, 'manual-work', CURRENT_DATE - 40, 'Trabajo sin facturar CI',
       'third-party', ${customerId}::uuid, 300, 0, 'pending', ${userId}::uuid)
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO work_collections (id, workspace_id, work_id, client_operation_id, collected_on, amount_eur, method, created_by)
    VALUES (${collectionId}::uuid, ${workspaceId}::uuid, ${invoicedWorkId}::uuid, '99999999-9999-4999-8999-999999999995'::uuid, CURRENT_DATE - 20, 100, 'bank', ${userId}::uuid)
    ON CONFLICT (workspace_id, client_operation_id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO professional_invoices (
      id, workspace_id, customer_party_id, client_operation_id, invoice_number, issued_on, due_on,
      status, subtotal_eur, tax_eur, total_eur, created_by
    ) VALUES (
      ${invoiceId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
      '99999999-9999-4999-8999-999999999996'::uuid, 'ATT-CI-001', CURRENT_DATE - 35, CURRENT_DATE - 10,
      'issued', 413.22, 86.78, 500, ${userId}::uuid
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO professional_invoice_works (invoice_id, work_id, amount_eur)
    VALUES (${invoiceId}::uuid, ${invoicedWorkId}::uuid, 500)
    ON CONFLICT DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO professional_quotes (
      id, workspace_id, customer_party_id, client_operation_id, quote_number, title, issued_on, valid_until,
      status, subtotal_eur, tax_eur, total_eur, created_by
    ) VALUES
      (${expiredQuoteId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
       '98bbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid, 'P-EXP-001', 'Presupuesto vencido CI', CURRENT_DATE - 20, CURRENT_DATE - 5,
       'sent', 661.16, 138.84, 800, ${userId}::uuid),
      (${followupQuoteId}::uuid, ${workspaceId}::uuid, ${customerId}::uuid,
       '98cccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid, 'P-FUP-001', 'Presupuesto seguimiento CI', CURRENT_DATE - 10, CURRENT_DATE + 10,
       'sent', 495.87, 104.13, 600, ${userId}::uuid)
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  const response = await app.inject({ method: 'GET', url: '/api/v1/professional/attention?limit=8', headers });
  if (response.statusCode !== 200) throw new Error(`Professional attention failed: ${response.statusCode} ${response.body}`);
  const body = response.json();

  if (body.summary.overdue_invoice_count !== 1) throw new Error(`Expected 1 overdue invoice, got ${body.summary.overdue_invoice_count}`);
  if (Math.abs(Number(body.summary.overdue_invoice_eur) - 400) > 0.001) throw new Error(`Expected 400 overdue EUR, got ${body.summary.overdue_invoice_eur}`);
  if (body.summary.unbilled_work_count !== 1) throw new Error(`Expected 1 unbilled work, got ${body.summary.unbilled_work_count}`);
  if (Math.abs(Number(body.summary.unbilled_work_eur) - 300) > 0.001) throw new Error(`Expected 300 unbilled EUR, got ${body.summary.unbilled_work_eur}`);
  if (body.summary.aged_customer_count !== 1) throw new Error(`Expected 1 aged customer, got ${body.summary.aged_customer_count}`);
  if (Math.abs(Number(body.summary.aged_receivable_eur) - 700) > 0.001) throw new Error(`Expected 700 aged receivable EUR, got ${body.summary.aged_receivable_eur}`);
  if (body.summary.expired_quote_count !== 1 || Math.abs(Number(body.summary.expired_quote_eur) - 800) > 0.001) throw new Error('Expired quote summary incorrect');
  if (body.summary.quote_followup_count !== 1 || Math.abs(Number(body.summary.quote_followup_eur) - 600) > 0.001) throw new Error('Quote follow-up summary incorrect');

  const overdue = body.overdue_invoices.find((item: { id: string }) => item.id === invoiceId);
  if (!overdue || Number(overdue.overdue_days) < 10) throw new Error('Overdue invoice missing or overdue days incorrect');
  const unbilled = body.unbilled_works.find((item: { id: string }) => item.id === unbilledWorkId);
  if (!unbilled || Number(unbilled.age_days) < 40) throw new Error('Unbilled work missing or age incorrect');
  const expired = body.expired_quotes.find((item: { id: string }) => item.id === expiredQuoteId);
  if (!expired || Number(expired.overdue_days) < 5) throw new Error('Expired quote missing or overdue days incorrect');
  const followup = body.quote_followups.find((item: { id: string }) => item.id === followupQuoteId);
  if (!followup || Number(followup.age_days) < 10) throw new Error('Quote follow-up missing or age incorrect');

  const statuses = await sql<{ id: string; status: string }>`SELECT id, status FROM professional_quotes WHERE id = ANY(${[expiredQuoteId, followupQuoteId]}::uuid[])`.execute(db);
  if (statuses.rows.some((item) => item.status !== 'sent')) throw new Error('Attention evaluation must not mutate quote status');

  console.log('Professional attention smoke test passed');
}

try { await main(); } finally { await app.close(); await db.destroy(); }