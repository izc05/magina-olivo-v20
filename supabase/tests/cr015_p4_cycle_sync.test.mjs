// CR-015 P4 contract tests on embedded PostgreSQL (PGlite) with synthetic claims.
// Not evidence of JWT verification or of the deployed DEV schema.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {PGlite} from '@electric-sql/pglite';

const ISSUER='https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
const file=(dir,name)=>readFileSync(new URL(`../${dir}/${name}`,import.meta.url),'utf8');

async function fixture() {
  const db=new PGlite();
  await db.exec(readFileSync(new URL('./fixtures/mock_supabase_environment.sql',import.meta.url),'utf8'));
  await db.exec('CREATE ROLE anon;');
  await db.exec("CREATE FUNCTION auth.jwt() RETURNS jsonb LANGUAGE sql STABLE AS $$ SELECT current_setting('request.jwt.claims',true)::jsonb $$;");
  for(const name of ['20261011120000_g0_auth_workspaces_shared_farms.sql','20261011120001_g0_agricultural_sync_schema.sql',
    '20261011120002_g0_contracts_and_storage_rls.sql','20261016041222_g0_legacy_storage_parcel_hardening.sql',
    '20261016041601_g0_aggregate_identity_guards.sql']) await db.exec(file('migrations',name));
  for(const name of ['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql']) await db.exec(file('proposals',name));
  await db.exec(file('proposals','cr015-p4-cycle-sync-v1.sql'));
  const owner=randomUUID(),other=randomUUID(),workspace=randomUUID(),farm=randomUUID();
  for(const id of [owner,other]) await db.query('INSERT INTO auth.users(id) VALUES($1)',[id]);
  await db.query("INSERT INTO public.workspaces(id,owner_user_id,name) VALUES($1,$2,'W')",[workspace,owner]);
  await db.query("INSERT INTO public.farms(id,workspace_id,creator_user_id,name) VALUES($1,$2,$3,'F')",[farm,workspace,owner]);
  return {db,owner,other,workspace,farm};
}
async function campaign(f,status='ACTIVE') {
  const id=randomUUID();
  await f.db.query("INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,status) VALUES($1,$2,$3,'R','2026-11-01',$4)",
    [id,f.workspace,f.farm,status]);
  return id;
}
async function as(db,actor,action,claims={}) {
  await db.exec('SET ROLE authenticated');
  await db.query("SELECT set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)",
    [actor,JSON.stringify({iss:ISSUER,sub:actor,role:'authenticated',is_anonymous:false,exp:Math.floor(Date.now()/1000)+600,...claims})]);
  try {return await action();} finally {await db.exec('RESET ROLE');}
}
const put=(f,op,expected,state,actor=f.owner,farm=f.farm)=>as(f.db,actor,async()=>(await f.db.query(
  'SELECT public.put_own_farm_cycles_v1($1::uuid,$2::uuid,$3::bigint,$4::jsonb) AS r',[op,farm,expected,JSON.stringify(state)])).rows[0].r);
const get=(f,actor=f.owner)=>as(f.db,actor,async()=>(await f.db.query('SELECT public.get_own_farm_cycles_v1($1::uuid) AS r',[f.farm])).rows[0].r);
const cycle=(id,start,end=null,extra={})=>({id,label:'2026/27',startDate:start,effectiveEndDate:end,
  closedAt:end?'2027-02-01T10:00:00.123456Z':null,noRecollection:false,...extra});
const state=(f,cycles,recollections=[])=>({workspaceId:f.workspace,cycles,recollections});
const reason=code=>e=>e.code==='23505' && e.message.endsWith(code);

test('create, receive and close a cycle with a recollection; replays are idempotent',async()=>{
  const f=await fixture();
  try {
    const c=randomUUID(),r=await campaign(f),op=randomUUID();
    const first=await put(f,op,0,state(f,[cycle(c,'2026-11-01')],[{campaignId:r,cycleId:c}]));
    assert.deepEqual([first.remote_version,first.idempotent,first.status],[1,false,'APPLIED']);
    assert.equal((await put(f,op,0,state(f,[cycle(c,'2026-11-01')],[{campaignId:r,cycleId:c}]))).idempotent,true);
    await assert.rejects(put(f,op,0,state(f,[cycle(c,'2026-11-02')])),reason('OPERATION_REUSED'));
    const head=await get(f);
    assert.equal(head.revision,1);
    assert.deepEqual(head.recollections,[{campaignId:r,cycleId:c}]);
    assert.equal(head.cycles[0].closedAt,null);
    // Closing needs the recollection finished on the server first.
    const closed=state(f,[cycle(c,'2026-11-01','2027-01-31')],[{campaignId:r,cycleId:c}]);
    await assert.rejects(put(f,randomUUID(),1,closed),reason('RECOLLECTION_NOT_CLOSED'));
    await f.db.query("UPDATE public.campaigns SET status='CLOSED' WHERE id=$1",[r]);
    assert.equal((await put(f,randomUUID(),1,closed)).remote_version,2);
    const after=await get(f);
    assert.equal(after.cycles[0].effectiveEndDate,'2027-01-31');
    assert.equal(after.cycles[0].closedAt,'2027-02-01T10:00:00.123456Z');
  } finally {await f.db.close();}
});

test('two devices on the same revision: the second gets a visible stale conflict, nothing is overwritten',async()=>{
  const f=await fixture();
  try {
    const a=randomUUID(),b=randomUUID();
    await put(f,randomUUID(),0,state(f,[cycle(a,'2026-11-01')]));
    await assert.rejects(put(f,randomUUID(),0,state(f,[cycle(b,'2026-10-01')])),reason('STALE_REVISION'));
    const head=await get(f);
    assert.deepEqual(head.cycles.map(x=>x.id),[a]);
    assert.equal((await f.db.query("SELECT count(*)::int n FROM magina_g2_private.sync_operations WHERE entity_type='FARM_CYCLES'")).rows[0].n,1);
  } finally {await f.db.close();}
});

test('a closed cycle keeps its history: no reopening, no change, no removal, no recollection leaving it',async()=>{
  const f=await fixture();
  try {
    const old=randomUUID(),next=randomUUID(),r=await campaign(f,'CLOSED');
    await put(f,randomUUID(),0,state(f,[cycle(old,'2025-11-01','2026-01-31')],[{campaignId:r,cycleId:old}]));
    const open=cycle(next,'2026-02-01');
    await assert.rejects(put(f,randomUUID(),1,state(f,[cycle(old,'2025-11-01'),open],[{campaignId:r,cycleId:old}])),
      e=>e.code==='22023' || reason('CLOSED_CYCLE_CHANGED')(e));
    await assert.rejects(put(f,randomUUID(),1,state(f,[cycle(old,'2025-11-01','2026-01-30'),open],[{campaignId:r,cycleId:old}])),
      reason('CLOSED_CYCLE_CHANGED'));
    await assert.rejects(put(f,randomUUID(),1,state(f,[open])),reason('CYCLE_REMOVED'));
    await assert.rejects(put(f,randomUUID(),1,state(f,[cycle(old,'2025-11-01','2026-01-31'),open])),reason('LINK_REMOVED'));
    await assert.rejects(put(f,randomUUID(),1,state(f,[cycle(old,'2025-11-01','2026-01-31'),open],[{campaignId:r,cycleId:next}])),
      reason('RECOLLECTION_LEFT_CLOSED_CYCLE'));
    assert.equal((await put(f,randomUUID(),1,state(f,[cycle(old,'2025-11-01','2026-01-31'),open],[{campaignId:r,cycleId:old}]))).remote_version,2);
  } finally {await f.db.close();}
});

test('invalid states are rejected before any write',async()=>{
  const f=await fixture();
  try {
    const a=randomUUID(),b=randomUUID();
    for(const bad of [
      state(f,[cycle(a,'2026-11-01'),cycle(b,'2027-11-01')]),                 // two open cycles
      state(f,[cycle(a,'2026-01-01','2026-12-31'),cycle(b,'2026-12-01')]),    // overlap
      state(f,[cycle(a,'2026-11-01','2026-10-01')]),                          // end before start
      state(f,[{...cycle(a,'2026-11-01'),closedAt:'2026-11-02T00:00:00Z'}]),  // closedAt without end
      state(f,[cycle(a,'2026-11-01')],[{campaignId:randomUUID(),cycleId:b}]), // link to a cycle not in the state
      state(f,[cycle(a,'2026-11-01',null,{noRecollection:true})],[{campaignId:randomUUID(),cycleId:a}]),
      state(f,[cycle(a,'2026-11-01',null,{label:' 2026/27'})]),
      {...state(f,[]),extra:1},
    ]) await assert.rejects(put(f,randomUUID(),0,bad),e=>e.code==='22023');
    await assert.rejects(put(f,randomUUID(),0,state(f,[cycle(a,'2026-11-01')],[{campaignId:randomUUID(),cycleId:a}])),
      reason('PARENT_NOT_SYNCED'));
    assert.equal((await f.db.query('SELECT count(*)::int n FROM public.agricultural_cycles')).rows[0].n,0);
  } finally {await f.db.close();}
});

test('only the workspace owner reads or writes; expired or foreign tokens are denied; tables are closed',async()=>{
  const f=await fixture();
  try {
    const s=state(f,[cycle(randomUUID(),'2026-11-01')]);
    await assert.rejects(put(f,randomUUID(),0,s,f.other),e=>e.code==='42501');
    await assert.rejects(get(f,f.other),e=>e.code==='42501');
    await assert.rejects(as(f.db,f.owner,()=>f.db.query('SELECT public.get_own_farm_cycles_v1($1::uuid)',[f.farm]),
      {exp:Math.floor(Date.now()/1000)-1}),e=>e.code==='42501');
    await assert.rejects(as(f.db,f.owner,()=>f.db.query('SELECT public.get_own_farm_cycles_v1($1::uuid)',[f.farm]),
      {iss:'https://evil.example/auth/v1'}),e=>e.code==='42501');
    await assert.rejects(as(f.db,f.owner,()=>f.db.query('SELECT * FROM public.agricultural_cycles')),e=>e.code==='42501');
    await assert.rejects(as(f.db,f.owner,()=>f.db.query("INSERT INTO public.agricultural_cycle_farm_heads VALUES($1,$2,9)",
      [f.farm,f.workspace])),e=>e.code==='42501');
    assert.equal((await get(f)).revision,0);
  } finally {await f.db.close();}
});
