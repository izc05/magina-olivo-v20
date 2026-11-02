// Disposable local Supabase fixture and TLS bridge for the G2.6 2c-3 convergence probe.
// Never point this at a remote project: the runner validates the local project ID and URL.
// Schema: #797-#805 proposals plus get_own_workspace_parents_v2 (#812), loaded explicitly once.
import assert from 'node:assert/strict';
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^mg26-conv-https-[0-9]+-[0-9]+$/);
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
  const email = `g26-convergence-${label}-${randomUUID()}@example.test`;
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
  ...['g26-parent-receive-v1.sql', 'g26-fpm-manage-hardening.sql', 'g26-parcel-mutation-v1.sql', 'g26-parent-receive-v2.sql']
    .map(name => new URL(`../../proposals/${name}`, import.meta.url))];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

const owner = await account('a');
const foreign = await account('c');
for (let attempt = 0; attempt < 40; attempt++) {
  const response = await fetch(new URL('/rest/v1/rpc/get_own_workspace_parents_v2', api), {
    method: 'POST', headers: { apikey: status.ANON_KEY, Authorization: `Bearer ${owner.token}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ p_workspace_id: randomUUID() }),
  });
  if (response.status !== 404) break;
  if (attempt === 39) throw new Error('PostgREST did not expose get_own_workspace_parents_v2');
  await new Promise(resolve => setTimeout(resolve, 250));
}
const fixture = { actor: owner.id, token: owner.token, foreignActor: foreign.id, foreignToken: foreign.token,
  anonKey: status.ANON_KEY };

// The ARCHIVE response can be cut AFTER Supabase committed (C7: lost response, receipt recovery).
let mutationMode = 'normal';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/** Server truth for one parcel, with the exact instant texts the contract stores. */
const parcelState = id => {
  const row = sql(`SELECT coalesce((SELECT json_build_object(
      'status', p.status, 'version', p.version, 'name', p.display_name,
      'archivedAt', coalesce(p.deleted_at_exact, to_jsonb(p.deleted_at)#>>'{}'),
      'receipts', (SELECT count(*) FROM magina_g2_private.sync_operations o WHERE o.entity_id=p.id),
      'memberships', coalesce((SELECT json_agg(json_build_object('id', m.id, 'farmId', m.farm_id,
          'validFrom', coalesce(m.valid_from_exact, to_jsonb(m.valid_from)#>>'{}'),
          'validUntil', coalesce(m.valid_until_exact, to_jsonb(m.valid_until)#>>'{}'),
          'version', m.version) ORDER BY m.id) FROM public.farm_parcel_memberships m
        WHERE m.parcel_id=p.id AND m.deleted_at IS NULL), '[]'::json))
    FROM public.parcels p WHERE p.id=${q(id)}::uuid)::text, '{}');`);
  return JSON.parse(row);
};

const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY),
  cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  const url = new URL(incoming.url, 'https://fixture.invalid');
  const json = (code, value) => {
    outgoing.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    outgoing.end(JSON.stringify(value));
  };
  if (url.pathname === '/__g26_fixture') return json(200, fixture);
  // Fixed toggles and reads only; the instrumentation cannot send SQL.
  if (incoming.method === 'POST' && url.pathname === '/__g26_admin/mutation_mode') {
    const mode = url.searchParams.get('mode');
    if (!['normal', 'drop'].includes(mode)) return json(400, {});
    mutationMode = mode; outgoing.writeHead(204); outgoing.end(); return;
  }
  const id = url.searchParams.get('id') ?? '';
  if (url.pathname === '/__g26_admin/parcel' && uuid.test(id)) return json(200, parcelState(id));
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
    if (url.pathname === '/rest/v1/rpc/archive_own_parcel_v1' && mutationMode === 'drop') {
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
