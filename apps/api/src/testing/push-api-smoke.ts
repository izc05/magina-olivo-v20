import assert from 'node:assert/strict';
import { Pool } from 'pg';
import type { NotificationDispatchJobPayload } from '@magina/contracts';
import { buildApp } from '../app.js';
import { issueSession, SESSION_COOKIE_NAME } from '../auth/session.js';
import { createDatabase } from '../db/client.js';
import type { NotificationDispatchQueuePort } from '../notifications/port.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const workspaceId = '81000000-0000-4000-8000-000000000001';
const userId = '82000000-0000-4000-8000-000000000001';
const fieldId = '83000000-0000-4000-8000-000000000001';
const pool = new Pool({ connectionString: databaseUrl });
const db = createDatabase(databaseUrl);

class FakeQueue implements NotificationDispatchQueuePort {
  jobs: NotificationDispatchJobPayload[] = [];
  async enqueue(payload: NotificationDispatchJobPayload) {
    this.jobs.push(payload);
    return `fake-${this.jobs.length}`;
  }
}

const queue = new FakeQueue();
const app = buildApp({ db, notificationQueue: queue, pushPublicKey: 'BPublicVapidKeyForSmokeOnly_1234567890' });

try {
  await pool.query(`INSERT INTO workspaces (id,name,type) VALUES ($1,'Push smoke','family')`, [workspaceId]);
  await pool.query(`INSERT INTO users (id,display_name,status) VALUES ($1,'Push user','active')`, [userId]);
  await pool.query(`INSERT INTO workspace_memberships (workspace_id,user_id,role,status) VALUES ($1,$2,'owner','active')`, [workspaceId, userId]);
  await pool.query(`INSERT INTO fields (id,workspace_id,client_operation_id,name,status) VALUES ($1,$2,$3,'Finca Push','active')`, [
    fieldId,
    workspaceId,
    '84000000-0000-4000-8000-000000000001',
  ]);

  const session = await issueSession(db, userId, 'push-smoke');
  await app.ready();
  const cookie = `${SESSION_COOKIE_NAME}=${session.token}`;

  const config = await app.inject({ method: 'GET', url: '/api/v1/push/config' });
  assert.equal(config.statusCode, 200, config.body);
  assert.equal(config.json().configured, true);

  const endpoint = 'https://push.example.test/subscription/magina-smoke';
  const subscription = await app.inject({
    method: 'POST',
    url: '/api/v1/push/subscriptions',
    headers: { cookie, 'content-type': 'application/json' },
    payload: {
      endpoint,
      expiration_time: null,
      keys: {
        p256dh: 'BAbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-',
        auth: 'AbCdEfGhIjKlMnOpQrStUvWx',
      },
      device_label: 'CI browser',
    },
  });
  assert.equal(subscription.statusCode, 201, subscription.body);

  const status = await app.inject({ method: 'GET', url: '/api/v1/push/status', headers: { cookie } });
  assert.equal(status.statusCode, 200, status.body);
  assert.equal(status.json().active_subscriptions, 1);

  const testPush = await app.inject({
    method: 'POST',
    url: '/api/v1/push/test',
    headers: { cookie, 'x-workspace-id': workspaceId, 'content-type': 'application/json' },
    payload: { field_id: fieldId },
  });
  assert.equal(testPush.statusCode, 202, testPush.body);
  assert.equal(testPush.json().dispatch_queued, true);
  assert.equal(queue.jobs.length, 1);
  assert.equal(queue.jobs[0]?.intent_id, testPush.json().intent_id);

  const intent = await pool.query<{ payload_json: { path?: string }; status: string }>(`
    SELECT payload_json, status FROM notification_intents WHERE id = $1
  `, [testPush.json().intent_id]);
  assert.equal(intent.rows[0]?.status, 'pending');
  assert.equal(intent.rows[0]?.payload_json.path, `radar/?fieldId=${fieldId}`);

  const removed = await app.inject({
    method: 'DELETE',
    url: '/api/v1/push/subscriptions',
    headers: { cookie, 'content-type': 'application/json' },
    payload: { endpoint },
  });
  assert.equal(removed.statusCode, 204, removed.body);

  const statusAfter = await app.inject({ method: 'GET', url: '/api/v1/push/status', headers: { cookie } });
  assert.equal(statusAfter.statusCode, 200, statusAfter.body);
  assert.equal(statusAfter.json().active_subscriptions, 0);

  const noDevice = await app.inject({
    method: 'POST',
    url: '/api/v1/push/test',
    headers: { cookie, 'x-workspace-id': workspaceId, 'content-type': 'application/json' },
    payload: { field_id: fieldId },
  });
  assert.equal(noDevice.statusCode, 409, noDevice.body);

  console.log('PUSH_API_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
  await pool.end();
}
