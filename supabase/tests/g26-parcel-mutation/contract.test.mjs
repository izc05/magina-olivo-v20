// Native disposable Supabase only. The runner validates its local project ID.
// Contract for proposals/g26-parcel-mutation-v1.sql (#803); tests run in file order.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHash, createHmac } from 'node:crypto';

const work = process.env.G26_PMUT_WORK;
const project = process.env.G26_PMUT_PROJECT;
assert.match(project ?? '', /^mg26-pmut-[0-9]+-[0-9]+$/);
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
async function http(path, {token,body,method='POST',admin=false}={}) {
  const headers={apikey:admin?status.SERVICE_ROLE_KEY:status.ANON_KEY,Authorization:`Bearer ${token??status.ANON_KEY}`,
    'Content-Type':'application/json'};
  const response = await fetch(api + path, {method,headers,body:body===undefined?undefined:JSON.stringify(body),
    signal:AbortSignal.timeout(15000)});
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
const farm=async(actor,ws,name='Finca Norte')=>{
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
const fields=(overrides={})=>({displayName:'Parcela 1',cadastralReference:null,cadastralPolygon:null,cadastralParcel:null,
  municipality:'Bedmar',province:'Jaén',source:'MANUAL',geometryGeoJson:null,sourceProvider:null,sourceImportedAt:null,
  cadastralAreaM2:null,managedAreaM2:1234.5,notes:null,oliveTreeCount:120,variety:'Picual',irrigationSystem:null,
  irrigationNetwork:null,irrigationSector:null,irrigationDays:null,...overrides});
/** A confirmed #800 parcel: returns its id and its current membership id. */
const parcel=async(actor,ws,farmId,overrides={})=>{
  const id=randomUUID(), membership=randomUUID();
  const created=await rpc('create_own_parcel',actor,{p_operation_id:randomUUID(),p_parcel:{id,workspaceId:ws,status:'ACTIVE',
    ...fields(overrides),memberships:[{id:membership,farmId,validFrom:START,validUntil:null,deletedAt:null}]}});
  assert.equal(created.status,200,JSON.stringify(created.data));
  return {id,membership};
};
const update=(actor,op,id,version,body)=>rpc('update_own_parcel_v1',actor,
  {p_operation_id:op,p_parcel_id:id,p_expected_version:version,p_parcel:body});
const archive=(actor,op,id,version,body)=>rpc('archive_own_parcel_v1',actor,
  {p_operation_id:op,p_parcel_id:id,p_expected_version:version,p_archive:body});
const restore=(actor,op,id,version,body)=>rpc('restore_own_parcel_v1',actor,
  {p_operation_id:op,p_parcel_id:id,p_expected_version:version,p_restore:body});
const restoreBody=(farmId,at,id=randomUUID())=>({restoredAt:at,openMembership:{id,farmId,validFrom:at}});
const receipt=(actor,op)=>rpc('check_sync_operations',actor,{p_operation_ids:[op]});
const parents=(actor,ws)=>rpc('get_own_workspace_parents_v1',actor,{p_workspace_id:ws});
const canonicalOf=value=>sql(`SELECT ${q(JSON.stringify(value))}::jsonb::text;`);
const row=id=>sql(`SELECT status||'|'||version||'|'||(deleted_at IS NOT NULL)::text||'|'||coalesce(deleted_at_exact,'-')||'|'||display_name
  FROM public.parcels WHERE id=${q(id)}::uuid;`);
const history=id=>sql(`SELECT string_agg(id||'|'||farm_id||'|'||coalesce(valid_from_exact,'-')||'|'||coalesce(valid_until_exact,'-')||'|'||version,
  ',' ORDER BY valid_from) FROM public.farm_parcel_memberships WHERE parcel_id=${q(id)}::uuid;`);
const receipts=id=>sql(`SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_id=${q(id)}::uuid;`);
const actors={};
const f={};

before(async()=>{
  for(const label of ['A','C','G']) {
    const email=`g26-pmut-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{token:status.SERVICE_ROLE_KEY,admin:true,body:{email,password,email_confirm:true}});
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
    ...['g26-parent-receive-v1.sql','g26-fpm-manage-hardening.sql'].map(name=>new URL(`../../proposals/${name}`,import.meta.url))];
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    if((await receipt(actors.A,randomUUID())).status===200) break;
    if(attempt===39) throw new Error('PostgREST did not reload');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  f.ws=await workspace(actors.A);
  f.north=await farm(actors.A,f.ws);
  f.south=await farm(actors.A,f.ws,'Finca Sur');
  f.baseline=await parcel(actors.A,f.ws,f.north);
});

test('baseline before the proposal: the owner can rewrite a parcel directly, skipping CAS and receipts',async()=>{
  const patched=await http(`/rest/v1/parcels?id=eq.${f.baseline.id}`,{token:token(actors.A),method:'PATCH',body:{display_name:'Sin CAS'}});
  assert.equal(patched.status,204,JSON.stringify(patched.data));
  // deleted_at_exact does not exist yet: read only the legacy columns.
  assert.equal(sql(`SELECT status||'|'||version||'|'||display_name FROM public.parcels WHERE id=${q(f.baseline.id)}::uuid;`),'ACTIVE|1|Sin CAS');
  assert.equal(receipts(f.baseline.id),'1');
});

test('the proposal applies atomically with its self-check',async()=>{
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-parcel-mutation-v1.sql',import.meta.url),'utf8')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    const probe=await update(actors.C,randomUUID(),randomUUID(),1,fields());
    if(probe.status!==404) break;
    if(attempt===39) throw new Error('PostgREST did not expose the mutation RPCs');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  assert.equal(sql(`SELECT string_agg(r||':'||p,' ' ORDER BY r,p) FROM unnest(ARRAY['anon','authenticated']) r,
    unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE']) p WHERE has_table_privilege(r,'public.parcels',p);`),
    'anon:SELECT authenticated:SELECT');
});

test('UPDATE: owner CAS 1->2, receipt hash is the canonical envelope, membership untouched, lost-response replay',async()=>{
  const p=await parcel(actors.A,f.ws,f.north), op=randomUUID(), body=fields({displayName:'Parcela renombrada',managedAreaM2:2500,
    sourceImportedAt:'2026-02-03T04:05:06.123456789Z'});
  const before=history(p.id);
  const done=await update(actors.A,op,p.id,1,body);
  assert.equal(done.status,200,JSON.stringify(done.data));
  assert.deepEqual(Object.keys(done.data).sort(),
    ['entity_id','idempotent','kind','memberships','operation_id','payload_version','remote_version','status','success']);
  assert.deepEqual([done.data.kind,done.data.remote_version,done.data.idempotent,done.data.memberships],['UPDATE',2,false,[]]);
  assert.equal(row(p.id),'ACTIVE|2|false|-|Parcela renombrada');
  assert.equal(sql(`SELECT managed_area_m2||'|'||source_imported_at_exact FROM public.parcels WHERE id=${q(p.id)}::uuid;`),
    '2500|2026-02-03T04:05:06.123456789Z');
  assert.equal(history(p.id),before);
  const rows=(await receipt(actors.A,op)).data.confirmed_receipts;
  assert.equal(rows.length,1);
  assert.equal(rows[0].entity_type,'PARCEL_UPDATE');
  assert.equal(rows[0].remote_version,2);
  assert.equal(rows[0].payload_hash,createHash('sha256').update(canonicalOf({kind:'UPDATE',parcelId:p.id,expectedVersion:1,
    mutation:body}),'utf8').digest('hex'));
  const replay=await update(actors.A,op,p.id,1,body);
  assert.deepEqual([replay.status,replay.data.idempotent,replay.data.remote_version],[200,true,2]);
  const altered=await update(actors.A,op,p.id,1,{...body,notes:'otro'});
  assert.equal(altered.status,409);
  assert.match(altered.data.message,/RECEIPT_CONFLICT/);
  const stale=await update(actors.A,randomUUID(),p.id,1,fields({displayName:'Edición antigua'}));
  assert.equal(stale.status,409);
  assert.match(stale.data.message,/PARCEL_VERSION_CONFLICT/);
  assert.equal(stale.data.details,'remote_version=2');
  assert.equal((await update(actors.A,randomUUID(),p.id,2,fields({displayName:'Tercera'}))).data.remote_version,3);
  // The replay still answers with the version it produced, not the current one.
  assert.deepEqual([(await update(actors.A,op,p.id,1,body)).data.remote_version],[2]);
  assert.equal(row(p.id),'ACTIVE|3|false|-|Tercera');
  assert.equal(receipts(p.id),'3');
});

test('the Android hash literal of every kind is already PostgreSQL canonical JSONB (ParcelMutationEnvelopeTest)',()=>{
  const lines=readFileSync(new URL('../../../app/src/test/resources/g26-parcel-mutation-canonical.txt',import.meta.url),'utf8')
    .split(String.fromCharCode(10)).map(line=>line.trimEnd()).filter(Boolean);
  assert.deepEqual(lines.map(line=>JSON.parse(line).kind),['UPDATE','ARCHIVE','RESTORE']);
  for(const line of lines) assert.equal(canonicalOf(JSON.parse(line)),line);
  // The server hashes jsonb_build_object(kind, parcelId, expectedVersion, mutation): the same canonical text.
  const [update]=lines.map(line=>JSON.parse(line));
  assert.equal(sql(`SELECT jsonb_build_object('kind',${q(update.kind)},'parcelId',${q(update.parcelId)},
    'expectedVersion',${update.expectedVersion}::bigint,'mutation',${q(JSON.stringify(update.mutation))}::jsonb)::text;`),lines[0]);
});

test('UPDATE validation: closed 19-field payload, no status change, invalid values rejected without writing',async()=>{
  const p=await parcel(actors.A,f.ws,f.north);
  for(const body of [{...fields(),status:'ARCHIVED'},{...fields(),memberships:[]},(({notes,...rest})=>rest)(fields()),
    fields({displayName:' '}),fields({source:'CATASTRO'}),fields({managedAreaM2:-1}),fields({oliveTreeCount:1.5}),
    fields({irrigationSystem:'FLOOD'}),fields({sourceImportedAt:'2026-02-30T00:00:00Z'})]) {
    const result=await update(actors.A,randomUUID(),p.id,1,body);
    assert.equal(result.status,400,JSON.stringify(body));
  }
  assert.equal(row(p.id),'ACTIVE|1|false|-|Parcela 1');
  assert.equal(receipts(p.id),'1');
});

test('concurrent UPDATEs with the same expected version: exactly one applies',async()=>{
  const p=await parcel(actors.A,f.ws,f.north);
  const results=await Promise.all(['Uno','Dos','Tres'].map(name=>update(actors.A,randomUUID(),p.id,1,fields({displayName:name}))));
  assert.deepEqual(results.map(result=>result.status).sort(),[200,409,409]);
  assert.equal(sql(`SELECT version FROM public.parcels WHERE id=${q(p.id)}::uuid;`),'2');
  assert.equal(receipts(p.id),'2');
});

test('ARCHIVE closes the current membership with the exact instant and archives the parcel; replay is stable',async()=>{
  const p=await parcel(actors.A,f.ws,f.north), op=randomUUID(), at='2026-03-01T10:00:00.123456789Z';
  const body={archivedAt:at,closeMembershipId:p.membership};
  assert.equal((await archive(actors.A,randomUUID(),p.id,1,{archivedAt:'2025-12-31T23:59:59Z',closeMembershipId:p.membership})).status,400);
  const wrong=await archive(actors.A,randomUUID(),p.id,1,{archivedAt:at,closeMembershipId:randomUUID()});
  assert.equal(wrong.status,409);
  assert.match(wrong.data.message,/MEMBERSHIP_NOT_CURRENT/);
  const done=await archive(actors.A,op,p.id,1,body);
  assert.equal(done.status,200,JSON.stringify(done.data));
  assert.deepEqual([done.data.kind,done.data.remote_version,done.data.memberships],['ARCHIVE',2,[{id:p.membership,version:2}]]);
  assert.equal(row(p.id),`ARCHIVED|2|true|${at}|Parcela 1`);
  assert.equal(history(p.id),`${p.membership}|${f.north}|${START}|${at}|2`);
  assert.equal((await receipt(actors.A,op)).data.confirmed_receipts[0].entity_type,'PARCEL_ARCHIVE');
  const replay=await archive(actors.A,op,p.id,1,body);
  assert.deepEqual([replay.status,replay.data.idempotent,replay.data.memberships],[200,true,[{id:p.membership,version:2}]]);
  const again=await archive(actors.A,randomUUID(),p.id,2,body);
  assert.equal(again.status,409);
  assert.match(again.data.message,/PARCEL_NOT_ACTIVE/);
  assert.match((await update(actors.A,randomUUID(),p.id,2,fields())).data.message,/PARCEL_NOT_ACTIVE/);
  // v1 parents receiver keeps excluding archived parcels; the parcel stays readable by its owner.
  assert.ok(!(await parents(actors.A,f.ws)).data.parcels.some(item=>item.id===p.id));
  const own=await http(`/rest/v1/parcels?id=eq.${p.id}&select=id,status`,{token:token(actors.A),method:'GET'});
  assert.deepEqual(own.data,[{id:p.id,status:'ARCHIVED'}]);
});

test('ARCHIVE is blocked by a running campaign; closed campaigns and their snapshots never block and stay intact',async()=>{
  const p=await parcel(actors.A,f.ws,f.north), running=randomUUID(), closed=randomUUID(), snapshot=randomUUID();
  sql(`INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,status) VALUES
      (${q(running)}::uuid,${q(f.ws)}::uuid,${q(f.north)}::uuid,'2026/27','2026-10-01','HARVEST'),
      (${q(closed)}::uuid,${q(f.ws)}::uuid,${q(f.north)}::uuid,'2025/26','2025-10-01','CLOSED');
    INSERT INTO public.campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,farm_name_at_start,parcel_name_at_start)
    VALUES (${q(randomUUID())}::uuid,${q(f.ws)}::uuid,${q(running)}::uuid,${q(p.id)}::uuid,${q(f.north)}::uuid,'Finca Norte','Parcela 1'),
      (${q(snapshot)}::uuid,${q(f.ws)}::uuid,${q(closed)}::uuid,${q(p.id)}::uuid,${q(f.north)}::uuid,'Finca Norte','Parcela 1');`);
  const body={archivedAt:'2026-11-01T08:00:00Z',closeMembershipId:p.membership};
  for(const state of ['PREPARATION','ACTIVE','HARVEST']) {
    sql(`UPDATE public.campaigns SET status=${q(state)} WHERE id=${q(running)}::uuid;`);
    const blocked=await archive(actors.A,randomUUID(),p.id,1,body);
    assert.equal(blocked.status,409,state);
    assert.match(blocked.data.message,/ARCHIVE_BLOCKED_CAMPAIGN/);
  }
  assert.equal(row(p.id),'ACTIVE|1|false|-|Parcela 1');
  sql(`UPDATE public.campaigns SET status='CLOSED' WHERE id=${q(running)}::uuid;`);
  assert.equal((await archive(actors.A,randomUUID(),p.id,1,body)).status,200);
  assert.equal(sql(`SELECT count(*) FROM public.campaign_parcels WHERE parcel_id=${q(p.id)}::uuid;`),'2');
});

test('RESTORE into another farm keeps the UUID and the closed period, opens a new v1 membership; parents v1 sees it again',async()=>{
  const p=await parcel(actors.A,f.ws,f.north), archivedAt='2026-03-01T10:00:00.123456789Z';
  assert.equal((await archive(actors.A,randomUUID(),p.id,1,{archivedAt,closeMembershipId:p.membership})).status,200);
  assert.match((await restore(actors.A,randomUUID(),p.id,2,restoreBody(f.south,'2026-03-01T10:00:00.123456788Z'))).data.message,
    /restore before archive/);
  assert.equal((await restore(actors.A,randomUUID(),p.id,2,{...restoreBody(f.south,archivedAt),restoredAt:'2026-04-01T00:00:00Z'})).status,400);
  const taken=await restore(actors.A,randomUUID(),p.id,2,restoreBody(f.south,'2026-04-01T00:00:00Z',p.membership));
  assert.equal(taken.status,409);
  assert.match(taken.data.message,/MEMBERSHIP_ID_TAKEN/);
  const op=randomUUID(), open=randomUUID(), body=restoreBody(f.south,'2026-04-01T09:30:00.000000001Z',open);
  const done=await restore(actors.A,op,p.id,2,body);
  assert.equal(done.status,200,JSON.stringify(done.data));
  assert.deepEqual([done.data.kind,done.data.remote_version,done.data.memberships],['RESTORE',3,[{id:open,version:1}]]);
  assert.equal(row(p.id),'ACTIVE|3|false|-|Parcela 1');
  assert.equal(history(p.id),[`${p.membership}|${f.north}|${START}|${archivedAt}|2`,
    `${open}|${f.south}|2026-04-01T09:30:00.000000001Z|-|1`].join(','));
  assert.equal((await restore(actors.A,op,p.id,2,body)).data.idempotent,true);
  const again=await restore(actors.A,randomUUID(),p.id,3,restoreBody(f.north,'2026-05-01T00:00:00Z'));
  assert.equal(again.status,409);
  assert.match(again.data.message,/PARCEL_NOT_ARCHIVED/);
  const graph=(await parents(actors.A,f.ws)).data;
  assert.deepEqual(graph.parcels.filter(item=>item.id===p.id).map(item=>[item.status,item.version]),[['ACTIVE',3]]);
  assert.deepEqual(graph.memberships.filter(item=>item.parcelId===p.id).map(item=>[item.id,item.farmId,item.validUntil]).sort(),
    [[open,f.south,null],[p.membership,f.north,archivedAt]].sort());
  // Archive again from the south farm, then restore at the exact closing instant (half-open periods).
  const second='2026-06-01T00:00:00Z';
  assert.equal((await archive(actors.A,randomUUID(),p.id,3,{archivedAt:second,closeMembershipId:open})).status,200);
  assert.equal((await restore(actors.A,randomUUID(),p.id,4,restoreBody(f.north,second))).status,200);
  assert.equal(sql(`SELECT count(*)||'|'||count(*) FILTER (WHERE valid_until IS NULL) FROM public.farm_parcel_memberships
    WHERE parcel_id=${q(p.id)}::uuid;`),'3|1');
});

test('CATASTRO: a reference held by another active parcel blocks UPDATE and RESTORE',async()=>{
  const geometry='{"type":"Polygon","coordinates":[]}';
  const holder=await parcel(actors.A,f.ws,f.north,{source:'CATASTRO',cadastralReference:'23050A00100001',geometryGeoJson:geometry});
  const other=await parcel(actors.A,f.ws,f.north);
  const duplicate=await update(actors.A,randomUUID(),other.id,1,fields({source:'CATASTRO',cadastralReference:' 23050a00100001 ',geometryGeoJson:geometry}));
  assert.equal(duplicate.status,409);
  assert.match(duplicate.data.message,/DUPLICATE_CADASTRAL_REFERENCE/);
  assert.equal((await archive(actors.A,randomUUID(),holder.id,1,{archivedAt:'2026-03-01T00:00:00Z',closeMembershipId:holder.membership})).status,200);
  assert.equal((await update(actors.A,randomUUID(),other.id,1,fields({source:'CATASTRO',cadastralReference:'23050A00100001',
    geometryGeoJson:geometry}))).status,200);
  const blocked=await restore(actors.A,randomUUID(),holder.id,2,restoreBody(f.north,'2026-04-01T00:00:00Z'));
  assert.equal(blocked.status,409);
  assert.match(blocked.data.message,/DUPLICATE_CADASTRAL_REFERENCE/);
  assert.equal(row(holder.id).split('|')[0],'ARCHIVED');
});

test('authorization: owner only; foreign accounts, write/manage_farm grantees, foreign farms, wrong issuer and anon are refused',async()=>{
  const p=await parcel(actors.A,f.ws,f.north);
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
      VALUES(${q(f.ws)}::uuid,${q(actors.G.id)}::uuid,'MANAGER',ARRAY['read','write']);
    INSERT INTO public.farm_access_grants(farm_id,user_id,granted_by_user_id,role,capabilities)
      VALUES(${q(f.north)}::uuid,${q(actors.G.id)}::uuid,${q(actors.A.id)}::uuid,'WORKER',ARRAY['view_farm','manage_farm','manage_all']);`);
  const archiveBody={archivedAt:'2026-03-01T00:00:00Z',closeMembershipId:p.membership};
  for(const actor of [actors.C,actors.G]) {
    assert.equal((await update(actor,randomUUID(),p.id,1,fields({displayName:'Ajena'}))).status,403);
    assert.equal((await archive(actor,randomUUID(),p.id,1,archiveBody)).status,403);
  }
  assert.equal((await update(actors.A,randomUUID(),randomUUID(),1,fields())).status,403);
  const raw=await http('/rest/v1/rpc/update_own_parcel_v1',{token:actors.A.token,
    body:{p_operation_id:randomUUID(),p_parcel_id:p.id,p_expected_version:1,p_parcel:fields()}});
  assert.equal(raw.status,403);
  const anon=await http('/rest/v1/rpc/archive_own_parcel_v1',{body:{p_operation_id:randomUUID(),p_parcel_id:p.id,
    p_expected_version:1,p_archive:archiveBody}});
  assert.equal(anon.status,401);
  // C's own farm (another workspace) cannot receive A's parcel on restore.
  const cws=await workspace(actors.C), cfarm=await farm(actors.C,cws);
  assert.equal((await archive(actors.A,randomUUID(),p.id,1,archiveBody)).status,200);
  assert.equal((await restore(actors.A,randomUUID(),p.id,2,restoreBody(cfarm,'2026-04-01T00:00:00Z'))).status,403);
  assert.equal((await restore(actors.C,randomUUID(),p.id,2,restoreBody(cfarm,'2026-04-01T00:00:00Z'))).status,403);
  assert.equal(row(p.id),'ARCHIVED|2|true|2026-03-01T00:00:00Z|Parcela 1');
});

test('a deleted target or current farm, or a deleted workspace, refuses the mutation and its replay',async()=>{
  const ws=await workspace(actors.A), home=await farm(actors.A,ws), gone=await farm(actors.A,ws,'Finca vendida');
  const p=await parcel(actors.A,ws,home), q2=await parcel(actors.A,ws,gone);
  const op=randomUUID(), body={archivedAt:'2026-03-01T00:00:00Z',closeMembershipId:p.membership};
  const edit=randomUUID(), edited=fields({displayName:'Antes de vender'});
  assert.equal((await archive(actors.A,op,p.id,1,body)).status,200);
  assert.equal((await update(actors.A,edit,q2.id,1,edited)).status,200);
  assert.equal((await update(actors.A,edit,q2.id,1,edited)).data.idempotent,true);
  sql(`UPDATE public.farms SET deleted_at=now() WHERE id=${q(gone)}::uuid;`);
  assert.equal((await update(actors.A,randomUUID(),q2.id,2,fields())).status,403);
  // UPDATE replays re-check the current farm too, like ARCHIVE and RESTORE.
  assert.equal((await update(actors.A,edit,q2.id,1,edited)).status,403);
  assert.equal((await restore(actors.A,randomUUID(),p.id,2,restoreBody(gone,'2026-04-01T00:00:00Z'))).status,403);
  sql(`UPDATE public.farms SET deleted_at=now() WHERE id=${q(home)}::uuid;`);
  assert.equal((await archive(actors.A,op,p.id,1,body)).status,403);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(ws)}::uuid;`);
  assert.equal((await update(actors.A,randomUUID(),q2.id,2,fields())).status,403);
  assert.equal((await receipt(actors.A,op)).data.confirmed_receipts.length,1);
});

test('no direct client write remains on parcels or memberships; legitimate reads and create_own_parcel keep working',async()=>{
  const p=await parcel(actors.A,f.ws,f.north);
  for(const actor of [actors.A,actors.G,actors.C]) {
    const auth=token(actor);
    assert.equal((await http(`/rest/v1/parcels?id=eq.${p.id}`,{token:auth,method:'PATCH',body:{display_name:'Directo'}})).status,403);
    assert.equal((await http('/rest/v1/parcels',{token:auth,body:{workspace_id:f.ws,display_name:'Directa'}})).status,403);
    assert.equal((await http(`/rest/v1/parcels?id=eq.${p.id}`,{token:auth,method:'DELETE'})).status,403);
    assert.equal((await http(`/rest/v1/farm_parcel_memberships?id=eq.${p.membership}`,{token:auth,method:'PATCH',
      body:{valid_until:'2026-02-01T00:00:00Z'}})).status,403);
  }
  assert.equal((await http(`/rest/v1/parcels?id=eq.${p.id}`,{method:'PATCH',body:{display_name:'Anon'}})).status,401);
  assert.equal(row(p.id),'ACTIVE|1|false|-|Parcela 1');
  const owner=await http(`/rest/v1/parcels?id=eq.${p.id}&select=id`,{token:token(actors.A),method:'GET'});
  assert.deepEqual(owner.data,[{id:p.id}]);
  const delegated=await http(`/rest/v1/parcels?id=eq.${p.id}&select=id`,{token:token(actors.G),method:'GET'});
  assert.deepEqual(delegated.data,[{id:p.id}]);
  assert.deepEqual((await http(`/rest/v1/parcels?id=eq.${p.id}&select=id`,{token:token(actors.C),method:'GET'})).data,[]);
  const memberships=await http(`/rest/v1/farm_parcel_memberships?parcel_id=eq.${p.id}&select=id`,{token:token(actors.A),method:'GET'});
  assert.deepEqual(memberships.data,[{id:p.membership}]);
  assert.equal((await rpc('get_own_parcel',actors.A,{p_parcel_id:p.id})).status,200);
});

test('inventory: the only writers of parcels and memberships are the two private cores, neither callable by clients',()=>{
  const writers=table=>sql(`SELECT string_agg(n.nspname||'.'||p.proname||':'||
      (has_function_privilege('authenticated',p.oid,'EXECUTE') OR has_function_privilege('anon',p.oid,'EXECUTE'))::text, ',' ORDER BY n.nspname,p.proname)
    FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname NOT IN ('pg_catalog','information_schema')
      AND p.prosrc ~* '(insert[[:space:]]+into|update|delete[[:space:]]+from)[[:space:]]+public[.]${table}([^_a-z]|$)';`);
  assert.equal(writers('farm_parcel_memberships'),'magina_g2_private.create_parcel:false,magina_g2_private.mutate_parcel:false');
  assert.equal(writers('parcels'),'magina_g2_private.create_parcel:false,magina_g2_private.mutate_parcel:false');
  assert.equal(sql(`SELECT string_agg(p.proname||':'||has_function_privilege('authenticated',p.oid,'EXECUTE')::text||':'||
      has_function_privilege('anon',p.oid,'EXECUTE')::text||':'||p.prosecdef::text, ',' ORDER BY p.proname)
    FROM pg_proc p WHERE p.pronamespace='public'::regnamespace AND p.proname LIKE '%\\_own\\_parcel\\_v1';`),
    'archive_own_parcel_v1:true:false:true,restore_own_parcel_v1:true:false:true,update_own_parcel_v1:true:false:true');
});
