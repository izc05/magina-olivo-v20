import assert from 'node:assert/strict';
import pg from 'pg';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for farm radar observation schema smoke');

const pool = new Pool({ connectionString: databaseUrl, max: 2 });

const workspaceA = '11111111-1111-4111-8111-111111111111';
const workspaceB = '22222222-2222-4222-8222-222222222222';
const fieldA = '33333333-3333-4333-8333-333333333333';
const snapshot = '44444444-4444-4444-8444-444444444444';

try {
  await pool.query(`
    INSERT INTO workspaces (id, name, type) VALUES
      ($1, 'Radar Workspace A', 'family'),
      ($2, 'Radar Workspace B', 'family')
  `, [workspaceA, workspaceB]);

  await pool.query(`
    INSERT INTO fields (
      id, workspace_id, client_operation_id, name, crop, status
    ) VALUES (
      $1, $2, '55555555-5555-4555-8555-555555555555', 'Finca Radar A', 'olivar', 'active'
    )
  `, [fieldA, workspaceA]);

  await pool.query(`
    INSERT INTO radar_snapshots (
      id, source, product, crs, observed_at, fetched_at, asset_format, analysis_ready,
      content_type, byte_size, sha256, source_url, storage_key, status, metadata_json
    ) VALUES (
      $1, 'aemet_national_mosaic', 'reflectivity', 'EPSG:4326',
      '2026-09-10T05:00:00Z', '2026-09-10T05:00:10Z', 'geotiff', true,
      'image/tiff', 128, repeat('a', 64),
      'https://www.aemet.es/es/api-eltiempo/radar/download/compo',
      'ci/weather/radar/a.tif', 'processed', '{}'::jsonb
    )
  `, [snapshot]);

  const inserted = await pool.query<{ id: string }>(`
    INSERT INTO farm_radar_observations (
      workspace_id, field_id, radar_snapshot_id, analysis_version, observed_at,
      coverage_status, precipitation_detected, nearest_echo_distance_km,
      direction_degrees, direction_label, reflectivity_dbz_min, reflectivity_dbz_max,
      representative_dbz, analysis_radius_km, quality_flags
    ) VALUES (
      $1, $2, $3, 'radar-observation-v1', '2026-09-10T05:00:00Z',
      'covered', true, 8.125, 270, 'W', 18, 24, 21, 20, ARRAY['scale_verified']
    )
    RETURNING id
  `, [workspaceA, fieldA, snapshot]);
  assert.equal(inserted.rowCount, 1);

  await assert.rejects(
    () => pool.query(`
      INSERT INTO farm_radar_observations (
        workspace_id, field_id, radar_snapshot_id, analysis_version, observed_at,
        coverage_status, precipitation_detected, analysis_radius_km
      ) VALUES ($1, $2, $3, 'wrong-workspace-v1', '2026-09-10T05:00:00Z', 'covered', NULL, 20)
    `, [workspaceB, fieldA, snapshot]),
    /foreign key|violates/i,
  );

  await assert.rejects(
    () => pool.query(`
      INSERT INTO farm_radar_observations (
        workspace_id, field_id, radar_snapshot_id, analysis_version, observed_at,
        coverage_status, precipitation_detected, nearest_echo_distance_km,
        direction_label, analysis_radius_km
      ) VALUES ($1, $2, $3, 'inconsistent-no-echo-v1', '2026-09-10T05:00:00Z', 'covered', false, 4, 'W', 20)
    `, [workspaceA, fieldA, snapshot]),
    /check constraint|violates/i,
  );

  const row = await pool.query<{
    workspace_id: string;
    field_id: string;
    coverage_status: string;
    precipitation_detected: boolean | null;
    nearest_echo_distance_km: string | null;
    direction_label: string | null;
    representative_dbz: string | null;
  }>(`
    SELECT workspace_id, field_id, coverage_status, precipitation_detected,
           nearest_echo_distance_km, direction_label, representative_dbz
    FROM farm_radar_observations
    WHERE analysis_version = 'radar-observation-v1'
  `);
  assert.equal(row.rowCount, 1);
  assert.equal(row.rows[0]?.workspace_id, workspaceA);
  assert.equal(row.rows[0]?.field_id, fieldA);
  assert.equal(row.rows[0]?.coverage_status, 'covered');
  assert.equal(row.rows[0]?.precipitation_detected, true);
  assert.equal(Number(row.rows[0]?.nearest_echo_distance_km), 8.125);
  assert.equal(row.rows[0]?.direction_label, 'W');
  assert.equal(Number(row.rows[0]?.representative_dbz), 21);

  console.log('FARM_RADAR_OBSERVATION_SCHEMA_SMOKE_OK');
} finally {
  await pool.end();
}
