import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import {
  AGRONOMY_RULE_VERSION,
  evaluateWeatherDayForTask,
  normalizeAgronomyTask,
} from '../domain/agronomy-advisory.js';
import {
  RADAR_AGRONOMY_RULE_VERSION,
  combineAgronomySignals,
  evaluateRadarObservationForTask,
  type RadarAgronomyObservation,
} from '../domain/agronomy-radar.js';

type Target = { municipality_id: string | null; municipality_name: string | null; aemet_code: string | null };
type RadarRow = {
  observation_id: string;
  observed_at: Date | string;
  coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable';
  precipitation_detected: boolean | null;
  nearest_echo_distance_km: number | string | null;
  reflectivity_dbz_max: number | string | null;
  quality_flags: string[] | null;
};

function finiteNumber(value: unknown) {
  if (value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

async function latestRadarObservation(database: DatabaseClient, workspaceId: string, fieldId: string): Promise<RadarAgronomyObservation | null> {
  const result = await sql<RadarRow>`
    SELECT id AS observation_id, observed_at, coverage_status, precipitation_detected,
           nearest_echo_distance_km, reflectivity_dbz_max, quality_flags
    FROM farm_radar_observations
    WHERE workspace_id = ${workspaceId}::uuid
      AND field_id = ${fieldId}::uuid
    ORDER BY observed_at DESC, created_at DESC
    LIMIT 1
  `.execute(database);
  const row = result.rows[0];
  if (!row) return null;
  return {
    observationId: row.observation_id,
    observedAt: row.observed_at,
    coverageStatus: row.coverage_status,
    precipitationDetected: row.precipitation_detected,
    nearestEchoDistanceKm: finiteNumber(row.nearest_echo_distance_km),
    reflectivityDbzMax: finiteNumber(row.reflectivity_dbz_max),
    qualityFlags: row.quality_flags ?? [],
  };
}

export function registerAgronomyRoutes(app: FastifyInstance, db: DatabaseClient | null, provider: MunicipalityWeatherProvider) {
  app.get('/api/v1/fields/:fieldId/agronomy/advisory', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId) return reply.code(400).send({ error: 'invalid_field_id' });

    const query = request.query as { date?: string; task?: string };
    const targetDate = query.date?.trim() || new Date().toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) return reply.code(400).send({ error: 'invalid_date' });
    const task = normalizeAgronomyTask(query.task);

    const targetResult = await sql<Target>`
      SELECT m.id AS municipality_id, m.name AS municipality_name, m.aemet_code
      FROM fields f
      LEFT JOIN territory_municipalities m ON m.id = f.municipality_id
      WHERE f.id = ${fieldId}::uuid
        AND f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
      LIMIT 1
    `.execute(database);
    const target = targetResult.rows[0];
    if (!target) return reply.code(404).send({ error: 'field_not_found' });
    if (!target.municipality_id || !target.aemet_code) return reply.code(409).send({ error: 'field_weather_context_unavailable' });

    const cached = await getCachedMunicipalityForecast(database, provider, target.municipality_id, target.aemet_code);
    const day = cached.forecast.days.find((item) => item.date.slice(0, 10) === targetDate) ?? cached.forecast.days[0];
    if (!day) return reply.code(409).send({ error: 'forecast_day_unavailable' });

    const forecastAdvisory = evaluateWeatherDayForTask(day, task);
    const radarObservation = await latestRadarObservation(database, context.workspaceId, fieldId);
    const radarAdvisory = evaluateRadarObservationForTask(radarObservation, task);
    const combined = combineAgronomySignals(forecastAdvisory, radarAdvisory);

    return {
      field_id: fieldId,
      date: targetDate,
      task,
      suitability: combined.suitability,
      risk_level: combined.riskLevel,
      title: combined.suitability === 'avoid'
        ? 'Condiciones poco favorables'
        : combined.suitability === 'caution'
          ? 'Conviene revisar las condiciones'
          : 'Sin alertas meteorológicas destacadas',
      summary: combined.summary,
      reasons: forecastAdvisory.reasons,
      evidence: {
        forecast: {
          source: cached.forecast.provider,
          municipality: target.municipality_name,
          forecast_date: day.date,
          precipitation_probability_percent: day.precipitationProbabilityPercent,
          wind_max_kmh: day.windMaxKmh,
          temperature_min_c: day.temperatureMinC,
          temperature_max_c: day.temperatureMaxC,
          fetched_at: cached.fetchedAt,
          stale: cached.cacheStatus === 'stale',
        },
        radar: radarObservation ? {
          observation_id: radarObservation.observationId,
          observed_at: radarObservation.observedAt,
          coverage_status: radarObservation.coverageStatus,
          precipitation_detected: radarObservation.precipitationDetected,
          nearest_echo_distance_km: radarObservation.nearestEchoDistanceKm,
          reflectivity_dbz_max: radarObservation.reflectivityDbzMax,
          quality_flags: radarObservation.qualityFlags,
          fresh: radarAdvisory.fresh,
          age_minutes: radarAdvisory.ageMinutes,
          summary: radarAdvisory.summary,
        } : null,
      },
      confidence: cached.cacheStatus === 'fresh' && (!radarObservation || radarAdvisory.fresh) ? 'medium' : 'low',
      rule_version: `${AGRONOMY_RULE_VERSION}+${RADAR_AGRONOMY_RULE_VERSION}`,
      radar_elevated: combined.radarElevated,
      requires_user_judgement: true,
      semantics: {
        forecast: 'daily_forecast',
        radar: 'observed_reflectivity_not_eta',
      },
      disclaimer: 'Contexto orientativo. El radar confirma observación, no predice hora de llegada. No sustituye la etiqueta del producto, la normativa aplicable ni el criterio técnico del usuario.',
    };
  });
}
