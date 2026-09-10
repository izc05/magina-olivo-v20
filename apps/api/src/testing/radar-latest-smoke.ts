import assert from 'node:assert/strict';
import { Pool } from 'pg';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
process.env.ALLOW_DEV_AUTH_HEADERS = 'true';

const workspaceA = '51000000-0000-4000-8000-000000000001';
const workspaceB = '51000000-0000-4000-8000-000000000002';
const userId = '52000000-0000-4000-8000-000000000001';
const fieldEcho = '53000000-0000-4000-8000-000000000001';
const fieldEmpty = '53000000-0000-4000-8000-000000000002';
const fieldForeign = '53000000-0000-4000-8000-000000000003';
const snapshotId = '54000000-0000-4000-8000-000000000001';
const observationId = '55000000-0000-4000-8000-000000000001';

const pool = new Pool({ connectionString: databaseUrl });
const db = createDatabase(databaseUrl);
const app = buildApp({ db });

try {
  await pool.query(`INSERT INTO workspaces (id,name,type) VALUES ($1,'Radar A','family'),($2,'Radar B','family')`, [workspaceA, workspaceB]);
  await pool.query(`
    INSERT INTO fields (id,workspace_id,client_operation_id,name,status)
    VALUES
      ($1,$2,'56000000-0000-4000-8000-000000000001','Con eco','active'),
      ($3,$2,'56000000-0000-4000-8000-000000000002','Sin observación','active'),
      ($4,$5,'56000000-0000-4000-8000-000000000003','Ajena','active')
  `, [fieldEcho, workspaceA, fieldEmpty, fieldForeign, workspaceB]);
  await pool.query(`
    INSERT INTO radar_snapshots (
      id,source,product,crs,observed_at,fetched_at,asset_format,analysis_ready,
      content_type,byte_size,sha256,source_url,storage_key,status
    ) VALUES ($1,'aemet_national_mosaic','reflectivity','EPSG:4326','2026-09-10T08:00:00Z',
      '2026-09-10T08:01:00Z','geotiff',true,'image/tiff',100,$2,
      'https://www.aemet.es/es/api-eltiempo/radar/download/compo','weather/radar/api-smoke.tif','processed')
  `, [snapshotId, 'b'.repeat(64)]);
  await pool.query(`
    INSERT INTO farm_radar_observations (
      id,workspace_id,field_id,radar_snapshot_id,analysis_version,observed_at,
      coverage_status,precipitation_detected,nearest_echo_distance_km,direction_degrees,
      direction_label,reflectivity_dbz_min,reflectivity_dbz_max,representative_dbz,
      analysis_radius_km,quality_flags,metadata_json
    ) VALUES ($1,$2,$3,$4,'aemet-national-palette-v1-spatial-v1','2026-09-10T08:00:00Z',
      'covered',true,8.4,270,'W',18,24,NULL,80,ARRAY['palette_mapping_v1'],'{}'::jsonb)
  `, [observationId, workspaceA, fieldEcho, snapshotId]);

  await app.ready();

  const echo = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldEcho}/radar/latest`,
    headers: { 'x-workspace-id': workspaceA, 'x-user-id': userId },
  });
  assert.equal(echo.statusCode, 200, echo.body);
  const echoBody = echo.json();
  assert.equal(echoBody.observation.precipitation_detected, true);
  assert.equal(echoBody.observation.nearest_echo.distance_km, 8.4);
  assert.equal(echoBody.observation.nearest_echo.direction, 'W');
  assert.deepEqual(echoBody.observation.nearest_echo.reflectivity_dbz, { min: 18, max: 24 });
  assert.match(echoBody.summary, /8\.4 km al oeste/);
  assert.equal(echoBody.attribution, 'AEMET');
  assert.equal(echoBody.semantics, 'observed_reflectivity_not_forecast');
  assert.equal(/llover[aá]/i.test(JSON.stringify(echoBody)), false);
  assert.equal(Object.prototype.hasOwnProperty.call(echoBody.observation, 'representative_dbz'), false);

  const empty = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldEmpty}/radar/latest`,
    headers: { 'x-workspace-id': workspaceA, 'x-user-id': userId },
  });
  assert.equal(empty.statusCode, 200, empty.body);
  assert.equal(empty.json().observation, null);

  const foreign = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldForeign}/radar/latest`,
    headers: { 'x-workspace-id': workspaceA, 'x-user-id': userId },
  });
  assert.equal(foreign.statusCode, 404, foreign.body);

  console.log('RADAR_LATEST_API_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
  await pool.end();
}
