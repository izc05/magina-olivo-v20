// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-harvest-writer-v1.sql (#808 Jornadas gate, owner GO B: H2, H3a, H4).
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const work = process.env.G26_HV_WORK;
const project = process.env.G26_HV_PROJECT;
assert.match(project ?? '', /^mg26-hv-[0-9]+-[0-9]+$/);
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
const harvestRow=id=>{ const [w,v,o,del]=sql(`SELECT weight_grams||'|'||version||'|'||coalesce(day_origin,'-')||'|'||(deleted_at IS NOT NULL)
  FROM public.harvests WHERE id=${q(id)};`).split('|'); return {weight:Number(w),version:Number(v),origin:o,deleted:del==='t'}; };
const parcelsOf=id=>sql(`SELECT coalesce(string_agg(parcel_id||':'||allocation_mode||':'||coalesce(weight_grams::text,'null'),',' ORDER BY parcel_id),'')
  FROM public.harvest_parcels WHERE harvest_id=${q(id)};`);
const expectParcels=(id,rows)=>assert.equal(parcelsOf(id),rows.map(([p,m,w])=>`${p.id}:${m}:${w}`).sort().join(','));

// Two sessions: a psql holder keeps a transaction open while an HTTP call runs into its locks.
function holder(actor,statements,seconds=4){
  const tag=`g808h_${randomUUID().replaceAll('-','')}`;
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

const WRITERS=['public.sync_own_delivery(uuid,jsonb)','public.sync_own_delivery_v2(uuid,jsonb)','public.sync_own_delivery_v3(uuid,jsonb)',
  'public.get_own_delivery_v2(uuid)','public.get_own_delivery_head_v3(uuid)','public.get_own_delivery_receive_v1(uuid)',
  'magina_g2_private.sync_delivery(uuid,jsonb,integer)','magina_g2_private.sync_delivery_historical(uuid,jsonb)','public.check_sync_operations(uuid[])'];
const surface=()=>sql(`SELECT concat_ws(E'\\n',
  (SELECT string_agg(p.oid::regprocedure::text||'='||md5(p.prosrc||'|'||coalesce(array_to_string(p.proconfig,','),'')||'|'||coalesce(p.proacl::text,'')),';'
     ORDER BY p.oid::regprocedure::text) FROM pg_proc p WHERE p.oid IN (${WRITERS.map(fn=>`${q(fn)}::regprocedure`).join(',')})),
  (SELECT string_agg(tablename||'.'||policyname||':'||cmd||':'||coalesce(qual,'-')||':'||coalesce(with_check,'-'),';' ORDER BY tablename,policyname) FROM pg_policies WHERE schemaname='public'),
  (SELECT string_agg(table_name||':'||grantee||':'||privilege_type,';' ORDER BY table_name,grantee,privilege_type)
   FROM information_schema.role_table_grants WHERE grantee IN ('anon','authenticated','PUBLIC')),
  (SELECT string_agg(table_name||'.'||column_name||':'||data_type||':'||is_nullable,';' ORDER BY table_name,ordinal_position)
   FROM information_schema.columns WHERE table_schema='public'));`);
const triggers=()=>sql(`SELECT string_agg('public.'||c.relname||'.'||t.tgname,';' ORDER BY c.relname,t.tgname) FROM pg_trigger t
  JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE NOT t.tgisinternal AND n.nspname='public';`);

before(async()=>{
  for(const label of ['A','C']) {
    const email=`g26-hv-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql','g26-parcel-mutation-v1.sql','g26-historical-writers-v1.sql','g26-delivery-head-v3.sql']
      .map(n=>new URL(`../../proposals/${n}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.surfaceBefore=surface(); f.triggersBefore=triggers();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-harvest-writer-v1.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.surfaceAfter=surface(); f.triggersAfter=triggers();
  for(let attempt=0;;attempt++){ if((await hhead(actors.A,randomUUID())).status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose the harvest RPCs'); await new Promise(r=>setTimeout(r,250)); }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte'); f.south=await farm(a,f.ws,'Finca Sur');
  f.act=await parcel(a,f.ws,f.north,'Activa'); f.arch=await parcel(a,f.ws,f.north,'Archivada'); f.second=await parcel(a,f.ws,f.north,'Segunda');
  f.southParcel=await parcel(a,f.ws,f.south,'Sur');
  f.cn=await campaign(a,f.ws,f.north,[f.act,f.arch,f.second]);
  f.cn2=await campaign(a,f.ws,f.north,[f.act],'2026-09-01','2026-10-31');
  f.cs=await campaign(a,f.ws,f.south,[f.southParcel]);
  const archived=await rpc('archive_own_parcel_v1',a,{p_operation_id:randomUUID(),p_parcel_id:f.arch.id,p_expected_version:1,
    p_archive:{archivedAt:'2026-10-15T08:30:00Z',closeMembershipId:f.arch.membership}}); assert.equal(archived.status,200,archived.raw);
});

test('writers, reads, RLS, grants and columns are unchanged; only the two derivation triggers are added',()=>{
  assert.equal(f.surfaceAfter,f.surfaceBefore);
  assert.deepEqual(f.triggersAfter.split(';').filter(t=>!f.triggersBefore.split(';').includes(t)).sort(),
    ['public.deliveries.trg_deliveries_derive_harvest','public.delivery_parcels.trg_delivery_parcels_derive_harvest']);
  assert.ok(f.triggersBefore.split(';').every(t=>f.triggersAfter.split(';').includes(t)));
});

test('privileges: three endpoints authenticated-only, private core and triggers closed, no table grant',async()=>{
  for(const fn of ['public.sync_own_harvest_v1(uuid,jsonb)','public.delete_own_harvest_v1(uuid,uuid,bigint)','public.get_own_harvest_head_v1(uuid)'])
    assert.equal(sql(`SELECT has_function_privilege('anon',${q(fn)},'EXECUTE')||'|'||has_function_privilege('public',${q(fn)},'EXECUTE')||'|'||
      has_function_privilege('authenticated',${q(fn)},'EXECUTE');`),'false|false|true',fn);
  assert.equal(sql(`SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='magina_g2_private'
    AND p.proname IN ('sync_harvest','delete_harvest','derive_auto_day','deliveries_derive_harvest','delivery_parcels_derive_harvest')
    AND (has_function_privilege('anon',p.oid,'EXECUTE') OR has_function_privilege('authenticated',p.oid,'EXECUTE'));`),'0');
  // harvest_parcels stays closed; the G0 grants on harvests are unchanged (asserted by the surface test).
  assert.equal(sql(`SELECT has_table_privilege('authenticated','public.harvest_parcels','SELECT') OR has_table_privilege('anon','public.harvest_parcels','SELECT');`),'f');
  const anon=await http('/rest/v1/rpc/sync_own_harvest_v1',{body:{p_operation_id:randomUUID(),p_harvest:day()}});
  assert.ok([401,403].includes(anon.status),anon.raw);
});

test('H4: AUTO_DAY kilos and parcels are derived from ALL live Pesadas, with EXACT/UNALLOCATED semantics',async()=>{
  const a=actors.A, h=day(), op=randomUUID();
  const created=ok(await hv(a,h,op),'create');
  assert.deepEqual([created.remote_version,created.payload_version,created.idempotent],[1,1,false]);
  assert.deepEqual(harvestRow(h.id),{weight:0,version:1,origin:'AUTO_DAY',deleted:false});
  // A multi-parcel lot (EXACT 600 + EXACT 400, one of them archived since) and a Pesada without distribution.
  const p1=dv(h.id,{netGrams:1000,parcels:[share(f.act,600),share(f.arch,400)]});
  const p2=dv(h.id,{netGrams:500,parcels:[share(f.act,null,'UNALLOCATED')]});
  const op1=randomUUID(); ok(await v3(a,p1,op1),'p1'); ok(await v3(a,p2),'p2');
  assert.equal(harvestRow(h.id).weight,1500);
  expectParcels(h.id,[[f.act,'UNALLOCATED','null'],[f.arch,'EXACT',400]]);
  // A retried Pesada is not counted twice; its update replaces, never adds.
  assert.equal(ok(await v3(a,p1,op1),'replay').idempotent,true);
  assert.equal(harvestRow(h.id).weight,1500);
  ok(await v3(a,{...p1,baseVersion:1,netGrams:1200,parcels:[{...p1.parcels[0],weightGrams:800},p1.parcels[1]]}),'p1 update');
  assert.equal(harvestRow(h.id).weight,1700);
  expectParcels(h.id,[[f.act,'UNALLOCATED','null'],[f.arch,'EXACT',400]]);
  // A logically deleted Pesada leaves the derivation; derived kilos never move the Jornada version.
  sql(`UPDATE public.deliveries SET deleted_at=now() WHERE id=${q(p2.id)};`);
  assert.equal(harvestRow(h.id).weight,1200);
  expectParcels(h.id,[[f.act,'EXACT',800],[f.arch,'EXACT',400]]);
  assert.equal(harvestRow(h.id).version,1);
  const head=ok(await hhead(a,h.id),'head');
  assert.deepEqual([head.head_operation_id,head.version,head.weight_grams,head.day_origin],[op,1,1200,'AUTO_DAY']);
});

test('legacy writers on derived Jornadas: v1 header, v2 create and update, a move between Jornadas; MANUAL untouched',async()=>{
  const a=actors.A, h1=day({harvestDate:'2026-10-10'}), h2=day({harvestDate:'2026-10-11'});
  ok(await hv(a,h1),'h1'); ok(await hv(a,h2),'h2');
  ok(await v1(a,{id:randomUUID(),farmId:f.north.id,campaignId:f.cn,deliveryDate:'2026-10-10',destinationName:'Coop',netGrams:300,harvestId:h1.id}),'v1');
  assert.equal(harvestRow(h1.id).weight,300);
  const d=dv(h1.id,{deliveryDate:'2026-10-10',netGrams:700,parcels:[share(f.second,700)]});
  const created=ok(await v2(a,legacy(d)),'v2');
  assert.deepEqual([created.remote_version,created.payload_version],[1,2]);
  assert.equal(harvestRow(h1.id).weight,1000);
  // The v2 update moves the Pesada to the other Jornada: both are recomputed, nothing counted twice.
  ok(await v2(a,legacy({...d,baseVersion:1,harvestId:h2.id})),'v2 move');
  assert.equal(harvestRow(h1.id).weight,300); assert.equal(harvestRow(h2.id).weight,700);
  expectParcels(h2.id,[[f.second,'EXACT',700]]);
  // A MANUAL day keeps its typed kilos whatever its Pesadas weigh.
  const manual=day({dayOrigin:'MANUAL',weightGrams:4242,harvestDate:'2026-10-12'});
  ok(await hv(a,manual),'manual');
  ok(await v2(a,legacy(dv(manual.id,{deliveryDate:'2026-10-12',netGrams:999}))),'v2 manual');
  assert.equal(harvestRow(manual.id).weight,4242);
  assert.equal(parcelsOf(manual.id),'');
});

test('H2: one canonical AUTO_DAY per farm, campaign and date; MANUAL and other farms or campaigns are separate',async()=>{
  const a=actors.A, first=day({harvestDate:'2026-10-02'});
  ok(await hv(a,first),'first');
  const second=day({harvestDate:'2026-10-02'});
  const conflict=refused(await hv(a,second),'23505','sync conflict: AUTO_DAY_EXISTS');
  assert.equal(conflict.details,`harvest_id=${first.id}`);
  assert.equal(sql(`SELECT count(*) FROM public.harvests WHERE id=${q(second.id)};`),'0');
  ok(await hv(a,day({harvestDate:'2026-10-02',campaignId:f.cn2})),'other campaign');
  ok(await hv(a,day({harvestDate:'2026-10-02',farmId:f.south.id,campaignId:f.cs})),'other farm');
  ok(await hv(a,day({harvestDate:'2026-10-02',dayOrigin:'MANUAL',weightGrams:10})),'manual 1');
  ok(await hv(a,day({harvestDate:'2026-10-02',dayOrigin:'MANUAL',weightGrams:20})),'manual 2');
  // Another account never learns the canonical id.
  const foreign=refused(await hv(actors.C,day({harvestDate:'2026-10-02'})),'42501','sync denied: SCOPE');
  assert.ok(!JSON.stringify(foreign).includes(first.id));
  // Null keys cannot slip past the identity, through the writer or a direct insert.
  refused(await hv(a,day({campaignId:null})),'22023','sync invalid: DAY_ORIGIN_FIELDS');
  refused(await hv(a,day({weightGrams:5})),'22023','sync invalid: DAY_ORIGIN_FIELDS');
  refused(await hv(a,day({dayOrigin:'MANUAL'})),'22023','sync invalid: DAY_ORIGIN_FIELDS');
  assert.throws(()=>sql(`INSERT INTO public.harvests(workspace_id,farm_id,campaign_id,harvest_date,weight_grams,day_origin,recorded_by_user_id)
    VALUES (${q(f.ws)},${q(f.north.id)},NULL,'2026-10-03',0,'AUTO_DAY',${q(a.id)});`),/harvests_auto_day_campaign_required/);
  assert.throws(()=>sql(`INSERT INTO public.harvests(workspace_id,farm_id,campaign_id,harvest_date,weight_grams,day_origin,recorded_by_user_id)
    VALUES (${q(f.ws)},${q(f.north.id)},${q(f.cn)},'2026-10-02',0,'AUTO_DAY',${q(a.id)});`),/harvests_auto_day_identity/);
});

test('H2 concurrency: two devices create the same AUTO_DAY at once; the second gets AUTO_DAY_EXISTS, never a duplicate',async()=>{
  const a=actors.A, mine=day({harvestDate:'2026-10-03'}), theirs=day({harvestDate:'2026-10-03'});
  const h=holder(a,`SELECT public.sync_own_harvest_v1(${q(randomUUID())},${q(JSON.stringify(theirs))}::jsonb);`);
  const [result]=await race(h,[()=>hv(a,mine)]);
  const conflict=refused(result,'23505','sync conflict: AUTO_DAY_EXISTS');
  assert.equal(conflict.details,`harvest_id=${theirs.id}`);
  assert.equal(sql(`SELECT count(*) FROM public.harvests WHERE farm_id=${q(f.north.id)} AND campaign_id=${q(f.cn)}
    AND harvest_date='2026-10-03' AND day_origin='AUTO_DAY' AND deleted_at IS NULL;`),'1');
  // Nothing is reassigned on the server: a Pesada naming the losing id is simply refused by the FK.
  const orphan=await v3(a,dv(mine.id,{deliveryDate:'2026-10-03'}));
  assert.notEqual(orphan.status,200);
});

test('two concurrent Pesadas on one AUTO_DAY are both counted (serialized on the Jornada row)',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-04'}); ok(await hv(a,h),'day');
  const held=dv(h.id,{deliveryDate:'2026-10-04',netGrams:400});
  const hd=holder(a,`SELECT public.sync_own_delivery_v3(${q(randomUUID())},${q(JSON.stringify(held))}::jsonb);`);
  const [other]=await race(hd,[()=>v3(a,dv(h.id,{deliveryDate:'2026-10-04',netGrams:600}))]);
  ok(other,'concurrent');
  assert.equal(harvestRow(h.id).weight,1000);
});

test('updates: baseVersion, immutable identity, MANUAL kilos frozen by Pesadas; HEAD follows the current version',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-05'}); ok(await hv(a,h),'day');
  const op=randomUUID(); const updated=ok(await hv(a,{...h,notes:'cuadrilla de 5',workerCount:5,baseVersion:1},op),'update');
  assert.equal(updated.remote_version,2);
  let head=ok(await hhead(a,h.id),'head'); assert.deepEqual([head.head_operation_id,head.version],[op,2]);
  ok(await v3(a,dv(h.id,{deliveryDate:'2026-10-05',netGrams:250})),'pesada');
  head=ok(await hhead(a,h.id),'head after pesada');
  assert.deepEqual([head.head_operation_id,head.version,head.weight_grams],[op,2,250]);
  refused(await hv(a,{...h,notes:'x',baseVersion:1}),'23505','sync conflict: STALE_VERSION');
  refused(await hv(a,{...h,notes:'x',baseVersion:null}),'23505','sync conflict: BASE_VERSION_REQUIRED');
  refused(await hv(a,{...h,harvestDate:'2026-10-06',baseVersion:2}),'23505','sync conflict: BINDING_IMMUTABLE');
  refused(await hv(a,{...h,dayOrigin:'MANUAL',weightGrams:1,baseVersion:2}),'23505','sync conflict: BINDING_IMMUTABLE');
  const manual=day({dayOrigin:'MANUAL',weightGrams:900,harvestDate:'2026-10-05'}); ok(await hv(a,manual),'manual');
  ok(await hv(a,{...manual,weightGrams:950,baseVersion:1}),'manual kilos before pesadas');
  ok(await v2(a,legacy(dv(manual.id,{deliveryDate:'2026-10-05',netGrams:100}))),'pesada manual');
  refused(await hv(a,{...manual,weightGrams:999,baseVersion:2}),'23505','sync conflict: MANUAL_WEIGHT_HAS_PESADAS');
});

test('DELETE: refused with live Pesadas; a deleted Jornada frees its identity and accepts no Pesada (legacy writers too)',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-06'}); ok(await hv(a,h),'day');
  const p=dv(h.id,{deliveryDate:'2026-10-06'}); ok(await v3(a,p),'pesada');
  refused(await hdel(a,h.id,1),'23505','sync conflict: HARVEST_HAS_DEPENDENTS');
  const empty=day({harvestDate:'2026-10-07'}); ok(await hv(a,empty),'empty');
  refused(await hdel(a,empty.id,2),'23505','sync conflict: STALE_VERSION');
  const op=randomUUID(); const deleted=ok(await hdel(a,empty.id,1,op),'delete');
  assert.equal(deleted.remote_version,2);
  assert.equal(ok(await hdel(a,empty.id,1,op),'delete replay').idempotent,true);
  const head=ok(await hhead(a,empty.id),'head'); assert.deepEqual([head.deleted,head.head_operation_id],[true,op]);
  ok(await hv(a,day({harvestDate:'2026-10-07'})),'identity freed');
  refused(await v3(a,dv(empty.id,{deliveryDate:'2026-10-07'})),'23514','harvest conflict: HARVEST_DELETED');
  refused(await v2(a,legacy(dv(empty.id,{deliveryDate:'2026-10-07'}))),'23514','harvest conflict: HARVEST_DELETED');
  refused(await hdel(a,empty.id,2),'23505','sync conflict: DELETED');
});

test('CREATE/DELETE race both ways: the commit order decides, never a Pesada on a deleted Jornada',async()=>{
  const a=actors.A;
  // DELETE first: the waiting Pesada is refused once the deletion commits.
  const h1=day({harvestDate:'2026-10-08'}); ok(await hv(a,h1),'h1');
  const hd=holder(a,`SELECT public.delete_own_harvest_v1(${q(randomUUID())},${q(h1.id)},1);`);
  const [late]=await race(hd,[()=>v3(a,dv(h1.id,{deliveryDate:'2026-10-08'}))]);
  refused(late,'23514','harvest conflict: HARVEST_DELETED');
  // Pesada first: the waiting DELETE sees it and is refused.
  const h2=day({harvestDate:'2026-10-09'}); ok(await hv(a,h2),'h2');
  const pesada=dv(h2.id,{deliveryDate:'2026-10-09'});
  const hp=holder(a,`SELECT public.sync_own_delivery_v3(${q(randomUUID())},${q(JSON.stringify(pesada))}::jsonb);`);
  const [del]=await race(hp,[()=>hdel(a,h2.id,1)]);
  refused(del,'23505','sync conflict: HARVEST_HAS_DEPENDENTS');
  assert.equal(harvestRow(h2.id).deleted,false);
});

test('authority, time zone and periods: owner only, future dates and campaign periods refused; lost receipt recovered',async()=>{
  const a=actors.A;
  const tomorrow=sql(`SELECT ((now() AT TIME ZONE 'Europe/Madrid')::date+1)::text;`);
  refused(await hv(a,day({harvestDate:tomorrow,campaignId:null,dayOrigin:'MANUAL',weightGrams:1})),'22023','sync review: FUTURE_DATE');
  refused(await hv(a,day({harvestDate:'2025-09-15'})),'22023','sync review: CAMPAIGN_PERIOD');
  refused(await hv(a,day({campaignId:f.cs})),'22023','sync invalid: CAMPAIGN_SCOPE');
  refused(await hv(a,{...day(),extra:1}),'22023','sync invalid: SHAPE');
  refused(await hv(a,day()),'23505','sync conflict: AUTO_DAY_EXISTS');
  // Lost receipt: the repeat returns the same receipt, check_sync_operations recovers it.
  const h=day({harvestDate:'2026-10-13'}), op=randomUUID();
  ok(await hv(a,h,op),'create'); assert.equal(ok(await hv(a,h,op),'replay').idempotent,true);
  refused(await hv(a,{...h,notes:'otro'},op),'23505','sync conflict: RECEIPT_CONFLICT');
  const receipt=ok(await rpc('check_sync_operations',a,{p_operation_ids:[op]}),'check').confirmed_receipts[0];
  assert.deepEqual([receipt.entity_type,receipt.remote_version,receipt.payload_version],['HARVEST',1,1]);
  // Ownership transferred: refused, and the head is not readable by the former owner.
  sql(`UPDATE public.workspaces SET owner_user_id=${q(actors.C.id)} WHERE id=${q(f.ws)};`);
  try {
    refused(await hv(a,day({harvestDate:'2026-10-01'})),'42501','sync denied: SCOPE');
    refused(await hhead(a,h.id),'42501','sync denied: SCOPE');
    refused(await hdel(a,h.id,1),'42501','sync denied: SCOPE');
  } finally { sql(`UPDATE public.workspaces SET owner_user_id=${q(a.id)} WHERE id=${q(f.ws)};`); }
  refused(await rpc('sync_own_harvest_v1',a,{p_operation_id:randomUUID(),p_harvest:day()},token(a,{iss:'https://evil.example/auth/v1'})),
    '42501','sync denied: IDENTITY');
});

test('exact sums: an AUTO_DAY total beyond bigint is refused, never wrapped, and the Pesada is rolled back',async()=>{
  const a=actors.A, h=day({harvestDate:'2026-10-01'}); ok(await hv(a,h),'day');
  const big=n=>legacy(dv(h.id,{deliveryDate:'2026-10-01',netGrams:n,parcels:[share(f.act,n)]}));
  ok(await v2(a,big(5000000000000000000)),'first');
  const second=big(5000000000000000000);
  refused(await v2(a,second),'22003','harvest invalid: WEIGHT_OVERFLOW');
  assert.equal(sql(`SELECT count(*) FROM public.deliveries WHERE id=${q(second.id)};`),'0');
  assert.equal(harvestRow(h.id).weight,5000000000000000000);
});
