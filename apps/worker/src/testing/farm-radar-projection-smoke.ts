import assert from 'node:assert/strict';
import { Pool } from 'pg';
import type { RadarGeoTiffInspection, RadarGrid } from '@magina/weather';
import {
  FARM_RADAR_ANALYSIS_VERSION,
  projectRadarSnapshotToFarms,
} from '../radar/project-farm-observations.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const pool = new Pool({ connectionString: databaseUrl });

try {
  const workspaceA = '10000000-0000-4000-8000-000000000001';
  const workspaceB = '10000000-0000-4000-8000-000000000002';
  const fieldA = '20000000-0000-4000-8000-000000000001';
  const fieldB = '20000000-0000-4000-8000-000000000002';
  const snapshotId = '30000000-0000-4000-8000-000000000001';
  // This fingerprint is intentionally unique to this smoke. The radar workflow
  // runs several integration tests against the same database, so fixtures must
  // not collide with the content-addressed snapshot constraint.
  const snapshotSha256 = 'b'.repeat(64);

  await pool.query(`INSERT INTO workspaces (id, name, type) VALUES ($1,'A','family'),($2,'B','family')`, [workspaceA, workspaceB]);
  await pool.query(`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, geometry, status)
    VALUES
      ($1,$2,'40000000-0000-4000-8000-000000000001','Finca A', ST_Multi(ST_GeomFromText('POLYGON((-0.02 -0.02,0.02 -0.02,0.02 0.02,-0.02 0.02,-0.02 -0.02))',4326)), 'active'),
      ($3,$4,'40000000-0000-4000-8000-000000000002','Finca B', ST_Multi(ST_GeomFromText('POLYGON((0.18 -0.02,0.22 -0.02,0.22 0.02,0.18 0.02,0.18 -0.02))',4326)), 'active')
  `, [fieldA, workspaceA, fieldB, workspaceB]);

  await pool.query(`
    INSERT INTO radar_snapshots (
      id, source, product, crs, observed_at, fetched_at, asset_format, analysis_ready,
      content_type, byte_size, sha256, source_url, storage_key, status
    ) VALUES (
      $1,'aemet_national_mosaic','reflectivity','EPSG:4326','2026-09-10T08:00:00Z','2026-09-10T08:01:00Z',
      'geotiff',true,'image/tiff',100,$2,'https://www.aemet.es/test','weather/radar/test.tif','processed'
    )
  `, [snapshotId, snapshotSha256]);

  const width = 5;
  const height = 5;
  const values = new Uint8Array(width * height).fill(1);
  values[2 * width + 3] = 7; // echo east of Finca A

  const grid: RadarGrid = {
    width,
    height,
    bbox: [-0.25, -0.25, 0.25, 0.25],
    values,
    indexBands: [{ index: 7, min: 12, max: 18, rgba: [0, 0, 252, 255] }],
    noCoverageIndexes: [0],
    clearIndexes: [1],
  };

  const inspection: RadarGeoTiffInspection = {
    parsed: true,
    analysisReady: true,
    width,
    height,
    samplesPerPixel: 1,
    crs: 'EPSG:4326',
    geographicTypeGeoKey: 4326,
    bbox: grid.bbox,
    resolution: [0.1, -0.1, 0],
    noData: 0,
    photometricInterpretation: 3,
    bitsPerSample: [8],
    scaleRaw: null,
    scaleSource: 'aemet-national-reflectivity-palette-v1',
    scaleBands: [{ min: 12, max: 18, rgba: [0, 0, 252, 255] }],
    paletteIndexBands: grid.indexBands,
    noCoverageIndexes: [0],
    clearIndexes: [1],
    validationErrors: [],
  };

  const first = await projectRadarSnapshotToFarms({
    pool,
    snapshotId,
    observedAt: '2026-09-10T08:00:00Z',
    sourceName: 'down_radw202609100800_4326.tif',
    inspection,
    grid,
  });
  assert.equal(first.eligibleFields, 2);
  assert.equal(first.projected, 2);

  const second = await projectRadarSnapshotToFarms({
    pool,
    snapshotId,
    observedAt: '2026-09-10T08:00:00Z',
    sourceName: 'down_radw202609100800_4326.tif',
    inspection,
    grid,
  });
  assert.equal(second.projected, 2);

  const rows = await pool.query<{
    workspace_id: string;
    field_id: string;
    precipitation_detected: boolean;
    representative_dbz: string | null;
    analysis_version: string;
  }>(`
    SELECT workspace_id, field_id, precipitation_detected, representative_dbz, analysis_version
    FROM farm_radar_observations
    WHERE radar_snapshot_id = $1
    ORDER BY field_id
  `, [snapshotId]);

  assert.equal(rows.rows.length, 2, 'reprojection must upsert rather than duplicate');
  assert.deepEqual(rows.rows.map((row) => [row.field_id, row.workspace_id]), [
    [fieldA, workspaceA],
    [fieldB, workspaceB],
  ]);
  assert.ok(rows.rows.every((row) => row.analysis_version === FARM_RADAR_ANALYSIS_VERSION));
  assert.ok(rows.rows.every((row) => row.representative_dbz === null), 'no synthetic representative dBZ is allowed');

  console.log('FARM_RADAR_PROJECTION_SMOKE_OK');
} finally {
  await pool.end();
}
