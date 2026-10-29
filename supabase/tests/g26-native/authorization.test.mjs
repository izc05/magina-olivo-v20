import {before,test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHmac,randomUUID} from 'node:crypto';

const work=process.env.G26_NATIVE_WORK;
const project=process.env.G26_NATIVE_PROJECT;
assert.match(project??'',/^magina-g26-native-[0-9]+-[0-9]+$/);
const status=JSON.parse(readFileSync(join(work,'status.json'),'utf8'));
const api=new URL(status.API_URL);
assert.equal(api.protocol,'http:');
assert.ok(['localhost','127.0.0.1'].includes(api.hostname));
const container=`supabase_db_${project}`;
const issuer='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
const ids={a:randomUUID(),c:randomUUID(),w1:randomUUID(),w2:randomUUID(),wc:randomUUID()};
const actors={};
const quote=x=>`'${String(x).replaceAll("'","''")}'`;
const sql=s=>execFileSync('docker',['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'],{input:s,encoding:'utf8',timeout:30000}).trim();
const file=p=>readFileSync(new URL(p,new URL('../../../',import.meta.url)),'utf8');
const signed=claims=>{
  const part=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part(claims)}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
};
async function post(path,token,body,admin=false){
  const key=admin?status.SERVICE_ROLE_KEY:status.ANON_KEY;
  const response=await fetch(status.API_URL+path,{method:'POST',headers:{apikey:key,Authorization:`Bearer ${token??key}`,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  return {status:response.status,data:await response.json()};
}
async function get(path,token){
  const response=await fetch(status.API_URL+path,{headers:{apikey:status.ANON_KEY,Authorization:`Bearer ${token}`,Accept:'application/json'},signal:AbortSignal.timeout(20000)});
  return {status:response.status,data:await response.json()};
}
const list=(who,token=actors[who]?.token)=>post('/rest/v1/rpc/list_authorized_workspaces_v1',token,{});
const verify=(who,id,permission='read',token=actors[who]?.token)=>post('/rest/v1/rpc/verify_workspace_access_v1',token,{p_workspace_id:id,p_permission:permission});

before(async()=>{
  for(const who of ['A','C']){
    const email=`g26-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await post('/auth/v1/admin/users',null,{email,password,email_confirm:true},true);
    assert.equal(created.status,200);
    const login=await post('/auth/v1/token?grant_type=password',null,{email,password});
    assert.equal(login.status,200);
    const claims=JSON.parse(Buffer.from(login.data.access_token.split('.')[1],'base64url'));
    actors[who]={id:created.data.id,nativeToken:login.data.access_token,token:signed({...claims,iss:issuer})};
  }
  const migrations=['20261011120000_g0_auth_workspaces_shared_farms.sql','20261011120001_g0_agricultural_sync_schema.sql','20261011120002_g0_contracts_and_storage_rls.sql','20261016041222_g0_legacy_storage_parcel_hardening.sql','20261016041601_g0_aggregate_identity_guards.sql'];
  sql(`BEGIN;\n${migrations.map(n=>file(`supabase/migrations/${n}`)).join('\n')}\n${file('supabase/migrations/20261029044901_g26_workspace_authorization.sql')}\nCOMMIT; NOTIFY pgrst,'reload schema';`);
  sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES
    (${quote(ids.w1)},${quote(actors.A.id)},'A one'),
    (${quote(ids.w2)},${quote(actors.A.id)},'A two'),
    (${quote(ids.wc)},${quote(actors.C.id)},'C only');
    INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
    VALUES (${quote(ids.w2)},${quote(actors.C.id)},'VIEWER',ARRAY['read']);`);
  for(let i=0;i<40;i++){
    const response=await list('A');
    if(response.status===200) return;
    assert.ok([404,503].includes(response.status),JSON.stringify(response));
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  assert.fail('PostgREST did not load G2.6 RPC');
});

test('owner A lists two, C sees only owned and current membership, not A private workspace',async()=>{
  const a=await list('A'); assert.equal(a.status,200);
  assert.equal(a.data.contract_version,1);
  assert.equal(a.data.issuer,issuer);
  assert.equal(a.data.subject,actors.A.id);
  assert.deepEqual(a.data.workspaces.map(w=>w.id).sort(),[ids.w1,ids.w2].sort());
  assert.ok(a.data.workspaces.every(w=>new Date(w.authorization_expires_at)>new Date(a.data.verified_at)));
  const c=await list('C'); assert.equal(c.status,200);
  assert.deepEqual(c.data.workspaces.map(w=>w.id).sort(),[ids.w2,ids.wc].sort());
  assert.equal((await verify('C',ids.w1)).data.authorized,false);
  assert.equal((await verify('C',ids.w2,'write')).data.authorized,false);
  assert.equal((await verify('C',ids.w2,'read')).data.authorized,true);
  assert.deepEqual((await get(`/rest/v1/workspaces?id=eq.${ids.w1}&select=id`,actors.C.token)).data,[]);
});

test('revocation, expiry, deleted workspace and connection recovery fail closed',async()=>{
  sql(`UPDATE public.workspace_memberships SET status='REVOKED',revoked_at=now(),version=version+1 WHERE workspace_id=${quote(ids.w2)} AND user_id=${quote(actors.C.id)};`);
  assert.equal((await verify('C',ids.w2)).data.authorized,false);
  assert.deepEqual((await list('C')).data.workspaces.map(w=>w.id),[ids.wc]);
  assert.deepEqual((await get(`/rest/v1/workspaces?id=eq.${ids.w2}&select=id`,actors.C.token)).data,[]);
  const claims=JSON.parse(Buffer.from(actors.A.token.split('.')[1],'base64url'));
  const expired=await list('A',signed({...claims,exp:Math.floor(Date.now()/1000)-10}));
  assert.equal(expired.status,403);
  assert.equal(expired.data.code,'42501');
  const foreignToken=signed({...claims,iss:'https://other.example/auth/v1'});
  assert.equal((await list('A',foreignToken)).status,403);
  assert.deepEqual((await get(`/rest/v1/workspaces?id=eq.${ids.w1}&select=id`,foreignToken)).data,[]);
  assert.equal((await list('A',status.ANON_KEY)).status,401);
  assert.equal((await verify('A',randomUUID())).data.authorized,false);
  sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${quote(ids.w1)};`);
  assert.equal((await verify('A',ids.w1)).data.authorized,false);
  sql(`UPDATE public.workspace_memberships SET status='ACTIVE',revoked_at=NULL,version=version+1 WHERE workspace_id=${quote(ids.w2)} AND user_id=${quote(actors.C.id)};`);
  assert.equal((await verify('C',ids.w2)).data.authorized,true);
  sql(`UPDATE public.workspace_memberships SET valid_from=now()-interval '2 days',valid_until=now()-interval '1 day',version=version+1 WHERE workspace_id=${quote(ids.w2)} AND user_id=${quote(actors.C.id)};`);
  assert.equal((await verify('C',ids.w2)).data.authorized,false);
});

test('RLS denies C write to A workspace and direct membership manipulation',async()=>{
  const c=actors.C.token;
  const update=await fetch(status.API_URL+`/rest/v1/workspaces?id=eq.${ids.w1}`,{method:'PATCH',headers:{apikey:status.ANON_KEY,Authorization:`Bearer ${c}`,'Content-Type':'application/json',Prefer:'return=representation'},body:JSON.stringify({name:'cross-account'}),signal:AbortSignal.timeout(20000)});
  assert.equal(update.status,200);
  assert.deepEqual(await update.json(),[]);
  const grant=await fetch(status.API_URL+'/rest/v1/workspace_memberships',{method:'POST',headers:{apikey:status.ANON_KEY,Authorization:`Bearer ${c}`,'Content-Type':'application/json'},body:JSON.stringify({workspace_id:ids.w1,user_id:actors.C.id,role:'MANAGER',permissions:['read','write']}),signal:AbortSignal.timeout(20000)});
  assert.ok([401,403].includes(grant.status));
  assert.equal(sql(`SELECT name FROM public.workspaces WHERE id=${quote(ids.w1)};`),'A one');
});
