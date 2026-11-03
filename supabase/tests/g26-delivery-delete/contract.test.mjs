// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-delivery-delete-v1.sql (#808 R1-a, owner GO on #828).
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac, createHash } from 'node:crypto';

const work = process.env.G26_DD_WORK;
const project = process.env.G26_DD_PROJECT;
assert.match(project ?? '', /^mg26-dd-[0-9]+-[0-9]+$/);
assert.ok(work);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['localhost', '127.0.0.1'].includes(api.hostname));
assert.ok(readFileSync(join(work, 'supabase/config.toml'), 'utf8').includes(`project_id = "${project}"`));
const container = `supabase_db_${project}`;
const psqlArgs = ['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'];
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', psqlArgs, {input:text,encoding:'utf8',timeout:120000,maxBuffer:64*1024*1024,stdio:['pipe','pipe','pipe']}).trim();
async function http(path, {token,admin=false,body,method='POST'}={}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path.replace(/^\//,''), {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data,raw};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor, extra={}) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:ISSUER,...extra})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const rpc=(name,actor,body,authToken=token(actor))=>http(`/rest/v1/rpc/${name}`,{token:authToken,body});
const hv=(actor,h,op=randomUUID())=>rpc('sync_own_harvest_v1',actor,{p_operation_id:op,p_harvest:h});
const hdel=(actor,id,version,op=randomUUID())=>rpc('delete_own_harvest_v1',actor,{p_operation_id:op,p_harvest_id:id,p_expected_version:version});
const hhead=(actor,id)=>rpc('get_own_harvest_head_v1',actor,{p_harvest_id:id});
const v3=(actor,d,op=randomUUID())=>rpc('sync_own_delivery_v3',actor,{p_operation_id:op,p_delivery:d});
const v2=(actor,d,op=randomUUID())=>rpc('sync_own_delivery_v2',actor,{p_operation_id:op,p_delivery:d});
const v1=(actor,d,op=randomUUID())=>rpc('sync_own_delivery',actor,{p_operation_id:op,p_delivery:d});

const START='2025-09-01T00:00:00.007Z';
const workspace=async actor=>{ const id=randomUUID();
  const r=await rpc('create_own_workspace',actor,{p_operation_id:randomUUID(),p_workspace:{id,name:'Olivar QA',countryCode:'ES',
    timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}}); assert.equal(r.status,200,r.raw); return id; };
const farm=async(actor,ws,name)=>{ const id=randomUUID(), req={payloadVersion:1,operationId:randomUUID(),farmId:id,localWorkspaceId:ws,
    consentId:randomUUID(),consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const reserved=await rpc('reserve_local_farm_g1c',actor,{p_request:req,p_remote_workspace:ws}); assert.equal(reserved.status,200,reserved.raw);
  const r=await rpc('create_reserved_farm',actor,{p_operation_id:randomUUID(),p_reservation_operation_id:req.operationId,
    p_reservation_receipt_id:reserved.data.receiptId,p_farm:{id,workspaceId:ws,name,description:null,municipality:'Bedmar',province:'Jaén',
      notes:null,status:'ACTIVE',coverDocumentId:null}}); assert.equal(r.status,200,r.raw); return {id,name}; };
const parcel=async(actor,ws,farmRef,name)=>{ const id=randomUUID(), membership=randomUUID();
  const r=await rpc('create_own_parcel',actor,{p_operation_id:randomUUID(),p_parcel:{id,workspaceId:ws,status:'ACTIVE',displayName:name,
    cadastralReference:null,cadastralPolygon:null,cadastralParcel:null,municipality:'Bedmar',province:'Jaén',source:'MANUAL',geometryGeoJson:null,
    sourceProvider:null,sourceImportedAt:null,cadastralAreaM2:null,managedAreaM2:1234.5,notes:null,oliveTreeCount:120,variety:'Picual',
    irrigationSystem:null,irrigationNetwork:null,irrigationSector:null,irrigationDays:null,
    memberships:[{id:membership,farmId:farmRef.id,validFrom:START,validUntil:null,deletedAt:null}]}}); assert.equal(r.status,200,r.raw);
  return {id,membership,name}; };
const campaign=async(actor,ws,farmRef,parcels,start='2025-10-01',end='2026-10-31')=>{ const id=randomUUID();
  const r=await rpc('create_own_campaign',actor,{p_operation_id:randomUUID(),p_campaign:{id,workspaceId:ws,farmId:farmRef.id,name:'Campaña QA',
    startDate:start,endDate:end,status:'CLOSED',notes:null,parcels:parcels.map(p=>({id:randomUUID(),parcelId:p.id,farmIdAtStart:farmRef.id,
      farmNameAtStart:farmRef.name,parcelNameAtStart:p.name,managedAreaM2AtStart:1234.5,cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}))}});
  assert.equal(r.status,200,r.raw); return id; };

const f={}, actors={};
const day=(o={})=>({id:randomUUID(),farmId:f.north.id,campaignId:f.cn,harvestDate:'2026-10-14',dayOrigin:'AUTO_DAY',weightGrams:null,
  destination:null,notes:null,collectionMethod:null,workerCount:null,machineryText:null,baseVersion:null,...o});
const share=(p,w=1000,mode='EXACT')=>({id:randomUUID(),parcelId:p.id,campaignParcelId:null,parcelNameAtDelivery:p.name,
  weightGrams:mode==='EXACT'?w:null,allocationMode:mode});
// The parcel share follows netGrams unless the caller gives its own lot, so every Pesada reconciles.
const dv=(harvestId,o={})=>({id:randomUUID(),farmId:f.north.id,campaignId:f.cn,deliveryDate:'2026-10-14',destinationName:'Cooperativa QA',
  destinationOrganizationId:null,netGrams:1000,grossGrams:null,tareGrams:null,deliveryNumber:null,ticketNumber:null,source:'MANUAL',
  notes:null,harvestId,deliveryTime:null,harvestOrigin:null,baseVersion:null,parcels:[share(f.act,o.netGrams??1000)],
  originCreatedAt:'2026-10-14T18:00:00Z',...o});
const legacy=d=>{const {originCreatedAt,...rest}=d; return rest;};
const ok=(r,label)=>{ assert.equal(r.status,200,`${label}: ${r.raw.slice(0,400)}`); return r.data; };
const refused=(r,code,message,httpStatus)=>{
  assert.equal(r.status,httpStatus ?? ({'42501':403,'22023':400,'23505':409,'23514':400,'22003':400})[code],r.raw.slice(0,300));
  assert.equal(r.data.code,code,r.raw); assert.equal(r.data.message,message,r.raw);
  return r.data;
};
// boolean::text inside || is 'true'/'false' (not psql's t/f display): parse strictly, never guess.
const pgBool=t=>{ if(t==='true') return true; if(t==='false') return false; throw new Error(`unexpected boolean text: ${JSON.stringify(t)}`); };
const harvestRow=id=>{ const [w,v,o,del]=sql(`SELECT weight_grams||'|'||version||'|'||coalesce(day_origin,'-')||'|'||(deleted_at IS NOT NULL)
  FROM public.harvests WHERE id=${q(id)};`).split('|'); return {weight:Number(w),version:Number(v),origin:o,deleted:pgBool(del)}; };
const parcelsOf=id=>sql(`SELECT coalesce(string_agg(parcel_id||':'||allocation_mode||':'||coalesce(weight_grams::text,'null'),',' ORDER BY parcel_id),'')
  FROM public.harvest_parcels WHERE harvest_id=${q(id)};`);
const expectParcels=(id,rows)=>assert.equal(parcelsOf(id),rows.map(([p,m,w])=>`${p.id}:${m}:${w}`).sort().join(','));

// Two sessions: a psql holder keeps a transaction open while an HTTP call runs into its locks.
function holder(actor,statements,seconds=4){
  const tag=`g808d_${randomUUID().replaceAll('-','')}`;
  const claims=JSON.stringify({sub:actor.id,role:'authenticated',aud:'authenticated',iss:ISSUER,exp:Math.floor(Date.now()/1000)+3600,is_anonymous:false});
  const child=spawn('docker',psqlArgs,{stdio:['pipe','pipe','pipe']});
  let out='',err=''; child.stdout.on('data',d=>out+=d); child.stderr.on('data',d=>err+=d);
  child.stdin.end(`SET application_name=${q(tag)};\nBEGIN;\nSELECT set_config('request.jwt.claims',${q(claims)},true);\nSET LOCAL ROLE authenticated;\n${statements}\nRESET ROLE;\nSELECT pg_sleep(${seconds});\nCOMMIT;\nSELECT 'HOLDER_COMMITTED';`);
  return {tag,done:new Promise(resolve=>child.on('close',code=>resolve({code,out,err})))};
}
const until=async(probe,what,ms=20000)=>{ const deadline=Date.now()+ms;
  while(Date.now()<deadline){ if(probe()) return; await new Promise(r=>setTimeout(r,100)); } throw new Error(`timeout: ${what}`); };
async function race(h,calls){
  await until(()=>sql(`SELECT count(*) FROM pg_stat_activity WHERE application_name=${q(h.tag)} AND state='active' AND query LIKE 'SELECT pg_sleep%';`)==='1','holder');
  const pending=calls.map(call=>call());
  await until(()=>Number(sql(`SELECT count(*) FROM pg_stat_activity a WHERE a.wait_event_type IN ('Lock','Advisory')
    AND (SELECT pid FROM pg_stat_activity WHERE application_name=${q(h.tag)}) = ANY(pg_blocking_pids(a.pid));`))>0,'blocked request');
  const results=await Promise.all(pending);
  const held=await h.done; assert.equal(held.code,0,held.err); assert.match(held.out,/HOLDER_COMMITTED/);
  return results;
}

const ddel=(actor,id,version,op=randomUUID(),authToken)=>rpc('delete_own_delivery_v1',actor,
  {p_operation_id:op,p_delivery_id:id,p_expected_version:version},authToken);
const dhead=(actor,id)=>rpc('get_own_delivery_delete_head_v1',actor,{p_delivery_id:id});
const v3head=(actor,id)=>rpc('get_own_delivery_head_v3',actor,{p_delivery_id:id});
const check=(actor,ops)=>rpc('check_sync_operations',actor,{p_operation_ids:ops});
// The canonical jsonb text PostgreSQL hashes: keys by length then bytes, ", " and ": " separators.
const deleteHash=(id,version)=>createHash('sha256')
  .update(`{"kind": "DELETE", "deliveryId": "${id}", "expectedVersion": ${version}}`,'utf8').digest('hex');

// Everything a refused delete must leave as it was: the Pesada row, its parcels, its Jornada and receipts.
const snapshot=(deliveryId,harvestId)=>sql(`SELECT concat_ws(E'\\n',
  (SELECT row_to_json(d)::text FROM public.deliveries d WHERE d.id=${q(deliveryId)}),
  (SELECT coalesce(string_agg(row_to_json(p)::text,';' ORDER BY p.id),'') FROM public.delivery_parcels p WHERE p.delivery_id=${q(deliveryId)}),
  (SELECT row_to_json(h)::text FROM public.harvests h WHERE h.id=${q(harvestId)}),
  (SELECT coalesce(string_agg(row_to_json(hp)::text,';' ORDER BY hp.parcel_id),'') FROM public.harvest_parcels hp WHERE hp.harvest_id=${q(harvestId)}),
  (SELECT count(*)::text FROM magina_g2_private.sync_operations WHERE entity_id IN (${q(deliveryId)},${q(harvestId)})),
  (SELECT count(*)::text FROM magina_g2_private.fact_origins WHERE entity_id=${q(deliveryId)}));`);
const receipts=id=>Number(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)};`));
const deliveryRow=id=>{ const [v,del]=sql(`SELECT version||'|'||(deleted_at IS NOT NULL) FROM public.deliveries WHERE id=${q(id)};`).split('|');
  return {version:Number(v),deleted:pgBool(del)}; };

const WRITERS=['public.sync_own_delivery(uuid,jsonb)','public.sync_own_delivery_v2(uuid,jsonb)','public.sync_own_delivery_v3(uuid,jsonb)',
  'public.get_own_delivery_v2(uuid)','public.get_own_delivery_head_v3(uuid)','public.get_own_delivery_receive_v1(uuid)',
  'magina_g2_private.sync_delivery(uuid,jsonb,integer)','magina_g2_private.sync_delivery_historical(uuid,jsonb)','public.check_sync_operations(uuid[])',
  'public.sync_own_harvest_v1(uuid,jsonb)','public.delete_own_harvest_v1(uuid,uuid,bigint)','public.get_own_harvest_head_v1(uuid)',
  'magina_g2_private.sync_harvest(uuid,jsonb)','magina_g2_private.delete_harvest(uuid,uuid,bigint)','magina_g2_private.derive_auto_day(uuid)',
  'magina_g2_private.deliveries_derive_harvest()','magina_g2_private.delivery_parcels_derive_harvest()'];
const surface=()=>sql(`SELECT concat_ws(E'\\n',
  (SELECT string_agg(p.oid::regprocedure::text||'='||md5(p.prosrc||'|'||coalesce(array_to_string(p.proconfig,','),'')||'|'||coalesce(p.proacl::text,'')),';'
     ORDER BY p.oid::regprocedure::text) FROM pg_proc p WHERE p.oid IN (${WRITERS.map(fn=>`${q(fn)}::regprocedure`).join(',')})),
  (SELECT string_agg(schemaname||'.'||tablename||'.'||policyname||':'||cmd||':'||coalesce(qual,'-')||':'||coalesce(with_check,'-'),';'
     ORDER BY schemaname,tablename,policyname) FROM pg_policies WHERE schemaname IN ('public','magina_g2_private')),
  (SELECT string_agg(table_schema||'.'||table_name||':'||grantee||':'||privilege_type,';' ORDER BY table_schema,table_name,grantee,privilege_type)
   FROM information_schema.role_table_grants WHERE grantee IN ('anon','authenticated','PUBLIC')),
  (SELECT string_agg(table_schema||'.'||table_name||'.'||column_name||':'||data_type||':'||is_nullable,';' ORDER BY table_schema,table_name,ordinal_position)
   FROM information_schema.columns WHERE table_schema IN ('public','magina_g2_private')),
  (SELECT string_agg(conrelid::regclass::text||'.'||conname||':'||md5(pg_get_constraintdef(oid)),';' ORDER BY conrelid::regclass::text,conname)
   FROM pg_constraint WHERE connamespace IN ('public'::regnamespace,'magina_g2_private'::regnamespace)),
  (SELECT string_agg(c.relname||'.'||t.tgname,';' ORDER BY c.relname,t.tgname) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
   WHERE NOT t.tgisinternal AND c.relnamespace IN ('public'::regnamespace,'magina_g2_private'::regnamespace)));`);
const functions=()=>sql(`SELECT string_agg(p.oid::regprocedure::text,';' ORDER BY 1) FROM pg_proc p
  WHERE p.pronamespace IN ('public'::regnamespace,'magina_g2_private'::regnamespace);`).split(';');

before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-dd-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}}); assert.equal(created.status,200,created.raw);
    const login=await http('/auth/v1/token?grant_type=password',{body:{email,password}}); assert.equal(login.status,200);
    actors[label]={id:created.data.id,token:login.data.access_token};
  }
  const migrations=['20261011120000_g0_auth_workspaces_shared_farms.sql','20261011120001_g0_agricultural_sync_schema.sql',
    '20261011120002_g0_contracts_and_storage_rls.sql','20261016041222_g0_legacy_storage_parcel_hardening.sql','20261016041601_g0_aggregate_identity_guards.sql'];
  const base=[...migrations.map(n=>new URL(`../../migrations/${n}`,import.meta.url)),
    ...['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql'].map(n=>new URL(`../../proposals/${n}`,import.meta.url)),
    new URL('../../migrations/20261027155737_g25_receive_bundle_contract.sql',import.meta.url),
    new URL('../../migrations/20261029044901_g26_workspace_authorization.sql',import.meta.url),
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql','g26-parcel-mutation-v1.sql','g26-historical-writers-v1.sql',
      'g26-delivery-head-v3.sql','g26-harvest-writer-v1.sql'].map(n=>new URL(`../../proposals/${n}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.surfaceBefore=surface(); f.functionsBefore=functions();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-delivery-delete-v1.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.surfaceAfter=surface(); f.functionsAfter=functions();
  for(let attempt=0;;attempt++){ if((await dhead(actors.A,randomUUID())).status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose delete_own_delivery_v1'); await new Promise(r=>setTimeout(r,250)); }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.act=await parcel(a,f.ws,f.north,'Activa'); f.second=await parcel(a,f.ws,f.north,'Segunda');
  f.cn=await campaign(a,f.ws,f.north,[f.act,f.second]);
});

test('additive only: writers, reads, RLS, grants, columns, constraints and triggers unchanged; exactly three new functions',()=>{
  assert.equal(f.surfaceAfter,f.surfaceBefore);
  assert.deepEqual(f.functionsAfter.filter(fn=>!f.functionsBefore.includes(fn)).sort(),
    ['delete_own_delivery_v1(uuid,uuid,bigint)','get_own_delivery_delete_head_v1(uuid)','magina_g2_private.delete_delivery(uuid,uuid,bigint)']);
  assert.ok(f.functionsBefore.every(fn=>f.functionsAfter.includes(fn)));
});

test('privileges: two endpoints authenticated-only, private core closed; anon refused',async()=>{
  for(const fn of ['public.delete_own_delivery_v1(uuid,uuid,bigint)','public.get_own_delivery_delete_head_v1(uuid)'])
    assert.equal(sql(`SELECT has_function_privilege('anon',${q(fn)},'EXECUTE')||'|'||has_function_privilege('public',${q(fn)},'EXECUTE')||'|'||
      has_function_privilege('authenticated',${q(fn)},'EXECUTE');`),'false|false|true',fn);
  assert.equal(sql(`SELECT has_function_privilege('anon','magina_g2_private.delete_delivery(uuid,uuid,bigint)','EXECUTE')
    OR has_function_privilege('authenticated','magina_g2_private.delete_delivery(uuid,uuid,bigint)','EXECUTE');`),'f');
  const anon=await http('/rest/v1/rpc/delete_own_delivery_v1',{body:{p_operation_id:randomUUID(),p_delivery_id:randomUUID(),p_expected_version:1}});
  assert.ok([401,403].includes(anon.status),anon.raw);
});

test('logical delete on an AUTO_DAY with three Pesadas: kilos and split recomputed, receipt and HEADs coherent, nothing physically removed',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-20'}), hop=randomUUID(); ok(await hv(a,h,hop),'day');
  const p1=dv(h.id,{deliveryDate:'2026-10-20',netGrams:1000,parcels:[share(f.act,600),share(f.second,400)]});
  const p2=dv(h.id,{deliveryDate:'2026-10-20',netGrams:700,parcels:[share(f.second,700)]});
  const p3=dv(h.id,{deliveryDate:'2026-10-20',netGrams:300});
  ok(await v3(a,p1),'p1'); ok(await v3(a,p2),'p2'); ok(await v2(a,legacy(p3)),'p3 v2');
  assert.equal(harvestRow(h.id).weight,2000);
  expectParcels(h.id,[[f.act,'EXACT',900],[f.second,'EXACT',1100]]);
  const before=receipts(p1.id);
  const op=randomUUID(), done=ok(await ddel(a,p1.id,1,op),'delete');
  assert.deepEqual([done.remote_version,done.payload_version,done.deleted,done.idempotent,done.entity_id],[2,3,true,false,p1.id]);
  assert.deepEqual(deliveryRow(p1.id),{version:2,deleted:true});
  // The AUTO_DAY is recomputed in the same transaction; its own version never moves.
  assert.deepEqual(harvestRow(h.id),{weight:1000,version:1,origin:'AUTO_DAY',deleted:false});
  expectParcels(h.id,[[f.act,'EXACT',300],[f.second,'EXACT',700]]);
  // Logical only: the row, its parcels and its origin stay.
  assert.equal(sql(`SELECT count(*) FROM public.delivery_parcels WHERE delivery_id=${q(p1.id)};`),'2');
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.fact_origins WHERE entity_id=${q(p1.id)};`),'1');
  assert.throws(()=>sql(`DELETE FROM public.deliveries WHERE id=${q(p1.id)};`),/Hard delete forbidden/);
  // One receipt more, verifiable: v3 family, the deleted version, the canonical DELETE hash.
  assert.equal(receipts(p1.id),before+1);
  const receipt=ok(await check(a,[op]),'check').confirmed_receipts[0];
  assert.deepEqual([receipt.operation_id,receipt.entity_type,receipt.entity_id,receipt.remote_version,receipt.payload_version,receipt.payload_hash],
    [op,'DELIVERY',p1.id,2,3,deleteHash(p1.id,1)]);
  // HEAD v3 unchanged: deleted, no head, so a held WRITE can never be confirmed by this delete.
  const h3=ok(await v3head(a,p1.id),'head v3');
  assert.deepEqual([h3.version,h3.deleted,h3.head_operation_id,h3.head_payload_hash],[2,true,null,null]);
  // The delete HEAD names exactly this operation.
  const dh=ok(await dhead(a,p1.id),'delete head');
  assert.deepEqual([dh.version,dh.deleted,dh.head_operation_id,dh.head_payload_version,dh.head_payload_hash],[2,true,op,3,deleteHash(p1.id,1)]);
  // A live Pesada has no delete head; its v3 head is untouched.
  const live=ok(await dhead(a,p2.id),'live');
  assert.deepEqual([live.deleted,live.head_operation_id,live.head_payload_hash],[false,null,null]);
  assert.notEqual(ok(await v3head(a,p2.id),'live v3').head_operation_id,null);
  // A deleted Pesada accepts no further write nor a second delete.
  refused(await v3(a,{...p1,baseVersion:2}),'23505','sync conflict: DELETED');
  refused(await ddel(a,p1.id,2),'23505','sync conflict: DELETED');
  assert.equal(receipts(p1.id),before+1);
  // The Jornada HEAD still names its own operation.
  assert.equal(ok(await hhead(a,h.id),'harvest head').head_operation_id,hop);
});

test('idempotency: the same operation replays its receipt; a reused UUID with other data is RECEIPT_CONFLICT, nothing changes',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-21'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-21',netGrams:800}), other=dv(h.id,{deliveryDate:'2026-10-21',netGrams:200});
  const wop=randomUUID(); ok(await v3(a,p,wop),'p'); ok(await v3(a,other),'other');
  const op=randomUUID(); const first=ok(await ddel(a,p.id,1,op),'delete');
  const after=snapshot(p.id,h.id), otherAfter=snapshot(other.id,h.id);
  const again=ok(await ddel(a,p.id,1,op),'replay');
  assert.deepEqual([again.idempotent,again.remote_version,again.payload_version,again.deleted],[true,first.remote_version,3,true]);
  assert.equal(snapshot(p.id,h.id),after);
  // Same UUID, another expected version / another Pesada / a WRITE operation UUID: refused, no effect.
  refused(await ddel(a,p.id,2,op),'23505','sync conflict: RECEIPT_CONFLICT');
  refused(await ddel(a,other.id,1,op),'23505','sync conflict: RECEIPT_CONFLICT');
  refused(await ddel(a,other.id,1,wop),'23505','sync conflict: RECEIPT_CONFLICT');
  assert.equal(snapshot(p.id,h.id),after); assert.equal(snapshot(other.id,h.id),otherAfter);
  // The delete UUID can never be replayed as a v3 write either.
  refused(await v3(a,{...other,baseVersion:1},op),'23505','sync conflict: RECEIPT_CONFLICT');
  assert.equal(snapshot(other.id,h.id),otherAfter);
  assert.equal(harvestRow(h.id).weight,200);
});

test('CAS: a stale expected version is STALE_VERSION with the remote version, and changes nothing',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-22'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-22',netGrams:500}); ok(await v3(a,p),'p');
  ok(await v3(a,{...p,baseVersion:1,netGrams:600,parcels:[{...p.parcels[0],weightGrams:600}]}),'update');
  const before=snapshot(p.id,h.id);
  const stale=refused(await ddel(a,p.id,1),'23505','sync conflict: STALE_VERSION');
  assert.equal(stale.details,'remote_version=2');
  refused(await ddel(a,p.id,3),'23505','sync conflict: STALE_VERSION');
  refused(await ddel(a,p.id,0),'22023','sync invalid: PAYLOAD');
  assert.equal(snapshot(p.id,h.id),before);
  assert.equal(harvestRow(h.id).weight,600);
  ok(await ddel(a,p.id,2),'current version');
  assert.equal(harvestRow(h.id).weight,0);
});

test('the last Pesada of an AUTO_DAY: kilos 0, no split, Jornada alive and only then deletable',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-23'}), hop=randomUUID(); ok(await hv(a,h,hop),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-23',netGrams:900,parcels:[share(f.act,900)]}); ok(await v3(a,p),'p');
  refused(await hdel(a,h.id,1),'23505','sync conflict: HARVEST_HAS_DEPENDENTS');
  ok(await ddel(a,p.id,1),'delete last');
  assert.deepEqual(harvestRow(h.id),{weight:0,version:1,origin:'AUTO_DAY',deleted:false});
  assert.equal(parcelsOf(h.id),'');
  assert.equal(ok(await hhead(a,h.id),'head').head_operation_id,hop);
  ok(await hdel(a,h.id,1),'day delete now allowed');
  assert.equal(harvestRow(h.id).deleted,true);
});

test('MANUAL: deleting its Pesadas never touches the typed kilos',async()=>{
  const a=actors.A, manual=day({dayOrigin:'MANUAL',weightGrams:4242,harvestDate:'2026-10-24'}); ok(await hv(a,manual),'manual');
  const p=dv(manual.id,{deliveryDate:'2026-10-24',netGrams:999}), q2=dv(manual.id,{deliveryDate:'2026-10-24',netGrams:1});
  ok(await v3(a,p),'p'); ok(await v2(a,legacy(q2)),'q v2');
  ok(await ddel(a,p.id,1),'delete v3'); ok(await ddel(a,q2.id,1),'delete v2-written');
  assert.deepEqual(harvestRow(manual.id),{weight:4242,version:1,origin:'MANUAL',deleted:false});
  assert.equal(parcelsOf(manual.id),'');
});

test('identity and scope: other account, wrong issuer, expired session, revoked and restored owner; refusals change nothing',async()=>{
  const a=actors.A, c=actors.C, h=day({harvestDate:'2026-10-25'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-25',netGrams:400}); ok(await v3(a,p),'p');
  const other=dv(h.id,{deliveryDate:'2026-10-25',netGrams:600}); ok(await v3(a,other),'other');
  const before=snapshot(p.id,h.id);
  // Another account never learns whether the Pesada exists.
  const foreign=refused(await ddel(c,p.id,1),'42501','sync denied: SCOPE');
  assert.ok(!JSON.stringify(foreign).includes(String(h.id)));
  refused(await dhead(c,p.id),'42501','sync denied: SCOPE');
  refused(await ddel(c,randomUUID(),1),'42501','sync denied: SCOPE');
  // Another project's token is refused by identity; an expired session never reaches the function.
  refused(await ddel(a,p.id,1,randomUUID(),token(a,{iss:'https://otro-proyecto.supabase.co/auth/v1'})),'42501','sync denied: IDENTITY');
  const expired=await ddel(a,p.id,1,randomUUID(),token(a,{exp:Math.floor(Date.now()/1000)-3600}));
  assert.ok([401,403].includes(expired.status),expired.raw);
  assert.equal(snapshot(p.id,h.id),before);
  // The owner loses the workspace (soft-deleted): refused, nothing changes. Restored: the SAME request applies.
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  const op=randomUUID();
  try {
    refused(await ddel(a,p.id,1,op),'42501','sync denied: SCOPE');
    refused(await dhead(a,p.id),'42501','sync denied: SCOPE');
    assert.equal(snapshot(p.id,h.id),before);
  } finally {
    sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`);
  }
  // The farm is archived: FARM_NOT_LIVE, nothing changes.
  sql(`UPDATE public.farms SET status='ARCHIVED' WHERE id=${q(f.north.id)};`);
  try {
    refused(await ddel(a,p.id,1,op),'42501','sync denied: FARM_NOT_LIVE');
    assert.equal(snapshot(p.id,h.id),before);
  } finally {
    sql(`UPDATE public.farms SET status='ACTIVE' WHERE id=${q(f.north.id)};`);
  }
  const done=ok(await ddel(a,p.id,1,op),'authorized owner');
  assert.equal(done.idempotent,false);
  // D5, as in sync_own_delivery_v3: an APPLIED operation replays its receipt even after a later revocation.
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  try {
    const replay=ok(await ddel(a,p.id,1,op),'replay after revocation');
    assert.deepEqual([replay.idempotent,replay.entity_id,replay.remote_version,replay.payload_version],[true,p.id,done.remote_version,3]);
    const afterRevocation=snapshot(other.id,h.id), receiptsBefore=receipts(p.id)+receipts(other.id);
    // The replay is a read of that exact receipt only: the same UUID with other data is RECEIPT_CONFLICT.
    refused(await ddel(a,other.id,1,op),'23505','sync conflict: RECEIPT_CONFLICT');
    refused(await ddel(a,p.id,2,op),'23505','sync conflict: RECEIPT_CONFLICT');
    // No new mutation after revocation: a new delete is SCOPE and nothing moves.
    refused(await ddel(a,other.id,1),'42501','sync denied: SCOPE');
    refused(await ddel(a,p.id,2),'42501','sync denied: SCOPE');
    // The replay needs a valid JWT of the SAME account: another account with that UUID, another issuer or an
    // expired session never see the receipt (receipts are keyed by account; identity is checked first).
    const stolen=refused(await ddel(c,p.id,1,op),'42501','sync denied: SCOPE');
    assert.ok(!JSON.stringify(stolen).includes(op) && !JSON.stringify(stolen).includes(String(h.id)));
    refused(await ddel(a,p.id,1,op,token(a,{iss:'https://otro-proyecto.supabase.co/auth/v1'})),'42501','sync denied: IDENTITY');
    const expiredReplay=await ddel(a,p.id,1,op,token(a,{exp:Math.floor(Date.now()/1000)-3600}));
    assert.ok([401,403].includes(expiredReplay.status),expiredReplay.raw);
    assert.ok(!expiredReplay.raw.includes(op));
    assert.equal(snapshot(other.id,h.id),afterRevocation);
    assert.equal(receipts(p.id)+receipts(other.id),receiptsBefore);
    assert.deepEqual(deliveryRow(other.id),{version:1,deleted:false});
  } finally {
    sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`);
  }
});

test('concurrency: two deletes at once apply once; a delete racing a v3 update leaves exactly one winner',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-26'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-26',netGrams:300}), keep=dv(h.id,{deliveryDate:'2026-10-26',netGrams:700});
  ok(await v3(a,p),'p'); ok(await v3(a,keep),'keep');
  const held=holder(a,`SELECT public.delete_own_delivery_v1(${q(randomUUID())},${q(p.id)},1);`);
  const [second]=await race(held,[()=>ddel(a,p.id,1)]);
  refused(second,'23505','sync conflict: DELETED');
  assert.equal(receipts(p.id),2);
  assert.equal(harvestRow(h.id).weight,700);
  // A delete and an update of the same version: the update waits for the delete and sees it.
  const r=dv(h.id,{deliveryDate:'2026-10-26',netGrams:100}); ok(await v3(a,r),'r');
  const del=holder(a,`SELECT public.delete_own_delivery_v1(${q(randomUUID())},${q(r.id)},1);`);
  const [update]=await race(del,[()=>v3(a,{...r,baseVersion:1,netGrams:150,parcels:[{...r.parcels[0],weightGrams:150}]})]);
  refused(update,'23505','sync conflict: DELETED');
  assert.deepEqual(deliveryRow(r.id),{version:2,deleted:true});
  assert.equal(harvestRow(h.id).weight,700);
});
