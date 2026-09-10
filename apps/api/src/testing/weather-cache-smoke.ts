import assert from 'node:assert/strict';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for weather cache smoke test.');

const workspaceId = '11111111-1111-4111-8111-111111111111';
const userId = '33333333-3333-4333-8333-333333333333';
const fieldId = '55555555-5555-4555-8555-555555555555';
const foreignWorkspaceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const foreignFieldId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

let failUpstream = false;
const calls: string[] = [];
const weatherProvider: MunicipalityWeatherProvider = {
  async dailyForecast(code) {
    calls.push(code);
    if (failUpstream) throw new Error('SYNTHETIC_AEMET_OUTAGE');
    const name = code === '23902' ? 'Bedmar y Garcíez' : code === '23044' ? 'Huelma' : `Municipio ${code}`;
    return {
      provider: 'AEMET OpenData',
      municipalityCode: code,
      municipalityName: name,
      province: 'Jaén',
      elaboratedAt: '2026-09-10T06:00:00',
      days: [{
        date: '2026-09-10',
        precipitationProbabilityPercent: 35,
        temperatureMinC: 14,
        temperatureMaxC: 27,
        windMaxKmh: 20,
      }],
    };
  },
};

const db = createDatabase(databaseUrl);
const app = buildApp({ db, weatherProvider });
const developmentHeaders = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
};

try {
  await app.ready();

  const places = await app.inject({ method: 'GET', url: '/api/v1/public/territory/places' });
  assert.equal(places.statusCode, 200, places.body);
  const placeRows = places.json().places as Array<{ slug: string; municipality_id: string }>;
  const bedmar = placeRows.find((item) => item.slug === 'bedmar');
  const garciez = placeRows.find((item) => item.slug === 'garciez');
  assert.ok(bedmar);
  assert.ok(garciez);
  assert.equal(bedmar.municipality_id, garciez.municipality_id);

  const huelmaFirst = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/huelma/daily' });
  assert.equal(huelmaFirst.statusCode, 200, huelmaFirst.body);
  assert.equal(huelmaFirst.json().cache_status, 'refreshed');
  assert.equal(huelmaFirst.json().forecast.municipalityCode, '23044');
  assert.deepEqual(calls, ['23044']);

  const huelmaSecond = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/huelma/daily' });
  assert.equal(huelmaSecond.statusCode, 200, huelmaSecond.body);
  assert.equal(huelmaSecond.json().cache_status, 'fresh');
  assert.deepEqual(calls, ['23044']);

  const fieldWeather = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${fieldId}/weather/daily`,
    headers: developmentHeaders,
  });
  assert.equal(fieldWeather.statusCode, 200, fieldWeather.body);
  assert.equal(fieldWeather.json().cache_status, 'fresh');
  assert.deepEqual(calls, ['23044']);

  const bedmarWeather = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/bedmar/daily' });
  assert.equal(bedmarWeather.statusCode, 200, bedmarWeather.body);
  assert.equal(bedmarWeather.json().forecast.municipalityCode, '23902');
  assert.deepEqual(calls, ['23044', '23902']);

  const garciezWeather = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/garciez/daily' });
  assert.equal(garciezWeather.statusCode, 200, garciezWeather.body);
  assert.equal(garciezWeather.json().cache_status, 'fresh');
  assert.equal(garciezWeather.json().forecast.municipalityCode, '23902');
  assert.deepEqual(calls, ['23044', '23902']);

  await sql`
    UPDATE weather_forecast_cache
    SET expires_at = now() - interval '1 minute'
    WHERE municipality_id = (SELECT id FROM territory_municipalities WHERE ine_code = '23044')
      AND provider = 'aemet_daily'
  `.execute(db);
  failUpstream = true;

  const staleHuelma = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/huelma/daily' });
  assert.equal(staleHuelma.statusCode, 200, staleHuelma.body);
  assert.equal(staleHuelma.json().cache_status, 'stale');
  assert.equal(staleHuelma.json().stale, true);
  assert.deepEqual(calls, ['23044', '23902', '23044']);

  failUpstream = false;
  const noCacheUnknown = await app.inject({ method: 'GET', url: '/api/v1/public/weather/places/no-existe/daily' });
  assert.equal(noCacheUnknown.statusCode, 404, noCacheUnknown.body);

  await db.insertInto('workspaces').values({
    id: foreignWorkspaceId,
    name: 'Weather foreign workspace',
    type: 'family',
  }).execute();
  await sql`
    INSERT INTO fields (
      id, workspace_id, client_operation_id, name, municipality, province, municipality_id,
      tree_count, crop, variety, water_regime, status
    ) VALUES (
      ${foreignFieldId}::uuid,
      ${foreignWorkspaceId}::uuid,
      'cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid,
      'Finca weather ajena', 'Huelma', 'Jaén',
      (SELECT id FROM territory_municipalities WHERE ine_code = '23044'),
      10, 'olivar', 'picual', 'secano', 'active'
    )
  `.execute(db);

  const foreignWeather = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${foreignFieldId}/weather/daily`,
    headers: developmentHeaders,
  });
  assert.equal(foreignWeather.statusCode, 404, foreignWeather.body);

  console.log('WEATHER_CACHE_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
