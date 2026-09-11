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
const headers = { 'x-workspace-id': workspaceId, 'x-user-id': userId, 'content-type': 'application/json' };

async function postWork(body: Record<string, unknown>) {
  const response = await app.inject({ method: 'POST', url: '/api/v1/works', headers, payload: body });
  if (response.statusCode !== 201 && response.statusCode !== 200) {
    throw new Error(`Work create failed: ${response.statusCode} ${response.body}`);
  }
  return response.json();
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
  if (Math.abs(Number(breakdown.labor_eur) - 160) > 0.001) throw new Error(`Unexpected labor cost: ${breakdown.labor_eur}`);
  if (Math.abs(Number(breakdown.machinery_eur) - 60) > 0.001) throw new Error(`Unexpected machinery cost: ${breakdown.machinery_eur}`);
  if (Math.abs(Number(breakdown.materials_eur) - 20) > 0.001) throw new Error(`Unexpected material cost: ${breakdown.materials_eur}`);
  if (Math.abs(Number(breakdown.services_eur) - 0) > 0.001) throw new Error(`Unexpected service cost: ${breakdown.services_eur}`);
  if (Math.abs(Number(breakdown.total_work_eur) - 240) > 0.001) throw new Error(`Unexpected total work cost: ${breakdown.total_work_eur}`);

  const ledger = await sql<{ amount_eur: number | string }>`
    SELECT amount_eur FROM cost_ledger_projection
    WHERE domain_type = 'work' AND domain_record_id = '22111111-1111-4111-8111-111111111111'::uuid
  `.execute(db);
  if (Math.abs(Number(ledger.rows[0]?.amount_eur) - 240) > 0.001) throw new Error(`Unexpected work ledger projection: ${ledger.rows[0]?.amount_eur}`);

  console.log('Work economics smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
