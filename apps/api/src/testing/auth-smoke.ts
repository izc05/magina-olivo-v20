import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for auth smoke test.');

const db = createDatabase(databaseUrl);

const googleVerifier: GoogleIdentityVerifier = {
  async verify() {
    return {
      subject: 'google-sub-ci-0001',
      email: 'ci.auth@example.test',
      emailVerified: true,
      displayName: 'Agricultor CI',
      pictureUrl: 'https://example.test/avatar.png',
      givenName: 'Agricultor',
      familyName: 'CI',
      hostedDomain: null,
    };
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

  const secondLogin = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/google',
    payload: { credential },
  });
  assert.equal(secondLogin.statusCode, 200, secondLogin.body);
  assert.equal(secondLogin.json().created, false);
  assert.equal(Number((await db.selectFrom('users').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);
  assert.equal(Number((await db.selectFrom('auth_identities').select(({ fn }) => fn.countAll<number>().as('count')).executeTakeFirstOrThrow()).count), 1);

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
