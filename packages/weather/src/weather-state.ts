export type WeatherCondition = 'clear' | 'cloudy' | 'rain' | 'storm' | 'fog' | 'snow' | 'wind';
export type WeatherIntensity = 0 | 1 | 2 | 3;
export type WeatherDayPhase = 'day' | 'golden_hour' | 'dusk' | 'night';

export type WeatherOfficialAlert = {
  source: 'AEMET';
  level: 'yellow' | 'orange' | 'red' | 'unknown';
  title: string;
  description: string | null;
  startsAt: string | null;
  endsAt: string | null;
} | null;

export type WeatherState = {
  provider: 'Open-Meteo' | 'AEMET OpenData';
  condition: WeatherCondition;
  intensity: WeatherIntensity;
  temperatureC: number | null;
  feelsLikeC: number | null;
  windSpeedKmh: number | null;
  windDirectionDeg: number | null;
  windGustsKmh: number | null;
  precipitationMm: number | null;
  visibilityM: number | null;
  cloudCoverPercent: number | null;
  weatherCode: number | null;
  dayPhase: WeatherDayPhase;
  observedAt: string | null;
  expiresAt: string | null;
  officialAlert: WeatherOfficialAlert;
  stale: boolean;
};

export type WeatherStateInput = {
  provider?: WeatherState['provider'];
  weatherCode?: number | null;
  isDay?: boolean | null;
  temperatureC?: number | null;
  apparentTemperatureC?: number | null;
  precipitationMm?: number | null;
  rainMm?: number | null;
  snowfallCm?: number | null;
  cloudCoverPercent?: number | null;
  windSpeedKmh?: number | null;
  windDirectionDeg?: number | null;
  windGustsKmh?: number | null;
  visibilityM?: number | null;
  observedAt?: string | null;
  expiresAt?: string | null;
  officialAlert?: WeatherOfficialAlert;
  stale?: boolean;
};

type OpenMeteoPayload = {
  latitude?: unknown;
  longitude?: unknown;
  current?: {
    time?: unknown;
    temperature_2m?: unknown;
    apparent_temperature?: unknown;
    is_day?: unknown;
    precipitation?: unknown;
    rain?: unknown;
    snowfall?: unknown;
    weather_code?: unknown;
    cloud_cover?: unknown;
    wind_speed_10m?: unknown;
    wind_direction_10m?: unknown;
    wind_gusts_10m?: unknown;
    visibility?: unknown;
  };
};

function finite(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function hasCode(code: number | null, values: readonly number[]) {
  return code !== null && values.includes(code);
}

function codeBetween(code: number | null, min: number, max: number) {
  return code !== null && code >= min && code <= max;
}

function inferCondition(input: WeatherStateInput): WeatherCondition {
  const code = finite(input.weatherCode);
  const precipitation = Math.max(finite(input.precipitationMm) ?? 0, finite(input.rainMm) ?? 0);
  const snow = finite(input.snowfallCm) ?? 0;
  const visibility = finite(input.visibilityM);
  const wind = finite(input.windSpeedKmh) ?? 0;
  const gusts = finite(input.windGustsKmh) ?? 0;
  const cloud = finite(input.cloudCoverPercent) ?? 0;

  if (codeBetween(code, 95, 99)) return 'storm';
  if (hasCode(code, [45, 48]) || (visibility !== null && visibility < 1000)) return 'fog';
  if (codeBetween(code, 71, 77) || codeBetween(code, 85, 86) || snow > 0) return 'snow';
  if (codeBetween(code, 51, 67) || codeBetween(code, 80, 82) || precipitation > 0) return 'rain';
  if (wind >= 35 || gusts >= 50) return 'wind';
  if (hasCode(code, [2, 3]) || cloud >= 60) return 'cloudy';
  return 'clear';
}

function inferIntensity(condition: WeatherCondition, input: WeatherStateInput): WeatherIntensity {
  const precipitation = Math.max(finite(input.precipitationMm) ?? 0, finite(input.rainMm) ?? 0);
  const snow = finite(input.snowfallCm) ?? 0;
  const visibility = finite(input.visibilityM);
  const wind = finite(input.windSpeedKmh) ?? 0;
  const gusts = finite(input.windGustsKmh) ?? 0;
  const cloud = finite(input.cloudCoverPercent) ?? 0;

  if (condition === 'clear') return 0;
  if (condition === 'storm') return precipitation >= 5 || gusts >= 60 ? 3 : 2;
  if (condition === 'rain') return precipitation >= 5 ? 3 : precipitation >= 1 ? 2 : 1;
  if (condition === 'snow') return snow >= 2 ? 3 : snow >= 0.5 ? 2 : 1;
  if (condition === 'fog') return visibility !== null && visibility < 200 ? 3 : visibility !== null && visibility < 1000 ? 2 : 1;
  if (condition === 'wind') return wind >= 60 || gusts >= 80 ? 3 : wind >= 40 || gusts >= 60 ? 2 : 1;
  return cloud >= 90 ? 2 : 1;
}

function expiryFromObservedAt(observedAt: string | null) {
  if (!observedAt) return null;
  const normalized = /(?:Z|[+-]\d\d:\d\d)$/.test(observedAt) ? observedAt : `${observedAt}Z`;
  const instant = new Date(normalized);
  if (Number.isNaN(instant.getTime())) return null;
  return new Date(instant.getTime() + 15 * 60 * 1000).toISOString();
}

export function classifyWeatherState(input: WeatherStateInput): WeatherState {
  const condition = inferCondition(input);
  const observedAt = typeof input.observedAt === 'string' && input.observedAt.trim() ? input.observedAt : null;
  return {
    provider: input.provider ?? 'Open-Meteo',
    condition,
    intensity: inferIntensity(condition, input),
    temperatureC: finite(input.temperatureC),
    feelsLikeC: finite(input.apparentTemperatureC),
    windSpeedKmh: finite(input.windSpeedKmh),
    windDirectionDeg: finite(input.windDirectionDeg),
    windGustsKmh: finite(input.windGustsKmh),
    precipitationMm: finite(input.precipitationMm),
    visibilityM: finite(input.visibilityM),
    cloudCoverPercent: finite(input.cloudCoverPercent),
    weatherCode: finite(input.weatherCode),
    dayPhase: input.isDay === false ? 'night' : 'day',
    observedAt,
    expiresAt: input.expiresAt ?? expiryFromObservedAt(observedAt),
    officialAlert: input.officialAlert ?? null,
    stale: input.stale ?? false,
  };
}

export function validateWeatherCoordinates(latitude: number, longitude: number) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    throw new Error('INVALID_WEATHER_COORDINATES');
  }
  return { latitude, longitude };
}

export function parseOpenMeteoCurrentWeather(payload: unknown): WeatherState {
  const root = (payload ?? {}) as OpenMeteoPayload;
  validateWeatherCoordinates(Number(root.latitude), Number(root.longitude));
  const current = root.current ?? {};
  const observedAt = typeof current.time === 'string' ? current.time : null;
  return classifyWeatherState({
    provider: 'Open-Meteo',
    weatherCode: finite(current.weather_code),
    isDay: finite(current.is_day) === 1,
    temperatureC: finite(current.temperature_2m),
    apparentTemperatureC: finite(current.apparent_temperature),
    precipitationMm: finite(current.precipitation),
    rainMm: finite(current.rain),
    snowfallCm: finite(current.snowfall),
    cloudCoverPercent: finite(current.cloud_cover),
    windSpeedKmh: finite(current.wind_speed_10m),
    windDirectionDeg: finite(current.wind_direction_10m),
    windGustsKmh: finite(current.wind_gusts_10m),
    visibilityM: finite(current.visibility),
    observedAt,
  });
}
