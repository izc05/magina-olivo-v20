// Run only through run.sh. Real GoTrue + PostgREST; SQL connections use native psql.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { createHmac, randomBytes, randomUUID } from 'node:crypto';
import { validateTarget } from './safety.mjs';

const work = process.env.G1C_NATIVE_WORK;
assert.ok(work, 'Use the disposable run.sh runner');
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const container = validateTarget(process.env.G1C_NATIVE_PROJECT, status.API_URL,
  readFileSync(join(work, 'supabase/config.toml'), 'utf8'));
const api = status.API_URL;
assert.ok(status.ANON_KEY && status.SERVICE_ROLE_KEY && status.JWT_SECRET, 'Local legacy JWT signing fixture required; review CLI status contract if absent');
const actors = {};
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const args = ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'];
function sql(input) { return execFileSync('docker', args, { input, encoding: 'utf8', timeout: 15000, stdio: ['pipe', 'pipe', 'pipe'] }).trim(); }
function connection(input) {
  const child = spawn('docker', args, { stdio: ['pipe', 'pipe', 'pipe'] });
  let stdout = '', stderr = '';
  child.stdout.on('data', b => { stdout += b; });
  child.stderr.on('data', b => { stderr += b; });
  const timer = setTimeout(() => child.kill(), 15000);
  const result = new Promise((resolve, reject) => {
    child.on('error', reject);
    child.on('close', code => { clearTimeout(timer); resolve({ code, stdout, stderr }); });
  });
  child.stdin.end(input);
  return result;
}
function claims(actor) {
  return { ...JSON.parse(Buffer.from(actors[actor].token.split('.')[1], 'base64url')), exp: Math.floor(Date.now()/1000)+600 };
}
function context(actor) {
  // This SQL context is trusted fixture input, not evidence of JWT verification.
  return `SET LOCAL ROLE authenticated; SELECT set_config('request.jwt.claims',${quote(JSON.stringify(claims(actor)))},true);`;
}
const request = () => ({ payloadVersion: 1, operationId: randomUUID(), farmId: randomUUID(), localWorkspaceId: randomUUID(), consentId: randomUUID(), consentTextVersion: 1, manifestSha256: 'a'.repeat(64), aggregateVersion: 1 });
const reserve = (actor, r) => `SELECT public.reserve_local_farm_g1c(${quote(JSON.stringify(r))}::jsonb,'${actors[actor].workspace}');`;
const insert = (actor, farm) => `INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES('${farm}','${actors[actor].workspace}','${actors[actor].id}','${actors[actor].id}','ephemeral native fixture');`;
async function http(path, { token, admin = false, body, method = 'POST' } = {}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path, { method, headers: { apikey: key, Authorization: `Bearer ${token ?? key}`, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  return { status: response.status, data: await response.json() };
}
const rpc = (actor, r, token = actors[actor].token) => http('/rest/v1/rpc/reserve_local_farm_g1c', { token, body: { p_request: r, p_remote_workspace: actors[actor].workspace } });
function signed(claim, secret = status.JWT_SECRET) {
  const part = x => Buffer.from(JSON.stringify(x)).toString('base64url');
  const body = `${part({alg:'HS256',typ:'JWT'})}.${part(claim)}`;
  return body + '.' + createHmac('sha256', secret).update(body).digest('base64url');
}
async function race(winnerActor, winnerSQL, loserActor, loserSQL) {
  const name = `g1c-${randomUUID()}`;
  // Winner holds its reservation/INSERT uncommitted. Observe backend PgSleep only
  // after its statement completed, then prove the competing backend waits on a lock.
  const winner = connection(`SET application_name='${name}-winner'; BEGIN; SET LOCAL statement_timeout='10s'; ${context(winnerActor)} ${winnerSQL} SELECT pg_sleep(3); COMMIT;`);
  const waitFor = async (suffix, predicate) => {
    for (let i = 0; i < 80; i++) {
      if (sql(`SELECT count(*) FROM pg_stat_activity WHERE application_name='${name}-${suffix}' AND ${predicate};`) === '1') return;
      await delay(25);
    }
    assert.fail(`Native ${suffix} never reached expected ${predicate}`);
  };
  await waitFor('winner', "wait_event='PgSleep'");
  const loser = connection(`SET application_name='${name}-loser'; BEGIN; SET LOCAL statement_timeout='10s'; ${context(loserActor)} ${loserSQL} COMMIT;`);
  await waitFor('loser', "wait_event_type='Lock'");
  const [a,b] = await Promise.all([winner, loser]);
  assert.equal(a.code, 0, a.stderr);
  return [a,b];
}

before(async () => {
  // No migration history writes: these are baseline fixture DDL and candidate SQL
  // executed exclusively inside the runner's newly created disposable database.
  for (const actor of ['A','B']) {
    const email = `g1c-${randomUUID()}@example.test`, password = `Fixture!${randomUUID()}`;
    const created = await http('/auth/v1/admin/users', { admin:true, body:{email,password,email_confirm:true} });
    assert.equal(created.status, 200);
    const login = await http('/auth/v1/token?grant_type=password', { body:{email,password} });
    assert.equal(login.status, 200);
    const nativeToken = login.data.access_token;
    const nativeClaims = JSON.parse(Buffer.from(nativeToken.split('.')[1], 'base64url'));
    assert.ok(nativeClaims.iss.startsWith('http://'), 'Review fixture if local Auth starts issuing HTTPS tokens');
    // Synthetic HTTPS issuer signed by this disposable gateway key; not proof of real HTTPS Auth issuance.
    const token = signed({...nativeClaims, iss:'https://g1c-native.example.test/auth/v1'});
    actors[actor] = { id:created.data.id, nativeToken, token, workspace:randomUUID() };
  }
  const migrations = ['20261011120000_g0_auth_workspaces_shared_farms.sql','20261011120001_g0_agricultural_sync_schema.sql','20261011120002_g0_contracts_and_storage_rls.sql','20261016041222_g0_legacy_storage_parcel_hardening.sql','20261016041601_g0_aggregate_identity_guards.sql'];
  sql('BEGIN;\n' + migrations.map(name => readFileSync(new URL(`../../migrations/${name}`, import.meta.url),'utf8')).join('\n') + '\n' + readFileSync(new URL('../../proposals/g1c-reservations.sql',import.meta.url),'utf8') + '\nCOMMIT; NOTIFY pgrst,\'reload schema\';');
  for (const a of Object.values(actors)) sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES('${a.workspace}','${a.id}','ephemeral native');`);
  let ready = false;
  for (let i=0;i<40;i++) {
    const probe = await rpc('A',request());
    if (probe.status===200) { ready=true; break; }
    assert.ok([404,503].includes(probe.status), `Unexpected schema probe status ${probe.status}`);
    await delay(250);
  }
  assert.ok(ready, 'Native gateway schema ready');
  console.log('Native versions:', sql('SELECT version();'));
}, {timeout:60000});

test('native reservation/reservation serializes and exact duplicate response is stable', {timeout:20000}, async () => {
  const r=request();
  const [a,b]=await race('A',reserve('A',r),'A',reserve('A',r));
  assert.equal(b.code,0,b.stderr);
  const receipt = output => JSON.parse(output.split('\n').find(line=>line.startsWith('{') && line.includes('receiptId')));
  assert.deepEqual(receipt(a.stdout),receipt(b.stdout));
  assert.equal(sql(`SELECT count(*) FROM magina_g1c_private.g1c_reservations WHERE farm_id='${r.farmId}';`),'1');
});

test('native same-operation different-farm race cannot create a second receipt', {timeout:20000}, async () => {
  const r=request();
  const [,loser]=await race('A',reserve('A',r),'A',reserve('A',{...r,farmId:randomUUID()}));
  assert.notEqual(loser.code,0); assert.match(loser.stderr,/reservation conflict/);
  assert.equal(sql(`SELECT count(*) FROM magina_g1c_private.g1c_reservations WHERE actor_id='${actors.A.id}' AND operation_id='${r.operationId}';`),'1');
});

test('native conflicting reservations are account isolated with generic conflict', {timeout:20000}, async () => {
  const r=request();
  const [,b]=await race('A',reserve('A',r),'B',reserve('B',{...r,operationId:randomUUID()}));
  assert.notEqual(b.code,0);
  assert.match(b.stderr,/reservation conflict/);
  assert.ok(!b.stderr.includes(actors.A.id) && !b.stderr.includes('receiptId'));
  const direct = await connection(`BEGIN; ${context('B')} SELECT * FROM magina_g1c_private.g1c_reservations; COMMIT;`);
  assert.notEqual(direct.code,0); assert.match(direct.stderr,/permission denied/);
});

test('native reservation/INSERT race blocks theft in both commit orderings', {timeout:30000}, async () => {
  let r=request();
  let [,loser]=await race('A',reserve('A',r),'B',insert('B',r.farmId));
  assert.notEqual(loser.code,0); assert.match(loser.stderr,/reservation conflict/);
  assert.equal(sql(`SELECT count(*) FROM public.farms WHERE id='${r.farmId}';`),'0');
  r=request();
  [,loser]=await race('B',insert('B',r.farmId),'A',reserve('A',r));
  assert.notEqual(loser.code,0); assert.match(loser.stderr,/reservation conflict/);
  assert.equal(sql(`SELECT owner_id FROM public.farms WHERE id='${r.farmId}';`),actors.B.id);
  assert.equal(sql(`SELECT count(*) FROM magina_g1c_private.g1c_reservations WHERE farm_id='${r.farmId}';`),'0');
});

test('native gateway verifies JWT signatures/expiry and denies anonymous; exact HTTP replay', {timeout:60000}, async () => {
  const loopbackDenied=await rpc('A',request(),actors.A.nativeToken);
  assert.equal(loopbackDenied.status,403); assert.equal(loopbackDenied.data.message,'reservation denied');
  const r=request(), first=await rpc('A',r);
  assert.equal(first.status,200);
  assert.deepEqual(Object.keys(first.data).sort(),['actorId','farmId','issuer','operationId','payloadHash','receiptId','remoteWorkspaceId'].sort());
  assert.deepEqual(await rpc('A',r),first);
  const wrongProject=signed({...claims('A'),iss:'https://other-project.example.test/auth/v1'},randomBytes(48));
  const expired=signed({...claims('A'),exp:Math.floor(Date.now()/1000)-3600});
  for (const token of [wrongProject,expired]) {
    const response=await rpc('A',request(),token);
    assert.equal(response.status,401);
    assert.ok(!JSON.stringify(response.data).includes('receiptId'));
  }
  const noSession=await rpc('A',request(),status.ANON_KEY);
  assert.equal(noSession.status,401);
  const anonymous=await http('/auth/v1/signup',{body:{}});
  assert.equal(anonymous.status,200);
  assert.ok(anonymous.data.access_token && anonymous.data.user.is_anonymous);
  // Give anonymous actor its own workspace so denial proves session restriction,
  // rather than merely failing the unrelated workspace ownership predicate.
  const anonWorkspace=randomUUID();
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES('${anonWorkspace}','${anonymous.data.user.id}','anonymous fixture');`);
  const anonymousClaims=JSON.parse(Buffer.from(anonymous.data.access_token.split('.')[1],'base64url'));
  const anonymousGatewayToken=signed({...anonymousClaims,iss:'https://g1c-native.example.test/auth/v1'});
  const denied=await http('/rest/v1/rpc/reserve_local_farm_g1c',{token:anonymousGatewayToken,body:{p_request:request(),p_remote_workspace:anonWorkspace}});
  assert.equal(denied.status,403); assert.equal(denied.data.message,'reservation denied');
  const foreign=await http('/rest/v1/rpc/reserve_local_farm_g1c',{token:actors.B.token,body:{p_request:r,p_remote_workspace:actors.B.workspace}});
  assert.equal(foreign.status,409); assert.equal(foreign.data.message,'reservation conflict');
  assert.ok(!JSON.stringify(foreign.data).includes(first.data.receiptId));
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id='${actors.A.workspace}';`);
  const revoked=await rpc('A',r);
  assert.equal(revoked.status,403); assert.equal(revoked.data.message,'reservation denied');
});
