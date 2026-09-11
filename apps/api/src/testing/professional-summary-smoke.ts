import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '71111111-1111-4111-8111-111111111111';
const userId = '72222222-2222-4222-8222-222222222222';
const campaignId = '73333333-3333-4333-8333-333333333333';
const fieldId = '74444444-4444-4444-8444-444444444444';
const customerA = '75555555-5555-4555-8555-555555555555';
const customerB = '76666666-6666-4666-8666-666666666666';

const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
  'content-type': 'application/json',
};

function near(actual: unknown, expected: number, label: string) {
  const value = Number(actual);
  if (!Number.isFinite(value) || Math.abs(value - expected) > 0.001) {
    throw new Error(`${label}: expected ${expected}, got ${String(actual)}`);
  }
}

async function createWork(payload: Record<string, unknown>) {
  const response = await app.inject({ method: 'POST', url: '/api/v1/works', headers, payload });
  if (response.statusCode !== 201) throw new Error(`Work create failed: ${response.statusCode} ${response.body}`);
}

async function main() {
  await sql`
    INSERT INTO users (id, primary_email, display_name)
    VALUES (${userId}::uuid, 'professional-ci@example.test', 'Professional CI')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO workspaces (id, name, type)
    VALUES (${workspaceId}::uuid, 'Professional CI Workspace', 'professional')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO workspace_memberships (workspace_id, user_id, role, status)
    VALUES (${workspaceId}::uuid, ${userId}::uuid, 'owner', 'active')
    ON CONFLICT (workspace_id, user_id) DO NOTHING;

    INSERT INTO campaigns (id, workspace_id, name, start_date, status)
    VALUES (${campaignId}::uuid, ${workspaceId}::uuid, 'Professional CI 2026/27', '2026-09-01', 'active')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status)
    VALUES (${fieldId}::uuid, ${workspaceId}::uuid, '77777777-7777-4777-8777-777777777777'::uuid, 'Finca Profesional CI', 'olivar', 'active')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles)
    VALUES
      (${customerA}::uuid, ${workspaceId}::uuid, '78888888-8888-4888-8888-888888888888'::uuid, 'person', 'Cliente Alfa', ARRAY['customer']),
      (${customerB}::uuid, ${workspaceId}::uuid, '79999999-9999-4999-8999-999999999999'::uuid, 'organization', 'Cliente Beta', ARRAY['customer'])
    ON CONFLICT (id) DO NOTHING;
  `.execute(db);

  await createWork({
    client_operation_id: '7aaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    entity_id: '7bbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    field_id: fieldId,
    campaign_id: campaignId,
    type: 'manual-work',
    occurred_on: '2026-10-01',
    title: 'Trabajo Alfa 1',
    performed_for: 'third-party',
    customer_party_id: customerA,
    charge_eur: 500,
    collected_eur: 200,
    participants: [{ display_name: 'Operario A', cost_eur: 100 }],
    resources: [{ kind: 'machinery', name: 'Máquina A', cost_eur: 50 }],
  });

  await createWork({
    client_operation_id: '7ccccccc-cccc-4ccc-8ccc-cccccccccccc',
    entity_id: '7ddddddd-dddd-4ddd-8ddd-dddddddddddd',
    field_id: fieldId,
    campaign_id: campaignId,
    type: 'transport',
    occurred_on: '2026-10-02',
    title: 'Trabajo Alfa 2',
    performed_for: 'third-party',
    customer_party_id: customerA,
    charge_eur: 200,
    collected_eur: 200,
    participants: [{ display_name: 'Operario B', cost_eur: 40 }],
    resources: [],
  });

  await createWork({
    client_operation_id: '7eeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    entity_id: '7fffffff-ffff-4fff-8fff-ffffffffffff',
    field_id: fieldId,
    campaign_id: campaignId,
    type: 'machinery-work',
    occurred_on: '2026-10-03',
    title: 'Trabajo Beta',
    performed_for: 'third-party',
    customer_party_id: customerB,
    charge_eur: 100,
    collected_eur: 0,
    participants: [],
    resources: [{ kind: 'machinery', name: 'Máquina B', cost_eur: 30 }],
  });

  const response = await app.inject({ method: 'GET', url: '/api/v1/professional/summary', headers });
  if (response.statusCode !== 200) throw new Error(`Professional summary failed: ${response.statusCode} ${response.body}`);
  const body = response.json();

  near(body.summary.work_count, 3, 'work_count');
  near(body.summary.customer_count, 2, 'customer_count');
  near(body.summary.direct_cost_eur, 220, 'direct_cost_eur');
  near(body.summary.charged_eur, 800, 'charged_eur');
  near(body.summary.collected_eur, 400, 'collected_eur');
  near(body.summary.pending_eur, 400, 'pending_eur');
  near(body.summary.accrued_margin_eur, 580, 'accrued_margin_eur');
  near(body.summary.collected_less_direct_costs_eur, 180, 'collected_less_direct_costs_eur');
  if ('cash_margin_eur' in body.summary) throw new Error('Legacy cash_margin_eur must not be exposed');

  const alpha = body.customers.find((item: { customer_id: string }) => item.customer_id === customerA);
  const beta = body.customers.find((item: { customer_id: string }) => item.customer_id === customerB);
  if (!alpha || !beta) throw new Error('Customer profitability rows missing');
  near(alpha.work_count, 2, 'alpha.work_count');
  near(alpha.direct_cost_eur, 190, 'alpha.direct_cost_eur');
  near(alpha.charged_eur, 700, 'alpha.charged_eur');
  near(alpha.collected_eur, 400, 'alpha.collected_eur');
  near(alpha.pending_eur, 300, 'alpha.pending_eur');
  near(alpha.accrued_margin_eur, 510, 'alpha.accrued_margin_eur');
  near(beta.pending_eur, 100, 'beta.pending_eur');
  near(beta.accrued_margin_eur, 70, 'beta.accrued_margin_eur');

  const paidWork = body.recent_work.find((item: { id: string }) => item.id === '7ddddddd-dddd-4ddd-8ddd-dddddddddddd');
  if (!paidWork || paidWork.payment_status !== 'paid') throw new Error(`Expected fully collected work to be paid, got ${paidWork?.payment_status}`);
  near(paidWork.accrued_margin_eur, 160, 'paidWork.accrued_margin_eur');

  if (!body.semantics?.collected_less_direct_costs?.includes('not cash flow')) {
    throw new Error('Professional semantics must explicitly reject cash-flow interpretation');
  }

  console.log('Professional summary smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
