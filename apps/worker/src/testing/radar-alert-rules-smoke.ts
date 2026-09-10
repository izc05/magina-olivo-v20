import assert from 'node:assert/strict';
import { Pool } from 'pg';
import { evaluateRadarAlertRules, type RadarAlertObservation } from '../radar/evaluate-alert-rules.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const pool = new Pool({ connectionString: databaseUrl });

try {
  const workspaceId = '51000000-0000-4000-8000-000000000001';
  const fieldId = '52000000-0000-4000-8000-000000000001';
  const userMatch = '53000000-0000-4000-8000-000000000001';
  const userTooStrict = '53000000-0000-4000-8000-000000000002';
  const userAlertsOff = '53000000-0000-4000-8000-000000000003';
  const userTooFar = '53000000-0000-4000-8000-000000000004';
  const userInactiveMembership = '53000000-0000-4000-8000-000000000005';

  await pool.query(`INSERT INTO workspaces (id, name, type) VALUES ($1, 'Radar alerts', 'family')`, [workspaceId]);
  await pool.query(`
    INSERT INTO users (id, display_name) VALUES
      ($1, 'Match'), ($2, 'Strict'), ($3, 'Off'), ($4, 'Far'), ($5, 'Inactive')
  `, [userMatch, userTooStrict, userAlertsOff, userTooFar, userInactiveMembership]);
  await pool.query(`
    INSERT INTO user_preferences (user_id, weather_alerts) VALUES
      ($1, true), ($2, true), ($3, false), ($4, true), ($5, true)
  `, [userMatch, userTooStrict, userAlertsOff, userTooFar, userInactiveMembership]);
  await pool.query(`
    INSERT INTO workspace_memberships (workspace_id, user_id, role, status) VALUES
      ($1, $2, 'owner', 'active'),
      ($1, $3, 'member', 'active'),
      ($1, $4, 'member', 'active'),
      ($1, $5, 'member', 'active'),
      ($1, $6, 'member', 'revoked')
  `, [workspaceId, userMatch, userTooStrict, userAlertsOff, userTooFar, userInactiveMembership]);
  await pool.query(`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, geometry, status)
    VALUES ($1, $2, '54000000-0000-4000-8000-000000000001', 'Las Alertas',
      ST_Multi(ST_GeomFromText('POLYGON((-3.5 37.7,-3.49 37.7,-3.49 37.71,-3.5 37.71,-3.5 37.7))', 4326)), 'active')
  `, [fieldId, workspaceId]);

  await pool.query(`
    INSERT INTO radar_alert_rules (user_id, workspace_id, field_id, radius_km, min_dbz, cooldown_minutes) VALUES
      ($1, $6, $7, 10, 12, 60),
      ($2, $6, $7, 10, 20, 60),
      ($3, $6, $7, 10, 12, 60),
      ($4, $6, $7, 5, 12, 60),
      ($5, $6, $7, 10, 12, 60)
  `, [userMatch, userTooStrict, userAlertsOff, userTooFar, userInactiveMembership, workspaceId, fieldId]);

  const observation: RadarAlertObservation = {
    id: '55000000-0000-4000-8000-000000000001',
    workspaceId,
    fieldId,
    fieldName: 'Las Alertas',
    observedAt: '2026-09-10T10:00:00.000Z',
    precipitationDetected: true,
    nearestEchoDistanceKm: 8,
    directionLabel: 'W',
    reflectivityDbzMin: 12,
    reflectivityDbzMax: 18,
  };

  const first = await evaluateRadarAlertRules(pool, observation);
  assert.equal(first, 1, 'only the active matching user should receive an intent');

  const intents = await pool.query<{
    user_id: string;
    status: string;
    body: string;
    payload_json: { semantics?: string };
  }>(`
    SELECT user_id, status, body, payload_json
    FROM notification_intents
    WHERE field_id = $1
  `, [fieldId]);
  assert.equal(intents.rows.length, 1);
  assert.equal(intents.rows[0]?.user_id, userMatch);
  assert.equal(intents.rows[0]?.status, 'pending');
  assert.match(intents.rows[0]?.body ?? '', /no estima cuándo llegará/i);
  assert.equal(intents.rows[0]?.payload_json.semantics, 'observed_reflectivity_not_forecast');

  const replay = await evaluateRadarAlertRules(pool, observation);
  assert.equal(replay, 0, 'replaying the same observation must not duplicate an intent');

  const observationTwo: RadarAlertObservation = {
    ...observation,
    id: '55000000-0000-4000-8000-000000000002',
    observedAt: '2026-09-10T10:10:00.000Z',
  };
  const duringCooldown = await evaluateRadarAlertRules(pool, observationTwo);
  assert.equal(duringCooldown, 0, 'a second observation inside cooldown must not create an intent');

  await pool.query(`
    UPDATE radar_alert_rules
    SET last_triggered_at = now() - interval '2 hours'
    WHERE user_id = $1 AND field_id = $2
  `, [userMatch, fieldId]);
  const afterCooldown = await evaluateRadarAlertRules(pool, observationTwo);
  assert.equal(afterCooldown, 1, 'the same rule may create a new intent after cooldown');

  const finalIntents = await pool.query<{ count: string }>(`
    SELECT count(*)::text AS count
    FROM notification_intents
    WHERE field_id = $1
  `, [fieldId]);
  assert.equal(Number(finalIntents.rows[0]?.count), 2);

  const noRain: RadarAlertObservation = {
    ...observation,
    id: '55000000-0000-4000-8000-000000000003',
    precipitationDetected: false,
    nearestEchoDistanceKm: null,
    directionLabel: null,
    reflectivityDbzMin: null,
    reflectivityDbzMax: null,
  };
  assert.equal(await evaluateRadarAlertRules(pool, noRain), 0);

  console.log('RADAR_ALERT_RULES_SMOKE_OK');
} finally {
  await pool.end();
}
