import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const allowedOrigin = 'https://staging.example.test';
const rejectedOrigin = 'https://untrusted.example.test';
const developmentUserId = '33333333-3333-4333-8333-333333333333';
const developmentWorkspaceId = '11111111-1111-4111-8111-111111111111';
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function requireCondition(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for staging security smoke');

process.env.NODE_ENV = 'production';
process.env.CORS_ALLOWED_ORIGINS = allowedOrigin;
process.env.ALLOW_DEV_AUTH_HEADERS = 'false';
process.env.RATE_LIMIT_ENABLED = 'true';
process.env.RATE_LIMIT_WINDOW_MS = '60000';
process.env.RATE_LIMIT_AUTH_MAX = '2';
process.env.RATE_LIMIT_PUBLIC_MAX = '2';
process.env.RATE_LIMIT_PRIVATE_MAX = '2';
process.env.RATE_LIMIT_MAX_BUCKETS = '100';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });
const appWithoutDb = buildApp();

try {
  const health = await app.inject({
    method: 'GET',
    url: '/health',
    headers: { origin: allowedOrigin },
  });
  requireCondition(health.statusCode === 200, `Expected health 200, got ${health.statusCode}`);
  requireCondition(
    health.headers['access-control-allow-origin'] === allowedOrigin,
    `Expected allowed CORS origin, got ${String(health.headers['access-control-allow-origin'])}`,
  );
  requireCondition(
    typeof health.headers['x-request-id'] === 'string' && uuidPattern.test(health.headers['x-request-id']),
    `Health response is missing UUID X-Request-Id: ${String(health.headers['x-request-id'])}`,
  );

  const ready = await app.inject({ method: 'GET', url: '/ready' });
  requireCondition(ready.statusCode === 200, `Expected ready 200, got ${ready.statusCode}: ${ready.body}`);
  const readiness = ready.json() as { ok?: unknown; database?: unknown };
  requireCondition(readiness.ok === true && readiness.database === 'ready', `Unexpected readiness payload: ${ready.body}`);

  const notReady = await appWithoutDb.inject({ method: 'GET', url: '/ready' });
  requireCondition(notReady.statusCode === 503, `Expected unconfigured readiness 503, got ${notReady.statusCode}`);
  requireCondition(
    (notReady.json() as { database?: unknown }).database === 'unconfigured',
    `Unexpected unconfigured readiness payload: ${notReady.body}`,
  );

  const rejected = await app.inject({
    method: 'GET',
    url: '/health',
    headers: { origin: rejectedOrigin },
  });
  requireCondition(rejected.statusCode >= 400, `Untrusted origin was not rejected: ${rejected.statusCode}`);
  requireCondition(
    rejected.headers['access-control-allow-origin'] !== rejectedOrigin,
    'Untrusted origin received an Access-Control-Allow-Origin header',
  );

  const privateRequest = await app.inject({
    method: 'GET',
    url: '/api/v1/me',
    headers: {
      origin: allowedOrigin,
      'x-user-id': developmentUserId,
      'x-workspace-id': developmentWorkspaceId,
    },
  });
  requireCondition(
    privateRequest.statusCode === 401,
    `Development auth headers were accepted in production mode: ${privateRequest.statusCode}`,
  );
  requireCondition(privateRequest.headers['cache-control'] === 'no-store', 'Private API is missing Cache-Control: no-store');
  requireCondition(privateRequest.headers['x-content-type-options'] === 'nosniff', 'API is missing X-Content-Type-Options: nosniff');
  requireCondition(privateRequest.headers['x-frame-options'] === 'DENY', 'API is missing X-Frame-Options: DENY');
  requireCondition(
    privateRequest.headers['referrer-policy'] === 'strict-origin-when-cross-origin',
    'API is missing the expected Referrer-Policy',
  );
  requireCondition(
    privateRequest.headers['permissions-policy'] === 'camera=(), microphone=(), geolocation=(self)',
    'API is missing the expected Permissions-Policy',
  );
  requireCondition(privateRequest.headers['x-ratelimit-limit'] === '2', 'Private API rate limit header is missing or incorrect');
  requireCondition(privateRequest.headers['x-ratelimit-remaining'] === '1', 'Private API remaining rate limit is incorrect');
  requireCondition(
    privateRequest.headers['x-request-id'] !== health.headers['x-request-id'],
    'Request IDs must be unique per request',
  );

  const secondPrivateRequest = await app.inject({ method: 'GET', url: '/api/v1/me' });
  requireCondition(secondPrivateRequest.statusCode === 401, `Expected second private request 401, got ${secondPrivateRequest.statusCode}`);
  requireCondition(secondPrivateRequest.headers['x-ratelimit-remaining'] === '0', 'Expected private rate limit to reach zero remaining');

  const limited = await app.inject({ method: 'GET', url: '/api/v1/me' });
  requireCondition(limited.statusCode === 429, `Expected private rate limit 429, got ${limited.statusCode}`);
  requireCondition(typeof limited.headers['retry-after'] === 'string', '429 response is missing Retry-After');
  requireCondition(limited.headers['x-ratelimit-remaining'] === '0', '429 response should report zero remaining');
  const limitedBody = limited.json() as { error?: unknown; request_id?: unknown };
  requireCondition(limitedBody.error === 'rate_limited', `Unexpected 429 payload: ${limited.body}`);
  requireCondition(limitedBody.request_id === limited.headers['x-request-id'], '429 request_id does not match X-Request-Id');

  const healthAfterLimit = await app.inject({ method: 'GET', url: '/health' });
  requireCondition(healthAfterLimit.statusCode === 200, 'Health endpoint must remain exempt from rate limiting');

  console.log('Staging security smoke passed: CORS, production auth, security headers, request IDs, readiness and rate limiting are enforced.');
} finally {
  await app.close();
  await appWithoutDb.close();
  await db.destroy();
}
