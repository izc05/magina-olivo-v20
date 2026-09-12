import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin campaign/plan smoke test.');

const db = createDatabase(databaseUrl);
const claims: GoogleIdentityClaims = {
  subject: 'campaign-plan-admin-subject',
  email: 'campaign-plan-admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Campañas Planes',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Campañas',
  hostedDomain: 'magina.test',
};
const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-campaign-plan-token-'.padEnd(140, 'p');

async function login() {
  const response = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.ok(response.statusCode === 200 || response.statusCode === 201, response.body);
  const cookieHeader = response.headers['set-cookie'];
  assert.equal(typeof cookieHeader, 'string');
  return { body: response.json(), cookie: String(cookieHeader).split(';', 1)[0] };
}

try {
  await app.ready();
  const adminLogin = await login();
  const userId = adminLogin.body.user.id as string;
  const workspace = await db.insertInto('workspaces').values({ name: 'Workspace Campaña Admin', type: 'professional' }).returningAll().executeTakeFirstOrThrow();
  const campaign = await db.insertInto('campaigns').values({
    workspace_id: workspace.id,
    name: 'Campaña 2026/27 Smoke',
    start_date: '2026-09-01',
    end_date: null,
    status: 'active',
  }).returningAll().executeTakeFirstOrThrow();

  const interest = await db.insertInto('plan_interest_requests').values({
    workspace_id: workspace.id,
    requested_by: userId,
    target_plan: 'professional',
    status: 'pending',
  }).returningAll().executeTakeFirstOrThrow();

  const campaigns = await app.inject({ method: 'GET', url: `/api/v1/admin/campaigns?workspace_id=${workspace.id}`, headers: { cookie: adminLogin.cookie } });
  assert.equal(campaigns.statusCode, 200, campaigns.body);
  assert.ok(campaigns.json().campaigns.some((item: { id: string }) => item.id === campaign.id));

  const invalidWindow = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/campaigns/${campaign.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { start_date: '2026-10-01', end_date: '2026-09-01' },
  });
  assert.equal(invalidWindow.statusCode, 400, invalidWindow.body);
  assert.equal(invalidWindow.json().error, 'invalid_campaign_window');

  const updateCampaign = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/campaigns/${campaign.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { name: 'Campaña Administrada 2026/27', start_date: '2026-09-01', end_date: '2027-08-31', status: 'closed' },
  });
  assert.equal(updateCampaign.statusCode, 200, updateCampaign.body);
  assert.equal(updateCampaign.json().campaign.status, 'closed');

  const plansBefore = await app.inject({ method: 'GET', url: '/api/v1/admin/plans', headers: { cookie: adminLogin.cookie } });
  assert.equal(plansBefore.statusCode, 200, plansBefore.body);
  assert.equal(plansBefore.json().billing_enabled, false);
  assert.ok(plansBefore.json().interests.some((item: { id: string }) => item.id === interest.id));

  const setPlan = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/workspaces/${workspace.id}/plan`,
    headers: { cookie: adminLogin.cookie },
    payload: { plan_code: 'professional', status: 'trialing', current_period_end: '2026-12-31T23:59:59.000Z' },
  });
  assert.equal(setPlan.statusCode, 200, setPlan.body);
  assert.equal(setPlan.json().subscription.plan_code, 'professional');
  assert.equal(setPlan.json().subscription.source, 'manual');
  assert.equal(setPlan.json().billing_enabled, false);

  const updateInterest = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/plan-interests/${interest.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { status: 'contacted' },
  });
  assert.equal(updateInterest.statusCode, 200, updateInterest.body);
  assert.equal(updateInterest.json().interest.status, 'contacted');

  const storedPlan = await db.selectFrom('workspace_plan_subscriptions').selectAll().where('workspace_id', '=', workspace.id).executeTakeFirstOrThrow();
  assert.equal(storedPlan.plan_code, 'professional');
  assert.equal(storedPlan.status, 'trialing');
  assert.equal(storedPlan.source, 'manual');

  const storedInterest = await db.selectFrom('plan_interest_requests').select(['status']).where('id', '=', interest.id).executeTakeFirstOrThrow();
  assert.equal(storedInterest.status, 'contacted');

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  const actions = new Set(audit.json().entries.map((entry: { action: string }) => entry.action));
  assert.ok(actions.has('campaign.updated'));
  assert.ok(actions.has('workspace_plan.updated'));
  assert.ok(actions.has('plan_interest.updated'));

  console.log('ADMIN_CAMPAIGN_PLANS_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
