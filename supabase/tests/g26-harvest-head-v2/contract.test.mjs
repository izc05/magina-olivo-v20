// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-harvest-head-v2.sql (#808 R1-c, owner GO on #831).
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac, createHash } from 'node:crypto';

const work = process.env.G26_HH2_WORK;
const project = process.env.G26_HH2_PROJECT;
assert.match(project ?? '', /^mg26-hh2-[0-9]+-[0-9]+$/);
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

const head1=(actor,id,authToken)=>rpc('get_own_harvest_head_v1',actor,{p_harvest_id:id},authToken);
const head2=(actor,id,authToken)=>rpc('get_own_harvest_head_v2',actor,{p_harvest_id:id},authToken);

// Canonical jsonb text, computed here independently of PostgreSQL: keys by length then bytes, ', ' and ': '
// separators, arrays in their order. The fingerprints are sha256 of exactly this text.
const canon=v=>v===null?'null':Array.isArray(v)?`[${v.map(canon).join(', ')}]`:typeof v==='object'
  ?`{${Object.keys(v).sort((x,y)=>x.length-y.length||(x<y?-1:x>y?1:0)).map(k=>`${JSON.stringify(k)}: ${canon(v[k])}`).join(', ')}}`
  :JSON.stringify(v);
const sha=text=>createHash('sha256').update(text,'utf8').digest('hex');
// The client side: own fields are p_harvest minus baseVersion, with an AUTO_DAY's kilos null.
const ownOf=h=>{ const {baseVersion,...own}=h; return {...own,dayOrigin:own.dayOrigin==='AUTO_DAY'?'AUTO_DAY':'MANUAL',
  weightGrams:own.dayOrigin==='AUTO_DAY'?null:own.weightGrams}; };
const ownFingerprint=h=>sha(canon(ownOf(h)));
// The derived side of an AUTO_DAY, read from the server's own rows (what derive_auto_day produced).
const derivedFingerprint=id=>{
  const weight=Number(sql(`SELECT weight_grams FROM public.harvests WHERE id=${q(id)};`));
  const rows=sql(`SELECT coalesce(string_agg(parcel_id||'|'||coalesce(weight_grams::text,'null')||'|'||allocation_mode,';' ORDER BY parcel_id::text),'')
    FROM public.harvest_parcels WHERE harvest_id=${q(id)};`);
  const parcels=rows===''?[]:rows.split(';').map(r=>{ const [p,w,m]=r.split('|'); return {parcelId:p,weightGrams:w==='null'?null:Number(w),allocationMode:m}; });
  return sha(canon({weightGrams:weight,parcels}));
};
const V1_KEYS=['version','day_origin','deleted','weight_grams','head_operation_id','head_payload_version','head_payload_hash'];
/** v2 = v1 + fingerprints: every v1 field is identical, read under the same session. */
async function heads(actor,id){
  const one=ok(await head1(actor,id),'head v1'), two=ok(await head2(actor,id),'head v2');
  assert.deepEqual([two.contract_version,two.fingerprint_version,two.harvest_id],[2,1,id]);
  for(const key of V1_KEYS) assert.deepEqual(two[key],one[key],key);
  assert.match(two.own_fingerprint,/^[0-9a-f]{64}$/);
  assert.deepEqual(Object.keys(two).sort(),['contract_version','day_origin','deleted','derived_fingerprint','fingerprint_version',
    'harvest_id','head_operation_id','head_payload_hash','head_payload_version','own_fingerprint','version','weight_grams']);
  return two;
}
const harvestSnapshot=id=>sql(`SELECT concat_ws(E'\\n',(SELECT row_to_json(h)::text FROM public.harvests h WHERE h.id=${q(id)}),
  (SELECT coalesce(string_agg(row_to_json(hp)::text,';' ORDER BY hp.parcel_id),'') FROM public.harvest_parcels hp WHERE hp.harvest_id=${q(id)}),
  (SELECT count(*)::text FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)}));`);

const WRITERS=['public.sync_own_delivery(uuid,jsonb)','public.sync_own_delivery_v2(uuid,jsonb)','public.sync_own_delivery_v3(uuid,jsonb)',
  'public.get_own_delivery_v2(uuid)','public.get_own_delivery_head_v3(uuid)','public.get_own_delivery_receive_v1(uuid)',
  'magina_g2_private.sync_delivery(uuid,jsonb,integer)','magina_g2_private.sync_delivery_historical(uuid,jsonb)','public.check_sync_operations(uuid[])',
  'public.sync_own_harvest_v1(uuid,jsonb)','public.delete_own_harvest_v1(uuid,uuid,bigint)','public.get_own_harvest_head_v1(uuid)',
  'magina_g2_private.sync_harvest(uuid,jsonb)','magina_g2_private.delete_harvest(uuid,uuid,bigint)','magina_g2_private.derive_auto_day(uuid)',
  'magina_g2_private.deliveries_derive_harvest()','magina_g2_private.delivery_parcels_derive_harvest()',
  'public.delete_own_delivery_v1(uuid,uuid,bigint)','public.get_own_delivery_delete_head_v1(uuid)','magina_g2_private.delete_delivery(uuid,uuid,bigint)'];
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
    const email=`g26-hh2-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
      'g26-delivery-head-v3.sql','g26-harvest-writer-v1.sql','g26-delivery-delete-v1.sql'].map(n=>new URL(`../../proposals/${n}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.surfaceBefore=surface(); f.functionsBefore=functions();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-harvest-head-v2.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.surfaceAfter=surface(); f.functionsAfter=functions();
  for(let attempt=0;;attempt++){ if((await head2(actors.A,randomUUID())).status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose get_own_harvest_head_v2'); await new Promise(r=>setTimeout(r,250)); }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.act=await parcel(a,f.ws,f.north,'Activa'); f.second=await parcel(a,f.ws,f.north,'Segunda');
  f.cn=await campaign(a,f.ws,f.north,[f.act,f.second]);
});

test('additive only: v1 HEAD, writers, RLS, grants, columns, constraints and triggers unchanged; exactly one new function',()=>{
  assert.equal(f.surfaceAfter,f.surfaceBefore);
  assert.deepEqual(f.functionsAfter.filter(fn=>!f.functionsBefore.includes(fn)),['get_own_harvest_head_v2(uuid)']);
  assert.ok(f.functionsBefore.every(fn=>f.functionsAfter.includes(fn)));
  assert.equal(sql(`SELECT provolatile::text||'|'||prosecdef::text FROM pg_proc WHERE oid='public.get_own_harvest_head_v2(uuid)'::regprocedure;`),'s|true');
});

test('privileges: authenticated-only; anon refused; a null id is PAYLOAD',async()=>{
  assert.equal(sql(`SELECT has_function_privilege('anon','public.get_own_harvest_head_v2(uuid)','EXECUTE')||'|'||
    has_function_privilege('public','public.get_own_harvest_head_v2(uuid)','EXECUTE')||'|'||
    has_function_privilege('authenticated','public.get_own_harvest_head_v2(uuid)','EXECUTE');`),'false|false|true');
  const anon=await http('/rest/v1/rpc/get_own_harvest_head_v2',{body:{p_harvest_id:randomUUID()}});
  assert.ok([401,403].includes(anon.status),anon.raw);
  refused(await head2(actors.A,null),'22023','sync invalid: PAYLOAD');
});

test('MANUAL: own fingerprint covers the typed kilos and every own field, follows edits, and the HEAD equals v1',async()=>{
  const a=actors.A;
  const m=day({dayOrigin:'MANUAL',weightGrams:4242,harvestDate:'2026-10-14',destination:'Cooperativa Jaén',notes:'Ñora «vareo»',
    collectionMethod:'VAREO',workerCount:3,machineryText:'Vibrador'}), op=randomUUID();
  ok(await hv(a,m,op),'manual');
  const first=await heads(a,m.id);
  assert.deepEqual([first.version,first.deleted,first.day_origin,first.weight_grams,first.head_operation_id,first.head_payload_version],
    [1,false,'MANUAL',4242,op,1]);
  assert.equal(first.own_fingerprint,ownFingerprint(m));
  assert.equal(first.derived_fingerprint,null);
  // Deterministic: a second read, and a read after an unrelated write, return the same value; reading writes nothing.
  const before=harvestSnapshot(m.id);
  assert.deepEqual(await heads(a,m.id),first);
  assert.equal(harvestSnapshot(m.id),before);
  // An edit of an own field (and of the typed kilos) moves the version, the HEAD and the fingerprint, exactly.
  const edited={...m,notes:'Segunda pasada',weightGrams:5000,baseVersion:1}, op2=randomUUID();
  ok(await hv(a,edited,op2),'edit');
  const second=await heads(a,m.id);
  assert.deepEqual([second.version,second.head_operation_id,second.weight_grams],[2,op2,5000]);
  assert.equal(second.own_fingerprint,ownFingerprint(edited));
  assert.notEqual(second.own_fingerprint,first.own_fingerprint);
  // Pesadas of a MANUAL never touch its kilos nor its fingerprint (derived values are AUTO_DAY only).
  const p=dv(m.id,{netGrams:700}); ok(await v3(a,p),'pesada'); ok(await ddel(a,p.id,1),'delete pesada');
  const third=await heads(a,m.id);
  assert.deepEqual([third.version,third.weight_grams,third.own_fingerprint,third.derived_fingerprint],[2,5000,second.own_fingerprint,null]);
});

test('AUTO_DAY: own fingerprint ignores derived kilos; derived fingerprint follows Pesadas exactly; version and HEAD never move',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-15'}), op=randomUUID(); ok(await hv(a,h,op),'day');
  const empty=await heads(a,h.id);
  assert.deepEqual([empty.version,empty.day_origin,empty.weight_grams,empty.head_operation_id],[1,'AUTO_DAY',0,op]);
  assert.equal(empty.own_fingerprint,ownFingerprint(h));
  assert.equal(empty.derived_fingerprint,sha('{"parcels": [], "weightGrams": 0}'));
  const p1=dv(h.id,{deliveryDate:'2026-10-15',netGrams:1000,parcels:[share(f.act,600),share(f.second,400)]});
  const p2=dv(h.id,{deliveryDate:'2026-10-15',netGrams:500,parcels:[share(f.second,500,'UNALLOCATED')]});
  ok(await v3(a,p1),'p1'); ok(await v2(a,legacy(p2)),'p2 v2');
  const full=await heads(a,h.id);
  assert.deepEqual([full.version,full.weight_grams,full.head_operation_id,full.own_fingerprint],[1,1500,op,empty.own_fingerprint]);
  assert.equal(full.derived_fingerprint,derivedFingerprint(h.id));
  // Independent of the reading: the exact canonical text of the derived values.
  const [low,high]=[f.act.id,f.second.id].sort();
  const parcels={[f.act.id]:{weightGrams:600,allocationMode:'EXACT'},[f.second.id]:{weightGrams:null,allocationMode:'UNALLOCATED'}};
  assert.equal(full.derived_fingerprint,sha(canon({weightGrams:1500,parcels:[low,high].map(id=>({parcelId:id,...parcels[id]}))})));
  assert.notEqual(full.derived_fingerprint,empty.derived_fingerprint);
  // R1-a delete: derived values follow, own fingerprint, version and HEAD do not.
  ok(await ddel(a,p2.id,1),'delete p2');
  const after=await heads(a,h.id);
  assert.deepEqual([after.version,after.weight_grams,after.head_operation_id,after.own_fingerprint],[1,1000,op,empty.own_fingerprint]);
  assert.equal(after.derived_fingerprint,derivedFingerprint(h.id));
  assert.equal(after.derived_fingerprint,sha(canon({weightGrams:1000,parcels:[low,high].map(id=>({parcelId:id,
    weightGrams:id===f.act.id?600:400,allocationMode:'EXACT'}))})));
  // An own-field edit moves the version and the own fingerprint; the derived values stay.
  const edited={...h,notes:'Cuadrilla de tarde',workerCount:4,baseVersion:1}, op2=randomUUID(); ok(await hv(a,edited,op2),'edit');
  const own=await heads(a,h.id);
  assert.deepEqual([own.version,own.head_operation_id,own.derived_fingerprint],[2,op2,after.derived_fingerprint]);
  assert.equal(own.own_fingerprint,ownFingerprint(edited));
});

test('a deleted Jornada: the HEAD names the delete, deleted=true, fingerprints still readable and exact',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-16'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-16',netGrams:900}); ok(await v3(a,p),'p');
  ok(await ddel(a,p.id,1),'last pesada');
  const dop=randomUUID(); ok(await hdel(a,h.id,1,dop),'delete day');
  const gone=await heads(a,h.id);
  assert.deepEqual([gone.version,gone.deleted,gone.head_operation_id,gone.weight_grams],[2,true,dop,0]);
  assert.equal(gone.own_fingerprint,ownFingerprint(h));
  assert.equal(gone.derived_fingerprint,sha('{"parcels": [], "weightGrams": 0}'));
});

test('two Jornadas with the same content differ only by id; a v1-written Jornada and its v2 HEAD agree on every v1 field',async()=>{
  const a=actors.A;
  const x=day({dayOrigin:'MANUAL',weightGrams:100,harvestDate:'2026-10-17'}), y={...x,id:randomUUID()};
  ok(await hv(a,x),'x'); ok(await hv(a,y),'y');
  const hx=await heads(a,x.id), hy=await heads(a,y.id);
  assert.notEqual(hx.own_fingerprint,hy.own_fingerprint);
  assert.equal(hx.own_fingerprint,ownFingerprint(x)); assert.equal(hy.own_fingerprint,ownFingerprint(y));
});

test('identity and scope: refused exactly like v1, without leaking anything; nothing is read for a revoked owner',async()=>{
  const a=actors.A, c=actors.C, h=day({harvestDate:'2026-10-18'}); ok(await hv(a,h),'day');
  const same=async(make,code,message)=>{
    const one=refused(await make(head1),code,message), two=refused(await make(head2),code,message);
    assert.ok(!JSON.stringify(two).includes(h.id)); return [one,two];
  };
  await same(fn=>fn(c,h.id),'42501','sync denied: SCOPE');
  await same(fn=>fn(a,randomUUID()),'42501','sync denied: SCOPE');
  await same(fn=>fn(a,h.id,token(a,{iss:'https://otro-proyecto.supabase.co/auth/v1'})),'42501','sync denied: IDENTITY');
  const expired=await head2(a,h.id,token(a,{exp:Math.floor(Date.now()/1000)-3600}));
  assert.ok([401,403].includes(expired.status),expired.raw);
  assert.ok(!expired.raw.includes('fingerprint'));
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  try { await same(fn=>fn(a,h.id),'42501','sync denied: SCOPE'); }
  finally { sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`); }
  sql(`UPDATE public.farms SET status='ARCHIVED' WHERE id=${q(f.north.id)};`);
  try { await same(fn=>fn(a,h.id),'42501','sync denied: FARM_NOT_LIVE'); }
  finally { sql(`UPDATE public.farms SET status='ACTIVE' WHERE id=${q(f.north.id)};`); }
  // Restored: readable again, with the same fingerprint.
  assert.equal((await heads(a,h.id)).own_fingerprint,ownFingerprint(h));
});
