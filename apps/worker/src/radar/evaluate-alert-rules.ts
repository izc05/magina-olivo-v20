import type { Pool, PoolClient } from 'pg';

type AlertRuleRow = {
  id: string;
  user_id: string;
  radius_km: number | string;
  min_dbz: number | string;
  cooldown_minutes: number;
};

export type RadarAlertObservation = {
  id: string;
  workspaceId: string;
  fieldId: string;
  fieldName: string;
  observedAt: string;
  precipitationDetected: boolean;
  nearestEchoDistanceKm: number | null;
  directionLabel: 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW' | 'OVER_FIELD' | null;
  reflectivityDbzMin: number | null;
  reflectivityDbzMax: number | null;
};

const DIRECTION_ES: Record<Exclude<NonNullable<RadarAlertObservation['directionLabel']>, 'OVER_FIELD'>, string> = {
  N: 'norte',
  NE: 'noreste',
  E: 'este',
  SE: 'sureste',
  S: 'sur',
  SW: 'suroeste',
  W: 'oeste',
  NW: 'noroeste',
};

function finite(value: number | string): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error('Invalid radar alert rule numeric value');
  return parsed;
}

function matchesObservation(rule: AlertRuleRow, observation: RadarAlertObservation) {
  if (!observation.precipitationDetected) return false;
  if (observation.reflectivityDbzMin == null) return false;
  if (observation.reflectivityDbzMin < finite(rule.min_dbz)) return false;
  if (observation.directionLabel === 'OVER_FIELD') return true;
  if (observation.nearestEchoDistanceKm == null) return false;
  return observation.nearestEchoDistanceKm <= finite(rule.radius_km);
}

function notificationCopy(fieldName: string, observation: RadarAlertObservation) {
  if (observation.directionLabel === 'OVER_FIELD') {
    return {
      title: `Eco de lluvia sobre ${fieldName}`,
      body: 'El radar de AEMET detecta reflectividad de precipitación sobre la finca. Es una observación, no una previsión de llegada.',
    };
  }

  const direction = observation.directionLabel ? DIRECTION_ES[observation.directionLabel] : null;
  const distance = observation.nearestEchoDistanceKm;
  const rounded = distance == null ? null : (distance < 10 ? Math.round(distance * 10) / 10 : Math.round(distance));
  return {
    title: `Lluvia detectada cerca de ${fieldName}`,
    body: rounded != null && direction
      ? `AEMET detecta un eco de reflectividad a unos ${rounded} km al ${direction}. Es una observación radar; no estima cuándo llegará.`
      : 'AEMET detecta un eco de reflectividad cerca de la finca. Es una observación radar; no estima cuándo llegará.',
  };
}

async function acquireCooldown(client: PoolClient, rule: AlertRuleRow) {
  const result = await client.query<{ id: string }>(`
    UPDATE radar_alert_rules
    SET last_triggered_at = now(), updated_at = now()
    WHERE id = $1
      AND enabled = true
      AND (
        last_triggered_at IS NULL
        OR last_triggered_at <= now() - make_interval(mins => cooldown_minutes)
      )
    RETURNING id
  `, [rule.id]);
  return result.rowCount === 1;
}

async function createIntent(pool: Pool, rule: AlertRuleRow, observation: RadarAlertObservation) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const allowed = await acquireCooldown(client, rule);
    if (!allowed) {
      await client.query('ROLLBACK');
      return false;
    }

    const copy = notificationCopy(observation.fieldName, observation);
    const dedupeKey = `radar:${rule.id}:${observation.id}`;
    const payload = {
      field_id: observation.fieldId,
      observed_at: observation.observedAt,
      distance_km: observation.nearestEchoDistanceKm,
      direction: observation.directionLabel,
      reflectivity_dbz: {
        min: observation.reflectivityDbzMin,
        max: observation.reflectivityDbzMax,
      },
      semantics: 'observed_reflectivity_not_forecast',
    };

    const inserted = await client.query(`
      INSERT INTO notification_intents (
        user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
        title, body, payload_json, dedupe_key, status
      ) VALUES (
        $1, $2, $3, 'radar_echo_observed', 'push', 'farm_radar_observation', $4,
        $5, $6, $7::jsonb, $8, 'pending'
      )
      ON CONFLICT (dedupe_key) DO NOTHING
      RETURNING id
    `, [
      rule.user_id,
      observation.workspaceId,
      observation.fieldId,
      observation.id,
      copy.title,
      copy.body,
      JSON.stringify(payload),
      dedupeKey,
    ]);

    if (inserted.rowCount !== 1) {
      // A duplicate observation should not consume the cooldown window.
      await client.query('ROLLBACK');
      return false;
    }

    await client.query('COMMIT');
    return true;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

export async function evaluateRadarAlertRules(
  pool: Pool,
  observation: RadarAlertObservation,
): Promise<number> {
  if (!observation.precipitationDetected) return 0;

  const rules = await pool.query<AlertRuleRow>(`
    SELECT r.id, r.user_id, r.radius_km, r.min_dbz, r.cooldown_minutes
    FROM radar_alert_rules r
    INNER JOIN users u
      ON u.id = r.user_id
     AND u.status = 'active'
    INNER JOIN user_preferences p
      ON p.user_id = r.user_id
     AND p.weather_alerts = true
    INNER JOIN workspace_memberships m
      ON m.workspace_id = r.workspace_id
     AND m.user_id = r.user_id
     AND m.status = 'active'
    WHERE r.workspace_id = $1
      AND r.field_id = $2
      AND r.enabled = true
  `, [observation.workspaceId, observation.fieldId]);

  let created = 0;
  for (const rule of rules.rows) {
    if (!matchesObservation(rule, observation)) continue;
    if (await createIntent(pool, rule, observation)) created += 1;
  }
  return created;
}
