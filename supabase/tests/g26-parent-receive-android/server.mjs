// Disposable local Supabase fixture and TLS bridge for the G2.6 parent receiver.
// Never point this at a remote project: the runner validates the local project ID and URL.
import assert from 'node:assert/strict';
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^magina-g26-parents-https-[0-9]+-[0-9]+$/);
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
  const email = `g26-parents-${label}-${randomUUID()}@example.test`;
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
  new URL('../../migrations/20261029104720_g26_campaign_cas.sql', import.meta.url),
  new URL('../../proposals/g26-campaign-head-v1.sql', import.meta.url),
  new URL('../../proposals/g26-campaign-receive-v1.sql', import.meta.url),
  new URL('../../proposals/g26-parent-receive-v1.sql', import.meta.url)];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

const owner = await account('a');
const foreign = await account('c');
const member = await account('m');
const ids = { workspace: randomUUID(), farm1: randomUUID(), farm2: randomUUID(), parcel1: randomUUID(),
  parcel2: randomUUID(), m1: randomUUID(), m2: randomUUID(), campaign: randomUUID(), snapshot: randomUUID(),
  foreignWorkspace: randomUUID() };
// A's graph as it exists in Supabase after A's own sync. B never receives these as Room fixtures.
sql(`INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(ids.workspace)},${q(owner.id)},'Olivar QA A');
  INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,municipality,province) VALUES
    (${q(ids.farm1)},${q(ids.workspace)},${q(owner.id)},${q(owner.id)},'Finca Norte','Bedmar','Jaén'),
    (${q(ids.farm2)},${q(ids.workspace)},${q(owner.id)},${q(owner.id)},'Finca Sur','Jódar','Jaén');
  INSERT INTO public.parcels(id,workspace_id,display_name,managed_area_m2,olive_tree_count,variety) VALUES
    (${q(ids.parcel1)},${q(ids.workspace)},'Parcela 1',1234.5,120,'Picual'),
    (${q(ids.parcel2)},${q(ids.workspace)},'Parcela 2',NULL,NULL,NULL);
  INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_from_exact) VALUES
    (${q(ids.m1)},${q(ids.workspace)},${q(ids.farm1)},${q(ids.parcel1)},'2026-01-01T00:00:00.007Z','2026-01-01T00:00:00.007Z'),
    (${q(ids.m2)},${q(ids.workspace)},${q(ids.farm2)},${q(ids.parcel2)},'2026-01-01T00:00:00Z',NULL);
  INSERT INTO public.workspaces(id,owner_user_id,name) VALUES (${q(ids.foreignWorkspace)},${q(foreign.id)},'Olivar QA C');
  INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
    VALUES (${q(ids.workspace)},${q(member.id)},'VIEWER',ARRAY['read']);`);
for (let attempt = 0; attempt < 40; attempt++) {
  const response = await fetch(new URL('/rest/v1/rpc/get_own_workspace_parents_v1', api), {
    method: 'POST', headers: { apikey: status.ANON_KEY, Authorization: `Bearer ${owner.token}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ p_workspace_id: ids.workspace }),
  });
  if (response.status === 200) break;
  if (attempt === 39) throw new Error('PostgREST did not reload the parents RPC');
  await new Promise(resolve => setTimeout(resolve, 250));
}
// The campaign is created through the real A-side RPC, so B's campaign receive depends on received parents.
await request('/rest/v1/rpc/create_own_campaign', { p_operation_id: randomUUID(), p_campaign: {
  id: ids.campaign, workspaceId: ids.workspace, farmId: ids.farm1, name: 'Campaña 2026/27',
  startDate: '2026-10-01', endDate: null, status: 'PREPARATION', notes: null,
  parcels: [{ id: ids.snapshot, parcelId: ids.parcel1, farmIdAtStart: ids.farm1,
    farmNameAtStart: 'Finca Norte', parcelNameAtStart: 'Parcela 1', managedAreaM2AtStart: 1234.5,
    cadastralReferenceAtStart: null, geometryGeoJsonSnapshot: null }],
} }, owner.token);
const fixture = { ...ids, actor: owner.id, token: owner.token, foreignActor: foreign.id, foreignToken: foreign.token,
  memberActor: member.id, memberToken: member.token, anonKey: status.ANON_KEY };

// Fixed mutations only; the instrumentation cannot send SQL.
const admin = {
  '/__g26_admin/edit_farms': () => sql(`UPDATE public.farms SET name=name||' (A)',version=version+1,updated_at=now()
    WHERE id IN (${q(ids.farm1)},${q(ids.farm2)});`),
  '/__g26_admin/revoke_member': () => sql(`UPDATE public.workspace_memberships SET status='REVOKED',revoked_at=now(),
    version=version+1 WHERE workspace_id=${q(ids.workspace)} AND user_id=${q(member.id)};`),
};

const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY),
  cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  if (incoming.url === '/__g26_fixture') {
    outgoing.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    outgoing.end(JSON.stringify(fixture));
    return;
  }
  if (incoming.method === 'POST' && admin[incoming.url]) {
    admin[incoming.url]();
    outgoing.writeHead(204); outgoing.end(); return;
  }
  if (!incoming.url?.startsWith('/rest/v1/rpc/') && incoming.url !== '/auth/v1/user') {
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
    outgoing.writeHead(response.status, { 'Content-Type': 'application/json' });
    outgoing.end(Buffer.from(await response.arrayBuffer()));
  } catch {
    outgoing.writeHead(502); outgoing.end('{}');
  }
});
server.listen(54443, '127.0.0.1', () => process.stdout.write('G26_HTTPS_READY\n'));
