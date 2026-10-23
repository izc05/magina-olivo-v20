// Run only through run.sh. Real GoTrue + PostgREST; SQL connections use native psql.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash, createHmac } from 'node:crypto';
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
  const proposalDDL = ['g1c-reservations.sql', 'g2-sync-own-data.sql'].map(name =>
    readFileSync(new URL(`../../proposals/${name}`, import.meta.url), 'utf8')
  ).join('\n');

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
  assert.equal(res.data.remote_version, 1);

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
  assert.equal(res1.data.remote_version, 1);

  // Re-sync with exact same operation_id (e.g. timeout retry)
  const res2 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(res2.status, 200);
  assert.equal(res2.data.idempotent, true);
  assert.equal(res2.data.remote_version, 1);

  // Another operation advances the delivery to v2, but a late retry of the original
  // operation must still acknowledge its own committed v1 (not the current v2).
  const next = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: randomUUID(), p_delivery: { ...delivery, netGrams: 2100000, baseVersion: 1 } }
  });
  assert.equal(next.status, 200, JSON.stringify(next.data));
  assert.equal(next.data.remote_version, 2);
  const lateReplay = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: opId, p_delivery: delivery }
  });
  assert.equal(lateReplay.status, 200, JSON.stringify(lateReplay.data));
  assert.equal(lateReplay.data.idempotent, true);
  assert.equal(lateReplay.data.remote_version, 1);

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
  assert.equal(res2.status, 409);
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
  assert.equal(res.data.remote_version, 1);

  const expenseRecovery = await http('/rest/v1/rpc/check_sync_operations', {
    token: actors.A.token,
    body: { p_operation_ids: [opId] }
  });
  assert.equal(expenseRecovery.status, 200);
  assert.equal(expenseRecovery.data.confirmed_receipts[0].entity_type, 'EXPENSE');
  assert.equal(expenseRecovery.data.confirmed_receipts[0].entity_id, expenseId);
  assert.equal(expenseRecovery.data.confirmed_receipts[0].remote_version, 1);

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
  const originalReceipt = res.data.confirmed_receipts.find(x => x.operation_id === confirmedOp);
  assert.equal(originalReceipt.entity_id, deliveryId);
  assert.equal(originalReceipt.entity_type, 'DELIVERY');
  assert.equal(originalReceipt.remote_version, 1);
  assert.match(originalReceipt.payload_hash, /^[a-f0-9]{64}$/);
  assert.ok(!res.data.confirmed_receipts.some(x => x.operation_id === unknownOp));

  // The delivery is now on version 2, but recovery of an old operation must return
  // its historical v1, not the current delivery row's version.
  const changedOp = randomUUID();
  const update = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: {
      p_operation_id: changedOp,
      p_delivery: {
        id: deliveryId, farmId: actors.A.farm, campaignId: actors.A.campaign,
        deliveryDate: '2026-11-20', destinationName: 'Almazara Sierra Mágina',
        netGrams: 3500000, baseVersion: 1
      }
    }
  });
  assert.equal(update.status, 200, JSON.stringify(update.data));
  assert.equal(update.data.remote_version, 2);
  const recovered = await http('/rest/v1/rpc/check_sync_operations', {
    token: actors.A.token,
    body: { p_operation_ids: [confirmedOp, changedOp] }
  });
  assert.equal(recovered.status, 200, JSON.stringify(recovered.data));
  assert.equal(recovered.data.confirmed_receipts.find(x => x.operation_id === confirmedOp).remote_version, 1);
  assert.equal(recovered.data.confirmed_receipts.find(x => x.operation_id === changedOp).remote_version, 2);

  // Neither the operation IDs nor the committed versions of account A can be recovered by B.
  const foreign = await http('/rest/v1/rpc/check_sync_operations', {
    token: actors.B.token,
    body: { p_operation_ids: [confirmedOp, changedOp] }
  });
  assert.equal(foreign.status, 200);
  assert.deepEqual(foreign.data.confirmed_operations, []);
  assert.deepEqual(foreign.data.confirmed_receipts, []);

  // Bound the size of a recovery request and fail closed.
  const oversized = await http('/rest/v1/rpc/check_sync_operations', {
    token: actors.A.token,
    body: { p_operation_ids: Array.from({ length: 201 }, () => randomUUID()) }
  });
  assert.equal(oversized.status, 400);
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
  assert.equal(ins.data.remote_version, 1);

  // Update with correct baseVersion (1) -> version becomes 2
  const op2 = randomUUID();
  const upd1 = await http('/rest/v1/rpc/sync_own_delivery', {
    token: actors.A.token,
    body: { p_operation_id: op2, p_delivery: { ...delivery, netGrams: 1100000, baseVersion: 1 } }
  });
  assert.equal(upd1.status, 200);
  assert.equal(upd1.data.remote_version, 2);

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
  assert.equal(expIns.data.remote_version, 1);

  const opExp2 = randomUUID();
  const expUpd = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExp2, p_expense: { ...exp, amountMinor: 55000, baseVersion: 1 } }
  });
  assert.equal(expUpd.status, 200);
  assert.equal(expUpd.data.remote_version, 2);

  // Late duplicate of v1 must return its original receipt, not the current expense version.
  const expLateReplay = await http('/rest/v1/rpc/sync_own_expense', {
    token: actors.A.token,
    body: { p_operation_id: opExp1, p_expense: exp }
  });
  assert.equal(expLateReplay.status, 200, JSON.stringify(expLateReplay.data));
  assert.equal(expLateReplay.data.idempotent, true);
  assert.equal(expLateReplay.data.remote_version, 1);

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
    assert.equal(r.data.remote_version, 1);
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



// Freeze PostgreSQL JSONB bytes before implementing Kotlin recovery hashing.
test('recovery hash vectors use PostgreSQL JSONB text and UTF-8, not original JSON bytes', () => {
  const vectors = [
    ['key order and spaces', '{"b":2,"a":1}', '{"a": 1, "b": 2}'],
    ['UTF-8 key byte length', '{"é":2,"b":1}', '{"b": 1, "é": 2}'],
    ['Unicode and escapes', '{"notes":"M\\u00e1gina\\nOlivo","id":"x"}',
      '{"id": "x", "notes": "Mágina\\nOlivo"}'],
    ['exponent normalization and decimal scale', '{"n":1e2,"d":1.00}',
      '{"d": 1.00, "n": 100}'],
    ['nested objects arrays and null', '{"z":[null,{"b":2,"a":1}],"a":null}',
      '{"a": null, "z": [null, {"a": 1, "b": 2}]}'],
  ];
  for (const [name, input, expectedText] of vectors) {
    const result = JSON.parse(sql(
      "SELECT json_build_object('text', " + quote(input) + "::jsonb::text, " +
      "'hash', encode(sha256(convert_to(" + quote(input) + "::jsonb::text, 'UTF8')), 'hex'))::text;"
    ));
    assert.equal(result.text, expectedText, name);
    const expectedHash = createHash('sha256').update(expectedText, 'utf8').digest('hex');
    const originalHash = createHash('sha256').update(input, 'utf8').digest('hex');
    assert.equal(result.hash, expectedHash, name);
    assert.notEqual(result.hash, originalHash, name + ': compact raw bytes must differ');
  }
});


test('v1 RPC rejects complete v2 snapshots without partial delivery or false ACK', async () => {
  for (const extra of [
    { parcels: [] },
    { parcels: [{ id: randomUUID(), parcelId: randomUUID(), campaignParcelId: null,
      parcelNameAtDelivery: 'Parcela histórica', weightGrams: null, allocationMode: 'UNALLOCATED' }] },
    { destinationOrganizationId: randomUUID() },
    { payloadVersion: 2 }
  ]) {
    const operationId = randomUUID();
    const deliveryId = randomUUID();
    const payload = {
      id: deliveryId, farmId: actors.A.farm, campaignId: actors.A.campaign,
      deliveryDate: '2026-11-25', destinationName: 'Cooperativa sintética', netGrams: 3000000,
      ...extra
    };
    const result = await http('/rest/v1/rpc/sync_own_delivery', {
      token: actors.A.token, body: { p_operation_id: operationId, p_delivery: payload }
    });
    assert.equal(result.status, 400, JSON.stringify(result.data));
    assert.match(JSON.stringify(result.data), /sync unsupported/);
    assert.equal(sql("SELECT count(*) FROM public.deliveries WHERE id=" + quote(deliveryId) + "::uuid;"), '0');
    assert.equal(sql("SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id=" +
      quote(operationId) + "::uuid;"), '0');
    const recovery = await http('/rest/v1/rpc/check_sync_operations', {
      token: actors.A.token, body: { p_operation_ids: [operationId] }
    });
    assert.equal(recovery.status, 200);
    assert.deepEqual(recovery.data.confirmed_operations, []);
    assert.deepEqual(recovery.data.confirmed_receipts, []);
  }
});


function completeDeliveryFixture() {
  const workspace = randomUUID(), farm = randomUUID(), campaign = randomUUID();
  const parcelA = randomUUID(), parcelB = randomUUID();
  sql("INSERT INTO public.workspaces(id,owner_user_id,name) VALUES ("+
    quote(workspace)+","+quote(actors.A.id)+",'v2 fixture');"+
    "INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES ("+
    [farm,workspace,actors.A.id,actors.A.id].map(quote).join(',')+",'v2 farm');"+
    "INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date) VALUES ("+
    [campaign,workspace,farm].map(quote).join(',')+",'v2 campaign','2026-01-01');");
  for (const parcel of [parcelA,parcelB]) {
    sql("INSERT INTO public.parcels(id,workspace_id,display_name) VALUES ("+
      [parcel,workspace,'Current parcel name'].map(quote).join(',')+");"+
      "INSERT INTO public.farm_parcel_memberships(workspace_id,farm_id,parcel_id,valid_from) VALUES ("+
      [workspace,farm,parcel].map(quote).join(',')+",'2026-01-01');");
  }
  const payload = {
    id: randomUUID(), farmId: farm, campaignId: campaign, deliveryDate:'2026-11-25',
    destinationName:'Synthetic cooperative', destinationOrganizationId:randomUUID(),
    netGrams:3000000, source:'MANUAL',
    parcels:[
      {id:randomUUID(),parcelId:parcelA,campaignParcelId:randomUUID(),
        parcelNameAtDelivery:'Historical A',weightGrams:1000000,allocationMode:'EXACT'},
      {id:randomUUID(),parcelId:parcelB,campaignParcelId:null,
        parcelNameAtDelivery:'Historical B',weightGrams:null,allocationMode:'UNALLOCATED'}
    ].sort((a,b)=>a.parcelId.localeCompare(b.parcelId))
  };
  return {payload,workspace,farm};
}
async function sendComplete(operationId,payload,token=actors.A.token) {
  return http('/rest/v1/rpc/sync_own_delivery_v2',{
    token,body:{p_operation_id:operationId,p_delivery:payload}
  });
}
async function readComplete(id,token=actors.A.token) {
  return http('/rest/v1/rpc/get_own_delivery_v2',{token,body:{p_delivery_id:id}});
}

// Same issuer fixture as the G1c native suite. GoTrue creates the real account;
// only the HTTP-local issuer is replaced and signed with the DISPOSABLE key.
// This proves gateway verification + SQL authorization, not production HTTPS Auth.
function reservationGatewayToken(actor) {
  const claims = JSON.parse(Buffer.from(actor.token.split('.')[1], 'base64url'));
  assert.ok(claims.iss.startsWith('http://'), 'Review local Auth issuer fixture');
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const body = `${part({alg:'HS256',typ:'JWT'})}.${part({
    ...claims, iss:'https://g1c-native.example.test/auth/v1'
  })}`;
  return body + '.' + createHmac('sha256', status.JWT_SECRET).update(body).digest('base64url');
}

async function clientBootstrapFixture(pendingFarmOnly=false) {
  const actor = actors.A, token = reservationGatewayToken(actor);
  const workspace = randomUUID(), farm = randomUUID(), campaign = randomUUID(), parcel = randomUUID();
  const create = async (table, body) => {
    const result = await http(`/rest/v1/${table}`, {token, body});
    assert.equal(result.status, 201, `${table}: ${JSON.stringify(result.data)}`);
  };
  // Every agricultural row below is created through ordinary authenticated REST,
  // never SQL or the service-role key. Identifiers are persisted fixture input.
  const workspaceBody={p_operation_id:randomUUID(),p_workspace:{id:workspace,name:'Client-created workspace',
    countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}};
  const firstWorkspace=await http('/rest/v1/rpc/create_own_workspace',{token,body:workspaceBody});
  assert.equal(firstWorkspace.status,200,JSON.stringify(firstWorkspace.data));
  const workspaceReplay=await http('/rest/v1/rpc/create_own_workspace',{token,body:workspaceBody});
  assert.equal(workspaceReplay.status,200);
  assert.equal(workspaceReplay.data.idempotent,true);
  assert.equal(workspaceReplay.data.entity_id,workspace);
  const request = {payloadVersion:1, operationId:randomUUID(), farmId:farm,
    localWorkspaceId:randomUUID(), consentId:randomUUID(), consentTextVersion:2,
    manifestSha256:'a'.repeat(64), aggregateVersion:1};
  const reserve = () => http('/rest/v1/rpc/reserve_local_farm_g1c', {
    token, body:{p_request:request, p_remote_workspace:workspace}
  });
  const reservation = await reserve();
  assert.equal(reservation.status, 200, JSON.stringify(reservation.data));
  assert.equal(reservation.data.actorId, actor.id);
  assert.equal(reservation.data.farmId, farm);
  assert.equal(reservation.data.remoteWorkspaceId, workspace);
  const farmBody={p_operation_id:randomUUID(),p_reservation_operation_id:request.operationId,
    p_reservation_receipt_id:reservation.data.receiptId,p_farm:{id:farm,workspaceId:workspace,
      name:'Client-created farm',description:'Synthetic grove',municipality:'Synthetic municipality',
      province:null,notes:'Preserved farm notes',status:'ACTIVE',coverDocumentId:randomUUID()}};
  if(pendingFarmOnly) return {workspace,farm,token,request,reservation,farmBody,workspaceOperationId:workspaceBody.p_operation_id};
  const firstFarm=await http('/rest/v1/rpc/create_reserved_farm',{token,body:farmBody});
  assert.equal(firstFarm.status,200,JSON.stringify(firstFarm.data));
  const farmReplay=await http('/rest/v1/rpc/create_reserved_farm',{token,body:farmBody});
  assert.equal(farmReplay.status,200);assert.equal(farmReplay.data.idempotent,true);
  // A lost reservation ACK can be recovered even after the farm was materialized.
  assert.deepEqual((await reserve()).data, reservation.data);
  await create('campaigns', {id:campaign, workspace_id:workspace, farm_id:farm,
    name:'Client-created campaign', start_date:'2026-01-01'});
  await create('parcels', {id:parcel, workspace_id:workspace, display_name:'Client-created parcel'});
  await create('farm_parcel_memberships', {id:randomUUID(), workspace_id:workspace,
    farm_id:farm, parcel_id:parcel, valid_from:'2026-01-01T00:00:00Z'});
  const payload = {id:randomUUID(), farmId:farm, campaignId:campaign,
    deliveryDate:'2026-11-25', destinationName:'Synthetic destination', netGrams:3000000,
    source:'MANUAL', parcels:[{id:randomUUID(), parcelId:parcel, campaignParcelId:null,
      parcelNameAtDelivery:'Historical client parcel', weightGrams:null, allocationMode:'UNALLOCATED'}]};
  return {workspace, farm, campaign, parcel, token, request, reservation, payload};
}

test('authenticated REST bootstrap links G1c reservation to complete G2 delivery and recovery', async () => {
  const fixture = await clientBootstrapFixture(), operation = randomUUID();
  const {payload, token} = fixture;
  const result = await sendComplete(operation, payload, token);
  assert.equal(result.status, 200, JSON.stringify(result.data));
  assert.equal(result.data.remote_version, 1);
  const replay = await sendComplete(operation, payload, token);
  assert.equal(replay.status, 200);
  assert.equal(replay.data.idempotent, true);
  assert.equal(replay.data.remote_version, 1);
  const restored = await readComplete(payload.id, token);
  assert.equal(restored.status, 200);
  assert.equal(restored.data.delivery.farm_id, fixture.farm);
  assert.equal(restored.data.delivery.campaign_id, fixture.campaign);
  assert.equal(restored.data.parcels.length, 1);
  assert.equal(restored.data.parcels[0].id, payload.parcels[0].id);
  assert.equal(restored.data.parcels[0].weight_grams, null);
  assert.equal(restored.data.parcels[0].parcel_name_at_delivery, 'Historical client parcel');
  const recovery = await http('/rest/v1/rpc/check_sync_operations', {
    token, body:{p_operation_ids:[operation]}
  });
  assert.equal(recovery.status, 200);
  const receipt = recovery.data.confirmed_receipts.find(x => x.operation_id === operation);
  assert.ok(receipt);
  assert.equal(receipt.payload_version, 2);
  assert.equal(receipt.remote_version, 1);
  for (const [table,id] of [['workspaces',fixture.workspace],['farms',fixture.farm],
    ['campaigns',fixture.campaign],['parcels',fixture.parcel],['deliveries',payload.id]]) {
    const foreign = await http(`/rest/v1/${table}?id=eq.${id}`, {token:actors.B.token, method:'GET'});
    assert.equal(foreign.status, 200);
    assert.deepEqual(foreign.data, [], table);
  }
  assert.equal((await readComplete(payload.id,actors.B.token)).status,403);
});

test('reservation prevents another account materializing or claiming the selected farm', async () => {
  const workspace = randomUUID(), farm = randomUUID(), token = reservationGatewayToken(actors.A);
  const created = await http('/rest/v1/workspaces', {token,
    body:{id:workspace,owner_user_id:actors.A.id,name:'Reservation-only fixture'}});
  assert.equal(created.status,201,JSON.stringify(created.data));
  const request = {payloadVersion:1,operationId:randomUUID(),farmId:farm,
    localWorkspaceId:randomUUID(),consentId:randomUUID(),consentTextVersion:2,
    manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const first = await http('/rest/v1/rpc/reserve_local_farm_g1c', {
    token,body:{p_request:request,p_remote_workspace:workspace}
  });
  assert.equal(first.status,200,JSON.stringify(first.data));
  const foreignToken = reservationGatewayToken(actors.B);
  const claim = await http('/rest/v1/rpc/reserve_local_farm_g1c', {
    token:foreignToken,body:{p_request:{...request,operationId:randomUUID()},p_remote_workspace:actors.B.workspace}
  });
  assert.equal(claim.status,409);
  const foreignInsert = await http('/rest/v1/farms', {token:foreignToken,
    body:{id:farm,workspace_id:actors.B.workspace,creator_user_id:actors.B.id,
      owner_id:actors.B.id,name:'Must not be created'}});
  assert.equal(foreignInsert.status,409,JSON.stringify(foreignInsert.data));
  assert.equal(sql(`SELECT count(*) FROM public.farms WHERE id=${quote(farm)}::uuid;`),'0');
  const ownerInsert = await http('/rest/v1/farms', {token,
    body:{id:farm,workspace_id:workspace,creator_user_id:actors.A.id,
      owner_id:actors.A.id,name:'Reserved owner farm'}});
  assert.equal(ownerInsert.status,201,JSON.stringify(ownerInsert.data));
});

test('workspace bootstrap survives replay, preserves later edits and serializes concurrent creation',async()=>{
  const payload={id:randomUUID(),name:'Resumable workspace',countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'};
  const operation=randomUUID();
  const create=(op=operation,value=payload,token=actors.A.token)=>http('/rest/v1/rpc/create_own_workspace',{
    token,body:{p_operation_id:op,p_workspace:value}
  });
  const concurrent=await Promise.all([create(),create(),create()]);
  concurrent.forEach(result=>assert.equal(result.status,200,JSON.stringify(result.data)));
  assert.equal(concurrent.filter(result=>result.data.idempotent===false).length,1);
  assert.equal(sql(`SELECT count(*) FROM public.workspaces WHERE id=${quote(payload.id)}::uuid;`),'1');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE account_id=${quote(actors.A.id)}::uuid AND operation_id=${quote(operation)}::uuid;`),'1');
  const edited=await http(`/rest/v1/workspaces?id=eq.${payload.id}`,{token:actors.A.token,method:'PATCH',
    body:{name:'Later workspace name',version:2}});
  assert.equal(edited.status,204,JSON.stringify(edited.data));
  const replay=await create();
  assert.equal(replay.status,200);
  assert.equal(replay.data.remote_version,1);
  assert.equal(sql(`SELECT name FROM public.workspaces WHERE id=${quote(payload.id)}::uuid;`),'Later workspace name');
  const recovery=await http('/rest/v1/rpc/check_sync_operations',{
    token:actors.A.token,body:{p_operation_ids:[operation]}
  });
  assert.equal(recovery.status,200);
  const receipt=recovery.data.confirmed_receipts[0];
  assert.equal(receipt.entity_type,'WORKSPACE');
  assert.equal(receipt.entity_id,payload.id);
  assert.equal(receipt.remote_version,1);
  assert.equal((await create(operation,{...payload,name:'Changed operation'})).status,409);
  assert.equal((await create(randomUUID(),payload)).status,409);
  assert.equal((await create(randomUUID(),payload,actors.B.token)).status,409);
  const crossEntityId=randomUUID();
  const crossEntity=await http('/rest/v1/rpc/sync_own_delivery',{
    token:actors.A.token,body:{p_operation_id:operation,p_delivery:{id:crossEntityId,
      farmId:actors.A.farm,campaignId:actors.A.campaign,deliveryDate:'2026-11-25',
      destinationName:'Operation ID collision',netGrams:1000000}}
  });
  assert.equal(crossEntity.status,409);
  assert.equal(sql(`SELECT count(*) FROM public.deliveries WHERE id=${quote(crossEntityId)}::uuid;`),'0');
  // Two different operations racing for a fresh UUID cannot adopt each other's row.
  const second={...payload,id:randomUUID()};
  const contenders=await Promise.all([create(randomUUID(),second),create(randomUUID(),second)]);
  assert.deepEqual(contenders.map(x=>x.status).sort(),[200,409]);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${quote(second.id)}::uuid;`),'1');
  const denied=await http('/rest/v1/rpc/check_sync_operations',{
    token:actors.B.token,body:{p_operation_ids:[operation]}
  });
  assert.deepEqual(denied.data.confirmed_receipts,[]);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${quote(payload.id)}::uuid;`);
  assert.equal((await create()).status,403);
});

test('workspace bootstrap never adopts own legacy IDs and rejects anonymous or malformed requests',async()=>{
  const payload={id:actors.A.workspace,name:'ephemeral workspace',countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'};
  const create=(value,token=actors.A.token)=>http('/rest/v1/rpc/create_own_workspace',{
    token,body:{p_operation_id:randomUUID(),p_workspace:value}
  });
  assert.equal((await create(payload)).status,409);
  const id=randomUUID();
  for(const value of [{...payload,id,ownerUserId:actors.B.id},{...payload,id,name:{}},
    {...payload,id,timezone:'Invalid/Timezone'},{...payload,id,currency:null}])
    assert.equal((await create(value)).status,400);
  const anonymous=await http('/auth/v1/signup',{body:{}});
  assert.equal(anonymous.status,200);
  assert.ok(anonymous.data.access_token);
  assert.equal((await create({...payload,id},anonymous.data.access_token)).status,403);
  const unauthenticated=await http('/rest/v1/rpc/create_own_workspace',{
    body:{p_operation_id:randomUUID(),p_workspace:{...payload,id}}
  });
  assert.ok([401,403].includes(unauthenticated.status));
  assert.equal(sql(`SELECT count(*) FROM public.workspaces WHERE id=${quote(id)}::uuid;`),'0');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${quote(id)}::uuid;`),'0');
});

test('reserved farm bootstrap atomically stores all fields and recovers the historical receipt under concurrency',async()=>{
  const f=await clientBootstrapFixture(true);
  const create=(body=f.farmBody,token=f.token)=>http('/rest/v1/rpc/create_reserved_farm',{token,body});
  const replies=await Promise.all([create(),create(),create()]);
  replies.forEach(result=>assert.equal(result.status,200,JSON.stringify(result.data)));
  assert.equal(replies.filter(result=>result.data.idempotent===false).length,1);
  const row=await http(`/rest/v1/farms?id=eq.${f.farm}`,{token:f.token,method:'GET'});
  assert.equal(row.status,200);assert.equal(row.data.length,1);
  const stored=row.data[0],payload=f.farmBody.p_farm;
  assert.equal(stored.workspace_id,f.workspace);assert.equal(stored.creator_user_id,actors.A.id);
  assert.equal(stored.owner_id,actors.A.id);assert.equal(stored.name,payload.name);
  assert.equal(stored.description,payload.description);assert.equal(stored.municipality,payload.municipality);
  assert.equal(stored.province,null);assert.equal(stored.notes,payload.notes);
  assert.equal(stored.status,payload.status);assert.equal(stored.cover_document_id,payload.coverDocumentId);
  assert.equal(stored.cover_image_uri,null);
  const edited=await http(`/rest/v1/farms?id=eq.${f.farm}`,{token:f.token,method:'PATCH',body:{name:'Later farm name',version:2}});
  assert.equal(edited.status,204);
  const replay=await create();assert.equal(replay.status,200);assert.equal(replay.data.remote_version,1);
  assert.equal(sql(`SELECT name FROM public.farms WHERE id=${quote(f.farm)}::uuid;`),'Later farm name');
  const recovered=await http('/rest/v1/rpc/check_sync_operations',{
    token:f.token,body:{p_operation_ids:[f.farmBody.p_operation_id]}
  });
  assert.equal(recovered.status,200);assert.equal(recovered.data.confirmed_receipts.length,1);
  assert.equal(recovered.data.confirmed_receipts[0].entity_type,'FARM');
  assert.equal(recovered.data.confirmed_receipts[0].remote_version,1);
  assert.equal((await create({...f.farmBody,p_farm:{...payload,notes:'Changed operation'}})).status,409);
  assert.equal((await create({...f.farmBody,p_operation_id:randomUUID()})).status,409);
  assert.equal((await create(f.farmBody,reservationGatewayToken(actors.B))).status,403);
  const foreignRead=await http(`/rest/v1/farms?id=eq.${f.farm}`,{token:actors.B.token,method:'GET'});
  assert.deepEqual(foreignRead.data,[]);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='FARM' AND entity_id=${quote(f.farm)}::uuid;`),'1');
  sql(`UPDATE public.farms SET deleted_at=now() WHERE id=${quote(f.farm)}::uuid;`);
  assert.equal((await create()).status,403);
});

test('reserved farm bootstrap denies mismatched reservations and different operations cannot adopt the same ID',async()=>{
  const f=await clientBootstrapFixture(true);
  const create=(body,token=f.token)=>http('/rest/v1/rpc/create_reserved_farm',{token,body});
  for(const body of [{...f.farmBody,p_reservation_operation_id:randomUUID()},
    {...f.farmBody,p_reservation_receipt_id:randomUUID()},
    {...f.farmBody,p_farm:{...f.farmBody.p_farm,id:randomUUID()}},
    {...f.farmBody,p_farm:{...f.farmBody.p_farm,workspaceId:actors.B.workspace}}])
    assert.equal((await create(body)).status,403);
  // The native HTTP issuer cannot stand in for the HTTPS issuer stored in this reservation fixture.
  assert.equal((await create(f.farmBody,actors.A.token)).status,403);
  for(const p_farm of [{...f.farmBody.p_farm,ownerUserId:actors.A.id},
    {...f.farmBody.p_farm,notes:123},{...f.farmBody.p_farm,coverDocumentId:'not-a-uuid'},
    {...f.farmBody.p_farm,status:'DELETED'}])
    assert.equal((await create({...f.farmBody,p_farm})).status,400);
  assert.equal((await create({...f.farmBody,p_operation_id:f.workspaceOperationId})).status,409);
  assert.equal(sql(`SELECT count(*) FROM public.farms WHERE id=${quote(f.farm)}::uuid;`),'0');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='FARM' AND entity_id=${quote(f.farm)}::uuid;`),'0');
  const results=await Promise.all([create(f.farmBody),create({...f.farmBody,p_operation_id:randomUUID()})]);
  assert.deepEqual(results.map(x=>x.status).sort(),[200,409]);
  assert.equal(sql(`SELECT count(*) FROM public.farms WHERE id=${quote(f.farm)}::uuid;`),'1');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='FARM' AND entity_id=${quote(f.farm)}::uuid;`),'1');
});

test('v2 persists and restores historical parcel shares with idempotent versioned receipts',async()=>{
  const {payload}=completeDeliveryFixture(), firstId=randomUUID();
  const wrongActor=await sendComplete(randomUUID(),payload,actors.B.token);
  assert.equal(wrongActor.status,403,JSON.stringify(wrongActor.data));
  const first=await sendComplete(firstId,payload);
  assert.equal(first.status,200,JSON.stringify(first.data));
  assert.equal(first.data.payload_version,2);
  assert.equal(first.data.remote_version,1);
  const restored=await readComplete(payload.id);
  assert.equal(restored.status,200,JSON.stringify(restored.data));
  assert.equal(restored.data.delivery.destination_organization_id,payload.destinationOrganizationId);
  assert.equal(restored.data.delivery.snapshot_format,2);
  for(const original of payload.parcels) {
    const child=restored.data.parcels.find(x=>x.parcel_id===original.parcelId);
    assert.equal(child.id,original.id);
    assert.equal(child.campaign_parcel_id,original.campaignParcelId);
    assert.equal(child.parcel_name_at_delivery,original.parcelNameAtDelivery);
    assert.equal(child.weight_grams,original.weightGrams);
    assert.equal(child.allocation_mode,original.allocationMode);
  }
  const next={...payload,baseVersion:1,parcels:payload.parcels.map(x=>({
    ...x,weightGrams:1500000,allocationMode:'EXACT'
  }))};
  const secondId=randomUUID(),second=await sendComplete(secondId,next);
  assert.equal(second.status,200,JSON.stringify(second.data));
  assert.equal(second.data.remote_version,2);
  const replay=await sendComplete(firstId,payload);
  assert.equal(replay.status,200);
  assert.equal(replay.data.idempotent,true);
  assert.equal(replay.data.remote_version,1);
  const current=await readComplete(payload.id);
  assert.equal(current.data.delivery.version,2);
  assert.deepEqual(current.data.parcels.map(x=>x.weight_grams),[1500000,1500000]);
  const recovery=await http('/rest/v1/rpc/check_sync_operations',{
    token:actors.A.token,body:{p_operation_ids:[firstId,secondId]}
  });
  assert.equal(recovery.status,200);
  for(const [id,version] of [[firstId,1],[secondId,2]]) {
    const receipt=recovery.data.confirmed_receipts.find(x=>x.operation_id===id);
    assert.equal(receipt.remote_version,version);
    assert.equal(receipt.payload_version,2);
  }
  assert.equal((await readComplete(payload.id,actors.B.token)).status,403);
  const foreign=await http('/rest/v1/delivery_parcels?delivery_id=eq.'+payload.id,{
    token:actors.B.token,method:'GET'
  });
  assert.equal(foreign.status,200);
  assert.deepEqual(foreign.data,[]);
  const downgrade=await http('/rest/v1/rpc/sync_own_delivery',{
    token:actors.A.token,body:{p_operation_id:randomUUID(),p_delivery:{
      id:payload.id,farmId:payload.farmId,campaignId:payload.campaignId,
      deliveryDate:payload.deliveryDate,destinationName:payload.destinationName,
      netGrams:payload.netGrams,baseVersion:2
    }}
  });
  assert.equal(downgrade.status,409,JSON.stringify(downgrade.data));
});

test('v2 rejects invalid parcel snapshots atomically and forbids rewriting history',async()=>{
  const {payload}=completeDeliveryFixture();
  const bad=[
    {...payload,parcels:[]},
    {...payload,netGrams:0},
    {...payload,netGrams:"3000000"},
    {...payload,destinationName:{name:'Cooperative'}},
    {...payload,notes:123},
    {...payload,parcels:[payload.parcels[0],payload.parcels[0]]},
    {...payload,parcels:payload.parcels.map(x=>({...x,weightGrams:1,allocationMode:'EXACT'}))},
    {...payload,parcels:payload.parcels.map(x=>({...x,weightGrams:0,allocationMode:'EXACT'}))},
    {...payload,parcels:[{...payload.parcels[0],parcelId:randomUUID()}]},
    {...payload,parcels:[{...payload.parcels[0],unsupported:'discard me'}]}
  ];
  for(const candidate of bad) {
    const operation=randomUUID(),id=randomUUID();
    const result=await sendComplete(operation,{...candidate,id});
    assert.ok([400,403].includes(result.status),JSON.stringify(result.data));
    assert.equal(sql("SELECT count(*) FROM public.deliveries WHERE id="+quote(id)+"::uuid;"),'0');
    assert.equal(sql("SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id="+quote(operation)+"::uuid;"),'0');
  }
  assert.equal((await sendComplete(randomUUID(),payload)).status,200);
  const changed={...payload,baseVersion:1,parcels:payload.parcels.map(x=>({...x,parcelNameAtDelivery:'Rewrite history'}))};
  assert.equal((await sendComplete(randomUUID(),changed)).status,409);
  assert.equal((await readComplete(payload.id)).data.delivery.version,1);
});
