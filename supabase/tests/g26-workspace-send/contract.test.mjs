// Native disposable Supabase only. The runner validates its local project ID.
// Reuses create_own_workspace + check_sync_operations (g2-sync-own-data proposal); adds no RPC.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { extraProposals } from '../lib/extra-proposals.mjs';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash, createHmac } from 'node:crypto';

const work = process.env.G26_WS_WORK;
const project = process.env.G26_WS_PROJECT;
assert.match(project ?? '', /^magina-g26-ws-send-[0-9]+-[0-9]+$/);
assert.ok(work);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['localhost', '127.0.0.1'].includes(api.hostname));
assert.ok(readFileSync(join(work, 'supabase/config.toml'), 'utf8').includes(`project_id = "${project}"`));
const container = `supabase_db_${project}`;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', ['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'],
  {input:text,encoding:'utf8',timeout:20000,stdio:['pipe','pipe','pipe']}).trim();
async function http(path, {token,admin=false,body,method='POST'}={}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path, {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:ISSUER})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const create=(actor,operation,workspace)=>http('/rest/v1/rpc/create_own_workspace',{token:token(actor),
  body:{p_operation_id:operation,p_workspace:workspace}});
const receipt=(actor,operation)=>http('/rest/v1/rpc/check_sync_operations',{token:token(actor),
  body:{p_operation_ids:[operation]}});
const parents=(actor,workspace)=>http('/rest/v1/rpc/get_own_workspace_parents_v1',{token:token(actor),
  body:{p_workspace_id:workspace}});
const grants=actor=>http('/rest/v1/rpc/list_authorized_workspaces_v1',{token:token(actor),body:{}});
const draft=id=>({id,name:'Olivar Jódar "A"\\',countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'});
const actors={};

before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-ws-send-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}});
    assert.equal(created.status,200);
    const login=await http('/auth/v1/token?grant_type=password',{body:{email,password}});
    assert.equal(login.status,200);
    actors[label]={id:created.data.id,token:login.data.access_token};
  }
  const migrations=['20261011120000_g0_auth_workspaces_shared_farms.sql',
    '20261011120001_g0_agricultural_sync_schema.sql','20261011120002_g0_contracts_and_storage_rls.sql',
    '20261016041222_g0_legacy_storage_parcel_hardening.sql','20261016041601_g0_aggregate_identity_guards.sql'];
  const files=[...migrations.map(name=>new URL(`../../migrations/${name}`,import.meta.url)),
    ...['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql'].map(name=>new URL(`../../proposals/${name}`,import.meta.url)),
    new URL('../../migrations/20261029044901_g26_workspace_authorization.sql',import.meta.url),
    new URL('../../proposals/g26-parent-receive-v1.sql',import.meta.url)];
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\n${extraProposals()}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    if((await receipt(actors.A,randomUUID())).status===200) break;
    if(attempt===39) throw new Error('PostgREST did not reload');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
});

test('receipt hash is sha256 of the canonical JSONB text Android renders (WorkspaceCreateEnvelopeTest)',async()=>{
  const id='0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b';
  // Byte-identical literal to app/src/test/.../WorkspaceCreateEnvelopeTest.kt.
  const canonical='{"id": "0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b", "name": "Olivar Jódar \\"A\\"\\\\", '+
    '"locale": "es-ES", "currency": "EUR", "timezone": "Europe/Madrid", "countryCode": "ES"}';
  assert.equal(sql(`SELECT ${q(JSON.stringify(draft(id)))}::jsonb::text;`),canonical);
  const operation=randomUUID();
  const created=await create(actors.A,operation,draft(id));
  assert.equal(created.status,200,JSON.stringify(created.data));
  assert.deepEqual(Object.keys(created.data).sort(),
    ['entity_id','idempotent','operation_id','payload_version','remote_version','status','success']);
  assert.equal(created.data.status,'APPLIED');
  assert.equal(created.data.idempotent,false);
  const found=(await receipt(actors.A,operation)).data.confirmed_receipts;
  assert.equal(found.length,1);
  assert.equal(found[0].payload_hash,createHash('sha256').update(canonical,'utf8').digest('hex'));
  assert.equal(found[0].entity_type,'WORKSPACE');
  assert.equal(found[0].entity_id,id);
  assert.equal(found[0].remote_version,1);
  assert.equal(found[0].payload_version,1);
});

test('created workspace keeps its UUID and becomes an owner grant and a v1 parent graph for the same account only',async()=>{
  const id=randomUUID(), operation=randomUUID();
  assert.equal((await parents(actors.A,id)).status,403);
  assert.equal((await create(actors.A,operation,draft(id))).status,200);
  const grant=(await grants(actors.A)).data.workspaces.find(w=>w.id===id);
  assert.equal(grant.role,'OWNER');
  assert.ok(grant.permissions.includes('read'));
  const graph=await parents(actors.A,id);
  assert.equal(graph.status,200,JSON.stringify(graph.data));
  assert.equal(graph.data.workspace.id,id);
  assert.equal(graph.data.workspace.ownerUserId,actors.A.id);
  assert.equal(graph.data.workspace.version,1);
  assert.equal(graph.data.workspace.name,'Olivar Jódar "A"\\');
  assert.deepEqual([graph.data.farms,graph.data.parcels,graph.data.memberships],[[],[],[]]);
  // The other account sees neither the grant, the graph nor A's receipt.
  assert.equal((await grants(actors.C)).data.workspaces.some(w=>w.id===id),false);
  assert.equal((await parents(actors.C,id)).status,403);
  assert.deepEqual((await receipt(actors.C,operation)).data.confirmed_receipts,[]);
});

test('lost-response replay is idempotent; other operations, accounts or payloads cannot duplicate or take the UUID',async()=>{
  const id=randomUUID(), operation=randomUUID();
  assert.equal((await create(actors.A,operation,draft(id))).status,200);
  const replay=await create(actors.A,operation,draft(id));
  assert.equal(replay.status,200);
  assert.equal(replay.data.idempotent,true);
  assert.equal(replay.data.remote_version,1);
  assert.equal((await create(actors.A,operation,{...draft(id),name:'Otro contenido'})).status,409);
  assert.equal((await create(actors.A,randomUUID(),draft(id))).status,409);
  assert.equal((await create(actors.C,randomUUID(),{...draft(id),name:'Toma de C'})).status,409);
  assert.equal(sql(`SELECT count(*)||'|'||max(name)||'|'||max(owner_user_id::text) FROM public.workspaces WHERE id=${q(id)}::uuid;`),
    `1|Olivar Jódar "A"\\|${actors.A.id}`);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)}::uuid;`),'1');
});

test('after deletion a replayed receipt is refused and the workspace is no longer received',async()=>{
  const id=randomUUID(), operation=randomUUID();
  assert.equal((await create(actors.A,operation,draft(id))).status,200);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(id)}::uuid;`);
  assert.equal((await create(actors.A,operation,draft(id))).status,403);
  assert.equal((await parents(actors.A,id)).status,403);
  assert.equal((await grants(actors.A)).data.workspaces.some(w=>w.id===id),false);
  // The immutable receipt survives for audit; it does not restore permission.
  assert.equal((await receipt(actors.A,operation)).data.confirmed_receipts.length,1);
});
