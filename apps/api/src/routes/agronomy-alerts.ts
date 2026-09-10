import type { FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import type { NotificationDispatchQueuePort } from '../notifications/port.js';
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

const preferencesSchema = z.object({
  enabled: z.boolean(),
  notify_caution: z.boolean().default(false),
  notify_avoid: z.boolean().default(true),
  lead_hours: z.number().int().min(1).max(168).default(24),
});

type Candidate = {
  event_id: string;
  field_id: string;
  field_name: string;
  source_domain_type: string | null;
  title: string;
  scheduled_at: string;
  municipality_id: string | null;
  municipality_name: string | null;
  aemet_code: string | null;
};

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
    WHERE workspace_id = ${workspaceId}::uuid AND field_id = ${fieldId}::uuid
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

export function registerAgronomyAlertRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  provider: MunicipalityWeatherProvider,
  queue: NotificationDispatchQueuePort,
) {
  app.get('/api/v1/agronomy-alerts/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql`
      SELECT enabled, notify_caution, notify_avoid, lead_hours
      FROM agronomy_alert_preferences
      WHERE user_id = ${context.userId}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    return result.rows[0] ?? { enabled: false, notify_caution: false, notify_avoid: true, lead_hours: 24 };
  });

  app.put('/api/v1/agronomy-alerts/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = preferencesSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_preferences', issues: parsed.error.issues });
    const input = parsed.data;
    const result = await sql`
      INSERT INTO agronomy_alert_preferences (user_id, workspace_id, enabled, notify_caution, notify_avoid, lead_hours)
      VALUES (${context.userId}::uuid, ${context.workspaceId}::uuid, ${input.enabled}, ${input.notify_caution}, ${input.notify_avoid}, ${input.lead_hours})
      ON CONFLICT (user_id, workspace_id)
      DO UPDATE SET enabled = EXCLUDED.enabled,
                    notify_caution = EXCLUDED.notify_caution,
                    notify_avoid = EXCLUDED.notify_avoid,
                    lead_hours = EXCLUDED.lead_hours,
                    updated_at = now()
      RETURNING enabled, notify_caution, notify_avoid, lead_hours
    `.execute(database);
    return result.rows[0];
  });

  app.post('/api/v1/agronomy-alerts/evaluate', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const preferencesResult = await sql<{
      enabled: boolean;
      notify_caution: boolean;
      notify_avoid: boolean;
      lead_hours: number;
    }>`
      SELECT enabled, notify_caution, notify_avoid, lead_hours
      FROM agronomy_alert_preferences
      WHERE user_id = ${context.userId}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const preferences = preferencesResult.rows[0];
    if (!preferences?.enabled) return { evaluated: 0, created: 0, skipped: 0, reason: 'alerts_not_enabled' };

    const candidates = await sql<Candidate>`
      SELECT se.id AS event_id, se.field_id, f.name AS field_name, se.source_domain_type,
             se.title, se.scheduled_at,
             m.id AS municipality_id, m.name AS municipality_name, m.aemet_code
      FROM scheduled_events se
      JOIN fields f ON f.id = se.field_id AND f.workspace_id = se.workspace_id
      LEFT JOIN territory_municipalities m ON m.id = f.municipality_id
      WHERE se.workspace_id = ${context.workspaceId}::uuid
        AND se.status IN ('planned','postponed')
        AND se.field_id IS NOT NULL
        AND se.source_domain_type IN ('treatment','irrigation','pruning','harvest','harvest_delivery','work')
        AND se.scheduled_at >= now() - interval '2 hours'
        AND se.scheduled_at <= now() + (${preferences.lead_hours}::text || ' hours')::interval
      ORDER BY se.scheduled_at ASC
      LIMIT 100
    `.execute(database);

    let created = 0;
    let skipped = 0;
    const queuedIntentIds: string[] = [];
    const combinedRuleVersion = `${AGRONOMY_RULE_VERSION}+${RADAR_AGRONOMY_RULE_VERSION}`;

    for (const candidate of candidates.rows) {
      if (!candidate.municipality_id || !candidate.aemet_code) { skipped += 1; continue; }
      try {
        const cached = await getCachedMunicipalityForecast(database, provider, candidate.municipality_id, candidate.aemet_code);
        if (cached.cacheStatus !== 'fresh') { skipped += 1; continue; }
        const targetDate = candidate.scheduled_at.slice(0, 10);
        const day = cached.forecast.days.find((item) => item.date.slice(0, 10) === targetDate);
        if (!day) { skipped += 1; continue; }
        const task = normalizeAgronomyTask(candidate.source_domain_type ?? undefined);
        const forecastAdvisory = evaluateWeatherDayForTask(day, task);
        const radarObservation = await latestRadarObservation(database, context.workspaceId, candidate.field_id);
        const radarAdvisory = evaluateRadarObservationForTask(radarObservation, task);
        const advisory = combineAgronomySignals(forecastAdvisory, radarAdvisory);
        const shouldNotify = advisory.suitability === 'avoid'
          ? preferences.notify_avoid
          : advisory.suitability === 'caution' && preferences.notify_caution;
        if (!shouldNotify) { skipped += 1; continue; }

        const dedupeKey = `agronomy:${context.userId}:${candidate.event_id}:${targetDate}:${combinedRuleVersion}:${advisory.suitability}`;
        const intentId = randomUUID();
        const inserted = await sql<{ id: string }>`
          INSERT INTO notification_intents (
            id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
            title, body, payload_json, dedupe_key, status
          ) VALUES (
            ${intentId}::uuid, ${context.userId}::uuid, ${context.workspaceId}::uuid, ${candidate.field_id}::uuid,
            'agronomy_task_warning', 'push', 'scheduled_event', ${candidate.event_id}::uuid,
            ${`Mágina · ${candidate.field_name}`},
            ${`${candidate.title}: ${advisory.summary}`},
            ${JSON.stringify({
              path: 'mi-campo/hoy/',
              scheduled_event_id: candidate.event_id,
              task,
              suitability: advisory.suitability,
              risk_level: advisory.riskLevel,
              rule_version: combinedRuleVersion,
              forecast_date: targetDate,
              forecast_source: cached.forecast.provider,
              radar_observation_id: radarObservation?.observationId ?? null,
              radar_observed_at: radarObservation?.observedAt ?? null,
              radar_fresh: radarAdvisory.fresh,
              radar_elevated: advisory.radarElevated,
              semantics: { forecast: 'daily_forecast', radar: 'observed_reflectivity_not_eta' },
            })}::jsonb,
            ${dedupeKey}, 'pending'
          )
          ON CONFLICT (dedupe_key) DO NOTHING
          RETURNING id
        `.execute(database);
        const id = inserted.rows[0]?.id;
        if (!id) { skipped += 1; continue; }
        created += 1;
        queuedIntentIds.push(id);
        try {
          await queue.enqueue({ version: 1, intent_id: id, limit: 8 });
        } catch (error) {
          request.log.warn({ err: error, intentId: id }, 'Agronomy alert dispatch queue unavailable; dispatcher may pick up pending intent later');
        }
      } catch (error) {
        request.log.warn({ err: error, eventId: candidate.event_id }, 'Unable to evaluate agronomy alert candidate');
        skipped += 1;
      }
    }

    return { evaluated: candidates.rows.length, created, skipped, intent_ids: queuedIntentIds, rule_version: combinedRuleVersion };
  });
}
