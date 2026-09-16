import assert from 'node:assert/strict';
import test from 'node:test';
import { buildWeatherVisualModel } from '../weather-visual-model.js';
import type { WeatherState } from '../weather-state.js';

function weather(overrides: Partial<WeatherState> = {}): WeatherState {
  return {
    provider: 'Open-Meteo',
    condition: 'clear',
    intensity: 0,
    temperatureC: 24,
    feelsLikeC: 24,
    windSpeedKmh: 5,
    windDirectionDeg: 0,
    windGustsKmh: 8,
    precipitationMm: 0,
    visibilityM: 20000,
    cloudCoverPercent: 10,
    weatherCode: 0,
    dayPhase: 'day',
    observedAt: '2026-09-16T08:00:00Z',
    expiresAt: '2026-09-16T08:15:00Z',
    officialAlert: null,
    stale: false,
    ...overrides,
  };
}

test('maps clear phases to stable sky and ambient presets', () => {
  const day = buildWeatherVisualModel(weather());
  const golden = buildWeatherVisualModel(weather({ dayPhase: 'golden_hour' }));
  const night = buildWeatherVisualModel(weather({ dayPhase: 'night' }));

  assert.equal(day.skyPreset, 'clear');
  assert.equal(day.ambientTone, 'neutral');
  assert.equal(day.dustAmount, 0.15);
  assert.equal(golden.ambientTone, 'warm');
  assert.equal(night.skyPreset, 'night');
  assert.equal(night.ambientTone, 'cool');
});

test('maps cloudy intensity without inventing precipitation', () => {
  const light = buildWeatherVisualModel(weather({ condition: 'cloudy', intensity: 1 }));
  const heavy = buildWeatherVisualModel(weather({ condition: 'cloudy', intensity: 3 }));

  assert.equal(light.skyPreset, 'soft-cloud');
  assert.equal(light.cloudDensity, 0.45);
  assert.equal(heavy.skyPreset, 'overcast');
  assert.equal(heavy.cloudDensity, 0.9);
  assert.equal(heavy.rainAmount, 0);
});

test('maps rain intensity and bounds wind-derived rain angle', () => {
  const medium = buildWeatherVisualModel(weather({ condition: 'rain', intensity: 2, windDirectionDeg: 270 }));
  const extreme = buildWeatherVisualModel(weather({ condition: 'rain', intensity: 3, windDirectionDeg: 359 }));

  assert.equal(medium.rainAmount, 0.55);
  assert.ok(medium.cloudDensity >= 0.7);
  assert.ok(medium.rainAngleDeg >= -25 && medium.rainAngleDeg <= 25);
  assert.ok(extreme.rainAngleDeg >= -25 && extreme.rainAngleDeg <= 25);
});

test('maps storm to lightning and safety emphasis', () => {
  const storm = buildWeatherVisualModel(weather({ condition: 'storm', intensity: 3 }));

  assert.equal(storm.skyPreset, 'storm');
  assert.equal(storm.lightningAmount, 0.7);
  assert.equal(storm.ambientTone, 'storm');
  assert.equal(storm.safetyEmphasis, true);
  assert.ok(storm.rainAmount >= 0.65);
});

test('maps fog, snow and wind into isolated visual families', () => {
  const fog = buildWeatherVisualModel(weather({ condition: 'fog', intensity: 2 }));
  const snow = buildWeatherVisualModel(weather({ condition: 'snow', intensity: 2 }));
  const wind = buildWeatherVisualModel(weather({ condition: 'wind', intensity: 3 }));

  assert.equal(fog.fogDensity, 0.62);
  assert.equal(fog.rainAmount, 0);
  assert.equal(snow.snowAmount, 0.55);
  assert.equal(snow.ambientTone, 'cool');
  assert.equal(wind.windStrength, 0.9);
  assert.equal(wind.leafAmount, 0.8);
});

test('official AEMET alert emphasizes safety without increasing visual intensity', () => {
  const base = buildWeatherVisualModel(weather({ condition: 'cloudy', intensity: 1 }));
  const alerted = buildWeatherVisualModel(weather({
    condition: 'cloudy',
    intensity: 1,
    officialAlert: {
      source: 'AEMET',
      level: 'orange',
      title: 'Aviso por viento',
      description: null,
      startsAt: null,
      endsAt: null,
    },
  }));

  assert.equal(alerted.safetyEmphasis, true);
  assert.equal(alerted.cloudDensity, base.cloudDensity);
  assert.equal(alerted.windStrength, base.windStrength);
  assert.equal(alerted.lightningAmount, base.lightningAmount);
});

test('reduced motion disables transitions and animated decorative families while preserving static precipitation strength', () => {
  const model = buildWeatherVisualModel(
    weather({ condition: 'storm', intensity: 3 }),
    { reducedMotion: true },
  );

  assert.equal(model.reducedMotion, true);
  assert.equal(model.transitionDurationMs, 0);
  assert.equal(model.lightningAmount, 0);
  assert.ok(model.rainAmount >= 0.65);
  assert.equal(model.leafAmount, 0);
  assert.equal(model.dustAmount, 0);
});

test('minimal tier caps transition duration and disables continuous particle families', () => {
  const model = buildWeatherVisualModel(
    weather({ condition: 'wind', intensity: 3 }),
    { performanceTier: 'minimal' },
  );

  assert.equal(model.performanceTier, 'minimal');
  assert.ok(model.transitionDurationMs <= 1000);
  assert.equal(model.leafAmount, 0);
  assert.equal(model.dustAmount, 0);
  assert.equal(model.lightningAmount, 0);
});

test('identical weather input produces deterministic visual output', () => {
  const input = weather({ condition: 'rain', intensity: 2, windDirectionDeg: 145, dayPhase: 'dusk' });
  assert.deepEqual(buildWeatherVisualModel(input), buildWeatherVisualModel(input));
});
