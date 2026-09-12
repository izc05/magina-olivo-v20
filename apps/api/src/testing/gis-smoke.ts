import assert from 'node:assert/strict';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GisProviders } from '../gis/providers.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for GIS smoke test.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '55555555-5555-4555-8555-555555555555';
const foreignWorkspaceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const foreignFieldId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const cadastralReference = '23044A00100001';
const sigpacFeatureId = '233788127';

const catastroGeometry = {
  type: 'Polygon' as const,
  coordinates: [[
    [-3.5000, 37.7000],
    [-3.4990, 37.7000],
    [-3.4990, 37.7010],
    [-3.5000, 37.7010],
    [-3.5000, 37.7000],
  ]],
};

const sigpacGeometry = {
  type: 'Polygon' as const,
  coordinates: [[
    [-3.4998, 37.7002],
    [-3.4992, 37.7002],
    [-3.4992, 37.7008],
    [-3.4998, 37.7008],
    [-3.4998, 37.7002],
  ]],
};

const gisProviders: GisProviders = {
  catastro: {
    async parcelsByBbox() {
      return [{
        id: cadastralReference,
        nationalCadastralReference: cadastralReference,
        label: 'Parcela Catastro CI',
        areaM2: 12000,
        beginLifespanVersion: '2026-01-01T00:00:00Z',
        geometry: catastroGeometry,
      }];
    },
    async parcelByReference(reference) {
      assert.equal(reference, cadastralReference);
      return {
        id: cadastralReference,
        nationalCadastralReference: cadastralReference,
        label: 'Parcela Catastro CI',
        areaM2: 12000,
        beginLifespanVersion: '2026-01-01T00:00:00Z',
        geometry: catastroGeometry,
      };
    },
  },
  sigpac: {
    async recintosByBbox() {
      return [{
        id: sigpacFeatureId,
        provincia: 23,
        municipio: 44,
        agregado: 0,
        zona: 0,
        poligono: 12,
        parcela: 345,
        recinto: 2,
        pendienteMedia: 18.5,
        altitud: 740,
        surfaceM2: 8000,
        usoSigpac: 'OV',
        geometry: sigpacGeometry,
      }];
    },
    async recintoById(featureId) {
      assert.equal(featureId, sigpacFeatureId);
      return {
        id: sigpacFeatureId,
        provincia: 23,
        municipio: 44,
        agregado: 0,
        zona: 0,
        poligono: 12,
        parcela: 345,
        recinto: 2,
        pendienteMedia: 18.5,
        altitud: 740,
        surfaceM2: 8000,
        usoSigpac: 'OV',
        geometry: sigpacGeometry,
      };
    },
  },
};

const db = createDatabase(databaseUrl);
const app = buildApp({ db, gisProviders });
const developmentHeaders = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
};

try {
  await app.ready();

  const malformedBbox = await app.inject({
    method: 'GET',
    url: '/api/v1/gis/catastro/parcels?minLon=-3.5&minLat=37.7&maxLon=-3.6&maxLat=37.71',
    headers: developmentHeaders,
  });
  assert.equal(malformedBbox.statusCode, 400, malformedBbox.body);

  const catastroLookup = await app.inject({
    method: 'GET',
    url: '/api/v1/gis/catastro/parcels?minLon=-3.51&minLat=37.70&maxLon=-3.50&maxLat=37.71',
    headers: developmentHeaders,
  });
  assert.equal(catastroLookup.statusCode, 200, catastroLookup.body);
  assert.equal(catastroLookup.json().items[0].nationalCadastralReference, cadastralReference);

  const sigpacLookup = await app.inject({
    method: 'GET',
    url: '/api/v1/gis/sigpac/recintos?minLon=-3.51&minLat=37.70&maxLon=-3.50&maxLat=37.71',
    headers: developmentHeaders,
  });
  assert.equal(sigpacLookup.statusCode, 200, sigpacLookup.body);
  assert.equal(sigpacLookup.json().items[0].id, sigpacFeatureId);

  for (const attempt of [1, 2]) {
    const catastroLink = await app.inject({
      method: 'POST',
      url: `/api/v1/fields/${fieldId}/land-references/catastro`,
      headers: { ...developmentHeaders, 'content-type': 'application/json' },
      payload: { reference: cadastralReference, set_as_geometry: true },
    });
    assert.equal(catastroLink.statusCode, 201, `attempt ${attempt}: ${catastroLink.body}`);
  }

  const canonicalAfterCatastro = await sql<{
    geometry_source: string | null;
    geometry_status: string;
    calculated_area_ha: number | string | null;
    valid: boolean;
  }>`
    SELECT geometry_source, geometry_status, calculated_area_ha,
           ST_IsValid(geometry) AS valid
    FROM fields
    WHERE id = ${fieldId}::uuid
  `.execute(db);
  assert.equal(canonicalAfterCatastro.rows[0]?.geometry_source, 'catastro');
  assert.equal(canonicalAfterCatastro.rows[0]?.geometry_status, 'verified');
  assert.ok(Math.abs(Number(canonicalAfterCatastro.rows[0]?.calculated_area_ha) - 1.2) < 0.0001);
  assert.equal(canonicalAfterCatastro.rows[0]?.valid, true);

  const catastroCount = await sql<{ count: number | string }>`
    SELECT count(*) AS count FROM field_land_refs
    WHERE field_id = ${fieldId}::uuid AND source = 'catastro' AND reference = ${cadastralReference}
  `.execute(db);
  assert.equal(Number(catastroCount.rows[0]?.count), 1);

  const sigpacLink = await app.inject({
    method: 'POST',
    url: `/api/v1/fields/${fieldId}/land-references/sigpac`,
    headers: { ...developmentHeaders, 'content-type': 'application/json' },
    payload: { feature_id: sigpacFeatureId, set_as_geometry: false },
  });
  assert.equal(sigpacLink.statusCode, 201, sigpacLink.body);
  assert.equal(sigpacLink.json().canonical_geometry_updated, false);

  const canonicalAfterSigpac = await sql<{ geometry_source: string | null }>`
    SELECT geometry_source FROM fields WHERE id = ${fieldId}::uuid
  `.execute(db);
  assert.equal(canonicalAfterSigpac.rows[0]?.geometry_source, 'catastro');

  const references = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldId}/land-references`,
    headers: developmentHeaders,
  });
  assert.equal(references.statusCode, 200, references.body);
  const referenceItems = references.json().items;
  assert.equal(referenceItems.length, 2);
  assert.deepEqual(referenceItems.map((item: { source: string }) => item.source).sort(), ['catastro', 'sigpac']);
  assert.ok(referenceItems.every((item: { geometry: unknown }) => item.geometry));

  await db.insertInto('workspaces').values({
    id: foreignWorkspaceId,
    name: 'Workspace GIS ajeno',
    type: 'family',
  }).execute();
  await db.insertInto('fields').values({
    id: foreignFieldId,
    workspace_id: foreignWorkspaceId,
    client_operation_id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    name: 'Finca ajena',
    description: null,
    municipality: 'Bedmar',
    province: 'Jaén',
    calculated_area_ha: null,
    tree_count: 10,
    crop: 'olivar',
    variety: 'picual',
    water_regime: 'secano',
    planting_year: null,
    tenure_type: null,
    status: 'active',
  }).execute();

  const foreignFieldRead = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${foreignFieldId}/land-references`,
    headers: developmentHeaders,
  });
  assert.equal(foreignFieldRead.statusCode, 404, foreignFieldRead.body);

  const foreignFieldLink = await app.inject({
    method: 'POST',
    url: `/api/v1/fields/${foreignFieldId}/land-references/catastro`,
    headers: { ...developmentHeaders, 'content-type': 'application/json' },
    payload: { reference: cadastralReference, set_as_geometry: true },
  });
  assert.equal(foreignFieldLink.statusCode, 404, foreignFieldLink.body);

  console.log('GIS_POSTGIS_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
