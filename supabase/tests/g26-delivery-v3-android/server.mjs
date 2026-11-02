// Disposable local Supabase fixture and TLS bridge for the #808 Pesada v3 writer (Android transport).
// Never point this at a remote project: the runner validates the local project ID and URL.
// The instrumentation cannot send SQL: it only calls the fixed admin routes below.
import assert from 'node:assert/strict';
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^mg26-dv3-[0-9]+-[0-9]+$/);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['127.0.0.1', 'localhost'].includes(api.hostname));
const container = `supabase_db_${project}`;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'],
  { input: text, encoding: 'utf8', timeout: 60000, maxBuffer: 64 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
const ISSUER = 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1';
const request = async (path, body, token = status.ANON_KEY, admin = false) => {
  const response = await fetch(new URL(path, api), { method: 'POST', headers: {
    apikey: admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY,
    Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
  }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  const text = await response.text();
  assert.equal(response.status, 200, `${path}: ${response.status} ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
};
const jwt = raw => {
  const claims = JSON.parse(Buffer.from(raw.split('.')[1], 'base64url'));
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const text = `${part({ alg: 'HS256', typ: 'JWT' })}.${part({ ...claims, iss: ISSUER })}`;
  return `${text}.${createHmac('sha256', status.JWT_SECRET).update(text).digest('base64url')}`;
};
const account = async label => {
  const email = `g26-dv3-${label}-${randomUUID()}@example.test`;
  const password = `Fixture!${randomUUID()}`;
  const user = await request('/auth/v1/admin/users', { email, password, email_confirm: true }, status.SERVICE_ROLE_KEY, true);
  const login = await request('/auth/v1/token?grant_type=password', { email, password });
  return { id: user.id, token: jwt(login.access_token) };
};

const names = ['20261011120000_g0_auth_workspaces_shared_farms.sql',
  '20261011120001_g0_agricultural_sync_schema.sql',
  '20261011120002_g0_contracts_and_storage_rls.sql',
  '20261016041222_g0_legacy_storage_parcel_hardening.sql',
  '20261016041601_g0_aggregate_identity_guards.sql'];
const files = [...names.map(name => new URL(`../../migrations/${name}`, import.meta.url)),
  ...['g1c-reservations.sql', 'g2-sync-own-data.sql', 'g2-parcel-bootstrap.sql']
    .map(name => new URL(`../../proposals/${name}`, import.meta.url)),
  new URL('../../migrations/20261027155737_g25_receive_bundle_contract.sql', import.meta.url),
  new URL('../../migrations/20261029044901_g26_workspace_authorization.sql', import.meta.url),
  ...['g26-parent-receive-v1.sql', 'g26-fpm-manage-hardening.sql', 'g26-parcel-mutation-v1.sql', 'g26-historical-writers-v1.sql',
    'g26-delivery-head-v3.sql', 'g26-harvest-writer-v1.sql']
    .map(name => new URL(`../../proposals/${name}`, import.meta.url))];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

const owner = await account('owner');
const member = await account('member');
for (let attempt = 0; attempt < 40; attempt++) {
  const response = await fetch(new URL('/rest/v1/rpc/sync_own_delivery_v3', api), {
    method: 'POST', headers: { apikey: status.ANON_KEY, Authorization: `Bearer ${owner.token}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ p_operation_id: randomUUID(), p_delivery: {} }),
  });
  if (response.status !== 404) break;
  if (attempt === 39) throw new Error('PostgREST did not expose sync_own_delivery_v3');
  await new Promise(resolve => setTimeout(resolve, 250));
}
const fixture = { actor: owner.id, token: owner.token, member: member.id, memberToken: member.token, anonKey: status.ANON_KEY };
process.stdout.write(`G808-HOST ${sql(`SELECT version();`)}\n`);

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const date = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
const instant = /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?Z$/;
let v3Mode = 'normal';

/** Server parents with the ids the device already holds. Real RPCs where they exist (#798, #800). */
async function seed(body) {
  const { workspace, farm, campaign, parcels } = body;
  for (const id of [workspace, farm, campaign, ...parcels.flatMap(p => [p.id, p.snapshot])]) assert.match(id, uuid);
  await request('/rest/v1/rpc/create_own_workspace', { p_operation_id: randomUUID(), p_workspace: { id: workspace,
    name: 'Olivar QA', countryCode: 'ES', timezone: 'Europe/Madrid', locale: 'es-ES', currency: 'EUR' } }, owner.token);
  sql(`INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,status)
       VALUES (${q(farm)},${q(workspace)},${q(owner.id)},${q(owner.id)},'La Solana','ACTIVE');`);
  for (const p of parcels) {
    await request('/rest/v1/rpc/create_own_parcel', { p_operation_id: randomUUID(), p_parcel: { id: p.id, workspaceId: workspace,
      status: 'ACTIVE', displayName: p.name, cadastralReference: null, cadastralPolygon: null, cadastralParcel: null,
      municipality: 'Bedmar', province: 'Jaén', source: 'MANUAL', geometryGeoJson: null, sourceProvider: null,
      sourceImportedAt: null, cadastralAreaM2: null, managedAreaM2: 1234.5, notes: null, oliveTreeCount: 120,
      variety: 'Picual', irrigationSystem: null, irrigationNetwork: null, irrigationSector: null, irrigationDays: null,
      memberships: [{ id: randomUUID(), farmId: farm, validFrom: '2025-09-01T00:00:00Z', validUntil: null, deletedAt: null }] } },
      owner.token);
  }
  // A CLOSED campaign (so the parcel can be archived) whose snapshot ids are the device's.
  sql(`INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,end_date,status,snapshot_format)
       VALUES (${q(campaign)},${q(workspace)},${q(farm)},'2026/27','2025-10-01',NULL,'CLOSED',1);
       ${parcels.map(p => `INSERT INTO public.campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,
         farm_name_at_start,parcel_name_at_start) VALUES (${q(p.snapshot)},${q(workspace)},${q(campaign)},${q(p.id)},
         ${q(farm)},'La Solana',${q(p.name)});`).join('\n')}
       INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
         VALUES (${q(workspace)},${q(member.id)},'EDITOR',ARRAY['read','write']);`);
}

/** Read-only server state of a Jornada. Jornadas are created only by the device, through sync_own_harvest_v1. */
function harvestState(id) {
  assert.match(id, uuid);
  const [rows, weight, version, deleted, parcels] = sql(`SELECT
    (SELECT count(*) FROM public.harvests WHERE id=${q(id)}),
    (SELECT coalesce(max(weight_grams),0) FROM public.harvests WHERE id=${q(id)}),
    (SELECT coalesce(max(version),0) FROM public.harvests WHERE id=${q(id)}),
    (SELECT count(*) FROM public.harvests WHERE id=${q(id)} AND deleted_at IS NOT NULL),
    (SELECT count(*) FROM public.harvest_parcels WHERE harvest_id=${q(id)});`).split('|');
  return { rows: Number(rows), weight: Number(weight), version: Number(version), deleted: Number(deleted), parcels: Number(parcels) };
}

async function archive(parcel, at) {
  assert.match(parcel, uuid); assert.match(at, instant);
  const [membership, version] = sql(`SELECT m.id, p.version FROM public.parcels p JOIN public.farm_parcel_memberships m
    ON m.parcel_id=p.id AND m.valid_until IS NULL AND m.deleted_at IS NULL WHERE p.id=${q(parcel)};`).split('|');
  await request('/rest/v1/rpc/archive_own_parcel_v1', { p_operation_id: randomUUID(), p_parcel_id: parcel,
    p_expected_version: Number(version), p_archive: { archivedAt: at, closeMembershipId: membership } }, owner.token);
}

function delivery(id) {
  assert.match(id, uuid);
  const [rows, version, receipts, payloadVersion, hash, origin, parcels, harvestId] = sql(`SELECT
    (SELECT count(*) FROM public.deliveries WHERE id=${q(id)}),
    (SELECT coalesce(max(version),0) FROM public.deliveries WHERE id=${q(id)}),
    (SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='DELIVERY' AND entity_id=${q(id)}),
    (SELECT coalesce(max(payload_version),0) FROM magina_g2_private.sync_operations WHERE entity_type='DELIVERY' AND entity_id=${q(id)}),
    (SELECT coalesce(max(payload_hash),'') FROM magina_g2_private.sync_operations WHERE entity_type='DELIVERY' AND entity_id=${q(id)}),
    (SELECT coalesce(max(origin_created_at_exact),'') FROM magina_g2_private.fact_origins WHERE entity_id=${q(id)}),
    (SELECT count(*) FROM public.delivery_parcels WHERE delivery_id=${q(id)}),
    (SELECT coalesce(max(harvest_id::text),'') FROM public.deliveries WHERE id=${q(id)});`).split('|');
  return { rows: Number(rows), version: Number(version), receipts: Number(receipts), payloadVersion: Number(payloadVersion),
    hash, origin, parcels: Number(parcels), harvestId };
}

const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY),
  cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  const url = new URL(incoming.url, 'https://fixture.invalid');
  const json = (code, value) => {
    outgoing.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    outgoing.end(JSON.stringify(value));
  };
  const done = () => { outgoing.writeHead(204); outgoing.end(); };
  const chunks = [];
  for await (const chunk of incoming) chunks.push(chunk);
  const raw = Buffer.concat(chunks);
  try {
    if (url.pathname === '/__g26_fixture') return json(200, fixture);
    if (incoming.method === 'POST' && url.pathname === '/__g26_admin/seed') { await seed(JSON.parse(raw.toString('utf8'))); return done(); }
    if (url.pathname === '/__g26_admin/harvest_state') return json(200, harvestState(url.searchParams.get('id') ?? ''));
    if (incoming.method === 'POST' && url.pathname === '/__g26_admin/archive') {
      await archive(url.searchParams.get('parcel') ?? '', url.searchParams.get('at') ?? ''); return done();
    }
    if (incoming.method === 'POST' && url.pathname === '/__g26_admin/v3_mode') {
      const mode = url.searchParams.get('mode');
      if (!['normal', 'drop'].includes(mode)) return json(400, {});
      v3Mode = mode; return done();
    }
    if (url.pathname === '/__g26_admin/delivery') return json(200, delivery(url.searchParams.get('id') ?? ''));
  } catch (error) {
    process.stdout.write(`fixture admin error: ${String(error?.message ?? error).slice(0, 300)}\n`);
    return json(500, {});
  }
  if (!url.pathname.startsWith('/rest/v1/rpc/') && url.pathname !== '/auth/v1/user') {
    outgoing.writeHead(404); outgoing.end(); return;
  }
  try {
    const response = await fetch(new URL(incoming.url, api), {
      method: incoming.method,
      headers: { apikey: incoming.headers.apikey, authorization: incoming.headers.authorization,
        'content-type': incoming.headers['content-type'] ?? 'application/json' },
      body: raw.length ? raw : undefined,
      signal: AbortSignal.timeout(15000),
    });
    const body = Buffer.from(await response.arrayBuffer());
    // A lost response AFTER the server committed: the device must recover it by the same UUID.
    if (url.pathname === '/rest/v1/rpc/sync_own_delivery_v3' && v3Mode === 'drop') {
      incoming.socket.destroy(); return;
    }
    outgoing.writeHead(response.status, { 'Content-Type': 'application/json' });
    outgoing.end(body);
  } catch {
    try { outgoing.writeHead(502); outgoing.end('{}'); } catch { /* client already gone */ }
  }
});
server.on('clientError', (_, socket) => socket.destroy());
server.listen(54443, '127.0.0.1', () => process.stdout.write('G26_HTTPS_READY\n'));
