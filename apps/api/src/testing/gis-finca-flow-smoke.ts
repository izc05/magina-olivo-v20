import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GisProviders } from '../gis/providers.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for finca GIS flow smoke.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '77777777-7777-4777-8777-777777777777';
const unlocatedFieldId = '88888888-8888-4888-8888-888888888888';
const cadastralReference = '23044A00100001';
const failingCadastralReference = '23044A00100099';
const sigpacFeatureId = '233788127';

const catastroGeometry = {
  type: 'Polygon' as const,
  coordinates: [[
    [-3.5000, 37.7000], [-3.4990, 37.7000], [-3.4990, 37.7010],
    [-3.5000, 37.7010], [-3.5000, 37.7000],
  ]],
};
const sigpacGeometry = {
  type: 'Polygon' as const,
  coordinates: [[
    [-3.4998, 37.7002], [-3.4992, 37.7002], [-3.4992, 37.7008],
    [-3.4998, 37.7008], [-3.4998, 37.7002],
  ]],
};

const providers: GisProviders = {
  catastro: {
    async parcelsByBbox() {
      return [{
        id: cadastralReference,
        nationalCadastralReference: cadastralReference,
        label: 'Parcela Catastro flujo',
        areaM2: 12_000,
        beginLifespanVersion: '2026-01-01T00:00:00Z',
        geometry: catastroGeometry,
      }];
    },
    async parcelByReference(reference) {
      if (reference === failingCadastralReference) throw new Error('fixture_catastro_failure');
      if (reference !== cadastralReference) throw new Error(`unexpected_catastro_reference:${reference}`);
      return {
        id: cadastralReference,
        nationalCadastralReference: cadastralReference,
        label: 'Parcela Catastro flujo',
        areaM2: 12_000,
        beginLifespanVersion: '2026-01-01T00:00:00Z',
        geometry: catastroGeometry,
      };
    },
  },
  sigpac: {
    async recintosByBbox() {
      return [{
        id: sigpacFeatureId,
        provincia: 23, municipio: 44, agregado: 0, zona: 0, poligono: 12,
        parcela: 345, recinto: 2, pendienteMedia: 18.5, altitud: 740,
        surfaceM2: 8_000, usoSigpac: 'OV', geometry: sigpacGeometry,
      }];
    },
    async recintoById(featureId) {
      if (featureId !== sigpacFeatureId) throw new Error(`unexpected_sigpac_id:${featureId}`);
      return {
        id: sigpacFeatureId,
        provincia: 23, municipio: 44, agregado: 0, zona: 0, poligono: 12,
        parcela: 345, recinto: 2, pendienteMedia: 18.5, altitud: 740,
        surfaceM2: 8_000, usoSigpac: 'OV', geometry: sigpacGeometry,
      };
    },
  },
};

const db = createDatabase(databaseUrl);
const app = buildApp({ db, gisProviders: providers });
const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
  'content-type': 'application/json',
};

try {
  await app.ready();

  const created = await app.inject({
    method: 'POST', url: '/api/v1/fields', headers,
    payload: {
      client_operation_id: '99999999-9999-4999-8999-999999999999',
      entity_id: fieldId,
      name: 'Finca selector GIS', municipality: 'Bedmar', province: 'Jaén',
      tree_count: 120, variety: 'Picual', water_regime: 'secano',
    },
  });
  assert.equal(created.statusCode, 201, created.body);
  assert.equal(created.json().field.geometry_status, 'unlocated');
  assert.equal(created.json().field.geometry_source, null);

  const initialRead = await app.inject({ method: 'GET', url: `/api/v1/fields/${fieldId}`, headers });
  assert.equal(initialRead.statusCode, 200, initialRead.body);
  assert.equal(initialRead.json().field.geometry_status, 'unlocated');

  const initialMap = await app.inject({ method: 'GET', url: `/api/v1/fields/${fieldId}/map-context`, headers });
  assert.equal(initialMap.statusCode, 200, initialMap.body);
  assert.equal(initialMap.json().field.geometry, null);
  assert.equal(initialMap.json().references.length, 0);

  const catastroCandidate = await app.inject({
    method: 'GET', url: `/api/v1/gis/catastro/parcels/${cadastralReference}`, headers,
  });
  assert.equal(catastroCandidate.statusCode, 200, catastroCandidate.body);
  assert.equal(catastroCandidate.json().item.nationalCadastralReference, cadastralReference);

  const linkedCatastro = await app.inject({
    method: 'POST', url: `/api/v1/fields/${fieldId}/land-references/catastro`, headers,
    payload: { reference: cadastralReference, set_as_geometry: true },
  });
  assert.equal(linkedCatastro.statusCode, 201, linkedCatastro.body);
  assert.equal(linkedCatastro.json().canonical_geometry_updated, true);

  const recoveredAfterCatastro = await app.inject({ method: 'GET', url: `/api/v1/fields/${fieldId}/map-context`, headers });
  assert.equal(recoveredAfterCatastro.statusCode, 200, recoveredAfterCatastro.body);
  assert.equal(recoveredAfterCatastro.json().field.geometry_source, 'catastro');
  assert.equal(recoveredAfterCatastro.json().field.geometry_status, 'verified');
  assert.equal(recoveredAfterCatastro.json().field.geometry.type, 'MultiPolygon');
  assert.equal(recoveredAfterCatastro.json().references.length, 1);
  assert.equal(recoveredAfterCatastro.json().references[0].reference, cadastralReference);

  const updated = await app.inject({
    method: 'PATCH', url: `/api/v1/fields/${fieldId}`, headers,
    payload: { name: 'Finca selector GIS editada', tree_count: 125, water_regime: 'regadio' },
  });
  assert.equal(updated.statusCode, 200, updated.body);
  assert.equal(updated.json().field.name, 'Finca selector GIS editada');
  assert.equal(updated.json().field.tree_count, 125);

  const linkedSigpac = await app.inject({
    method: 'POST', url: `/api/v1/fields/${fieldId}/land-references/sigpac`, headers,
    payload: { feature_id: sigpacFeatureId, set_as_geometry: true },
  });
  assert.equal(linkedSigpac.statusCode, 201, linkedSigpac.body);

  const recoveredAfterSigpac = await app.inject({ method: 'GET', url: `/api/v1/fields/${fieldId}/map-context`, headers });
  assert.equal(recoveredAfterSigpac.statusCode, 200, recoveredAfterSigpac.body);
  assert.equal(recoveredAfterSigpac.json().field.geometry_source, 'sigpac');
  assert.equal(recoveredAfterSigpac.json().field.geometry_status, 'verified');
  assert.equal(recoveredAfterSigpac.json().references.length, 2);

  const failingLink = await app.inject({
    method: 'POST', url: `/api/v1/fields/${fieldId}/land-references/catastro`, headers,
    payload: { reference: failingCadastralReference, set_as_geometry: true },
  });
  assert.equal(failingLink.statusCode, 502, failingLink.body);

  const afterFailure = await app.inject({ method: 'GET', url: `/api/v1/fields/${fieldId}/map-context`, headers });
  assert.equal(afterFailure.statusCode, 200, afterFailure.body);
  assert.equal(afterFailure.json().field.geometry_source, 'sigpac');
  assert.equal(afterFailure.json().references.length, 2);

  const unlocatedCreated = await app.inject({
    method: 'POST', url: '/api/v1/fields', headers,
    payload: {
      client_operation_id: 'aaaaaaaa-9999-4999-8999-aaaaaaaaaaaa',
      entity_id: unlocatedFieldId,
      name: 'Finca sin límites', municipality: 'Jódar', province: 'Jaén', tree_count: 40,
    },
  });
  assert.equal(unlocatedCreated.statusCode, 201, unlocatedCreated.body);

  const unlocatedContext = await app.inject({ method: 'GET', url: `/api/v1/fields/${unlocatedFieldId}/map-context`, headers });
  assert.equal(unlocatedContext.statusCode, 200, unlocatedContext.body);
  assert.equal(unlocatedContext.json().field.geometry_status, 'unlocated');
  assert.equal(unlocatedContext.json().field.geometry, null);
  assert.equal(unlocatedContext.json().references.length, 0);

  console.log('GIS_FINCA_FLOW_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
