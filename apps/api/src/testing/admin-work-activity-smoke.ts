import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin work activity smoke test.');

const db = createDatabase(databaseUrl);
const claims: GoogleIdentityClaims = {
  subject: 'work-activity-admin-subject',
  email: 'work-activity-admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Work Activity',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Work',
  hostedDomain: 'magina.test',
};
const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-work-activity-admin-token-'.padEnd(140, 'w');

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
  const adminUserId = adminLogin.body.user.id as string;
  const workspace = await db.insertInto('workspaces').values({ name: 'Workspace Trabajo Admin', type: 'professional' }).returningAll().executeTakeFirstOrThrow();
  const fieldId = randomUUID();
  await db.insertInto('fields').values({
    id: fieldId,
    workspace_id: workspace.id,
    client_operation_id: randomUUID(),
    name: 'Finca Trabajo Smoke',
    crop: 'olivar',
    status: 'active',
  }).execute();
  const campaign = await db.insertInto('campaigns').values({
    workspace_id: workspace.id,
    name: 'Campaña Trabajo 2026',
    start_date: '2026-09-01',
    end_date: '2026-09-30',
    status: 'active',
  }).returningAll().executeTakeFirstOrThrow();
  const futureCampaign = await db.insertInto('campaigns').values({
    workspace_id: workspace.id,
    name: 'Campaña Trabajo Octubre',
    start_date: '2026-10-01',
    end_date: '2026-10-31',
    status: 'planned',
  }).returningAll().executeTakeFirstOrThrow();

  const workId = randomUUID();
  await sql`
    INSERT INTO work_records (
      id, workspace_id, field_id, campaign_id, client_operation_id, type, occurred_on,
      title, notes, performed_for, created_by
    ) VALUES (
      ${workId}::uuid, ${workspace.id}::uuid, ${fieldId}::uuid, ${campaign.id}::uuid,
      ${randomUUID()}::uuid, 'pruning', '2026-09-10'::date,
      'Poda administrativa', 'Registro original', 'self', ${adminUserId}::uuid
    )
  `.execute(db);
  await sql`
    INSERT INTO work_participants (work_id, display_name, role, quantity, unit, rate_eur, cost_eur)
    VALUES (${workId}::uuid, 'Trabajador Smoke', 'peón', 1, 'jornales', 90, 90)
  `.execute(db);
  await sql`
    INSERT INTO work_resources (work_id, kind, name, quantity, unit, unit_cost_eur, cost_eur)
    VALUES (${workId}::uuid, 'machinery', 'Trituradora Smoke', 1, 'hours', 30, 30)
  `.execute(db);

  const list = await app.inject({
    method: 'GET',
    url: `/api/v1/admin/works?workspace_id=${workspace.id}&performed_for=self`,
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(list.statusCode, 200, list.body);
  const listed = list.json().works.find((item: { id: string }) => item.id === workId);
  assert.ok(listed, list.body);
  assert.equal(listed.field_id, fieldId);
  assert.equal(listed.campaign_id, campaign.id);
  assert.equal(listed.participants_count, 1);
  assert.equal(listed.resources_count, 1);
  assert.equal(listed.participant_cost_eur, 90);
  assert.equal(listed.resource_cost_eur, 30);
  assert.equal(typeof list.json().counts.total_30d, 'number');
  assert.equal(typeof list.json().counts.cost_30d, 'number');

  const detail = await app.inject({ method: 'GET', url: `/api/v1/admin/works/${workId}`, headers: { cookie: adminLogin.cookie } });
  assert.equal(detail.statusCode, 200, detail.body);
  assert.equal(detail.json().participants.length, 1);
  assert.equal(detail.json().resources.length, 1);
  assert.equal(detail.json().collections.length, 0);

  const invalidCampaign = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/works/${workId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { campaign_id: futureCampaign.id, occurred_on: '2026-09-11' },
  });
  assert.equal(invalidCampaign.statusCode, 409, invalidCampaign.body);
  assert.equal(invalidCampaign.json().error, 'work_date_outside_campaign');

  const update = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/works/${workId}`,
    headers: { cookie: adminLogin.cookie },
    payload: {
      title: 'Poda corregida por Administración',
      type: 'shredding',
      occurred_on: '2026-09-11',
      notes: 'Corrección administrativa auditada',
      campaign_id: campaign.id,
    },
  });
  assert.equal(update.statusCode, 200, update.body);
  assert.equal(update.json().work.title, 'Poda corregida por Administración');
  assert.equal(update.json().work.type, 'shredding');
  assert.equal(update.json().work.occurred_on, '2026-09-11');
  assert.equal(update.json().work.field_id, fieldId);
  assert.equal(update.json().work.workspace_id, workspace.id);

  const stored = await sql<{
    field_id: string;
    workspace_id: string;
    campaign_id: string | null;
    created_by: string;
    title: string;
    type: string;
  }>`
    SELECT field_id::text, workspace_id::text, campaign_id::text, created_by::text, title, type
    FROM work_records WHERE id=${workId}::uuid
  `.execute(db);
  assert.equal(stored.rows[0]?.field_id, fieldId);
  assert.equal(stored.rows[0]?.workspace_id, workspace.id);
  assert.equal(stored.rows[0]?.campaign_id, campaign.id);
  assert.equal(stored.rows[0]?.created_by, adminUserId);
  assert.equal(stored.rows[0]?.title, 'Poda corregida por Administración');
  assert.equal(stored.rows[0]?.type, 'shredding');

  const relationCounts = await sql<{ participants: number; resources: number }>`
    SELECT
      (SELECT count(*)::int FROM work_participants WHERE work_id=${workId}::uuid) AS participants,
      (SELECT count(*)::int FROM work_resources WHERE work_id=${workId}::uuid) AS resources
  `.execute(db);
  assert.equal(relationCounts.rows[0]?.participants, 1);
  assert.equal(relationCounts.rows[0]?.resources, 1);

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  const action = audit.json().entries.find((entry: { action: string; target_id: string | null }) => entry.action === 'work.updated' && entry.target_id === workId);
  assert.ok(action, audit.body);

  console.log('ADMIN_WORK_ACTIVITY_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
