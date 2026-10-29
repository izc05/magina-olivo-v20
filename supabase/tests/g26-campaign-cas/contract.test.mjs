// Native disposable Supabase only. The runner validates its local project ID.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const work = process.env.G26_CAS_WORK;
const project = process.env.G26_CAS_PROJECT;
assert.match(project ?? '', /^magina-g26-cas-[0-9]+-[0-9]+$/);
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
function token(actor, issuer='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1', expired=false) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:issuer,
    exp:expired?Math.floor(Date.now()/1000)-1:claims.exp})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const actors={};
let fixture;
before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-cas-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
    new URL('../../migrations/20261029104720_g26_campaign_cas.sql',import.meta.url)];
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  fixture={workspace:randomUUID(),farm:randomUUID(),parcel:randomUUID(),campaign:randomUUID(),snapshot:randomUUID()};
  const f=fixture,a=actors.A;
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(f.workspace)},${q(a.id)},'CAS fixture');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(f.farm)},${q(f.workspace)},${q(a.id)},${q(a.id)},'CAS farm');
    INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(f.parcel)},${q(f.workspace)},'CAS parcel');
    INSERT INTO public.farm_parcel_memberships(workspace_id,farm_id,parcel_id,valid_from) VALUES (${q(f.workspace)},${q(f.farm)},${q(f.parcel)},'2026-01-01');`);
  for(let i=0;i<40;i++) {
    const probe=await http('/rest/v1/rpc/update_own_campaign_v1',{token:token(a),body:{p_operation_id:randomUUID(),p_expected_version:1,p_campaign:{}}});
    if(probe.status!==404) return;
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  assert.fail('PostgREST did not reload the CAS RPC');
});

test('campaign create, CAS, concurrent conflict and lost-response receipt remain account-bound',async()=>{
  const f=fixture,a=actors.A,auth=token(a);
  assert.equal(sql("SELECT has_function_privilege('anon','public.update_own_campaign_v1(uuid,bigint,jsonb)','EXECUTE'),has_function_privilege('authenticated','public.update_own_campaign_v1(uuid,bigint,jsonb)','EXECUTE');"),'f|t');
  assert.equal(sql("SELECT has_table_privilege('authenticated','public.campaigns','UPDATE');"),'f');
  const createOp=randomUUID();
  const created=await http('/rest/v1/rpc/create_own_campaign',{token:auth,body:{p_operation_id:createOp,p_campaign:{
    id:f.campaign,workspaceId:f.workspace,farmId:f.farm,name:'Initial',startDate:'2026-01-01',endDate:null,
    status:'PREPARATION',notes:null,parcels:[{id:f.snapshot,parcelId:f.parcel,farmIdAtStart:f.farm,
      farmNameAtStart:'CAS farm',parcelNameAtStart:'CAS parcel',managedAreaM2AtStart:null,
      cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}]}}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  assert.equal((await http('/rest/v1/rpc/create_own_campaign',{token:auth,body:{p_operation_id:createOp,p_campaign:{
    id:f.campaign,workspaceId:f.workspace,farmId:f.farm,name:'Initial',startDate:'2026-01-01',endDate:null,
    status:'PREPARATION',notes:null,parcels:[{id:f.snapshot,parcelId:f.parcel,farmIdAtStart:f.farm,
      farmNameAtStart:'CAS farm',parcelNameAtStart:'CAS parcel',managedAreaM2AtStart:null,
      cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}]}}})).data.idempotent,true);
  const payload={id:f.campaign,workspaceId:f.workspace,farmId:f.farm,name:'Editor one',
    startDate:'2026-01-01',endDate:null,status:'ACTIVE',notes:'reviewed'};
  const op=randomUUID(),update=()=>http('/rest/v1/rpc/update_own_campaign_v1',{
    token:auth,body:{p_operation_id:op,p_expected_version:1,p_campaign:payload}});
  const first=await update(); assert.equal(first.status,200,JSON.stringify(first.data));
  assert.equal(first.data.remote_version,2);assert.equal(first.data.idempotent,false);
  // Simulate the client losing the first HTTP response: the same request is safe to replay.
  const replay=await update();assert.equal(replay.status,200);assert.equal(replay.data.idempotent,true);
  assert.equal(replay.data.remote_version,2);
  const receipt=await http('/rest/v1/rpc/check_sync_operations',{token:auth,body:{p_operation_ids:[op]}});
  assert.equal(receipt.status,200);assert.equal(receipt.data.confirmed_receipts[0].remote_version,2);
  assert.equal(receipt.data.confirmed_receipts[0].entity_type,'CAMPAIGN');
  assert.equal((await update()).data.remote_version,2);
  assert.equal(sql(`SELECT name||'|'||version FROM public.campaigns WHERE id=${q(f.campaign)};`),'Editor one|2');
  assert.equal(sql(`SELECT count(*) FROM public.campaign_parcels WHERE campaign_id=${q(f.campaign)} AND id=${q(f.snapshot)};`),'1');
  const changed=await http('/rest/v1/rpc/update_own_campaign_v1',{token:auth,body:{
    p_operation_id:op,p_expected_version:1,p_campaign:{...payload,name:'Modified retry'}}});
  assert.equal(changed.status,409);
  const staleOp=randomUUID();
  const stale=await http('/rest/v1/rpc/update_own_campaign_v1',{token:auth,body:{
    p_operation_id:staleOp,p_expected_version:1,p_campaign:{...payload,name:'Stale editor'}}});
  assert.equal(stale.status,409);assert.match(JSON.stringify(stale.data),/stale version/);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id=${q(staleOp)};`),'0');
  const concurrent=[randomUUID(),randomUUID()];
  const replies=await Promise.all(concurrent.map((id,i)=>http('/rest/v1/rpc/update_own_campaign_v1',{
    token:auth,body:{p_operation_id:id,p_expected_version:2,p_campaign:{...payload,name:`Concurrent ${i}`}}})));
  assert.deepEqual(replies.map(x=>x.status).sort(),[200,409]);
  assert.equal(sql(`SELECT version FROM public.campaigns WHERE id=${q(f.campaign)};`),'3');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id IN (${concurrent.map(q).join(',')});`),'1');
  const foreign=await http('/rest/v1/rpc/update_own_campaign_v1',{token:token(actors.C),body:{
    p_operation_id:randomUUID(),p_expected_version:3,p_campaign:{...payload,name:'Foreign'}}});
  assert.equal(foreign.status,403);
  const wrongIssuer=await http('/rest/v1/rpc/update_own_campaign_v1',{token:token(a,'https://other.example/auth/v1'),body:{
    p_operation_id:randomUUID(),p_expected_version:3,p_campaign:payload}});
  assert.equal(wrongIssuer.status,403);
  const expired=await http('/rest/v1/rpc/update_own_campaign_v1',{token:token(a,undefined,true),body:{
    p_operation_id:randomUUID(),p_expected_version:3,p_campaign:payload}});
  assert.ok([401,403].includes(expired.status));
  const direct=await http(`/rest/v1/campaigns?id=eq.${f.campaign}`,{token:auth,method:'PATCH',body:{name:'Bypass',version:4}});
  assert.equal(direct.status,403);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='CAMPAIGN' AND entity_id=${q(f.campaign)};`),'3');
});
