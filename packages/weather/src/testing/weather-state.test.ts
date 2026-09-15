import assert from 'node:assert/strict';
import test from 'node:test';
import {
  classifyWeatherState,
  parseOpenMeteoCurrentWeather,
  validateWeatherCoordinates,
} from '../weather-state.js';

test('classifies precipitation and WMO thunder codes as storm before generic rain', () => {
  const state = classifyWeatherState({
    weatherCode: 95,
    isDay: true,
    temperatureC: 14,
    apparentTemperatureC: 12,
    precipitationMm: 5.2,
    rainMm: 4.8,
    snowfallCm: 0,
    cloudCoverPercent: 100,
    windSpeedKmh: 38,
    windDirectionDeg: 230,
    windGustsKmh: 64,
    visibilityM: 7000,
    observedAt: '2026-09-15T18:00:00Z',
  });

  assert.equal(state.condition, 'storm');
  assert.equal(state.intensity, 3);
  assert.equal(state.dayPhase, 'day');
});

test('classifies fog, snow and strong wind into stable Mágina conditions', () => {
  assert.equal(classifyWeatherState({ weatherCode: 45, isDay: true, visibilityM: 500 }).condition, 'fog');
  assert.equal(classifyWeatherState({ weatherCode: 73, isDay: true, snowfallCm: 1.5 }).condition, 'snow');
  assert.equal(classifyWeatherState({ weatherCode: 1, isDay: true, windSpeedKmh: 47 }).condition, 'wind');
});

test('uses night phase when Open-Meteo reports is_day=0', () => {
  const state = parseOpenMeteoCurrentWeather({
    latitude: 37.73,
    longitude: -3.41,
    current: {
      time: '2026-09-15T22:00',
      interval: 900,
      temperature_2m: 17.1,
      apparent_temperature: 16.4,
      is_day: 0,
      precipitation: 0,
      rain: 0,
      snowfall: 0,
      weather_code: 1,
      cloud_cover: 18,
      wind_speed_10m: 8.2,
      wind_direction_10m: 215,
      wind_gusts_10m: 15.1,
    },
  });

  assert.equal(state.dayPhase, 'night');
  assert.equal(state.condition, 'clear');
  assert.equal(state.provider, 'Open-Meteo');
});

test('rejects coordinates outside Earth bounds', () => {
  assert.deepEqual(validateWeatherCoordinates(37.7, -3.4), { latitude: 37.7, longitude: -3.4 });
  assert.throws(() => validateWeatherCoordinates(91, -3.4), /INVALID_WEATHER_COORDINATES/);
  assert.throws(() => validateWeatherCoordinates(37.7, 181), /INVALID_WEATHER_COORDINATES/);
});
