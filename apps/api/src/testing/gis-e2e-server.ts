import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GisProviders } from '../gis/providers.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for GIS E2E server.');

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
        label: 'Parcela Catastro E2E',
        areaM2: 12_000,
        beginLifespanVersion: '2026-01-01T00:00:00Z',
        geometry: catastroGeometry,
      }];
    },
    async parcelByReference(reference) {
      if (reference === failingCadastralReference) throw new Error('fixture_catastro_failure');
      if (reference !== cadastralReference) throw new Error('fixture_catastro_not_found');
      return {
        id: cadastralReference,
        nationalCadastralReference: cadastralReference,
        label: 'Parcela Catastro E2E',
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
      if (featureId !== sigpacFeatureId) throw new Error('fixture_sigpac_not_found');
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
const port = Number(process.env.PORT ?? 3001);
const host = process.env.HOST ?? '127.0.0.1';

async function shutdown() {
  await app.close();
  await db.destroy();
}

process.on('SIGINT', () => void shutdown().finally(() => process.exit(0)));
process.on('SIGTERM', () => void shutdown().finally(() => process.exit(0)));

try {
  await app.listen({ port, host });
} catch (error) {
  app.log.error(error);
  await shutdown();
  process.exit(1);
}
