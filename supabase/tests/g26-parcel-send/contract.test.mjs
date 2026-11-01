// Native disposable Supabase only. The runner validates its local project ID.
// Reuses create_own_parcel + check_sync_operations (g2-parcel-bootstrap proposal); adds no RPC.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash, createHmac } from 'node:crypto';

const work = process.env.G26_PARCEL_WORK;
const project = process.env.G26_PARCEL_PROJECT;
assert.match(project ?? '', /^mg26-parcel-[0-9]+-[0-9]+$/);
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
/** A FARM confirmed through the #799 path: G1c reservation then create_reserved_farm. */
const farm=async(actor,ws)=>{
  const id=randomUUID(), req={payloadVersion:1,operationId:randomUUID(),farmId:id,localWorkspaceId:ws,
    consentId:randomUUID(),consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const reserved=await rpc('reserve_local_farm_g1c',actor,{p_request:req,p_remote_workspace:ws});
  assert.equal(reserved.status,200,JSON.stringify(reserved.data));
  const created=await rpc('create_reserved_farm',actor,{p_operation_id:randomUUID(),p_reservation_operation_id:req.operationId,
    p_reservation_receipt_id:reserved.data.receiptId,p_farm:{id,workspaceId:ws,name:'Finca Norte',description:null,
      municipality:'Bedmar',province:'Jaén',notes:null,status:'ACTIVE',coverDocumentId:null}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return id;
};
const parcelBody=(id,ws,farmId,overrides={})=>({id,workspaceId:ws,displayName:'Parcela 1',cadastralReference:null,
  cadastralPolygon:null,cadastralParcel:null,municipality:'Bedmar',province:'Jaén',source:'MANUAL',geometryGeoJson:null,
  sourceProvider:null,sourceImportedAt:null,cadastralAreaM2:null,managedAreaM2:1234.5,notes:null,status:'ACTIVE',
  oliveTreeCount:120,variety:'Picual',irrigationSystem:null,irrigationNetwork:null,irrigationSector:null,irrigationDays:null,
  memberships:[{id:randomUUID(),farmId,validFrom:'2026-01-01T00:00:00.007Z',validUntil:null,deletedAt:null}],...overrides});
const create=(actor,op,parcel)=>rpc('create_own_parcel',actor,{p_operation_id:op,p_parcel:parcel});
const receipt=(actor,op)=>rpc('check_sync_operations',actor,{p_operation_ids:[op]});
const parents=(actor,ws)=>rpc('get_own_workspace_parents_v1',actor,{p_workspace_id:ws});
const canonicalOf=parcel=>sql(`SELECT ${q(JSON.stringify(parcel))}::jsonb::text;`);
const actors={};

before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-parcel-send-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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

test('payload hash is sha256 of the canonical JSONB text Android renders (ParcelCreateEnvelopeTest)',()=>{
  const literal=readFileSync(new URL('../../../app/src/test/resources/g26-parcel-canonical.txt',import.meta.url),'utf8');
  const fixture={id:'4d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b',workspaceId:'0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b',
    displayName:'Parcela "Olivo" Jódar',cadastralReference:'23050A00100001',cadastralPolygon:'1',cadastralParcel:'1',
    municipality:'Jódar',province:'Jaén',source:'CATASTRO',geometryGeoJson:'{"type":"Polygon","coordinates":[]}',
    sourceProvider:'catastro',sourceImportedAt:'2026-01-02T03:04:05.006Z',cadastralAreaM2:12345.5,managedAreaM2:10000000,
    notes:null,status:'ACTIVE',oliveTreeCount:120,variety:'Picual',irrigationSystem:'DRIP',irrigationNetwork:null,
    irrigationSector:null,irrigationDays:'L,X,V',memberships:[{id:'5d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b',
      farmId:'1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b',validFrom:'2026-01-01T00:00:00.007Z',validUntil:null,deletedAt:null}]};
  assert.equal(canonicalOf(fixture),literal);
});

test('owner creates the parcel aggregate with its UUIDs; receipt correlates; only the owner receives it',async()=>{
  const ws=await workspace(actors.A), farmId=await farm(actors.A,ws), id=randomUUID(), op=randomUUID();
  const parcel=parcelBody(id,ws,farmId);
  const created=await create(actors.A,op,parcel);
  assert.equal(created.status,200,JSON.stringify(created.data));
  assert.deepEqual(Object.keys(created.data).sort(),
    ['entity_id','idempotent','operation_id','payload_version','remote_version','status','success']);
  assert.equal(created.data.idempotent,false);
  const rows=(await receipt(actors.A,op)).data.confirmed_receipts;
  assert.equal(rows.length,1);
  assert.equal(rows[0].entity_type,'PARCEL');
  assert.equal(rows[0].payload_hash,createHash('sha256').update(canonicalOf(parcel),'utf8').digest('hex'));
  const graph=await parents(actors.A,ws);
  assert.equal(graph.status,200,JSON.stringify(graph.data));
  assert.deepEqual(graph.data.parcels.map(p=>[p.id,p.displayName,p.version,p.managedAreaM2,p.oliveTreeCount]),
    [[id,'Parcela 1',1,1234.5,120]]);
  assert.deepEqual(graph.data.memberships.map(m=>[m.id,m.farmId,m.parcelId,m.validFrom,m.version]),
    [[parcel.memberships[0].id,farmId,id,'2026-01-01T00:00:00.007Z',1]]);
  assert.equal((await parents(actors.C,ws)).status,403);
  assert.deepEqual((await receipt(actors.C,op)).data.confirmed_receipts,[]);
});

test('lost-response replay is idempotent; other operations, payloads or accounts cannot duplicate or take the parcel',async()=>{
  const ws=await workspace(actors.A), farmId=await farm(actors.A,ws), id=randomUUID(), op=randomUUID();
  const parcel=parcelBody(id,ws,farmId);
  assert.equal((await create(actors.A,op,parcel)).status,200);
  const replay=await create(actors.A,op,parcel);
  assert.equal(replay.status,200);
  assert.equal(replay.data.idempotent,true);
  assert.equal((await create(actors.A,op,{...parcel,displayName:'Otro contenido'})).status,409);
  assert.equal((await create(actors.A,randomUUID(),parcel)).status,409);
  assert.equal((await create(actors.A,randomUUID(),parcelBody(randomUUID(),ws,farmId,{memberships:[]}))).status,400);
  // C: not in A's workspace, not with A's farm in C's workspace, and not A's parcel UUID in C's own aggregate.
  assert.equal((await create(actors.C,randomUUID(),parcelBody(randomUUID(),ws,farmId))).status,403);
  const cws=await workspace(actors.C), cfarm=await farm(actors.C,cws);
  assert.equal((await create(actors.C,randomUUID(),parcelBody(randomUUID(),cws,farmId))).status,403);
  assert.equal((await create(actors.C,randomUUID(),parcelBody(id,cws,cfarm))).status,409);
  assert.equal(sql(`SELECT count(*)||'|'||max(display_name)||'|'||max(workspace_id::text) FROM public.parcels WHERE id=${q(id)}::uuid;`),
    `1|Parcela 1|${ws}`);
  assert.equal(sql(`SELECT count(*) FROM public.farm_parcel_memberships WHERE parcel_id=${q(id)}::uuid;`),'1');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)}::uuid;`),'1');
});

test('after the workspace is deleted, a replay is refused and the parcel is no longer received',async()=>{
  const ws=await workspace(actors.A), farmId=await farm(actors.A,ws), id=randomUUID(), op=randomUUID();
  const parcel=parcelBody(id,ws,farmId);
  assert.equal((await create(actors.A,op,parcel)).status,200);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(ws)}::uuid;`);
  assert.equal((await create(actors.A,op,parcel)).status,403);
  assert.equal((await parents(actors.A,ws)).status,403);
  assert.equal((await receipt(actors.A,op)).data.confirmed_receipts.length,1);
});
