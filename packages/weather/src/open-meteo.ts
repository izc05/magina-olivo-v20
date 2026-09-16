import { parseOpenMeteoCurrentWeather, validateWeatherCoordinates, type WeatherState } from './weather-state.js';

const OPEN_METEO_BASE_URL = 'https://api.open-meteo.com/v1/forecast';
const OPEN_METEO_TIMEOUT_MS = 8_000;

const CURRENT_FIELDS = [
  'temperature_2m',
  'apparent_temperature',
  'is_day',
  'precipitation',
  'rain',
  'snowfall',
  'weather_code',
  'cloud_cover',
  'wind_speed_10m',
  'wind_direction_10m',
  'wind_gusts_10m',
  'visibility',
].join(',');

export function buildOpenMeteoCurrentWeatherUrl(latitude: number, longitude: number) {
  const coordinates = validateWeatherCoordinates(latitude, longitude);
  const url = new URL(OPEN_METEO_BASE_URL);
  url.searchParams.set('latitude', String(coordinates.latitude));
  url.searchParams.set('longitude', String(coordinates.longitude));
  url.searchParams.set('current', CURRENT_FIELDS);
  url.searchParams.set('wind_speed_unit', 'kmh');
  url.searchParams.set('precipitation_unit', 'mm');
  url.searchParams.set('timezone', 'auto');
  return url;
}

export async function fetchOpenMeteoCurrentWeather(latitude: number, longitude: number): Promise<WeatherState> {
  const url = buildOpenMeteoCurrentWeatherUrl(latitude, longitude);
  const response = await fetch(url, {
    headers: {
      accept: 'application/json',
      'user-agent': 'Magina-Olivo-V20/1.0 (+adventure-weather)',
    },
    signal: AbortSignal.timeout(OPEN_METEO_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`OPEN_METEO_REQUEST_FAILED:${response.status}`);
  const payload = await response.json() as unknown;
  return parseOpenMeteoCurrentWeather(payload);
}
