// Real disposable Supabase DEV: native Auth JWTs, PostgREST and Storage HTTP.
// Requires the guarded runner; never accepts a remote project or a production URL.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';

const project = process.env.G0_NATIVE_PROJECT_ID;
assert.match(project ?? '', /^magina-g0-native-[a-z0-9-]+$/);
const status = JSON.parse(readFileSync(process.env.G0_NATIVE_STATUS_FILE, 'utf8'));
const api = status.API_URL;
const url = new URL(api);
assert.ok(['127.0.0.1', 'localhost'].includes(url.hostname) && url.protocol === 'http:', 'DEV loopback required');
const anon = status.ANON_KEY;
const service = status.SERVICE_ROLE_KEY;
assert.ok(anon && service, 'Local native API keys required; never logged or archived');
const baseline = process.env.G0_NATIVE_BASELINE;
assert.ok(['clean', 'legacy'].includes(baseline));
const container = `supabase_db_${project}`;
const users = {};
let workspace, farm, farm2, campaign, contract, agreement, sharedParcel;
let legacyPolicyBlocked = false;
const legacyId = randomUUID();
const pdf = '%PDF-1.4\n% Synthetic native DEV contract evidence\n%%EOF\n';
const evidence = 'evidence/g0-native';
mkdirSync(evidence, { recursive: true });

function sql(command) {
  return execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'],
    { input: command, encoding: 'utf8', maxBuffer: 5 * 1024 * 1024 });
}

async function request(path, { actor, admin = false, method = 'GET', body, headers = {} } = {}) {
  const key = admin ? service : anon;
  const token = actor ? users[actor].token : key;
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { apikey: key, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const raw = await response.text();
  let data;
  try { data = JSON.parse(raw); } catch { data = raw; }
  return { status: response.status, ok: response.ok, data };
}

async function insert(table, actor, row) {
  const result = await request(`/rest/v1/${table}`, { actor, method: 'POST', body: row, headers: { Prefer: 'return=representation' } });
  assert.ok(result.ok, `Native insert ${table}: ${result.status} ${result.data?.message ?? 'failed'}`);
  return result.data[0];
}

async function rpc(name, actor, body) {
  const result = await request(`/rest/v1/rpc/${name}`, { actor, method: 'POST', body });
  assert.ok(result.ok, `Native RPC ${name}: ${result.status} ${result.data?.message ?? 'failed'}`);
  return result.data;
}

async function storage(path, actor, { upload = false, publicRead = false } = {}) {
  const response = await fetch(`${api}/storage/v1/object/${publicRead ? 'public/' : ''}${path}`, {
    method: upload ? 'POST' : 'GET',
    headers: { apikey: anon, Authorization: `Bearer ${users[actor].token}`, 'Content-Type': 'application/pdf' },
    body: upload ? pdf : undefined,
  });
  return { ok: response.ok, status: response.status, text: await response.text() };
}

before(async () => {
  for (const actor of ['A', 'B', 'C', 'D']) {
    const email = `g0-${actor.toLowerCase()}-${randomUUID()}@example.test`;
    const password = `MaginaQa!${randomUUID()}`;
    const created = await request('/auth/v1/admin/users', { admin: true, method: 'POST', body: { email, password, email_confirm: true } });
    assert.ok(created.ok && created.data.id, 'Native Auth user creation must succeed');
    const login = await request('/auth/v1/token?grant_type=password', { method: 'POST', body: { email, password } });
    assert.ok(login.ok && login.data.access_token, 'Native password login must issue a JWT');
    users[actor] = { id: created.data.id, token: login.data.access_token, email };
  }
  if (baseline === 'legacy') {
    sql(`CREATE TABLE public.farms (
      id uuid PRIMARY KEY, owner_id uuid REFERENCES auth.users(id), name text NOT NULL,
      cover_image_uri text, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
      ALTER TABLE public.farms ENABLE ROW LEVEL SECURITY;
      CREATE POLICY farms_select_own ON public.farms FOR SELECT TO authenticated USING (owner_id=auth.uid());
      INSERT INTO public.farms (id, owner_id, name, cover_image_uri)
      VALUES ('${legacyId}', '${users.A.id}', 'Legacy preserved farm', 'local://original-cover');`);
    const backup = execFileSync('docker', ['exec', container, 'pg_dump', '-U', 'postgres', '-d', 'postgres', '--table=public.farms', '--data-only', '--column-inserts', '--no-owner', '--no-privileges'], { encoding: 'utf8' });
    writeFileSync(`${evidence}/legacy-farms-before.sql`, backup);
  }

  // Reproduce inherited public buckets through the actual Storage API, not SQL objects.
  if (baseline === 'legacy') {
    for (const id of ['farm-attachments', 'farm-contracts']) {
      assert.ok((await request('/storage/v1/bucket', { admin:true, method:'POST', body:{id,name:id,public:true} })).ok);
    }
    const uploaded = await fetch(`${api}/storage/v1/object/farm-contracts/legacy-preserved.pdf`, {
      method:'POST', headers:{apikey:service, Authorization:`Bearer ${service}`, 'Content-Type':'application/pdf'}, body:pdf,
    });
    assert.ok(uploaded.ok);
    assert.equal(await (await fetch(`${api}/storage/v1/object/public/farm-contracts/legacy-preserved.pdf`)).text(), pdf);
  }
  const migrations = ['20261011120000_g0_auth_workspaces_shared_farms.sql', '20261011120001_g0_agricultural_sync_schema.sql', '20261011120002_g0_contracts_and_storage_rls.sql'];
  const ddl = migrations.map(name => readFileSync(new URL(`../migrations/${name}`, import.meta.url), 'utf8')).join('\n');
  sql(`BEGIN;\n${ddl}\nCOMMIT;\nNOTIFY pgrst, 'reload schema';`);
  const hardening = readFileSync(new URL('../migrations/20261016041222_g0_legacy_storage_parcel_hardening.sql', import.meta.url), 'utf8');
  sql('CREATE POLICY g0_test_legacy_public_read ON storage.objects FOR SELECT USING(true);');
  try {
    sql(`BEGIN; ${hardening} COMMIT;`);
  } catch (error) {
    legacyPolicyBlocked = /Unknown Storage policies require explicit review/.test(String(error.stderr));
  }
  assert.ok(legacyPolicyBlocked, 'Unknown permissive Storage policy must fail closed');
  // Only remove the known synthetic policy in this disposable fixture, never an unknown real policy.
  sql('DROP POLICY g0_test_legacy_public_read ON storage.objects;');
  sql(`BEGIN; ${hardening} COMMIT; NOTIFY pgrst, 'reload schema';`);
  sql(`BEGIN; ${readFileSync(new URL('../migrations/20261016041601_g0_aggregate_identity_guards.sql', import.meta.url), 'utf8')} COMMIT;`);
  sql(`BEGIN; ${readFileSync(new URL('../migrations/20261024125046_g0_function_security_hardening.sql', import.meta.url), 'utf8')} COMMIT; NOTIFY pgrst, 'reload schema';`);
  writeFileSync(`${evidence}/${baseline}-runtime.txt`, sql(`SELECT version(); SELECT 'baseline=${baseline}'; SELECT bucket_id, count(*) FROM storage.objects GROUP BY bucket_id;`));

  let ready = false;
  for (let attempt = 0; attempt < 30; attempt++) {
    const probe = await request('/rest/v1/workspaces?select=id', { actor: 'A' });
    if (probe.ok) { ready = true; break; }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  assert.ok(ready, 'PostgREST must expose migrated schema with native authenticated grants');
  workspace = await insert('workspaces', 'A', { id: randomUUID(), owner_user_id: users.A.id, name: 'Native DEV workspace' });
  farm = await insert('farms', 'A', { id: randomUUID(), workspace_id: workspace.id, creator_user_id: users.A.id, owner_id: users.A.id, name: 'Native shared farm' });
  farm2 = await insert('farms', 'A', { id: randomUUID(), workspace_id: workspace.id, creator_user_id: users.A.id, owner_id: users.A.id, name: 'Native separate farm' });
  sharedParcel = await insert('parcels', 'A', {workspace_id:workspace.id, display_name:'Native shared parcel'});
  const privateParcel = await insert('parcels', 'A', {workspace_id:workspace.id, display_name:'Native private parcel'});
  await insert('farm_parcel_memberships', 'A', {workspace_id:workspace.id, farm_id:farm.id, parcel_id:sharedParcel.id});
  await insert('farm_parcel_memberships', 'A', {workspace_id:workspace.id, farm_id:farm2.id, parcel_id:privateParcel.id});
  campaign = await insert('campaigns', 'A', { id: randomUUID(), workspace_id: workspace.id, farm_id: farm.id, name: 'Native 2026/27', start_date: '2026-10-01' });
  for (const actor of ['B', 'C']) {
    await insert('farm_access_grants', 'A', { farm_id: farm.id, user_id: users[actor].id, granted_by_user_id: users.A.id,
      role: actor === 'B' ? 'EXPLOITER' : 'WORKER', capabilities: actor === 'B'
        ? ['view_farm', 'add_deliveries', 'view_deliveries', 'view_contracts', 'upload_contracts', 'manage_agreements']
        : ['view_farm', 'add_deliveries'] });
  }
  contract = await insert('farm_contracts', 'A', { farm_id: farm.id, start_date: '2026-10-01', end_date: '2027-09-30',
    owner_user_id: users.A.id, exploiter_user_id: users.B.id, terms_notes: 'Original native terms', cost_responsibilities: { fuel: 'exploiter' } });
  agreement = await insert('campaign_harvest_share_agreements', 'A', { farm_id: farm.id, campaign_id: campaign.id,
    contract_id: contract.id, owner_user_id: users.A.id, exploiter_user_id: users.B.id, owner_percentage: 30, exploiter_percentage: 70, version: 1 });
});

test('Native G0 privileged RPCs deny anonymous callers and retain authenticated access', async () => {
  const calls = [
    ['accept_farm_contract', {p_contract_id:contract.id}],
    ['accept_harvest_share_agreement', {p_agreement_id:agreement.id}],
    ['claim_farm_invitation', {token_plaintext:'synthetic-invalid-token'}],
    ['confirm_farm_invitation', {p_invitation_id:randomUUID()}],
    ['has_farm_capability', {f_id:farm.id,req_cap:'view_farm'}],
    ['user_accessible_farm_ids', {}],
  ];
  for (const [name, body] of calls) {
    const denied = await request(`/rest/v1/rpc/${name}`, {method:'POST',body});
    assert.ok([401,403].includes(denied.status), `${name} anonymous status ${denied.status}`);
  }
  assert.equal(await rpc('has_farm_capability','A',{f_id:farm.id,req_cap:'view_farm'}),true);
  assert.equal(sql(`SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname IN ('check_farm_principal_immutability',
      'deny_delivery_hard_delete','check_contract_acceptance_integrity',
      'check_share_agreement_acceptance_integrity')
      AND p.proconfig @> ARRAY['search_path=pg_catalog, public'];`).trim(),'4');
});

test('Native hardening blocks inherited policies and private buckets deny anonymous downloads', async () => {
  assert.ok(legacyPolicyBlocked);
  assert.equal(sql("SELECT count(*) FROM storage.buckets WHERE id IN ('farm-attachments','farm-contracts') AND public=false;").trim(),'2');
  if (baseline === 'legacy') {
    const publicRead = await fetch(`${api}/storage/v1/object/public/farm-contracts/legacy-preserved.pdf`);
    assert.ok(!publicRead.ok);
    const preserved = await fetch(`${api}/storage/v1/object/farm-contracts/legacy-preserved.pdf`, {headers:{apikey:service,Authorization:`Bearer ${service}`}});
    assert.equal(await preserved.text(),pdf);
  }
});

test('Native delegated parcel read stays within the authorized farm and forbids writes', async () => {
  for (const actor of ['B','C']) {
    const visible = await request('/rest/v1/parcels?select=id', {actor});
    assert.equal(visible.status,200);
    assert.deepEqual(visible.data,[{id:sharedParcel.id}]);
    const edited = await request(`/rest/v1/parcels?id=eq.${sharedParcel.id}`, {actor,method:'PATCH',body:{display_name:'Forbidden'},headers:{Prefer:'return=representation'}});
    assert.equal(edited.status,200);
    assert.deepEqual(edited.data,[]);
  }
  assert.deepEqual((await request('/rest/v1/parcels?select=id',{actor:'D'})).data,[]);
});

test('Native Auth issues four distinct identities and authenticated sessions', async () => {
  assert.equal(new Set(Object.values(users).map(user => user.id)).size, 4);
  for (const actor of ['A', 'B', 'C', 'D']) {
    const current = await request('/auth/v1/user', { actor });
    assert.equal(current.status, 200);
    assert.equal(current.data.id, users[actor].id);
  }
});

test('Native migration preserves legacy UUID, owner, name and cover; backup restores data', () => {
  if (baseline === 'clean') {
    assert.equal(sql(`SELECT count(*) FROM public.farms WHERE id='${legacyId}';`).trim(), '0');
    return;
  }
  assert.equal(sql(`SELECT owner_id=creator_user_id AND workspace_id IS NULL AND name='Legacy preserved farm' AND cover_image_uri='local://original-cover' FROM public.farms WHERE id='${legacyId}';`).trim(), 't');
  // Preserve legacy identity without silently claiming it into a new account's
  // canonical workspace. Client/audited legacy claim is a separate #725/#726 gate.
  assert.equal(sql(`SELECT count(*) FROM pg_catalog.pg_policies WHERE schemaname='public' AND tablename='farms' AND policyname='farms_select_own';`).trim(), '0');
  sql('CREATE SCHEMA g0_backup_verify; CREATE TABLE g0_backup_verify.farms (LIKE public.farms INCLUDING DEFAULTS);');
  const backup = readFileSync(`${evidence}/legacy-farms-before.sql`, 'utf8').replaceAll('public.farms', 'g0_backup_verify.farms');
  sql(backup);
  assert.equal(sql(`SELECT name || '|' || cover_image_uri FROM g0_backup_verify.farms WHERE id='${legacyId}';`).trim(), 'Legacy preserved farm|local://original-cover');
});

test('Native RLS permits A/B/C farm access and isolates D and anonymous', async () => {
  for (const actor of ['A', 'B', 'C']) {
    const visible = await request(`/rest/v1/farms?id=eq.${farm.id}&select=id`, { actor });
    assert.equal(visible.status, 200);
    assert.equal(visible.data.length, 1);
  }
  const intruder = await request(`/rest/v1/farms?id=eq.${farm.id}&select=id`, { actor: 'D' });
  assert.equal(intruder.status, 200);
  assert.deepEqual(intruder.data, []);
  const anonymous = await request(`/rest/v1/farms?id=eq.${farm.id}&select=id`);
  assert.ok(!anonymous.ok || anonymous.data.length === 0);
});

test('Native farm creation cannot assign foreign creator or owner identities', async () => {
  const own=await insert('workspaces','D',{owner_user_id:users.D.id,name:'Principal fixture'});
  for (const principals of [
    {creator_user_id:users.A.id,owner_id:users.D.id},
    {creator_user_id:users.D.id,owner_id:users.A.id},
  ]) {
    for (const workspace_id of [own.id,null]) {
      const rejected=await request('/rest/v1/farms',{actor:'D',method:'POST',body:{id:randomUUID(),workspace_id,name:'Unconsented attribution',...principals}});
      assert.equal(rejected.status,403);
    }
  }
  const legitimate=await insert('farms','D',{id:randomUUID(),workspace_id:own.id,name:'Own unassigned farm',creator_user_id:users.D.id,owner_id:null});
  assert.deepEqual((await request(`/rest/v1/farms?id=eq.${legitimate.id}&select=id`,{actor:'A'})).data,[]);
});

test('Native delivery A/B sharing rejects cross-farm campaign references', async () => {
  const row = { id: randomUUID(), workspace_id: workspace.id, farm_id: farm.id, campaign_id: campaign.id,
    delivery_date: '2026-10-02', destination_name: 'Native QA cooperative', net_grams: 1200000, recorded_by_user_id: users.B.id };
  const delivery = await insert('deliveries', 'B', row);
  assert.equal((await request(`/rest/v1/deliveries?id=eq.${delivery.id}&select=net_grams`, { actor: 'A' })).data[0].net_grams, 1200000);
  assert.deepEqual((await request(`/rest/v1/deliveries?id=eq.${delivery.id}&select=id`, { actor: 'D' })).data, []);
  const mismatch = await request('/rest/v1/deliveries', { actor: 'A', method: 'POST', body: { ...row, id: randomUUID(), farm_id: farm2.id, recorded_by_user_id: users.A.id } });
  assert.equal(mismatch.status, 409);
});

test('Native historical author and aggregate references cannot be forged', async () => {
  const delivery = await insert('deliveries','A',{workspace_id:workspace.id,farm_id:farm.id,campaign_id:campaign.id,
    delivery_date:'2026-10-03',destination_name:'Audit fixture',net_grams:0,recorded_by_user_id:users.A.id});
  assert.ok(!(await request(`/rest/v1/deliveries?id=eq.${delivery.id}`,{actor:'A',method:'PATCH',body:{recorded_by_user_id:users.D.id}})).ok);
  assert.ok(!(await request(`/rest/v1/deliveries?id=eq.${delivery.id}`,{actor:'A',method:'PATCH',body:{harvest_id:randomUUID()}})).ok);
  const otherSeason=await insert('campaigns','A',{workspace_id:workspace.id,farm_id:farm.id,name:'Other campaign',start_date:'2027-10-01'});
  const otherHarvest=await insert('harvests','A',{workspace_id:workspace.id,farm_id:farm.id,campaign_id:otherSeason.id,
    harvest_date:'2027-10-02',weight_grams:1000,recorded_by_user_id:users.A.id});
  assert.ok(!(await request(`/rest/v1/deliveries?id=eq.${delivery.id}`,{actor:'A',method:'PATCH',body:{harvest_id:otherHarvest.id}})).ok);
  const expense={workspace_id:workspace.id,farm_id:farm.id,campaign_id:campaign.id,delivery_id:delivery.id,
    expense_date:'2026-10-03',concept:'Fixture',category:'OTHER',amount_minor:12345};
  assert.equal((await insert('expenses','A',expense)).amount_minor,12345);
  assert.ok(!(await request('/rest/v1/expenses',{actor:'A',method:'POST',body:{...expense,farm_id:farm2.id,campaign_id:null}})).ok);
  const doc={workspace_id:workspace.id,owner_type:'DELIVERY',owner_id:delivery.id,type:'PHOTO',mime_type:'image/jpeg',display_name:'Fixture'};
  await insert('documents','A',doc);
  const other=await insert('workspaces','D',{owner_user_id:users.D.id,name:'Document isolation fixture'});
  assert.ok(!(await request('/rest/v1/documents',{actor:'D',method:'POST',body:{...doc,workspace_id:other.id}})).ok);
});

test('Native expired reinvitation rolls back instead of accepting an inactive grant', async () => {
  const isolated=await insert('farms','A',{id:randomUUID(),workspace_id:workspace.id,creator_user_id:users.A.id,owner_id:users.A.id,name:'Reinvitation fixture'});
  await insert('farm_access_grants','A',{farm_id:isolated.id,user_id:users.B.id,granted_by_user_id:users.A.id,role:'VIEWER',
    capabilities:['view_farm'],valid_from:new Date(Date.now()-172800000).toISOString(),valid_until:new Date(Date.now()-86400000).toISOString()});
  const token=randomUUID();
  const invitation=await insert('farm_invitations','A',{farm_id:isolated.id,issued_by_user_id:users.A.id,
    token_hash:createHash('sha256').update(token).digest('hex'),expires_at:new Date(Date.now()+3600000).toISOString(),requires_issuer_confirmation:true});
  assert.equal((await rpc('claim_farm_invitation','B',{token_plaintext:token})).status,'CLAIMED');
  const rejected=await request('/rest/v1/rpc/confirm_farm_invitation',{actor:'A',method:'POST',body:{p_invitation_id:invitation.id}});
  assert.ok(!rejected.ok);
  assert.equal((await request(`/rest/v1/farm_invitations?id=eq.${invitation.id}&select=state`,{actor:'A'})).data[0].state,'CLAIMED');
  assert.deepEqual((await request(`/rest/v1/farms?id=eq.${isolated.id}&select=id`,{actor:'B'})).data,[]);
});

test('Native first and second contract consent freeze terms and forbid downgrade', async () => {
  const first = await rpc('accept_farm_contract', 'A', { p_contract_id: contract.id });
  assert.equal(first.status, 'PROPOSED');
  const edited = await request(`/rest/v1/farm_contracts?id=eq.${contract.id}`, { actor: 'A', method: 'PATCH', body: { terms_notes: 'Unilateral change' } });
  assert.ok(!edited.ok);
  const second = await rpc('accept_farm_contract', 'B', { p_contract_id: contract.id });
  assert.equal(second.status, 'ACCEPTED');
  for (const body of [{ status: 'PROPOSED' }, { terms_notes: 'Changed after consent' }, { cost_responsibilities: { fuel: 'owner' } }]) {
    assert.ok(!(await request(`/rest/v1/farm_contracts?id=eq.${contract.id}`, { actor: 'A', method: 'PATCH', body })).ok);
  }
  const repeated = await rpc('accept_farm_contract', 'B', { p_contract_id: contract.id });
  assert.equal(repeated.accepted_by_exploiter_at, second.accepted_by_exploiter_at);
});

test('Native negotiated harvest split remains 30/70 and never rewrites deliveries', async () => {
  assert.equal((await rpc('accept_harvest_share_agreement', 'A', { p_agreement_id: agreement.id })).status, 'PROPOSED');
  assert.equal((await rpc('accept_harvest_share_agreement', 'B', { p_agreement_id: agreement.id })).status, 'ACCEPTED');
  for (const body of [{ status: 'PROPOSED' }, { owner_percentage: 50, exploiter_percentage: 50 }]) {
    assert.ok(!(await request(`/rest/v1/campaign_harvest_share_agreements?id=eq.${agreement.id}`, { actor: 'A', method: 'PATCH', body })).ok);
  }
  const row = await request(`/rest/v1/campaign_harvest_share_agreements?id=eq.${agreement.id}`, { actor: 'A' });
  assert.equal(Number(row.data[0].owner_percentage), 30);
  assert.equal(Number(row.data[0].exploiter_percentage), 70);
  assert.equal(sql(`SELECT sum(net_grams) FROM public.deliveries WHERE farm_id='${farm.id}';`).trim(), '1200000');
});

test('Native Storage permits contract parties and rejects C/D, public and cross-farm paths', async () => {
  const path = `farm-contracts/${farm.id}/${contract.id}/native-proof.pdf`;
  assert.ok((await storage(path, 'A', { upload: true })).ok);
  assert.equal((await storage(path, 'B')).text, pdf);
  assert.ok(!(await storage(path, 'C')).ok);
  assert.ok(!(await storage(path, 'D')).ok);
  assert.ok(!(await storage(path, 'D', { publicRead: true })).ok);
  assert.ok(!(await storage(`farm-contracts/${farm2.id}/${contract.id}/mismatch.pdf`, 'A', { upload: true })).ok);
  assert.ok(!(await storage(`farm-contracts/${farm.id}/${contract.id}/intruder.pdf`, 'D', { upload: true })).ok);
});

test('Native private delivery photo uploads require farm capability', async () => {
  const path = `farm-attachments/${farm.id}/${randomUUID()}.pdf`;
  assert.ok((await storage(path, 'B', { upload: true })).ok);
  assert.equal((await storage(path, 'A')).text, pdf);
  assert.ok(!(await storage(path, 'D')).ok);
  assert.ok(!(await storage(`farm-attachments/${farm2.id}/${randomUUID()}.pdf`, 'B', { upload: true })).ok);
});

test('Native transaction rollback leaves agricultural data unchanged', () => {
  sql(`BEGIN; UPDATE public.farms SET name='Uncommitted edit' WHERE id='${farm.id}'; ROLLBACK;`);
  assert.equal(sql(`SELECT name FROM public.farms WHERE id='${farm.id}';`).trim(), 'Native shared farm');
});

test('Native workspace/campaign references and delegated principals cannot be forged', async () => {
  const other = await insert('workspaces', 'D', { id: randomUUID(), owner_user_id: users.D.id, name: 'Isolated workspace D' });
  const mismatch = await request('/rest/v1/deliveries', { actor: 'A', method: 'POST', body: {
    workspace_id: other.id, farm_id: farm.id, campaign_id: campaign.id, delivery_date: '2026-10-02',
    destination_name: 'Mismatched workspace', net_grams: 1000, recorded_by_user_id: users.A.id } });
  assert.equal(mismatch.status, 409);
  const wrongHarvest = await request('/rest/v1/harvests', { actor: 'A', method: 'POST', body: {
    workspace_id: workspace.id, farm_id: farm2.id, campaign_id: campaign.id, harvest_date: '2026-10-02',
    weight_grams: 1000, recorded_by_user_id: users.A.id } });
  assert.equal(wrongHarvest.status, 409);
  await insert('farm_access_grants', 'A', { farm_id: farm2.id, user_id: users.B.id, granted_by_user_id: users.A.id,
    role: 'EXPLOITER', capabilities: ['view_farm', 'manage_farm'] });
  const promoted = await request(`/rest/v1/farms?id=eq.${farm2.id}`, { actor: 'B', method: 'PATCH', body: {
    creator_user_id: users.B.id, owner_id: users.B.id } });
  assert.ok(!promoted.ok);
  await insert('farm_access_grants', 'A', { farm_id: farm2.id, user_id: users.C.id, granted_by_user_id: users.A.id,
    role: 'VIEWER', capabilities: ['view_farm'], valid_from: new Date(Date.now() + 86400000).toISOString() });
  assert.deepEqual((await request(`/rest/v1/farms?id=eq.${farm2.id}&select=id`, { actor: 'C' })).data, []);
});

test('Native invitations bind real Auth email and unbound links require confirmation', async () => {
  const isolated = await insert('farms', 'A', { id: randomUUID(), workspace_id: workspace.id, creator_user_id: users.A.id,
    owner_id: users.A.id, name: 'Isolated invitation farm' });
  const token = randomUUID() + randomUUID();
  await insert('farm_invitations', 'A', { farm_id: isolated.id, issued_by_user_id: users.A.id,
    token_hash: createHash('sha256').update(token).digest('hex'), role: 'VIEWER', capabilities: ['view_farm'],
    invitee_email_hash: createHash('sha256').update(users.D.email.toLowerCase()).digest('hex'),
    expires_at: new Date(Date.now() + 3600000).toISOString() });
  assert.equal((await rpc('claim_farm_invitation', 'C', { token_plaintext: token })).success, false);
  assert.equal((await rpc('claim_farm_invitation', 'D', { token_plaintext: token })).status, 'ACCEPTED');
  assert.equal((await rpc('claim_farm_invitation', 'D', { token_plaintext: token })).success, false);
  const unbound = randomUUID() + randomUUID();
  const invitation = await insert('farm_invitations', 'A', { farm_id: isolated.id, issued_by_user_id: users.A.id,
    token_hash: createHash('sha256').update(unbound).digest('hex'), role: 'VIEWER', capabilities: ['view_farm'],
    requires_issuer_confirmation: false, expires_at: new Date(Date.now() + 3600000).toISOString() });
  assert.equal((await rpc('claim_farm_invitation', 'B', { token_plaintext: unbound })).status, 'CLAIMED');
  assert.deepEqual((await request(`/rest/v1/farms?id=eq.${isolated.id}&select=id`, { actor: 'B' })).data, []);
  assert.equal((await rpc('confirm_farm_invitation', 'A', { p_invitation_id: invitation.id })).success, true);
  assert.equal((await request(`/rest/v1/farms?id=eq.${isolated.id}&select=id`, { actor: 'B' })).data.length, 1);
});

test('Native grant revocation closes RLS, Storage and definer RPC with the same Auth session', async () => {
  const revoked = await request(`/rest/v1/farm_access_grants?farm_id=eq.${farm.id}&user_id=eq.${users.B.id}`, {
    actor: 'A', method: 'PATCH', body: { status: 'REVOKED' }, headers: { Prefer: 'return=representation' } });
  assert.ok(revoked.ok && revoked.data.length === 1);
  assert.deepEqual((await request(`/rest/v1/parcels?id=eq.${sharedParcel.id}&select=id`, { actor:'B' })).data, []);
  assert.deepEqual((await request(`/rest/v1/deliveries?farm_id=eq.${farm.id}&select=id`, { actor: 'B' })).data, []);
  assert.ok(!(await storage(`farm-contracts/${farm.id}/${contract.id}/native-proof.pdf`, 'B')).ok);
  assert.equal((await rpc('accept_farm_contract', 'B', { p_contract_id: contract.id })).success, false);
  assert.equal((await rpc('accept_harvest_share_agreement', 'B', { p_agreement_id: agreement.id })).success, false);
});
