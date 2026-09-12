import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import type { MunicipalityForecast } from './aemet.js';
import type { MunicipalityWeatherProvider } from './providers.js';

const FORECAST_CACHE_TTL_MS = 30 * 60 * 1000;

type CacheRow = {
  payload_json: MunicipalityForecast;
  fetched_at: Date;
  expires_at: Date;
};

export type ForecastResult = {
  forecast: MunicipalityForecast;
  cacheStatus: 'fresh' | 'refreshed' | 'stale';
  fetchedAt: string;
};

async function readCache(db: DatabaseClient, municipalityId: string) {
  const result = await sql<CacheRow>`
    SELECT payload_json, fetched_at, expires_at
    FROM weather_forecast_cache
    WHERE municipality_id = ${municipalityId}::uuid
      AND provider = 'aemet_daily'
  `.execute(db);
  return result.rows[0] ?? null;
}

async function persistForecast(
  db: DatabaseClient,
  municipalityId: string,
  forecast: MunicipalityForecast,
  fetchedAt: Date,
) {
  const expiresAt = new Date(fetchedAt.getTime() + FORECAST_CACHE_TTL_MS);
  await sql`
    INSERT INTO weather_forecast_cache (
      municipality_id, provider, payload_json, fetched_at, expires_at, last_error_at, last_error_code
    ) VALUES (
      ${municipalityId}::uuid, 'aemet_daily', ${JSON.stringify(forecast)}::jsonb,
      ${fetchedAt}, ${expiresAt}, NULL, NULL
    )
    ON CONFLICT (municipality_id, provider)
    DO UPDATE SET
      payload_json = EXCLUDED.payload_json,
      fetched_at = EXCLUDED.fetched_at,
      expires_at = EXCLUDED.expires_at,
      last_error_at = NULL,
      last_error_code = NULL
  `.execute(db);
}

async function markFailure(db: DatabaseClient, municipalityId: string, error: unknown) {
  const errorCode = error instanceof Error ? error.message.slice(0, 160) : 'UNKNOWN_WEATHER_ERROR';
  await sql`
    UPDATE weather_forecast_cache
    SET last_error_at = now(), last_error_code = ${errorCode}
    WHERE municipality_id = ${municipalityId}::uuid
      AND provider = 'aemet_daily'
  `.execute(db);
}

export async function getCachedMunicipalityForecast(
  db: DatabaseClient,
  provider: MunicipalityWeatherProvider,
  municipalityId: string,
  aemetCode: string,
): Promise<ForecastResult> {
  const existing = await readCache(db, municipalityId);
  const now = new Date();

  if (existing && existing.expires_at.getTime() > now.getTime()) {
    return {
      forecast: existing.payload_json,
      cacheStatus: 'fresh',
      fetchedAt: existing.fetched_at.toISOString(),
    };
  }

  try {
    const forecast = await provider.dailyForecast(aemetCode);
    await persistForecast(db, municipalityId, forecast, now);
    return { forecast, cacheStatus: 'refreshed', fetchedAt: now.toISOString() };
  } catch (error) {
    if (existing) {
      await markFailure(db, municipalityId, error);
      return {
        forecast: existing.payload_json,
        cacheStatus: 'stale',
        fetchedAt: existing.fetched_at.toISOString(),
      };
    }
    throw error;
  }
}
