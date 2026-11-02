// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-historical-writers-v1.sql (#808 SQL phase): sync_own_delivery_v3 and
// sync_own_expense_v2. Matrix T1-T14, A1-A6, I1-I6 plus P3, DST, lost receipts and revocation.
// Tests run in file order and share one fixture.
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { createServer, request as httpsRequest } from 'node:https';

const work = process.env.G26_HW_WORK;
const project = process.env.G26_HW_PROJECT;
assert.match(project ?? '', /^mg26-hw-[0-9]+-[0-9]+$/);
assert.ok(work);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['localhost', '127.0.0.1'].includes(api.hostname));
assert.ok(readFileSync(join(work, 'supabase/config.toml'), 'utf8').includes(`project_id = "${project}"`));
const container = `supabase_db_${project}`;
const psqlArgs = ['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'];
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', psqlArgs,
  {input:text,encoding:'utf8',timeout:120000,maxBuffer:64*1024*1024,stdio:['pipe','pipe','pipe']}).trim();
async function http(path, {token,admin=false,body,method='POST'}={}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const started = Date.now();
  const response = await fetch(api + path.replace(/^\//,''), {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data,raw,ms:Date.now()-started};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor, {issuer=ISSUER, expired=false, expiredBy=1, extra={}}={}) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:issuer,
    exp:expired?Math.floor(Date.now()/1000)-expiredBy:claims.exp,...extra})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const rpc=(name,actor,body,authToken=token(actor))=>http(`/rest/v1/rpc/${name}`,{token:authToken,body});
const v3=(actor,d,op=randomUUID(),authToken)=>rpc('sync_own_delivery_v3',actor,{p_operation_id:op,p_delivery:d},authToken);
const v2=(actor,d,op=randomUUID())=>rpc('sync_own_delivery_v2',actor,{p_operation_id:op,p_delivery:d});
const e2=(actor,e,op=randomUUID(),authToken)=>rpc('sync_own_expense_v2',actor,{p_operation_id:op,p_expense:e},authToken);
const e1=(actor,e,op=randomUUID())=>rpc('sync_own_expense',actor,{p_operation_id:op,p_expense:e});

// ---- Real producers of the history: #798 workspace, #799 farm, #800 parcel, #805 archive/restore, campaigns.
const START='2025-09-01T00:00:00.007Z';
const workspace=async(actor,timezone='Europe/Madrid')=>{
  const id=randomUUID();
  const r=await rpc('create_own_workspace',actor,{p_operation_id:randomUUID(),p_workspace:{id,name:'Olivar QA',
    countryCode:'ES',timezone,locale:'es-ES',currency:'EUR'}});
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
const campaign=async(actor,ws,farmRef,parcels,{start,end})=>{
  const id=randomUUID();
  const children=parcels.map(p=>({id:randomUUID(),parcelId:p.id,farmIdAtStart:farmRef.id,farmNameAtStart:farmRef.name,
    parcelNameAtStart:p.name,managedAreaM2AtStart:1234.5,cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}));
  const r=await rpc('create_own_campaign',actor,{p_operation_id:randomUUID(),p_campaign:{id,workspaceId:ws,farmId:farmRef.id,
    name:'Campaña QA',startDate:start,endDate:end,status:'CLOSED',notes:null,parcels:children}});
  assert.equal(r.status,200,r.raw);
  return {id,cp:Object.fromEntries(children.map(c=>[c.parcelId,c.id]))};
};
const archiveBody=(p,at)=>({p_operation_id:randomUUID(),p_parcel_id:p.id,p_expected_version:p.version,
  p_archive:{archivedAt:at,closeMembershipId:p.membership}});
const restoreBody=(p,farmRef,at,openId)=>({p_operation_id:randomUUID(),p_parcel_id:p.id,p_expected_version:p.version,
  p_restore:{restoredAt:at,openMembership:{id:openId,farmId:farmRef.id,validFrom:at}}});
const archive=async(actor,p,at)=>{
  const r=await rpc('archive_own_parcel_v1',actor,archiveBody(p,at));
  assert.equal(r.status,200,r.raw); p.version++; p.archivedAt=at;
};
const restore=async(actor,p,farmRef,at)=>{
  const openId=randomUUID();
  const r=await rpc('restore_own_parcel_v1',actor,restoreBody(p,farmRef,at,openId));
  assert.equal(r.status,200,r.raw); p.version++; p.membership=openId;
};

// ---- Payloads. v3 = every v2 field plus originCreatedAt; expense v2 = closed 14-key object.
const share=(p,{weight=1000,cp=null,name=p.name,id=randomUUID()}={})=>({id,parcelId:p.id,campaignParcelId:cp,
  parcelNameAtDelivery:name,weightGrams:weight,allocationMode:'EXACT'});
const f={};
const actors={};
const dv=(o={})=>({id:randomUUID(),farmId:f.north.id,campaignId:f.cn.id,deliveryDate:'2026-10-14',destinationName:'Cooperativa QA',
  destinationOrganizationId:null,netGrams:1000,grossGrams:null,tareGrams:null,deliveryNumber:null,ticketNumber:null,source:'MANUAL',
  notes:null,harvestId:null,deliveryTime:null,harvestOrigin:null,baseVersion:null,parcels:[share(f.act)],
  originCreatedAt:'2026-10-14T18:00:00Z',...o});
const legacy=d=>{const {originCreatedAt,...rest}=d; return rest;};
const ex=(o={})=>({id:randomUUID(),farmId:f.north.id,campaignId:f.cn.id,parcelId:null,expenseDate:'2026-10-14',concept:'Poda',
  category:'LABOR',amountMinor:15050,currency:'EUR',provider:null,notes:null,status:'POSTED',baseVersion:null,
  originCreatedAt:'2026-10-14T18:00:00Z',...o});

// ---- Assertions.
/** Every row any writer could touch; equal before and after a rejection means nothing was written. */
const fingerprint=()=>sql(`SELECT md5(concat_ws('|',
  (SELECT string_agg(id||':'||version||':'||delivery_date||':'||coalesce(delivery_time,'-'),',' ORDER BY id) FROM public.deliveries),
  (SELECT string_agg(id||':'||parcel_id||':'||coalesce(weight_grams::text,'-'),',' ORDER BY id) FROM public.delivery_parcels),
  (SELECT string_agg(id||':'||version||':'||coalesce(parcel_id::text,'-')||':'||amount_minor,',' ORDER BY id) FROM public.expenses),
  (SELECT string_agg(entity_id||':'||origin_created_at_exact,',' ORDER BY entity_id) FROM magina_g2_private.fact_origins),
  (SELECT count(*)::text FROM magina_g2_private.sync_operations),
  (SELECT string_agg(id||':'||version||':'||status,',' ORDER BY id) FROM public.parcels)));`);
const ok=(r,{version=1,pv,idempotent=false}={})=>{
  assert.equal(r.status,200,r.raw.slice(0,500));
  assert.equal(r.data.success,true); assert.equal(r.data.status,'APPLIED');
  assert.equal(r.data.idempotent,idempotent); assert.equal(r.data.remote_version,version);
  if(pv!==undefined) assert.equal(r.data.payload_version,pv);
  return r;
};
const HTTP={'42501':403,'22023':400,'23505':409};
/** Rejected whole with the exact SQLSTATE and cause token, and the database is untouched. */
const rejected=async(call,code,message)=>{
  const before=fingerprint();
  const r=await call();
  assert.equal(r.status,HTTP[code],r.raw.slice(0,500));
  assert.equal(r.data.code,code,r.raw.slice(0,500));
  assert.equal(r.data.message,message,r.raw.slice(0,500));
  assert.equal(fingerprint(),before,`no write for ${message}`);
  return r;
};
const origin=id=>sql(`SELECT origin_created_at_exact||'|'||adopted FROM magina_g2_private.fact_origins WHERE entity_id=${q(id)};`);

// ---- Two sessions: a psql holder keeps a transaction open while an HTTP call runs into its locks.
const claimsFor=actor=>JSON.stringify({sub:actor.id,role:'authenticated',aud:'authenticated',iss:ISSUER,
  exp:Math.floor(Date.now()/1000)+3600,is_anonymous:false});
function holder(actor,statements,seconds=4){
  const tag=`g808_${randomUUID().replaceAll('-','')}`;
  const text=`SET application_name=${q(tag)};
BEGIN;
SELECT set_config('request.jwt.claims',${q(claimsFor(actor))},true);
SET LOCAL ROLE authenticated;
${statements}
RESET ROLE;
SELECT pg_sleep(${seconds});
COMMIT;
SELECT 'HOLDER_COMMITTED';`;
  const child=spawn('docker',psqlArgs,{stdio:['pipe','pipe','pipe']});
  let out='',err='';
  child.stdout.on('data',d=>out+=d); child.stderr.on('data',d=>err+=d);
  child.stdin.end(text);
  const done=new Promise(resolve=>child.on('close',code=>resolve({code,out,err})));
  return {tag,done};
}
const until=async(probe,what,ms=20000)=>{
  const deadline=Date.now()+ms;
  while(Date.now()<deadline){ if(probe()) return; await new Promise(r=>setTimeout(r,100)); }
  throw new Error(`timeout waiting for ${what}`);
};
/** The holder is sleeping inside its open transaction. */
const holding=h=>until(()=>sql(`SELECT count(*) FROM pg_stat_activity WHERE application_name=${q(h.tag)}
  AND state='active' AND query LIKE 'SELECT pg_sleep%';`)==='1','holder transaction');
/** Some other backend is blocked on a row lock held by the holder. */
const blockedBy=h=>until(()=>Number(sql(`SELECT count(*) FROM pg_stat_activity a
  WHERE a.wait_event_type='Lock' AND (SELECT pid FROM pg_stat_activity WHERE application_name=${q(h.tag)}) = ANY(pg_blocking_pids(a.pid));`))>0,
  'a request blocked by the holder');
async function race(h,calls){
  await holding(h);
  const pending=calls.map(call=>call());
  await blockedBy(h);
  const results=await Promise.all(pending);
  const held=await h.done;
  assert.equal(held.code,0,held.err); assert.match(held.out,/HOLDER_COMMITTED/);
  return results;
}

// ---- Exact legacy surface, captured before the proposal is loaded and compared after.
const LEGACY_FUNCTIONS=['magina_g2_private.sync_delivery(uuid,jsonb,integer)','public.sync_own_delivery(uuid,jsonb)',
  'public.sync_own_delivery_v2(uuid,jsonb)','public.sync_own_expense(uuid,jsonb)','public.check_sync_operations(uuid[])',
  'public.get_own_delivery_v2(uuid)','magina_g2_private.mutate_parcel(text,uuid,uuid,bigint,jsonb)',
  'magina_private.has_farm_capability_for(uuid,text,uuid)'];
const legacySurface=()=>sql(`SELECT concat_ws(E'\\n',
  (SELECT string_agg(p.oid::regprocedure::text||'='||md5(p.prosrc||'|'||coalesce(array_to_string(p.proconfig,','),'')||'|'||
     coalesce(p.proacl::text,'')||'|'||p.prosecdef::text||'|'||p.provolatile::text),';' ORDER BY p.oid::regprocedure::text)
   FROM pg_proc p WHERE p.oid IN (${LEGACY_FUNCTIONS.map(fn=>`${q(fn)}::regprocedure`).join(',')})),
  (SELECT string_agg(table_name||'.'||column_name||':'||data_type||':'||coalesce(column_default,'-')||':'||is_nullable,';'
     ORDER BY table_name,ordinal_position) FROM information_schema.columns
   WHERE table_schema='public' AND table_name IN ('deliveries','delivery_parcels','expenses','parcels','farm_parcel_memberships','campaigns','farms','workspaces')),
  (SELECT string_agg(tablename||'.'||policyname||':'||cmd||':'||coalesce(qual,'-')||':'||coalesce(with_check,'-'),';' ORDER BY tablename,policyname)
   FROM pg_policies WHERE schemaname='public'),
  (SELECT string_agg(table_name||':'||grantee||':'||privilege_type,';' ORDER BY table_name,grantee,privilege_type)
   FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','authenticated','PUBLIC')),
  (SELECT string_agg(tgrelid::regclass::text||'.'||tgname,';' ORDER BY tgrelid::regclass::text,tgname) FROM pg_trigger
   WHERE NOT tgisinternal AND tgrelid::regclass::text LIKE 'public.%'),
  (SELECT string_agg(p.oid::regprocedure::text,';' ORDER BY p.oid::regprocedure::text) FROM pg_proc p
   JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
     AND has_function_privilege('anon',p.oid,'EXECUTE') AND p.proname NOT LIKE 'sync_own_%'));`);

// ---- HTTPS leg: a pinned self-signed TLS endpoint in front of the disposable API.
let tlsServer, tlsPort;
const tls={key:readFileSync(process.env.G26_HW_TLS_KEY),cert:readFileSync(process.env.G26_HW_TLS_CERT)};
const TLS_ROUTES=new Set(['/rest/v1/rpc/sync_own_delivery_v3','/rest/v1/rpc/sync_own_expense_v2','/rest/v1/rpc/check_sync_operations']);
function viaTls(path,authToken,body,{ca=tls.cert}={}){
  return new Promise((resolve,reject)=>{
    const payload=JSON.stringify(body);
    const req=httpsRequest({host:'127.0.0.1',port:tlsPort,path,method:'POST',...(ca?{ca}:{}),
      headers:{apikey:status.ANON_KEY,Authorization:`Bearer ${authToken}`,'Content-Type':'application/json',
        'Content-Length':Buffer.byteLength(payload)}},res=>{
      const protocol=res.socket?.getProtocol?.(); let raw=''; res.setEncoding('utf8'); res.on('data',c=>raw+=c);
      res.on('end',()=>{ let data; try{data=JSON.parse(raw);}catch{data=raw;}
        resolve({status:res.statusCode,data,raw,protocol}); });
    });
    req.on('error',reject); req.end(payload);
  });
}

before(async()=>{
  for(const label of ['A','C','M']) {
    const email=`g26-hw-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}});
    assert.equal(created.status,200,created.raw);
    const login=await http('/auth/v1/token?grant_type=password',{body:{email,password}});
    assert.equal(login.status,200);
    actors[label]={id:created.data.id,token:login.data.access_token};
  }
  const migrations=['20261011120000_g0_auth_workspaces_shared_farms.sql',
    '20261011120001_g0_agricultural_sync_schema.sql','20261011120002_g0_contracts_and_storage_rls.sql',
    '20261016041222_g0_legacy_storage_parcel_hardening.sql','20261016041601_g0_aggregate_identity_guards.sql'];
  const base=[...migrations.map(name=>new URL(`../../migrations/${name}`,import.meta.url)),
    ...['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql'].map(name=>new URL(`../../proposals/${name}`,import.meta.url)),
    new URL('../../migrations/20261029044901_g26_workspace_authorization.sql',import.meta.url),
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql','g26-parcel-mutation-v1.sql']
      .map(name=>new URL(`../../proposals/${name}`,import.meta.url))];
  sql(`BEGIN;\n${base.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT;`);
  f.legacyBefore=legacySurface();
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-historical-writers-v1.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  f.legacyAfter=legacySurface();
  for(let attempt=0;;attempt++){
    const probe=await v3(actors.A,{});
    if(probe.status!==404) break;
    if(attempt===40) throw new Error('PostgREST did not expose sync_own_delivery_v3');
    await new Promise(resolve=>setTimeout(resolve,250));
  }

  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.south=await farm(a,f.ws,'Finca Sur');
  for(const key of ['act','arch','nano','fall','spring','move','leg','race1','race2','bad1','bad2','bad3'])
    f[key]=await parcel(a,f.ws,f.north,`Parcela ${key}`);
  f.south1=await parcel(a,f.ws,f.south,'Parcela sur');
  const northParcels=['act','arch','nano','fall','spring','move','leg','race1','race2','bad1','bad2','bad3'].map(k=>f[k]);
  f.cn=await campaign(a,f.ws,f.north,northParcels,{start:'2025-10-01',end:'2026-10-31'});
  f.cnOpen=await campaign(a,f.ws,f.north,[f.act],{start:'2026-11-01',end:null});
  f.cs=await campaign(a,f.ws,f.south,[f.south1],{start:'2025-10-01',end:null});

  // A legacy v2 Pesada recorded while P_leg was alive; the server saw it on 10 Oct (backdated created_at).
  f.legDelivery=dv({deliveryDate:'2026-10-10',parcels:[share(f.leg)]});
  ok(await v2(a,legacy(f.legDelivery)),{pv:2});
  sql(`UPDATE public.deliveries SET created_at='2026-10-10T20:00:00Z' WHERE id=${q(f.legDelivery.id)};`);
  f.legExpense=ex({parcelId:f.leg.id,expenseDate:'2026-10-10'});
  const legacyExpense=(({originCreatedAt,baseVersion,...rest})=>rest)(f.legExpense);
  assert.equal((await e1(a,legacyExpense)).status,200);
  sql(`UPDATE public.expenses SET created_at='2026-10-10T20:00:00Z' WHERE id=${q(f.legExpense.id)};`);

  await archive(a,f.arch,'2026-10-15T08:30:00Z');             // 10:30 CEST on 15 Oct
  await archive(a,f.nano,'2026-10-14T08:30:00.000000001Z');   // one nanosecond after 10:30 CEST
  await archive(a,f.fall,'2026-10-25T23:30:00Z');             // 00:30 CET on 26 Oct, after the 25-hour day
  await archive(a,f.spring,'2026-03-29T22:30:00Z');           // 00:30 CEST on 30 Mar, after the 23-hour day
  await archive(a,f.move,'2026-06-01T10:00:00Z');
  await restore(a,f.move,f.south,'2026-06-10T10:00:00Z');
  await archive(a,f.leg,'2026-10-12T10:00:00Z');
  await archive(a,f.bad3,'2026-10-01T10:00:00Z');
  // Incongruent tombstones, only reachable by external edits.
  sql(`UPDATE public.parcels SET deleted_at='2026-10-01T10:00:00Z' WHERE id=${q(f.bad1.id)};
       UPDATE public.parcels SET status='ARCHIVED' WHERE id=${q(f.bad2.id)};
       UPDATE public.farm_parcel_memberships SET valid_until=NULL,valid_until_exact=NULL WHERE parcel_id=${q(f.bad3.id)};`);

  // Midnight DST: America/Santiago skips 00:00-00:59 on 6 Sep 2026 and repeats 23:00-23:59 on 4 Apr 2026.
  f.wsCl=await workspace(a,'America/Santiago');
  f.cl=await farm(a,f.wsCl,'Fundo QA');
  f.pcl=await parcel(a,f.wsCl,f.cl,'Cuartel');
  f.ccl=await campaign(a,f.wsCl,f.cl,[f.pcl],{start:'2025-10-01',end:'2026-10-31'});
  await archive(a,f.pcl,'2026-09-06T04:00:00Z');              // exactly the first instant of local 6 Sep

  // Other principals.
  f.cWs=await workspace(actors.C); f.cFarm=await farm(actors.C,f.cWs,'Finca C');
  f.cParcel=await parcel(actors.C,f.cWs,f.cFarm,'Parcela C');
  f.cCampaign=await campaign(actors.C,f.cWs,f.cFarm,[f.cParcel],{start:'2025-10-01',end:null});
  // M: workspace member read+write, G0 farm grant with every capability, and creator of a farm in A's workspace.
  f.mFarm=randomUUID(); f.mParcel=randomUUID(); f.mCampaign=randomUUID();
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions) VALUES
         (${q(f.ws)},${q(actors.M.id)},'EDITOR',ARRAY['read','write']);
       INSERT INTO public.farm_access_grants(farm_id,user_id,granted_by_user_id,role,capabilities,status) VALUES
         (${q(f.north.id)},${q(actors.M.id)},${q(a.id)},'WORKER',ARRAY['add_deliveries','add_expenses','manage_all'],'ACTIVE');
       INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,status)
         VALUES (${q(f.mFarm)},${q(f.ws)},${q(actors.M.id)},${q(actors.M.id)},'Finca de M','ACTIVE');
       INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(f.mParcel)},${q(f.ws)},'Parcela de M');
       INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from)
         VALUES (gen_random_uuid(),${q(f.ws)},${q(f.mFarm)},${q(f.mParcel)},'2025-09-01T00:00:00Z');
       INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,end_date,status)
         VALUES (${q(f.mCampaign)},${q(f.ws)},${q(f.mFarm)},'Campaña M','2025-10-01',NULL,'CLOSED');`);
  // Dead farms of A: soft-deleted, and ARCHIVED with an instant.
  f.deadFarm=await farm(a,f.ws,'Finca borrada'); f.archFarm=await farm(a,f.ws,'Finca archivada');
  f.deadCampaign=randomUUID(); f.archCampaign=randomUUID();
  sql(`INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,status) VALUES
         (${q(f.deadCampaign)},${q(f.ws)},${q(f.deadFarm.id)},'C','2025-10-01','CLOSED'),
         (${q(f.archCampaign)},${q(f.ws)},${q(f.archFarm.id)},'C','2025-10-01','CLOSED');
       UPDATE public.farms SET deleted_at='2026-10-01T00:00:00Z' WHERE id=${q(f.deadFarm.id)};
       UPDATE public.farms SET status='ARCHIVED',deleted_at='2026-10-01T00:00:00Z' WHERE id=${q(f.archFarm.id)};`);

  tlsServer=createServer(tls,async(incoming,outgoing)=>{
    const chunks=[]; for await (const c of incoming) chunks.push(c);
    if(incoming.method!=='POST'||!TLS_ROUTES.has(incoming.url)){ outgoing.writeHead(404).end(); return; }
    const upstream=await fetch(api+incoming.url.slice(1),{method:'POST',body:Buffer.concat(chunks),
      headers:{apikey:incoming.headers.apikey,authorization:incoming.headers.authorization,'content-type':'application/json'}});
    outgoing.writeHead(upstream.status,{'content-type':'application/json'}).end(await upstream.text());
  });
  await new Promise(resolve=>tlsServer.listen(0,'127.0.0.1',resolve));
  tlsPort=tlsServer.address().port;
});
after(()=>new Promise(resolve=>tlsServer?tlsServer.close(resolve):resolve()));

test('legacy writers, tables, RLS, grants and triggers are byte-identical after loading the proposal',()=>{
  assert.ok(f.legacyBefore.split('\n')[0].split(';').length===LEGACY_FUNCTIONS.length,'every legacy function captured');
  assert.equal(f.legacyAfter,f.legacyBefore);
});

test('A6 privileges: endpoints authenticated-only, private core and helpers closed, fact_origins unreachable',async()=>{
  for(const fn of ['public.sync_own_delivery_v3(uuid,jsonb)','public.sync_own_expense_v2(uuid,jsonb)'])
    assert.equal(sql(`SELECT has_function_privilege('anon',${q(fn)},'EXECUTE')||'|'||has_function_privilege('authenticated',${q(fn)},'EXECUTE')
      ||'|'||has_function_privilege('public',${q(fn)},'EXECUTE');`),'false|true|false',fn);
  assert.equal(sql(`SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='magina_g2_private' AND (p.proname LIKE '%historical%' OR p.proname IN ('resolve_civil_time','local_day_key','origin_key','fact_origins_immutable'))
      AND (has_function_privilege('anon',p.oid,'EXECUTE') OR has_function_privilege('authenticated',p.oid,'EXECUTE'));`),'0');
  assert.equal(sql(`SELECT string_agg(p.proname||':'||p.prosecdef||':'||array_to_string(p.proconfig,','),';' ORDER BY p.proname)
    FROM pg_proc p WHERE p.proname IN ('sync_own_delivery_v3','sync_own_expense_v2','sync_delivery_historical','sync_expense_historical');`),
    'sync_delivery_historical:true:search_path="";sync_expense_historical:true:search_path="";sync_own_delivery_v3:true:search_path="";sync_own_expense_v2:true:search_path=""');
  assert.equal(sql(`SELECT has_table_privilege('authenticated','magina_g2_private.fact_origins','SELECT')
    OR has_table_privilege('anon','magina_g2_private.fact_origins','SELECT');`),'f');
  for(const name of ['sync_delivery_historical','sync_expense_historical','check_historical_parcel'])
    assert.equal((await rpc(name,actors.A,{})).status,404,name);
  const anon=await http('/rest/v1/rpc/sync_own_delivery_v3',{body:{p_operation_id:randomUUID(),p_delivery:dv()}});
  assert.ok([401,403].includes(anon.status),anon.raw);
  assert.equal((await http('/rest/v1/fact_origins?select=*',{method:'GET',token:token(actors.A)})).status,404);
});

test('T1-T5 and the exact archive instant: history before A is written, after A is refused',async()=>{
  const a=actors.A, at=f.arch;
  // T1, T2: before the archive day, and on it at 09:00 local.
  const t1=dv({deliveryDate:'2026-10-14',parcels:[share(at,{cp:f.cn.cp[at.id]})]});
  ok(await v3(a,t1),{pv:3});
  assert.equal(origin(t1.id),'2026-10-14T18:00:00Z|false');
  assert.equal(sql(`SELECT snapshot_format||'|'||recorded_by_user_id FROM public.deliveries WHERE id=${q(t1.id)};`),`2|${a.id}`);
  ok(await v3(a,dv({deliveryDate:'2026-10-15',deliveryTime:'09:00',originCreatedAt:'2026-10-15T07:10:00Z',parcels:[share(at)]})),{pv:3});
  // The last minute before A (10:30:00 local) is accepted; 10:30 itself is not before A.
  ok(await v3(a,dv({deliveryDate:'2026-10-15',deliveryTime:'10:29',originCreatedAt:'2026-10-15T08:20:00Z',parcels:[share(at)]})));
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-15',deliveryTime:'10:30',originCreatedAt:'2026-10-15T08:00:00Z',parcels:[share(at)]})),
    '42501','sync review: AFTER_ARCHIVE');
  // T3: on the archive day after A. T5: the day after.
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-15',deliveryTime:'11:00',originCreatedAt:'2026-10-15T08:00:00Z',parcels:[share(at)]})),
    '42501','sync review: AFTER_ARCHIVE');
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-16',originCreatedAt:'2026-10-15T08:00:00Z',parcels:[share(at)]})),
    '42501','sync review: AFTER_ARCHIVE');
  // Nanosecond precision: A is 1 ns after 10:30:00 local, so a 10:30 Pesada is (just) before it.
  ok(await v3(a,dv({deliveryDate:'2026-10-14',deliveryTime:'10:30',originCreatedAt:'2026-10-14T08:00:00Z',parcels:[share(f.nano)]})));
  // Legacy column: v2 refuses any archived parcel, whatever the date.
  await rejected(()=>v2(a,legacy(dv({deliveryDate:'2026-10-14',parcels:[share(at)]}))),'42501','sync denied: parcel does not belong to farm');
});

test('T4 and P3: same-day untimed facts need originCreatedAt before A, with a 5-minute clock margin',async()=>{
  const a=actors.A, at=f.arch;
  // Created offline at 10:00 local (08:00Z), sent after the archive at 10:30 local: written.
  const d=dv({deliveryDate:'2026-10-15',originCreatedAt:'2026-10-15T08:00:00Z',parcels:[share(at)]});
  ok(await v3(a,d));
  const e=ex({parcelId:at.id,expenseDate:'2026-10-15',originCreatedAt:'2026-10-15T08:00:00.000000001Z'});
  ok(await e2(a,e),{pv:2});
  assert.equal(origin(e.id),'2026-10-15T08:00:00.000000001Z|false');
  // Recorded 3 minutes before A: the devices' clocks cannot be told apart from an honest late entry.
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-15',originCreatedAt:'2026-10-15T08:27:00Z',parcels:[share(at)]})),
    '42501','sync review: ORIGIN_CLOCK_UNCERTAIN');
  // Recorded after A (honest late entry, or a device clock 2 h ahead): review, never written.
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-15',originCreatedAt:'2026-10-15T10:00:00Z',parcels:[share(at)]})),
    '42501','sync review: ORIGIN_NOT_BEFORE_CLOSURE');
  await rejected(()=>e2(a,ex({parcelId:at.id,expenseDate:'2026-10-15',originCreatedAt:'2026-10-15T08:30:00Z'})),
    '42501','sync review: ORIGIN_NOT_BEFORE_CLOSURE');
  // Retroactive entry of an old fact after the archive goes to review too (manual flow later).
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-01',originCreatedAt:'2026-10-20T09:00:00Z',parcels:[share(at)]})),
    '42501','sync review: ORIGIN_NOT_BEFORE_CLOSURE');
  // A device clock in the future, a missing or malformed origin.
  const ahead=new Date(Date.now()+86400000).toISOString();
  await rejected(()=>v3(a,dv({originCreatedAt:ahead})),'22023','sync review: ORIGIN_IN_FUTURE');
  await rejected(()=>e2(a,ex({originCreatedAt:ahead})),'22023','sync review: ORIGIN_IN_FUTURE');
  const {originCreatedAt,...missing}=dv();
  await rejected(()=>v3(a,missing),'22023','sync invalid: SHAPE');
  for(const bad of ['2026-10-14 18:00:00Z','2026-10-14T18:00:00+02:00','2026-02-30T10:00:00Z','2026-10-14T18:00:00.1234567890Z'])
    await rejected(()=>v3(a,dv({originCreatedAt:bad})),'22023','sync invalid: ORIGIN_CREATED_AT');
  await rejected(()=>v3(a,dv({originCreatedAt:null})),'22023','sync invalid: SHAPE');
  // An ACTIVE parcel with an open period has no closure: any past origin is fine.
  ok(await v3(a,dv({deliveryDate:'2026-10-14',originCreatedAt:'2026-10-30T09:00:00Z'})));
});

test('T6 future dates in the workspace zone are refused only by v3; v2 keeps accepting them',async()=>{
  const a=actors.A;
  const [today,tomorrow]=sql(`SELECT (now() AT TIME ZONE 'Europe/Madrid')::date||'|'||((now() AT TIME ZONE 'Europe/Madrid')::date+1);`).split('|');
  await rejected(()=>v3(a,dv({campaignId:f.cnOpen.id,deliveryDate:tomorrow})),'22023','sync review: FUTURE_DATE');
  await rejected(()=>e2(a,ex({campaignId:null,expenseDate:tomorrow})),'22023','sync review: FUTURE_DATE');
  // Today, two hours from now (local): a future instant. Skipped near local midnight, where it is tomorrow.
  const [date,time]=sql(`SELECT to_char(now() AT TIME ZONE 'Europe/Madrid'+interval '2 hours','YYYY-MM-DD|HH24:MI');`).split('|');
  if(date===today) await rejected(()=>v3(a,dv({campaignId:f.cnOpen.id,deliveryDate:date,deliveryTime:time})),'22023','sync review: FUTURE_DATE');
  ok(await v2(a,legacy(dv({campaignId:f.cnOpen.id,deliveryDate:tomorrow}))),{pv:2});
});

test('T7 DST in Europe/Madrid: 23- and 25-hour days, skipped and repeated civil times are never guessed',async()=>{
  const day=d=>sql(`SELECT (magina_g2_private.local_day_key(${q(d)}::date+1,'Europe/Madrid')-magina_g2_private.local_day_key(${q(d)}::date,'Europe/Madrid'))/3600000000000;`);
  assert.equal(Number(day('2026-03-29')),23); assert.equal(Number(day('2026-10-25')),25); assert.equal(Number(day('2026-10-14')),24);
  assert.equal(sql(`SELECT to_char(to_timestamp(magina_g2_private.local_day_key('2026-10-26','Europe/Madrid')/1000000000) AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI');`),'2026-10-25T23:00');
  const civil=(d,t)=>{ try { return sql(`SELECT to_char(magina_g2_private.resolve_civil_time(${q(d)},${q(t)},'Europe/Madrid') AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI');`); }
    catch(error){ return String(error.stderr).match(/DELIVERY_TIME_[A-Z]+/)?.[0] ?? String(error.stderr); } };
  assert.equal(civil('2026-03-29','01:59'),'2026-03-29T00:59');
  assert.equal(civil('2026-03-29','02:30'),'DELIVERY_TIME_NONEXISTENT');
  assert.equal(civil('2026-03-29','03:00'),'2026-03-29T01:00');
  assert.equal(civil('2026-10-25','01:59'),'2026-10-24T23:59');
  assert.equal(civil('2026-10-25','02:00'),'DELIVERY_TIME_AMBIGUOUS');
  assert.equal(civil('2026-10-25','02:59'),'DELIVERY_TIME_AMBIGUOUS');
  assert.equal(civil('2026-10-25','03:00'),'2026-10-25T02:00');
  assert.equal(civil('2026-10-25','00:00'),'2026-10-24T22:00');
  const a=actors.A;
  await rejected(()=>v3(a,dv({deliveryDate:'2026-03-29',deliveryTime:'02:30'})),'22023','sync review: DELIVERY_TIME_NONEXISTENT');
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-25',deliveryTime:'02:30'})),'22023','sync review: DELIVERY_TIME_AMBIGUOUS');
  ok(await v3(a,dv({deliveryDate:'2026-10-25',deliveryTime:'03:30',originCreatedAt:'2026-10-25T10:00:00Z'})));
  // 25-hour day with the archive 30 minutes after it ends (00:30 CET on 26 Oct = 23:30Z on 25 Oct).
  const fall=f.fall;
  ok(await v3(a,dv({deliveryDate:'2026-10-25',deliveryTime:'23:59',originCreatedAt:'2026-10-25T22:59:00Z',parcels:[share(fall)]})));
  // T8: local 26 Oct starts at 23:00Z, before A, so an untimed fact of that day is history (UTC would say 26 Oct starts after A).
  ok(await v3(a,dv({deliveryDate:'2026-10-26',originCreatedAt:'2026-10-25T23:00:00Z',parcels:[share(fall)]})));
  ok(await v3(a,dv({deliveryDate:'2026-10-26',deliveryTime:'00:15',originCreatedAt:'2026-10-25T23:20:00Z',parcels:[share(fall)]})));
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-26',deliveryTime:'00:30',originCreatedAt:'2026-10-25T23:00:00Z',parcels:[share(fall)]})),
    '42501','sync review: AFTER_ARCHIVE');
  await rejected(()=>v3(a,dv({deliveryDate:'2026-10-27',originCreatedAt:'2026-10-25T23:00:00Z',parcels:[share(fall)]})),
    '42501','sync review: AFTER_ARCHIVE');
  // 23-hour day: archive at 00:30 CEST on 30 Mar (22:30Z on 29 Mar); local 30 Mar starts at 22:00Z.
  const spring=f.spring;
  ok(await v3(a,dv({deliveryDate:'2026-03-30',originCreatedAt:'2026-03-29T22:00:00Z',parcels:[share(spring)]})));
  ok(await e2(a,ex({parcelId:spring.id,expenseDate:'2026-03-30',originCreatedAt:'2026-03-29T22:10:00Z'})));
  await rejected(()=>v3(a,dv({deliveryDate:'2026-03-30',originCreatedAt:'2026-03-29T22:28:00Z',parcels:[share(spring)]})),
    '42501','sync review: ORIGIN_CLOCK_UNCERTAIN');
  await rejected(()=>v3(a,dv({deliveryDate:'2026-03-31',originCreatedAt:'2026-03-29T22:00:00Z',parcels:[share(spring)]})),
    '42501','sync review: AFTER_ARCHIVE');
});

test('T7/T8 midnight DST in America/Santiago: the skipped midnight starts the day at the transition',async()=>{
  const tz=q('America/Santiago');
  const hours=d=>Number(sql(`SELECT (magina_g2_private.local_day_key(${q(d)}::date+1,${tz})-magina_g2_private.local_day_key(${q(d)}::date,${tz}))/3600000000000;`));
  assert.equal(hours('2026-09-06'),23); assert.equal(hours('2026-04-04'),25); assert.equal(hours('2026-09-05'),24);
  assert.equal(sql(`SELECT to_char(to_timestamp(magina_g2_private.local_day_key('2026-09-06',${tz})/1000000000) AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI');`),'2026-09-06T04:00');
  const a=actors.A, base={farmId:f.cl.id,campaignId:f.ccl.id};
  Object.defineProperty(base,'parcels',{enumerable:true,get:()=>[share(f.pcl)]});
  // A = 04:00Z, the first instant of local 6 Sep: nothing of 6 Sep is before it; all of 5 Sep is.
  await rejected(()=>v3(a,dv({...base,deliveryDate:'2026-09-06',originCreatedAt:'2026-09-05T20:00:00Z'})),'42501','sync review: AFTER_ARCHIVE');
  ok(await v3(a,dv({...base,deliveryDate:'2026-09-05',originCreatedAt:'2026-09-05T20:00:00Z'})));
  ok(await v3(a,dv({...base,deliveryDate:'2026-09-05',deliveryTime:'23:30',originCreatedAt:'2026-09-06T03:40:00Z'})));
  await rejected(()=>v3(a,dv({...base,deliveryDate:'2026-09-06',deliveryTime:'00:30'})),'22023','sync review: DELIVERY_TIME_NONEXISTENT');
  await rejected(()=>v3(a,dv({...base,deliveryDate:'2026-04-04',deliveryTime:'23:30'})),'22023','sync review: DELIVERY_TIME_AMBIGUOUS');
});

test('T9-T11 restore in another farm: history stays with F1, new facts go to F2, nothing is reassigned',async()=>{
  const a=actors.A, m=f.move;   // F1 until 1 Jun 10:00Z, archived, F2 from 10 Jun 10:00Z
  const t9=dv({deliveryDate:'2026-05-20',originCreatedAt:'2026-05-20T18:00:00Z',parcels:[share(m)]});
  ok(await v3(a,t9));
  ok(await e2(a,ex({parcelId:m.id,expenseDate:'2026-05-20',originCreatedAt:'2026-05-20T18:00:00Z'})));
  // T9b: the F1 period has closed; a fact first recorded after that closure goes to review.
  await rejected(()=>v3(a,dv({deliveryDate:'2026-05-21',originCreatedAt:'2026-06-05T00:00:00Z',parcels:[share(m)]})),
    '42501','sync review: ORIGIN_NOT_BEFORE_CLOSURE');
  // T10: after the restore, against F1.
  await rejected(()=>v3(a,dv({deliveryDate:'2026-06-15',originCreatedAt:'2026-06-15T18:00:00Z',parcels:[share(m)]})),
    '42501','sync review: NOT_MEMBER_ON_DATE');
  // T11: against F2.
  const south={farmId:f.south.id,campaignId:f.cs.id};
  ok(await v3(a,dv({...south,deliveryDate:'2026-06-15',originCreatedAt:'2026-06-15T18:00:00Z',parcels:[share(m)]})));
  ok(await v3(a,dv({...south,deliveryDate:'2026-06-10',originCreatedAt:'2026-06-10T18:00:00Z',parcels:[share(m)]})));
  // In the archived gap neither farm holds the parcel.
  await rejected(()=>v3(a,dv({...south,deliveryDate:'2026-06-05',originCreatedAt:'2026-06-05T18:00:00Z',parcels:[share(m)]})),
    '42501','sync review: NOT_MEMBER_ON_DATE');
  assert.equal(sql(`SELECT farm_id FROM public.deliveries WHERE id=${q(t9.id)};`),f.north.id);
});

test('T12 incongruent tombstones are never accepted',async()=>{
  for(const p of [f.bad1,f.bad2,f.bad3]) {
    await rejected(()=>v3(actors.A,dv({deliveryDate:'2026-09-01',originCreatedAt:'2026-09-01T18:00:00Z',parcels:[share(p)]})),
      '42501','sync review: PARCEL_INCOHERENT');
    await rejected(()=>e2(actors.A,ex({parcelId:p.id,expenseDate:'2026-09-01',originCreatedAt:'2026-09-01T18:00:00Z'})),
      '42501','sync review: PARCEL_INCOHERENT');
  }
});

test('T13 campaign period and scope are checked only by the new writers',async()=>{
  const a=actors.A;
  await rejected(()=>v3(a,dv({deliveryDate:'2025-09-15',originCreatedAt:'2025-09-15T18:00:00Z'})),'22023','sync review: CAMPAIGN_PERIOD');
  await rejected(()=>v3(a,dv({campaignId:f.cnOpen.id,deliveryDate:'2026-10-14'})),'22023','sync review: CAMPAIGN_PERIOD');
  await rejected(()=>e2(a,ex({expenseDate:'2025-09-15',originCreatedAt:'2025-09-15T18:00:00Z'})),'22023','sync review: CAMPAIGN_PERIOD');
  await rejected(()=>v3(a,dv({campaignId:f.cs.id})),'22023','sync invalid: CAMPAIGN_SCOPE');
  await rejected(()=>e2(a,ex({campaignId:f.cs.id})),'22023','sync invalid: CAMPAIGN_SCOPE');
  ok(await v2(a,legacy(dv({deliveryDate:'2025-09-15'}))),{pv:2});
});

test('T14 expenses: farm mandatory, parcel and campaign optional, closed shape and ledger amounts',async()=>{
  const a=actors.A;
  const e=ex({campaignId:null,parcelId:null,expenseDate:'2026-10-20',originCreatedAt:'2026-10-21T09:00:00Z'});
  ok(await e2(a,e),{pv:2});
  assert.equal(sql(`SELECT farm_id||'|'||coalesce(campaign_id::text,'-')||'|'||coalesce(parcel_id::text,'-')||'|'||origin FROM public.expenses WHERE id=${q(e.id)};`),
    `${f.north.id}|-|-|MANUAL`);
  ok(await e2(a,ex({parcelId:null,expenseDate:'2026-10-20'})));
  ok(await e2(a,ex({status:'DRAFT',amountMinor:0})));
  await rejected(()=>e2(a,ex({farmId:null})),'22023','sync invalid: SHAPE');
  await rejected(()=>e2(a,ex({amountMinor:0})),'22023','sync invalid: AMOUNT');
  await rejected(()=>e2(a,ex({amountMinor:-5})),'22023','sync invalid: SHAPE');
  await rejected(()=>e2(a,ex({status:'PROPOSED'})),'22023','sync invalid: SHAPE');
  await rejected(()=>e2(a,ex({currency:'eur'})),'22023','sync invalid: SHAPE');
  await rejected(()=>e2(a,{...ex(),extra:1}),'22023','sync invalid: SHAPE');
  await rejected(()=>v3(a,{...dv(),extra:1}),'22023','sync invalid: SHAPE');
  await rejected(()=>v3(a,dv({deliveryTime:'9:00'})),'22023','sync invalid: SHAPE');
  await rejected(()=>v3(a,dv({netGrams:900})),'22023','sync invalid: WEIGHTS');
});

test('A1-A4 authority: owner of the workspace only, fail-closed identity, dead scope',async()=>{
  const {A,C,M}=actors;
  // A1: another account; a workspace member with read+write; A2: a G0 grant with manage_all.
  await rejected(()=>v3(C,dv()),'42501','sync denied: SCOPE');
  await rejected(()=>e2(C,ex()),'42501','sync denied: SCOPE');
  await rejected(()=>v3(M,dv()),'42501','sync denied: SCOPE');
  await rejected(()=>e2(M,ex()),'42501','sync denied: SCOPE');
  await rejected(()=>v3(A,dv({farmId:f.cFarm.id,campaignId:f.cCampaign.id,parcels:[share(f.cParcel)]})),'42501','sync denied: SCOPE');
  // Q1: the creator of a farm who does not own the workspace keeps the legacy route only.
  const mine={farmId:f.mFarm,campaignId:f.mCampaign,parcels:[{...share({id:f.mParcel,name:'Parcela de M'})}]};
  await rejected(()=>v3(M,dv(mine)),'42501','sync denied: SCOPE');
  await rejected(()=>e2(M,ex({farmId:f.mFarm,campaignId:f.mCampaign})),'42501','sync denied: SCOPE');
  ok(await v2(M,legacy(dv(mine))),{pv:2});
  // A parcel of another workspace inside an owned farm's Pesada.
  await rejected(()=>v3(A,dv({parcels:[share(f.cParcel)]})),'42501','sync denied: PARCEL_SCOPE');
  await rejected(()=>e2(A,ex({parcelId:f.cParcel.id})),'42501','sync denied: PARCEL_SCOPE');
  // A3: deleted or archived farm; deleted workspace (restored right after).
  await rejected(()=>v3(A,dv({farmId:f.deadFarm.id,campaignId:f.deadCampaign,parcels:[share(f.act)]})),'42501','sync denied: FARM_NOT_LIVE');
  await rejected(()=>e2(A,ex({farmId:f.archFarm.id,campaignId:f.archCampaign})),'42501','sync denied: FARM_NOT_LIVE');
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.ws)};`);
  try {
    await rejected(()=>v3(A,dv()),'42501','sync denied: SCOPE');
    await rejected(()=>e2(A,ex()),'42501','sync denied: SCOPE');
  } finally { sql(`UPDATE public.workspaces SET deleted_at=NULL WHERE id=${q(f.ws)};`); }
  // A4: issuer, expiry, anonymous, wrong role.
  for(const authToken of [token(A,{issuer:'https://evil.example/auth/v1'}),
    token(A,{extra:{is_anonymous:true}}),token(A,{extra:{iss:undefined}})]) {
    await rejected(()=>v3(A,dv(),randomUUID(),authToken),'42501','sync denied: IDENTITY');
    await rejected(()=>e2(A,ex(),randomUUID(),authToken),'42501','sync denied: IDENTITY');
  }
  // Expired 1 s ago: inside the gateway's clock-skew tolerance, so the function itself must refuse it.
  await rejected(()=>v3(A,dv(),randomUUID(),token(A,{expired:true})),'42501','sync denied: IDENTITY');
  await rejected(()=>e2(A,ex(),randomUUID(),token(A,{expired:true})),'42501','sync denied: IDENTITY');
  // Expired 1 h ago: the gateway refuses it before any SQL runs, and nothing is written.
  const before=fingerprint();
  for(const call of [()=>v3(A,dv(),randomUUID(),token(A,{expired:true,expiredBy:3600})),
    ()=>e2(A,ex(),randomUUID(),token(A,{expired:true,expiredBy:3600}))]) {
    const r=await call();
    assert.equal(r.status,401,r.raw);
  }
  assert.equal(fingerprint(),before);
});

test('I1 lost response: the receipt is recoverable and a repeat returns it without a second write',async()=>{
  const a=actors.A, d=dv({deliveryDate:'2026-10-13',parcels:[share(f.arch)]}), op=randomUUID();
  f.i1={d,op};
  ok(await v3(a,d,op));      // the client never sees this answer
  const recovered=await rpc('check_sync_operations',a,{p_operation_ids:[op]});
  assert.equal(recovered.status,200,recovered.raw);
  const receipt=recovered.data.confirmed_receipts[0];
  assert.deepEqual([receipt.entity_id,receipt.entity_type,receipt.remote_version,receipt.payload_version],[d.id,'DELIVERY',1,3]);
  assert.equal(receipt.payload_hash,sql(`SELECT encode(sha256(convert_to(${q(JSON.stringify(d))}::jsonb::text,'UTF8')),'hex');`));
  const before=fingerprint();
  for(let i=0;i<3;i++) ok(await v3(a,d,op),{idempotent:true,pv:3});
  assert.equal(fingerprint(),before);
  assert.equal(sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE operation_id=${q(op)};`),'1');
  const e=ex({parcelId:f.arch.id,expenseDate:'2026-10-13'}), eop=randomUUID();
  f.i1e={e,op:eop};
  ok(await e2(a,e,eop)); ok(await e2(a,e,eop),{idempotent:true,pv:2});
  // I2: same operation, another payload (including only a different originCreatedAt).
  await rejected(()=>v3(a,{...d,netGrams:2000,parcels:[{...d.parcels[0],weightGrams:2000}]},op),'23505','sync conflict: RECEIPT_CONFLICT');
  await rejected(()=>v3(a,{...d,originCreatedAt:'2026-10-13T18:00:00.000000001Z'},op),'23505','sync conflict: RECEIPT_CONFLICT');
  await rejected(()=>e2(a,{...e,originCreatedAt:'2026-10-13T19:00:00Z'},eop),'23505','sync conflict: RECEIPT_CONFLICT');
  // One operation id never crosses entity types or writers.
  await rejected(()=>e2(a,ex(),op),'23505','sync conflict: RECEIPT_CONFLICT');
});

test('I5/I6 updates: archived references freeze the historical binding; economic fields stay correctable',async()=>{
  const a=actors.A, {d}=f.i1;
  // Weight and ticket correction on a Pesada over an archived parcel.
  const fixed={...d,netGrams:1200,ticketNumber:'T-42',parcels:[{...d.parcels[0],weightGrams:1200}],baseVersion:1};
  ok(await v3(a,fixed),{version:2});
  assert.equal(sql(`SELECT net_grams||'|'||ticket_number||'|'||(SELECT weight_grams FROM public.delivery_parcels WHERE delivery_id=d.id)
    FROM public.deliveries d WHERE id=${q(d.id)};`),'1200|T-42|1200');
  // I5: add or remove a parcel, move the date or the time, rename the snapshot, replace the child id.
  const frozen=async change=>rejected(()=>v3(a,{...fixed,baseVersion:2,...change}),'23505','sync conflict: HISTORICAL_BINDING_FROZEN');
  await frozen({netGrams:2200,parcels:[fixed.parcels[0],share(f.act)]});
  await frozen({deliveryDate:'2026-10-12'});
  await frozen({deliveryTime:'08:00'});
  await frozen({parcels:[{...fixed.parcels[0],parcelNameAtDelivery:'Otro nombre'}]});
  await frozen({parcels:[{...fixed.parcels[0],id:randomUUID()}]});
  const active=dv({deliveryDate:'2026-10-13'});
  ok(await v3(a,active));
  await rejected(()=>v3(a,{...active,baseVersion:1,netGrams:2000,parcels:[active.parcels[0],share(f.arch)]}),
    '23505','sync conflict: HISTORICAL_BINDING_FROZEN');
  // originCreatedAt is immutable across operations; stale and missing versions conflict.
  await rejected(()=>v3(a,{...fixed,baseVersion:2,originCreatedAt:'2026-10-13T17:00:00Z'}),'23505','sync conflict: ORIGIN_IMMUTABLE');
  await rejected(()=>v3(a,{...fixed,baseVersion:1}),'23505','sync conflict: STALE_VERSION');
  await rejected(()=>v3(a,{...fixed,baseVersion:null}),'23505','sync conflict: BASE_VERSION_REQUIRED');
  await rejected(()=>v3(a,{...fixed,baseVersion:2,campaignId:f.cnOpen.id}),'23505','sync conflict: BINDING_IMMUTABLE');
  // I6: expense amount, concept and status over an archived parcel; parcel, campaign or date are frozen.
  const {e}=f.i1e;
  ok(await e2(a,{...e,amountMinor:20000,concept:'Poda corregida',status:'DRAFT',baseVersion:1}),{version:2});
  assert.equal(sql(`SELECT amount_minor||'|'||concept||'|'||status||'|'||parcel_id||'|'||campaign_id||'|'||expense_date FROM public.expenses WHERE id=${q(e.id)};`),
    `20000|Poda corregida|DRAFT|${f.arch.id}|${f.cn.id}|2026-10-13`);
  for(const change of [{parcelId:null},{parcelId:f.act.id},{campaignId:null},{expenseDate:'2026-10-12'}])
    await rejected(()=>e2(a,{...e,baseVersion:2,...change}),'23505','sync conflict: HISTORICAL_BINDING_FROZEN');
  await rejected(()=>e2(a,{...e,baseVersion:2,farmId:f.south.id,campaignId:null}),'23505','sync conflict: BINDING_IMMUTABLE');
  // Without archived references, a binding change is validated in full and allowed.
  const free=ex({parcelId:f.act.id,expenseDate:'2026-10-13'}); ok(await e2(a,free));
  ok(await e2(a,{...free,parcelId:null,expenseDate:'2026-10-12',baseVersion:1}),{version:2});
  const added=ex({parcelId:null,expenseDate:'2026-10-13'}); ok(await e2(a,added));
  await rejected(()=>e2(a,{...added,parcelId:f.arch.id,baseVersion:1}),'23505','sync conflict: HISTORICAL_BINDING_FROZEN');
});

test('legacy rows: the first v3/v2 update adopts a coherent originCreatedAt once; an incoherent one goes to review',async()=>{
  const a=actors.A, d=f.legDelivery;
  const version=Number(sql(`SELECT version FROM public.deliveries WHERE id=${q(d.id)};`));
  // The server saw the Pesada on 10 Oct 20:00Z: the device cannot have created it on 11 Oct.
  await rejected(()=>v3(a,{...d,baseVersion:version,netGrams:1000,originCreatedAt:'2026-10-11T00:00:00Z'}),'22023','sync review: ORIGIN_INCOHERENT');
  ok(await v3(a,{...d,baseVersion:version,ticketNumber:'L-1',originCreatedAt:'2026-10-10T18:00:00Z'}),{version:version+1});
  assert.equal(origin(d.id),'2026-10-10T18:00:00Z|true');
  await rejected(()=>v3(a,{...d,baseVersion:version+1,originCreatedAt:'2026-10-10T17:00:00Z'}),'23505','sync conflict: ORIGIN_IMMUTABLE');
  const e=f.legExpense, ev=Number(sql(`SELECT version FROM public.expenses WHERE id=${q(e.id)};`));
  ok(await e2(a,{...e,baseVersion:ev,amountMinor:999,originCreatedAt:'2026-10-10T18:00:00Z'}),{version:ev+1});
  assert.equal(origin(e.id),'2026-10-10T18:00:00Z|true');
  // fact_origins is append-only even for the database owner.
  assert.throws(()=>sql(`UPDATE magina_g2_private.fact_origins SET origin_created_at_exact='x' WHERE entity_id=${q(e.id)};`),/fact origin is immutable/);
  assert.throws(()=>sql(`DELETE FROM magina_g2_private.fact_origins WHERE entity_id=${q(e.id)};`),/fact origin is immutable/);
});

test('I3 repeat after revocation returns the same receipt to the same actor only, with no new write',async()=>{
  const {A,M}=actors, {d,op}=f.i1, {e,op:eop}=f.i1e;
  sql(`UPDATE public.workspaces SET owner_user_id=${q(M.id)} WHERE id=${q(f.ws)};`);
  try {
    const before=fingerprint();
    ok(await v3(A,d,op),{idempotent:true,pv:3});
    ok(await e2(A,e,eop),{idempotent:true,pv:2});
    assert.equal(fingerprint(),before);
    // The new owner does not get A's receipt through the same operation id (receipts are per actor):
    // the call is evaluated as M's own new write and refused on its merits.
    await rejected(()=>v3(M,d,op),'23505','sync conflict: BASE_VERSION_REQUIRED');
    await rejected(()=>v3(A,dv()),'42501','sync denied: SCOPE');
    await rejected(()=>e2(A,ex()),'42501','sync denied: SCOPE');
  } finally { sql(`UPDATE public.workspaces SET owner_user_id=${q(A.id)} WHERE id=${q(f.ws)};`); }
});

test('A5 revocation at commit: an ownership transfer committed while the writer waits denies the write',async()=>{
  const {A,M}=actors;
  const h=holder(A,`RESET ROLE; UPDATE public.workspaces SET owner_user_id=${q(M.id)} WHERE id=${q(f.ws)};`);
  const before=fingerprint();
  const [delivery,expense]=await race(h,[()=>v3(A,dv()),()=>e2(A,ex())]);
  try {
    assert.equal(delivery.status,403,delivery.raw); assert.equal(delivery.data.message,'sync denied: SCOPE');
    assert.equal(expense.status,403,expense.raw); assert.equal(expense.data.message,'sync denied: SCOPE');
    assert.equal(fingerprint(),before);
  } finally { sql(`UPDATE public.workspaces SET owner_user_id=${q(A.id)} WHERE id=${q(f.ws)};`); }
});

test('I4 ARCHIVE vs writer, both orders: serialized on the parcel lock and decided on committed state',async()=>{
  const A=actors.A, at='2026-10-20T12:00:00Z';
  // Writer first: the Pesada commits, then the waiting archive succeeds (a CLOSED campaign never blocks it).
  const first=dv({deliveryDate:'2026-10-19',originCreatedAt:'2026-10-19T18:00:00Z',parcels:[share(f.race1)]});
  const hw=holder(A,`SELECT public.sync_own_delivery_v3(${q(randomUUID())},${q(JSON.stringify(first))}::jsonb);`);
  const [archived]=await race(hw,[()=>rpc('archive_own_parcel_v1',A,archiveBody(f.race1,at))]);
  assert.equal(archived.status,200,archived.raw); f.race1.version++;
  assert.equal(sql(`SELECT count(*) FROM public.delivery_parcels WHERE delivery_id=${q(first.id)} AND parcel_id=${q(f.race1.id)};`),'1');
  // Archive first: the waiting writers see A once it commits.
  const ha=holder(A,`SELECT public.archive_own_parcel_v1(${q(randomUUID())},${q(f.race2.id)},1,
    ${q(JSON.stringify({archivedAt:at,closeMembershipId:f.race2.membership}))}::jsonb);`);
  const late=dv({deliveryDate:'2026-10-21',originCreatedAt:'2026-10-21T18:00:00Z',parcels:[share(f.race2)]});
  const early=dv({deliveryDate:'2026-10-19',originCreatedAt:'2026-10-19T18:00:00Z',parcels:[share(f.race2)]});
  const [lateR,earlyR]=await race(ha,[()=>v3(A,late),()=>v3(A,early)]);
  f.race2.version++;
  assert.equal(lateR.status,403,lateR.raw); assert.equal(lateR.data.message,'sync review: AFTER_ARCHIVE');
  ok(earlyR);
  assert.equal(sql(`SELECT count(*) FROM public.deliveries WHERE id=${q(late.id)};`),'0');
});

test('I4 RESTORE vs writer, both orders: the new period is honoured only once committed',async()=>{
  const A=actors.A;
  // Restore first (race2, archived): a Pesada against F2 after the restore waits and is then accepted.
  const openId=randomUUID(), r2='2026-10-22T10:00:00Z';
  const hr=holder(A,`SELECT public.restore_own_parcel_v1(${q(randomUUID())},${q(f.race2.id)},${f.race2.version},
    ${q(JSON.stringify({restoredAt:r2,openMembership:{id:openId,farmId:f.south.id,validFrom:r2}}))}::jsonb);`);
  const after=dv({farmId:f.south.id,campaignId:f.cs.id,deliveryDate:'2026-10-23',originCreatedAt:'2026-10-23T18:00:00Z',parcels:[share(f.race2)]});
  const [afterR]=await race(hr,[()=>v3(A,after)]);
  ok(afterR); f.race2.version++;
  // Writer first (race1, archived): a historical Pesada against F1 commits, then the waiting restore to F2 succeeds.
  const hist=dv({deliveryDate:'2026-10-18',originCreatedAt:'2026-10-18T18:00:00Z',parcels:[share(f.race1)]});
  const hw=holder(A,`SELECT public.sync_own_delivery_v3(${q(randomUUID())},${q(JSON.stringify(hist))}::jsonb);`);
  const [restored]=await race(hw,[()=>rpc('restore_own_parcel_v1',A,restoreBody(f.race1,f.south,'2026-10-22T11:00:00Z',randomUUID()))]);
  assert.equal(restored.status,200,restored.raw);
  assert.equal(sql(`SELECT farm_id FROM public.deliveries WHERE id=${q(hist.id)};`),f.north.id);
});

test('HTTPS: pinned TLS endpoint carries v3/v2 create, lost-response repeat and recovery; an unpinned client is refused',async()=>{
  const A=actors.A, authToken=token(A);
  const d=dv({deliveryDate:'2026-10-14',parcels:[share(f.arch)]}), op=randomUUID();
  const created=await viaTls('/rest/v1/rpc/sync_own_delivery_v3',authToken,{p_operation_id:op,p_delivery:d});
  assert.equal(created.status,200,created.raw); assert.equal(created.data.idempotent,false);
  assert.match(created.protocol??'',/^TLSv1\.[23]$/);
  const again=await viaTls('/rest/v1/rpc/sync_own_delivery_v3',authToken,{p_operation_id:op,p_delivery:d});
  assert.equal(again.status,200); assert.equal(again.data.idempotent,true);
  assert.equal(again.data.remote_version,created.data.remote_version);
  const e=ex({parcelId:f.arch.id}), eop=randomUUID();
  assert.equal((await viaTls('/rest/v1/rpc/sync_own_expense_v2',authToken,{p_operation_id:eop,p_expense:e})).status,200);
  const recovered=await viaTls('/rest/v1/rpc/check_sync_operations',authToken,{p_operation_ids:[op,eop]});
  assert.deepEqual(recovered.data.confirmed_receipts.map(x=>`${x.entity_type}:${x.payload_version}`).sort(),['DELIVERY:3','EXPENSE:2']);
  const late=await viaTls('/rest/v1/rpc/sync_own_delivery_v3',authToken,{p_operation_id:randomUUID(),
    p_delivery:dv({deliveryDate:'2026-10-16',originCreatedAt:'2026-10-15T08:00:00Z',parcels:[share(f.arch)]})});
  assert.equal(late.status,403); assert.equal(late.data.message,'sync review: AFTER_ARCHIVE');
  await assert.rejects(()=>viaTls('/rest/v1/rpc/sync_own_delivery_v3',authToken,{p_operation_id:randomUUID(),p_delivery:dv()},{ca:null}),
    error=>/self[- ]signed|certificate/i.test(String(error.code)+String(error.message)));
});
