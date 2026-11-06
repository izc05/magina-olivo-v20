// Native disposable Supabase only. The runner validates its local project ID.
// Gate J (#808): server-side coherence of Jornada kilos, split and delete order (owner GO on #837).
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac, createHash } from 'node:crypto';

const work = process.env.G26_JC_WORK;
const project = process.env.G26_JC_PROJECT;
assert.match(project ?? '', /^mg26-jc-[0-9]+-[0-9]+$/);
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
    const email=`g26-jc-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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

// The derived rule of an AUTO_DAY (server, derive_auto_day), recomputed here from the live Pesadas: the same rule
// Android adopts in gate J (CR-010 amendment J-D1). EXACT with the sum only if every share is EXACT, else UNALLOCATED.
const derivedRule=harvestId=>{
  const rows=sql(`SELECT coalesce(string_agg(s.parcel_id||'|'||s.allocation_mode||'|'||coalesce(s.weight_grams::text,'null'),';'),'')
    FROM public.delivery_parcels s JOIN public.deliveries d ON d.id=s.delivery_id WHERE d.harvest_id=${q(harvestId)} AND d.deleted_at IS NULL;`);
  const by=new Map();
  for(const r of rows?rows.split(';'):[]) { const [p,m,w]=r.split('|'); (by.get(p)??by.set(p,[]).get(p)).push({m,w:w==='null'?null:Number(w)}); }
  return [...by.keys()].sort().map(p=>{ const s=by.get(p); return s.every(x=>x.m==='EXACT'&&x.w!==null)
    ?[p,'EXACT',s.reduce((t,x)=>t+x.w,0)]:[p,'UNALLOCATED',null]; });
};
const serverSplit=harvestId=>JSON.parse(sql(`SELECT coalesce(jsonb_agg(jsonb_build_array(parcel_id,allocation_mode,weight_grams) ORDER BY parcel_id::text),'[]')
  FROM public.harvest_parcels WHERE harvest_id=${q(harvestId)};`));

test('J1 MANUAL keeps its typed kilos and split through Pesada creates, v2/v3 edits, moves and deletes',async()=>{
  const a=actors.A, m=day({harvestDate:'2026-10-27',dayOrigin:'MANUAL',weightGrams:4242}); ok(await hv(a,m),'manual');
  // A typed split (as a legacy MANUAL may have): never touched by the derivation either.
  sql(`INSERT INTO public.harvest_parcels(id,workspace_id,harvest_id,parcel_id,parcel_name_at_harvest,weight_grams,allocation_mode)
       VALUES (gen_random_uuid(),${q(f.ws)},${q(m.id)},${q(f.act.id)},'Activa',4242,'EXACT');`);
  const before=sql(`SELECT weight_grams||'|'||version FROM public.harvests WHERE id=${q(m.id)};`), split=serverSplit(m.id);
  const p3=dv(m.id,{deliveryDate:'2026-10-27',netGrams:900}), p2=dv(m.id,{deliveryDate:'2026-10-27',netGrams:100});
  ok(await v3(a,p3),'v3 create'); ok(await v2(a,legacy(p2)),'v2 create');
  ok(await v2(a,{...legacy(p2),baseVersion:1,netGrams:150,parcels:[{...p2.parcels[0],weightGrams:150}]}),'v2 edit');
  ok(await ddel(a,p3.id,1),'delete v3');
  assert.equal(sql(`SELECT weight_grams||'|'||version FROM public.harvests WHERE id=${q(m.id)};`),before);
  assert.deepEqual(serverSplit(m.id),split);
  // Moving a Pesada out of the MANUAL into an AUTO_DAY leaves the MANUAL intact too.
  const auto=day({harvestDate:'2026-10-28'}); ok(await hv(a,auto),'auto');
  ok(await v2(a,{...legacy(p2),baseVersion:2,deliveryDate:'2026-10-28',harvestId:auto.id,netGrams:150,parcels:[{...p2.parcels[0],weightGrams:150}]}),'move');
  assert.equal(sql(`SELECT weight_grams||'|'||version FROM public.harvests WHERE id=${q(m.id)};`),before);
  assert.deepEqual(serverSplit(m.id),split);
  assert.equal(harvestRow(auto.id).weight,150);
});

test('J2 AUTO_DAY split equals the documented rule: EXACT sums, UNALLOCATED when any share is not exact, deletes follow',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-29'}); ok(await hv(a,h),'day');
  const p1=dv(h.id,{deliveryDate:'2026-10-29',netGrams:1000,parcels:[share(f.act,600),share(f.second,400)]});
  const p2=dv(h.id,{deliveryDate:'2026-10-29',netGrams:500,parcels:[share(f.act,500)]});
  const p3=dv(h.id,{deliveryDate:'2026-10-29',netGrams:300,parcels:[share(f.second,300,'UNALLOCATED')]});
  ok(await v3(a,p1),'p1'); ok(await v3(a,p2),'p2');
  assert.deepEqual(serverSplit(h.id),derivedRule(h.id));
  assert.deepEqual(serverSplit(h.id),[[f.act.id,'EXACT',1100],[f.second.id,'EXACT',400]].sort((x,y)=>x[0]<y[0]?-1:1));
  ok(await v2(a,legacy(p3)),'p3 unallocated');
  assert.deepEqual(serverSplit(h.id),derivedRule(h.id));
  assert.equal(serverSplit(h.id).find(r=>r[0]===f.second.id)[1],'UNALLOCATED');
  assert.equal(harvestRow(h.id).weight,1800);
  // Attributed (EXACT) vs not attributed: 1100 attributed, 700 not attributed (400+300 of the mixed Parcel).
  const attributed=serverSplit(h.id).filter(r=>r[1]==='EXACT').reduce((t,r)=>t+Number(r[2]),0);
  assert.deepEqual([attributed,harvestRow(h.id).weight-attributed],[1100,700]);
  ok(await ddel(a,p3.id,1),'delete p3');
  assert.deepEqual(serverSplit(h.id),derivedRule(h.id));
  assert.equal(serverSplit(h.id).find(r=>r[0]===f.second.id)[1],'EXACT');
});

test('J3 a Pesada without Parcels: refused by the writers (Android never sends one) and never turns a day farm-wide',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-30'}); ok(await hv(a,h),'day');
  const empty=dv(h.id,{deliveryDate:'2026-10-30',netGrams:700,parcels:[]});
  const v3r=await v3(a,empty), v2r=await v2(a,legacy(empty));
  assert.notEqual(v3r.status,200,v3r.raw); assert.notEqual(v2r.status,200,v2r.raw);
  assert.equal(sql(`SELECT count(*) FROM public.deliveries WHERE id=${q(empty.id)};`),'0');
  // A legacy row without Parcels (written outside these writers) adds kilos but no Parcel to the derived day.
  const withParcels=dv(h.id,{deliveryDate:'2026-10-30',netGrams:200,parcels:[share(f.act,200)]}); ok(await v3(a,withParcels),'p');
  sql(`INSERT INTO public.deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,recorded_by_user_id,harvest_id)
       VALUES (gen_random_uuid(),${q(f.ws)},${q(f.north.id)},${q(f.cn)},'2026-10-30','Coop',700,'MANUAL',${q(a.id)},${q(h.id)});`);
  assert.equal(harvestRow(h.id).weight,900);
  assert.deepEqual(serverSplit(h.id),[[f.act.id,'EXACT',200]]);
  assert.deepEqual(serverSplit(h.id),derivedRule(h.id));
});

test('J4 delete order: the Jornada delete before its last Pesada is HARVEST_HAS_DEPENDENTS and changes nothing; the SAME operation applies after',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-31'}), hop=randomUUID(); ok(await hv(a,h,hop),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-31',netGrams:400}); ok(await v3(a,p),'p');
  const dayOp=randomUUID(), before=harvestSnapshot(h.id);
  refused(await hdel(a,h.id,1,dayOp),'23505','sync conflict: HARVEST_HAS_DEPENDENTS');
  assert.equal(harvestSnapshot(h.id),before);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id=${q(dayOp)};`),'0');
  // The Pesada delete first (it never waits for the Jornada): kilos 0, day alive, HEAD on its own operation.
  ok(await ddel(a,p.id,1),'pesada delete');
  assert.deepEqual(harvestRow(h.id),{weight:0,version:1,origin:'AUTO_DAY',deleted:false});
  assert.equal(ok(await head2(a,h.id),'head').head_operation_id,hop);
  // Re-evaluation with the SAME operation UUID and bytes applies once; a replay is idempotent.
  const applied=ok(await hdel(a,h.id,1,dayOp),'reevaluated');
  assert.deepEqual([applied.idempotent,applied.remote_version],[false,2]);
  assert.equal(ok(await hdel(a,h.id,1,dayOp),'replay').idempotent,true);
  const gone=ok(await head2(a,h.id),'gone');
  assert.deepEqual([gone.deleted,gone.head_operation_id,gone.version],[true,dayOp,2]);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id=${q(dayOp)};`),'1');
});

test('J5 a stale Jornada delete after a Pesada changed the day is refused by CAS, never applied blindly',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-24'}); ok(await hv(a,h),'day');
  ok(await hv(a,{...h,notes:'cambio',baseVersion:1}),'edit day');
  refused(await hdel(a,h.id,1),'23505','sync conflict: STALE_VERSION');
  assert.equal(harvestRow(h.id).deleted,false);
  ok(await hdel(a,h.id,2),'current version');
  assert.equal(harvestRow(h.id).deleted,true);
});
