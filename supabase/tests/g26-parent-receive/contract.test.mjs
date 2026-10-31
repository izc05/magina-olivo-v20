// Native disposable Supabase only. The runner validates its local project ID.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const work = process.env.G26_PARENTS_WORK;
const project = process.env.G26_PARENTS_PROJECT;
assert.match(project ?? '', /^magina-g26-parents-[0-9]+-[0-9]+$/);
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
async function http(path, {token,admin=false,body,method='POST'}={}) {
  const key = admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY;
  const response = await fetch(api + path, {method,headers:{apikey:key,Authorization:`Bearer ${token??key}`,
    'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const raw = await response.text();
  let data; try { data=JSON.parse(raw); } catch { data=raw; }
  return {status:response.status,data};
}
const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
function token(actor, issuer=ISSUER, expired=false, extra={}) {
  const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url'));
  const part=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part({...claims,iss:issuer,
    exp:expired?Math.floor(Date.now()/1000)-1:claims.exp,...extra})}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
}
const parents=(actor,workspace,authToken=token(actor))=>http('/rest/v1/rpc/get_own_workspace_parents_v1',{
  token:authToken,body:{p_workspace_id:workspace}});
const actors={};
const f={};
before(async()=>{
  for(const label of ['A','C','M']) {
    const email=`g26-parents-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',{admin:true,body:{email,password,email_confirm:true}});
    assert.equal(created.status,200);
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
  Object.assign(f,{workspace:randomUUID(),farm1:randomUUID(),farm2:randomUUID(),deletedFarm:randomUUID(),
    parcel1:randomUUID(),parcel2:randomUUID(),deletedParcel:randomUUID(),m1:randomUUID(),m2:randomUUID(),
    deletedMembership:randomUUID(),foreignWorkspace:randomUUID(),foreignFarm:randomUUID(),foreignParcel:randomUUID()});
  const a=actors.A,c=actors.C;
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(f.workspace)},${q(a.id)},'Olivar A');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,municipality) VALUES
      (${q(f.farm1)},${q(f.workspace)},${q(a.id)},${q(a.id)},'Finca Norte','Bedmar'),
      (${q(f.farm2)},${q(f.workspace)},${q(a.id)},${q(a.id)},'Finca Sur','Jódar'),
      (${q(f.deletedFarm)},${q(f.workspace)},${q(a.id)},${q(a.id)},'Borrada',NULL);
    UPDATE public.farms SET deleted_at=now() WHERE id=${q(f.deletedFarm)};
    INSERT INTO public.parcels(id,workspace_id,display_name,managed_area_m2,olive_tree_count,variety,source_imported_at,source_imported_at_exact) VALUES
      (${q(f.parcel1)},${q(f.workspace)},'Parcela 1',1234.5,120,'Picual','2026-10-01T10:00:00.123Z','2026-10-01T10:00:00.123Z'),
      (${q(f.parcel2)},${q(f.workspace)},'Parcela 2',NULL,NULL,NULL,NULL,NULL),
      (${q(f.deletedParcel)},${q(f.workspace)},'Parcela borrada',NULL,NULL,NULL,NULL,NULL);
    UPDATE public.parcels SET deleted_at=now() WHERE id=${q(f.deletedParcel)};
    INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_from_exact) VALUES
      (${q(f.m1)},${q(f.workspace)},${q(f.farm1)},${q(f.parcel1)},'2026-01-01T00:00:00.007Z','2026-01-01T00:00:00.007Z'),
      (${q(f.m2)},${q(f.workspace)},${q(f.farm2)},${q(f.parcel2)},'2026-01-01T00:00:00Z',NULL),
      (${q(f.deletedMembership)},${q(f.workspace)},${q(f.farm1)},${q(f.parcel2)},'2025-01-01T00:00:00Z',NULL);
    UPDATE public.farm_parcel_memberships SET deleted_at=now() WHERE id=${q(f.deletedMembership)};
    INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(f.foreignWorkspace)},${q(c.id)},'Olivar C');
    INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES (${q(f.foreignFarm)},${q(f.foreignWorkspace)},${q(c.id)},${q(c.id)},'Finca C');
    INSERT INTO public.parcels(id,workspace_id,display_name) VALUES (${q(f.foreignParcel)},${q(f.foreignWorkspace)},'Parcela C');`);
  for(let i=0;i<40;i++) {
    const probe=await parents(a,f.workspace);
    if(probe.status!==404) return;
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  assert.fail('PostgREST did not reload the parents RPC');
});

test('owner receives one complete, live and deterministic WORKSPACE/FARM/PARCEL graph',async()=>{
  const a=actors.A;
  assert.equal(sql("SELECT has_function_privilege('anon','public.get_own_workspace_parents_v1(uuid)','EXECUTE'),has_function_privilege('authenticated','public.get_own_workspace_parents_v1(uuid)','EXECUTE');"),'f|t');
  const own=await parents(a,f.workspace);
  assert.equal(own.status,200,JSON.stringify(own.data));
  const d=own.data;
  assert.deepEqual(Object.keys(d).sort(),['contract_version','farms','issuer','memberships','parcels','subject','workspace']);
  assert.equal(d.contract_version,1);assert.equal(d.issuer,ISSUER);assert.equal(d.subject,a.id);
  assert.equal(d.workspace.id,f.workspace);assert.equal(d.workspace.ownerUserId,a.id);assert.equal(d.workspace.version,1);
  assert.deepEqual(d.farms.map(x=>x.id),[f.farm1,f.farm2].sort());
  assert.deepEqual(d.parcels.map(x=>x.id),[f.parcel1,f.parcel2].sort());
  assert.deepEqual(d.memberships.map(x=>x.id),[f.m1,f.m2].sort());
  const p1=d.parcels.find(x=>x.id===f.parcel1);
  assert.equal(p1.managedAreaM2,1234.5);assert.equal(p1.oliveTreeCount,120);
  assert.equal(p1.sourceImportedAt,'2026-10-01T10:00:00.123Z');
  assert.equal(d.memberships.find(x=>x.id===f.m1).validFrom,'2026-01-01T00:00:00.007Z');
  assert.ok(d.farms.every(x=>x.workspaceId===f.workspace&&x.version===1&&x.createdAt&&x.updatedAt));
  assert.ok(!JSON.stringify(d).includes(f.foreignFarm)&&!JSON.stringify(d).includes(f.foreignParcel));
  assert.ok(!JSON.stringify(d).includes('cover_image')&&!JSON.stringify(d).includes('creator'));
  assert.deepEqual((await parents(a,f.workspace)).data,d,'repeated read must be byte-for-byte stable');
});

test('foreign account, issuer, expiry, anonymous and deleted workspace receive nothing',async()=>{
  const a=actors.A,c=actors.C;
  assert.equal((await parents(c,f.workspace)).status,403);
  assert.equal((await parents(a,f.foreignWorkspace)).status,403);
  assert.equal((await parents(a,randomUUID())).status,403);
  assert.equal((await parents(a,f.workspace,token(a,'https://other.example/auth/v1'))).status,403);
  assert.ok([401,403].includes((await parents(a,f.workspace,token(a,undefined,true))).status));
  assert.equal((await parents(a,f.workspace,token(a,ISSUER,false,{is_anonymous:true}))).status,403);
  const anon=await http('/rest/v1/rpc/get_own_workspace_parents_v1',{body:{p_workspace_id:f.workspace}});
  assert.ok([401,403].includes(anon.status));
  assert.equal((await parents(c,f.foreignWorkspace)).status,200);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(f.foreignWorkspace)};`);
  assert.equal((await parents(c,f.foreignWorkspace)).status,403);
});

test('member read follows current grant: active, suspended, expired window and revoked',async()=>{
  const m=actors.M;
  assert.equal((await parents(m,f.workspace)).status,403);
  sql(`INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
       VALUES (${q(f.workspace)},${q(m.id)},'VIEWER',ARRAY['read']);`);
  const member=await parents(m,f.workspace);
  assert.equal(member.status,200,JSON.stringify(member.data));
  assert.equal(member.data.subject,m.id);
  assert.equal(member.data.workspace.ownerUserId,actors.A.id);
  assert.equal(member.data.farms.length,2);
  sql(`UPDATE public.workspace_memberships SET status='SUSPENDED',version=version+1 WHERE workspace_id=${q(f.workspace)} AND user_id=${q(m.id)};`);
  assert.equal((await parents(m,f.workspace)).status,403);
  sql(`UPDATE public.workspace_memberships SET status='ACTIVE',valid_from=now()-interval '2 days',valid_until=now()-interval '1 day',version=version+1 WHERE workspace_id=${q(f.workspace)} AND user_id=${q(m.id)};`);
  assert.equal((await parents(m,f.workspace)).status,403);
  sql(`UPDATE public.workspace_memberships SET valid_until=NULL,version=version+1 WHERE workspace_id=${q(f.workspace)} AND user_id=${q(m.id)};`);
  assert.equal((await parents(m,f.workspace)).status,200);
  sql(`UPDATE public.workspace_memberships SET status='REVOKED',revoked_at=now(),version=version+1 WHERE workspace_id=${q(f.workspace)} AND user_id=${q(m.id)};`);
  assert.equal((await parents(m,f.workspace)).status,403);
});

test('versions advance with edits and unsupported or oversized graphs are rejected, not clipped',async()=>{
  const a=actors.A;
  sql(`UPDATE public.farms SET name='Finca Norte II',version=version+1,updated_at=now() WHERE id=${q(f.farm1)};`);
  const edited=await parents(a,f.workspace);
  assert.equal(edited.data.farms.find(x=>x.id===f.farm1).version,2);
  assert.equal(edited.data.farms.find(x=>x.id===f.farm1).name,'Finca Norte II');
  sql(`UPDATE public.farms SET status='PENDING_REVIEW' WHERE id=${q(f.farm2)};`);
  assert.equal((await parents(a,f.workspace)).status,400);
  sql(`UPDATE public.farms SET status='ACTIVE' WHERE id=${q(f.farm2)};`);
  assert.equal((await parents(a,f.workspace)).status,200);
  const big=randomUUID();
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(big)},${q(a.id)},'Grande');
       INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name)
       SELECT gen_random_uuid(),${q(big)},${q(a.id)},${q(a.id)},'F'||g FROM generate_series(1,101) g;`);
  const tooLarge=await parents(a,big);
  assert.notEqual(tooLarge.status,200);
  assert.match(JSON.stringify(tooLarge.data),/too large/);
  assert.equal(sql(`SELECT count(*) FROM public.farms WHERE workspace_id=${q(f.workspace)};`),'3','read RPC never writes');
});
