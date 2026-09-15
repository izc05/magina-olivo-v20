import assert from 'node:assert/strict';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for plans smoke test.');

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: 'google-plans-ci-0001',
  email: 'plans.ci@example.test',
  emailVerified: true,
  displayName: 'Planes CI',
  pictureUrl: null,
  givenName: 'Planes',
  familyName: 'CI',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

try {
  await app.ready();

  const catalog = await app.inject({ method: 'GET', url: '/api/v1/public/plans' });
  assert.equal(catalog.statusCode, 200, catalog.body);
  assert.equal(catalog.json().plans.length, 3);
  assert.equal(catalog.json().billing_enabled, false);
  assert.equal(catalog.json().checkout_available, false);
  assert.equal(catalog.json().beta_access_override, true);
  assert.deepEqual(catalog.json().plans.find((plan: { code: string }) => plan.code === 'free').future_entitlements, []);
  assert.deepEqual(
    catalog.json().plans.find((plan: { code: string }) => plan.code === 'professional').future_entitlements,
    ['advanced_automation', 'advanced_analysis', 'professional_commercial_suite'],
  );

  const credential = 'synthetic-google-id-token-'.padEnd(140, 'p');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const initial = await app.inject({ method: 'GET', url: '/api/v1/plans/current', headers });
  assert.equal(initial.statusCode, 200, initial.body);
  assert.equal(initial.json().effective_plan, 'free');
  assert.equal(initial.json().subscription.source, 'default');
  assert.equal(initial.json().can_manage_plan, true);
  assert.equal(initial.json().interests.length, 0);
  assert.equal(initial.json().beta_access_override, true);
  assert.deepEqual(initial.json().future_entitlements, []);

  const firstInterest = await app.inject({
    method: 'POST',
    url: '/api/v1/plans/interest',
    headers,
    payload: { target_plan: 'pro' },
  });
  assert.equal(firstInterest.statusCode, 201, firstInterest.body);
  assert.equal(firstInterest.json().interest.target_plan, 'pro');
  assert.equal(firstInterest.json().already_registered, false);
  assert.equal(firstInterest.json().checkout_available, false);

  const repeatedInterest = await app.inject({
    method: 'POST',
    url: '/api/v1/plans/interest',
    headers,
    payload: { target_plan: 'pro' },
  });
  assert.equal(repeatedInterest.statusCode, 200, repeatedInterest.body);
  assert.equal(repeatedInterest.json().already_registered, true);

  const requestCount = await sql<{ total: number }>`
    SELECT COUNT(*)::int AS total
    FROM plan_interest_requests
    WHERE workspace_id = ${workspaceId}::uuid
      AND target_plan = 'pro'
  `.execute(db);
  assert.equal(requestCount.rows[0]?.total, 1, 'interest must be idempotent per workspace and target plan');

  await sql`
    INSERT INTO workspace_plan_subscriptions (workspace_id, plan_code, status, source)
    VALUES (${workspaceId}::uuid, 'pro', 'active', 'manual')
    ON CONFLICT (workspace_id)
    DO UPDATE SET plan_code = 'pro', status = 'active', source = 'manual', updated_at = now()
  `.execute(db);
  await sql`
    UPDATE plan_interest_requests
    SET status = 'converted', updated_at = now()
    WHERE workspace_id = ${workspaceId}::uuid
      AND target_plan = 'pro'
  `.execute(db);

  const upgraded = await app.inject({ method: 'GET', url: '/api/v1/plans/current', headers });
  assert.equal(upgraded.statusCode, 200, upgraded.body);
  assert.equal(upgraded.json().effective_plan, 'pro');
  assert.equal(upgraded.json().subscription.source, 'manual');
  assert.equal(upgraded.json().interests.length, 0);
  assert.deepEqual(upgraded.json().future_entitlements, ['advanced_automation', 'advanced_analysis']);
  assert.equal(upgraded.json().beta_access_override, true);

  const duplicateActive = await app.inject({
    method: 'POST',
    url: '/api/v1/plans/interest',
    headers,
    payload: { target_plan: 'pro' },
  });
  assert.equal(duplicateActive.statusCode, 409, duplicateActive.body);
  assert.equal(duplicateActive.json().error, 'plan_already_active');

  await sql`
    UPDATE workspace_memberships
    SET role = 'member', updated_at = now()
    WHERE workspace_id = ${workspaceId}::uuid
      AND user_id = ${userId}::uuid
  `.execute(db);

  const memberView = await app.inject({ method: 'GET', url: '/api/v1/plans/current', headers });
  assert.equal(memberView.statusCode, 200, memberView.body);
  assert.equal(memberView.json().can_manage_plan, false);
  assert.equal(memberView.json().beta_access_override, true);

  const forbidden = await app.inject({
    method: 'POST',
    url: '/api/v1/plans/interest',
    headers,
    payload: { target_plan: 'professional' },
  });
  assert.equal(forbidden.statusCode, 403, forbidden.body);
  assert.equal(forbidden.json().error, 'plan_management_forbidden');

  console.log('PLANS_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
