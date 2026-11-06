// Disposable local Supabase fixture and TLS bridge for the first real A2 -> B2 sync between two disposable emulators.
// Never point this at a remote project: the runner creates the local project and this file validates it.
// The devices only reach Auth and RPCs through this bridge; the host reads the server state through one fixed route.
import assert from 'node:assert/strict';
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^mg26-ab-[0-9]+-[0-9]+$/);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['127.0.0.1', 'localhost'].includes(api.hostname));
const container = `supabase_db_${project}`;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'],
  { input: text, encoding: 'utf8', timeout: 60000, maxBuffer: 64 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
// The issuer the device verifies against (the DEV project's), signed with the local JWT secret: never a real token.
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

// The same SQL, in the same order, as the CI runners (sql-regression / https-regression) plus the campaign contracts.
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
  new URL('../../migrations/20261029104720_g26_campaign_cas.sql', import.meta.url),
  ...['g26-campaign-head-v1.sql', 'g26-campaign-receive-v1.sql',
    'g26-parent-receive-v1.sql', 'g26-fpm-manage-hardening.sql', 'g26-parcel-mutation-v1.sql', 'g26-historical-writers-v1.sql',
    'g26-delivery-head-v3.sql', 'g26-harvest-writer-v1.sql', 'g26-delivery-delete-v1.sql', 'g26-harvest-head-v2.sql',
    'g26-harvest-receive-v1.sql', 'g26-campaign-list-v1.sql']
    .map(name => new URL(`../../proposals/${name}`, import.meta.url))];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

// One owner account; device A2 and device B2 each start their OWN session of it (two independent logins).
const email = `g26-ab-owner-${randomUUID()}@example.test`;
const password = `Fixture!${randomUUID()}`;
const user = await request('/auth/v1/admin/users', { email, password, email_confirm: true }, status.SERVICE_ROLE_KEY, true);
const loginA = await request('/auth/v1/token?grant_type=password', { email, password });
const loginB = await request('/auth/v1/token?grant_type=password', { email, password });
for (let attempt = 0; attempt < 40; attempt++) {
  const response = await fetch(new URL('/rest/v1/rpc/get_own_harvest_manifest_v1', api), {
    method: 'POST', headers: { apikey: status.ANON_KEY, Authorization: `Bearer ${jwt(loginA.access_token)}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ p_workspace_id: randomUUID(), p_farm_id: randomUUID(),
      p_campaign_id: randomUUID(), p_from: null, p_to: null }),
  });
  if (response.status !== 404) break;
  if (attempt === 39) throw new Error('PostgREST did not expose the #808 receive RPCs');
  await new Promise(resolve => setTimeout(resolve, 250));
}
const fixture = { actor: user.id, tokenA: jwt(loginA.access_token), tokenB: jwt(loginB.access_token), anonKey: status.ANON_KEY };
process.stdout.write(`G808-AB-HOST ${sql(`SELECT version();`)}\n`);

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** Read-only server state of one workspace: row counts and the facts the devices must agree on. */
function state(workspace) {
  assert.match(workspace, uuid);
  const w = q(workspace);
  const row = JSON.parse(sql(`SELECT jsonb_build_object(
    'workspaces', (SELECT count(*) FROM public.workspaces WHERE id=${w}),
    'farms', (SELECT count(*) FROM public.farms WHERE workspace_id=${w}),
    'parcels', (SELECT count(*) FROM public.parcels WHERE workspace_id=${w}),
    'campaigns', (SELECT count(*) FROM public.campaigns WHERE workspace_id=${w}),
    'campaignParcels', (SELECT count(*) FROM public.campaign_parcels WHERE workspace_id=${w}),
    'harvests', (SELECT count(*) FROM public.harvests WHERE workspace_id=${w}),
    'deliveries', (SELECT count(*) FROM public.deliveries WHERE workspace_id=${w}),
    'deliveryParcels', (SELECT count(*) FROM public.delivery_parcels WHERE workspace_id=${w}),
    'campaignStatus', (SELECT max(status::text) FROM public.campaigns WHERE workspace_id=${w}),
    'harvestWeight', (SELECT coalesce(max(weight_grams),0) FROM public.harvests WHERE workspace_id=${w}),
    'deliveryNet', (SELECT coalesce(max(net_grams),0) FROM public.deliveries WHERE workspace_id=${w}),
    'farmName', (SELECT max(name) FROM public.farms WHERE workspace_id=${w}),
    'parcelName', (SELECT max(display_name) FROM public.parcels WHERE workspace_id=${w}));`));
  return row;
}

const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY),
  cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  const url = new URL(incoming.url, 'https://fixture.invalid');
  const json = (code, value) => {
    outgoing.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    outgoing.end(JSON.stringify(value));
  };
  const chunks = [];
  for await (const chunk of incoming) chunks.push(chunk);
  const raw = Buffer.concat(chunks);
  try {
    if (url.pathname === '/__g26_fixture') return json(200, fixture);
    if (url.pathname === '/__ab/state') return json(200, state(url.searchParams.get('workspace') ?? ''));
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
    outgoing.writeHead(response.status, { 'Content-Type': 'application/json' });
    outgoing.end(body);
  } catch {
    try { outgoing.writeHead(502); outgoing.end('{}'); } catch { /* client already gone */ }
  }
});
server.on('clientError', (_, socket) => socket.destroy());
server.listen(54443, '127.0.0.1', () => process.stdout.write('G26_HTTPS_READY\n'));
