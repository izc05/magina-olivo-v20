// Native disposable Supabase only. The runner validates its local project ID.
// Before/after proof for proposals/g26-fpm-manage-hardening.sql; tests run in file order.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const work = process.env.G26_FPM_WORK;
const project = process.env.G26_FPM_PROJECT;
assert.match(project ?? '', /^mg26-fpm-[0-9]+-[0-9]+$/);
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
async function http(path, {token,body,method='POST',prefer,admin=false}={}) {
  const headers={apikey:admin?status.SERVICE_ROLE_KEY:status.ANON_KEY,Authorization:`Bearer ${token??status.ANON_KEY}`,'Content-Type':'application/json'};
  if (prefer) headers.Prefer=prefer;
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
const parcelBody=(id,ws,farmId)=>({id,workspaceId:ws,displayName:'Parcela',cadastralReference:null,cadastralPolygon:null,
  cadastralParcel:null,municipality:null,province:null,source:'MANUAL',geometryGeoJson:null,sourceProvider:null,
  sourceImportedAt:null,cadastralAreaM2:null,managedAreaM2:1234.5,notes:null,status:'ACTIVE',oliveTreeCount:null,
  variety:null,irrigationSystem:null,irrigationNetwork:null,irrigationSector:null,irrigationDays:null,
  memberships:[{id:randomUUID(),farmId,validFrom:'2026-01-01T00:00:00.007Z',validUntil:null,deletedAt:null}]});
const reload=async()=>{
  sql(`NOTIFY pgrst, 'reload schema';`);
  await new Promise(resolve=>setTimeout(resolve,1500));
};
const actors={};
const f={};
const members=parcel=>Number(sql(`SELECT count(*) FROM public.farm_parcel_memberships WHERE parcel_id=${q(parcel)}::uuid;`));

before(async()=>{
  for(const label of ['A','C','G']) {
    const email=`g26-fpm-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
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
    new URL('../../proposals/g26-parent-receive-v1.sql',import.meta.url)];
  sql(`BEGIN;\n${files.map(file=>readFileSync(file,'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);
  for(let attempt=0;attempt<40;attempt++){
    if((await rpc('check_sync_operations',actors.A,{p_operation_ids:[]})).status===200) break;
    if(attempt===39) throw new Error('PostgREST did not reload');
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  // A's graph through the reviewed RPCs: WORKSPACE (#798), FARM (#799), PARCEL with its membership (#800).
  f.workspace=randomUUID(); f.farm=randomUUID();
  assert.equal((await rpc('create_own_workspace',actors.A,{p_operation_id:randomUUID(),p_workspace:{id:f.workspace,
    name:'Olivar',countryCode:'ES',timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'}})).status,200);
  const req={payloadVersion:1,operationId:randomUUID(),farmId:f.farm,localWorkspaceId:f.workspace,consentId:randomUUID(),
    consentTextVersion:2,manifestSha256:'b'.repeat(64),aggregateVersion:1};
  const reserved=await rpc('reserve_local_farm_g1c',actors.A,{p_request:req,p_remote_workspace:f.workspace});
  assert.equal(reserved.status,200,JSON.stringify(reserved.data));
  assert.equal((await rpc('create_reserved_farm',actors.A,{p_operation_id:randomUUID(),p_reservation_operation_id:req.operationId,
    p_reservation_receipt_id:reserved.data.receiptId,p_farm:{id:f.farm,workspaceId:f.workspace,name:'Finca',description:null,
      municipality:null,province:null,notes:null,status:'ACTIVE',coverDocumentId:null}})).status,200);
  for (const key of ['parcel','spare']) {
    f[key]=randomUUID();
    const body=parcelBody(f[key],f.workspace,f.farm);
    f[`${key}Membership`]=body.memberships[0].id;
    const created=await rpc('create_own_parcel',actors.A,{p_operation_id:randomUUID(),p_parcel:body});
    assert.equal(created.status,200,JSON.stringify(created.data));
  }
  // G: a farm grantee with manage_farm, not the workspace owner.
  sql(`INSERT INTO public.farm_access_grants(farm_id,user_id,granted_by_user_id,role,capabilities)
    VALUES(${q(f.farm)},${q(actors.G.id)},${q(actors.A.id)},'WORKER',ARRAY['view_farm','manage_farm']);`);
});

test('baseline without the hardening: fpm_manage lets the owner and a farm grantee rewrite history directly',async()=>{
  const patched=await http(`/rest/v1/farm_parcel_memberships?id=eq.${f.parcelMembership}`,{token:token(actors.A),method:'PATCH',
    body:{valid_until:'2026-06-01T00:00:00Z'},prefer:'return=representation'});
  assert.equal(patched.status,200,JSON.stringify(patched.data));
  assert.equal(patched.data.length,1);
  const injected=randomUUID();
  const inserted=await http('/rest/v1/farm_parcel_memberships',{token:token(actors.G),body:{id:injected,workspace_id:f.workspace,
    farm_id:f.farm,parcel_id:f.parcel,valid_from:'2026-07-01T00:00:00Z'}});
  assert.equal(inserted.status,201,JSON.stringify(inserted.data));
  const removed=await http(`/rest/v1/farm_parcel_memberships?id=eq.${injected}`,{token:token(actors.G),method:'DELETE',prefer:'return=representation'});
  assert.equal(removed.status,200,JSON.stringify(removed.data));
  assert.equal(removed.data.length,1);
  // Hard parcel delete cascades into its whole membership history.
  assert.equal(members(f.spare),1);
  const gone=await http(`/rest/v1/parcels?id=eq.${f.spare}`,{token:token(actors.A),method:'DELETE',prefer:'return=representation'});
  assert.equal(gone.status,200,JSON.stringify(gone.data));
  assert.equal(members(f.spare),0);
  // Restore the fixture: the RPC-written history is the reference for the next steps.
  sql(`UPDATE public.farm_parcel_memberships SET valid_until=NULL,valid_until_exact=NULL WHERE id=${q(f.parcelMembership)}::uuid;`);
  f.spare=randomUUID();
  const body=parcelBody(f.spare,f.workspace,f.farm);
  f.spareMembership=body.memberships[0].id;
  assert.equal((await rpc('create_own_parcel',actors.A,{p_operation_id:randomUUID(),p_parcel:body})).status,200);
});

test('the hardening applies transactionally and leaves only SELECT on memberships',async()=>{
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-fpm-manage-hardening.sql',import.meta.url),'utf8')}\nCOMMIT;`);
  await reload();
  assert.equal(sql(`SELECT string_agg(policyname||':'||cmd, ',' ORDER BY policyname) FROM pg_policies
    WHERE schemaname='public' AND tablename='farm_parcel_memberships';`),'fpm_access:SELECT');
  assert.equal(sql(`SELECT string_agg(grantee||':'||privilege_type, ',' ORDER BY grantee, privilege_type)
    FROM information_schema.role_table_grants WHERE table_schema='public' AND table_name='farm_parcel_memberships'
    AND grantee IN ('PUBLIC','anon','authenticated');`),'authenticated:SELECT');
  assert.equal(sql(`SELECT has_table_privilege('authenticated','public.parcels','DELETE')::text||has_table_privilege('anon','public.parcels','DELETE')::text;`),
    'falsefalse');
  // Idempotent: applying it again changes nothing and still passes its own fail-closed checks.
  sql(`BEGIN;\n${readFileSync(new URL('../../proposals/g26-fpm-manage-hardening.sql',import.meta.url),'utf8')}\nCOMMIT;`);
});

test('no direct write remains for the owner, a manage_farm grantee, a foreign account or anon',async()=>{
  const before=sql(`SELECT string_agg(id||'|'||farm_id||'|'||coalesce(valid_until::text,'∅')||'|'||coalesce(deleted_at::text,'∅'),',' ORDER BY id)
    FROM public.farm_parcel_memberships;`);
  for (const [label, auth, expected] of [['owner',token(actors.A),403],['grantee',token(actors.G),403],
    ['foreign',token(actors.C),403],['anon',undefined,401]]) {
    const insert=await http('/rest/v1/farm_parcel_memberships',{token:auth,body:{id:randomUUID(),workspace_id:f.workspace,
      farm_id:f.farm,parcel_id:f.parcel,valid_from:'2026-08-01T00:00:00Z'}});
    assert.equal(insert.status,expected,`${label} insert ${JSON.stringify(insert.data)}`);
    const patch=await http(`/rest/v1/farm_parcel_memberships?id=eq.${f.parcelMembership}`,{token:auth,method:'PATCH',
      body:{valid_until:'2026-06-01T00:00:00Z'}});
    assert.equal(patch.status,expected,`${label} patch ${JSON.stringify(patch.data)}`);
    const remove=await http(`/rest/v1/farm_parcel_memberships?id=eq.${f.parcelMembership}`,{token:auth,method:'DELETE'});
    assert.equal(remove.status,expected,`${label} delete ${JSON.stringify(remove.data)}`);
    const parcel=await http(`/rest/v1/parcels?id=eq.${f.spare}`,{token:auth,method:'DELETE'});
    assert.equal(parcel.status,expected,`${label} parcel delete ${JSON.stringify(parcel.data)}`);
  }
  assert.equal(sql(`SELECT string_agg(id||'|'||farm_id||'|'||coalesce(valid_until::text,'∅')||'|'||coalesce(deleted_at::text,'∅'),',' ORDER BY id)
    FROM public.farm_parcel_memberships;`),before);
  assert.equal(members(f.spare),1);
});

test('legitimate reads keep their previous scope',async()=>{
  const read=actor=>http(`/rest/v1/farm_parcel_memberships?select=id&parcel_id=eq.${f.parcel}`,{token:actor&&token(actor),method:'GET'});
  assert.deepEqual((await read(actors.A)).data.map(r=>r.id),[f.parcelMembership]);
  assert.deepEqual((await read(actors.G)).data.map(r=>r.id),[f.parcelMembership]);
  const foreign=await read(actors.C);
  assert.equal(foreign.status,200);
  assert.deepEqual(foreign.data,[]);
  // Delegated parcel visibility still evaluates memberships inside its policy.
  const delegated=await http(`/rest/v1/parcels?select=id&id=eq.${f.parcel}`,{token:token(actors.G),method:'GET'});
  assert.deepEqual(delegated.data.map(r=>r.id),[f.parcel]);
  assert.equal((await http(`/rest/v1/farm_parcel_memberships?select=id`,{method:'GET'})).status,401);
});

test('reviewed SECURITY DEFINER paths keep working after the hardening',async()=>{
  const id=randomUUID(), op=randomUUID(), body=parcelBody(id,f.workspace,f.farm);
  const created=await rpc('create_own_parcel',actors.A,{p_operation_id:op,p_parcel:body});
  assert.equal(created.status,200,JSON.stringify(created.data));
  assert.equal((await rpc('create_own_parcel',actors.A,{p_operation_id:op,p_parcel:body})).data.idempotent,true);
  assert.equal(members(id),1);
  const own=await rpc('get_own_parcel',actors.A,{p_parcel_id:id});
  assert.equal(own.status,200,JSON.stringify(own.data));
  assert.equal(own.data.memberships.length,1);
  const graph=await rpc('get_own_workspace_parents_v1',actors.A,{p_workspace_id:f.workspace});
  assert.equal(graph.status,200,JSON.stringify(graph.data));
  assert.ok(graph.data.memberships.some(m=>m.id===body.memberships[0].id));
  assert.equal((await rpc('get_own_workspace_parents_v1',actors.G,{p_workspace_id:f.workspace})).status,403);
});

test('inventory: no client-executable function or remaining privilege writes memberships; other direct paths reported',()=>{
  // Functions whose own body writes the table, and whether clients may call them directly.
  const writers=sql(`SELECT string_agg(n.nspname||'.'||p.proname||':'||
      (has_function_privilege('authenticated',p.oid,'EXECUTE') OR has_function_privilege('anon',p.oid,'EXECUTE'))::text, ',' ORDER BY 1)
    FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname NOT IN ('pg_catalog','information_schema')
      AND p.prosrc ~* '(insert[[:space:]]+into|update|delete[[:space:]]+from)[[:space:]]+public[.]farm_parcel_memberships';`);
  assert.equal(writers,'magina_g2_private.create_parcel:false');
  assert.equal(sql(`SELECT has_function_privilege('authenticated','public.create_own_parcel(uuid,jsonb)','EXECUTE')::text;`),'true');
  // Report every other client-reachable direct write path in public (evidence for the next blocks).
  const report=sql(`WITH writes AS (
      SELECT c.relname, string_agg(DISTINCT g.grantee||':'||g.privilege_type, ' ' ORDER BY g.grantee||':'||g.privilege_type) AS privileges
      FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
      JOIN information_schema.role_table_grants g ON g.table_schema=n.nspname AND g.table_name=c.relname
      WHERE n.nspname='public' AND c.relkind IN ('r','p') AND g.grantee IN ('PUBLIC','anon','authenticated')
        AND g.privilege_type IN ('INSERT','UPDATE','DELETE','TRUNCATE')
      GROUP BY c.relname),
    policies AS (
      SELECT tablename AS relname, string_agg(policyname||':'||cmd, ' ' ORDER BY policyname) AS write_policies
      FROM pg_policies WHERE schemaname='public' AND cmd IN ('ALL','INSERT','UPDATE','DELETE') GROUP BY tablename)
    SELECT string_agg(w.relname||' | rls='||c.relrowsecurity::text||' | '||coalesce(p.write_policies,'no write policy')||' | '||w.privileges, E'\\n' ORDER BY w.relname)
    FROM writes w JOIN pg_class c ON c.relname=w.relname AND c.relnamespace='public'::regnamespace
    LEFT JOIN policies p ON p.relname=w.relname;`);
  console.log(`# direct-write inventory after the hardening (table | RLS | write policies | client privileges)\n${report}`);
  assert.ok(!report.split('\n').some(line=>line.startsWith('farm_parcel_memberships ')));
  // Every table that still grants client writes has RLS on: no privilege-only open table.
  assert.ok(report.split('\n').every(line=>line.includes('| rls=true |')), report);
});
