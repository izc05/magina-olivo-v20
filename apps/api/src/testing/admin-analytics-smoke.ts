import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin analytics smoke test.');

const db = createDatabase(databaseUrl);
let claims: GoogleIdentityClaims = {
  subject: 'analytics-admin-google-subject',
  email: 'analytics-admin@magina.test',
  emailVerified: true,
  displayName: 'Analytics Admin',
  pictureUrl: null,
  givenName: 'Analytics',
  familyName: 'Admin',
  hostedDomain: 'magina.test',
};
const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-google-id-token-analytics-'.padEnd(140, 'x');

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

  const thirty = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/timeseries?days=30',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(thirty.statusCode, 200, thirty.body);
  const thirtyPayload = thirty.json();
  assert.equal(thirtyPayload.days, 30);
  assert.equal(thirtyPayload.points.length, 30);
  assert.equal(typeof thirtyPayload.summary.users_new, 'number');
  assert.equal(typeof thirtyPayload.summary.workspaces_new, 'number');
  assert.equal(typeof thirtyPayload.summary.works, 'number');
  assert.equal(typeof thirtyPayload.summary.harvest_kg, 'number');
  assert.equal(typeof thirtyPayload.summary.expenses_eur, 'number');
  assert.equal(typeof thirtyPayload.summary.invoiced_eur, 'number');
  assert.equal(typeof thirtyPayload.summary.admin_actions, 'number');
  assert.ok(thirtyPayload.points[0].day <= thirtyPayload.points.at(-1).day);

  const ninety = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/timeseries?days=90',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(ninety.statusCode, 200, ninety.body);
  assert.equal(ninety.json().points.length, 90);

  const invalid = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/timeseries?days=31',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(invalid.statusCode, 400, invalid.body);
  assert.equal(invalid.json().error, 'validation_error');

  const csv = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/export.csv?days=30',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(csv.statusCode, 200, csv.body);
  assert.match(String(csv.headers['content-type']), /text\/csv/);
  assert.match(String(csv.headers['content-disposition']), /magina-admin-analytics-30d\.csv/);
  assert.ok(csv.body.includes('usuarios_nuevos'));
  assert.ok(csv.body.includes('facturado_eur'));
  assert.equal(csv.body.trim().split('\n').length, 31);

  const audit = await db.selectFrom('admin_audit_log')
    .select(['action', 'target_type', 'target_id', 'metadata'])
    .where('action', '=', 'analytics.exported')
    .orderBy('created_at', 'desc')
    .executeTakeFirst();
  assert.equal(audit?.target_type, 'analytics');
  assert.equal(audit?.target_id, 'operations_timeseries');
  assert.equal((audit?.metadata as { days?: number } | null)?.days, 30);

  claims = {
    ...claims,
    subject: 'analytics-editor-google-subject',
    email: 'analytics-editor@magina.test',
    displayName: 'Analytics Editor',
  };
  const editorLogin = await login();
  const editorUserId = String(editorLogin.body.user.id);
  await db.insertInto('platform_admins').values({
    user_id: editorUserId,
    role: 'editor',
    status: 'active',
    granted_by: null,
  }).onConflict((conflict) => conflict.column('user_id').doUpdateSet({ role: 'editor', status: 'active', updated_at: new Date() })).execute();

  const editorRead = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/timeseries?days=30',
    headers: { cookie: editorLogin.cookie },
  });
  assert.equal(editorRead.statusCode, 200, editorRead.body);

  const editorExport = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/analytics/export.csv?days=30',
    headers: { cookie: editorLogin.cookie },
  });
  assert.equal(editorExport.statusCode, 403, editorExport.body);
  assert.equal(editorExport.json().error, 'platform_admin_role_required');
  assert.equal(editorExport.json().minimum_role, 'admin');

  console.log('ADMIN_ANALYTICS_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
