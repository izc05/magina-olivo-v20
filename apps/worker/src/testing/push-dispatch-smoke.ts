import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { Pool } from 'pg';
import type { PushNotificationMessage, PushSendResult, PushSenderPort, PushSubscriptionDelivery } from '../notifications/ports.js';
import { runNotificationDispatchJob } from '../notifications/dispatch.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');
const pool = new Pool({ connectionString: databaseUrl });

const workspaceId = '91000000-0000-4000-8000-000000000001';
const fieldId = '92000000-0000-4000-8000-000000000001';
const userGood = '93000000-0000-4000-8000-000000000001';
const userRetry = '93000000-0000-4000-8000-000000000002';
const userNone = '93000000-0000-4000-8000-000000000003';
const userRevokedMembership = '93000000-0000-4000-8000-000000000004';

function hash(value: string) {
  return createHash('sha256').update(value).digest('hex');
}

class FakeSender implements PushSenderPort {
  retryCalls = 0;
  messages: PushNotificationMessage[] = [];

  async send(subscription: PushSubscriptionDelivery, message: PushNotificationMessage): Promise<PushSendResult> {
    this.messages.push(message);
    if (subscription.endpoint.includes('/gone')) {
      return { ok: false, statusCode: 410, permanentFailure: true, errorCode: 'subscription_gone', retryAfterSeconds: null };
    }
    if (subscription.endpoint.includes('/retry')) {
      this.retryCalls += 1;
      if (this.retryCalls === 1) {
        return { ok: false, statusCode: 503, permanentFailure: false, errorCode: 'push_http_503', retryAfterSeconds: 15 };
      }
    }
    return { ok: true, statusCode: 201, permanentFailure: false, errorCode: null, retryAfterSeconds: null };
  }
}

try {
  await pool.query(`INSERT INTO workspaces (id,name,type) VALUES ($1,'Push dispatch','family')`, [workspaceId]);
  await pool.query(`
    INSERT INTO users (id,display_name,status) VALUES
      ($1,'Good','active'),($2,'Retry','active'),($3,'None','active'),($4,'Revoked','active')
  `, [userGood, userRetry, userNone, userRevokedMembership]);
  await pool.query(`
    INSERT INTO workspace_memberships (workspace_id,user_id,role,status) VALUES
      ($1,$2,'owner','active'),($1,$3,'member','active'),($1,$4,'member','active'),($1,$5,'member','revoked')
  `, [workspaceId, userGood, userRetry, userNone, userRevokedMembership]);
  await pool.query(`INSERT INTO fields (id,workspace_id,client_operation_id,name,status) VALUES ($1,$2,$3,'Finca Push','active')`, [
    fieldId, workspaceId, '94000000-0000-4000-8000-000000000001',
  ]);

  const sessions = [
    ['95000000-0000-4000-8000-000000000001', userGood, '1'.repeat(64)],
    ['95000000-0000-4000-8000-000000000002', userGood, '2'.repeat(64)],
    ['95000000-0000-4000-8000-000000000003', userRetry, '3'.repeat(64)],
  ];
  for (const [sessionId, userId, tokenHash] of sessions) {
    await pool.query(`
      INSERT INTO user_sessions (id,user_id,token_hash,expires_at,user_agent)
      VALUES ($1,$2,$3,now() + interval '1 day','ci')
    `, [sessionId, userId, tokenHash]);
  }

  const endpoints = {
    good: 'https://push.example.test/good',
    gone: 'https://push.example.test/gone',
    retry: 'https://push.example.test/retry',
  };
  await pool.query(`
    INSERT INTO push_subscriptions (id,user_id,session_id,endpoint,endpoint_hash,p256dh,auth_secret,status) VALUES
      ('96000000-0000-4000-8000-000000000001',$1,$3,$6,$9,$12,$13,'active'),
      ('96000000-0000-4000-8000-000000000002',$1,$4,$7,$10,$12,$13,'active'),
      ('96000000-0000-4000-8000-000000000003',$2,$5,$8,$11,$12,$13,'active')
  `, [
    userGood, userRetry,
    sessions[0]?.[0], sessions[1]?.[0], sessions[2]?.[0],
    endpoints.good, endpoints.gone, endpoints.retry,
    hash(endpoints.good), hash(endpoints.gone), hash(endpoints.retry),
    'BAbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-', 'AbCdEfGhIjKlMnOpQrStUvWx',
  ]);

  const intents = {
    good: '97000000-0000-4000-8000-000000000001',
    retry: '97000000-0000-4000-8000-000000000002',
    none: '97000000-0000-4000-8000-000000000003',
    revoked: '97000000-0000-4000-8000-000000000004',
  };
  await pool.query(`
    INSERT INTO notification_intents (
      id,user_id,workspace_id,field_id,kind,channel,source_type,source_record_id,title,body,payload_json,dedupe_key,status
    ) VALUES
      ($1,$5,$9,$10,'radar_observed_echo','push','radar_observation','98000000-0000-4000-8000-000000000001','Radar cerca','Eco observado','{"path":"radar/"}'::jsonb,'push-smoke-good-01','pending'),
      ($2,$6,$9,$10,'push_test','push','push_test','98000000-0000-4000-8000-000000000002','Prueba','Reintento','{}'::jsonb,'push-smoke-retry-01','pending'),
      ($3,$7,$9,$10,'push_test','push','push_test','98000000-0000-4000-8000-000000000003','Prueba','Sin dispositivo','{}'::jsonb,'push-smoke-none-01','pending'),
      ($4,$8,$9,$10,'radar_observed_echo','push','radar_observation','98000000-0000-4000-8000-000000000004','Radar','Sin membership','{}'::jsonb,'push-smoke-revoked-01','pending')
  `, [intents.good, intents.retry, intents.none, intents.revoked, userGood, userRetry, userNone, userRevokedMembership, workspaceId, fieldId]);

  const sender = new FakeSender();
  const first = await runNotificationDispatchJob(pool, sender, { version: 1, limit: 20 });
  assert.equal(first.intentsScanned, 4);
  assert.equal(first.deliveriesSent, 1);
  assert.equal(first.permanentFailures, 1);
  assert.equal(first.retryableFailures, 1);
  assert.equal(first.suppressed, 2);

  const states = await pool.query<{ id: string; status: string }>(`
    SELECT id,status FROM notification_intents WHERE id = ANY($1::uuid[]) ORDER BY id
  `, [Object.values(intents)]);
  const map = new Map(states.rows.map((row) => [row.id, row.status]));
  assert.equal(map.get(intents.good), 'dispatched');
  assert.equal(map.get(intents.retry), 'pending');
  assert.equal(map.get(intents.none), 'suppressed');
  assert.equal(map.get(intents.revoked), 'suppressed');

  const gone = await pool.query<{ status: string }>(`SELECT status FROM push_subscriptions WHERE endpoint_hash = $1`, [hash(endpoints.gone)]);
  assert.equal(gone.rows[0]?.status, 'revoked');

  await pool.query(`UPDATE push_deliveries SET next_attempt_at = now() WHERE notification_intent_id = $1`, [intents.retry]);
  const second = await runNotificationDispatchJob(pool, sender, { version: 1, intent_id: intents.retry, limit: 1 });
  assert.equal(second.deliveriesSent, 1);
  const retried = await pool.query<{ status: string }>(`SELECT status FROM notification_intents WHERE id = $1`, [intents.retry]);
  assert.equal(retried.rows[0]?.status, 'dispatched');

  assert.ok(sender.messages.some((message) => message.tag === `radar-${fieldId}`));
  assert.ok(sender.messages.every((message) => !message.path.includes('://')));

  console.log('PUSH_DISPATCH_SMOKE_OK');
} finally {
  await pool.end();
}
