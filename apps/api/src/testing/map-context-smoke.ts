import assert from 'node:assert/strict';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for map context smoke test.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '55555555-5555-4555-8555-555555555555';
const cadastralReference = '23044A00100001';
const sigpacReference = '233788127';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

try {
  await sql`
    UPDATE fields
    SET
      name = 'Finca GIS CI',
      geometry = ST_Multi(ST_GeomFromText('POLYGON((-3.5000 37.7000,-3.4990 37.7000,-3.4990 37.7010,-3.5000 37.7010,-3.5000 37.7000))', 4326)),
      calculated_area_ha = 1.2,
      geometry_source = 'catastro',
      geometry_status = 'verified',
      geometry_checked_at = now()
    WHERE id = ${fieldId}::uuid AND workspace_id = ${workspaceId}::uuid
  `.execute(db);

  await sql`
    INSERT INTO field_land_refs (field_id, source, reference, geometry, area_ha, status, metadata_json, checked_at)
    VALUES
      (
        ${fieldId}::uuid,
        'catastro',
        ${cadastralReference},
        ST_Multi(ST_GeomFromText('POLYGON((-3.5000 37.7000,-3.4990 37.7000,-3.4990 37.7010,-3.5000 37.7010,-3.5000 37.7000))', 4326)),
        1.2,
        'verified',
        '{}'::jsonb,
        now()
      ),
      (
        ${fieldId}::uuid,
        'sigpac',
        ${sigpacReference},
        ST_Multi(ST_GeomFromText('POLYGON((-3.4998 37.7002,-3.4992 37.7002,-3.4992 37.7008,-3.4998 37.7008,-3.4998 37.7002))', 4326)),
        0.8,
        'verified',
        '{}'::jsonb,
        now()
      )
    ON CONFLICT (field_id, source, reference) WHERE reference IS NOT NULL DO NOTHING
  `.execute(db);

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
