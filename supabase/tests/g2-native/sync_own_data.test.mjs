// Run only through run.sh. Real GoTrue + PostgREST; SQL connections use native psql.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { validateTarget } from './safety.mjs';

const work = process.env.G2_NATIVE_WORK;
assert.ok(work, 'Use the disposable run.sh runner');
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const container = validateTarget(process.env.G2_NATIVE_PROJECT, status.API_URL,
  readFileSync(join(work, 'supabase/config.toml'), 'utf8'));
const api = status.API_URL;
assert.ok(status.ANON_KEY && status.SERVICE_ROLE_KEY && status.JWT_SECRET, 'Local JWT signing fixture required');
const actors = {};
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const args = ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'];
function sql(input) {
  return execFileSync('docker', args, { input, encoding: 'utf8', timeout: 15000, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}

async function http(path, { token, admin = false, body, method = 'POST' } = {}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path, {
    method,
    headers: {
      apikey: key,
      Authorization: `Bearer ${token ?? key}`,
      'Content-Type': 'application/json'
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000)
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data };
}

before(async () => {
  // Create test actors in GoTrue Auth
  for (const actor of ['A', 'B']) {
    const email = `g2-${randomUUID()}@example.test`, password = `Fixture!${randomUUID()}`;
    const created = await http('/auth/v1/admin/users', { admin: true, body: { email, password, email_confirm: true } });
    assert.equal(created.status, 200);
    const login = await http('/auth/v1/token?grant_type=password', { body: { email, password } });
    assert.equal(login.status, 200);
    actors[actor] = {
      id: created.data.id,
      token: login.data.access_token,
      workspace: randomUUID(),
      farm: randomUUID(),
      campaign: randomUUID()
    };
  }

  // Apply baseline G0 migrations + G2.1 proposal into disposable native database
  const migrations = [
    '20261011120000_g0_auth_workspaces_shared_farms.sql',
    '20261011120001_g0_agricultural_sync_schema.sql',
    '20261011120002_g0_contracts_and_storage_rls.sql',
    '20261016041222_g0_legacy_storage_parcel_hardening.sql',
    '20261016041601_g0_aggregate_identity_guards.sql'
  ];
  const baselineDDL = migrations.map(name =>
    readFileSync(new URL(`../../migrations/${name}`, import.meta.url), 'utf8')
  ).join('\n');
  const proposalDDL = readFileSync(new URL('../../proposals/g2-sync-own-data.sql', import.meta.url), 'utf8');

  sql('BEGIN;\n' + baselineDDL + '\n' + proposalDDL + '\nCOMMIT; NOTIFY pgrst, \'reload schema\';');

  // Seed workspaces, farms, and campaigns for Actor A and Actor B
  for (const a of Object.values(actors)) {
    sql(`
      INSERT INTO public.workspaces(id, owner_user_id, name) VALUES ('${a.workspace}', '${a.id}', 'ephemeral workspace');
      INSERT INTO public.farms(id, workspace_id, creator_user_id, owner_id, name)
        VALUES ('${a.farm}', '${a.workspace}', '${a.id}', '${a.id}', 'ephemeral farm');
      INSERT INTO public.campaigns(id, workspace_id, farm_id, name, start_date)
        VALUES ('${a.campaign}', '${a.workspace}', '${a.farm}', 'Campaña 2026/2027', '2026-10-01');
    `);
  }

  // Ensure PostgREST picked up the new schema
  let ready = false;
  for (let i = 0; i < 40; i++) {
    const probe = await http('/rest/v1/rpc/check_sync_operations', {
      token: actors.A.token,
      body: { p_operation_ids: [randomUUID()] }
    });
    if (probe.status === 200) { ready = true; break; }
    await delay(250);
  }
  assert.ok(ready, 'PostgREST schema reload timed out');
});

test('sync_own_delivery inserts delivery idempotently and returns confirmation', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-15',
    destinationName: 'Cooperativa San Isidro',
    netGrams: 1540000,
    grossGrams: 2100000,
    tareGrams: 560000,
    deliveryNumber: 'ALB-001',
    ticketNumber: 'TK-12345',
    source: 'MANUAL',
    notes: 'Aceituna picual de vuelo'
  };

  const res = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });

  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.equal(res.data.success, true);
  assert.equal(res.data.operation_id, opId);
  assert.equal(res.data.entity_id, deliveryId);
  assert.equal(res.data.idempotent, false);

  // Check delivery in DB
  const count = sql(`SELECT count(*) FROM public.deliveries WHERE id = '${deliveryId}' AND net_grams = 1540000;`);
  assert.equal(count, '1');
});

test('duplicate operation_id with identical payload is an idempotent no-op', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-16',
    destinationName: 'Cooperativa San Isidro',
    netGrams: 2000000
  };

  // First sync
  const res1 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(res1.status, 200);
  assert.equal(res1.data.idempotent, false);

  // Re-sync with exact same operation_id (e.g. timeout retry)
  const res2 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(res2.status, 200);
  assert.equal(res2.data.idempotent, true);

  // Confirm exactly 1 row exists
  const total = sql(`SELECT count(*) FROM public.deliveries WHERE id = '${deliveryId}';`);
  assert.equal(total, '1');
});

test('conflicting payload with reused operation_id fails closed (23505 conflict)', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-17',
    destinationName: 'Cooperativa San Isidro',
    netGrams: 1000000
  };

  // First sync
  const res1 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(res1.status, 200);

  // Altered payload with same operation_id
  const altered = { ...delivery, netGrams: 9999999 };
  const res2 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: altered }
  });

  // PostgREST maps 23505 to 409 Conflict
  assert.ok([409, 400].includes(res2.status));
  assert.match(JSON.stringify(res2.data), /sync conflict: operation payload mismatch/);
});

test('cross-account isolation: Actor B receives 0 rows from Actor A', async () => {
  // Actor B queries deliveries via PostgREST
  const deliveriesB = await http('/rest/v1/deliveries?select=*', { token: actors.B.token, method: 'GET' });
  assert.equal(deliveriesB.status, 200);
  assert.equal(deliveriesB.data.length, 0);

  // Actor B queries expenses via PostgREST
  const expensesB = await http('/rest/v1/expenses?select=*', { token: actors.B.token, method: 'GET' });
  assert.equal(expensesB.status, 200);
  assert.equal(expensesB.data.length, 0);

  // Actor B queries farms via PostgREST -> only sees own farm, never Actor A farm
  const farmsB = await http('/rest/v1/farms?select=id', { token: actors.B.token, method: 'GET' });
  assert.equal(farmsB.status, 200);
  assert.ok(farmsB.data.every(f => f.id !== actors.A.farm));
});

test('cross-account write protection: Actor B cannot sync to Actor A farm', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const maliciousDelivery = {
    id: deliveryId,
    farmId: actors.A.farm, // Target Actor A's farm!
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-18',
    destinationName: 'Malicious Destination',
    netGrams: 500000
  };

  const res = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.B.token, // Actor B sending!
    body: { p_operation_id: opId, p_delivery: deliveryId }
  });

  // Must be rejected with 400/403 (42501 permission denied)
  assert.ok([400, 403].includes(res.status));
});

test('economic data protection: sync_own_expense succeeds for owner and isolates from other accounts', async () => {
  const opId = randomUUID();
  const expenseId = randomUUID();
  const expense = {
    id: expenseId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    expenseDate: '2026-11-10',
    concept: 'Abono foliar olivar',
    category: 'FERTILIZANTES',
    amountMinor: 145000, // 1450.00 EUR
    currency: 'EUR',
    provider: 'Suministros Agrícolas Mágina'
  };

  const res = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_expense: expense }
  });

  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.equal(res.data.success, true);
  assert.equal(res.data.entity_id, expenseId);

  // Actor A reads expense
  const resA = await http(`/rest/v1/expenses?id=eq.${expenseId}&select=*`, { token: actors.A.token, method: 'GET' });
  assert.equal(resA.status, 200);
  assert.equal(resA.data.length, 1);
  assert.equal(resA.data[0].amount_minor, 145000);

  // Actor B reads expense -> 0 rows (economic isolation)
  const resB = await http(`/rest/v1/expenses?id=eq.${expenseId}&select=*`, { token: actors.B.token, method: 'GET' });
  assert.equal(resB.status, 200);
  assert.equal(resB.data.length, 0);
});

test('immediate revocation: grant revocation blocks access instantly', async () => {
  // Actor A grants Actor B capability to view deliveries
  const grantId = randomUUID();
  sql(`
    INSERT INTO public.farm_access_grants(id, farm_id, user_id, granted_by_user_id, role, capabilities, status)
    VALUES ('${grantId}', '${actors.A.farm}', '${actors.B.id}', '${actors.A.id}', 'WORKER', ARRAY['view_deliveries'], 'ACTIVE');
  `);

  // Actor B can now view deliveries of Actor A farm
  const beforeRevoke = await http(`/rest/v1/deliveries?farm_id=eq.${actors.A.farm}&select=id`, {
    token: actors.B.token,
    method: 'GET'
  });
  assert.equal(beforeRevoke.status, 200);
  assert.ok(beforeRevoke.data.length >= 1);

  // Actor A revokes the grant
  sql(`UPDATE public.farm_access_grants SET status = 'REVOKED', revoked_at = now() WHERE id = '${grantId}';`);

  // Actor B immediately loses access (0 rows) without waiting for token refresh
  const afterRevoke = await http(`/rest/v1/deliveries?farm_id=eq.${actors.A.farm}&select=id`, {
    token: actors.B.token,
    method: 'GET'
  });
  assert.equal(afterRevoke.status, 200);
  assert.equal(afterRevoke.data.length, 0);
});

test('offline recovery: check_sync_operations reports confirmed operations', async () => {
  // Use a known operation from earlier
  const confirmedOp = randomUUID();
  const deliveryId = randomUUID();
  await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: {
      p_operation_id: confirmedOp,
      p_delivery: {
        id: deliveryId,
        farmId: actors.A.farm,
        campaignId: actors.A.campaign,
        deliveryDate: '2026-11-20',
        destinationName: 'Almazara Sierra Mágina',
        netGrams: 3000000
      }
    }
  });

  const unknownOp = randomUUID();
  const res = await http('/rest/v1/rpc/check_sync_operations', {
    token: actors.A.token,
    body: { p_operation_ids: [confirmedOp, unknownOp] }
  });

  assert.equal(res.status, 200);
  assert.equal(res.data.success, true);
  assert.ok(res.data.confirmed_operations.includes(confirmedOp));
  assert.ok(!res.data.confirmed_operations.includes(unknownOp));
});

test('stale version conflict protection: stale delivery or expense update is rejected', async () => {
  const deliveryId = randomUUID();
  const op1 = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-21',
    destinationName: 'Cooperativa San Isidro',
    netGrams: 1000000
  };

  // Initial insert -> version 1
  const ins = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: op1, p_delivery: delivery }
  });
  assert.equal(ins.status, 200);

  // Update with correct baseVersion (1) -> version becomes 2
  const op2 = randomUUID();
  const upd1 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: op2, p_delivery: { ...delivery, netGrams: 1100000, baseVersion: 1 } }
  });
  assert.equal(upd1.status, 200);

  // Update without baseVersion -> rejected (must provide baseVersion on update) with 409
  const opNoBase = randomUUID();
  const updNoBase = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opNoBase, p_delivery: { ...delivery, netGrams: 1150000 } }
  });
  assert.equal(updNoBase.status, 409, `Expected 409 on missing baseVersion, got ${updNoBase.status}`);
  assert.match(JSON.stringify(updNoBase.data), /sync conflict: baseVersion required for updating existing delivery/);

  // Update with stale baseVersion (1) -> rejected because current version is 2
  const op3 = randomUUID();
  const updStale = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: op3, p_delivery: { ...delivery, netGrams: 1200000, baseVersion: 1 } }
  });
  assert.equal(updStale.status, 409, `Expected 409 on stale delivery version, got ${updStale.status}`);
  assert.match(JSON.stringify(updStale.data), /sync conflict: stale delivery version/);

  // Same for expense:
  const expenseId = randomUUID();
  const opExp1 = randomUUID();
  const exp = {
    id: expenseId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    expenseDate: '2026-11-22',
    concept: 'Poda',
    category: 'LABORES',
    amountMinor: 50000
  };
  const expIns = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExp1, p_expense: exp }
  });
  assert.equal(expIns.status, 200);

  const opExp2 = randomUUID();
  const expUpd = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExp2, p_expense: { ...exp, amountMinor: 55000, baseVersion: 1 } }
  });
  assert.equal(expUpd.status, 200);

  // Update expense without baseVersion -> rejected 409
  const opExpNoBase = randomUUID();
  const expNoBase = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExpNoBase, p_expense: { ...exp, amountMinor: 58000 } }
  });
  assert.equal(expNoBase.status, 409, `Expected 409 on missing baseVersion for expense, got ${expNoBase.status}`);
  assert.match(JSON.stringify(expNoBase.data), /sync conflict: baseVersion required for updating existing expense/);

  const opExpStale = randomUUID();
  const expStale = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExpStale, p_expense: { ...exp, amountMinor: 60000, baseVersion: 1 } }
  });
  assert.equal(expStale.status, 409, `Expected 409 on stale expense version, got ${expStale.status}`);
  assert.match(JSON.stringify(expStale.data), /sync conflict: stale expense version/);
});

test('integrity: mismatched entity_type, entity_id or foreign campaign is rejected', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-23',
    destinationName: 'Cooperativa',
    netGrams: 800000
  };

  // 1. Sync delivery with opId
  const res1 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(res1.status, 200);

  // 2. Attempt to use same opId for an EXPENSE -> rejected with conflict
  const expRes = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: {
      p_operation_id: opId,
      p_expense: {
        id: randomUUID(),
        farmId: actors.A.farm,
        campaignId: actors.A.campaign,
        expenseDate: '2026-11-23',
        concept: 'Tractor',
        category: 'MAQUINARIA',
        amountMinor: 30000
      }
    }
  });
  assert.equal(expRes.status, 409);
  assert.match(JSON.stringify(expRes.data), /sync conflict: operation payload mismatch/);

  // 3. Foreign campaign from Actor B on Actor A farm -> rejected (22023)
  const opForeign = randomUUID();
  const foreignCampRes = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: {
      p_operation_id: opForeign,
      p_delivery: {
        id: randomUUID(),
        farmId: actors.A.farm,
        campaignId: actors.B.campaign, // Foreign campaign!
        deliveryDate: '2026-11-24',
        destinationName: 'Cooperativa',
        netGrams: 500000
      }
    }
  });
  assert.ok([400, 422].includes(foreignCampRes.status));
  assert.match(JSON.stringify(foreignCampRes.data), /sync invalid: campaign does not belong to farm/);

  // 4. Updating existing delivery with different campaign belonging to the same farm -> rejected 409
  const secondCampaignId = randomUUID();
  sql(`
    INSERT INTO public.campaigns(id, workspace_id, farm_id, name, start_date)
    VALUES ('${secondCampaignId}', '${actors.A.workspace}', '${actors.A.farm}', 'Campaña 2027/2028', '2027-10-01');
  `);

  const opMismatchCamp = randomUUID();
  const mismatchCampRes = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: {
      p_operation_id: opMismatchCamp,
      p_delivery: {
        ...delivery,
        campaignId: secondCampaignId,
        baseVersion: 1
      }
    }
  });
  assert.equal(mismatchCampRes.status, 409);
  assert.match(JSON.stringify(mismatchCampRes.data), /sync conflict: delivery campaign mismatch/);

  // Verify delivery campaign remains unchanged in DB
  const currentCampaign = sql(`SELECT campaign_id FROM public.deliveries WHERE id = '${deliveryId}';`);
  assert.equal(currentCampaign.trim(), actors.A.campaign);

  // Verify rejected operation was NOT recorded as applied
  const rejectedOpCount = sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id = '${opMismatchCamp}';`);
  assert.equal(rejectedOpCount.trim(), '0');
});

test('concurrency: simultaneous identical sync requests succeed idempotently without deadlocks or duplicates', async () => {
  const opId = randomUUID();
  const deliveryId = randomUUID();
  const delivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-25',
    destinationName: 'Cooperativa Sierra',
    netGrams: 2500000
  };

  // Launch 3 simultaneous requests with same operation_id
  const results = await Promise.all([
    http('/rest/v1/rpc/sync_own_delivery', { token: actors.A.token, body: { p_operation_id: opId, p_delivery: delivery } }),
    http('/rest/v1/rpc/sync_own_delivery', { token: actors.A.token, body: { p_operation_id: opId, p_delivery: delivery } }),
    http('/rest/v1/rpc/sync_own_delivery', { token: actors.A.token, body: { p_operation_id: opId, p_delivery: delivery } })
  ]);

  // All 3 must succeed (200 OK)
  for (const r of results) {
    assert.equal(r.status, 200, JSON.stringify(r.data));
    assert.equal(r.data.success, true);
    assert.equal(r.data.operation_id, opId);
  }

  // Exactly 1 row in deliveries table
  const total = sql(`SELECT count(*) FROM public.deliveries WHERE id = '${deliveryId}';`);
  assert.equal(total, '1');

  // Exactly 1 row in sync_operations table
  const totalOps = sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE account_id = '${actors.A.id}' AND operation_id = '${opId}';`);
  assert.equal(totalOps, '1');
});

test('concurrency: race between distinct operations on same version results in exactly one 200 and one 409', async () => {
  const initialOpId = randomUUID();
  const deliveryId = randomUUID();
  const initialDelivery = {
    id: deliveryId,
    farmId: actors.A.farm,
    campaignId: actors.A.campaign,
    deliveryDate: '2026-11-26',
    destinationName: 'Cooperativa Mancha Real',
    netGrams: 3000000
  };

  // Seed initial delivery (version 1)
  const seedRes = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: initialOpId, p_delivery: initialDelivery }
  });
  assert.equal(seedRes.status, 200);

  // Two distinct operations attempting to update version 1 simultaneously
  const opA = randomUUID();
  const opB = randomUUID();
  const payloadA = { ...initialDelivery, netGrams: 3100000, baseVersion: 1 };
  const payloadB = { ...initialDelivery, netGrams: 3200000, baseVersion: 1 };

  const [resA, resB] = await Promise.all([
    http('/rest/v1/rpc/sync_own_delivery', { token: actors.A.token, body: { p_operation_id: opA, p_delivery: payloadA } }),
    http('/rest/v1/rpc/sync_own_delivery', { token: actors.A.token, body: { p_operation_id: opB, p_delivery: payloadB } })
  ]);

  const statuses = [resA.status, resB.status].sort();
  assert.deepEqual(statuses, [200, 409], `Expected exactly one 200 and one 409, got ${JSON.stringify(statuses)}`);

  const winner = resA.status === 200 ? 'A' : 'B';
  const loserRes = winner === 'A' ? resB : resA;
  assert.match(JSON.stringify(loserRes.data), /sync conflict: stale delivery version/);

  // Delivery version in DB must be exactly 2
  const finalVersion = sql(`SELECT version FROM public.deliveries WHERE id = '${deliveryId}';`);
  assert.equal(finalVersion.trim(), '2');

  // Verify winning netGrams
  const finalGrams = sql(`SELECT net_grams FROM public.deliveries WHERE id = '${deliveryId}';`);
  const expectedGrams = winner === 'A' ? '3100000' : '3200000';
  assert.equal(finalGrams.trim(), expectedGrams);
});

