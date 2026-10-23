// PostgreSQL contract tests with synthetic SQL claims; not evidence of JWT verification.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {PGlite} from '@electric-sql/pglite';

async function fixture() {
  const db=new PGlite();
  await db.exec(readFileSync(new URL('./fixtures/mock_supabase_environment.sql',import.meta.url),'utf8'));
  await db.exec('CREATE ROLE anon;');
  await db.exec("CREATE FUNCTION auth.jwt() RETURNS jsonb LANGUAGE sql STABLE AS $$ SELECT current_setting('request.jwt.claims',true)::jsonb $$;");
  for(const file of readdirSync(new URL('../migrations/',import.meta.url)).sort())
    await db.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
  for(const name of ['g1c-reservations.sql','g2-sync-own-data.sql'])
    await db.exec(readFileSync(new URL('../proposals/'+name,import.meta.url),'utf8'));
  const actor=randomUUID();
  await db.query('INSERT INTO auth.users(id) VALUES($1)',[actor]);
  return {db,actor};
}
async function asActor(db,actor,action,overrides={}) {
  const claims={iss:'https://fixture-project.example.test/auth/v1',sub:actor,role:'authenticated',is_anonymous:false,
    exp:Math.floor(Date.now()/1000)+600,...overrides};
  await db.exec('SET ROLE authenticated');
  await db.query("SELECT set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)",
    [actor,JSON.stringify(claims)]);
  try {return await action();}
  finally {await db.exec('RESET ROLE');}
}
async function call(db,actor,operation,payload,overrides={}) {
  return asActor(db,actor,async()=>(await db.query('SELECT public.create_own_workspace($1::uuid,$2::jsonb) AS receipt',
    [operation,JSON.stringify(payload)])).rows[0].receipt,overrides);
}
const workspace=()=>({id:randomUUID(),name:'Synthetic local workspace',countryCode:'ES',
  timezone:'Europe/Madrid',locale:'es-ES',currency:'EUR'});

test('workspace creation has an immutable recoverable receipt and never adopts an existing UUID',async()=>{
  const {db,actor}=await fixture();
  try {
    const payload=workspace(),operation=randomUUID();
    const first=await call(db,actor,operation,payload);
    assert.equal(first.entity_id,payload.id);
    assert.equal(first.remote_version,1);
    assert.equal(first.idempotent,false);
    assert.equal((await call(db,actor,operation,payload)).idempotent,true);
    await assert.rejects(call(db,actor,operation,{...payload,name:'Changed'}),e=>e.code==='23505');
    await assert.rejects(call(db,actor,randomUUID(),payload),e=>e.code==='23505');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM magina_g2_private.sync_operations')).rows[0].n,1);
    assert.equal((await db.query('SELECT owner_user_id FROM public.workspaces WHERE id=$1',[payload.id])).rows[0].owner_user_id,actor);
  } finally {await db.close();}
});

test('workspace creation rejects anonymous or invalid claims and malformed payload without writes',async()=>{
  const {db,actor}=await fixture();
  try {
    for(const claims of [{is_anonymous:true},{role:'anon'},{sub:randomUUID()},{exp:1},{exp:'9999999999'}])
      await assert.rejects(call(db,actor,randomUUID(),workspace(),claims),e=>e.code==='42501');
    const valid=workspace();
    for(const payload of [null,[],{...valid,name:' '},{...valid,ownerUserId:actor},
      {...valid,timezone:'Invalid/Timezone'},{...valid,countryCode:12},{...valid,currency:'eu'},
      {...valid,locale:null},{...valid,id:'not-a-uuid'}])
      await assert.rejects(call(db,actor,randomUUID(),payload),e=>e.code==='22023');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.workspaces')).rows[0].n,0);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM magina_g2_private.sync_operations')).rows[0].n,0);
  } finally {await db.close();}
});

async function reservedFarm(db,actor) {
  const ws=workspace();await call(db,actor,randomUUID(),ws);
  const request={payloadVersion:1,operationId:randomUUID(),farmId:randomUUID(),localWorkspaceId:randomUUID(),
    consentId:randomUUID(),consentTextVersion:2,manifestSha256:'a'.repeat(64),aggregateVersion:1};
  const receipt=await asActor(db,actor,async()=>(await db.query(
    'SELECT public.reserve_local_farm_g1c($1::jsonb,$2::uuid) AS receipt',[JSON.stringify(request),ws.id])).rows[0].receipt);
  return {request,receipt,payload:{id:request.farmId,workspaceId:ws.id,name:'Reserved farm',description:'Olivos antiguos',
    municipality:'Synthetic municipality',province:null,notes:'Historical notes',status:'ACTIVE',coverDocumentId:randomUUID()}};
}
async function farmCall(db,actor,operation,reservation,payload,overrides={}) {
  return asActor(db,actor,async()=>(await db.query(
    'SELECT public.create_reserved_farm($1::uuid,$2::uuid,$3::uuid,$4::jsonb) AS receipt',
    [operation,reservation.request.operationId,reservation.receipt.receiptId,JSON.stringify(payload)])).rows[0].receipt,overrides);
}

test('reserved farm creation persists complete fields and receipt together, replay never rewrites it',async()=>{
  const {db,actor}=await fixture();
  try {
    const farm=await reservedFarm(db,actor),operation=randomUUID();
    const first=await farmCall(db,actor,operation,farm,farm.payload);
    assert.equal(first.entity_id,farm.payload.id);assert.equal(first.remote_version,1);
    const row=(await db.query('SELECT * FROM public.farms WHERE id=$1',[farm.payload.id])).rows[0];
    assert.equal(row.creator_user_id,actor);assert.equal(row.owner_id,actor);
    assert.equal(row.cover_document_id,farm.payload.coverDocumentId);
    assert.equal(row.description,farm.payload.description);assert.equal(row.province,null);
    await db.query('UPDATE public.farms SET name=$1,version=2 WHERE id=$2',['Later name',farm.payload.id]);
    const replay=await farmCall(db,actor,operation,farm,farm.payload);
    assert.equal(replay.idempotent,true);assert.equal(replay.remote_version,1);
    assert.equal((await db.query('SELECT name FROM public.farms WHERE id=$1',[farm.payload.id])).rows[0].name,'Later name');
    await assert.rejects(farmCall(db,actor,randomUUID(),farm,farm.payload),e=>e.code==='23505');
  } finally {await db.close();}
});

test('reserved farm creation rejects mismatched reservation, actor, issuer or payload without rows',async()=>{
  const {db,actor}=await fixture();
  try {
    const farm=await reservedFarm(db,actor),operation=randomUUID(),other=randomUUID();
    await db.query('INSERT INTO auth.users(id) VALUES($1)',[other]);
    for(const reservation of [{...farm,request:{...farm.request,operationId:randomUUID()}},
      {...farm,receipt:{...farm.receipt,receiptId:randomUUID()}}])
      await assert.rejects(farmCall(db,actor,operation,reservation,farm.payload),e=>e.code==='42501');
    await assert.rejects(farmCall(db,other,operation,farm,farm.payload),e=>e.code==='42501');
    await assert.rejects(farmCall(db,actor,operation,farm,farm.payload,{iss:'https://other.example/auth/v1'}),e=>e.code==='42501');
    for(const claims of [{is_anonymous:true},{exp:1},{role:'anon'}])
      await assert.rejects(farmCall(db,actor,operation,farm,farm.payload,claims),e=>e.code==='42501');
    for(const payload of [{...farm.payload,ownerUserId:actor},{...farm.payload,notes:123},
      {...farm.payload,status:'DELETED'},{...farm.payload,coverDocumentId:'not-a-uuid'}])
      await assert.rejects(farmCall(db,actor,operation,farm,payload),e=>e.code==='22023');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.farms')).rows[0].n,0);
    assert.equal((await db.query("SELECT count(*)::int AS n FROM magina_g2_private.sync_operations WHERE entity_type='FARM'")).rows[0].n,0);
  } finally {await db.close();}
});
