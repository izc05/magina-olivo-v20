// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-harvest-receive-v1.sql (#808 R2, owner GO on #832).
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac, createHash } from 'node:crypto';

const work = process.env.G26_HR_WORK;
const project = process.env.G26_HR_PROJECT;
assert.match(project ?? '', /^mg26-hr-[0-9]+-[0-9]+$/);
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

const manifest=(actor,ws,farmId,campaignId,from=null,to=null,authToken)=>rpc('get_own_harvest_manifest_v1',actor,
  {p_workspace_id:ws,p_farm_id:farmId,p_campaign_id:campaignId,p_from:from,p_to:to},authToken);
const bundle=(actor,id,authToken)=>rpc('get_own_harvest_bundle_v1',actor,{p_harvest_id:id},authToken);
/** A typed limit error: SQLSTATE 54000, never a truncated answer. */
const limited=(r,cause,detail)=>{ assert.ok(r.status>=400,r.raw.slice(0,200)); assert.equal(r.data.code,'54000',r.raw);
  assert.equal(r.data.message,`sync limit: ${cause}`,r.raw); assert.equal(r.data.details,detail,r.raw); assert.ok(!('harvests' in r.data)); };
// Server truth, read directly, to compare the manifest with (sorted by id text like the contract).
const truthHarvests=(campaignId,from=null,to=null)=>JSON.parse(sql(`SELECT coalesce(jsonb_agg(jsonb_build_object('id',h.id,'version',h.version,
  'deleted',h.deleted_at IS NOT NULL,'day_origin',h.day_origin,'harvest_date',to_char(h.harvest_date,'YYYY-MM-DD'),
  'recorded_by_self',h.recorded_by_user_id=${q(actors.A.id)}) ORDER BY h.id::text),'[]') FROM public.harvests h
  WHERE h.campaign_id=${q(campaignId)} ${from?`AND h.harvest_date>=${q(from)}`:''} ${to?`AND h.harvest_date<=${q(to)}`:''};`));
const truthDeliveries=campaignId=>JSON.parse(sql(`SELECT coalesce(jsonb_agg(jsonb_build_object('id',d.id,'harvest_id',d.harvest_id,
  'version',d.version,'deleted',d.deleted_at IS NOT NULL,'partition_date',to_char(coalesce(h.harvest_date,d.delivery_date),'YYYY-MM-DD'),
  'recorded_by_self',d.recorded_by_user_id=${q(actors.A.id)}) ORDER BY d.id::text),'[]')
  FROM public.deliveries d LEFT JOIN public.harvests h ON h.id=d.harvest_id WHERE d.campaign_id=${q(campaignId)};`));
const receiptOf=async(actor,op)=>ok(await check(actor,[op]),'check').confirmed_receipts.find(r=>r.operation_id===op);
const byId=(rows,id)=>rows.find(r=>(r.delivery?.id??r.id)===id);

const WRITERS=['public.sync_own_delivery(uuid,jsonb)','public.sync_own_delivery_v2(uuid,jsonb)','public.sync_own_delivery_v3(uuid,jsonb)',
  'public.get_own_delivery_v2(uuid)','public.get_own_delivery_head_v3(uuid)','public.get_own_delivery_receive_v1(uuid)',
  'magina_g2_private.sync_delivery(uuid,jsonb,integer)','magina_g2_private.sync_delivery_historical(uuid,jsonb)','public.check_sync_operations(uuid[])',
  'public.sync_own_harvest_v1(uuid,jsonb)','public.delete_own_harvest_v1(uuid,uuid,bigint)','public.get_own_harvest_head_v1(uuid)',
  'magina_g2_private.sync_harvest(uuid,jsonb)','magina_g2_private.delete_harvest(uuid,uuid,bigint)','magina_g2_private.derive_auto_day(uuid)',
  'magina_g2_private.deliveries_derive_harvest()','magina_g2_private.delivery_parcels_derive_harvest()',
  'public.delete_own_delivery_v1(uuid,uuid,bigint)','public.get_own_delivery_delete_head_v1(uuid)','magina_g2_private.delete_delivery(uuid,uuid,bigint)',
  'public.get_own_harvest_head_v2(uuid)','public.has_farm_capability(uuid,text)'];
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
    const email=`g26-hr-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
      'g26-delivery-head-v3.sql','g26-harvest-writer-v1.sql','g26-delivery-delete-v1.sql','g26-harvest-head-v2.sql']
      .map(n=>new URL(`../../proposals/${n}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.surfaceBefore=surface(); f.functionsBefore=functions();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-harvest-receive-v1.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.surfaceAfter=surface(); f.functionsAfter=functions();
  for(let attempt=0;;attempt++){ if((await bundle(actors.A,randomUUID())).status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose get_own_harvest_bundle_v1'); await new Promise(r=>setTimeout(r,250)); }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.act=await parcel(a,f.ws,f.north,'Activa'); f.second=await parcel(a,f.ws,f.north,'Segunda');
  f.cn=await campaign(a,f.ws,f.north,[f.act,f.second]);
  f.big=await campaign(a,f.ws,f.north,[f.act]);
  f.many=await campaign(a,f.ws,f.north,[f.act]);

  // S1 fixture: 3 Jornadas (2 AUTO_DAY, 1 MANUAL) + one deleted, 7 Pesadas v2/v3 with an edit, an R1-a delete and an
  // out-of-protocol write, plus one Pesada without a Jornada.
  const at=(date,o={})=>day({harvestDate:date,...o});
  f.A=at('2026-10-14'); f.B=at('2026-10-15'); f.M=at('2026-10-16',{dayOrigin:'MANUAL',weightGrams:4242}); f.D=at('2026-10-17');
  f.ops={};
  for(const h of [f.A,f.B,f.M,f.D]) { const op=randomUUID(); ok(await hv(a,h,op),'day'); f.ops[h.id]=op; }
  const pd=(h,net,o={})=>dv(h.id,{deliveryDate:h.harvestDate,netGrams:net,...o});
  f.p1=pd(f.A,1000,{parcels:[share(f.act,600),share(f.second,400)]}); f.p2=pd(f.A,500); f.p3=pd(f.A,300);
  f.p4=pd(f.B,700); f.p5=pd(f.B,200); f.p6=pd(f.M,999); f.p7=pd(f.M,1);
  f.loose=dv(null,{deliveryDate:'2026-10-18',netGrams:50});
  for(const [p,write] of [[f.p1,v3],[f.p2,(x,d,op)=>v2(x,legacy(d),op)],[f.p3,v3],[f.p4,v3],[f.p5,(x,d,op)=>v2(x,legacy(d),op)],[f.p6,v3],
    [f.p7,(x,d,op)=>v2(x,legacy(d),op)],[f.loose,(x,d,op)=>v2(x,legacy(d),op)]]) { const op=randomUUID(); ok(await write(a,p,op),'pesada'); f.ops[p.id]=op; }
  f.ops.p4edit=randomUUID(); ok(await v3(a,{...f.p4,baseVersion:1,netGrams:750,parcels:[{...f.p4.parcels[0],weightGrams:750}]},f.ops.p4edit),'edit p4');
  f.ops.p3del=randomUUID(); ok(await ddel(a,f.p3.id,1,f.ops.p3del),'delete p3');
  sql(`UPDATE public.deliveries SET notes='fuera de protocolo' WHERE id=${q(f.p5.id)};`);
  f.ops.Ddel=randomUUID(); ok(await hdel(a,f.D.id,1,f.ops.Ddel),'delete D');
});

test('additive only: every earlier contract unchanged; exactly two new functions',()=>{
  assert.equal(f.surfaceAfter,f.surfaceBefore);
  assert.deepEqual(f.functionsAfter.filter(fn=>!f.functionsBefore.includes(fn)).sort(),
    ['get_own_harvest_bundle_v1(uuid)','get_own_harvest_manifest_v1(uuid,uuid,uuid,date,date)']);
  assert.ok(f.functionsBefore.every(fn=>f.functionsAfter.includes(fn)));
  for(const fn of ['public.get_own_harvest_manifest_v1(uuid,uuid,uuid,date,date)','public.get_own_harvest_bundle_v1(uuid)'])
    assert.equal(sql(`SELECT provolatile::text||'|'||prosecdef::text FROM pg_proc WHERE oid=${q(fn)}::regprocedure;`),'s|true',fn);
});

test('privileges: authenticated-only; anon refused; null ids and an inverted range are PAYLOAD',async()=>{
  for(const fn of ['public.get_own_harvest_manifest_v1(uuid,uuid,uuid,date,date)','public.get_own_harvest_bundle_v1(uuid)'])
    assert.equal(sql(`SELECT has_function_privilege('anon',${q(fn)},'EXECUTE')||'|'||has_function_privilege('public',${q(fn)},'EXECUTE')||'|'||
      has_function_privilege('authenticated',${q(fn)},'EXECUTE');`),'false|false|true',fn);
  for(const path of ['get_own_harvest_manifest_v1','get_own_harvest_bundle_v1']) {
    const anon=await http(`/rest/v1/rpc/${path}`,{body:path.includes('bundle')?{p_harvest_id:f.A.id}:{p_workspace_id:f.ws,p_farm_id:f.north.id,p_campaign_id:f.cn}});
    assert.ok([401,403].includes(anon.status),anon.raw); assert.ok(!anon.raw.includes(f.A.id));
  }
  const a=actors.A;
  refused(await manifest(a,f.ws,f.north.id,null),'22023','sync invalid: PAYLOAD');
  refused(await manifest(a,null,f.north.id,f.cn),'22023','sync invalid: PAYLOAD');
  refused(await manifest(a,f.ws,f.north.id,f.cn,'2026-10-16','2026-10-15'),'22023','sync invalid: PAYLOAD');
  refused(await bundle(a,null),'22023','sync invalid: PAYLOAD');
});

test('S1 manifest: Jornadas and Pesadas with exact ids, versions, tombstones, membership and recorder, live and deleted',async()=>{
  const m=ok(await manifest(actors.A,f.ws,f.north.id,f.cn),'manifest');
  assert.deepEqual([m.manifest_contract_version,m.issuer,m.account_id,m.workspace_id,m.farm_id,m.campaign_id,m.from,m.to],
    [1,ISSUER,actors.A.id,f.ws,f.north.id,f.cn,null,null]);
  assert.deepEqual(m.harvests,truthHarvests(f.cn));
  assert.deepEqual(m.deliveries,truthDeliveries(f.cn));
  const h=id=>byId(m.harvests,id), d=id=>byId(m.deliveries,id);
  assert.deepEqual([h(f.A.id).day_origin,h(f.B.id).day_origin,h(f.M.id).day_origin],['AUTO_DAY','AUTO_DAY','MANUAL']);
  assert.deepEqual([h(f.D.id).deleted,h(f.D.id).version,h(f.A.id).deleted,h(f.A.id).version],[true,2,false,1]);
  assert.equal(m.harvests.length,4); assert.equal(m.deliveries.length,8);
  assert.deepEqual([d(f.p3.id).deleted,d(f.p3.id).version],[true,2]);       // R1-a delete
  assert.deepEqual([d(f.p4.id).deleted,d(f.p4.id).version],[false,2]);      // v3 edit
  assert.deepEqual([d(f.p5.id).version],[2]);                               // written outside the protocol
  assert.deepEqual([d(f.p6.id).harvest_id,d(f.loose.id).harvest_id,d(f.loose.id).partition_date],[f.M.id,null,'2026-10-18']);
  assert.ok(m.deliveries.every(x=>x.recorded_by_self===true) && m.harvests.every(x=>x.recorded_by_self===true));
  // Deterministic and read-only: the same answer twice, and nothing written.
  const before=snapshot(f.p1.id,f.A.id);
  assert.deepEqual(ok(await manifest(actors.A,f.ws,f.north.id,f.cn),'again'),m);
  assert.equal(snapshot(f.p1.id,f.A.id),before);
});

test('S3 partition by dates: disjoint ranges give disjoint manifests whose union is exactly the full one',async()=>{
  const a=actors.A, full=ok(await manifest(a,f.ws,f.north.id,f.cn),'full');
  const ranges=[['2026-10-14','2026-10-15'],['2026-10-16','2026-10-17'],['2026-10-18',null]];
  const parts=[];
  for(const [from,to] of ranges) {
    const part=ok(await manifest(a,f.ws,f.north.id,f.cn,from,to),`${from}..${to}`);
    assert.deepEqual([part.from,part.to],[from,to]);
    assert.deepEqual(part.harvests,truthHarvests(f.cn,from,to));
    for(const x of part.harvests) assert.ok(x.harvest_date>=from && (!to || x.harvest_date<=to));
    for(const x of part.deliveries) assert.ok(x.partition_date>=from && (!to || x.partition_date<=to));
    parts.push(part);
  }
  const ids=key=>parts.flatMap(p=>p[key].map(x=>x.id));
  assert.equal(new Set(ids('harvests')).size,ids('harvests').length); assert.equal(new Set(ids('deliveries')).size,ids('deliveries').length);
  const sortById=rows=>[...rows].sort((x,y)=>x.id<y.id?-1:1);
  assert.deepEqual(sortById(parts.flatMap(p=>p.harvests)),full.harvests);
  assert.deepEqual(sortById(parts.flatMap(p=>p.deliveries)),full.deliveries);
  // A Pesada follows the date of its Jornada, so it is never split from it.
  assert.ok(parts[0].deliveries.some(x=>x.id===f.p1.id) && parts[0].harvests.some(x=>x.id===f.A.id));
  // An empty range is an empty manifest, not an error.
  const none=ok(await manifest(a,f.ws,f.north.id,f.cn,'2025-01-01','2025-01-02'),'empty');
  assert.deepEqual([none.harvests,none.deliveries],[[],[]]);
});

test('S3 limits: over 2000 Jornadas, 20000 Pesadas or 500 Pesadas per bundle is 54000, never truncated; partitions recover all',async()=>{
  const a=actors.A, rec=q(a.id);
  // 2001 MANUAL Jornadas in their own campaign, one per day from 2025-10-01 (MANUAL: no AUTO_DAY uniqueness).
  sql(`INSERT INTO public.harvests(id,workspace_id,farm_id,campaign_id,harvest_date,weight_grams,day_origin,recorded_by_user_id,version)
       SELECT gen_random_uuid(),${q(f.ws)},${q(f.north.id)},${q(f.big)},date '2025-10-01'+g,1,'MANUAL',${rec},1 FROM generate_series(0,2000) g;`);
  limited(await manifest(a,f.ws,f.north.id,f.big),'MANIFEST_HARVESTS','harvests=2001');
  const split=sql(`SELECT to_char(date '2025-10-01'+1000,'YYYY-MM-DD')||'|'||to_char(date '2025-10-01'+1001,'YYYY-MM-DD');`).split('|');
  const low=ok(await manifest(a,f.ws,f.north.id,f.big,null,split[0]),'low'), high=ok(await manifest(a,f.ws,f.north.id,f.big,split[1],null),'high');
  assert.deepEqual([low.harvests.length,high.harvests.length],[1001,1000]);
  assert.deepEqual([...low.harvests,...high.harvests].map(x=>x.id).sort(),truthHarvests(f.big).map(x=>x.id).sort());
  // 20001 Pesadas without a Jornada in another campaign.
  sql(`INSERT INTO public.deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,recorded_by_user_id)
       SELECT gen_random_uuid(),${q(f.ws)},${q(f.north.id)},${q(f.many)},date '2025-10-01'+(g % 300),'Coop',1,'MANUAL',${rec}
       FROM generate_series(1,20001) g;`);
  limited(await manifest(a,f.ws,f.north.id,f.many),'MANIFEST_DELIVERIES','deliveries=20001');
  const parts=[], cuts=[[null,'2026-03-15'],['2026-03-16',null]];
  for(const [from,to] of cuts) parts.push(ok(await manifest(a,f.ws,f.north.id,f.many,from,to),`${from}..${to}`));
  assert.equal(parts[0].deliveries.length+parts[1].deliveries.length,20001);
  assert.equal(new Set([...parts[0].deliveries,...parts[1].deliveries].map(x=>x.id)).size,20001);
  // 501 Pesadas in one Jornada: the bundle refuses; 500 would be accepted.
  const big=at501(); ok(await hv(a,big),'big day');
  sql(`INSERT INTO public.deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,recorded_by_user_id,harvest_id)
       SELECT gen_random_uuid(),${q(f.ws)},${q(f.north.id)},${q(f.cn)},date '2026-10-20','Coop',1,'MANUAL',${rec},${q(big.id)}
       FROM generate_series(1,501) g;`);
  limited(await bundle(a,big.id),'BUNDLE_DELIVERIES','deliveries=501');
  // Hard deletes are forbidden by the G0 trigger: one Pesada is moved out of the Jornada instead.
  sql(`UPDATE public.deliveries SET harvest_id=NULL WHERE id=(SELECT id FROM public.deliveries WHERE harvest_id=${q(big.id)} LIMIT 1);`);
  assert.equal(ok(await bundle(a,big.id),'500').deliveries.length,500);
});
// A writer outside PostgREST (database owner): one open transaction that commits after a pause.
function rawHolder(statements,seconds){
  const tag=`g808r_${randomUUID().replaceAll('-','')}`;
  const child=spawn('docker',psqlArgs,{stdio:['pipe','pipe','pipe']});
  let out='',err=''; child.stdout.on('data',d=>out+=d); child.stderr.on('data',d=>err+=d);
  child.stdin.end(`SET application_name=${q(tag)};
BEGIN;
${statements}
SELECT pg_sleep(${seconds});
COMMIT;
SELECT 'HOLDER_COMMITTED';`);
  return {tag,done:new Promise(resolve=>child.on('close',code=>resolve({code,out,err})))};
}
const at501=()=>day({harvestDate:'2026-10-20',dayOrigin:'MANUAL',weightGrams:1});

test('S4 bundle: one Jornada with all its Pesadas, R1-c HEAD v2, strict receipts per Pesada and verifiable provenance',async()=>{
  const a=actors.A, b=ok(await bundle(a,f.A.id),'bundle A');
  assert.deepEqual([b.bundle_contract_version,b.issuer,b.account_id],[1,ISSUER,a.id]);
  assert.deepEqual(b.harvest,JSON.parse(sql(`SELECT to_jsonb(h) FROM public.harvests h WHERE h.id=${q(f.A.id)};`)));
  // R1-c embedded, identical to a direct read, with the client-recomputable fingerprints.
  assert.deepEqual(b.harvest_head,await heads(a,f.A.id));
  assert.equal(b.harvest_head.head_operation_id,f.ops[f.A.id]);
  assert.equal(b.harvest_head.own_fingerprint,ownFingerprint(f.A));
  assert.equal(b.harvest_head.derived_fingerprint,derivedFingerprint(f.A.id));
  // Server kilos = live Pesadas only (p3 deleted); its split for comparison.
  assert.equal(b.server_weight_grams,1500);
  assert.deepEqual(b.server_harvest_parcels,JSON.parse(sql(`SELECT coalesce(jsonb_agg(jsonb_build_object('parcel_id',parcel_id,'weight_grams',weight_grams,
    'allocation_mode',allocation_mode) ORDER BY parcel_id::text),'[]') FROM public.harvest_parcels WHERE harvest_id=${q(f.A.id)};`)));
  // All Pesadas, live and deleted, ordered by id, each with its full row and parcels.
  assert.deepEqual(b.deliveries.map(x=>x.delivery.id),[f.p1.id,f.p2.id,f.p3.id].sort());
  for(const entry of b.deliveries) {
    const id=entry.delivery.id;
    assert.deepEqual(entry.delivery,JSON.parse(sql(`SELECT to_jsonb(d) FROM public.deliveries d WHERE d.id=${q(id)};`)));
    assert.deepEqual(entry.parcels,JSON.parse(sql(`SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY p.parcel_id::text,p.id::text),'[]')
      FROM public.delivery_parcels p WHERE p.delivery_id=${q(id)};`)));
    assert.equal(entry.origin_created_at_exact,sql(`SELECT origin_created_at_exact FROM magina_g2_private.fact_origins WHERE entity_id=${q(id)};`)||null);
    assert.equal(entry.recorded_by_self,true);
  }
  // Heads: the ONE applied receipt at the current version, equal to check_sync_operations.
  const p1=byId(b.deliveries,f.p1.id), r1=await receiptOf(a,f.ops[f.p1.id]);
  assert.deepEqual(p1.head,{operation_id:f.ops[f.p1.id],version:1,payload_version:3,payload_hash:r1.payload_hash,kind:'WRITE'});
  assert.equal(p1.origin_created_at_exact!==null,true);
  const p2=byId(b.deliveries,f.p2.id), r2=await receiptOf(a,f.ops[f.p2.id]);
  assert.deepEqual(p2.head,{operation_id:f.ops[f.p2.id],version:1,payload_version:2,payload_hash:r2.payload_hash,kind:'WRITE'});
  const p3=byId(b.deliveries,f.p3.id);
  assert.notEqual(p3.delivery.deleted_at,null);
  assert.deepEqual(p3.head,{operation_id:f.ops.p3del,version:2,payload_version:3,payload_hash:deleteHash(f.p3.id,1),kind:'DELETE'});
  // The MANUAL bundle: typed kilos, no derived fingerprint, its Pesadas listed.
  const m=ok(await bundle(a,f.M.id),'bundle M');
  assert.deepEqual([m.server_weight_grams,m.harvest_head.derived_fingerprint,m.harvest_head.own_fingerprint],[4242,null,ownFingerprint(f.M)]);
  assert.deepEqual(m.deliveries.map(x=>x.delivery.id),[f.p6.id,f.p7.id].sort());
  // The deleted Jornada: still readable, HEAD on its delete, no Pesadas.
  const d=ok(await bundle(a,f.D.id),'bundle D');
  assert.deepEqual([d.harvest_head.deleted,d.harvest_head.head_operation_id,d.deliveries,d.server_weight_grams],[true,f.ops.Ddel,[],0]);
});

test('S6 and receipts: an edit heads on its own operation; an out-of-protocol write or no receipt gives head null, never a guess',async()=>{
  const a=actors.A, b=ok(await bundle(a,f.B.id),'bundle B');
  const p4=byId(b.deliveries,f.p4.id), r4=await receiptOf(a,f.ops.p4edit);
  assert.deepEqual(p4.head,{operation_id:f.ops.p4edit,version:2,payload_version:3,payload_hash:r4.payload_hash,kind:'WRITE'});
  const p5=byId(b.deliveries,f.p5.id);
  assert.deepEqual([p5.delivery.version,p5.head],[2,null]);
  // Its earlier receipt still exists, but at version 1: not the head.
  assert.equal((await receiptOf(a,f.ops[f.p5.id])).remote_version,1);
  // The derived kilos follow the live Pesadas of B (750 + 200).
  assert.equal(b.server_weight_grams,950);
});

test('S9 a Pesada of another recorder is flagged, has no head and grants nothing',async()=>{
  const a=actors.A, c=actors.C;
  const foreign=randomUUID();
  sql(`INSERT INTO public.deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,recorded_by_user_id,harvest_id)
       VALUES (${q(foreign)},${q(f.ws)},${q(f.north.id)},${q(f.cn)},'2026-10-15','Coop',10,'MANUAL',${q(c.id)},${q(f.B.id)});`);
  const m=ok(await manifest(a,f.ws,f.north.id,f.cn),'manifest');
  assert.deepEqual(byId(m.deliveries,foreign),{id:foreign,harvest_id:f.B.id,version:1,deleted:false,partition_date:'2026-10-15',recorded_by_self:false});
  const entry=byId(ok(await bundle(a,f.B.id),'bundle').deliveries,foreign);
  assert.deepEqual([entry.recorded_by_self,entry.head,entry.origin_created_at_exact],[false,null,null]);
  // The recorder itself gains no access through this read.
  refused(await manifest(c,f.ws,f.north.id,f.cn),'42501','sync denied: SCOPE');
  refused(await bundle(c,f.B.id),'42501','sync denied: SCOPE');
  sql(`UPDATE public.deliveries SET deleted_at=now() WHERE id=${q(foreign)};`);
});

test('S2 access denied: other account, member, foreign or mismatched ids, issuer, expiry, revoked owner, archived farm, deleted campaign',async()=>{
  const a=actors.A, c=actors.C;
  const deny=async(r,message='sync denied: SCOPE')=>{ const body=refused(r,'42501',message);
    for(const secret of [f.A.id,f.ws,f.north.id,f.cn,'1500','harvests']) assert.ok(!JSON.stringify(body).includes(secret),secret); };
  // Another account, and the same account as an EDITOR member: owner-only v1.
  await deny(await manifest(c,f.ws,f.north.id,f.cn)); await deny(await bundle(c,f.A.id));
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions) VALUES (${q(f.ws)},${q(c.id)},'EDITOR',ARRAY['read','write']);`);
  try { await deny(await manifest(c,f.ws,f.north.id,f.cn)); await deny(await bundle(c,f.A.id)); }
  finally { sql(`DELETE FROM public.workspace_memberships WHERE workspace_id=${q(f.ws)} AND user_id=${q(c.id)};`); }
  // Unknown ids look exactly like foreign ones; ids that do not belong together are refused.
  await deny(await bundle(a,randomUUID()));
  await deny(await manifest(a,randomUUID(),f.north.id,f.cn));
  const cws=await workspace(c), cfarm=await farm(c,cws,'Finca C'), cpar=await parcel(c,cws,cfarm,'Parcela C'), ccamp=await campaign(c,cws,cfarm,[cpar]);
  await deny(await manifest(a,f.ws,cfarm.id,f.cn)); await deny(await manifest(a,f.ws,f.north.id,ccamp));
  await deny(await manifest(c,cws,cfarm.id,f.cn)); await deny(await manifest(c,cws,f.north.id,ccamp));
  assert.equal(ok(await manifest(c,cws,cfarm.id,ccamp),'C own').harvests.length,0);
  // Identity: another issuer, an expired session.
  await deny(await manifest(a,f.ws,f.north.id,f.cn,null,null,token(a,{iss:'https://otro-proyecto.supabase.co/auth/v1'})),'sync denied: IDENTITY');
  await deny(await bundle(a,f.A.id,token(a,{iss:'https://otro-proyecto.supabase.co/auth/v1'})),'sync denied: IDENTITY');
  for(const r of [await manifest(a,f.ws,f.north.id,f.cn,null,null,token(a,{exp:Math.floor(Date.now()/1000)-3600})),
    await bundle(a,f.A.id,token(a,{exp:Math.floor(Date.now()/1000)-3600}))]) { assert.ok([401,403].includes(r.status),r.raw); assert.ok(!r.raw.includes(f.A.id)); }
  // The owner loses the workspace; the farm is archived; the campaign is soft-deleted. Restored: readable again.
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  try { await deny(await manifest(a,f.ws,f.north.id,f.cn)); await deny(await bundle(a,f.A.id)); }
  finally { sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`); }
  sql(`UPDATE public.farms SET status='ARCHIVED' WHERE id=${q(f.north.id)};`);
  try { await deny(await manifest(a,f.ws,f.north.id,f.cn),'sync denied: FARM_NOT_LIVE'); await deny(await bundle(a,f.A.id),'sync denied: FARM_NOT_LIVE'); }
  finally { sql(`UPDATE public.farms SET status='ACTIVE' WHERE id=${q(f.north.id)};`); }
  sql(`UPDATE public.campaigns SET deleted_at=now() WHERE id=${q(f.cn)};`);
  try { await deny(await manifest(a,f.ws,f.north.id,f.cn)); await deny(await bundle(a,f.A.id)); }
  finally { sql(`UPDATE public.campaigns SET deleted_at=NULL WHERE id=${q(f.cn)};`); }
  assert.equal(ok(await bundle(a,f.A.id),'restored').harvest.id,f.A.id);
});

test('S5 one snapshot: reads never block on writers, see only committed state, and every bundle is internally coherent',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-19'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-19',netGrams:100}), q2=dv(h.id,{deliveryDate:'2026-10-19',netGrams:900});
  ok(await v3(a,p),'p'); ok(await v3(a,q2),'q');
  // An open transaction rewrites p (uncommitted): the bundle answers at once with the committed state.
  const held=rawHolder(`UPDATE public.deliveries SET net_grams=5000 WHERE id=${q(p.id)};
UPDATE public.delivery_parcels SET weight_grams=5000 WHERE delivery_id=${q(p.id)};`,4);
  await until(()=>sql(`SELECT count(*) FROM pg_stat_activity WHERE application_name=${q(held.tag)} AND state='active' AND query LIKE 'SELECT pg_sleep%';`)==='1','holder');
  const started=Date.now(), during=ok(await bundle(a,h.id),'during');
  assert.ok(Date.now()-started<3000,'the read did not wait for the writer');
  assert.deepEqual([during.server_weight_grams,byId(during.deliveries,p.id).delivery.net_grams],[1000,100]);
  const done=await held.done; assert.equal(done.code,0,done.err);
  const after=ok(await bundle(a,h.id),'after');
  assert.deepEqual([after.server_weight_grams,byId(after.deliveries,p.id).delivery.net_grams],[5900,5000]);
  // Writers and readers interleaved: every bundle's server kilos equal its own live Pesadas, and its split matches.
  let version=byId(after.deliveries,q2.id).delivery.version, net=900;
  const writes=(async()=>{ for(let i=0;i<12;i++){ net+=10;
    ok(await v3(a,{...q2,baseVersion:version,netGrams:net,parcels:[{...q2.parcels[0],weightGrams:net}]}),'write'); version++; } })();
  const reads=Array.from({length:24},(_,i)=>new Promise(r=>setTimeout(r,i*15)).then(()=>bundle(a,h.id)));
  const results=await Promise.all(reads); await writes;
  for(const r of results) {
    const b=ok(r,'read'), live=b.deliveries.filter(x=>x.delivery.deleted_at===null);
    assert.equal(b.server_weight_grams,live.reduce((s,x)=>s+Number(x.delivery.net_grams),0));
    assert.equal(b.server_harvest_parcels.reduce((s,x)=>s+Number(x.weight_grams??0),0),b.server_weight_grams);
    assert.equal(b.harvest_head.version,b.harvest.version);
    for(const x of live) if(x.head) assert.equal(x.head.version,x.delivery.version);
  }
});
