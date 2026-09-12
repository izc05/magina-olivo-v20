import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin sources smoke test.');

const db = createDatabase(databaseUrl);
let claims: GoogleIdentityClaims = {
  subject: 'admin-google-subject',
  email: 'admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Mágina',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Mágina',
  hostedDomain: 'magina.test',
};

const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const weatherRequests: string[] = [];
const radarJobs: unknown[] = [];
const notificationJobs: unknown[] = [];
const weatherProvider = {
  async dailyForecast(municipalityCode: string) {
    weatherRequests.push(municipalityCode);
    return {
      provider: 'AEMET OpenData' as const,
      municipalityCode,
      municipalityName: 'Municipio smoke',
      province: 'Jaén',
      elaboratedAt: new Date().toISOString(),
      days: [],
    };
  },
};
const radarQueue = {
  async enqueue(payload: unknown) {
    radarJobs.push(payload);
    return 'radar-job-smoke';
  },
};
const notificationQueue = {
  async enqueue(payload: unknown) {
    notificationJobs.push(payload);
    return 'notification-job-smoke';
  },
};
const app = buildApp({ db, googleVerifier: verifier, weatherProvider, radarQueue, notificationQueue });
const credential = 'synthetic-google-id-token-sources-'.padEnd(140, 'x');

async function login() {
  const response = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.ok(response.statusCode === 200 || response.statusCode === 201, response.body);
  const cookieHeader = response.headers['set-cookie'];
  assert.equal(typeof cookieHeader, 'string');
  return { body: response.json(), cookie: String(cookieHeader).split(';', 1)[0] };
}

try {
  await app.ready();

  const adminLogin = await login();

  claims = {
    ...claims,
    subject: 'sources-owner-google-subject',
    email: 'sources-owner@magina.test',
    displayName: 'Owner sin permisos corporativos',
  };
  const ownerLogin = await login();
  assert.equal(ownerLogin.body.workspaces[0].role, 'owner');
  const workspaceId = String(ownerLogin.body.workspaces[0].workspace_id);
  const ownerUserId = String(ownerLogin.body.user.id);

  const municipality = await sql<{ id: string }>`
    SELECT id FROM territory_municipalities WHERE name = 'Huelma' LIMIT 1
  `.execute(db);
  assert.ok(municipality.rows[0]?.id, 'Huelma must exist for sources smoke');
  const municipalityId = municipality.rows[0].id;

  await sql`
    INSERT INTO weather_forecast_cache (municipality_id, provider, payload_json, fetched_at, expires_at, last_error_at, last_error_code)
    VALUES (${municipalityId}::uuid, 'aemet_daily', '{}'::jsonb, now(), now() + interval '2 hours', NULL, NULL)
    ON CONFLICT (municipality_id, provider) DO UPDATE SET
      payload_json = EXCLUDED.payload_json,
      fetched_at = EXCLUDED.fetched_at,
      expires_at = EXCLUDED.expires_at,
      last_error_at = NULL,
      last_error_code = NULL
  `.execute(db);

  await sql`
    INSERT INTO radar_snapshots (
      source, product, crs, observed_at, fetched_at, asset_format, analysis_ready,
      content_type, byte_size, sha256, source_url, status, metadata_json
    ) VALUES (
      'aemet_national_mosaic', 'reflectivity', 'EPSG:4326', now(), now(), 'geotiff', true,
      'image/tiff', 2048, ${'a'.repeat(64)}, 'https://example.invalid/radar-smoke.tiff', 'processed', '{}'::jsonb
    )
    ON CONFLICT (source, product, sha256) DO UPDATE SET
      observed_at = EXCLUDED.observed_at,
      fetched_at = EXCLUDED.fetched_at,
      analysis_ready = true,
      status = 'processed',
      error_code = NULL,
      updated_at = now()
  `.execute(db);

  const field = await sql<{ id: string }>`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status)
    VALUES (gen_random_uuid(), ${workspaceId}::uuid, gen_random_uuid(), 'Finca telemetría', 'olivar', 'active')
    RETURNING id
  `.execute(db);
  const fieldId = field.rows[0].id;

  await sql`
    INSERT INTO field_land_refs (field_id, source, reference, status, checked_at)
    VALUES
      (${fieldId}::uuid, 'catastro', 'SOURCE-SMOKE-CAT', 'verified', now()),
      (${fieldId}::uuid, 'sigpac', 'SOURCE-SMOKE-SIG', 'linked', now())
  `.execute(db);

  const documentId = randomUUID();
  const versionId = randomUUID();
  await sql`
    INSERT INTO documents (id, workspace_id, client_operation_id, kind, title, status, created_by)
    VALUES (${documentId}::uuid, ${workspaceId}::uuid, gen_random_uuid(), 'harvest_ticket', 'Documento OCR telemetry', 'active', ${ownerUserId}::uuid)
  `.execute(db);
  await sql`
    INSERT INTO document_versions (
      id, document_id, version_no, storage_key, original_filename, mime_type, byte_size, sha256,
      created_by, upload_status, integrity_status, uploaded_at
    ) VALUES (
      ${versionId}::uuid, ${documentId}::uuid, 1, ${`smoke/${versionId}.jpg`}, 'ocr-smoke.jpg', 'image/jpeg', 128,
      ${'b'.repeat(64)}, ${ownerUserId}::uuid, 'uploaded', 'verified', now()
    )
  `.execute(db);
  await sql`
    INSERT INTO ocr_runs (
      id, workspace_id, client_operation_id, document_version_id, provider, provider_version,
      status, raw_text, confidence, created_at, started_at, completed_at
    ) VALUES (
      gen_random_uuid(), ${workspaceId}::uuid, gen_random_uuid(), ${versionId}::uuid, 'smoke-ocr', '1',
      'succeeded', 'ok', 0.99000, now(), now(), now()
    )
  `.execute(db);

  const response = await app.inject({ method: 'GET', url: '/api/v1/admin/sources', headers: { cookie: adminLogin.cookie } });
  assert.equal(response.statusCode, 200, response.body);
  const payload = response.json();
  assert.equal(payload.sources.length, 5);

  const weather = payload.sources.find((source: { id: string }) => source.id === 'aemet_forecast');
  assert.equal(weather.state, 'ok');
  assert.ok(weather.metrics.cache_entries >= 1);
  assert.equal(weather.metrics.expired_entries, 0);

  const radar = payload.sources.find((source: { id: string }) => source.id === 'aemet_radar');
  assert.equal(radar.state, 'ok');
  assert.ok(radar.metrics.analysis_ready_snapshots >= 1);

  const ocr = payload.sources.find((source: { id: string }) => source.id === 'ocr');
  assert.equal(ocr.state, 'ok');
  assert.equal(ocr.provider, 'smoke-ocr');
  assert.ok(ocr.metrics.succeeded_7d >= 1);

  const catastro = payload.sources.find((source: { id: string }) => source.id === 'catastro');
  const sigpac = payload.sources.find((source: { id: string }) => source.id === 'sigpac');
  assert.equal(catastro.state, 'unmonitored');
  assert.equal(sigpac.state, 'unmonitored');
  assert.ok(catastro.metrics.verified_references >= 1);
  assert.ok(sigpac.metrics.linked_references >= 1);

  const denied = await app.inject({ method: 'GET', url: '/api/v1/admin/sources', headers: { cookie: ownerLogin.cookie } });
  assert.equal(denied.statusCode, 403, denied.body);
  assert.equal(denied.json().error, 'platform_admin_required');

  await sql`
    UPDATE weather_forecast_cache
    SET expires_at = now() - interval '1 minute'
    WHERE municipality_id = ${municipalityId}::uuid AND provider = 'aemet_daily'
  `.execute(db);

  const weatherRefresh = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/sources/aemet_forecast/refresh',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(weatherRefresh.statusCode, 200, weatherRefresh.body);
  assert.ok(weatherRefresh.json().processed >= 1);
  assert.ok(weatherRefresh.json().refreshed >= 1);
  assert.equal(weatherRefresh.json().failed, 0);
  assert.ok(weatherRequests.length >= 1);

  const radarIngest = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/sources/aemet_radar/ingest',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(radarIngest.statusCode, 202, radarIngest.body);
  assert.equal(radarIngest.json().job_id, 'radar-job-smoke');
  assert.equal(radarJobs.length, 1);
  assert.deepEqual(radarJobs[0], {
    version: 1,
    source: 'aemet_national_mosaic',
    product: 'reflectivity',
    requested_at: radarIngest.json().queued_at,
  });

  const dispatch = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/sources/notifications/dispatch',
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(dispatch.statusCode, 202, dispatch.body);
  assert.equal(dispatch.json().job_id, 'notification-job-smoke');
  assert.equal(dispatch.json().limit, 50);
  assert.equal(notificationJobs.length, 1);
  assert.deepEqual(notificationJobs[0], { version: 1, limit: 50 });

  const actions = await db.selectFrom('admin_audit_log')
    .select(['action'])
    .where('action', 'in', [
      'source.weather_refresh',
      'source.radar_ingest_queued',
      'source.notification_dispatch_queued',
    ])
    .execute();
  const actionNames = new Set(actions.map((row) => row.action));
  assert.ok(actionNames.has('source.weather_refresh'));
  assert.ok(actionNames.has('source.radar_ingest_queued'));
  assert.ok(actionNames.has('source.notification_dispatch_queued'));

  await db.insertInto('platform_admins').values({
    user_id: ownerUserId,
    role: 'editor',
    status: 'active',
    granted_by: null,
  }).onConflict((conflict) => conflict.column('user_id').doUpdateSet({ role: 'editor', status: 'active', updated_at: new Date() })).execute();

  const editorRead = await app.inject({ method: 'GET', url: '/api/v1/admin/sources', headers: { cookie: ownerLogin.cookie } });
  assert.equal(editorRead.statusCode, 200, editorRead.body);
  for (const url of [
    '/api/v1/admin/sources/aemet_forecast/refresh',
    '/api/v1/admin/sources/aemet_radar/ingest',
    '/api/v1/admin/sources/notifications/dispatch',
  ]) {
    const blocked = await app.inject({ method: 'POST', url, headers: { cookie: ownerLogin.cookie } });
    assert.equal(blocked.statusCode, 403, blocked.body);
    assert.equal(blocked.json().error, 'platform_admin_role_required');
    assert.equal(blocked.json().minimum_role, 'admin');
  }

  console.log('ADMIN_SOURCES_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
