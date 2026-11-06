// APK-only acceptance (A2 <-> B2) against a DISPOSABLE local Supabase. Never a remote project.
//
// The DEV debug APK is unchanged: it believes it talks to the DEV project host. On the two disposable emulators only:
//  - this DNS server (the emulators start with `-dns-server 127.0.0.1`) answers that ONE host with the host machine
//    (10.0.2.2) and refuses every other name, so a redirection failure can never reach the real DEV or the internet;
//  - this HTTPS bridge serves that host with a certificate from a throw-away CA installed as a user CA on those
//    emulators (the dev APK trusts user CAs only in debuggable builds);
//  - Auth and RPC requests are forwarded to the local stack; access tokens are re-signed with the local secret under
//    the issuer the app verifies, exactly as the A/B harness does. Never a real token.
import assert from 'node:assert/strict';
import { createServer } from 'node:https';
import { createSocket } from 'node:dgram';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';
import { join } from 'node:path';

const work = process.env.G26_WORK;
const project = process.env.G26_PROJECT;
assert.match(project ?? '', /^mg26-apk-[0-9]+-[0-9]+$/);
const HOST = 'zubpicdibqbmewxeurhm.supabase.co';
const ISSUER = `https://${HOST}/auth/v1`;
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const api = new URL(status.API_URL);
assert.equal(api.protocol, 'http:');
assert.ok(['127.0.0.1', 'localhost'].includes(api.hostname));
const container = `supabase_db_${project}`;
const sql = text => execFileSync('docker', ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'],
  { input: text, encoding: 'utf8', timeout: 60000, maxBuffer: 64 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
const jwt = raw => {
  const claims = JSON.parse(Buffer.from(raw.split('.')[1], 'base64url'));
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const text = `${part({ alg: 'HS256', typ: 'JWT' })}.${part({ ...claims, iss: ISSUER })}`;
  return `${text}.${createHmac('sha256', status.JWT_SECRET).update(text).digest('base64url')}`;
};

// Same SQL, same order as the A/B harness, plus the read-only campaign list of the manual sync.
const names = ['20261011120000_g0_auth_workspaces_shared_farms.sql', '20261011120001_g0_agricultural_sync_schema.sql',
  '20261011120002_g0_contracts_and_storage_rls.sql', '20261016041222_g0_legacy_storage_parcel_hardening.sql',
  '20261016041601_g0_aggregate_identity_guards.sql'];
const files = [...names.map(name => new URL(`../../migrations/${name}`, import.meta.url)),
  ...['g1c-reservations.sql', 'g2-sync-own-data.sql', 'g2-parcel-bootstrap.sql'].map(name => new URL(`../../proposals/${name}`, import.meta.url)),
  new URL('../../migrations/20261027155737_g25_receive_bundle_contract.sql', import.meta.url),
  new URL('../../migrations/20261029044901_g26_workspace_authorization.sql', import.meta.url),
  new URL('../../migrations/20261029104720_g26_campaign_cas.sql', import.meta.url),
  ...['g26-campaign-head-v1.sql', 'g26-campaign-receive-v1.sql', 'g26-parent-receive-v1.sql', 'g26-fpm-manage-hardening.sql',
    'g26-parcel-mutation-v1.sql', 'g26-historical-writers-v1.sql', 'g26-delivery-head-v3.sql', 'g26-harvest-writer-v1.sql',
    'g26-delivery-delete-v1.sql', 'g26-harvest-head-v2.sql', 'g26-harvest-receive-v1.sql', 'g26-campaign-list-v1.sql']
    .map(name => new URL(`../../proposals/${name}`, import.meta.url))];
sql(`BEGIN;\n${files.map(file => readFileSync(file, 'utf8')).join('\n')}\nCOMMIT; NOTIFY pgrst, 'reload schema';`);

// One normal owner account; both devices sign in to it through the app's own login screen.
const email = `olivar-${randomUUID().slice(0, 8)}@example.test`;
const password = `Olivo-${randomUUID().slice(0, 12)}`;
const created = await fetch(new URL('/auth/v1/admin/users', api), { method: 'POST', headers: { apikey: status.SERVICE_ROLE_KEY,
  Authorization: `Bearer ${status.SERVICE_ROLE_KEY}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password, email_confirm: true }) });
assert.equal(created.status, 200, 'owner account');
const owner = (await created.json()).id;
writeFileSync(join(work, 'account.json'), JSON.stringify({ email, password, owner }));

/** Read-only server state of the owner's workspaces (host check only). */
const state = () => JSON.parse(sql(`SELECT jsonb_build_object(
  'workspaces', (SELECT count(*) FROM public.workspaces WHERE owner_user_id='${owner}'),
  'farms', (SELECT count(*) FROM public.farms f JOIN public.workspaces w ON w.id=f.workspace_id WHERE w.owner_user_id='${owner}'),
  'parcels', (SELECT count(*) FROM public.parcels p JOIN public.workspaces w ON w.id=p.workspace_id WHERE w.owner_user_id='${owner}'),
  'campaigns', (SELECT count(*) FROM public.campaigns c JOIN public.workspaces w ON w.id=c.workspace_id WHERE w.owner_user_id='${owner}'),
  'harvests', (SELECT count(*) FROM public.harvests h JOIN public.workspaces w ON w.id=h.workspace_id WHERE w.owner_user_id='${owner}'),
  'deliveries', (SELECT count(*) FROM public.deliveries d JOIN public.workspaces w ON w.id=d.workspace_id WHERE w.owner_user_id='${owner}'),
  'farmNames', (SELECT coalesce(jsonb_agg(f.name ORDER BY f.name),'[]') FROM public.farms f JOIN public.workspaces w ON w.id=f.workspace_id WHERE w.owner_user_id='${owner}'),
  'campaignNames', (SELECT coalesce(jsonb_agg(c.name || ':' || c.status::text ORDER BY c.name),'[]') FROM public.campaigns c JOIN public.workspaces w ON w.id=c.workspace_id WHERE w.owner_user_id='${owner}'),
  'deliveryNet', (SELECT coalesce(jsonb_agg(d.net_grams ORDER BY d.net_grams),'[]') FROM public.deliveries d JOIN public.workspaces w ON w.id=d.workspace_id WHERE w.owner_user_id='${owner}' AND d.deleted_at IS NULL));`));

const allowed = path => path.startsWith('/rest/v1/rpc/') || ['/auth/v1/token', '/auth/v1/user', '/auth/v1/logout'].includes(path);
const server = createServer({ key: readFileSync(process.env.G26_TLS_KEY), cert: readFileSync(process.env.G26_TLS_CERT) }, async (incoming, outgoing) => {
  const url = new URL(incoming.url, `https://${HOST}`);
  const chunks = [];
  for await (const chunk of incoming) chunks.push(chunk);
  const raw = Buffer.concat(chunks);
  if (url.pathname === '/__apk/state' && incoming.socket.remoteAddress === '127.0.0.1' && !incoming.headers['user-agent']?.includes('Dalvik')) {
    outgoing.writeHead(200, { 'Content-Type': 'application/json' }); outgoing.end(JSON.stringify(state())); return;
  }
  if (!allowed(url.pathname)) { outgoing.writeHead(404); outgoing.end(); return; }
  process.stdout.write(`apk ${incoming.method} ${url.pathname}\n`);
  try {
    const response = await fetch(new URL(incoming.url, api), {
      method: incoming.method,
      headers: { apikey: status.ANON_KEY, ...(incoming.headers.authorization ? { authorization: incoming.headers.authorization } : {}),
        'content-type': incoming.headers['content-type'] ?? 'application/json', accept: 'application/json' },
      body: raw.length ? raw : undefined, signal: AbortSignal.timeout(15000),
    });
    let body = Buffer.from(await response.arrayBuffer());
    if (url.pathname === '/auth/v1/token' && response.status === 200) {
      const session = JSON.parse(body.toString('utf8'));
      session.access_token = jwt(session.access_token);
      body = Buffer.from(JSON.stringify(session));
    }
    outgoing.writeHead(response.status, { 'Content-Type': response.headers.get('content-type') ?? 'application/json' });
    outgoing.end(body);
  } catch {
    try { outgoing.writeHead(502); outgoing.end('{}'); } catch { /* client gone */ }
  }
});
server.on('clientError', (_, socket) => socket.destroy());

// Fail-closed DNS for the disposable emulators: one A record, everything else NXDOMAIN.
const dns = createSocket('udp4');
dns.on('message', (query, peer) => {
  try {
    let offset = 12; const labels = [];
    while (query[offset] !== 0) { labels.push(query.subarray(offset + 1, offset + 1 + query[offset]).toString('ascii')); offset += query[offset] + 1; }
    const name = labels.join('.').toLowerCase(); const type = query.readUInt16BE(offset + 1);
    const question = query.subarray(12, offset + 5);
    const known = name === HOST;
    const answer = known && type === 1 ? Buffer.from([0xc0, 0x0c, 0, 1, 0, 1, 0, 0, 0, 30, 0, 4, 10, 0, 2, 2]) : Buffer.alloc(0);
    const header = Buffer.alloc(12);
    query.copy(header, 0, 0, 2);
    header.writeUInt16BE(known ? 0x8180 : 0x8183, 2);
    header.writeUInt16BE(1, 4); header.writeUInt16BE(answer.length ? 1 : 0, 6);
    dns.send(Buffer.concat([header, question, answer]), peer.port, peer.address);
  } catch { /* malformed query: no answer */ }
});
dns.bind(53, '127.0.0.1');
server.listen(443, '127.0.0.1', () => process.stdout.write('G26_APK_READY\n'));
