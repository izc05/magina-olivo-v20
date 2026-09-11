import { notificationDispatchJobPayloadSchema, type NotificationDispatchJobPayload } from '@magina/contracts';
import type { Pool } from 'pg';
import type { PushNotificationMessage, PushSendResult, PushSenderPort } from './ports.js';

const MAX_ATTEMPTS = 5;
const STALE_CLAIM_MINUTES = 10;

type IntentRow = {
  id: string;
  user_id: string;
  workspace_id: string;
  field_id: string | null;
  kind: string;
  title: string;
  body: string;
  payload_json: Record<string, unknown> | null;
};

type DeliveryRow = {
  delivery_id: string;
  subscription_id: string;
  endpoint: string;
  expiration_time: Date | null;
  p256dh: string;
  auth_secret: string;
  attempt_count: number;
};

export type NotificationDispatchOutcome = {
  intentsScanned: number;
  deliveriesSent: number;
  retryableFailures: number;
  permanentFailures: number;
  suppressed: number;
};

function safeRelativePath(value: unknown, fallback: string) {
  if (typeof value !== 'string') return fallback;
  const clean = value.trim().replace(/^\/+/, '');
  if (!clean || clean.includes('..') || clean.includes('://') || !/^[A-Za-z0-9_/?&=.%+-]+$/.test(clean)) return fallback;
  return clean;
}

function messageFor(intent: IntentRow): PushNotificationMessage {
  const fallback = intent.field_id ? `radar/?fieldId=${encodeURIComponent(intent.field_id)}` : '';
  return {
    title: intent.title,
    body: intent.body,
    path: safeRelativePath(intent.payload_json?.path, fallback),
    tag: intent.kind === 'radar_observed_echo' && intent.field_id
      ? `radar-${intent.field_id}`
      : `magina-${intent.kind}`,
  };
}

function retryDelaySeconds(attempt: number, result?: PushSendResult) {
  if (result?.retryAfterSeconds != null) return Math.max(15, Math.min(result.retryAfterSeconds, 3600));
  return Math.min(60 * (2 ** Math.max(0, attempt - 1)), 3600);
}

async function suppressIntent(pool: Pool, intentId: string) {
  await pool.query(`UPDATE notification_intents SET status = 'suppressed' WHERE id = $1 AND status = 'pending'`, [intentId]);
}

async function intentStillAllowed(pool: Pool, intent: IntentRow) {
  const result = await pool.query<{ allowed: boolean }>(`
    SELECT EXISTS (
      SELECT 1
      FROM users u
      INNER JOIN workspace_memberships wm
        ON wm.user_id = u.id
       AND wm.workspace_id = $2
       AND wm.status = 'active'
      LEFT JOIN user_preferences up ON up.user_id = u.id
      LEFT JOIN financial_notification_preferences fnp
        ON fnp.user_id = u.id AND fnp.workspace_id = wm.workspace_id
      LEFT JOIN agronomy_alert_preferences aap
        ON aap.user_id = u.id AND aap.workspace_id = wm.workspace_id
      WHERE u.id = $1
        AND u.status = 'active'
        AND ($3 <> 'radar_observed_echo' OR COALESCE(up.weather_alerts, true) = true)
        AND (
          $3 <> 'agronomy_task_warning'
          OR (
            COALESCE(aap.enabled, false) = true
            AND (
              COALESCE(($4->>'suitability') = 'avoid', false) AND COALESCE(aap.notify_avoid, false) = true
              OR COALESCE(($4->>'suitability') = 'caution', false) AND COALESCE(aap.notify_caution, false) = true
            )
          )
        )
        AND (
          $3 NOT IN ('financial_collection_pending','document_ocr_failed','document_review_pending')
          OR (
            COALESCE(fnp.enabled, false) = true
            AND ($3 <> 'financial_collection_pending' OR COALESCE(fnp.notify_settlements, false) = true)
            AND ($3 <> 'document_ocr_failed' OR COALESCE(fnp.notify_ocr_failure, false) = true)
            AND ($3 <> 'document_review_pending' OR COALESCE(fnp.notify_document_review, false) = true)
          )
        )
    ) AS allowed
  `, [intent.user_id, intent.workspace_id, intent.kind, intent.payload_json ?? {}]);
  return result.rows[0]?.allowed === true;
}

async function activeSubscriptions(pool: Pool, intent: IntentRow) {
  return pool.query<{ id: string }>(`
    SELECT ps.id
    FROM push_subscriptions ps
    INNER JOIN user_sessions s ON s.id = ps.session_id AND s.user_id = ps.user_id
    WHERE ps.user_id = $1
      AND ps.status = 'active'
      AND ps.revoked_at IS NULL
      AND (ps.expiration_time IS NULL OR ps.expiration_time > now())
      AND s.revoked_at IS NULL
      AND s.expires_at > now()
  `, [intent.user_id]);
}

async function createDeliveryRows(pool: Pool, intent: IntentRow, subscriptionIds: string[]) {
  for (const subscriptionId of subscriptionIds) {
    await pool.query(`
      INSERT INTO push_deliveries (notification_intent_id, push_subscription_id)
      VALUES ($1, $2)
      ON CONFLICT (notification_intent_id, push_subscription_id) DO NOTHING
    `, [intent.id, subscriptionId]);
  }
}

async function dueDeliveries(pool: Pool, intentId: string) {
  await pool.query(`
    UPDATE push_deliveries
    SET status = 'retryable_failed',
        next_attempt_at = now(),
        claimed_at = NULL,
        last_error_code = COALESCE(last_error_code, 'stale_claim_recovered'),
        updated_at = now()
    WHERE notification_intent_id = $1
      AND status = 'sending'
      AND claimed_at < now() - ($2::int * interval '1 minute')
  `, [intentId, STALE_CLAIM_MINUTES]);

  return pool.query<DeliveryRow>(`
    SELECT
      pd.id AS delivery_id,
      ps.id AS subscription_id,
      ps.endpoint,
      ps.expiration_time,
      ps.p256dh,
      ps.auth_secret,
      pd.attempt_count
    FROM push_deliveries pd
    INNER JOIN push_subscriptions ps ON ps.id = pd.push_subscription_id
    INNER JOIN user_sessions s ON s.id = ps.session_id AND s.user_id = ps.user_id
    WHERE pd.notification_intent_id = $1
      AND pd.status IN ('pending','retryable_failed')
      AND pd.next_attempt_at <= now()
      AND ps.status = 'active'
      AND ps.revoked_at IS NULL
      AND (ps.expiration_time IS NULL OR ps.expiration_time > now())
      AND s.revoked_at IS NULL
      AND s.expires_at > now()
    ORDER BY pd.created_at
  `, [intentId]);
}

async function claimDelivery(pool: Pool, deliveryId: string) {
  return pool.query<{ attempt_count: number }>(`
    UPDATE push_deliveries
    SET status = 'sending',
        attempt_count = attempt_count + 1,
        claimed_at = now(),
        updated_at = now()
    WHERE id = $1
      AND status IN ('pending','retryable_failed')
      AND next_attempt_at <= now()
    RETURNING attempt_count
  `, [deliveryId]);
}

async function recordSuccess(pool: Pool, deliveryId: string, statusCode: number | null) {
  await pool.query(`
    UPDATE push_deliveries
    SET status = 'sent', last_status_code = $2, last_error_code = NULL,
        claimed_at = NULL, sent_at = now(), updated_at = now()
    WHERE id = $1
  `, [deliveryId, statusCode]);
}

async function recordFailure(pool: Pool, delivery: DeliveryRow, attempt: number, result: PushSendResult) {
  const terminal = result.permanentFailure || attempt >= MAX_ATTEMPTS;
  const nextAttempt = new Date(Date.now() + retryDelaySeconds(attempt, result) * 1000);
  await pool.query(`
    UPDATE push_deliveries
    SET status = $2,
        last_status_code = $3,
        last_error_code = $4,
        next_attempt_at = $5,
        claimed_at = NULL,
        updated_at = now()
    WHERE id = $1
  `, [delivery.delivery_id, terminal ? 'permanent_failed' : 'retryable_failed', result.statusCode, terminal && !result.permanentFailure ? 'retries_exhausted' : result.errorCode, nextAttempt]);

  if (result.statusCode === 404 || result.statusCode === 410) {
    await pool.query(`
      UPDATE push_subscriptions
      SET status = 'revoked', revoked_at = now(), failure_count = failure_count + 1,
          last_failure_code = $2, updated_at = now()
      WHERE id = $1
    `, [delivery.subscription_id, result.errorCode ?? 'subscription_gone']);
  } else {
    await pool.query(`
      UPDATE push_subscriptions
      SET failure_count = failure_count + 1,
          last_failure_code = $2, updated_at = now()
      WHERE id = $1
    `, [delivery.subscription_id, result.errorCode]);
  }
  return terminal;
}

async function finalizeIntent(pool: Pool, intentId: string) {
  const counts = await pool.query<{ sent: number | string; active: number | string; permanent: number | string }>(`
    SELECT
      count(*) FILTER (WHERE status = 'sent')::int AS sent,
      count(*) FILTER (WHERE status IN ('pending','sending','retryable_failed'))::int AS active,
      count(*) FILTER (WHERE status = 'permanent_failed')::int AS permanent
    FROM push_deliveries
    WHERE notification_intent_id = $1
  `, [intentId]);
  const row = counts.rows[0];
  if (Number(row?.active ?? 0) > 0) return;
  if (Number(row?.sent ?? 0) > 0) {
    await pool.query(`UPDATE notification_intents SET status = 'dispatched', dispatched_at = now() WHERE id = $1`, [intentId]);
    return;
  }
  if (Number(row?.permanent ?? 0) > 0) await pool.query(`UPDATE notification_intents SET status = 'failed' WHERE id = $1`, [intentId]);
}

export async function runNotificationDispatchJob(pool: Pool, sender: PushSenderPort, rawJob: NotificationDispatchJobPayload): Promise<NotificationDispatchOutcome> {
  const job = notificationDispatchJobPayloadSchema.parse(rawJob);
  const params: unknown[] = [];
  let where = `status = 'pending' AND channel = 'push'`;
  if (job.intent_id) { params.push(job.intent_id); where += ` AND id = $${params.length}`; }
  params.push(job.limit);

  const intents = await pool.query<IntentRow>(`
    SELECT id, user_id, workspace_id, field_id, kind, title, body, payload_json
    FROM notification_intents
    WHERE ${where}
    ORDER BY created_at
    LIMIT $${params.length}
  `, params);

  const outcome: NotificationDispatchOutcome = { intentsScanned: intents.rows.length, deliveriesSent: 0, retryableFailures: 0, permanentFailures: 0, suppressed: 0 };

  for (const intent of intents.rows) {
    if (!(await intentStillAllowed(pool, intent))) { await suppressIntent(pool, intent.id); outcome.suppressed += 1; continue; }
    const subscriptions = await activeSubscriptions(pool, intent);
    if (subscriptions.rows.length === 0) { await suppressIntent(pool, intent.id); outcome.suppressed += 1; continue; }

    await createDeliveryRows(pool, intent, subscriptions.rows.map((row) => row.id));
    const due = await dueDeliveries(pool, intent.id);
    const message = messageFor(intent);

    for (const delivery of due.rows) {
      const claimed = await claimDelivery(pool, delivery.delivery_id);
      const attempt = claimed.rows[0]?.attempt_count;
      if (!attempt) continue;
      let result: PushSendResult;
      try {
        result = await sender.send({ id: delivery.subscription_id, endpoint: delivery.endpoint, expirationTime: delivery.expiration_time ? new Date(delivery.expiration_time).getTime() : null, p256dh: delivery.p256dh, auth: delivery.auth_secret }, message);
      } catch {
        result = { ok: false, statusCode: null, permanentFailure: false, errorCode: 'push_network_error', retryAfterSeconds: null };
      }
      if (result.ok) { await recordSuccess(pool, delivery.delivery_id, result.statusCode); outcome.deliveriesSent += 1; }
      else { const terminal = await recordFailure(pool, delivery, attempt, result); if (terminal) outcome.permanentFailures += 1; else outcome.retryableFailures += 1; }
    }
    await finalizeIntent(pool, intent.id);
  }
  return outcome;
}
