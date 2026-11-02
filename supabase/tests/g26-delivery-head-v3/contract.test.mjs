// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-delivery-head-v3.sql (#808 HEAD v3 gate). Tests run in file order.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac, createHash } from 'node:crypto';

const work = process.env.G26_HEAD_WORK;
const project = process.env.G26_HEAD_PROJECT;
assert.match(project ?? '', /^mg26-head-[0-9]+-[0-9]+$/);
assert.ok(work);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['localhost', '127.0.0.1'].includes(api.hostname));
assert.ok(readFileSync(join(work, 'supabase/config.toml'), 'utf8').includes(`project_id = "${project}"`));
const container = `supabase_db_${project}`;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', ['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'],
  {input:text,encoding:'utf8',timeout:120000,maxBuffer:64*1024*1024,stdio:['pipe','pipe','pipe']}).trim();
async function http(path, {token,admin=false,body,method='POST'}={}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path.replace(/^\//,''), {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data,raw};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor, {issuer=ISSUER, expiredBy=0, extra={}}={}) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:issuer,
    exp:expiredBy?Math.floor(Date.now()/1000)-expiredBy:claims.exp,...extra})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const rpc=(name,actor,body,authToken=token(actor))=>http(`/rest/v1/rpc/${name}`,{token:authToken,body});
const head=(actor,id,authToken)=>rpc('get_own_delivery_head_v3',actor,{p_delivery_id:id},authToken);
const headV2=(actor,id)=>rpc('get_own_delivery_v2',actor,{p_delivery_id:id});
const v3=(actor,d,op=randomUUID())=>rpc('sync_own_delivery_v3',actor,{p_operation_id:op,p_delivery:d});
const v2=(actor,d,op=randomUUID())=>rpc('sync_own_delivery_v2',actor,{p_operation_id:op,p_delivery:d});
const v1=(actor,d,op=randomUUID())=>rpc('sync_own_delivery',actor,{p_operation_id:op,p_delivery:d});
const receipt=async(actor,op)=>(await rpc('check_sync_operations',actor,{p_operation_ids:[op]})).data.confirmed_receipts[0];
const hashOf=payload=>sql(`SELECT encode(sha256(convert_to(${q(JSON.stringify(payload))}::jsonb::text,'UTF8')),'hex');`);

const START='2025-09-01T00:00:00.007Z';
const workspace=async actor=>{
  const id=randomUUID();
  const r=await rpc('create_own_workspace',actor,{p_operation_id:randomUUID(),p_workspace:{id,name:'Olivar QA',
    countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}});
  assert.equal(r.status,200,r.raw); return id;
};
const farm=async(actor,ws,name)=>{
  const id=randomUUID(), req={payloadVersion:1,operationId:randomUUID(),farmId:id,localWorkspaceId:ws,
    consentId:randomUUID(),consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const reserved=await rpc('reserve_local_farm_g1c',actor,{p_request:req,p_remote_workspace:ws});
  assert.equal(reserved.status,200,reserved.raw);
  const r=await rpc('create_reserved_farm',actor,{p_operation_id:randomUUID(),p_reservation_operation_id:req.operationId,
    p_reservation_receipt_id:reserved.data.receiptId,p_farm:{id,workspaceId:ws,name,description:null,
      municipality:'Bedmar',province:'Jaén',notes:null,status:'ACTIVE',coverDocumentId:null}});
  assert.equal(r.status,200,r.raw); return {id,name};
};
const parcel=async(actor,ws,farmRef,name)=>{
  const id=randomUUID(), membership=randomUUID();
  const r=await rpc('create_own_parcel',actor,{p_operation_id:randomUUID(),p_parcel:{id,workspaceId:ws,status:'ACTIVE',
    displayName:name,cadastralReference:null,cadastralPolygon:null,cadastralParcel:null,municipality:'Bedmar',province:'Jaén',
    source:'MANUAL',geometryGeoJson:null,sourceProvider:null,sourceImportedAt:null,cadastralAreaM2:null,managedAreaM2:1234.5,
    notes:null,oliveTreeCount:120,variety:'Picual',irrigationSystem:null,irrigationNetwork:null,irrigationSector:null,
    irrigationDays:null,memberships:[{id:membership,farmId:farmRef.id,validFrom:START,validUntil:null,deletedAt:null}]}});
  assert.equal(r.status,200,r.raw); return {id,membership,name,version:1};
};
const campaign=async(actor,ws,farmRef,parcels)=>{
  const id=randomUUID();
  const r=await rpc('create_own_campaign',actor,{p_operation_id:randomUUID(),p_campaign:{id,workspaceId:ws,farmId:farmRef.id,
    name:'Campaña QA',startDate:'2025-10-01',endDate:'2026-10-31',status:'CLOSED',notes:null,parcels:parcels.map(p=>({id:randomUUID(),
      parcelId:p.id,farmIdAtStart:farmRef.id,farmNameAtStart:farmRef.name,parcelNameAtStart:p.name,managedAreaM2AtStart:1234.5,
      cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}))}});
  assert.equal(r.status,200,r.raw); return id;
};

const f={}, actors={};
const share=p=>({id:randomUUID(),parcelId:p.id,campaignParcelId:null,parcelNameAtDelivery:p.name,weightGrams:1000,allocationMode:'EXACT'});
const dv=(o={})=>({id:randomUUID(),farmId:f.north.id,campaignId:f.cn,deliveryDate:'2026-10-14',destinationName:'Cooperativa QA',
  destinationOrganizationId:null,netGrams:1000,grossGrams:null,tareGrams:null,deliveryNumber:null,ticketNumber:null,source:'MANUAL',
  notes:null,harvestId:null,deliveryTime:null,harvestOrigin:null,baseVersion:null,parcels:[share(f.act)],
  originCreatedAt:'2026-10-14T18:00:00Z',...o});
const legacy=d=>{const {originCreatedAt,...rest}=d; return rest;};
const ok=(r,label)=>{ assert.equal(r.status,200,`${label}: ${r.raw.slice(0,400)}`); return r.data; };
/** The strict ACK triple: the head names THIS operation, at the receipt's version, with the receipt's hash. */
const assertHeadIs=async(id,op,version,payloadVersion)=>{
  const h=ok(await head(actors.A,id),'head');
  const r=await receipt(actors.A,op);
  assert.deepEqual([h.head_operation_id,h.version,h.head_payload_version,h.head_payload_hash,h.deleted],
    [op,version,payloadVersion,r.payload_hash,false]);
  assert.equal(r.remote_version,version);
  return h;
};
const assertHeadless=async(id)=>{
  const h=ok(await head(actors.A,id),'head');
  assert.deepEqual([h.head_operation_id,h.head_payload_version,h.head_payload_hash],[null,null,null]);
  return h;
};
const rejected=async(call,code,message,httpStatus=403)=>{
  const r=await call();
  assert.equal(r.status,httpStatus,r.raw.slice(0,300));
  if(code) { assert.equal(r.data.code,code,r.raw); assert.equal(r.data.message,message,r.raw); }
  assert.ok(!/head_payload_hash|payload_hash/.test(r.raw),'no hash leaked in a refusal');
};
const fingerprint=()=>sql(`SELECT md5(concat_ws('|',
  (SELECT string_agg(id||':'||version,',' ORDER BY id) FROM public.deliveries),
  (SELECT count(*)::text FROM magina_g2_private.sync_operations),
  (SELECT count(*)::text FROM magina_g2_private.fact_origins)));`);
const LEGACY=['public.get_own_delivery_v2(uuid)','public.sync_own_delivery(uuid,jsonb)','public.sync_own_delivery_v2(uuid,jsonb)',
  'public.sync_own_delivery_v3(uuid,jsonb)','public.sync_own_expense(uuid,jsonb)','public.sync_own_expense_v2(uuid,jsonb)',
  'public.check_sync_operations(uuid[])','magina_g2_private.sync_delivery(uuid,jsonb,integer)',
  'magina_g2_private.sync_delivery_historical(uuid,jsonb)','magina_g2_private.historical_actor()'];
const surface=()=>sql(`SELECT concat_ws(E'\\n',
  (SELECT string_agg(p.oid::regprocedure::text||'='||md5(p.prosrc||'|'||coalesce(array_to_string(p.proconfig,','),'')||'|'||
     coalesce(p.proacl::text,'')||'|'||p.prosecdef::text||'|'||p.provolatile::text),';' ORDER BY p.oid::regprocedure::text)
   FROM pg_proc p WHERE p.oid IN (${LEGACY.map(fn=>`${q(fn)}::regprocedure`).join(',')})),
  (SELECT string_agg(tablename||'.'||policyname||':'||cmd||':'||coalesce(qual,'-'),';' ORDER BY tablename,policyname) FROM pg_policies WHERE schemaname='public'),
  (SELECT string_agg(table_name||':'||grantee||':'||privilege_type,';' ORDER BY table_name,grantee,privilege_type)
   FROM information_schema.role_table_grants WHERE grantee IN ('anon','authenticated','PUBLIC')),
  (SELECT string_agg(table_schema||'.'||table_name||'.'||column_name,';' ORDER BY table_schema,table_name,ordinal_position)
   FROM information_schema.columns WHERE table_schema IN ('public','magina_g2_private')));`);

before(async()=>{
  for(const label of ['A','C','M']) {
    const email=`g26-head-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}});
    assert.equal(created.status,200,created.raw);
    const login=await http('/auth/v1/token?grant_type=password',{body:{email,password}});
    assert.equal(login.status,200);
    actors[label]={id:created.data.id,token:login.data.access_token};
  }
  const migrations=['20261011120000_g0_auth_workspaces_shared_farms.sql','20261011120001_g0_agricultural_sync_schema.sql',
    '20261011120002_g0_contracts_and_storage_rls.sql','20261016041222_g0_legacy_storage_parcel_hardening.sql',
    '20261016041601_g0_aggregate_identity_guards.sql'];
  const base=[...migrations.map(name=>new URL(`../../migrations/${name}`,import.meta.url)),
    ...['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql'].map(name=>new URL(`../../proposals/${name}`,import.meta.url)),
    new URL('../../migrations/20261029044901_g26_workspace_authorization.sql',import.meta.url),
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql','g26-parcel-mutation-v1.sql','g26-historical-writers-v1.sql']
      .map(name=>new URL(`../../proposals/${name}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.before=surface();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-delivery-head-v3.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.after=surface();
  for(let attempt=0;;attempt++){
    if((await head(actors.A,randomUUID())).status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose get_own_delivery_head_v3');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.south=await farm(a,f.ws,'Finca Sur');
  f.act=await parcel(a,f.ws,f.north,'Activa');
  f.arch=await parcel(a,f.ws,f.north,'Archivada');
  f.southParcel=await parcel(a,f.ws,f.south,'Sur');
  f.cn=await campaign(a,f.ws,f.north,[f.act,f.arch]);
  f.cs=await campaign(a,f.ws,f.south,[f.southParcel]);
  const archived=await rpc('archive_own_parcel_v1',a,{p_operation_id:randomUUID(),p_parcel_id:f.arch.id,p_expected_version:1,
    p_archive:{archivedAt:'2026-10-15T08:30:00Z',closeMembershipId:f.arch.membership}});
  assert.equal(archived.status,200,archived.raw);
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
       VALUES (${q(f.ws)},${q(actors.M.id)},'EDITOR',ARRAY['read','write']);`);
});

test('every older RPC, writer, table, column, RLS policy and grant is byte-identical after loading the proposal',()=>{
  assert.equal(f.before.split('\n')[0].split(';').length,LEGACY.length);
  assert.equal(f.after,f.before);
});

test('privileges: authenticated only, SECURITY DEFINER, empty search_path, STABLE, no private exposure',async()=>{
  assert.equal(sql(`SELECT has_function_privilege('anon','public.get_own_delivery_head_v3(uuid)','EXECUTE')||'|'||
    has_function_privilege('public','public.get_own_delivery_head_v3(uuid)','EXECUTE')||'|'||
    has_function_privilege('authenticated','public.get_own_delivery_head_v3(uuid)','EXECUTE');`),'false|false|true');
  assert.equal(sql(`SELECT prosecdef::text||'|'||provolatile::text||'|'||array_to_string(proconfig,',') FROM pg_proc
    WHERE proname='get_own_delivery_head_v3';`),'true|s|search_path=""');
  const anon=await http('/rest/v1/rpc/get_own_delivery_head_v3',{body:{p_delivery_id:randomUUID()}});
  assert.ok([401,403].includes(anon.status),anon.raw);
  assert.equal((await http('/rest/v1/sync_operations?select=*',{method:'GET',token:token(actors.A)})).status,404);
});

test('v3 create: the head is exactly that operation, its version and its hash; get_own_delivery_v2 stays blind',async()=>{
  const a=actors.A, d=dv(), op=randomUUID();
  ok(await v3(a,d,op),'v3');
  const h=await assertHeadIs(d.id,op,1,3);
  assert.equal(h.head_payload_hash,hashOf(d));
  assert.equal(h.snapshot_format,2);
  assert.deepEqual(Object.keys(h).sort(),['contract_version','deleted','delivery_id','head_operation_id','head_payload_hash',
    'head_payload_version','snapshot_format','version']);
  assert.equal(h.contract_version,1); assert.equal(h.delivery_id,d.id);
  // The legacy read is untouched and, as documented, cannot name a v3 receipt.
  assert.equal(ok(await headV2(a,d.id),'v2').head_payload_hash,null);
});

test('mixed history v2 -> v3 -> v2: the head always follows the CURRENT version only',async()=>{
  const a=actors.A, d=dv();
  const op1=randomUUID(); ok(await v2(a,legacy(d),op1),'v2 create');
  const h1=await assertHeadIs(d.id,op1,1,2);
  assert.equal(ok(await headV2(a,d.id),'v2').head_payload_hash,h1.head_payload_hash,'same hash as the legacy head for v2');
  const op2=randomUUID(); ok(await v3(a,{...d,baseVersion:1,ticketNumber:'T-1',originCreatedAt:'2026-10-14T18:00:00Z'},op2),'v3 update');
  await assertHeadIs(d.id,op2,2,3);
  assert.equal(ok(await headV2(a,d.id),'v2').head_payload_hash,null);
  const op3=randomUUID(); ok(await v2(a,legacy({...d,baseVersion:2,ticketNumber:'T-2'}),op3),'v2 update');
  await assertHeadIs(d.id,op3,3,2);
  // An older operation is never the head again.
  const old=await receipt(a,op1);
  assert.notEqual(ok(await head(a,d.id),'head').head_payload_hash,old.payload_hash);
});

test('mixed history v3 -> v2: the v2 update becomes the head',async()=>{
  const a=actors.A, d=dv();
  const op1=randomUUID(); ok(await v3(a,d,op1),'v3 create');
  await assertHeadIs(d.id,op1,1,3);
  const op2=randomUUID(); ok(await v2(a,legacy({...d,baseVersion:1,notes:'corregida'}),op2),'v2 update');
  await assertHeadIs(d.id,op2,2,2);
});

test('lost response: the repeat is idempotent and the head is still that single operation',async()=>{
  const a=actors.A, d=dv(), op=randomUUID();
  ok(await v3(a,d,op),'v3');
  const replay=ok(await v3(a,d,op),'replay'); assert.equal(replay.idempotent,true);
  await assertHeadIs(d.id,op,1,3);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(d.id)};`),'1');
});

test('zero or several candidates give a null head: duplicate receipt, v1 header write, out-of-band write',async()=>{
  const a=actors.A;
  // A second applied receipt claiming the same current version: ambiguous, so nothing is named.
  const dup=dv(); const op=randomUUID(); ok(await v3(a,dup,op),'v3');
  sql(`INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,status,result_version,payload_version)
       VALUES (${q(a.id)},${q(randomUUID())},'DELIVERY',${q(dup.id)},${q('f'.repeat(64))},'APPLIED',1,3);`);
  await assertHeadless(dup.id);
  // A v1 header-only write has no head in this contract.
  const header={id:randomUUID(),farmId:f.north.id,campaignId:f.cn,deliveryDate:'2026-10-14',destinationName:'Coop',netGrams:500};
  ok(await v1(a,header),'v1');
  const h1=await assertHeadless(header.id); assert.equal(h1.version,1);
  // A write outside the receipt protocol moves the version: the old receipt is no longer the head.
  const moved=dv(); const op2=randomUUID(); ok(await v3(a,moved,op2),'v3');
  sql(`UPDATE public.deliveries SET notes='fuera de protocolo' WHERE id=${q(moved.id)};`);
  const h2=await assertHeadless(moved.id); assert.equal(h2.version,2);
});

test('a Pesada over an archived parcel has a head; a deleted one has none',async()=>{
  const a=actors.A, d=dv({parcels:[share(f.arch)]}), op=randomUUID();
  ok(await v3(a,d,op),'v3 historical');
  await assertHeadIs(d.id,op,1,3);
  sql(`UPDATE public.deliveries SET deleted_at=now() WHERE id=${q(d.id)};`);
  const h=ok(await head(a,d.id),'head');
  assert.equal(h.deleted,true); assert.equal(h.head_operation_id,null); assert.equal(h.head_payload_hash,null);
});

test('authority: other account, member with write, unknown id, deleted workspace and archived farm; fail-closed identity',async()=>{
  const {A,C,M}=actors, d=dv(); ok(await v3(A,d),'v3');
  await rejected(()=>head(C,d.id),'42501','sync denied: SCOPE');
  await rejected(()=>head(M,d.id),'42501','sync denied: SCOPE');
  await rejected(()=>head(A,randomUUID()),'42501','sync denied: SCOPE');
  await rejected(()=>head(A,null),'22023','sync invalid: PAYLOAD',400);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  try { await rejected(()=>head(A,d.id),'42501','sync denied: SCOPE'); }
  finally { sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`); }
  const s=dv({farmId:f.south.id,campaignId:f.cs,parcels:[share(f.southParcel)]}); ok(await v3(A,s),'v3 south');
  sql(`UPDATE public.farms SET status='ARCHIVED',deleted_at=now() WHERE id=${q(f.south.id)};`);
  try { await rejected(()=>head(A,s.id),'42501','sync denied: FARM_NOT_LIVE'); }
  finally { sql(`UPDATE public.farms SET status='ACTIVE',deleted_at=NULL WHERE id=${q(f.south.id)};`); }
  for(const authToken of [token(A,{issuer:'https://evil.example/auth/v1'}),token(A,{extra:{is_anonymous:true}}),
    token(A,{extra:{iss:undefined}}),token(A,{expiredBy:1})])
    await rejected(()=>head(A,d.id,authToken),'42501','sync denied: IDENTITY');
  await rejected(()=>head(A,d.id,token(A,{expiredBy:3600})),null,null,401);
});

test('owner change: the former owner is refused and the new owner never sees the former owner\'s receipt',async()=>{
  const {A,M}=actors, d=dv(), op=randomUUID(); ok(await v3(A,d,op),'v3');
  sql(`UPDATE public.workspaces SET owner_user_id=${q(M.id)} WHERE id=${q(f.ws)};`);
  try {
    await rejected(()=>head(A,d.id),'42501','sync denied: SCOPE');
    const seen=ok(await head(M,d.id),'new owner');
    assert.deepEqual([seen.head_operation_id,seen.head_payload_version,seen.head_payload_hash],[null,null,null]);
    assert.ok(!JSON.stringify(seen).includes(op));
  } finally { sql(`UPDATE public.workspaces SET owner_user_id=${q(A.id)} WHERE id=${q(f.ws)};`); }
  await assertHeadIs(d.id,op,1,3);
});

test('the read never writes and repeated reads are byte-identical',async()=>{
  const a=actors.A, d=dv(); ok(await v3(a,d),'v3');
  const beforeState=fingerprint();
  const first=await head(a,d.id), second=await head(a,d.id);
  assert.equal(first.status,200); assert.equal(second.raw,first.raw);
  for(let i=0;i<5;i++) await head(a,randomUUID());
  assert.equal(fingerprint(),beforeState);
  assert.equal(createHash('sha256').update(first.raw).digest('hex'),createHash('sha256').update(second.raw).digest('hex'));
});
