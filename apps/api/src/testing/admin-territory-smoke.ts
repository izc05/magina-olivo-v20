import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin territory smoke test.');

const db = createDatabase(databaseUrl);
let claims: GoogleIdentityClaims = {
  subject: 'admin-google-subject',
  email: 'admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Mágina',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Mágina',
  hostedDomain: 'magina.test',
};

const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-google-id-token-territory-'.padEnd(140, 'x');

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
  const catalog = await app.inject({ method: 'GET', url: '/api/v1/admin/territory/catalog', headers: { cookie: adminLogin.cookie } });
  assert.equal(catalog.statusCode, 200, catalog.body);
  assert.ok(catalog.json().municipalities.some((municipality: { name: string }) => municipality.name === 'Huelma'));
  const huelma = catalog.json().places.find((place: { name: string }) => place.name === 'Huelma');
  assert.ok(huelma, 'Huelma canonical place must exist');
  assert.equal(typeof huelma.field_count, 'number');
  assert.equal(typeof huelma.editorial_count, 'number');

  claims = {
    ...claims,
    subject: 'territory-farmer-google-subject',
    email: 'territory-farmer@magina.test',
    displayName: 'Propietario sin permisos de territorio',
  };
  const farmerLogin = await login();
  const farmerId = String(farmerLogin.body.user.id);
  assert.equal(farmerLogin.body.workspaces[0].role, 'owner');

  const farmerDenied = await app.inject({ method: 'GET', url: '/api/v1/admin/territory/catalog', headers: { cookie: farmerLogin.cookie } });
  assert.equal(farmerDenied.statusCode, 403, farmerDenied.body);
  assert.equal(farmerDenied.json().error, 'platform_admin_required');

  const hideHuelma = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/territory/places/${huelma.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { public_enabled: false },
  });
  assert.equal(hideHuelma.statusCode, 200, hideHuelma.body);
  assert.equal(hideHuelma.json().place.public_enabled, false);

  const publicWhileHidden = await app.inject({ method: 'GET', url: '/api/v1/public/territory/places' });
  assert.equal(publicWhileHidden.statusCode, 200, publicWhileHidden.body);
  assert.ok(!publicWhileHidden.json().places.some((place: { id: string }) => place.id === huelma.id), 'Hidden canonical place must not be public');

  const restoreHuelma = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/territory/places/${huelma.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { public_enabled: true },
  });
  assert.equal(restoreHuelma.statusCode, 200, restoreHuelma.body);

  const publicRestored = await app.inject({ method: 'GET', url: '/api/v1/public/territory/places' });
  assert.equal(publicRestored.statusCode, 200, publicRestored.body);
  assert.ok(publicRestored.json().places.some((place: { id: string }) => place.id === huelma.id), 'Restored canonical place must return to public API');

  const grantEditor = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/platform-access/${farmerId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { role: 'editor', status: 'active' },
  });
  assert.equal(grantEditor.statusCode, 200, grantEditor.body);

  const editorChange = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/territory/places/${huelma.id}`,
    headers: { cookie: farmerLogin.cookie },
    payload: { kind: huelma.kind },
  });
  assert.equal(editorChange.statusCode, 200, editorChange.body);

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  assert.ok(audit.json().entries.some((entry: { action: string; target_id: string | null }) => entry.action === 'territory.place_changed' && entry.target_id === huelma.id));

  console.log('ADMIN_TERRITORY_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
