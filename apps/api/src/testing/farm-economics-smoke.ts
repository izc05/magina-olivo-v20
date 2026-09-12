import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const campaignId = '22222222-2222-4222-8222-222222222222';
const fieldId = '61616161-6161-4161-8161-616161616161';
const deliveryId = '62626262-6262-4262-8262-626262626262';
const settlementId = '63636363-6363-4363-8363-636363636363';
const collectionId = '64646464-6464-4464-8464-646464646464';
const costRecordId = '65656565-6565-4565-8565-656565656565';

const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
};

function near(actual: unknown, expected: number, label: string) {
  const value = Number(actual);
  if (!Number.isFinite(value) || Math.abs(value - expected) > 0.001) {
    throw new Error(`${label}: expected ${expected}, got ${String(actual)}`);
  }
}

async function main() {
  await sql`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status)
    VALUES (
      ${fieldId}::uuid, ${workspaceId}::uuid,
      '66666666-6666-4666-8666-666666666666'::uuid,
      'Finca Economics CI', 'olivar', 'active'
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_deliveries (
      id, workspace_id, campaign_id, cooperative_or_mill, delivery_at,
      ticket_number, total_kg, source, client_operation_id, created_by
    ) VALUES (
      ${deliveryId}::uuid, ${workspaceId}::uuid, ${campaignId}::uuid,
      'SCA Economics CI', '2026-12-10T09:00:00Z'::timestamptz,
      'ECO-CI-001', 1000, 'manual',
      '67676767-6767-4767-8767-676767676767'::uuid, ${userId}::uuid
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_delivery_fields (delivery_id, field_id, kg)
    VALUES (${deliveryId}::uuid, ${fieldId}::uuid, 1000)
    ON CONFLICT DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO cost_ledger_projection (
      workspace_id, field_id, campaign_id, occurred_on,
      domain_type, domain_record_id, category, amount_eur
    ) VALUES (
      ${workspaceId}::uuid, ${fieldId}::uuid, ${campaignId}::uuid, '2026-12-05'::date,
      'expense', ${costRecordId}::uuid, 'test', 300
    )
    ON CONFLICT (domain_type, domain_record_id) DO UPDATE SET amount_eur = EXCLUDED.amount_eur
  `.execute(db);

  await sql`
    INSERT INTO harvest_settlements (
      id, workspace_id, campaign_id, client_operation_id, counterparty_name,
      settlement_number, settled_on, basis, gross_eur, deductions_eur,
      net_eur, status, created_by
    ) VALUES (
      ${settlementId}::uuid, ${workspaceId}::uuid, ${campaignId}::uuid,
      '68686868-6868-4868-8868-686868686868'::uuid,
      'SCA Economics CI', 'ECO-LIQ-001', '2026-12-20'::date,
      'olive_kg', 1250, 50, 1200, 'confirmed', ${userId}::uuid
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_settlement_deliveries (settlement_id, delivery_id)
    VALUES (${settlementId}::uuid, ${deliveryId}::uuid)
    ON CONFLICT DO NOTHING
  `.execute(db);

  await sql`
    INSERT INTO harvest_collections (
      id, workspace_id, settlement_id, client_operation_id,
      collected_on, amount_eur, method, reference, created_by
    ) VALUES (
      ${collectionId}::uuid, ${workspaceId}::uuid, ${settlementId}::uuid,
      '69696969-6969-4969-8969-696969696969'::uuid,
      '2026-12-23'::date, 500, 'bank', 'ECO-COL-001', ${userId}::uuid
    )
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  const response = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldId}/economics-summary?campaignId=${campaignId}`,
    headers,
  });
  if (response.statusCode !== 200) throw new Error(`Economics summary failed: ${response.statusCode} ${response.body}`);

  const body = response.json();
  near(body.total_cost_eur, 300, 'total_cost_eur');
  near(body.accrued_income_eur, 1200, 'accrued_income_eur');
  near(body.collected_income_eur, 500, 'collected_income_eur');
  near(body.pending_collection_eur, 700, 'pending_collection_eur');
  near(body.accrued_margin_eur, 900, 'accrued_margin_eur');
  near(body.collected_less_registered_costs_eur, 200, 'collected_less_registered_costs_eur');
  near(body.delivered_kg, 1000, 'delivered_kg');
  near(body.cost_per_delivered_kg_eur, 0.3, 'cost_per_delivered_kg_eur');

  if ('cash_margin_eur' in body) throw new Error('Legacy cash_margin_eur must not be exposed');
  if (!body.semantics?.collected_less_registered_costs?.includes('not cash flow')) {
    throw new Error('Economics semantics must explicitly reject cash-flow interpretation');
  }

  console.log('Farm economics smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
