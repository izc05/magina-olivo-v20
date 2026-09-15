import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin management smoke test.');

const db = createDatabase(databaseUrl);
const adminEmail = 'management-admin@magina.test';
const claims: GoogleIdentityClaims = {
  subject: 'management-admin-subject',
  email: adminEmail,
  emailVerified: true,
  displayName: 'Admin Gestión',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Gestión',
  hostedDomain: 'magina.test',
};
const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier });
const credential = 'synthetic-management-id-token-'.padEnd(140, 'm');

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

  const secondUser = await db.insertInto('users').values({
    display_name: 'Segundo Propietario',
    primary_email: 'owner-two@magina.test',
    status: 'active',
  }).returning(['id']).executeTakeFirstOrThrow();

  const workspace = await db.insertInto('workspaces').values({ name: 'Finca Gestión Smoke', type: 'family' }).returningAll().executeTakeFirstOrThrow();
  await db.insertInto('workspace_memberships').values({
    workspace_id: workspace.id,
    user_id: adminUserId,
    role: 'owner',
    status: 'active',
    invited_by: adminUserId,
  }).execute();

  const fieldId = randomUUID();
  await db.insertInto('fields').values({
    id: fieldId,
    workspace_id: workspace.id,
    client_operation_id: randomUUID(),
    name: 'Los Olivos Smoke',
    crop: 'olivar',
    tree_count: 120,
    water_regime: 'secano',
    status: 'active',
  }).execute();

  const list = await app.inject({ method: 'GET', url: '/api/v1/admin/workspaces', headers: { cookie: adminLogin.cookie } });
  assert.equal(list.statusCode, 200, list.body);
  const listed = list.json().workspaces.find((item: { id: string }) => item.id === workspace.id);
  assert.ok(listed, 'workspace must be listed');
  assert.equal(listed.active_owners, 1);
  assert.equal(listed.active_fields, 1);

  const rename = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/workspaces/${workspace.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { name: 'Finca Gestión Actualizada', type: 'professional' },
  });
  assert.equal(rename.statusCode, 200, rename.body);
  assert.equal(rename.json().workspace.type, 'professional');

  const preventLastOwnerRemoval = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/workspaces/${workspace.id}/members/${adminUserId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { role: 'admin', status: 'active' },
  });
  assert.equal(preventLastOwnerRemoval.statusCode, 409, preventLastOwnerRemoval.body);
  assert.equal(preventLastOwnerRemoval.json().error, 'workspace_requires_active_owner');

  const addOwner = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/workspaces/${workspace.id}/members/${secondUser.id}`,
    headers: { cookie: adminLogin.cookie },
    payload: { role: 'owner', status: 'active' },
  });
  assert.equal(addOwner.statusCode, 200, addOwner.body);
  assert.equal(addOwner.json().membership.role, 'owner');

  const demoteOriginalOwner = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/workspaces/${workspace.id}/members/${adminUserId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { role: 'admin', status: 'active' },
  });
  assert.equal(demoteOriginalOwner.statusCode, 200, demoteOriginalOwner.body);

  const members = await app.inject({ method: 'GET', url: `/api/v1/admin/workspaces/${workspace.id}/members`, headers: { cookie: adminLogin.cookie } });
  assert.equal(members.statusCode, 200, members.body);
  assert.equal(members.json().members.length, 2);
  assert.ok(members.json().members.some((member: { user_id: string; role: string }) => member.user_id === secondUser.id && member.role === 'owner'));

  const fields = await app.inject({ method: 'GET', url: `/api/v1/admin/fields?workspace_id=${workspace.id}`, headers: { cookie: adminLogin.cookie } });
  assert.equal(fields.statusCode, 200, fields.body);
  assert.equal(fields.json().fields.length, 1);
  assert.equal(fields.json().fields[0].id, fieldId);

  const updateField = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/fields/${fieldId}`,
    headers: { cookie: adminLogin.cookie },
    payload: {
      name: 'Los Olivos Administrados',
      description: 'Actualizado desde Administración',
      tree_count: 135,
      crop: 'olivar',
      variety: 'Picual',
      water_regime: 'mixto',
      planting_year: 1998,
      tenure_type: 'propiedad',
      status: 'active',
    },
  });
  assert.equal(updateField.statusCode, 200, updateField.body);
  assert.equal(updateField.json().field.tree_count, 135);
  assert.equal(updateField.json().field.variety, 'Picual');

  const storedField = await db.selectFrom('fields').select(['name', 'tree_count', 'variety', 'geometry_status']).where('id', '=', fieldId).executeTakeFirstOrThrow();
  assert.equal(storedField.name, 'Los Olivos Administrados');
  assert.equal(storedField.tree_count, 135);
  assert.equal(storedField.variety, 'Picual');
  assert.equal(storedField.geometry_status, 'unlocated', 'management editor must not mutate GIS state');

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  const actions = new Set(audit.json().entries.map((entry: { action: string }) => entry.action));
  assert.ok(actions.has('workspace.updated'));
  assert.ok(actions.has('workspace_member.added'));
  assert.ok(actions.has('workspace_member.updated'));
  assert.ok(actions.has('field.updated'));

  console.log('ADMIN_MANAGEMENT_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
