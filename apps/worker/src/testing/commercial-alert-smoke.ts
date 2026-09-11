import pg from 'pg';
import { runCommercialAlertEvaluationJob } from '../notifications/commercial-evaluate.js';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const pool = new Pool({ connectionString: databaseUrl });

const workspaceId = 'c1111111-1111-4111-8111-111111111111';
const userId = 'c2222222-2222-4222-8222-222222222222';
const customerId = 'c3333333-3333-4333-8333-333333333333';
const customerSiteId = 'c3aaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const invoiceWorkId = 'c4444444-4444-4444-8444-444444444444';
const unbilledWorkId = 'c5555555-5555-4555-8555-555555555555';
const invoiceId = 'c6666666-6666-4666-8666-666666666666';
const expiredQuoteId = 'c7777777-7777-4777-8777-777777777777';
const followupQuoteId = 'c8888888-8888-4888-8888-888888888888';

async function main() {
  await pool.query("INSERT INTO users (id, primary_email, display_name) VALUES ($1, 'commercial-alert-ci@example.test', 'Commercial Alert CI') ON CONFLICT (id) DO NOTHING", [userId]);
  await pool.query("INSERT INTO workspaces (id, name, type) VALUES ($1, 'Commercial Alert CI', 'professional') ON CONFLICT (id) DO NOTHING", [workspaceId]);
  await pool.query("INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES ($1, $2, 'owner', 'active') ON CONFLICT (workspace_id, user_id) DO NOTHING", [workspaceId, userId]);
  await pool.query(`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
      VALUES ($1, $2, 'c9999999-9999-4999-8999-999999999999', 'person', 'Cliente Alertas CI', ARRAY['customer']) ON CONFLICT (id) DO NOTHING
  `, [customerId, workspaceId]);
  await pool.query(`
    INSERT INTO customer_sites (id, workspace_id, client_operation_id, customer_party_id, name, active)
      VALUES ($1, $2, 'c9aaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', $3, 'Finca Alertas CI', TRUE) ON CONFLICT (id) DO NOTHING
  `, [customerSiteId, workspaceId, customerId]);

  await pool.query(`
    INSERT INTO work_records (id, workspace_id, client_operation_id, type, occurred_on, title, performed_for, customer_party_id, customer_site_id, charge_eur, collected_eur, payment_status, created_by)
    VALUES
      ($1, $3, 'ca111111-1111-4111-8111-111111111111', 'manual-work', CURRENT_DATE - 40, 'Trabajo facturado CI', 'third-party', $4, $5, 500, 0, 'pending', $6),
      ($2, $3, 'ca222222-2222-4222-8222-222222222222', 'manual-work', CURRENT_DATE - 30, 'Trabajo sin factura CI', 'third-party', $4, $5, 300, 0, 'pending', $6)
    ON CONFLICT (id) DO NOTHING;
  `, [invoiceWorkId, unbilledWorkId, workspaceId, customerId, customerSiteId, userId]);

  await pool.query(`
    INSERT INTO professional_invoices (id, workspace_id, customer_party_id, client_operation_id, invoice_number, issued_on, due_on, status, subtotal_eur, tax_eur, total_eur, created_by)
    VALUES ($1, $2, $3, 'ca333333-3333-4333-8333-333333333333', 'COM-CI-001', CURRENT_DATE - 30, CURRENT_DATE - 15, 'issued', 413.22, 86.78, 500, $4)
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO professional_invoice_works (invoice_id, work_id, amount_eur) VALUES ($1, $5, 500) ON CONFLICT DO NOTHING;
  `, [invoiceId, workspaceId, customerId, userId, invoiceWorkId]);

  await pool.query(`
    INSERT INTO professional_quotes (id, workspace_id, customer_party_id, client_operation_id, quote_number, title, issued_on, valid_until, status, subtotal_eur, tax_eur, total_eur, created_by)
    VALUES
      ($1, $3, $4, 'ca444444-4444-4444-8444-444444444444', 'P-COM-EXP', 'Presupuesto vencido CI', CURRENT_DATE - 20, CURRENT_DATE - 5, 'sent', 500, 105, 605, $5),
      ($2, $3, $4, 'ca555555-5555-4555-8555-555555555555', 'P-COM-FUP', 'Presupuesto seguimiento CI', CURRENT_DATE - 10, CURRENT_DATE + 10, 'sent', 400, 84, 484, $5)
    ON CONFLICT (id) DO NOTHING;
  `, [expiredQuoteId, followupQuoteId, workspaceId, customerId, userId]);

  await pool.query(`
    INSERT INTO commercial_notification_preferences (
      user_id, workspace_id, enabled, notify_overdue_invoices, overdue_invoice_days,
      notify_expired_quotes, notify_quote_followup, quote_followup_days,
      notify_unbilled_work, unbilled_work_days
    ) VALUES ($1, $2, TRUE, TRUE, 7, TRUE, TRUE, 7, TRUE, 14)
    ON CONFLICT (user_id, workspace_id) DO UPDATE SET enabled=TRUE, notify_overdue_invoices=TRUE, overdue_invoice_days=7,
      notify_expired_quotes=TRUE, notify_quote_followup=TRUE, quote_followup_days=7, notify_unbilled_work=TRUE, unbilled_work_days=14;
  `, [userId, workspaceId]);

  const first = await runCommercialAlertEvaluationJob(pool, { version: 1, limit_users: 200 });
  if (first.created !== 4) throw new Error(`Expected 4 commercial intents, got ${first.created}`);

  const rows = await pool.query<{ kind: string }>(`
    SELECT kind FROM notification_intents
    WHERE user_id=$1 AND workspace_id=$2 AND kind IN ('professional_invoice_overdue','professional_quote_expired','professional_quote_followup','professional_work_unbilled')
  `, [userId, workspaceId]);
  const kinds = new Set(rows.rows.map((row) => row.kind));
  for (const expected of ['professional_invoice_overdue','professional_quote_expired','professional_quote_followup','professional_work_unbilled']) {
    if (!kinds.has(expected)) throw new Error(`Missing intent kind ${expected}`);
  }

  const second = await runCommercialAlertEvaluationJob(pool, { version: 1, limit_users: 200 });
  if (second.created !== 0) throw new Error(`Expected dedupe on second evaluation, got ${second.created} new intents`);

  console.log('Commercial alert smoke test passed');
}

try { await main(); } finally { await pool.end(); }
