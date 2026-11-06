// Disposable local Supabase fixture and TLS bridge for the G2.6 FARM sender.
// Never point this at a remote project: the runner validates the local project ID and URL.
import assert from 'node:assert/strict';
import { extraProposals } from '../lib/extra-proposals.mjs';
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^mg26-farm-https-[0-9]+-[0-9]+$/);
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['127.0.0.1', 'localhost'].includes(api.hostname));
const container = `supabase_db_${project}`;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const sql = text => execFileSync('docker', ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'],
  { input: text, encoding: 'utf8', timeout: 20000, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
const request = async (path, body, token = status.ANON_KEY, admin = false) => {
  const response = await fetch(new URL(path, api), { method: 'POST', headers: {
    apikey: admin ? status.SERVICE_ROLE_KEY : status.ANON_KEY,
    Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
  }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  const data = await response.json();
  assert.equal(response.status, 200, `${path}: ${response.status}`);
  return data;
};
const jwt = raw => {
  const claims = JSON.parse(Buffer.from(raw.split('.')[1], 'base64url'));
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const text = `${part({ alg: 'HS256', typ: 'JWT' })}.${part({ ...claims,
    iss: 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1' })}`;
  return `${text}.${createHmac('sha256', status.JWT_SECRET).update(text).digest('base64url')}`;
};
const account = async label => {
  const email = `g26-farm-send-${label}-${randomUUID()}@example.test`;
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
  new URL('../../migrations/20261029044901_g26_workspace_authorization.sql', import.meta.url),
  new URL('../../proposals/g26-parent-receive-v1.sql', import.meta.url)];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\n${extraProposals()}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

// Nothing of A is seeded: A creates its WORKSPACE and FARM rows from Android.
const owner = await account('a');
const foreign = await account('c');
for (let attempt = 0; attempt < 40; attempt++) {
  const response = await fetch(new URL('/rest/v1/rpc/check_sync_operations', api), {
    method: 'POST', headers: { apikey: status.ANON_KEY, Authorization: `Bearer ${owner.token}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ p_operation_ids: [] }),
  });
  if (response.status === 200) break;
  if (attempt === 39) throw new Error('PostgREST did not reload the G2 RPCs');
  await new Promise(resolve => setTimeout(resolve, 250));
}
const fixture = { actor: owner.id, token: owner.token, foreignActor: foreign.id, foreignToken: foreign.token,
  anonKey: status.ANON_KEY };

// The FARM create response can be cut or delayed AFTER Supabase committed, to prove replay recovery.
let createMode = 'normal';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const state = id => {
  const [rows, receipts, owners, deleted, version, name] = sql(`SELECT
    (SELECT count(*) FROM public.workspaces WHERE id=${q(id)}::uuid),
    (SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='WORKSPACE' AND entity_id=${q(id)}::uuid),
    (SELECT coalesce(string_agg(owner_user_id::text, ','), '') FROM public.workspaces WHERE id=${q(id)}::uuid),
    (SELECT count(*) FROM public.workspaces WHERE id=${q(id)}::uuid AND deleted_at IS NOT NULL),
    (SELECT coalesce(max(version), 0) FROM public.workspaces WHERE id=${q(id)}::uuid),
    (SELECT coalesce(max(name), '') FROM public.workspaces WHERE id=${q(id)}::uuid);`).split('|');
  return { rows: Number(rows), receipts: Number(receipts), owner: owners, deleted: Number(deleted),
    version: Number(version), name };
};
const farmState = id => {
  const [rows, receipts, reservations, owners, version, name] = sql(`SELECT
    (SELECT count(*) FROM public.farms WHERE id=${q(id)}::uuid),
    (SELECT count(*) FROM magina_g2_private.sync_operations WHERE entity_type='FARM' AND entity_id=${q(id)}::uuid),
    (SELECT count(*) FROM magina_g1c_private.g1c_reservations WHERE farm_id=${q(id)}::uuid),
    (SELECT coalesce(string_agg(creator_user_id::text||'/'||owner_id::text, ','), '') FROM public.farms WHERE id=${q(id)}::uuid),
    (SELECT coalesce(max(version), 0) FROM public.farms WHERE id=${q(id)}::uuid),
    (SELECT coalesce(max(name), '') FROM public.farms WHERE id=${q(id)}::uuid);`).split('|');
  return { rows: Number(rows), receipts: Number(receipts), reservations: Number(reservations), owner: owners,
    version: Number(version), name };
};

const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY),
  cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  const url = new URL(incoming.url, 'https://fixture.invalid');
  const json = (code, value) => {
    outgoing.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    outgoing.end(JSON.stringify(value));
  };
  if (url.pathname === '/__g26_fixture') return json(200, fixture);
  // Fixed mutations and reads only; the instrumentation cannot send SQL.
  if (incoming.method === 'POST' && url.pathname === '/__g26_admin/create_mode') {
    const mode = url.searchParams.get('mode');
    if (!['normal', 'drop', 'delay'].includes(mode)) return json(400, {});
    createMode = mode; outgoing.writeHead(204); outgoing.end(); return;
  }
  const id = url.searchParams.get('id') ?? '';
  if (url.pathname === '/__g26_admin/workspace' && uuid.test(id)) return json(200, state(id));
  if (url.pathname === '/__g26_admin/farm' && uuid.test(id)) return json(200, farmState(id));
  if (incoming.method === 'POST' && url.pathname === '/__g26_admin/delete_workspace' && uuid.test(id)) {
    sql(`UPDATE public.workspaces SET deleted_at=now() WHERE id=${q(id)}::uuid AND owner_user_id=${q(owner.id)}::uuid;`);
    outgoing.writeHead(204); outgoing.end(); return;
  }
  if (!url.pathname.startsWith('/rest/v1/rpc/') && url.pathname !== '/auth/v1/user') {
    outgoing.writeHead(404); outgoing.end(); return;
  }
  try {
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const response = await fetch(new URL(incoming.url, api), {
      method: incoming.method,
      headers: { apikey: incoming.headers.apikey, authorization: incoming.headers.authorization,
        'content-type': incoming.headers['content-type'] ?? 'application/json' },
      body: chunks.length ? Buffer.concat(chunks) : undefined,
      signal: AbortSignal.timeout(15000),
    });
    const body = Buffer.from(await response.arrayBuffer());
    if (url.pathname === '/rest/v1/rpc/create_reserved_farm' && createMode === 'drop') {
      incoming.socket.destroy(); return;
    }
    if (url.pathname === '/rest/v1/rpc/create_reserved_farm' && createMode === 'delay') {
      await new Promise(resolve => setTimeout(resolve, 6000));
    }
    outgoing.writeHead(response.status, { 'Content-Type': 'application/json' });
    outgoing.end(body);
  } catch {
    try { outgoing.writeHead(502); outgoing.end('{}'); } catch { /* client already gone */ }
  }
});
server.on('clientError', (_, socket) => socket.destroy());
server.listen(54443, '127.0.0.1', () => process.stdout.write('G26_HTTPS_READY\n'));
