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
  await db.exec(readFileSync(new URL('../proposals/g2-sync-own-data.sql',import.meta.url),'utf8'));
  const actor=randomUUID();
  await db.query('INSERT INTO auth.users(id) VALUES($1)',[actor]);
  return {db,actor};
}
async function call(db,actor,operation,payload,overrides={}) {
  const claims={sub:actor,role:'authenticated',is_anonymous:false,
    exp:Math.floor(Date.now()/1000)+600,...overrides};
  await db.exec('SET ROLE authenticated');
  await db.query("SELECT set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)",
    [actor,JSON.stringify(claims)]);
  try {return (await db.query('SELECT public.create_own_workspace($1::uuid,$2::jsonb) AS receipt',
    [operation,JSON.stringify(payload)])).rows[0].receipt;}
  finally {await db.exec('RESET ROLE');}
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
