// Native disposable Supabase only. The runner validates its local project ID.
// Reuses reserve_local_farm_g1c + create_reserved_farm + check_sync_operations; adds no RPC.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash, createHmac } from 'node:crypto';

const work = process.env.G26_FARM_WORK;
const project = process.env.G26_FARM_PROJECT;
assert.match(project ?? '', /^magina-g26-farm-send-[0-9]+-[0-9]+$/);
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
const rpc=(name,actor,body)=>http(`/rest/v1/rpc/${name}`,{token:token(actor),body});
const workspace=async actor=>{
  const id=randomUUID();
  const created=await rpc('create_own_workspace',actor,{p_operation_id:randomUUID(),p_workspace:{id,name:'Olivar QA',
    countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return id;
};
const request=(farmId,ws,op=randomUUID())=>({payloadVersion:1,operationId:op,farmId,localWorkspaceId:ws,
  consentId:randomUUID(),consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1});
const farmBody=(id,ws,name='Finca Norte')=>({id,workspaceId:ws,name,description:null,municipality:'Bedmar',
  province:'Jaén',notes:null,status:'ACTIVE',coverDocumentId:null});
const reserve=(actor,req,ws)=>rpc('reserve_local_farm_g1c',actor,{p_request:req,p_remote_workspace:ws});
const create=(actor,op,req,receipt,farm)=>rpc('create_reserved_farm',actor,{p_operation_id:op,
  p_reservation_operation_id:req.operationId,p_reservation_receipt_id:receipt,p_farm:farm});
const receipt=(actor,op)=>rpc('check_sync_operations',actor,{p_operation_ids:[op]});
const parents=(actor,ws)=>rpc('get_own_workspace_parents_v1',actor,{p_workspace_id:ws});
const actors={};

before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-farm-send-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    if((await receipt(actors.A,randomUUID())).status===200) break;
    if(attempt===39) throw new Error('PostgREST did not reload');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
});

test('create hash is sha256 of the canonical JSONB text Android renders (FarmCreateEnvelopeTest)',()=>{
  const farm={...farmBody('1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b','0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b','Finca Jódar "N"')};
  // Byte-identical literal to app/src/test/.../FarmCreateEnvelopeTest.kt.
  const canonical='{"farm": {"id": "1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b", "name": "Finca Jódar \\"N\\"", '+
    '"notes": null, "status": "ACTIVE", "province": "Jaén", "description": null, '+
    '"workspaceId": "0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b", "municipality": "Bedmar", "coverDocumentId": null}, '+
    '"reservationReceiptId": "3d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b", '+
    '"reservationOperationId": "2d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b"}';
  assert.equal(sql(`SELECT jsonb_build_object('farm',${q(JSON.stringify(farm))}::jsonb,
    'reservationOperationId','2d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b',
    'reservationReceiptId','3d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b')::text;`),canonical);
});

test('owner reserves and creates the farm with its UUID; receipts correlate; it is received only by the owner',async()=>{
  const ws=await workspace(actors.A), id=randomUUID(), op=randomUUID(), req=request(id,ws);
  const reserved=await reserve(actors.A,req,ws);
  assert.equal(reserved.status,200,JSON.stringify(reserved.data));
  assert.deepEqual(Object.keys(reserved.data).sort(),['actorId','farmId','issuer','operationId','payloadHash','receiptId','remoteWorkspaceId']);
  assert.equal(reserved.data.issuer,'https://zubpicdibqbmewxeurhm.supabase.co');
  assert.equal(reserved.data.actorId,actors.A.id);
  const created=await create(actors.A,op,req,reserved.data.receiptId,farmBody(id,ws));
  assert.equal(created.status,200,JSON.stringify(created.data));
  assert.deepEqual(Object.keys(created.data).sort(),
    ['entity_id','idempotent','operation_id','payload_version','remote_version','status','success']);
  assert.equal(created.data.idempotent,false);
  const rows=(await receipt(actors.A,op)).data.confirmed_receipts;
  assert.equal(rows.length,1);
  assert.equal(rows[0].entity_type,'FARM');
  assert.equal(rows[0].remote_version,1);
  const canonical=sql(`SELECT jsonb_build_object('farm',${q(JSON.stringify(farmBody(id,ws)))}::jsonb,
    'reservationOperationId',${q(req.operationId)},'reservationReceiptId',${q(reserved.data.receiptId)})::text;`);
  assert.equal(rows[0].payload_hash,createHash('sha256').update(canonical,'utf8').digest('hex'));
  assert.equal(sql(`SELECT creator_user_id||'|'||owner_id||'|'||workspace_id||'|'||coalesce(cover_document_id::text,'∅') FROM public.farms WHERE id=${q(id)}::uuid;`),
    `${actors.A.id}|${actors.A.id}|${ws}|∅`);
  const graph=await parents(actors.A,ws);
  assert.equal(graph.status,200);
  assert.deepEqual(graph.data.farms.map(f=>[f.id,f.name,f.version]),[[id,'Finca Norte',1]]);
  assert.equal((await parents(actors.C,ws)).status,403);
  assert.deepEqual((await receipt(actors.C,op)).data.confirmed_receipts,[]);
});

test('lost-response replays are idempotent; other accounts, operations and payloads cannot duplicate or take the farm',async()=>{
  const ws=await workspace(actors.A), id=randomUUID(), op=randomUUID(), req=request(id,ws);
  const first=await reserve(actors.A,req,ws);
  const again=await reserve(actors.A,req,ws);
  assert.equal(again.status,200);
  assert.equal(again.data.receiptId,first.data.receiptId);
  assert.equal((await create(actors.A,op,req,first.data.receiptId,farmBody(id,ws))).status,200);
  const replay=await create(actors.A,op,req,first.data.receiptId,farmBody(id,ws));
  assert.equal(replay.status,200);
  assert.equal(replay.data.idempotent,true);
  assert.equal((await create(actors.A,op,req,first.data.receiptId,farmBody(id,ws,'Otro contenido'))).status,409);
  assert.equal((await create(actors.A,randomUUID(),req,first.data.receiptId,farmBody(id,ws))).status,409);
  assert.equal((await reserve(actors.A,{...req,consentTextVersion:3},ws)).status,409);
  assert.equal((await reserve(actors.A,request(id,ws),ws)).status,409);
  // C cannot reserve in A's workspace, nor take A's farm UUID from its own workspace.
  assert.equal((await reserve(actors.C,request(randomUUID(),ws),ws)).status,403);
  const cws=await workspace(actors.C);
  assert.equal((await reserve(actors.C,request(id,cws),cws)).status,409);
  assert.equal((await create(actors.A,randomUUID(),req,randomUUID(),farmBody(randomUUID(),ws))).status,403);
  assert.equal(sql(`SELECT count(*)||'|'||max(name)||'|'||max(owner_id::text) FROM public.farms WHERE id=${q(id)}::uuid;`),
    `1|Finca Norte|${actors.A.id}`);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)}::uuid;`),'1');
});

test('after the workspace is deleted, replays are refused and the farm is no longer received',async()=>{
  const ws=await workspace(actors.A), id=randomUUID(), op=randomUUID(), req=request(id,ws);
  const reserved=await reserve(actors.A,req,ws);
  assert.equal((await create(actors.A,op,req,reserved.data.receiptId,farmBody(id,ws))).status,200);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(ws)}::uuid;`);
  assert.equal((await reserve(actors.A,req,ws)).status,403);
  assert.equal((await create(actors.A,op,req,reserved.data.receiptId,farmBody(id,ws))).status,403);
  assert.equal((await parents(actors.A,ws)).status,403);
  assert.equal((await receipt(actors.A,op)).data.confirmed_receipts.length,1);
});
