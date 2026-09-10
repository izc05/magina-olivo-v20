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

function sourceFor(asset: RadarBinaryAsset): RadarSourcePort {
  return { async fetchNationalReflectivity() { return asset; } };
}

const storedKeys: string[] = [];
const storage: RadarObjectStoragePort = {
  async putObject(input) {
    storedKeys.push(input.key);
    return { storageKey: `ci/weather/radar/${input.key}` };
  },
};

const gifAsset: RadarBinaryAsset = {
  metadata: {
    source: 'aemet_national_mosaic',
    product: 'reflectivity',
    crs: 'EPSG:4326',
    observed_at: null,
    fetched_at: '2026-09-10T04:30:05Z',
    asset_format: 'gif',
    analysis_ready: false,
  },
  bytes: new Uint8Array(Buffer.from('GIF89a-ci-radar-snapshot')),
  contentType: 'image/gif',
  sourceUrl: 'https://opendata.aemet.es/opendata/sh/ci-radar.gif',
};

const geotiffAsset: RadarBinaryAsset = {
  metadata: {
    source: 'aemet_national_mosaic',
    product: 'reflectivity',
    crs: 'EPSG:4326',
    observed_at: '2026-09-10T04:35:00Z',
    fetched_at: '2026-09-10T04:35:10Z',
    asset_format: 'geotiff',
    analysis_ready: true,
  },
  bytes: new Uint8Array(Buffer.from('II*-ci-geotiff-radar-snapshot')),
  contentType: 'image/tiff',
  sourceUrl: 'https://opendata.aemet.es/opendata/sh/ci-radar.tif',
};

try {
  const first = await runRadarIngestJob(pool, sourceFor(gifAsset), storage, job);
  assert.equal(first.replayed, false);
  assert.equal(first.analysisReady, false);
  assert.equal(first.status, 'stored');
  assert.match(first.storageKey, /\.gif$/);
  assert.equal(storedKeys.length, 1);

  const replay = await runRadarIngestJob(pool, sourceFor(gifAsset), storage, job);
  assert.equal(replay.replayed, true);
  assert.equal(replay.snapshotId, first.snapshotId);
  assert.equal(storedKeys.length, 1, 'a replay must not upload the same radar object twice');

  const geotiff = await runRadarIngestJob(pool, sourceFor(geotiffAsset), storage, {
    ...job,
    requested_at: '2026-09-10T04:35:15Z',
  });
  assert.equal(geotiff.replayed, false);
  assert.equal(geotiff.analysisReady, true);
  assert.match(geotiff.storageKey, /\.tif$/);
  assert.equal(storedKeys.length, 2);

  const rows = await pool.query<{
    asset_format: string;
    analysis_ready: boolean;
    status: string;
    storage_key: string | null;
  }>(`
    SELECT asset_format, analysis_ready, status, storage_key
    FROM radar_snapshots
    ORDER BY fetched_at
  `);
  assert.equal(rows.rowCount, 2);
  assert.deepEqual(rows.rows.map((row) => row.asset_format), ['gif', 'geotiff']);
  assert.deepEqual(rows.rows.map((row) => row.analysis_ready), [false, true]);
  assert.ok(rows.rows.every((row) => row.status === 'stored' && row.storage_key));

  console.log('RADAR_WORKER_SMOKE_OK');
} finally {
  await pool.end();
}
