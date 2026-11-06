// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-parent-receive-v2.sql (G2.6 2c-1, #810 D1/D2); tests run in file order.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const work = process.env.G26_PAR2_WORK;
const project = process.env.G26_PAR2_PROJECT;
assert.match(project ?? '', /^mg26-par2-[0-9]+-[0-9]+$/);
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
  const started = Date.now();
  const response = await fetch(api + path, {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data,raw,ms:Date.now()-started};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor, issuer=ISSUER, expired=false, extra={}) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:issuer,
    exp:expired?Math.floor(Date.now()/1000)-1:claims.exp,...extra})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const rpc=(name,actor,body,authToken=token(actor))=>http(`/rest/v1/rpc/${name}`,{token:authToken,body});
const v2=(actor,ws,authToken)=>rpc('get_own_workspace_parents_v2',actor,{p_workspace_id:ws},authToken);
const v1=(actor,ws)=>rpc('get_own_workspace_parents_v1',actor,{p_workspace_id:ws});

// Real producers of the history: #798 workspace, #799 farm, #800 parcel, #805 archive/restore.
const workspace=async actor=>{
  const id=randomUUID();
  const created=await rpc('create_own_workspace',actor,{p_operation_id:randomUUID(),p_workspace:{id,name:'Olivar QA',
    countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return id;
};
const farm=async(actor,ws,name)=>{
  const id=randomUUID(), req={payloadVersion:1,operationId:randomUUID(),farmId:id,localWorkspaceId:ws,
    consentId:randomUUID(),consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const reserved=await rpc('reserve_local_farm_g1c',actor,{p_request:req,p_remote_workspace:ws});
  assert.equal(reserved.status,200,JSON.stringify(reserved.data));
  const created=await rpc('create_reserved_farm',actor,{p_operation_id:randomUUID(),p_reservation_operation_id:req.operationId,
    p_reservation_receipt_id:reserved.data.receiptId,p_farm:{id,workspaceId:ws,name,description:null,
      municipality:'Bedmar',province:'Jaén',notes:null,status:'ACTIVE',coverDocumentId:null}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return id;
};
const START='2026-01-01T00:00:00.007Z';
const parcel=async(actor,ws,farmId,name)=>{
  const id=randomUUID(), membership=randomUUID();
  const created=await rpc('create_own_parcel',actor,{p_operation_id:randomUUID(),p_parcel:{id,workspaceId:ws,status:'ACTIVE',
    displayName:name,cadastralReference:null,cadastralPolygon:null,cadastralParcel:null,municipality:'Bedmar',province:'Jaén',
    source:'MANUAL',geometryGeoJson:null,sourceProvider:null,sourceImportedAt:null,cadastralAreaM2:null,managedAreaM2:1234.5,
    notes:null,oliveTreeCount:120,variety:'Picual',irrigationSystem:null,irrigationNetwork:null,irrigationSector:null,
    irrigationDays:null,memberships:[{id:membership,farmId,validFrom:START,validUntil:null,deletedAt:null}]}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return {id,membership};
};
const archive=async(actor,p,version,at)=>{
  const r=await rpc('archive_own_parcel_v1',actor,{p_operation_id:randomUUID(),p_parcel_id:p.id,p_expected_version:version,
    p_archive:{archivedAt:at,closeMembershipId:p.membership}});
  assert.equal(r.status,200,JSON.stringify(r.data));
};
const restore=async(actor,p,version,farmId,at,openId)=>{
  const r=await rpc('restore_own_parcel_v1',actor,{p_operation_id:randomUUID(),p_parcel_id:p.id,p_expected_version:version,
    p_restore:{restoredAt:at,openMembership:{id:openId,farmId,validFrom:at}}});
  assert.equal(r.status,200,JSON.stringify(r.data));
};

const V1_PARCEL_KEYS=['id','workspaceId','displayName','cadastralReference','cadastralPolygon','cadastralParcel','municipality',
  'province','source','geometryGeoJson','sourceProvider','sourceImportedAt','cadastralAreaM2','managedAreaM2','notes','status',
  'oliveTreeCount','variety','irrigationSystem','irrigationNetwork','irrigationSector','irrigationDays','createdAt','updatedAt','version'];
const ARCH2='2026-03-01T10:00:00.123456789Z';
const ARCH3='2026-03-02T08:00:00.000000001Z';
const RESTORE3='2026-04-01T09:30:00.5Z';
const actors={};
const f={};
const sorted=values=>[...values].sort();
/** Rejected whole: never 200, the expected cause, and no partial graph in the body. */
const rejected=(response,cause)=>{
  assert.notEqual(response.status,200,response.raw.slice(0,300));
  assert.match(response.raw,cause);
  assert.ok(!response.raw.includes('"parcels"'),'no partial graph');
};
const fingerprint=()=>sql(`SELECT md5(
  coalesce((SELECT string_agg(id||'|'||version||'|'||status||'|'||coalesce(deleted_at::text,'-')||'|'||updated_at,',' ORDER BY id) FROM public.parcels),'')||
  coalesce((SELECT string_agg(id||'|'||version||'|'||coalesce(valid_until::text,'-')||'|'||updated_at,',' ORDER BY id) FROM public.farm_parcel_memberships),'')||
  coalesce((SELECT string_agg(id||'|'||version||'|'||updated_at,',' ORDER BY id) FROM public.farms),'')||
  (SELECT count(*) FROM magina_g2_private.sync_operations)::text);`);

before(async()=>{
  for(const label of ['A','C','M']) {
    const email=`g26-par2-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}});
    assert.equal(created.status,200,JSON.stringify(created.data));
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
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql','g26-parcel-mutation-v1.sql','g26-parent-receive-v2.sql']
      .map(name=>new URL(`../../proposals/${name}`,import.meta.url))];
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    if((await v2(actors.A,randomUUID())).status!==404) break;
    if(attempt===39) throw new Error('PostgREST did not expose get_own_workspace_parents_v2');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  const a=actors.A;
  f.ws=await workspace(a);
  f.north=await farm(a,f.ws,'Finca Norte');
  f.south=await farm(a,f.ws,'Finca Sur');
  f.p1=await parcel(a,f.ws,f.north,'Activa');
  f.p2=await parcel(a,f.ws,f.north,'Archivada');
  await archive(a,f.p2,1,ARCH2);
  f.p3=await parcel(a,f.ws,f.north,'Restaurada');
  await archive(a,f.p3,1,ARCH3);
  f.p3open=randomUUID();
  await restore(a,f.p3,2,f.south,RESTORE3,f.p3open);
  // A legacy archive without the exact Android instant: archivedAt falls back to deleted_at.
  f.p4=randomUUID(); f.p4m=randomUUID();
  sql(`INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(f.p4)},${q(f.ws)},'Heredada');
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until)
      VALUES (${q(f.p4m)},${q(f.ws)},${q(f.north)},${q(f.p4)},'2025-01-01T00:00:00Z','2025-12-31T23:59:59.999999Z');
    UPDATE public.parcels SET status='ARCHIVED',deleted_at='2025-12-31T23:59:59.999999Z',version=2 WHERE id=${q(f.p4)};`);
  f.foreignWs=await workspace(actors.C);
  f.foreignFarm=await farm(actors.C,f.foreignWs,'Finca C');
  f.foreignParcel=await parcel(actors.C,f.foreignWs,f.foreignFarm,'Parcela C');
});

test('privileges, definer settings and v1 unchanged (archived parcels stay hidden there)',async()=>{
  assert.equal(sql(`SELECT has_function_privilege('anon','public.get_own_workspace_parents_v2(uuid)','EXECUTE'),
    has_function_privilege('authenticated','public.get_own_workspace_parents_v2(uuid)','EXECUTE');`),'f|t');
  assert.equal(sql(`SELECT prosecdef::text||'|'||provolatile::text||'|'||array_to_string(proconfig,',') FROM pg_proc
    WHERE proname='get_own_workspace_parents_v2';`),'true|s|search_path=""');
  const old=await v1(actors.A,f.ws);
  assert.equal(old.status,200,old.raw.slice(0,300));
  assert.equal(old.data.contract_version,1);
  assert.deepEqual(old.data.parcels.map(x=>x.id),sorted([f.p1.id,f.p3.id]));
  assert.ok(old.data.parcels.every(x=>!('archivedAt' in x)));
});

test('owner receives ACTIVE and ARCHIVED parcels with exact archivedAt and the full membership history',async()=>{
  const r=await v2(actors.A,f.ws);
  assert.equal(r.status,200,r.raw.slice(0,300));
  const d=r.data;
  assert.deepEqual(Object.keys(d).sort(),['contract_version','farms','issuer','memberships','parcels','subject','workspace']);
  assert.equal(d.contract_version,2); assert.equal(d.issuer,ISSUER); assert.equal(d.subject,actors.A.id);
  assert.equal(d.workspace.id,f.ws);
  assert.deepEqual(d.farms.map(x=>x.id),sorted([f.north,f.south]));
  assert.ok(d.farms.every(x=>x.status==='ACTIVE'&&x.archivedAt===null));
  assert.deepEqual(d.parcels.map(x=>x.id),sorted([f.p1.id,f.p2.id,f.p3.id,f.p4]));
  for(const p of d.parcels) assert.deepEqual(Object.keys(p).sort(),[...V1_PARCEL_KEYS,'archivedAt'].sort());
  const byId=Object.fromEntries(d.parcels.map(p=>[p.id,p]));
  assert.deepEqual([byId[f.p1.id].status,byId[f.p1.id].archivedAt,byId[f.p1.id].version],['ACTIVE',null,1]);
  assert.deepEqual([byId[f.p2.id].status,byId[f.p2.id].archivedAt,byId[f.p2.id].version],['ARCHIVED',ARCH2,2]);
  assert.deepEqual([byId[f.p3.id].status,byId[f.p3.id].archivedAt,byId[f.p3.id].version],['ACTIVE',null,3]);
  assert.equal(byId[f.p4].status,'ARCHIVED');
  assert.equal(byId[f.p4].archivedAt,sql(`SELECT to_jsonb(deleted_at)#>>'{}' FROM public.parcels WHERE id=${q(f.p4)};`));
  const of=id=>d.memberships.filter(m=>m.parcelId===id);
  assert.deepEqual(of(f.p1.id).map(m=>[m.id,m.farmId,m.validFrom,m.validUntil,m.version]),[[f.p1.membership,f.north,START,null,1]]);
  assert.deepEqual(of(f.p2.id).map(m=>[m.id,m.farmId,m.validFrom,m.validUntil,m.version]),[[f.p2.membership,f.north,START,ARCH2,2]]);
  assert.deepEqual(sorted(of(f.p3.id).map(m=>JSON.stringify([m.id,m.farmId,m.validFrom,m.validUntil,m.version]))),
    sorted([JSON.stringify([f.p3.membership,f.north,START,ARCH3,2]),JSON.stringify([f.p3open,f.south,RESTORE3,null,1])]));
  assert.equal(of(f.p4).length,1);
  // The #805 invariant holds for every returned parcel.
  for(const p of d.parcels) assert.equal(of(p.id).filter(m=>m.validUntil===null).length,p.status==='ACTIVE'?1:0,p.id);
  assert.ok(d.memberships.every(m=>m.workspaceId===f.ws&&d.farms.some(x=>x.id===m.farmId)));
  assert.deepEqual(d.memberships.map(m=>m.id),sorted(d.memberships.map(m=>m.id)),'deterministic order');
  for(const id of [f.foreignWs,f.foreignFarm,f.foreignParcel.id,f.foreignParcel.membership]) assert.ok(!r.raw.includes(id));
});

test('repeated reads are byte-for-byte identical and the read never writes',async()=>{
  const before=fingerprint();
  const first=await v2(actors.A,f.ws), second=await v2(actors.A,f.ws);
  assert.equal(first.status,200); assert.equal(second.raw,first.raw);
  assert.equal(fingerprint(),before);
});

test('authorization is fail-closed: foreign, unknown, issuer, expiry, anonymous, member grant and deleted workspace',async()=>{
  const a=actors.A,c=actors.C,m=actors.M;
  assert.equal((await v2(c,f.ws)).status,403);
  assert.equal((await v2(a,f.foreignWs)).status,403);
  assert.equal((await v2(a,randomUUID())).status,403);
  assert.equal((await v2(a,f.ws,token(a,'https://other.example/auth/v1'))).status,403);
  assert.ok([401,403].includes((await v2(a,f.ws,token(a,ISSUER,true))).status));
  assert.equal((await v2(a,f.ws,token(a,ISSUER,false,{is_anonymous:true}))).status,403);
  assert.ok([401,403].includes((await http('/rest/v1/rpc/get_own_workspace_parents_v2',{body:{p_workspace_id:f.ws}})).status));
  assert.equal((await v2(m,f.ws)).status,403);
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
       VALUES (${q(f.ws)},${q(m.id)},'VIEWER',ARRAY['read']);`);
  const member=await v2(m,f.ws);
  assert.equal(member.status,200,member.raw.slice(0,300));
  assert.equal(member.data.subject,m.id);
  assert.ok(member.data.parcels.some(p=>p.id===f.p2.id&&p.status==='ARCHIVED'),'a reader sees the archived history too');
  const grant=`WHERE workspace_id=${q(f.ws)} AND user_id=${q(m.id)}`;
  sql(`UPDATE public.workspace_memberships SET status='SUSPENDED',version=version+1 ${grant};`);
  assert.equal((await v2(m,f.ws)).status,403);
  sql(`UPDATE public.workspace_memberships SET status='ACTIVE',valid_from=now()-interval '2 days',valid_until=now()-interval '1 day',version=version+1 ${grant};`);
  assert.equal((await v2(m,f.ws)).status,403);
  sql(`UPDATE public.workspace_memberships SET valid_until=NULL,version=version+1 ${grant};`);
  assert.equal((await v2(m,f.ws)).status,200);
  sql(`UPDATE public.workspace_memberships SET status='REVOKED',revoked_at=now(),version=version+1 ${grant};`);
  assert.equal((await v2(m,f.ws)).status,403);
  assert.equal((await v2(c,f.foreignWs)).status,200);
  const doomed=await workspace(c);
  assert.equal((await v2(c,doomed)).status,200);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(doomed)};`);
  assert.equal((await v2(c,doomed)).status,403);
});

test('accounts stay isolated: RLS hides the archived history from another account and v2 leaks nothing',async()=>{
  const c=actors.C;
  for(const path of [`/rest/v1/parcels?id=eq.${f.p2.id}`,`/rest/v1/farm_parcel_memberships?parcel_id=eq.${f.p3.id}`]) {
    const direct=await http(path,{token:token(c),method:'GET'});
    assert.ok(direct.status!==200||(Array.isArray(direct.data)&&direct.data.length===0),`${path}: ${direct.raw.slice(0,200)}`);
  }
  const own=await v2(c,f.foreignWs);
  assert.equal(own.status,200);
  for(const id of [f.ws,f.north,f.south,f.p1.id,f.p2.id,f.p3.id,f.p4]) assert.ok(!own.raw.includes(id));
});

test('incomplete or invariant-breaking graphs are rejected whole, never clipped',async()=>{
  const a=actors.A, w=randomUUID(), farmLive=randomUUID(), x=randomUUID(), xm=randomUUID();
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(w)},${q(a.id)},'Errores');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(farmLive)},${q(w)},${q(a.id)},${q(a.id)},'Viva');
    INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(x)},${q(w)},'Base');
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from) VALUES (${q(xm)},${q(w)},${q(farmLive)},${q(x)},'2026-01-01T00:00:00Z');`);
  const base=await v2(a,w);
  assert.equal(base.status,200,base.raw.slice(0,300));
  const cases=[
    ['ACTIVE without an open period', y=>`INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(y)},${q(w)},'Sin periodo');`],
    ['legacy soft-deleted ACTIVE', y=>`INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(y)},${q(w)},'Borrada');
       INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from) VALUES (gen_random_uuid(),${q(w)},${q(farmLive)},${q(y)},'2026-01-01T00:00:00Z');
       UPDATE public.parcels SET deleted_at=now() WHERE id=${q(y)};`],
    ['ARCHIVED without deleted_at', y=>`INSERT INTO public.parcels(id,workspace_id,display_name,status) VALUES (${q(y)},${q(w)},'Sin fecha','ARCHIVED');`],
    ['ARCHIVED with an open period', y=>`INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(y)},${q(w)},'Abierta');
       INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from) VALUES (gen_random_uuid(),${q(w)},${q(farmLive)},${q(y)},'2026-01-01T00:00:00Z');
       UPDATE public.parcels SET status='ARCHIVED',deleted_at=now() WHERE id=${q(y)};`],
  ];
  for(const [label,insert] of cases) {
    const y=randomUUID();
    sql(insert(y));
    rejected(await v2(a,w),/parcel invariant/);
    sql(`DELETE FROM public.farm_parcel_memberships WHERE parcel_id=${q(y)}; DELETE FROM public.parcels WHERE id=${q(y)};`);
    assert.equal((await v2(a,w)).status,200,`${label}: cleanup`);
  }
  // History hole: a closed period whose farm was deleted afterwards.
  const gone=randomUUID(), y=randomUUID();
  sql(`INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(gone)},${q(w)},${q(a.id)},${q(a.id)},'Se borra');
    INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(y)},${q(w)},'Historia');
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until)
      VALUES (gen_random_uuid(),${q(w)},${q(gone)},${q(y)},'2025-01-01T00:00:00Z','2025-06-01T00:00:00Z');
    UPDATE public.parcels SET status='ARCHIVED',deleted_at='2025-06-01T00:00:00Z' WHERE id=${q(y)};`);
  assert.equal((await v2(a,w)).status,200,'a closed history over a live farm is valid');
  // A deletion (deleted_at with status ACTIVE) is not an archive: the history would have a hole.
  sql(`UPDATE public.farms SET deleted_at=now() WHERE id=${q(gone)};`);
  rejected(await v2(a,w),/membership farm/);
  sql(`UPDATE public.farms SET deleted_at=NULL WHERE id=${q(gone)};`);
  // ARCHIVED without its instant is incoherent: B could not tell it from a live farm.
  sql(`UPDATE public.farms SET status='ARCHIVED' WHERE id=${q(gone)};`);
  rejected(await v2(a,w),/farm invariant/);
  // A real archive (status and deleted_at, as Android writes it) keeps the history readable.
  sql(`UPDATE public.farms SET deleted_at='2026-05-01T12:00:00.123456Z' WHERE id=${q(gone)};`);
  const archivedFarm=await v2(a,w);
  assert.equal(archivedFarm.status,200,archivedFarm.raw.slice(0,300));
  const archivedRow=archivedFarm.data.farms.find(x=>x.id===gone);
  assert.deepEqual([archivedRow.status,archivedRow.archivedAt],['ARCHIVED','2026-05-01T12:00:00.123456+00:00']);
  sql(`UPDATE public.farms SET status='PENDING_REVIEW' WHERE id=${q(farmLive)};`);
  rejected(await v2(a,w),/unsupported status/);
  sql(`UPDATE public.farms SET status='ACTIVE' WHERE id=${q(farmLive)};`);
  assert.equal((await v2(a,w)).status,200);
});

/**
 * #812 audit: a farm archived for real (status ARCHIVED and deleted_at, exactly what
 * OfflineFirstFarmRepository.archive writes; no server farm-archive RPC exists yet) over a history
 * produced by the real #799/#800/#805 RPCs: archive + restore of a parcel, and a parcel left open in it.
 */
test('a really archived farm keeps the history: exact graph, v1 comparison, isolation, restore and rejections',async()=>{
  const a=actors.A, c=actors.C;
  const ws=await workspace(a), oldFarm=await farm(a,ws,'Finca vieja'), newFarm=await farm(a,ws,'Finca nueva');
  const moved=await parcel(a,ws,oldFarm,'Trasladada'), stays=await parcel(a,ws,oldFarm,'Se queda');
  const ARCH='2026-04-10T07:15:00.250000001Z', BACK='2026-04-20T18:45:00.75Z', FARM_ARCH='2026-05-01T12:00:00.123456Z';
  await archive(a,moved,1,ARCH);
  const reopened=randomUUID();
  await restore(a,moved,2,newFarm,BACK,reopened);
  sql(`UPDATE public.farms SET status='ARCHIVED',deleted_at=${q(FARM_ARCH)},version=version+1,updated_at=now() WHERE id=${q(oldFarm)};`);

  const r=await v2(a,ws);
  assert.equal(r.status,200,r.raw.slice(0,300));
  const farms=Object.fromEntries(r.data.farms.map(x=>[x.id,x]));
  assert.deepEqual(Object.keys(farms).sort(),sorted([oldFarm,newFarm]));
  for(const x of r.data.farms) assert.deepEqual(Object.keys(x).sort(),['archivedAt','createdAt','description','id','municipality',
    'name','notes','province','status','updatedAt','version','workspaceId']);
  assert.deepEqual([farms[oldFarm].status,farms[oldFarm].archivedAt,farms[oldFarm].version],
    ['ARCHIVED','2026-05-01T12:00:00.123456+00:00',2]);
  assert.deepEqual([farms[newFarm].status,farms[newFarm].archivedAt,farms[newFarm].version],['ACTIVE',null,1]);
  const parcels=Object.fromEntries(r.data.parcels.map(p=>[p.id,p]));
  assert.deepEqual([parcels[moved.id].status,parcels[moved.id].archivedAt,parcels[moved.id].version],['ACTIVE',null,3]);
  assert.deepEqual([parcels[stays.id].status,parcels[stays.id].version],['ACTIVE',1]);
  const rows=r.data.memberships.map(m=>JSON.stringify([m.id,m.farmId,m.parcelId,m.validFrom,m.validUntil,m.version])).sort();
  assert.deepEqual(rows,[
    JSON.stringify([moved.membership,oldFarm,moved.id,START,ARCH,2]),
    JSON.stringify([reopened,newFarm,moved.id,BACK,null,1]),
    // Archive is not a cascade: the period of a parcel left in the archived farm stays open.
    JSON.stringify([stays.membership,oldFarm,stays.id,START,null,1]),
  ].sort());

  // v1 (unchanged) hides the archived farm and silently drops its periods: the gap v2 closes.
  const old=await v1(a,ws);
  assert.equal(old.status,200);
  assert.deepEqual(old.data.farms.map(x=>x.id),[newFarm]);
  assert.ok(old.data.memberships.every(m=>m.farmId!==oldFarm));
  assert.ok(!old.data.memberships.some(m=>m.parcelId===stays.id));

  const before=fingerprint();
  assert.equal((await v2(a,ws)).raw,r.raw,'repeated read is byte-identical');
  assert.equal(fingerprint(),before,'the read never writes');

  assert.equal((await v2(c,ws)).status,403);
  const direct=await http(`/rest/v1/farms?id=eq.${oldFarm}`,{token:token(c),method:'GET'});
  assert.ok(direct.status!==200||(Array.isArray(direct.data)&&direct.data.length===0),direct.raw.slice(0,200));
  assert.ok(!(await v2(c,f.foreignWs)).raw.includes(oldFarm));

  // Rejected whole: an archive without its instant, then a deletion that is not an archive.
  sql(`UPDATE public.farms SET deleted_at=NULL WHERE id=${q(oldFarm)};`);
  rejected(await v2(a,ws),/farm invariant/);
  sql(`UPDATE public.farms SET status='ACTIVE',deleted_at=now() WHERE id=${q(oldFarm)};`);
  rejected(await v2(a,ws),/membership farm/);
  // A period pointing to another account's farm is never accepted.
  const stray=randomUUID();
  let inserted=true;
  try {
    sql(`BEGIN;
      INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(stray)},${q(ws)},'Ajena');
      INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until)
        VALUES (gen_random_uuid(),${q(ws)},${q(f.foreignFarm)},${q(stray)},'2025-01-01T00:00:00Z','2025-02-01T00:00:00Z');
      UPDATE public.parcels SET status='ARCHIVED',deleted_at='2025-02-01T00:00:00Z' WHERE id=${q(stray)};
      COMMIT;`);
  } catch { inserted=false; }
  // Restore the farm (Android restore: ACTIVE, no deleted_at) so only the stray row can fail.
  sql(`UPDATE public.farms SET status='ACTIVE',deleted_at=NULL,version=version+1 WHERE id=${q(oldFarm)};`);
  if(inserted) {
    rejected(await v2(a,ws),/membership farm/);
    sql(`DELETE FROM public.farm_parcel_memberships WHERE parcel_id=${q(stray)}; DELETE FROM public.parcels WHERE id=${q(stray)};`);
  } else console.log('G26-PAR2-NOTE the schema itself refuses a period over a foreign farm');
  const restored=await v2(a,ws);
  assert.equal(restored.status,200,restored.raw.slice(0,300));
  const back=restored.data.farms.find(x=>x.id===oldFarm);
  assert.deepEqual([back.status,back.archivedAt,back.version],['ACTIVE',null,3]);
  assert.equal(restored.data.memberships.length,3);
});

test('row limits reject whole: 101 farms, 1001 parcels, 5001 memberships',async()=>{
  const a=actors.A;
  const fresh=name=>{ const id=randomUUID(); sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(id)},${q(a.id)},${q(name)});`); return id; };
  const farms=fresh('101 fincas');
  sql(`INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name)
    SELECT gen_random_uuid(),${q(farms)},${q(a.id)},${q(a.id)},'F'||g FROM generate_series(1,101) g;`);
  rejected(await v2(a,farms),/too large for contract v2: rows/);
  // Real archives count towards the 100 farms; a deletion (not an archive) neither counts nor travels.
  const archivedCount=fresh('100 + 1 archivada'), deletedCount=fresh('100 + 1 borrada');
  for(const [ws,extra] of [[archivedCount,`'ARCHIVED',now()`],[deletedCount,`'ACTIVE',now()`]]) {
    sql(`INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name)
      SELECT gen_random_uuid(),${q(ws)},${q(a.id)},${q(a.id)},'F'||g FROM generate_series(1,100) g;
      INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,status,deleted_at)
      VALUES (gen_random_uuid(),${q(ws)},${q(a.id)},${q(a.id)},'Extra',${extra});`);
  }
  rejected(await v2(a,archivedCount),/too large for contract v2: rows/);
  const withDeleted=await v2(a,deletedCount);
  assert.equal(withDeleted.status,200,withDeleted.raw.slice(0,300));
  assert.equal(withDeleted.data.farms.length,100);
  assert.ok(withDeleted.data.farms.every(x=>x.name!=='Extra'));
  const parcels=fresh('1001 parcelas');
  sql(`INSERT INTO public.parcels(id,workspace_id,display_name) SELECT gen_random_uuid(),${q(parcels)},'P'||g FROM generate_series(1,1001) g;`);
  rejected(await v2(a,parcels),/too large for contract v2: rows/);
  const periods=fresh('5001 relaciones'), farm=randomUUID();
  sql(`INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(farm)},${q(periods)},${q(a.id)},${q(a.id)},'F');
    INSERT INTO public.parcels(id,workspace_id,display_name) SELECT gen_random_uuid(),${q(periods)},'P'||g FROM generate_series(1,2) g;
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until)
    SELECT gen_random_uuid(),${q(periods)},${q(farm)},p.id,timestamptz '2000-01-01' + g*interval '1 day',
      timestamptz '2000-01-01' + g*interval '1 day' + interval '12 hours'
    FROM (SELECT id,row_number() OVER (ORDER BY id) rn FROM public.parcels WHERE workspace_id=${q(periods)}) p
    CROSS JOIN generate_series(1,2501) g WHERE p.rn<=2;`);
  assert.equal(sql(`SELECT count(*) FROM public.farm_parcel_memberships WHERE workspace_id=${q(periods)};`),'5002');
  rejected(await v2(a,periods),/too large for contract v2: rows/);
});

test('at the row limit the whole graph is accepted; bytes and time are measured',async()=>{
  const a=actors.A, w=randomUUID();
  // ~4 KB of GeoJSON per parcel: a typical Catastro outline, not the 4 MiB worst case.
  const geometry=`{"type":"Polygon","coordinates":[[${Array(170).fill('[-3.4812345,37.6312345]').join(',')}]]}`;
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(w)},${q(a.id)},'En el límite');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name)
      SELECT gen_random_uuid(),${q(w)},${q(a.id)},${q(a.id)},'Finca '||g FROM generate_series(1,100) g;
    INSERT INTO public.parcels(id,workspace_id,display_name,geometry_geo_json,managed_area_m2,olive_tree_count,variety,municipality,province)
      SELECT gen_random_uuid(),${q(w)},'Parcela '||g,${q(geometry)},10000+g,100+g,'Picual','Bedmar','Jaén' FROM generate_series(1,1000) g;
    WITH f AS (SELECT id,row_number() OVER (ORDER BY id)-1 AS n FROM public.farms WHERE workspace_id=${q(w)}),
         p AS (SELECT id,row_number() OVER (ORDER BY id) AS n FROM public.parcels WHERE workspace_id=${q(w)})
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until)
      SELECT gen_random_uuid(),${q(w)},f.id,p.id,timestamptz '2020-01-01' + k*interval '1 year',
        CASE WHEN k<4 THEN timestamptz '2020-01-01' + (k+1)*interval '1 year' - interval '1 day' END
      FROM p CROSS JOIN generate_series(0,4) k JOIN f ON f.n=(p.n+k)%100;`);
  const r=await v2(a,w);
  assert.equal(r.status,200,r.raw.slice(0,300));
  assert.deepEqual([r.data.farms.length,r.data.parcels.length,r.data.memberships.length],[100,1000,5000]);
  const bytes=Buffer.byteLength(r.raw,'utf8');
  assert.ok(bytes<8388608,`${bytes} bytes`);
  console.log(`G26-PAR2-MEASURE at-limit farms=100 parcels=1000 memberships=5000 geometry=${geometry.length}B bytes=${bytes} ms=${r.ms}`);
});

test('the 8 MiB byte bound rejects whole even under the row limits',async()=>{
  const a=actors.A, w=randomUUID(), farm=randomUUID();
  const huge=`{"type":"Polygon","coordinates":[[${Array(140000).fill('[-3.4812345,37.6312345]').join(',')}]]}`; // ~3.3 MB
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(w)},${q(a.id)},'Bytes');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(farm)},${q(w)},${q(a.id)},${q(a.id)},'F');
    INSERT INTO public.parcels(id,workspace_id,display_name,geometry_geo_json)
      SELECT gen_random_uuid(),${q(w)},'Grande '||g,${q(huge)} FROM generate_series(1,3) g;
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from)
      SELECT gen_random_uuid(),${q(w)},${q(farm)},id,'2026-01-01T00:00:00Z' FROM public.parcels WHERE workspace_id=${q(w)};`);
  const r=await v2(a,w);
  rejected(r,/too large for contract v2: bytes/);
  console.log(`G26-PAR2-MEASURE byte-bound parcels=3 geometry=${huge.length}B status=${r.status} ms=${r.ms}`);
  sql(`DELETE FROM public.farm_parcel_memberships WHERE workspace_id=${q(w)} AND parcel_id IN
    (SELECT id FROM public.parcels WHERE workspace_id=${q(w)} ORDER BY id LIMIT 1);
    DELETE FROM public.parcels WHERE id IN (SELECT id FROM public.parcels WHERE workspace_id=${q(w)} ORDER BY id LIMIT 1);`);
  const under=await v2(a,w);
  assert.equal(under.status,200,'two large parcels fit under the bound');
  assert.ok(Buffer.byteLength(under.raw,'utf8')<8388608);
});
