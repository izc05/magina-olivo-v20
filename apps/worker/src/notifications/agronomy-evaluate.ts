import { randomUUID } from 'node:crypto';
import type { Pool } from 'pg';
import type { AgronomyAlertEvaluateJobPayload } from '@magina/contracts';
import {
  AGRONOMY_RULE_VERSION,
  RADAR_AGRONOMY_RULE_VERSION,
  combineAgronomySignals,
  evaluateRadarObservationForTask,
  evaluateWeatherDayForTask,
  fetchAemetDailyForecast,
  normalizeAgronomyTask,
  type MunicipalityForecast,
  type RadarAgronomyObservation,
} from '@magina/weather';

const ruleVersion = `${AGRONOMY_RULE_VERSION}+${RADAR_AGRONOMY_RULE_VERSION}`;

type PreferenceRow = {
  user_id: string;
  workspace_id: string;
  notify_caution: boolean;
  notify_avoid: boolean;
  lead_hours: number;
};

type CandidateRow = {
  event_id: string;
  field_id: string;
  field_name: string;
  title: string;
  source_domain_type: string | null;
  scheduled_at: Date | string;
  aemet_code: string | null;
};

type RadarRow = {
  id: string;
  observed_at: Date | string;
  coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable';
  precipitation_detected: boolean | null;
  nearest_echo_distance_km: string | number | null;
  reflectivity_dbz_max: string | number | null;
  quality_flags: string[] | null;
};

function dateOnly(value: Date | string) {
  return (value instanceof Date ? value.toISOString() : value).slice(0, 10);
}

function finite(value: unknown) {
  if (value == null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

async function latestRadar(pool: Pool, workspaceId: string, fieldId: string): Promise<RadarAgronomyObservation | null> {
  const result = await pool.query<RadarRow>(`
    SELECT id, observed_at, coverage_status, precipitation_detected,
           nearest_echo_distance_km, reflectivity_dbz_max, quality_flags
    FROM farm_radar_observations
    WHERE workspace_id = $1::uuid AND field_id = $2::uuid
    ORDER BY observed_at DESC, created_at DESC
    LIMIT 1
  `, [workspaceId, fieldId]);
  const row = result.rows[0];
  if (!row) return null;
  return {
    observationId: row.id,
    observedAt: row.observed_at,
    coverageStatus: row.coverage_status,
    precipitationDetected: row.precipitation_detected,
    nearestEchoDistanceKm: finite(row.nearest_echo_distance_km),
    reflectivityDbzMax: finite(row.reflectivity_dbz_max),
    qualityFlags: row.quality_flags ?? [],
  };
}

export async function runAgronomyAlertEvaluationJob(pool: Pool, payload: AgronomyAlertEvaluateJobPayload) {
  const preferences = await pool.query<PreferenceRow>(`
    SELECT aap.user_id, aap.workspace_id, aap.notify_caution, aap.notify_avoid, aap.lead_hours
    FROM agronomy_alert_preferences aap
    JOIN users u ON u.id = aap.user_id AND u.status = 'active'
    JOIN workspace_memberships wm
      ON wm.user_id = aap.user_id
     AND wm.workspace_id = aap.workspace_id
     AND wm.status = 'active'
    WHERE aap.enabled = TRUE
    ORDER BY aap.updated_at ASC
    LIMIT $1
  `, [payload.limit_users]);

  const forecastCache = new Map<string, MunicipalityForecast | null>();
  let evaluated = 0;
  let created = 0;
  let skipped = 0;

  for (const preference of preferences.rows) {
    const candidates = await pool.query<CandidateRow>(`
      SELECT se.id AS event_id, se.field_id, f.name AS field_name, se.title,
             se.source_domain_type, se.scheduled_at, tm.aemet_code
      FROM scheduled_events se
      JOIN fields f ON f.id = se.field_id AND f.workspace_id = se.workspace_id
      LEFT JOIN territory_municipalities tm ON tm.id = f.municipality_id
      WHERE se.workspace_id = $1::uuid
        AND se.status IN ('planned','postponed')
        AND se.field_id IS NOT NULL
        AND se.source_domain_type IN ('treatment','irrigation','pruning','harvest','harvest_delivery','work')
        AND se.scheduled_at >= now() - interval '2 hours'
        AND se.scheduled_at <= now() + ($2::text || ' hours')::interval
      ORDER BY se.scheduled_at ASC
      LIMIT 100
    `, [preference.workspace_id, preference.lead_hours]);

    for (const candidate of candidates.rows) {
      evaluated += 1;
      if (!candidate.aemet_code) { skipped += 1; continue; }

      let forecast = forecastCache.get(candidate.aemet_code);
      if (forecast === undefined) {
        try {
          forecast = await fetchAemetDailyForecast(candidate.aemet_code);
          forecastCache.set(candidate.aemet_code, forecast);
        } catch {
          forecast = null;
          forecastCache.set(candidate.aemet_code, null);
        }
      }
      if (!forecast) { skipped += 1; continue; }

      const targetDate = dateOnly(candidate.scheduled_at);
      const day = forecast.days.find((item) => item.date.slice(0, 10) === targetDate);
      if (!day) { skipped += 1; continue; }

      const task = normalizeAgronomyTask(candidate.source_domain_type ?? undefined);
      const forecastAdvisory = evaluateWeatherDayForTask(day, task);
      const radarObservation = await latestRadar(pool, preference.workspace_id, candidate.field_id);
      const radarAdvisory = evaluateRadarObservationForTask(radarObservation, task);
      const advisory = combineAgronomySignals(forecastAdvisory, radarAdvisory);
      const shouldNotify = advisory.suitability === 'avoid'
        ? preference.notify_avoid
        : advisory.suitability === 'caution' && preference.notify_caution;
      if (!shouldNotify) { skipped += 1; continue; }

      const dedupeKey = `agronomy-auto:${preference.user_id}:${candidate.event_id}:${targetDate}:${ruleVersion}:${advisory.suitability}`;
      const result = await pool.query<{ id: string }>(`
        INSERT INTO notification_intents (
          id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
          title, body, payload_json, dedupe_key, status
        ) VALUES (
          $1::uuid, $2::uuid, $3::uuid, $4::uuid,
          'agronomy_task_warning', 'push', 'scheduled_event', $5::uuid,
          $6, $7, $8::jsonb, $9, 'pending'
        )
        ON CONFLICT (dedupe_key) DO NOTHING
        RETURNING id
      `, [
        randomUUID(), preference.user_id, preference.workspace_id, candidate.field_id, candidate.event_id,
        `Mágina · ${candidate.field_name}`,
        `${candidate.title}: ${advisory.summary}`,
        JSON.stringify({
          path: 'mi-campo/hoy/',
          scheduled_event_id: candidate.event_id,
          task,
          suitability: advisory.suitability,
          risk_level: advisory.riskLevel,
          rule_version: ruleVersion,
          forecast_date: targetDate,
          forecast_source: forecast.provider,
          radar_observation_id: radarObservation?.observationId ?? null,
          radar_observed_at: radarObservation?.observedAt ?? null,
          radar_fresh: radarAdvisory.fresh,
          radar_elevated: advisory.radarElevated,
          semantics: { forecast: 'fresh_aemet_worker_fetch', radar: 'observed_reflectivity_not_eta' },
        }),
        dedupeKey,
      ]);
      if (result.rows[0]) created += 1;
      else skipped += 1;
    }
  }

  return { users: preferences.rows.length, evaluated, created, skipped, ruleVersion };
}
