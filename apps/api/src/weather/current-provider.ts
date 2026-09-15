import {
  fetchAemetOfficialAlertForCoordinates,
  fetchOpenMeteoCurrentWeather,
  validateWeatherCoordinates,
  type WeatherOfficialAlert,
  type WeatherState,
} from '@magina/weather';

const CURRENT_WEATHER_TTL_MS = 10 * 60 * 1000;

type CacheEntry = {
  weather: WeatherState;
  fetchedAt: Date;
  expiresAt: Date;
};

export type CurrentWeatherResult = {
  weather: WeatherState;
  cacheStatus: 'fresh' | 'refreshed' | 'stale';
  fetchedAt: string;
};

export interface CurrentWeatherProvider {
  currentWeather(latitude: number, longitude: number): Promise<CurrentWeatherResult>;
}

function privacyCacheKey(latitude: number, longitude: number) {
  return `${latitude.toFixed(3)},${longitude.toFixed(3)}`;
}

async function optionalOfficialAlert(latitude: number, longitude: number): Promise<WeatherOfficialAlert> {
  try {
    return await fetchAemetOfficialAlertForCoordinates(latitude, longitude, '61');
  } catch {
    return null;
  }
}

export class OpenMeteoCurrentWeatherProvider implements CurrentWeatherProvider {
  private readonly cache = new Map<string, CacheEntry>();

  async currentWeather(latitude: number, longitude: number): Promise<CurrentWeatherResult> {
    const coordinates = validateWeatherCoordinates(latitude, longitude);
    const key = privacyCacheKey(coordinates.latitude, coordinates.longitude);
    const existing = this.cache.get(key) ?? null;
    const now = new Date();

    if (existing && existing.expiresAt.getTime() > now.getTime()) {
      return {
        weather: { ...existing.weather, stale: false },
        cacheStatus: 'fresh',
        fetchedAt: existing.fetchedAt.toISOString(),
      };
    }

    try {
      const [weather, officialAlert] = await Promise.all([
        fetchOpenMeteoCurrentWeather(coordinates.latitude, coordinates.longitude),
        optionalOfficialAlert(coordinates.latitude, coordinates.longitude),
      ]);
      const entry: CacheEntry = {
        weather: { ...weather, officialAlert, stale: false },
        fetchedAt: now,
        expiresAt: new Date(now.getTime() + CURRENT_WEATHER_TTL_MS),
      };
      this.cache.set(key, entry);
      return {
        weather: entry.weather,
        cacheStatus: 'refreshed',
        fetchedAt: now.toISOString(),
      };
    } catch (error) {
      if (existing) {
        return {
          weather: { ...existing.weather, stale: true },
          cacheStatus: 'stale',
          fetchedAt: existing.fetchedAt.toISOString(),
        };
      }
      throw error;
    }
  }
}

export const remoteOpenMeteoCurrentWeatherProvider: CurrentWeatherProvider = new OpenMeteoCurrentWeatherProvider();
