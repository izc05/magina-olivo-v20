import {before, test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHmac,randomUUID} from 'node:crypto';

const work=process.env.G25_NATIVE_WORK;
const project=process.env.G25_NATIVE_PROJECT;
assert.match(project??'',/^magina-g25-native-[0-9]+-[0-9]+$/);
const status=JSON.parse(readFileSync(join(work,'status.json'),'utf8'));
const api=new URL(status.API_URL);
assert.equal(api.protocol,'http:');
assert.ok(['localhost','127.0.0.1'].includes(api.hostname));
assert.ok(status.ANON_KEY && status.SERVICE_ROLE_KEY && status.JWT_SECRET);
const container=`supabase_db_${project}`;
const actor={};
const delivery='99999999-9999-4999-8999-999999999999';
const q=s=>`'${String(s).replaceAll("'","''")}'`;
const sql=s=>execFileSync('docker',['exec','-i',container,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At'],{input:s,encoding:'utf8',timeout:30000,stdio:['pipe','pipe','pipe']}).trim();
const root=new URL('../../../',import.meta.url);
const file=p=>readFileSync(new URL(p,root),'utf8');
const prereq=name=>execFileSync('git',['show',`21bac42e9d8621b2ad0be6e68ed0e712c16868c5:supabase/proposals/${name}.sql`],{encoding:'utf8'});
const signed=claims=>{
  const part=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
  const body=`${part({alg:'HS256',typ:'JWT'})}.${part(claims)}`;
  return `${body}.${createHmac('sha256',status.JWT_SECRET).update(body).digest('base64url')}`;
};
async function http(path,token,body,admin=false){
  const key=admin?status.SERVICE_ROLE_KEY:status.ANON_KEY;
  const res=await fetch(status.API_URL+path,{method:'POST',headers:{apikey:key,Authorization:`Bearer ${token??key}`,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  return {status:res.status,data:await res.json()};
}
const rpc=(who,token=actor[who]?.token)=>http('/rest/v1/rpc/get_own_delivery_receive_v1',token,{p_delivery_id:delivery});

before(async()=>{
  for(const who of ['A','C']){
    const email=`g25-${randomUUID()}@example.test`,password=`Fixture!${randomUUID()}`;
    const created=await http('/auth/v1/admin/users',null,{email,password,email_confirm:true},true);
    assert.equal(created.status,200);
    const login=await http('/auth/v1/token?grant_type=password',null,{email,password});
    assert.equal(login.status,200);
    const claims=JSON.parse(Buffer.from(login.data.access_token.split('.')[1],'base64url'));
    actor[who]={id:created.data.id,nativeToken:login.data.access_token,
      token:signed({...claims,iss:'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'})};
  }
  const g0=['20261011120000_g0_auth_workspaces_shared_farms.sql',
    '20261011120001_g0_agricultural_sync_schema.sql',
    '20261011120002_g0_contracts_and_storage_rls.sql',
    '20261016041222_g0_legacy_storage_parcel_hardening.sql',
    '20261016041601_g0_aggregate_identity_guards.sql'];
  sql(`BEGIN;\n${g0.map(n=>file(`supabase/migrations/${n}`)).join('\n')}\n${prereq('g1c-reservations')}\n${prereq('g2-sync-own-data')}\n${prereq('g2-parcel-bootstrap')}\n${file('supabase/migrations/20261027155737_g25_receive_bundle_contract.sql')}\nCOMMIT; NOTIFY pgrst,'reload schema';`);
  const fixture=file('supabase/tests/g25-receive-native.sql');
  const seed=fixture.split('-- BEGIN G25 SEED')[1]?.split('-- END G25 SEED')[0];
  assert.ok(seed);
  sql(seed.replaceAll('008ea900-5f3c-407f-8917-ad72bc9d955b',actor.A.id)
    .replaceAll('49d1dbbb-d974-4eca-8bd7-1e51c83253f5',actor.C.id));
  let ready=false;
  for(let i=0;i<40;i++){
    const result=await rpc('A');
    if(result.status===400 && /unverified origin/.test(result.data.message??'')){ready=true;break;}
    assert.ok([404,503].includes(result.status),`Unexpected schema status ${result.status}`);
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  assert.ok(ready);
});

test('native Auth/PostgREST accepts only complete A bundle and denies C and other issuer',async()=>{
  const update=t=>sql(`UPDATE public.${t} SET origin_created_at='2026-10-25 00:00+00',origin_updated_at='2026-10-25 00:00+00',origin_version=1,origin_evidence_sha256=repeat('b',64);`);
  for(const t of ['campaign_parcels','harvest_parcels','delivery_parcels']) update(t);
  const a=await rpc('A');
  assert.equal(a.status,200);
  assert.equal(a.data.account_id,actor.A.id);
  assert.equal(a.data.delivery.id,delivery);
  assert.equal(Number(a.data.delivery.net_grams),1540000);
  assert.equal(a.data.head_payload_hash,'a'.repeat(64));
  const c=await rpc('C');
  assert.equal(c.status,403);
  assert.equal(await rpc('A',actor.A.nativeToken).then(x=>x.status),403);
  const claims=JSON.parse(Buffer.from(actor.A.token.split('.')[1],'base64url'));
  assert.equal(await rpc('A',signed({...claims,iss:'https://other-project.supabase.co/auth/v1'})).then(x=>x.status),403);
  assert.equal(await rpc('A',signed({...claims,exp:Math.floor(Date.now()/1000)-60})).then(x=>x.status),401);
  assert.equal(await rpc('A',status.ANON_KEY).then(x=>x.status),401);
  assert.equal(sql(`SELECT has_table_privilege('authenticated','public.harvest_parcels','SELECT');`),'f');
});

test('native RPC rejects crossed relationships, incomplete origin, changed HEAD',async()=>{
  sql("UPDATE public.harvest_parcels SET parcel_id='33333333-3333-4333-8333-333333333334';");
  assert.equal((await rpc('A')).status,400);
  sql("UPDATE public.harvest_parcels SET parcel_id='33333333-3333-4333-8333-333333333333';");
  sql('UPDATE public.delivery_parcels SET origin_created_at=NULL,origin_updated_at=NULL,origin_version=NULL,origin_evidence_sha256=NULL;');
  assert.equal((await rpc('A')).status,400);
  sql("UPDATE public.delivery_parcels SET origin_created_at='2026-10-25 00:00+00',origin_updated_at='2026-10-25 00:00+00',origin_version=1,origin_evidence_sha256=repeat('b',64);");
  sql(`INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version,payload_version)
    VALUES(${q(actor.A.id)},${q(randomUUID())},'DELIVERY',${q(delivery)},repeat('c',64),1,2);`);
  const changed=await rpc('A');
  assert.equal(changed.status,400);
  assert.match(changed.data.message??'',/head changed/);
});
