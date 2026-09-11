import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Mi Olivo smoke test.');

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: 'google-mi-olivo-ci-0001',
  email: 'mi.olivo.ci@example.test',
  emailVerified: true,
  displayName: 'Agricultor Mi Olivo CI',
  pictureUrl: null,
  givenName: 'Agricultor',
  familyName: 'CI',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

try {
  await app.ready();
  const credential = 'synthetic-google-id-token-'.padEnd(140, 'm');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const profile = await app.inject({
    method: 'PATCH',
    url: '/api/v1/me/profile',
    headers,
    payload: {
      municipality: 'Bedmar',
      public_role: 'agricultor',
      visibility: 'private',
    },
  });
  assert.equal(profile.statusCode, 200, profile.body);

  const first = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(first.statusCode, 200, first.body);
  assert.equal(first.json().balance, 20);
  assert.equal(first.json().missions.find((mission: { id: string }) => mission.id === 'profile').completed, true);
  assert.equal(first.json().rewards.find((reward: { id: string }) => reward.id === 'sprout-badge').unlocked, true);
  assert.equal(first.json().rewards.find((reward: { id: string }) => reward.id === 'master-olive-badge').unlocked, false);

  const second = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(second.statusCode, 200, second.body);
  assert.equal(second.json().balance, 20, 'reconciliation must be idempotent');

  const fieldId = randomUUID();
  await sql`
    INSERT INTO fields (id, workspace_id, client_operation_id, name)
    VALUES (${fieldId}::uuid, ${workspaceId}::uuid, ${randomUUID()}::uuid, 'Finca Mi Olivo CI')
  `.execute(db);

  for (let index = 0; index < 10; index += 1) {
    const daysAgo = 9 - index;
    await sql`
      INSERT INTO irrigation_records (
        id, workspace_id, field_id, occurred_at, client_operation_id, created_by, created_at
      ) VALUES (
        ${randomUUID()}::uuid,
        ${workspaceId}::uuid,
        ${fieldId}::uuid,
        now() - (${daysAgo} * interval '1 day'),
        ${randomUUID()}::uuid,
        ${userId}::uuid,
        now() - (${daysAgo} * interval '1 day')
      )
    `.execute(db);
  }

  const activity = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(activity.statusCode, 200, activity.body);
  assert.equal(activity.json().balance, 95);
  assert.equal(activity.json().achievements.find((achievement: { id: string }) => achievement.id === 'constancy').unlocked, true);
  const constancyMission = activity.json().missions.find((mission: { id: string }) => mission.id === 'constancy');
  assert.equal(constancyMission.completed, true);
  assert.equal(constancyMission.progress_current, 10);
  assert.equal(constancyMission.progress_target, 10);
  assert.ok(activity.json().rhythm.active_weeks >= 2, 'created_at spread across recent days should create a multi-week rhythm');
  assert.equal(activity.json().rhythm.grace_active, false);

  const paused = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/preferences',
    headers,
    payload: { enabled: false },
  });
  assert.equal(paused.statusCode, 200, paused.body);
  assert.equal(paused.json().enabled, false);

  await sql`
    INSERT INTO documents (
      id, workspace_id, client_operation_id, kind, title, status, created_by
    ) VALUES (
      ${randomUUID()}::uuid,
      ${workspaceId}::uuid,
      ${randomUUID()}::uuid,
      'other',
      'Documento Mi Olivo CI',
      'active',
      ${userId}::uuid
    )
  `.execute(db);

  await sql`
    INSERT INTO harvest_deliveries (
      id, workspace_id, delivery_at, total_kg, source, client_operation_id, created_by
    ) VALUES (
      ${randomUUID()}::uuid,
      ${workspaceId}::uuid,
      now(),
      1250,
      'manual',
      ${randomUUID()}::uuid,
      ${userId}::uuid
    )
  `.execute(db);

  const whilePaused = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(whilePaused.statusCode, 200, whilePaused.body);
  assert.equal(whilePaused.json().enabled, false);
  assert.equal(whilePaused.json().balance, 95, 'paused Mi Olivo must not award new events');

  const enabled = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/preferences',
    headers,
    payload: { enabled: true },
  });
  assert.equal(enabled.statusCode, 200, enabled.body);

  const final = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo', headers });
  assert.equal(final.statusCode, 200, final.body);
  assert.equal(final.json().balance, 150);
  assert.equal(final.json().level, 2);
  assert.equal(final.json().progress.current, 50);
  assert.equal(final.json().missions.every((mission: { completed: boolean }) => mission.completed), true);
  assert.equal(final.json().rewards.find((reward: { id: string }) => reward.id === 'new-branch-badge').unlocked, true);
  assert.equal(final.json().rewards.find((reward: { id: string }) => reward.id === 'young-olive-badge').unlocked, false);
  assert.equal(final.json().rewards.find((reward: { id: string }) => reward.id === 'master-olive-badge').required_level, 5);

  const ledger = await sql<{ total: number; distinct_keys: number }>`
    SELECT COUNT(*)::int AS total, COUNT(DISTINCT idempotency_key)::int AS distinct_keys
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
  `.execute(db);
  assert.equal(ledger.rows[0]?.total, 5);
  assert.equal(ledger.rows[0]?.distinct_keys, 5);

  console.log('MI_OLIVO_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
