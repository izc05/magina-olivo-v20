import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin operations smoke test.');

const db = createDatabase(databaseUrl);
let claims: GoogleIdentityClaims = {
  subject: 'operations-admin-subject',
  email: 'operations-admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Operaciones',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Operaciones',
  hostedDomain: 'magina.test',
};

const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-google-id-token-'.padEnd(140, 'x');

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
  const overview = await app.inject({ method: 'GET', url: '/api/v1/admin/operations', headers: { cookie: adminLogin.cookie } });
  assert.equal(overview.statusCode, 200, overview.body);
  const snapshot = overview.json();
  assert.equal(typeof snapshot.metrics.users_total, 'number');
  assert.equal(typeof snapshot.metrics.fields_active, 'number');
  assert.equal(typeof snapshot.metrics.work_records_total, 'number');
  assert.equal(typeof snapshot.metrics.expenses_eur, 'number');
  assert.equal(typeof snapshot.metrics.settlements_net_eur, 'number');
  assert.equal(typeof snapshot.metrics.invoiced_eur, 'number');
  assert.ok(Array.isArray(snapshot.datasets));
  assert.ok(snapshot.datasets.some((dataset: { id: string }) => dataset.id === 'fields'));
  assert.ok(snapshot.datasets.some((dataset: { id: string }) => dataset.id === 'work'));
  assert.ok(snapshot.datasets.some((dataset: { id: string }) => dataset.id === 'plans'));
  assert.ok(snapshot.datasets.some((dataset: { id: string }) => dataset.id === 'market'));
  assert.equal(snapshot.external_app.enabled, false);

  for (const dataset of snapshot.datasets as Array<{ id: string }>) {
    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/admin/data/${encodeURIComponent(dataset.id)}?limit=3`,
      headers: { cookie: adminLogin.cookie },
    });
    assert.equal(response.statusCode, 200, `${dataset.id}: ${response.body}`);
    assert.equal(response.json().dataset, dataset.id);
    assert.ok(Array.isArray(response.json().rows), `${dataset.id} rows must be an array`);
    assert.ok(response.json().rows.length <= 3, `${dataset.id} must honor limit`);
  }

  const invalidDataset = await app.inject({ method: 'GET', url: '/api/v1/admin/data/user_sessions', headers: { cookie: adminLogin.cookie } });
  assert.equal(invalidDataset.statusCode, 400, invalidDataset.body);

  const invalidGateway = await app.inject({
    method: 'PUT',
    url: '/api/v1/admin/external-app',
    headers: { cookie: adminLogin.cookie },
    payload: {
      enabled: true,
      name: 'Unsafe app',
      description: null,
      url: 'javascript:alert(1)',
      mode: 'new_tab',
      health_url: null,
    },
  });
  assert.equal(invalidGateway.statusCode, 400, invalidGateway.body);

  const saveGateway = await app.inject({
    method: 'PUT',
    url: '/api/v1/admin/external-app',
    headers: { cookie: adminLogin.cookie },
    payload: {
      enabled: true,
      name: 'Mágina App Externa',
      description: 'Aplicación complementaria de prueba',
      url: 'https://app.magina.test',
      mode: 'new_tab',
      health_url: 'https://app.magina.test/health',
    },
  });
  assert.equal(saveGateway.statusCode, 200, saveGateway.body);
  assert.equal(saveGateway.json().config.enabled, true);
  assert.equal(saveGateway.json().config.url, 'https://app.magina.test');

  const gateway = await app.inject({ method: 'GET', url: '/api/v1/admin/external-app', headers: { cookie: adminLogin.cookie } });
  assert.equal(gateway.statusCode, 200, gateway.body);
  assert.equal(gateway.json().config.name, 'Mágina App Externa');

  const publicSettings = await app.inject({ method: 'GET', url: '/api/v1/public/site-settings' });
  assert.equal(publicSettings.statusCode, 200, publicSettings.body);
  assert.equal(publicSettings.json().settings['platform.external_app'], undefined, 'External app configuration must remain private');

  claims = {
    ...claims,
    subject: 'operations-farmer-subject',
    email: 'farmer-operations@magina.test',
    displayName: 'Agricultor Operaciones',
  };
  const farmerLogin = await login();
  const denied = await app.inject({ method: 'GET', url: '/api/v1/admin/operations', headers: { cookie: farmerLogin.cookie } });
  assert.equal(denied.statusCode, 403, denied.body);

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  assert.ok(audit.json().entries.some((entry: { action: string; target_id: string | null }) => (
    entry.action === 'external_app.updated' && entry.target_id === 'platform.external_app'
  )));

  console.log('ADMIN_OPERATIONS_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
