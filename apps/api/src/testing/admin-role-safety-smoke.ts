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

  const grantAdmin = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/platform-access/${adminId}`,
    headers: { cookie: superAdminLogin.cookie },
    payload: { role: 'admin', status: 'active' },
  });
  assert.equal(grantAdmin.statusCode, 200, grantAdmin.body);

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

  console.log('ADMIN_ROLE_SAFETY_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
