import assert from 'node:assert/strict';
import type { RadarIngestJobPayload } from '@magina/contracts';
import type { RadarBinaryAsset } from '@magina/weather';
import pg from 'pg';
import type { RadarObjectStoragePort, RadarSourcePort } from '../radar/ports.js';
import { runRadarIngestJob } from '../radar/run-job.js';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for radar worker smoke');

const pool = new Pool({ connectionString: databaseUrl, max: 2 });

const job: RadarIngestJobPayload = {
  version: 1,
  source: 'aemet_national_mosaic',
  product: 'reflectivity',
  requested_at: '2026-09-10T04:30:00Z',
};

function geoTiffAsset(name: string, observedAt: string, payload: string): RadarBinaryAsset {
  return {
    metadata: {
      source: 'aemet_national_mosaic',
      product: 'reflectivity',
      crs: 'EPSG:4326',
      observed_at: observedAt,
      fetched_at: '2026-09-10T04:40:10Z',
      asset_format: 'geotiff',
      analysis_ready: true,
    },
    bytes: new Uint8Array(Buffer.concat([Buffer.from([0x49, 0x49, 0x2a, 0x00]), Buffer.from(payload)])),
    contentType: 'image/tiff',
    sourceUrl: 'https://www.aemet.es/es/api-eltiempo/radar/download/compo',
    sourceName: name,
  };
}

function sourceFor(assets: RadarBinaryAsset[]): RadarSourcePort {
  return { async fetchNationalReflectivity() { return assets; } };
}

const a = geoTiffAsset('compo_20260910_0430.tif', '2026-09-10T04:30:00Z', 'radar-A');
const b = geoTiffAsset('compo_20260910_0435.tif', '2026-09-10T04:35:00Z', 'radar-B');
const c = geoTiffAsset('compo_20260910_0440.tif', '2026-09-10T04:40:00Z', 'radar-C');

const storedKeys: string[] = [];
const storage: RadarObjectStoragePort = {
  async putObject(input) {
    storedKeys.push(input.key);
    return { storageKey: `ci/weather/radar/${input.key}` };
  },
};

try {
  const first = await runRadarIngestJob(pool, sourceFor([a, b]), storage, job);
  assert.equal(first.fetched, 2);
  assert.equal(first.stored, 2);
  assert.equal(first.replayed, 0);
  assert.equal(first.analysisReady, 0);
  assert.equal(storedKeys.length, 2);
  assert.ok(first.snapshots.every((item) => item.analysisReady === false));
  assert.ok(first.snapshots.every((item) => item.validationErrors.includes('geotiff_parse_failed')));
  assert.deepEqual(first.snapshots.map((item) => item.sourceName), [a.sourceName, b.sourceName]);

  const replay = await runRadarIngestJob(pool, sourceFor([a, b]), storage, {
    ...job,
    requested_at: '2026-09-10T04:41:00Z',
  });
  assert.equal(replay.fetched, 2);
  assert.equal(replay.stored, 0);
  assert.equal(replay.replayed, 2);
  assert.equal(replay.analysisReady, 0);
  assert.equal(storedKeys.length, 2, 'replaying the same bundle must not upload objects again');

  const oneNew = await runRadarIngestJob(pool, sourceFor([a, b, c]), storage, {
    ...job,
    requested_at: '2026-09-10T04:46:00Z',
  });
  assert.equal(oneNew.fetched, 3);
  assert.equal(oneNew.stored, 1);
  assert.equal(oneNew.replayed, 2);
  assert.equal(oneNew.analysisReady, 0);
  assert.equal(storedKeys.length, 3, 'only the new GeoTIFF must be uploaded');

  const invalidVisualAsset: RadarBinaryAsset = {
    ...a,
    metadata: { ...a.metadata, asset_format: 'gif', analysis_ready: false },
    bytes: new Uint8Array(Buffer.from('GIF89a-not-analytical')),
    contentType: 'image/gif',
    sourceName: 'visual.gif',
  };
  await assert.rejects(
    () => runRadarIngestJob(pool, sourceFor([invalidVisualAsset]), storage, job),
    /only accepts GeoTIFF assets/,
  );

  const rows = await pool.query<{
    asset_format: string;
    analysis_ready: boolean;
    status: string;
    storage_key: string | null;
    metadata_json: { source_name?: string; geotiff_inspection?: { validationErrors?: string[] } };
  }>(`
    SELECT asset_format, analysis_ready, status, storage_key, metadata_json
    FROM radar_snapshots
    ORDER BY observed_at
  `);
  assert.equal(rows.rowCount, 3);
  assert.ok(rows.rows.every((row) => row.asset_format === 'geotiff'));
  assert.ok(rows.rows.every((row) => row.analysis_ready === false));
  assert.ok(rows.rows.every((row) => row.status === 'processed' && row.storage_key));
  assert.ok(rows.rows.every((row) => row.metadata_json.geotiff_inspection?.validationErrors?.includes('geotiff_parse_failed')));
  assert.deepEqual(
    rows.rows.map((row) => row.metadata_json.source_name),
    [a.sourceName, b.sourceName, c.sourceName],
  );

  console.log('RADAR_WORKER_SMOKE_OK');
} finally {
  await pool.end();
}
