import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for auth smoke test.');

const db = createDatabase(databaseUrl);

let googleClaims: GoogleIdentityClaims = {
  subject: 'google-sub-ci-0001',
  email: 'ci.auth@example.test',
  emailVerified: true,
  displayName: 'Agricultor CI',
  pictureUrl: 'https://example.test/avatar.png',
  givenName: 'Agricultor',
  familyName: 'CI',
  hostedDomain: null,
};

const googleVerifier: GoogleIdentityVerifier = {
  async verify() {
    return googleClaims;
  },
};

const app = buildApp({ db, googleVerifier });

try {
  await app.ready();

  const credential = 'synthetic-google-id-token-'.padEnd(140, 'x');
  const firstLogin = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/google',
    payload: { credential },
  });
  assert.equal(firstLogin.statusCode, 201, firstLogin.body);

  const firstBody = firstLogin.json();
  assert.equal(firstBody.created, true);
  assert.equal(firstBody.workspaces.length, 1);
  assert.equal(firstBody.workspaces[0].role, 'owner');
  const workspaceId = String(firstBody.workspaces[0].workspace_id);

  const setCookie = firstLogin.headers['set-cookie'];
  assert.equal(typeof setCookie, 'string');
  const cookie = String(setCookie).split(';', 1)[0];
  assert.match(cookie, /^magina_session=/);

  assert.equal(Number((await db.selectFrom('users').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);
  assert.equal(Number((await db.selectFrom('auth_identities').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);
  assert.equal(Number((await db.selectFrom('workspace_memberships').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);

  const sessionResponse = await app.inject({
    method: 'GET',
    url: '/api/v1/auth/session',
    headers: { cookie },
  });
  assert.equal(sessionResponse.statusCode, 200, sessionResponse.body);
  assert.equal(sessionResponse.json().user.display_name, 'Agricultor CI');

  const initialMe = await app.inject({ method: 'GET', url: '/api/v1/me', headers: { cookie } });
  assert.equal(initialMe.statusCode, 200, initialMe.body);
  assert.equal(initialMe.json().profile.visibility, 'private');
  assert.equal(initialMe.json().preferences.theme, 'system');

  const profileUpdate = await app.inject({
    method: 'PATCH',
    url: '/api/v1/me/profile',
    headers: { cookie },
    payload: {
      display_name: 'Isi Mágina',
      municipality: 'Huelma',
      bio: 'Olivar y territorio.',
      public_role: 'agricultor',
      visibility: 'public',
    },
  });
  assert.equal(profileUpdate.statusCode, 200, profileUpdate.body);
  assert.equal(profileUpdate.json().user.display_name, 'Isi Mágina');
  assert.equal(profileUpdate.json().profile.municipality, 'Huelma');
  assert.equal(profileUpdate.json().profile.public_role, 'agricultor');

  const preferenceUpdate = await app.inject({
    method: 'PATCH',
    url: '/api/v1/me/preferences',
    headers: { cookie },
    payload: {
      theme: 'dark',
      preferred_municipality: 'Huelma',
      community_notifications: false,
      weather_alerts: true,
    },
  });
  assert.equal(preferenceUpdate.statusCode, 200, preferenceUpdate.body);
  assert.equal(preferenceUpdate.json().preferences.theme, 'dark');
  assert.equal(preferenceUpdate.json().preferences.preferred_municipality, 'Huelma');
  assert.equal(preferenceUpdate.json().preferences.community_notifications, false);

  const allowedWorkspace = await app.inject({
    method: 'GET',
    url: '/api/v1/fields',
    headers: { cookie, 'x-workspace-id': workspaceId },
  });
  assert.equal(allowedWorkspace.statusCode, 200, allowedWorkspace.body);

  const foreignWorkspace = await db.insertInto('workspaces').values({
    name: 'Workspace ajeno',
    type: 'family',
    updated_at: new Date(),
  }).returning('id').executeTakeFirstOrThrow();

  const deniedWorkspace = await app.inject({
    method: 'GET',
    url: '/api/v1/fields',
    headers: { cookie, 'x-workspace-id': foreignWorkspace.id },
  });
  assert.equal(deniedWorkspace.statusCode, 401, deniedWorkspace.body);

  googleClaims = { ...googleClaims, subject: 'different-google-sub-same-email' };
  const conflictingLogin = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/google',
    payload: { credential },
  });
  assert.equal(conflictingLogin.statusCode, 409, conflictingLogin.body);
  assert.equal(conflictingLogin.json().error, 'account_link_required');
  assert.equal(Number((await db.selectFrom('users').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);

  googleClaims = { ...googleClaims, subject: 'google-sub-ci-0001', displayName: 'Nombre Google Actualizado' };
  const secondLogin = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/google',
    payload: { credential },
  });
  assert.equal(secondLogin.statusCode, 200, secondLogin.body);
  assert.equal(secondLogin.json().created, false);
  assert.equal(Number((await db.selectFrom('users').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);
  assert.equal(Number((await db.selectFrom('auth_identities').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);

  const meAfterRelogin = await app.inject({
    method: 'GET',
    url: '/api/v1/me',
    headers: { cookie },
  });
  assert.equal(meAfterRelogin.statusCode, 200, meAfterRelogin.body);
  assert.equal(meAfterRelogin.json().user.display_name, 'Isi Mágina');
  assert.equal(meAfterRelogin.json().user.provider_display_name, 'Nombre Google Actualizado');

  const logout = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/logout',
    headers: { cookie },
  });
  assert.equal(logout.statusCode, 204, logout.body);

  const afterLogout = await app.inject({
    method: 'GET',
    url: '/api/v1/auth/session',
    headers: { cookie },
  });
  assert.equal(afterLogout.statusCode, 401, afterLogout.body);

  console.log('AUTH_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
