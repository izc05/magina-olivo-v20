import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import { evaluateWeatherDayForTask, normalizeAgronomyTask } from '../domain/agronomy-advisory.js';
import { combineAgronomySignals, evaluateRadarObservationForTask, type RadarAgronomyObservation } from '../domain/agronomy-radar.js';

type AttentionRow = {
  event_id: string;
  field_id: string;
  field_name: string;
  title: string;
  scheduled_at: string;
  source_domain_type: string | null;
  municipality_id: string | null;
  municipality_name: string | null;
  aemet_code: string | null;
  observation_id: string | null;
  observed_at: Date | string | null;
  coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable' | null;
  precipitation_detected: boolean | null;
  nearest_echo_distance_km: number | string | null;
  reflectivity_dbz_max: number | string | null;
  quality_flags: string[] | null;
};

function finite(value: unknown) {
  if (value == null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function registerAttentionRoutes(app: FastifyInstance, db: DatabaseClient | null, provider: MunicipalityWeatherProvider) {
  app.get('/api/v1/attention', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const query = request.query as { fieldId?: string; limit?: string };
    const fieldId = query.fieldId?.trim() || null;
    const parsedLimit = Number(query.limit ?? 6);
    const limit = Number.isFinite(parsedLimit) ? Math.max(1, Math.min(12, Math.trunc(parsedLimit))) : 6;

    const result = await sql<AttentionRow>`
      SELECT
        se.id AS event_id,
        se.field_id,
        f.name AS field_name,
        se.title,
        se.scheduled_at,
        se.source_domain_type,
        m.id AS municipality_id,
        m.name AS municipality_name,
        m.aemet_code,
        ro.id AS observation_id,
        ro.observed_at,
        ro.coverage_status,
        ro.precipitation_detected,
        ro.nearest_echo_distance_km,
        ro.reflectivity_dbz_max,
        ro.quality_flags
      FROM scheduled_events se
      JOIN fields f ON f.id = se.field_id AND f.workspace_id = se.workspace_id
      LEFT JOIN territory_municipalities m ON m.id = f.municipality_id
      LEFT JOIN LATERAL (
        SELECT fro.*
        FROM farm_radar_observations fro
        WHERE fro.field_id = f.id AND fro.workspace_id = f.workspace_id
        ORDER BY fro.observed_at DESC, fro.created_at DESC
        LIMIT 1
      ) ro ON true
      WHERE se.workspace_id = ${context.workspaceId}::uuid
        AND se.status IN ('planned','postponed')
        AND se.field_id IS NOT NULL
        AND se.scheduled_at >= now() - interval '1 day'
        AND se.scheduled_at < now() + interval '8 days'
        AND (${fieldId}::text IS NULL OR se.field_id = ${fieldId}::uuid)
      ORDER BY CASE WHEN se.scheduled_at < now() THEN 0 ELSE 1 END, se.scheduled_at ASC
      LIMIT ${limit}
    `.execute(database);

    const items = [] as Array<Record<string, unknown>>;
    for (const row of result.rows) {
      const task = normalizeAgronomyTask(row.source_domain_type ?? undefined);
      let advisory: Record<string, unknown> | null = null;

      if (row.municipality_id && row.aemet_code) {
        try {
          const cached = await getCachedMunicipalityForecast(database, provider, row.municipality_id, row.aemet_code);
          const targetDate = row.scheduled_at.slice(0, 10);
          const day = cached.forecast.days.find((candidate) => candidate.date.slice(0, 10) === targetDate);
          if (day) {
            const forecast = evaluateWeatherDayForTask(day, task);
            const radarObservation: RadarAgronomyObservation | null = row.observation_id && row.observed_at && row.coverage_status
              ? {
                  observationId: row.observation_id,
                  observedAt: row.observed_at,
                  coverageStatus: row.coverage_status,
                  precipitationDetected: row.precipitation_detected,
                  nearestEchoDistanceKm: finite(row.nearest_echo_distance_km),
                  reflectivityDbzMax: finite(row.reflectivity_dbz_max),
                  qualityFlags: row.quality_flags ?? [],
                }
              : null;
            const radar = evaluateRadarObservationForTask(radarObservation, task);
            const combined = combineAgronomySignals(forecast, radar);
            advisory = {
              suitability: combined.suitability,
              risk_level: combined.riskLevel,
              summary: combined.summary,
              stale: cached.cacheStatus !== 'fresh',
              radar_elevated: combined.radarElevated,
              forecast: {
                source: cached.forecast.provider,
                precipitation_probability_percent: day.precipitationProbabilityPercent,
                wind_max_kmh: day.windMaxKmh,
              },
              radar: radarObservation ? {
                observed_at: radarObservation.observedAt,
                fresh: radar.fresh,
                age_minutes: radar.ageMinutes,
                precipitation_detected: radarObservation.precipitationDetected,
                nearest_echo_distance_km: radarObservation.nearestEchoDistanceKm,
                reflectivity_dbz_max: radarObservation.reflectivityDbzMax,
              } : null,
            };
          }
        } catch (error) {
          request.log.warn({ err: error, eventId: row.event_id }, 'Unable to enrich attention item');
        }
      }

      items.push({
        id: row.event_id,
        field_id: row.field_id,
        field_name: row.field_name,
        title: row.title,
        scheduled_at: row.scheduled_at,
        source_domain_type: row.source_domain_type,
        overdue: new Date(row.scheduled_at).getTime() < Date.now(),
        advisory,
      });
    }

    const important = items.filter((item) => {
      const advisory = item.advisory as { suitability?: string } | null;
      return Boolean(item.overdue) || advisory?.suitability === 'avoid' || advisory?.suitability === 'caution';
    }).length;

    return {
      generated_at: new Date().toISOString(),
      field_id: fieldId,
      counts: { total: items.length, important },
      items,
      semantics: 'compact_projection_from_schedule_forecast_and_observed_radar',
    };
  });
}
