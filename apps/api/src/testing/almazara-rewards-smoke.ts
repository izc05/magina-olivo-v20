import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Almazara rewards smoke test.');
process.env.REWARD_QR_SECRET ??= 'ci-almazara-rewards-smoke-secret-change-me';

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: `google-almazara-rewards-ci-${randomUUID()}`,
  email: `almazara.rewards.${randomUUID()}@example.test`,
  emailVerified: true,
  displayName: 'Almazara Rewards CI',
  pictureUrl: null,
  givenName: 'Almazara',
  familyName: 'CI',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

async function product(businessId: string, userId: string, slug: string, title: string, oliveCost: number) {
  const id = randomUUID();
  await sql`
    INSERT INTO mill_reward_products (
      id, business_id, slug, title, olive_cost, stock_total, max_per_user,
      status, created_by, updated_by
    ) VALUES (
      ${id}::uuid, ${businessId}::uuid, ${slug}, ${title}, ${oliveCost}, 1, 1,
      'published', ${userId}::uuid, ${userId}::uuid
    )
  `.execute(db);
  return id;
}

async function balance(userId: string) {
  const result = await sql<{ balance: number }>`
    SELECT COALESCE(SUM(points),0)::int AS balance
    FROM mi_olivo_ledger
    WHERE user_id=${userId}::uuid
  `.execute(db);
  return Number(result.rows[0]?.balance ?? 0);
}

async function productStock(productId: string) {
  const result = await sql<{ stock_reserved: number; stock_redeemed: number }>`
    SELECT stock_reserved, stock_redeemed
    FROM mill_reward_products
    WHERE id=${productId}::uuid
  `.execute(db);
  const row = result.rows[0];
  assert.ok(row, 'reward product must exist');
  return row;
}

try {
  await app.ready();
  const credential = 'synthetic-google-reward-id-token-'.padEnd(140, 'r');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const businessId = randomUUID();
  await sql`
    INSERT INTO businesses (id, slug, name, status, published_at, created_by, updated_by)
    VALUES (
      ${businessId}::uuid, ${`almazara-rewards-ci-${businessId.slice(0, 8)}`}, 'Almazara Rewards CI',
      'published', now(), ${userId}::uuid, ${userId}::uuid
    )
  `.execute(db);
  await sql`
    INSERT INTO business_memberships (business_id, user_id, role, status)
    VALUES (${businessId}::uuid, ${userId}::uuid, 'owner', 'active')
  `.execute(db);

  await sql`
    INSERT INTO mi_olivo_ledger (
      user_id, workspace_id, event_type, source_type, source_id,
      points, reason, rule_version, idempotency_key
    ) VALUES (
      ${userId}::uuid, ${workspaceId}::uuid, 'security_smoke_seed', 'security_smoke', ${businessId},
      1200, 'Saldo controlado para probar concurrencia de recompensas',
      'reward-security-smoke-v1', ${`reward-security-smoke-v1:seed:${businessId}`}
    )
  `.execute(db);

  const progressionBefore = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/progression', headers });
  assert.equal(progressionBefore.statusCode, 200, progressionBefore.body);
  assert.equal(progressionBefore.json().currency.balance, 1200);
  assert.equal(progressionBefore.json().xp, 1200);
  assert.equal(progressionBefore.json().current_level.level, 6);

  const concurrentA = await product(businessId, userId, `concurrent-a-${businessId.slice(0, 8)}`, 'AOVE concurrente A', 700);
  const concurrentB = await product(businessId, userId, `concurrent-b-${businessId.slice(0, 8)}`, 'AOVE concurrente B', 700);

  const concurrentResults = await Promise.all([
    app.inject({ method: 'POST', url: `/api/v1/almazara-rewards/${concurrentA}/redeem`, headers }),
    app.inject({ method: 'POST', url: `/api/v1/almazara-rewards/${concurrentB}/redeem`, headers }),
  ]);
  const successIndex = concurrentResults.findIndex((response) => response.statusCode === 201);
  const failureIndex = concurrentResults.findIndex((response) => response.statusCode === 409);
  assert.notEqual(successIndex, -1, 'exactly one concurrent reward reservation must succeed');
  assert.notEqual(failureIndex, -1, 'the second concurrent reservation must be rejected');
  assert.equal(concurrentResults.filter((response) => response.statusCode === 201).length, 1);
  assert.equal(concurrentResults.filter((response) => response.statusCode === 409).length, 1);
  assert.equal(concurrentResults[failureIndex].json().error, 'insufficient_olives');
  assert.equal(await balance(userId), 500, 'concurrent spending cannot make the wallet negative');

  const successProductId = successIndex === 0 ? concurrentA : concurrentB;
  const failedProductId = successIndex === 0 ? concurrentB : concurrentA;
  assert.deepEqual(await productStock(successProductId), { stock_reserved: 1, stock_redeemed: 0 });
  assert.deepEqual(await productStock(failedProductId), { stock_reserved: 0, stock_redeemed: 0 });

  const reserved = concurrentResults[successIndex].json().redemption;
  const redemptionId = String(reserved.id);
  const token = String(reserved.token);
  assert.match(token, /^[0-9a-f-]{36}\.[A-Za-z0-9_-]{16}$/i);
  assert.equal(token.length, 53);
  assert.equal(String(reserved.qrPayload), token, 'QR payload must be the signed single-use token');

  const credentialRow = await sql<{
    redemption_code: string;
    qr_token_hash: string;
    product_title_snapshot: string;
    business_name_snapshot: string;
  }>`
    SELECT redemption_code::text, qr_token_hash, product_title_snapshot, business_name_snapshot
    FROM mill_reward_redemptions
    WHERE id=${redemptionId}::uuid
  `.execute(db);
  const stored = credentialRow.rows[0];
  assert.ok(stored, 'reserved redemption must exist');
  assert.equal(token.startsWith(`${stored.redemption_code}.`), true);
  assert.match(stored.qr_token_hash, /^[0-9a-f]{64}$/);
  assert.notEqual(stored.qr_token_hash, token, 'raw signed QR token must not be stored');
  assert.ok(stored.product_title_snapshot);
  assert.equal(stored.business_name_snapshot, 'Almazara Rewards CI');

  const tamperedToken = `${token.slice(0, -1)}${token.endsWith('A') ? 'B' : 'A'}`;
  const tampered = await app.inject({
    method: 'POST',
    url: `/api/v1/my/businesses/${businessId}/almazara-redemptions/${encodeURIComponent(tamperedToken)}/redeem`,
    headers,
  });
  assert.equal(tampered.statusCode, 400, tampered.body);
  assert.equal(tampered.json().error, 'invalid_redemption_token');

  const scanned = await app.inject({
    method: 'POST',
    url: `/api/v1/my/businesses/${businessId}/almazara-redemptions/${encodeURIComponent(token)}/redeem`,
    headers,
  });
  assert.equal(scanned.statusCode, 200, scanned.body);
  assert.equal(scanned.json().redemption.status, 'redeemed');
  assert.deepEqual(await productStock(successProductId), { stock_reserved: 0, stock_redeemed: 1 });

  const doubleScan = await app.inject({
    method: 'POST',
    url: `/api/v1/my/businesses/${businessId}/almazara-redemptions/${encodeURIComponent(token)}/redeem`,
    headers,
  });
  assert.equal(doubleScan.statusCode, 409, doubleScan.body);
  assert.equal(doubleScan.json().error, 'redemption_not_redeemable');

  const progressionAfterSpend = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/progression', headers });
  assert.equal(progressionAfterSpend.statusCode, 200, progressionAfterSpend.body);
  assert.equal(progressionAfterSpend.json().currency.balance, 500);
  assert.equal(progressionAfterSpend.json().xp, 1200, 'spending olives must never reduce lifetime XP');
  assert.equal(progressionAfterSpend.json().current_level.level, 6, 'spending olives must never lower the tree level');

  const cancelProduct = await product(businessId, userId, `cancel-${businessId.slice(0, 8)}`, 'AOVE cancelación CI', 250);
  const cancelReservation = await app.inject({ method: 'POST', url: `/api/v1/almazara-rewards/${cancelProduct}/redeem`, headers });
  assert.equal(cancelReservation.statusCode, 201, cancelReservation.body);
  assert.equal(await balance(userId), 250);
  const cancelId = String(cancelReservation.json().redemption.id);

  const cancelled = await app.inject({ method: 'POST', url: `/api/v1/my/almazara-redemptions/${cancelId}/cancel`, headers });
  assert.equal(cancelled.statusCode, 200, cancelled.body);
  assert.equal(cancelled.json().redemption.status, 'cancelled');
  assert.equal(await balance(userId), 500, 'cancelled reservation must refund olives exactly once');
  assert.deepEqual(await productStock(cancelProduct), { stock_reserved: 0, stock_redeemed: 0 });

  const cancelAgain = await app.inject({ method: 'POST', url: `/api/v1/my/almazara-redemptions/${cancelId}/cancel`, headers });
  assert.equal(cancelAgain.statusCode, 409, cancelAgain.body);
  const cancelRefund = await sql<{ total: number; points: number }>`
    SELECT COUNT(*)::int AS total, COALESCE(SUM(points),0)::int AS points
    FROM mi_olivo_ledger
    WHERE user_id=${userId}::uuid AND event_type='reward_refund' AND source_id=${cancelId}
  `.execute(db);
  assert.equal(cancelRefund.rows[0]?.total, 1);
  assert.equal(cancelRefund.rows[0]?.points, 250);

  const expireProduct = await product(businessId, userId, `expire-${businessId.slice(0, 8)}`, 'AOVE caducidad CI', 250);
  const expireReservation = await app.inject({ method: 'POST', url: `/api/v1/almazara-rewards/${expireProduct}/redeem`, headers });
  assert.equal(expireReservation.statusCode, 201, expireReservation.body);
  assert.equal(await balance(userId), 250);
  const expireId = String(expireReservation.json().redemption.id);
  await sql`
    UPDATE mill_reward_redemptions
    SET expires_at=now()-interval '1 minute', updated_at=now()
    WHERE id=${expireId}::uuid
  `.execute(db);

  const sweepOne = await sql<{ expired: number }>`
    SELECT expire_stale_mill_reward_reservations(500)::int AS expired
  `.execute(db);
  assert.equal(sweepOne.rows[0]?.expired, 1);
  const sweepTwo = await sql<{ expired: number }>`
    SELECT expire_stale_mill_reward_reservations(500)::int AS expired
  `.execute(db);
  assert.equal(sweepTwo.rows[0]?.expired, 0, 'expiry sweep must be idempotent');
  assert.equal(await balance(userId), 500, 'expired reservation must refund olives exactly once');
  assert.deepEqual(await productStock(expireProduct), { stock_reserved: 0, stock_redeemed: 0 });

  const expiredRow = await sql<{ status: string }>`
    SELECT status FROM mill_reward_redemptions WHERE id=${expireId}::uuid
  `.execute(db);
  assert.equal(expiredRow.rows[0]?.status, 'expired');
  const expireRefund = await sql<{ total: number; points: number }>`
    SELECT COUNT(*)::int AS total, COALESCE(SUM(points),0)::int AS points
    FROM mi_olivo_ledger
    WHERE user_id=${userId}::uuid AND event_type='reward_refund' AND source_id=${expireId}
  `.execute(db);
  assert.equal(expireRefund.rows[0]?.total, 1);
  assert.equal(expireRefund.rows[0]?.points, 250);

  const audit = await sql<{ redemption_events: number; stock_events: number }>`
    SELECT
      (SELECT COUNT(*)::int FROM mill_reward_redemption_audit WHERE redemption_id=${redemptionId}::uuid) AS redemption_events,
      (SELECT COUNT(*)::int FROM mill_reward_stock_audit WHERE redemption_id=${redemptionId}::uuid) AS stock_events
  `.execute(db);
  assert.ok((audit.rows[0]?.redemption_events ?? 0) >= 2, 'successful redemption must keep a complete lifecycle audit');
  assert.ok((audit.rows[0]?.stock_events ?? 0) >= 2, 'successful redemption must audit reserved and redeemed stock');

  const progressionFinal = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/progression', headers });
  assert.equal(progressionFinal.statusCode, 200, progressionFinal.body);
  assert.equal(progressionFinal.json().currency.balance, 500);
  assert.equal(progressionFinal.json().xp, 1200);
  assert.equal(progressionFinal.json().current_level.level, 6);

  console.log('Almazara rewards transaction + signed QR smoke OK');
} finally {
  await app.close();
  await db.destroy();
}
