import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const allowedOrigin = 'https://staging.example.test';
const rejectedOrigin = 'https://untrusted.example.test';
const developmentUserId = '33333333-3333-4333-8333-333333333333';
const developmentWorkspaceId = '11111111-1111-4111-8111-111111111111';

function requireCondition(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for staging security smoke');

process.env.NODE_ENV = 'production';
process.env.CORS_ALLOWED_ORIGINS = allowedOrigin;
process.env.ALLOW_DEV_AUTH_HEADERS = 'false';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

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

  console.log('Staging security smoke passed: production CORS, auth headers and private-cache/security headers are enforced.');
} finally {
  await app.close();
  await db.destroy();
}
