import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for map context smoke test.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '55555555-5555-4555-8555-555555555555';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

try {
  await app.ready();
  const response = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldId}/map-context`,
    headers: {
      'x-workspace-id': workspaceId,
      'x-user-id': userId,
    },
  });

  assert.equal(response.statusCode, 200, response.body);
  const body = response.json();
  assert.equal(body.field.id, fieldId);
  assert.equal(body.field.name, 'Finca GIS CI');
  assert.equal(body.field.geometry_source, 'catastro');
  assert.equal(body.field.geometry_status, 'verified');
  assert.ok(Math.abs(Number(body.field.calculated_area_ha) - 1.2) < 0.0001);
  assert.equal(body.field.geometry.type, 'MultiPolygon');
  assert.equal(body.field.centroid.type, 'Point');
  assert.equal(body.field.representative_point.type, 'Point');
  assert.ok(body.field.centroid.coordinates.every((value: unknown) => typeof value === 'number' && Number.isFinite(value)));
  assert.ok(body.field.representative_point.coordinates.every((value: unknown) => typeof value === 'number' && Number.isFinite(value)));
  assert.equal(body.references.length, 2);
  assert.deepEqual(body.references.map((item: { source: string }) => item.source).sort(), ['catastro', 'sigpac']);

  console.log('MAP_CONTEXT_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
