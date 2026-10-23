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
  for(const name of ['g1c-reservations.sql','g2-sync-own-data.sql','g2-parcel-bootstrap.sql'])
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

function parcelPayload(workspaceId,farmId) {
  return {id:randomUUID(),workspaceId,displayName:'Parcela sintética',cadastralReference:'SYNTHETIC-REF',
    cadastralPolygon:'03',cadastralParcel:'17',municipality:'Synthetic municipality',province:null,
    source:'CATASTRO',geometryGeoJson:'{"type":"Polygon","coordinates":[]}',sourceProvider:'ES_CATASTRO',
    sourceImportedAt:'2026-01-01T23:59:59.999999999Z',cadastralAreaM2:2000.125,managedAreaM2:1500.25,
    notes:'Do not lose notes',status:'ACTIVE',oliveTreeCount:100,variety:'Picual',irrigationSystem:'DRIP',
    irrigationNetwork:'Synthetic community',irrigationSector:'S3',irrigationDays:'MONDAY,FRIDAY',
    memberships:[{id:randomUUID(),farmId,validFrom:'2026-01-01T00:00:00.123456789Z',validUntil:null,deletedAt:null}]};
}
async function parcelCall(db,actor,operation,payload) {
  return asActor(db,actor,async()=>(await db.query('SELECT public.create_own_parcel($1::uuid,$2::jsonb) AS receipt',
    [operation,JSON.stringify(payload)])).rows[0].receipt);
}
test('parcel bootstrap preserves agronomy, exact instants and membership with one recoverable receipt',async()=>{
  const {db,actor}=await fixture();
  try {
    const farm=await reservedFarm(db,actor);await farmCall(db,actor,randomUUID(),farm,farm.payload);
    const payload=parcelPayload(farm.payload.workspaceId,farm.payload.id),operation=randomUUID();
    const first=await parcelCall(db,actor,operation,payload);assert.equal(first.entity_id,payload.id);
    assert.equal((await parcelCall(db,actor,operation,payload)).idempotent,true);
    const restored=await asActor(db,actor,async()=>(await db.query('SELECT public.get_own_parcel($1::uuid) AS data',[payload.id])).rows[0].data);
    assert.equal(restored.parcel.irrigation_network,'Synthetic community');assert.equal(restored.parcel.olive_tree_count,100);
    assert.equal(restored.parcel.geometry_geo_json,payload.geometryGeoJson);
    assert.equal(restored.parcel.source_imported_at_exact,payload.sourceImportedAt);
    assert.equal(restored.memberships[0].valid_from_exact,payload.memberships[0].validFrom);
    assert.equal(restored.memberships[0].valid_until,null);
    await db.query("UPDATE public.parcels SET source_imported_at='2026-02-01T00:00:00Z' WHERE id=$1",[payload.id]);
    assert.equal((await db.query('SELECT source_imported_at_exact FROM public.parcels WHERE id=$1',[payload.id])).rows[0].source_imported_at_exact,null);
    await assert.rejects(parcelCall(db,actor,randomUUID(),payload),e=>e.code==='23505');
  } finally {await db.close();}
});
test('parcel bootstrap rejects overlapping memberships and malformed fields without partial persistence',async()=>{
  const {db,actor}=await fixture();
  try {
    const farm=await reservedFarm(db,actor);await farmCall(db,actor,randomUUID(),farm,farm.payload);
    const p=parcelPayload(farm.payload.workspaceId,farm.payload.id),m=p.memberships[0];
    for(const payload of [{...p,oliveTreeCount:1.5},{...p,managedAreaM2:-1},{...p,ownerUserId:actor},
      {...p,memberships:[]},{...p,memberships:[m,{...m,id:randomUUID(),validFrom:'2026-01-02T00:00:00Z'}]},
      {...p,memberships:[{...m,validUntil:'2025-01-01T00:00:00Z'}]},
      {...p,memberships:[{...m,validFrom:'2026-01-01T00:00:00.000000001Z',validUntil:'2026-01-01T00:00:00.000000003Z'},
        {...m,id:randomUUID(),validFrom:'2026-01-01T00:00:00.000000002Z',validUntil:'2026-01-01T00:00:00.000000004Z'}]},
      {...p,sourceImportedAt:'2026-02-30T00:00:00Z'}])
      await assert.rejects(parcelCall(db,actor,randomUUID(),payload),e=>e.code==='22023');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.parcels')).rows[0].n,0);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.farm_parcel_memberships')).rows[0].n,0);
    assert.equal((await db.query("SELECT count(*)::int AS n FROM magina_g2_private.sync_operations WHERE entity_type='PARCEL'")).rows[0].n,0);
  } finally {await db.close();}
});

test('empty membership cannot authorize delivery while one nanosecond remains a positive interval',async()=>{
  const {db,actor}=await fixture();
  try {
    const farm=await reservedFarm(db,actor);await farmCall(db,actor,randomUUID(),farm,farm.payload);
    for(const [end,allowed] of [['2026-11-25T12:00:00.000000001Z',false],['2026-11-25T12:00:00.000000002Z',true]]) {
      const parcel=parcelPayload(farm.payload.workspaceId,farm.payload.id);
      parcel.memberships[0].validFrom='2026-11-25T12:00:00.000000001Z';parcel.memberships[0].validUntil=end;
      await parcelCall(db,actor,randomUUID(),parcel);
      const campaign={id:randomUUID(),workspaceId:parcel.workspaceId,farmId:farm.payload.id,name:'Interval campaign',
        startDate:'2026-11-25',endDate:'2026-11-25',status:'CLOSED',notes:null,parcels:[{
          id:randomUUID(),parcelId:parcel.id,farmIdAtStart:farm.payload.id,farmNameAtStart:'Farm',parcelNameAtStart:'Parcel',
          managedAreaM2AtStart:null,cadastralReferenceAtStart:null,geometryGeoJsonSnapshot:null}]};
      await campaignCall(db,actor,randomUUID(),campaign);
      const delivery={id:randomUUID(),farmId:farm.payload.id,campaignId:campaign.id,deliveryDate:'2026-11-25',
        destinationName:'Synthetic destination',netGrams:1000,source:'MANUAL',parcels:[{id:randomUUID(),parcelId:parcel.id,
          campaignParcelId:campaign.parcels[0].id,parcelNameAtDelivery:'Parcel',weightGrams:null,allocationMode:'UNALLOCATED'}]};
      const send=()=>asActor(db,actor,()=>db.query('SELECT public.sync_own_delivery_v2($1::uuid,$2::jsonb)',[randomUUID(),JSON.stringify(delivery)]));
      if(allowed) await send();else await assert.rejects(send(),e=>e.code==='42501');
    }
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.deliveries')).rows[0].n,1);
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

async function campaignFixture(db,actor) {
  const farm=await reservedFarm(db,actor);await farmCall(db,actor,randomUUID(),farm,farm.payload);
  const parcel=randomUUID();
  await db.query('INSERT INTO public.parcels(id,workspace_id,display_name) VALUES($1,$2,$3)',[parcel,farm.payload.workspaceId,'Current name']);
  await db.query('INSERT INTO public.farm_parcel_memberships(workspace_id,farm_id,parcel_id,valid_from) VALUES($1,$2,$3,$4)',
    [farm.payload.workspaceId,farm.payload.id,parcel,'2026-01-01']);
  return {id:randomUUID(),workspaceId:farm.payload.workspaceId,farmId:farm.payload.id,name:'Campaign history',
    startDate:'2026-01-01',endDate:'2026-12-31',status:'CLOSED',notes:'Preserve notes',parcels:[{
      id:randomUUID(),parcelId:parcel,farmIdAtStart:farm.payload.id,farmNameAtStart:'Historical farm',
      parcelNameAtStart:'Historical parcel',managedAreaM2AtStart:1250.25,cadastralReferenceAtStart:null,
      geometryGeoJsonSnapshot:'{"type":"Polygon","coordinates":[]}'}]};
}
async function campaignCall(db,actor,operation,payload) {
  return asActor(db,actor,async()=>(await db.query('SELECT public.create_own_campaign($1::uuid,$2::jsonb) AS receipt',
    [operation,JSON.stringify(payload)])).rows[0].receipt);
}

test('campaign creation atomically preserves historical parcel snapshots and a recoverable receipt',async()=>{
  const {db,actor}=await fixture();
  try {
    const payload=await campaignFixture(db,actor),operation=randomUUID();
    const first=await campaignCall(db,actor,operation,payload);
    assert.equal(first.remote_version,1);assert.equal(first.entity_id,payload.id);
    const restored=await asActor(db,actor,async()=>(await db.query('SELECT public.get_own_campaign($1::uuid) AS aggregate',[payload.id])).rows[0].aggregate);
    assert.equal(restored.campaign.status,'CLOSED');assert.equal(restored.parcels.length,1);
    const child=restored.parcels[0];
    assert.equal(child.id,payload.parcels[0].id);assert.equal(child.parcel_name_at_start,'Historical parcel');
    assert.equal(child.farm_name_at_start,'Historical farm');assert.equal(child.managed_area_m2_at_start,1250.25);
    assert.equal(child.geometry_geo_json_snapshot,payload.parcels[0].geometryGeoJsonSnapshot);
    assert.equal(child.cadastral_reference_at_start,null);
    assert.equal((await campaignCall(db,actor,operation,payload)).idempotent,true);
    await assert.rejects(campaignCall(db,actor,operation,{...payload,parcels:[]}),e=>e.code==='22023'||e.code==='23505');
    await assert.rejects(campaignCall(db,actor,randomUUID(),payload),e=>e.code==='23505');
  } finally {await db.close();}
});

test('campaign creation rejects incomplete or invalid historical snapshots without partial campaign or receipt',async()=>{
  const {db,actor}=await fixture();
  try {
    const p=await campaignFixture(db,actor);
    for(const payload of [{...p,startDate:'2026-02-30'},{...p,endDate:'2025-01-01'},
      {...p,status:'ARCHIVED'},{...p,parcels:[]},{...p,parcels:[p.parcels[0],p.parcels[0]]},
      {...p,parcels:[{...p.parcels[0],farmIdAtStart:randomUUID()}]},
      {...p,parcels:[{...p.parcels[0],managedAreaM2AtStart:-1}]},
      {...p,parcels:[{...p.parcels[0],unexpected:true}]}]) {
      await assert.rejects(campaignCall(db,actor,randomUUID(),payload),e=>['22023','42501'].includes(e.code));
    }
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.campaigns')).rows[0].n,0);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.campaign_parcels')).rows[0].n,0);
    assert.equal((await db.query("SELECT count(*)::int AS n FROM magina_g2_private.sync_operations WHERE entity_type='CAMPAIGN'")).rows[0].n,0);
  } finally {await db.close();}
});
