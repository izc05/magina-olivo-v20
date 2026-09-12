import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

process.env.ALLOW_DEV_AUTH_HEADERS = 'true';

const db = createDatabase(databaseUrl);
const app = buildApp({ db });

const workspaceId = '71111111-1111-4111-8111-111111111111';
const otherWorkspaceId = '72222222-2222-4222-8222-222222222222';
const userId = '73333333-3333-4333-8333-333333333333';
const otherUserId = '74444444-4444-4444-8444-444444444444';
const fieldId = '75555555-5555-4555-8555-555555555555';

const headers = {
  'x-workspace-id': workspaceId,
  'x-user-id': userId,
};

async function insertIntent(input: {
  userId: string;
  workspaceId: string;
  fieldId?: string | null;
  kind: string;
  title: string;
  status: 'pending' | 'dispatched' | 'suppressed' | 'failed';
  path: string;
}) {
  const id = randomUUID();
  await sql`
    INSERT INTO notification_intents (
      id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
      title, body, payload_json, dedupe_key, status, dispatched_at
    ) VALUES (
      ${id}::uuid, ${input.userId}::uuid, ${input.workspaceId}::uuid, ${input.fieldId ?? null}::uuid,
      ${input.kind}, 'push', 'notification_center_smoke', ${randomUUID()}::uuid,
      ${input.title}, 'Contenido del aviso de prueba.', ${JSON.stringify({ path: input.path })}::jsonb,
      ${`notification-center-smoke:${id}`}, ${input.status},
      ${input.status === 'dispatched' ? new Date('2026-09-12T06:15:00.000Z') : null}
    )
  `.execute(db);
  return id;
}

async function main() {
  await sql`
    INSERT INTO workspaces (id, name, type) VALUES
      (${workspaceId}::uuid, 'Avisos CI', 'family'),
      (${otherWorkspaceId}::uuid, 'Avisos CI externo', 'family')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO users (id, primary_email, display_name) VALUES
      (${userId}::uuid, 'avisos-ci@example.test', 'Avisos CI'),
      (${otherUserId}::uuid, 'avisos-other@example.test', 'Avisos Other')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);
  await sql`
    INSERT INTO fields (id, workspace_id, client_operation_id, name, crop, status)
    VALUES (${fieldId}::uuid, ${workspaceId}::uuid, ${randomUUID()}::uuid, 'Las Avisadas', 'olivar', 'active')
    ON CONFLICT (id) DO NOTHING
  `.execute(db);

  const dispatchedId = await insertIntent({
    userId,
    workspaceId,
    fieldId,
    kind: 'agronomy_task_warning',
    title: 'Mágina · Las Avisadas',
    status: 'dispatched',
    path: 'mi-campo/hoy/',
  });
  const unsafeId = await insertIntent({
    userId,
    workspaceId,
    kind: 'document_ocr_failed',
    title: 'Mágina · documento sin leer',
    status: 'failed',
    path: 'https://example.invalid/phishing',
  });
  await insertIntent({
    userId: otherUserId,
    workspaceId,
    kind: 'financial_collection_pending',
    title: 'Aviso de otro usuario',
    status: 'pending',
    path: 'mi-campo/campana/',
  });
  await insertIntent({
    userId,
    workspaceId: otherWorkspaceId,
    kind: 'commercial_collection_pending',
    title: 'Aviso de otro workspace',
    status: 'pending',
    path: 'mi-campo/',
  });

  const response = await app.inject({
    method: 'GET',
    url: '/api/v1/notifications?limit=10',
    headers,
  });
  if (response.statusCode !== 200) throw new Error(`Notification center failed: ${response.statusCode} ${response.body}`);

  const body = response.json() as {
    items: Array<{ id: string; field_name: string | null; action_path: string | null; status: string; title: string }>;
    counts: { total: number; dispatched: number; failed: number; pending: number };
    semantics: string;
  };

  if (body.items.length !== 2) throw new Error(`Expected exactly two scoped notifications, got ${body.items.length}`);
  if (body.items.some((item) => item.title.includes('otro'))) throw new Error('Notification center leaked another user/workspace intent');

  const dispatched = body.items.find((item) => item.id === dispatchedId);
  if (!dispatched) throw new Error('Dispatched notification missing');
  if (dispatched.field_name !== 'Las Avisadas') throw new Error(`Expected field name, got ${String(dispatched.field_name)}`);
  if (dispatched.action_path !== '/mi-campo/hoy/') throw new Error(`Unexpected internal path: ${String(dispatched.action_path)}`);

  const unsafe = body.items.find((item) => item.id === unsafeId);
  if (!unsafe) throw new Error('Unsafe-path notification missing');
  if (unsafe.action_path !== null) throw new Error(`External action path was not rejected: ${String(unsafe.action_path)}`);

  if (body.counts.total !== 2 || body.counts.dispatched !== 1 || body.counts.failed !== 1 || body.counts.pending !== 0) {
    throw new Error(`Unexpected counts: ${JSON.stringify(body.counts)}`);
  }
  if (body.semantics !== 'delivery_history_not_read_state') throw new Error(`Unexpected semantics: ${body.semantics}`);

  const invalid = await app.inject({ method: 'GET', url: '/api/v1/notifications?limit=101', headers });
  if (invalid.statusCode !== 400) throw new Error(`Expected invalid limit 400, got ${invalid.statusCode}`);

  console.log('Notification center smoke passed: scoping, counts, field context and safe actions are enforced.');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
