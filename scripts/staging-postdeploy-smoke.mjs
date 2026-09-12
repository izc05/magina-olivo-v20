const apiUrlRaw = process.env.STAGING_API_URL?.trim();
const webOriginRaw = process.env.STAGING_WEB_ORIGIN?.trim();
const rejectedOrigin = process.env.STAGING_REJECTED_ORIGIN?.trim() || 'https://untrusted.magina.invalid';
const allowHttp = process.env.STAGING_ALLOW_HTTP === 'true';

function fail(message) {
  throw new Error(`Staging post-deploy smoke failed: ${message}`);
}

function normalizeUrl(value, name) {
  if (!value) fail(`${name} is required`);
  let url;
  try {
    url = new URL(value);
  } catch {
    fail(`${name} is not a valid URL`);
  }
  if (!allowHttp && url.protocol !== 'https:') fail(`${name} must use https`);
  if (!['https:', 'http:'].includes(url.protocol)) fail(`${name} must use http/https`);
  return url;
}

const apiUrl = normalizeUrl(apiUrlRaw, 'STAGING_API_URL');
const webOrigin = normalizeUrl(webOriginRaw, 'STAGING_WEB_ORIGIN').origin;
const rejected = normalizeUrl(rejectedOrigin, 'STAGING_REJECTED_ORIGIN').origin;
if (rejected === webOrigin) fail('STAGING_REJECTED_ORIGIN must differ from STAGING_WEB_ORIGIN');

async function request(path, origin, extraHeaders = {}) {
  const url = new URL(path, apiUrl);
  return fetch(url, {
    method: 'GET',
    redirect: 'manual',
    headers: { origin, ...extraHeaders },
    signal: AbortSignal.timeout(15_000),
  });
}

function requireHeader(response, name, expected) {
  const actual = response.headers.get(name);
  if (actual !== expected) fail(`${name} expected ${expected}, got ${String(actual)}`);
}

const health = await request('/health', webOrigin);
if (health.status !== 200) fail(`/health expected 200, got ${health.status}`);
requireHeader(health, 'access-control-allow-origin', webOrigin);
requireHeader(health, 'x-content-type-options', 'nosniff');
requireHeader(health, 'x-frame-options', 'DENY');
requireHeader(health, 'referrer-policy', 'strict-origin-when-cross-origin');
requireHeader(health, 'permissions-policy', 'camera=(), microphone=(), geolocation=(self)');

const healthBody = await health.json().catch(() => fail('/health did not return JSON'));
if (healthBody?.ok !== true) fail('/health did not report ok=true');
if (healthBody?.databaseConfigured !== true) fail('/health reports databaseConfigured=false');
if (healthBody?.googleAuthConfigured !== true) fail('/health reports googleAuthConfigured=false');
if (healthBody?.webPushConfigured !== true) fail('/health reports webPushConfigured=false');

const ready = await request('/ready', webOrigin);
if (ready.status !== 200) fail(`/ready expected 200, got ${ready.status}`);
requireHeader(ready, 'access-control-allow-origin', webOrigin);
requireHeader(ready, 'x-content-type-options', 'nosniff');
requireHeader(ready, 'x-frame-options', 'DENY');
const readyBody = await ready.json().catch(() => fail('/ready did not return JSON'));
if (readyBody?.ok !== true || readyBody?.database !== 'ready') {
  fail(`/ready did not report a live database connection: ${JSON.stringify(readyBody)}`);
}

const rejectedResponse = await request('/health', rejected);
if (rejectedResponse.status < 400) fail(`untrusted origin was not rejected: ${rejectedResponse.status}`);
if (rejectedResponse.headers.get('access-control-allow-origin') === rejected) {
  fail('untrusted origin received Access-Control-Allow-Origin');
}

const privateResponse = await request('/api/v1/me', webOrigin, {
  'x-user-id': '33333333-3333-4333-8333-333333333333',
  'x-workspace-id': '11111111-1111-4111-8111-111111111111',
});
if (privateResponse.status !== 401) {
  fail(`development auth headers were accepted or private auth behaved unexpectedly: ${privateResponse.status}`);
}
requireHeader(privateResponse, 'access-control-allow-origin', webOrigin);
requireHeader(privateResponse, 'cache-control', 'no-store');
requireHeader(privateResponse, 'x-content-type-options', 'nosniff');
requireHeader(privateResponse, 'x-frame-options', 'DENY');
requireHeader(privateResponse, 'referrer-policy', 'strict-origin-when-cross-origin');
requireHeader(privateResponse, 'permissions-policy', 'camera=(), microphone=(), geolocation=(self)');

console.log(`Staging post-deploy smoke passed for ${apiUrl.origin}: health/readiness, live database connectivity, CORS allow/deny, production auth boundary and security headers are enforced.`);
