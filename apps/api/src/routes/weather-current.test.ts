import assert from 'node:assert/strict';
import test from 'node:test';
import Fastify from 'fastify';
import { registerWeatherRoutes } from './weather.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import type { CurrentWeatherProvider } from '../weather/current-provider.js';

const dailyProvider: MunicipalityWeatherProvider = {
  async dailyForecast() {
    throw new Error('daily forecast should not be called in current weather tests');
  },
};

function buildTestApp(currentWeatherProvider: CurrentWeatherProvider) {
  const app = Fastify();
  registerWeatherRoutes(app, null, dailyProvider, currentWeatherProvider);
  return app;
}

test('current weather rejects invalid coordinate payloads before calling upstream provider', async () => {
  let calls = 0;
  const app = buildTestApp({
    async currentWeather() {
      calls += 1;
      throw new Error('should_not_be_called');
    },
  });

  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/public/weather/current',
    payload: { latitude: 91, longitude: -3.4 },
  });

  assert.equal(response.statusCode, 400);
  assert.deepEqual(response.json(), { error: 'invalid_weather_coordinates' });
  assert.equal(calls, 0);
  await app.close();
});

test('current weather returns normalized weather without echoing exact coordinates', async () => {
  const app = buildTestApp({
    async currentWeather(latitude, longitude) {
      assert.equal(latitude, 37.73);
      assert.equal(longitude, -3.41);
      return {
        weather: {
          provider: 'Open-Meteo',
          condition: 'rain',
          intensity: 2,
          temperatureC: 17,
          feelsLikeC: 16,
          windSpeedKmh: 22,
          windDirectionDeg: 220,
          windGustsKmh: 34,
          precipitationMm: 1.8,
          visibilityM: 9000,
          cloudCoverPercent: 92,
          weatherCode: 63,
          dayPhase: 'day',
          observedAt: '2026-09-15T18:00',
          expiresAt: '2026-09-15T18:15:00.000Z',
          officialAlert: null,
          stale: false,
        },
        cacheStatus: 'refreshed',
        fetchedAt: '2026-09-15T18:00:00.000Z',
      };
    },
  });

  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/public/weather/current',
    payload: { latitude: 37.73, longitude: -3.41 },
  });

  assert.equal(response.statusCode, 200);
  assert.equal(response.headers['cache-control'], 'no-store');
  const payload = response.json();
  assert.equal(payload.weather.condition, 'rain');
  assert.equal(payload.weather.intensity, 2);
  assert.equal(payload.cache_status, 'refreshed');
  assert.equal('latitude' in payload, false);
  assert.equal('longitude' in payload, false);
  await app.close();
});
