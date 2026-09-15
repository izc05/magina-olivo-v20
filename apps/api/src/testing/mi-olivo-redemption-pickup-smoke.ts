import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Mi Olivo pickup smoke test.');
process.env.REWARD_QR_SECRET ??= 'ci-mi-olivo-pickup-secret-change-me';

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: `google-pickup-ci-${randomUUID()}`,
  email: `pickup.${randomUUID()}@example.test`,
  emailVerified: true,
  displayName: 'Pickup CI',
  pictureUrl: null,
  givenName: 'Pickup',
  familyName: 'CI',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

try {
  await app.ready();
  const credential = 'synthetic-google-pickup-id-token-'.padEnd(140, 'p');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const businessId = randomUUID();
  const businessSlug = `pickup-ci-${businessId.slice(0, 8)}`;
  await sql`
    INSERT INTO businesses (id, slug, name, status, published_at, created_by, updated_by)
    VALUES (${businessId}::uuid, ${businessSlug}, 'Almazara Pickup CI', 'published', now(), ${userId}::uuid, ${userId}::uuid)
  `.execute(db);

  const productId = randomUUID();
  await sql`
    INSERT INTO mill_reward_products (
      id, business_id, slug, title, olive_cost, stock_total, max_per_user,
      required_level, status, created_by, updated_by
    ) VALUES (
      ${productId}::uuid, ${businessId}::uuid, ${`botella-${productId.slice(0, 8)}`},
      'Botella AOVE Pickup CI', 200, 2, 1, 1, 'published', ${userId}::uuid, ${userId}::uuid
    )
  `.execute(db);

  await sql`
    INSERT INTO mi_olivo_ledger (
      user_id, workspace_id, event_type, source_type, source_id,
      points, reason, rule_version, idempotency_key
    ) VALUES (
      ${userId}::uuid, ${workspaceId}::uuid, 'pickup_seed', 'pickup_smoke', ${businessId},
      500, 'Saldo controlado para probar recogida', 'pickup-smoke-v1', ${`pickup-smoke-v1:${businessId}`}
    )
  `.execute(db);

  const redeemed = await app.inject({
    method: 'POST',
    url: `/api/v1/almazara-rewards/${productId}/redeem`,
    headers,
  });
  assert.equal(redeemed.statusCode, 201, redeemed.body);
  const redemptionId = String(redeemed.json().redemption.id);

  const pickups = await app.inject({
    method: 'GET',
    url: '/api/v1/my/almazara-redemption-pickups',
    headers,
  });
  assert.equal(pickups.statusCode, 200, pickups.body);
  const pickup = pickups.json().pickups.find((item: { redemptionId: string }) => item.redemptionId === redemptionId);
  assert.ok(pickup, 'reserved redemption must expose pickup metadata');
  assert.equal(pickup.businessSlug, businessSlug);

  const anonymous = await app.inject({ method: 'GET', url: '/api/v1/my/almazara-redemption-pickups' });
  assert.equal(anonymous.statusCode, 401, anonymous.body);

  console.log('Mi Olivo redemption pickup smoke OK');
} finally {
  await app.close();
  await db.destroy();
}
