import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Mi Olivo reward unlock smoke test.');
process.env.REWARD_QR_SECRET ??= 'ci-mi-olivo-reward-unlocks-secret-change-me';

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: `google-reward-unlocks-ci-${randomUUID()}`,
  email: `reward.unlocks.${randomUUID()}@example.test`,
  emailVerified: true,
  displayName: 'Reward Unlocks CI',
  pictureUrl: null,
  givenName: 'Reward',
  familyName: 'Unlocks',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

async function walletBalance(userId: string) {
  const result = await sql<{ balance: number }>`
    SELECT COALESCE(SUM(points),0)::int AS balance
    FROM mi_olivo_ledger
    WHERE user_id=${userId}::uuid
  `.execute(db);
  return Number(result.rows[0]?.balance ?? 0);
}

async function stock(productId: string) {
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
  const credential = 'synthetic-google-reward-unlocks-id-token-'.padEnd(140, 'u');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const businessId = randomUUID();
  const businessSlug = `reward-unlocks-ci-${businessId.slice(0, 8)}`;
  await sql`
    INSERT INTO businesses (id, slug, name, status, published_at, created_by, updated_by)
    VALUES (
      ${businessId}::uuid, ${businessSlug}, 'Almazara Unlocks CI',
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
      ${userId}::uuid, ${workspaceId}::uuid, 'reward_unlock_seed', 'reward_unlock_smoke', ${businessId},
      1200, 'XP y saldo controlados para prueba de desbloqueo',
      'reward-unlock-smoke-v1', ${`reward-unlock-smoke-v1:seed:${businessId}`}
    )
  `.execute(db);

  const progression = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/progression', headers });
  assert.equal(progression.statusCode, 200, progression.body);
  assert.equal(progression.json().currency.balance, 1200);
  assert.equal(progression.json().xp, 1200);
  assert.equal(progression.json().current_level.level, 6);

  const lockedProductId = randomUUID();
  await sql`
    INSERT INTO mill_reward_products (
      id, business_id, slug, title, olive_cost, stock_total, max_per_user,
      required_level, status, created_by, updated_by
    ) VALUES (
      ${lockedProductId}::uuid, ${businessId}::uuid, ${`locked-${lockedProductId.slice(0, 8)}`},
      'AOVE nivel 7', 300, 2, 1, 7, 'published', ${userId}::uuid, ${userId}::uuid
    )
  `.execute(db);

  const publicUnlocks = await app.inject({
    method: 'GET',
    url: `/api/v1/public/almazaras/${businessSlug}/reward-unlocks`,
  });
  assert.equal(publicUnlocks.statusCode, 200, publicUnlocks.body);
  assert.equal(publicUnlocks.json().rewards[0].requiredLevel, 7);
  assert.equal(publicUnlocks.json().rewards[0].requiredLevelName, 'Olivo maduro');
  assert.equal(publicUnlocks.json().rewards[0].minXp, 1400);

  const myUnlocks = await app.inject({
    method: 'GET',
    url: `/api/v1/my/almazaras/${businessSlug}/reward-unlocks`,
    headers,
  });
  assert.equal(myUnlocks.statusCode, 200, myUnlocks.body);
  assert.equal(myUnlocks.json().currentLevel, 6);
  assert.equal(myUnlocks.json().xp, 1200);
  assert.equal(myUnlocks.json().balance, 1200);
  assert.equal(myUnlocks.json().rewards[0].unlocked, false);

  const beforeBlockedBalance = await walletBalance(userId);
  const blocked = await app.inject({
    method: 'POST',
    url: `/api/v1/almazara-rewards/${lockedProductId}/redeem`,
    headers,
  });
  assert.equal(blocked.statusCode, 409, blocked.body);
  assert.equal(blocked.json().error, 'reward_level_locked');
  assert.equal(blocked.json().currentLevel, 6);
  assert.equal(blocked.json().requiredLevel, 7);
  assert.equal(await walletBalance(userId), beforeBlockedBalance, 'locked reward cannot spend olives');
  assert.deepEqual(await stock(lockedProductId), { stock_reserved: 0, stock_redeemed: 0 });

  // Database defense-in-depth: bypassing the HTTP endpoint must still fail.
  const bypassProductId = randomUUID();
  await sql`
    INSERT INTO mill_reward_products (
      id, business_id, slug, title, olive_cost, stock_total, required_level,
      status, created_by, updated_by
    ) VALUES (
      ${bypassProductId}::uuid, ${businessId}::uuid, ${`bypass-${bypassProductId.slice(0, 8)}`},
      'AOVE protegido en base', 100, 1, 7, 'published', ${userId}::uuid, ${userId}::uuid
    )
  `.execute(db);

  await assert.rejects(
    () => db.transaction().execute(async (trx) => {
      const inserted = await sql<{ id: string }>`
        INSERT INTO mill_reward_redemptions (
          user_id, business_id, product_id, olives_spent, expires_at,
          product_title_snapshot, business_name_snapshot
        ) VALUES (
          ${userId}::uuid, ${businessId}::uuid, ${bypassProductId}::uuid, 100,
          now()+interval '7 days', 'AOVE protegido en base', 'Almazara Unlocks CI'
        ) RETURNING id::text
      `.execute(trx);
      const redemptionId = inserted.rows[0]?.id;
      assert.ok(redemptionId);
      await sql`
        INSERT INTO mi_olivo_ledger (
          user_id, workspace_id, event_type, source_type, source_id,
          points, reason, rule_version, idempotency_key
        ) VALUES (
          ${userId}::uuid, NULL, 'reward_redemption', 'mill_reward_redemption', ${redemptionId},
          -100, 'Intento de bypass de nivel', 'reward-unlock-smoke-v1',
          ${`reward-unlock-smoke-v1:bypass:${redemptionId}`}
        )
      `.execute(trx);
    }),
    /reward_level_locked/,
  );
  assert.equal(await walletBalance(userId), beforeBlockedBalance, 'database guard must preserve wallet on bypass');
  assert.deepEqual(await stock(bypassProductId), { stock_reserved: 0, stock_redeemed: 0 });

  const unlock = await app.inject({
    method: 'PUT',
    url: `/api/v1/my/businesses/${businessId}/almazara-rewards/${lockedProductId}/unlock`,
    headers,
    payload: { requiredLevel: 6 },
  });
  assert.equal(unlock.statusCode, 200, unlock.body);
  assert.equal(unlock.json().reward.requiredLevel, 6);
  assert.equal(unlock.json().reward.requiredLevelName, 'Olivo de cosecha');

  const nowUnlocked = await app.inject({
    method: 'GET',
    url: `/api/v1/my/almazaras/${businessSlug}/reward-unlocks`,
    headers,
  });
  assert.equal(nowUnlocked.statusCode, 200, nowUnlocked.body);
  assert.equal(nowUnlocked.json().balance, 1200);
  const unlockedState = nowUnlocked.json().rewards.find((item: { rewardId: string }) => item.rewardId === lockedProductId);
  assert.equal(unlockedState?.unlocked, true);

  const redeemed = await app.inject({
    method: 'POST',
    url: `/api/v1/almazara-rewards/${lockedProductId}/redeem`,
    headers,
  });
  assert.equal(redeemed.statusCode, 201, redeemed.body);
  assert.equal(redeemed.json().redemption.status, 'reserved');
  assert.equal(await walletBalance(userId), 900);
  assert.deepEqual(await stock(lockedProductId), { stock_reserved: 1, stock_redeemed: 0 });

  const rewardStateAfterSpend = await app.inject({
    method: 'GET',
    url: `/api/v1/my/almazaras/${businessSlug}/reward-unlocks`,
    headers,
  });
  assert.equal(rewardStateAfterSpend.statusCode, 200, rewardStateAfterSpend.body);
  assert.equal(rewardStateAfterSpend.json().balance, 900, 'reward state must expose live spendable balance');
  assert.equal(rewardStateAfterSpend.json().xp, 1200, 'spending must not reduce reward XP state');
  assert.equal(rewardStateAfterSpend.json().currentLevel, 6, 'spending must not reduce reward level state');

  const afterSpend = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/progression', headers });
  assert.equal(afterSpend.statusCode, 200, afterSpend.body);
  assert.equal(afterSpend.json().currency.balance, 900);
  assert.equal(afterSpend.json().xp, 1200, 'reward spending must not reduce permanent XP');
  assert.equal(afterSpend.json().current_level.level, 6, 'reward spending must not reduce permanent level');

  console.log('Mi Olivo reward level unlock + spendable balance smoke OK');
} finally {
  await app.close();
  await db.destroy();
}
