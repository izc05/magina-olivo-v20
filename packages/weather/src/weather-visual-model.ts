import type { WeatherDayPhase, WeatherIntensity, WeatherState } from './weather-state.js';

export type WeatherPerformanceTier = 'full' | 'balanced' | 'minimal';
export type WeatherSkyPreset = 'clear' | 'soft-cloud' | 'overcast' | 'storm' | 'fog' | 'snow' | 'night';
export type WeatherAmbientTone = 'neutral' | 'warm' | 'cool' | 'storm';

export type WeatherVisualModel = {
  skyPreset: WeatherSkyPreset;
  ambientTone: WeatherAmbientTone;
  ambientOpacity: number;
  cloudDensity: number;
  fogDensity: number;
  rainAmount: number;
  rainAngleDeg: number;
  snowAmount: number;
  windStrength: number;
  leafAmount: number;
  dustAmount: number;
  lightningAmount: number;
  transitionDurationMs: number;
  performanceTier: WeatherPerformanceTier;
  reducedMotion: boolean;
  safetyEmphasis: boolean;
};

export type BuildWeatherVisualModelOptions = {
  reducedMotion?: boolean;
  performanceTier?: WeatherPerformanceTier;
};

const cloudyDensity: Record<WeatherIntensity, number> = { 0: 0.35, 1: 0.45, 2: 0.72, 3: 0.9 };
const rainAmount: Record<WeatherIntensity, number> = { 0: 0.25, 1: 0.25, 2: 0.55, 3: 0.9 };
const fogDensity: Record<WeatherIntensity, number> = { 0: 0.35, 1: 0.35, 2: 0.62, 3: 0.85 };
const snowAmount: Record<WeatherIntensity, number> = { 0: 0.25, 1: 0.25, 2: 0.55, 3: 0.85 };
const windAmount: Record<WeatherIntensity, number> = { 0: 0.35, 1: 0.35, 2: 0.65, 3: 0.9 };
const leafAmount: Record<WeatherIntensity, number> = { 0: 0.2, 1: 0.2, 2: 0.5, 3: 0.8 };

function clamp(value: number, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value));
}

function ambientToneFor(phase: WeatherDayPhase, condition: WeatherState['condition']): WeatherAmbientTone {
  if (condition === 'storm') return 'storm';
  if (condition === 'snow') return 'cool';
  if (phase === 'golden_hour') return 'warm';
  if (phase === 'dusk' || phase === 'night') return 'cool';
  return 'neutral';
}

function ambientOpacityFor(phase: WeatherDayPhase, condition: WeatherState['condition']) {
  if (condition === 'storm') return 0.5;
  if (condition === 'fog') return 0.36;
  if (condition === 'snow') return 0.28;
  if (phase === 'night') return 0.48;
  if (phase === 'dusk') return 0.32;
  if (phase === 'golden_hour') return 0.24;
  return 0.12;
}

function skyPresetFor(weather: WeatherState): WeatherSkyPreset {
  if (weather.condition === 'storm') return 'storm';
  if (weather.condition === 'fog') return 'fog';
  if (weather.condition === 'snow') return 'snow';
  if (weather.condition === 'cloudy') return weather.intensity <= 1 ? 'soft-cloud' : 'overcast';
  if (weather.condition === 'rain') return 'overcast';
  if (weather.dayPhase === 'night') return 'night';
  return 'clear';
}

function rainAngleFromDirection(direction: number | null) {
  if (direction == null || !Number.isFinite(direction)) return 0;
  const radians = ((direction % 360) * Math.PI) / 180;
  return clamp(Math.sin(radians) * 25, -25, 25);
}

function trustedWindStrength(weather: WeatherState) {
  if (weather.condition === 'wind') return windAmount[weather.intensity];
  const speed = weather.windSpeedKmh ?? 0;
  const gusts = weather.windGustsKmh ?? 0;
  return clamp(Math.max(speed / 60, gusts / 80));
}

function transitionDurationFor(weather: WeatherState, reducedMotion: boolean, performanceTier: WeatherPerformanceTier) {
  if (reducedMotion) return 0;
  const atmospheric = weather.condition === 'rain' || weather.condition === 'storm' || weather.condition === 'fog' || weather.condition === 'snow';
  const duration = atmospheric ? 3200 : 2400;
  return performanceTier === 'minimal' ? Math.min(duration, 1000) : duration;
}

export function buildWeatherVisualModel(
  weather: WeatherState,
  options: BuildWeatherVisualModelOptions = {},
): WeatherVisualModel {
  const reducedMotion = options.reducedMotion ?? false;
  const performanceTier = options.performanceTier ?? 'balanced';
  const windStrength = trustedWindStrength(weather);
  const animatedDecorationsAllowed = !reducedMotion && performanceTier !== 'minimal';

  let cloudDensityValue = 0;
  let fogDensityValue = 0;
  let rainAmountValue = 0;
  let snowAmountValue = 0;
  let leafAmountValue = 0;
  let dustAmountValue = 0;
  let lightningAmountValue = 0;

  switch (weather.condition) {
    case 'cloudy':
      cloudDensityValue = cloudyDensity[weather.intensity];
      break;
    case 'rain':
      cloudDensityValue = Math.max(0.7, cloudyDensity[Math.max(1, weather.intensity) as WeatherIntensity]);
      rainAmountValue = rainAmount[weather.intensity];
      if (animatedDecorationsAllowed && windStrength >= 0.45) {
        leafAmountValue = clamp(windStrength * 0.35);
      }
      break;
    case 'storm':
      cloudDensityValue = 0.95;
      rainAmountValue = Math.max(0.65, rainAmount[weather.intensity]);
      lightningAmountValue = weather.intensity >= 3 ? 0.7 : weather.intensity >= 2 ? 0.35 : 0.2;
      break;
    case 'fog':
      fogDensityValue = fogDensity[weather.intensity];
      cloudDensityValue = 0.45;
      break;
    case 'snow':
      cloudDensityValue = 0.72;
      snowAmountValue = snowAmount[weather.intensity];
      break;
    case 'wind':
      if (animatedDecorationsAllowed) leafAmountValue = leafAmount[weather.intensity];
      break;
    case 'clear':
      if (animatedDecorationsAllowed && (weather.dayPhase === 'day' || weather.dayPhase === 'golden_hour')) {
        dustAmountValue = 0.15;
      }
      break;
  }

  if (!animatedDecorationsAllowed) {
    leafAmountValue = 0;
    dustAmountValue = 0;
    lightningAmountValue = 0;
  }

  return {
    skyPreset: skyPresetFor(weather),
    ambientTone: ambientToneFor(weather.dayPhase, weather.condition),
    ambientOpacity: ambientOpacityFor(weather.dayPhase, weather.condition),
    cloudDensity: clamp(cloudDensityValue),
    fogDensity: clamp(fogDensityValue),
    rainAmount: clamp(rainAmountValue),
    rainAngleDeg: rainAngleFromDirection(weather.windDirectionDeg),
    snowAmount: clamp(snowAmountValue),
    windStrength: clamp(windStrength),
    leafAmount: clamp(leafAmountValue),
    dustAmount: clamp(dustAmountValue),
    lightningAmount: clamp(lightningAmountValue),
    transitionDurationMs: transitionDurationFor(weather, reducedMotion, performanceTier),
    performanceTier,
    reducedMotion,
    safetyEmphasis: weather.officialAlert !== null || (weather.condition === 'storm' && weather.intensity >= 2),
  };
}
