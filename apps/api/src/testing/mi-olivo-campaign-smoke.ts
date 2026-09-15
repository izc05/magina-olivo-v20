import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Mi Olivo campaign smoke test.');

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: 'google-mi-olivo-v5-ci-0001',
  email: 'mi.olivo.v5.ci@example.test',
  emailVerified: true,
  displayName: 'Agricultor Mi Olivo V5 CI',
  pictureUrl: null,
  givenName: 'Agricultor',
  familyName: 'V5',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

async function createCampaignData(input: {
  workspaceId: string;
  userId: string;
  fieldId: string;
  name: string;
  startDate: string;
}) {
  const campaignId = randomUUID();
  await sql`
    INSERT INTO campaigns (id, workspace_id, name, start_date, status)
    VALUES (${campaignId}::uuid, ${input.workspaceId}::uuid, ${input.name}, ${input.startDate}::date, 'active')
  `.execute(db);

  const deliveryIds: string[] = [];
  for (let index = 0; index < 3; index += 1) {
    const deliveryId = randomUUID();
    deliveryIds.push(deliveryId);
    await sql`
      INSERT INTO harvest_deliveries (
        id, workspace_id, campaign_id, delivery_at, total_kg, source, client_operation_id, created_by
      ) VALUES (
        ${deliveryId}::uuid,
        ${input.workspaceId}::uuid,
        ${campaignId}::uuid,
        now(),
        400,
        'manual',
        ${randomUUID()}::uuid,
        ${input.userId}::uuid
      )
    `.execute(db);
    await sql`
      INSERT INTO harvest_delivery_fields (delivery_id, field_id, kg)
      VALUES (${deliveryId}::uuid, ${input.fieldId}::uuid, 400)
    `.execute(db);
  }

  await sql`
    INSERT INTO delivery_results (
      id, workspace_id, delivery_id, result_date, yield_percent, status, client_operation_id, created_by
    ) VALUES (
      ${randomUUID()}::uuid,
      ${input.workspaceId}::uuid,
      ${deliveryIds[0]}::uuid,
      current_date,
      21.5,
      'confirmed',
      ${randomUUID()}::uuid,
      ${input.userId}::uuid
    )
  `.execute(db);

  return campaignId;
}

try {
  await app.ready();
  const credential = 'synthetic-google-v5-token-'.padEnd(140, 'v');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const fieldId = randomUUID();
  await sql`
    INSERT INTO fields (id, workspace_id, client_operation_id, name)
    VALUES (${fieldId}::uuid, ${workspaceId}::uuid, ${randomUUID()}::uuid, 'Finca V5 CI')
  `.execute(db);

  const firstCampaignId = await createCampaignData({
    workspaceId,
    userId,
    fieldId,
    name: 'Campaña V5 primera',
    startDate: '2026-09-01',
  });

  const core = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(core.statusCode, 200, core.body);
  assert.equal(core.json().missions.find((mission: { id: string }) => mission.id === 'harvest').completed, true);

  const firstCampaign = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/campaign', headers });
  assert.equal(firstCampaign.statusCode, 200, firstCampaign.body);
  assert.equal(firstCampaign.json().campaign.id, firstCampaignId);
  assert.equal(firstCampaign.json().metrics.delivery_count, 3);
  assert.equal(firstCampaign.json().metrics.delivered_kg, 1200);
  assert.equal(firstCampaign.json().metrics.has_confirmed_yield, true);
  assert.equal(firstCampaign.json().newly_awarded_points, 50);
  assert.equal(firstCampaign.json().campaign_olives_earned, 50);
  for (const mission of firstCampaign.json().missions) assert.equal(mission.completed, true);

  const appearance = firstCampaign.json().appearance;
  assert.equal(appearance.options.find((option: { id: string }) => option.id === 'harvest').unlocked, true);
  assert.equal(appearance.options.find((option: { id: string }) => option.id === 'campaign').unlocked, true);
  assert.equal(appearance.options.find((option: { id: string }) => option.id === 'explorer').unlocked, false);

  const lockedBadge = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/appearance',
    headers,
    payload: { badge: 'explorer' },
  });
  assert.equal(lockedBadge.statusCode, 409, lockedBadge.body);
  assert.equal(lockedBadge.json().error, 'badge_locked');

  const selectedBadge = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/appearance',
    headers,
    payload: { badge: 'campaign' },
  });
  assert.equal(selectedBadge.statusCode, 200, selectedBadge.body);
  assert.equal(selectedBadge.json().selected_badge, 'campaign');

  const repeated = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/campaign', headers });
  assert.equal(repeated.statusCode, 200, repeated.body);
  assert.equal(repeated.json().newly_awarded_points, 0, 'campaign rewards must be idempotent');
  assert.equal(repeated.json().campaign_olives_earned, 50);
  assert.equal(repeated.json().appearance.selected_badge, 'campaign');

  const paused = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/preferences',
    headers,
    payload: { enabled: false },
  });
  assert.equal(paused.statusCode, 200, paused.body);

  const secondCampaignId = await createCampaignData({
    workspaceId,
    userId,
    fieldId,
    name: 'Campaña V5 segunda',
    startDate: '2026-09-10',
  });

  const pausedCampaign = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/campaign', headers });
  assert.equal(pausedCampaign.statusCode, 200, pausedCampaign.body);
  assert.equal(pausedCampaign.json().campaign.id, secondCampaignId);
  assert.equal(pausedCampaign.json().enabled, false);
  assert.equal(pausedCampaign.json().newly_awarded_points, 0, 'paused Mi Olivo must not award campaign olives');
  assert.equal(pausedCampaign.json().campaign_olives_earned, 50);
  for (const mission of pausedCampaign.json().missions) assert.equal(mission.completed, true, 'progress remains visible while paused');

  const reenabled = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/preferences',
    headers,
    payload: { enabled: true },
  });
  assert.equal(reenabled.statusCode, 200, reenabled.body);

  const secondCampaign = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/campaign', headers });
  assert.equal(secondCampaign.statusCode, 200, secondCampaign.body);
  assert.equal(secondCampaign.json().newly_awarded_points, 50, 'a new campaign can earn the same missions once');
  assert.equal(secondCampaign.json().campaign_olives_earned, 100);

  const ledger = await sql<{ total: number; points: number; campaigns: number }>`
    SELECT
      COUNT(*)::int AS total,
      COALESCE(SUM(points), 0)::int AS points,
      COUNT(DISTINCT source_id)::int AS campaigns
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND workspace_id = ${workspaceId}::uuid
      AND rule_version = 'mi-olivo-v5'
  `.execute(db);
  assert.equal(ledger.rows[0]?.total, 6);
  assert.equal(ledger.rows[0]?.points, 100);
  assert.equal(ledger.rows[0]?.campaigns, 2);

  console.log('MI_OLIVO_CAMPAIGN_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
