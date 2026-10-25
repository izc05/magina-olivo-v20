import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { randomUUID, createHash } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';

const A=randomUUID(), B=randomUUID(), wa=randomUUID(), wb=randomUUID();
const issuer='https://zubpicdibqbmewxeurhm.supabase.co';
const request=()=>({payloadVersion:1,operationId:randomUUID(),farmId:randomUUID(),localWorkspaceId:randomUUID(),consentId:randomUUID(),consentTextVersion:1,manifestSha256:'a'.repeat(64),aggregateVersion:1});
function hash(r) {
  const h=createHash('sha256');
  for(const x of ['magina-reservation',r.payloadVersion,r.operationId,r.farmId,r.localWorkspaceId,r.consentId,r.consentTextVersion,r.manifestSha256,r.aggregateVersion]) {
    const b=Buffer.from(String(x)), n=Buffer.alloc(4);n.writeInt32BE(b.length);h.update(n);h.update(b);
  }
  return h.digest('hex');
}
async function fixture() {
  const db=new PGlite();
  await db.exec(readFileSync(new URL('./fixtures/mock_supabase_environment.sql',import.meta.url),'utf8'));
  await db.exec('CREATE ROLE anon;');
  for(const file of readdirSync(new URL('../migrations/',import.meta.url)).sort()) await db.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
  await db.exec(`GRANT USAGE ON SCHEMA public,auth TO authenticated; GRANT ALL ON ALL TABLES IN SCHEMA public TO authenticated;
    INSERT INTO auth.users(id) VALUES('${A}'),('${B}'); INSERT INTO public.workspaces(id,owner_user_id,name) VALUES('${wa}','${A}','A'),('${wb}','${B}','B');`);
  // Apply only the proposal to this disposable in-memory PostgreSQL.
  await db.exec(readFileSync(new URL('../proposals/g1c-reservations.sql',import.meta.url),'utf8'));
  return db;
}
async function actor(db,id,fn,overrides={}) {
  const claims={iss:issuer+'/auth/v1',sub:id,role:'authenticated',exp:Math.floor(Date.now()/1000)+3600,is_anonymous:false,...overrides};
  await db.exec(`SET ROLE authenticated; SELECT set_config('request.jwt.claim.sub','${id??''}',false);`);
  await db.query("SELECT set_config('request.jwt.claims',$1,false)",[JSON.stringify(claims)]);
  try{return await fn();}finally{await db.exec('RESET ROLE');}
}
async function reserve(db,id,r,ws=wa,claims={}) {return actor(db,id,async()=> (await db.query('SELECT public.reserve_local_farm_g1c($1::jsonb,$2::uuid) AS receipt',[JSON.stringify(r),ws])).rows[0].receipt,claims);}

test('G1c exact replay returns immutable seven-field receipt and canonical hash without agricultural writes',async()=>{
  const db=await fixture();try {
    const r=request(), first=await reserve(db,A,r);
    assert.deepEqual(first,{issuer,actorId:A,operationId:r.operationId,farmId:r.farmId,payloadHash:hash(r),remoteWorkspaceId:wa,receiptId:first.receiptId});
    assert.deepEqual(await reserve(db,A,r),first);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.farms')).rows[0].n,0);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM magina_g1c_private.g1c_reservations')).rows[0].n,1);
    await assert.rejects(db.exec('UPDATE magina_g1c_private.g1c_reservations SET receipt_id=gen_random_uuid()'),/immutable/);
    await assert.rejects(db.exec('DELETE FROM magina_g1c_private.g1c_reservations'),/immutable/);
  } finally{await db.close();}
});
test('G1c denies absent actor, foreign/deleted workspace, malformed or extra authority fields',async()=>{
  const db=await fixture();try {
    await assert.rejects(reserve(db,null,request()),/denied/);
    await assert.rejects(reserve(db,B,request(),wa),/denied/);
    for(const r of [null,[],{...request(),payloadVersion:null},{...request(),payloadVersion:'1'},{...request(),aggregateVersion:'1'},
      {...request(),manifestSha256:123},{...request(),actorId:B},{...request(),payloadHash:'f'.repeat(64)},{...request(),payloadVersion:2},
      {...request(),aggregateVersion:0},{...request(),manifestSha256:'A'.repeat(64)}]) await assert.rejects(reserve(db,A,r),/invalid/);
    await db.exec(`UPDATE public.workspaces SET deleted_at=now() WHERE id='${wa}'`);
    await assert.rejects(reserve(db,A,request()),/denied/);
  }finally{await db.close();}
});
test('G1c rejects anonymous sessions, wrong role, expired/malformed claims even for workspace owner',async()=>{
  const db=await fixture();try {
    for(const claims of [{is_anonymous:true},{role:'anon'},{exp:1},{exp:'9999999999'},{sub:B}]) {
      await assert.rejects(reserve(db,A,request(),wa,claims),e=>e.code==='42501'&&e.message==='reservation denied');
    }
  }finally{await db.close();}
});
test('G1c rejects a foreign HTTPS issuer for the same authenticated account',async()=>{
  const db=await fixture();try {
    await assert.rejects(reserve(db,A,request(),wa,{iss:'https://other-project.example.test/auth/v1'}),
      e=>e.code==='42501'&&e.message==='reservation denied');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM magina_g1c_private.g1c_reservations')).rows[0].n,0);
  }finally{await db.close();}
});
test('G1c execute grants and RLS catalog are narrow; revocation applies to replay',async()=>{
  const db=await fixture();try {
    const r=request();await reserve(db,A,r);
    assert.deepEqual((await db.query(`SELECT relrowsecurity FROM pg_class WHERE oid='magina_g1c_private.g1c_reservations'::regclass`)).rows,[{relrowsecurity:true}]);
    const grants=(await db.query(`SELECT has_function_privilege('anon','public.reserve_local_farm_g1c(jsonb,uuid)','EXECUTE') AS anon,
      has_table_privilege('authenticated','magina_g1c_private.g1c_reservations','SELECT,INSERT,UPDATE,DELETE') AS direct,
      has_function_privilege('authenticated','public.reserve_local_farm_g1c(jsonb,uuid)','EXECUTE') AS authenticated`)).rows[0];
    assert.deepEqual(grants,{anon:false,direct:false,authenticated:true});
    await db.exec('SET ROLE anon');
    try{await assert.rejects(db.query('SELECT public.reserve_local_farm_g1c($1::jsonb,$2::uuid)',[JSON.stringify(r),wa]),/permission denied/);}finally{await db.exec('RESET ROLE');}
    await db.exec(`UPDATE public.workspaces SET deleted_at=now() WHERE id='${wa}'`);
    await assert.rejects(reserve(db,A,r),/denied/);
  }finally{await db.close();}
});
test('G1c same operation changes and two accounts sharing farm UUID conflict generically; no foreign receipt leaks',async()=>{
  const db=await fixture();try {
    const r=request();await reserve(db,A,r);
    for(const changed of [{...r,consentTextVersion:2},{...r,farmId:randomUUID()}]) await assert.rejects(reserve(db,A,changed),e=>e.code==='23505'&&e.message==='reservation conflict');
    await assert.rejects(reserve(db,B,{...r,operationId:randomUUID()},wb),e=>e.code==='23505'&&e.message==='reservation conflict');
    for(const sql of ['SELECT * FROM magina_g1c_private.g1c_reservations','INSERT INTO magina_g1c_private.g1c_reservations DEFAULT VALUES','UPDATE magina_g1c_private.g1c_reservations SET actor_id=auth.uid()']) await assert.rejects(actor(db,B,()=>db.exec(sql)),/permission denied/);
  }finally{await db.close();}
});
test('G1c legacy UUID is never adopted and reserved UUID direct farm INSERT cannot be stolen',async()=>{
  const db=await fixture();try {
    const legacy=request();await db.query('INSERT INTO public.farms(id,owner_id,name) VALUES($1,$2,$3)',[legacy.farmId,B,'legacy original']);
    await assert.rejects(reserve(db,A,legacy),e=>e.code==='23505'&&e.message==='reservation conflict');
    assert.deepEqual((await db.query('SELECT owner_id,name,workspace_id FROM public.farms WHERE id=$1',[legacy.farmId])).rows,[{owner_id:B,name:'legacy original',workspace_id:null}]);
    const r=request();await reserve(db,A,r);
    await assert.rejects(actor(db,B,()=>db.query('INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES($1,$2,$3,$3,$4)',[r.farmId,wb,B,'theft'])),/reservation conflict/);
    await assert.rejects(actor(db,A,()=>db.query('INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES($1,$2,$3,$3,$4)',[r.farmId,wb,A,'wrong workspace'])),/reservation conflict/);
    await actor(db,A,()=>db.query('INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES($1,$2,$3,$3,$4)',[r.farmId,wa,A,'authorized existing path']));
    const other=randomUUID();await db.query('INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name) VALUES($1,$2,$3,$3,$4)',[other,wb,B,'unreserved']);
    await assert.rejects(actor(db,B,()=>db.query('UPDATE public.farms SET id=$1 WHERE id=$2',[r.farmId,other])),/Cannot alter farm access principals/);
    for(const [column,value] of [['workspace_id',wb],['creator_user_id',B],['owner_id',B]]) await assert.rejects(actor(db,A,()=>db.query(`UPDATE public.farms SET ${column}=$1 WHERE id=$2`,[value,r.farmId])),/Cannot alter farm access principals/);
  }finally{await db.close();}
});
