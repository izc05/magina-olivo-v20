import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for GIS location unlink smoke test.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const db = createDatabase(databaseUrl);
const app = buildApp({ db });
const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
  'content-type': 'application/json',
};

async function createField(name: string, municipality: string, province: string) {
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/fields',
    headers,
    payload: {
      client_operation_id: randomUUID(),
      entity_id: randomUUID(),
      name,
      municipality,
      province,
    },
  });
  assert.equal(response.statusCode, 201, response.body);
  return response.json().field as { id: string; municipality: string | null; province: string | null };
}

try {
  await app.ready();

  const derived = await createField('Finca ubicación derivada', 'Bedmar', 'Jaén');
  assert.equal(derived.municipality, 'Bedmar');
  assert.equal(derived.province, 'Jaén');

  const cleared = await app.inject({
    method: 'PATCH',
    url: `/api/v1/fields/${derived.id}`,
    headers,
    payload: { place_id: null },
  });
  assert.equal(cleared.statusCode, 200, cleared.body);
  assert.equal(cleared.json().field.place_id, null);
  assert.equal(cleared.json().field.municipality_id, null);
  assert.equal(cleared.json().field.municipality, null);
  assert.equal(cleared.json().field.province, null);

  const manual = await createField('Finca ubicación manual', 'Bedmar', 'Jaén');
  const replaced = await app.inject({
    method: 'PATCH',
    url: `/api/v1/fields/${manual.id}`,
    headers,
    payload: { place_id: null, municipality: 'Jódar', province: 'Jaén' },
  });
  assert.equal(replaced.statusCode, 200, replaced.body);
  assert.equal(replaced.json().field.place_id, null);
  assert.equal(replaced.json().field.municipality_id, null);
  assert.equal(replaced.json().field.municipality, 'Jódar');
  assert.equal(replaced.json().field.province, 'Jaén');

  console.log('GIS_LOCATION_UNLINK_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
