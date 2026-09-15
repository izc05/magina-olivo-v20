import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin agenda smoke test.');

const db = createDatabase(databaseUrl);
const claims: GoogleIdentityClaims = {
  subject: 'agenda-admin-subject',
  email: 'agenda-admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Agenda',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Agenda',
  hostedDomain: 'magina.test',
};
const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-agenda-admin-token-'.padEnd(140, 'a');

async function login() {
  const response = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.ok(response.statusCode === 200 || response.statusCode === 201, response.body);
  const cookieHeader = response.headers['set-cookie'];
  assert.equal(typeof cookieHeader, 'string');
  return { body: response.json(), cookie: String(cookieHeader).split(';', 1)[0] };
}

try {
  await app.ready();
  const adminLogin = await login();
  const adminUserId = adminLogin.body.user.id as string;
  const workspace = await db.insertInto('workspaces').values({ name: 'Workspace Agenda Admin', type: 'family' }).returningAll().executeTakeFirstOrThrow();
  const fieldId = randomUUID();
  await db.insertInto('fields').values({
    id: fieldId,
    workspace_id: workspace.id,
    client_operation_id: randomUUID(),
    name: 'Finca Agenda Smoke',
    crop: 'olivar',
    status: 'active',
  }).execute();

  const create = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/agenda',
    headers: { cookie: adminLogin.cookie },
    payload: {
      field_id: fieldId,
      title: 'Revisar riego desde Admin',
      scheduled_at: '2026-09-15T08:30:00+02:00',
      task_kind: 'irrigation',
      notes: 'Comprobar goteros',
    },
  });
  assert.equal(create.statusCode, 201, create.body);
  const task = create.json().task;
  assert.equal(task.workspace_id, workspace.id);
  assert.equal(task.field_id, fieldId);
  assert.equal(task.status, 'planned');
  assert.equal(task.source, 'manual');

  const list = await app.inject({
    method: 'GET',
    url: `/api/v1/admin/agenda?workspace_id=${workspace.id}&status=active`,
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(list.statusCode, 200, list.body);
  assert.ok(list.json().tasks.some((item: { id: string }) => item.id === task.id));
  assert.equal(typeof list.json().counts.planned, 'number');
  assert.equal(typeof list.json().counts.overdue, 'number');

  const postpone = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/agenda/${task.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: {
      title: 'Revisar riego reprogramado',
      scheduled_at: '2026-09-16T09:15:00+02:00',
      task_kind: 'irrigation',
      notes: 'Revisar presión y goteros',
      status: 'postponed',
    },
  });
  assert.equal(postpone.statusCode, 200, postpone.body);
  assert.equal(postpone.json().task.status, 'postponed');
  assert.equal(postpone.json().task.title, 'Revisar riego reprogramado');

  const completedId = randomUUID();
  await sql`
    INSERT INTO scheduled_events (
      id, workspace_id, field_id, source_domain_type, title, scheduled_at, status, source,
      task_kind, notes, created_by, completed_domain_type, completed_domain_record_id, completed_at
    ) VALUES (
      ${completedId}::uuid, ${workspace.id}::uuid, ${fieldId}::uuid, 'observation', 'Tarea ya completada',
      '2026-09-10T08:00:00+02:00'::timestamptz, 'completed', 'manual', 'observation', 'Histórica',
      ${adminUserId}::uuid, 'observation', ${randomUUID()}::uuid, now()
    )
  `.execute(db);

  const immutable = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/agenda/${completedId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { title: 'No debería cambiar' },
  });
  assert.equal(immutable.statusCode, 409, immutable.body);
  assert.equal(immutable.json().error, 'completed_task_is_immutable');

  const cancel = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/agenda/${task.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { status: 'cancelled' },
  });
  assert.equal(cancel.statusCode, 200, cancel.body);
  assert.equal(cancel.json().task.status, 'cancelled');

  const stored = await db.selectFrom('scheduled_events').select(['title', 'status', 'task_kind']).where('id', '=', task.id).executeTakeFirstOrThrow();
  assert.equal(stored.title, 'Revisar riego reprogramado');
  assert.equal(stored.status, 'cancelled');
  assert.equal(stored.task_kind, 'irrigation');

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  const actions = audit.json().entries.filter((entry: { target_id: string | null }) => entry.target_id === task.id).map((entry: { action: string }) => entry.action);
  assert.ok(actions.includes('scheduled_event.created'));
  assert.ok(actions.includes('scheduled_event.updated'));

  console.log('ADMIN_AGENDA_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
