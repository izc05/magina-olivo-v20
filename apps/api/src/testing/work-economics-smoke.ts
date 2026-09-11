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
const customerId = '25111111-1111-4111-8111-111111111111';
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function postWork(body: Record<string, unknown>) {
  const response = await app.inject({ method: 'POST', url: '/api/v1/works', headers, payload: body });
  if (response.statusCode !== 201 && response.statusCode !== 200) {
    throw new Error(`Work create failed: ${response.statusCode} ${response.body}`);
  }
  return response.json();
}

function near(actual: unknown, expected: number, label: string) {
  const value = Number(actual);
  if (!Number.isFinite(value) || Math.abs(value - expected) > 0.001) {
    throw new Error(`${label}: expected ${expected}, got ${String(actual)}`);
  }
}

async function main() {
  await postWork({
    client_operation_id: '21111111-1111-4111-8111-111111111111',
    entity_id: '22111111-1111-4111-8111-111111111111',
    field_id: fieldId,
    type: 'pruning',
    occurred_on: '2026-10-10',
    title: 'Poda económica CI',
    performed_for: 'self',
    participants: [
      { display_name: 'Cuadrilla CI', quantity: 2, unit: 'jornales', rate_eur: 80 },
    ],
    resources: [
      { kind: 'machinery', name: 'Trituradora CI', quantity: 3, unit: 'hours', unit_cost_eur: 20 },
      { kind: 'material', name: 'Consumible CI', quantity: 5, unit: 'units', unit_cost_eur: 4 },
    ],
  });

  await postWork({
    client_operation_id: '23111111-1111-4111-8111-111111111111',
    entity_id: '24111111-1111-4111-8111-111111111111',
    field_id: fieldId,
    type: 'manual-work',
    occurred_on: '2026-10-11',
    title: 'Trabajo sin coste CI',
    performed_for: 'self',
    participants: [
      { display_name: 'Propietario', cost_eur: 0 },
    ],
    resources: [
      { kind: 'service', name: 'Servicio propio', cost_eur: 0 },
    ],
  });

  await sql`
    INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, roles, active)
    VALUES (
      ${customerId}::uuid, ${workspaceId}::uuid,
      '26111111-1111-4111-8111-111111111111'::uuid,
      'person', 'Cliente CI', ARRAY['customer']::text[], TRUE
    ) ON CONFLICT (id) DO NOTHING
  `.execute(db);

  await postWork({
    client_operation_id: '27111111-1111-4111-8111-111111111111',
    entity_id: '28111111-1111-4111-8111-111111111111',
    field_id: fieldId,
    campaign_id: '22222222-2222-4222-8222-222222222222',
    type: 'machinery-work',
    occurred_on: '2026-10-12',
    title: 'Trabajo profesional CI',
    performed_for: 'third-party',
    customer_party_id: customerId,
    charge_eur: 500,
    collected_eur: 200,
    payment_status: 'partial',
    participants: [
      { display_name: 'Operario CI', quantity: 1, unit: 'jornales', rate_eur: 100 },
    ],
    resources: [
      { kind: 'machinery', name: 'Tractor cliente CI', quantity: 1, unit: 'hours', unit_cost_eur: 50 },
    ],
  });

  const participantZero = await sql<{ cost_eur: number | string | null }>`
    SELECT cost_eur FROM work_participants WHERE work_id = '24111111-1111-4111-8111-111111111111'::uuid LIMIT 1
  `.execute(db);
  const resourceZero = await sql<{ cost_eur: number | string | null }>`
    SELECT cost_eur FROM work_resources WHERE work_id = '24111111-1111-4111-8111-111111111111'::uuid LIMIT 1
  `.execute(db);
  if (participantZero.rows[0]?.cost_eur === null || Number(participantZero.rows[0]?.cost_eur) !== 0) throw new Error('Zero participant cost was not preserved');
  if (resourceZero.rows[0]?.cost_eur === null || Number(resourceZero.rows[0]?.cost_eur) !== 0) throw new Error('Zero resource cost was not preserved');

  const response = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldId}/economics-summary`,
    headers,
  });
  if (response.statusCode !== 200) throw new Error(`Economics summary failed: ${response.statusCode} ${response.body}`);
  const body = response.json();
  const breakdown = body.work_cost_breakdown;
  near(breakdown.labor_eur, 260, 'labor cost');
  near(breakdown.machinery_eur, 110, 'machinery cost');
  near(breakdown.materials_eur, 20, 'material cost');
  near(breakdown.services_eur, 0, 'service cost');
  near(breakdown.total_work_eur, 390, 'total work cost');

  near(body.professional_cost_eur, 150, 'professional cost');
  near(body.professional_work.charged_eur, 500, 'professional charged');
  near(body.professional_work.collected_eur, 200, 'professional collected');
  near(body.professional_work.pending_eur, 300, 'professional pending');
  near(body.professional_work.direct_cost_eur, 150, 'professional direct cost');
  near(body.professional_work.accrued_margin_eur, 350, 'professional accrued margin');
  near(Number(body.total_cost_eur) - Number(body.professional_cost_eur), body.production_cost_eur, 'production cost separation');
  near(
    Number(body.accrued_income_eur) + Number(body.professional_work.charged_eur) - Number(body.total_cost_eur),
    body.combined_accrued_margin_eur,
    'combined accrued margin',
  );
  if (Number(body.delivered_kg) > 0) {
    near(Number(body.production_cost_eur) / Number(body.delivered_kg), body.cost_per_delivered_kg_eur, 'production cost per kg');
  }

  const ledger = await sql<{ amount_eur: number | string }>`
    SELECT amount_eur FROM cost_ledger_projection
    WHERE domain_type = 'work' AND domain_record_id = '22111111-1111-4111-8111-111111111111'::uuid
  `.execute(db);
  near(ledger.rows[0]?.amount_eur, 240, 'self work ledger projection');

  const professionalLedger = await sql<{ amount_eur: number | string }>`
    SELECT amount_eur FROM cost_ledger_projection
    WHERE domain_type = 'work' AND domain_record_id = '28111111-1111-4111-8111-111111111111'::uuid
  `.execute(db);
  near(professionalLedger.rows[0]?.amount_eur, 150, 'professional work ledger projection');

  console.log('Work economics smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
