import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin role safety smoke test.');

const db = createDatabase(databaseUrl);
let claims: GoogleIdentityClaims = {
  subject: 'admin-google-subject',
  email: 'admin@magina.test',
  emailVerified: true,
  displayName: 'Super Admin Mágina',
  pictureUrl: null,
  givenName: 'Super',
  familyName: 'Admin',
  hostedDomain: 'magina.test',
};

const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-google-id-token-role-safety-'.padEnd(140, 'x');

async function login() {
  const response = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.ok(response.statusCode === 200 || response.statusCode === 201, response.body);
  const cookieHeader = response.headers['set-cookie'];
  assert.equal(typeof cookieHeader, 'string');
  return { body: response.json(), cookie: String(cookieHeader).split(';', 1)[0] };
}

async function grantRole(superAdminCookie: string, userId: string, role: 'admin' | 'editor' | 'support') {
  const response = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/platform-access/${userId}`,
    headers: { cookie: superAdminCookie },
    payload: { role, status: 'active' },
  });
  assert.equal(response.statusCode, 200, response.body);
}

try {
  await app.ready();

  const superAdminLogin = await login();
  const superAdminId = String(superAdminLogin.body.user.id);
  const superAdminSession = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/session',
    headers: { cookie: superAdminLogin.cookie },
  });
  assert.equal(superAdminSession.statusCode, 200, superAdminSession.body);
  assert.equal(superAdminSession.json().platform_access.role, 'super_admin');
  assert.equal(superAdminSession.json().platform_access.source, 'bootstrap');

  claims = {
    ...claims,
    subject: 'role-safety-admin',
    email: 'role-safety-admin@magina.test',
    displayName: 'Admin operativo',
  };
  const adminLogin = await login();
  const adminId = String(adminLogin.body.user.id);
  await grantRole(superAdminLogin.cookie, adminId, 'admin');

  const adminSession = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/session',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(adminSession.statusCode, 200, adminSession.body);
  assert.equal(adminSession.json().platform_access.role, 'admin');

  const blocked = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/users/${superAdminId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { status: 'suspended' },
  });
  assert.equal(blocked.statusCode, 403, blocked.body);
  assert.equal(blocked.json().error, 'platform_admin_role_required');
  assert.equal(blocked.json().minimum_role, 'super_admin');

  const protectedUser = await db.selectFrom('users')
    .select(['status'])
    .where('id', '=', superAdminId)
    .executeTakeFirstOrThrow();
  assert.equal(protectedUser.status, 'active');

  const allowed = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/users/${adminId}`,
    headers: { cookie: superAdminLogin.cookie },
    payload: { status: 'suspended' },
  });
  assert.equal(allowed.statusCode, 200, allowed.body);
  assert.equal(allowed.json().status, 'suspended');

  claims = {
    ...claims,
    subject: 'role-safety-editor',
    email: 'role-safety-editor@magina.test',
    displayName: 'Editor de plataforma',
  };
  const editorLogin = await login();
  const editorId = String(editorLogin.body.user.id);
  await grantRole(superAdminLogin.cookie, editorId, 'editor');

  const editorSession = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/session',
    headers: { cookie: editorLogin.cookie },
  });
  assert.equal(editorSession.statusCode, 200, editorSession.body);
  assert.equal(editorSession.json().platform_access.role, 'editor');

  const editorBlockedFromAdminConfig = await app.inject({
    method: 'PUT',
    url: '/api/v1/admin/external-app',
    headers: { cookie: editorLogin.cookie },
    payload: {},
  });
  assert.equal(editorBlockedFromAdminConfig.statusCode, 403, editorBlockedFromAdminConfig.body);
  assert.equal(editorBlockedFromAdminConfig.json().error, 'platform_admin_role_required');
  assert.equal(editorBlockedFromAdminConfig.json().minimum_role, 'admin');

  const editorCanReadContent = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/content',
    headers: { cookie: editorLogin.cookie },
  });
  assert.equal(editorCanReadContent.statusCode, 200, editorCanReadContent.body);

  claims = {
    ...claims,
    subject: 'role-safety-support',
    email: 'role-safety-support@magina.test',
    displayName: 'Soporte de plataforma',
  };
  const supportLogin = await login();
  const supportId = String(supportLogin.body.user.id);
  await grantRole(superAdminLogin.cookie, supportId, 'support');

  const supportSession = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/session',
    headers: { cookie: supportLogin.cookie },
  });
  assert.equal(supportSession.statusCode, 200, supportSession.body);
  assert.equal(supportSession.json().platform_access.role, 'support');

  const supportCanReadOverview = await app.inject({
    method: 'GET',
    url: '/api/v1/admin/overview',
    headers: { cookie: supportLogin.cookie },
  });
  assert.equal(supportCanReadOverview.statusCode, 200, supportCanReadOverview.body);

  const supportBlockedFromEditing = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/content',
    headers: { cookie: supportLogin.cookie },
    payload: {},
  });
  assert.equal(supportBlockedFromEditing.statusCode, 403, supportBlockedFromEditing.body);
  assert.equal(supportBlockedFromEditing.json().error, 'platform_admin_role_required');
  assert.equal(supportBlockedFromEditing.json().minimum_role, 'editor');

  const supportBlockedFromUserManagement = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/users/${supportId}`,
    headers: { cookie: supportLogin.cookie },
    payload: { status: 'suspended' },
  });
  assert.equal(supportBlockedFromUserManagement.statusCode, 403, supportBlockedFromUserManagement.body);
  assert.equal(supportBlockedFromUserManagement.json().error, 'platform_admin_role_required');
  assert.equal(supportBlockedFromUserManagement.json().minimum_role, 'admin');

  console.log('ADMIN_ROLE_SAFETY_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
