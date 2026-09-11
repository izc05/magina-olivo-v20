import assert from 'node:assert/strict';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';
import type { ReserveUploadInput, StoragePort, StoredObjectInfo, UploadReservation } from '../storage/port.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for admin smoke test.');

class SmokeStorage implements StoragePort {
  private readonly objects = new Map<string, StoredObjectInfo>();

  async reserveUpload(input: ReserveUploadInput): Promise<UploadReservation> {
    const storageKey = `smoke/${input.workspaceId}/${input.documentId}/${input.versionId}`;
    this.objects.set(storageKey, {
      exists: true,
      byteSize: input.byteSize,
      mimeType: input.mimeType,
      etag: `etag-${input.documentId}`,
      checksumSha256: Buffer.from(input.sha256, 'hex').toString('base64'),
    });
    return {
      storageKey,
      uploadUrl: `https://upload.invalid/${input.documentId}`,
      method: 'PUT',
      headers: { 'content-type': input.mimeType },
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
    };
  }

  async headObject(storageKey: string): Promise<StoredObjectInfo> {
    return this.objects.get(storageKey) ?? { exists: false };
  }

  async createReadUrl(storageKey: string): Promise<string> {
    return `https://media.invalid/read/${encodeURIComponent(storageKey)}`;
  }
}

const db = createDatabase(databaseUrl);
const storage = new SmokeStorage();
let claims: GoogleIdentityClaims = {
  subject: 'admin-google-subject',
  email: 'admin@magina.test',
  emailVerified: true,
  displayName: 'Admin Mágina',
  pictureUrl: null,
  givenName: 'Admin',
  familyName: 'Mágina',
  hostedDomain: 'magina.test',
};

const verifier: GoogleIdentityVerifier = { async verify() { return claims; } };
const app = buildApp({ db, googleVerifier: verifier, storage });
const credential = 'synthetic-google-id-token-'.padEnd(140, 'x');

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
  const adminUserId = String(adminLogin.body.user.id);
  const adminSession = await app.inject({ method: 'GET', url: '/api/v1/admin/session', headers: { cookie: adminLogin.cookie } });
  assert.equal(adminSession.statusCode, 200, adminSession.body);
  assert.equal(adminSession.json().platform_access.role, 'super_admin');
  assert.equal(adminSession.json().platform_access.source, 'bootstrap');

  claims = {
    ...claims,
    subject: 'farmer-google-subject',
    email: 'farmer@magina.test',
    displayName: 'Agricultor sin admin',
    hostedDomain: 'magina.test',
  };
  const farmerLogin = await login();
  const farmerUserId = String(farmerLogin.body.user.id);
  assert.equal(farmerLogin.body.workspaces[0].role, 'owner');

  const deniedAdmin = await app.inject({ method: 'GET', url: '/api/v1/admin/session', headers: { cookie: farmerLogin.cookie } });
  assert.equal(deniedAdmin.statusCode, 403, deniedAdmin.body);
  assert.equal(deniedAdmin.json().error, 'platform_admin_required');

  const createContent = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/content',
    headers: { cookie: adminLogin.cookie },
    payload: {
      type: 'news',
      slug: 'cosecha-admin-smoke',
      title: 'Cosecha de prueba',
      summary: 'Contenido publicado desde el panel.',
      content_json: { body: 'Contenido estructurado' },
      status: 'published',
      featured: true,
    },
  });
  assert.equal(createContent.statusCode, 201, createContent.body);
  const contentId = String(createContent.json().entry.id);

  const futurePromotion = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/content',
    headers: { cookie: adminLogin.cookie },
    payload: {
      type: 'promotion',
      slug: 'promocion-futura-smoke',
      title: 'Promoción futura',
      status: 'published',
      starts_at: '2099-01-01T00:00:00.000Z',
    },
  });
  assert.equal(futurePromotion.statusCode, 201, futurePromotion.body);
  const futurePromotionId = String(futurePromotion.json().entry.id);

  const expiredPromotion = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/content',
    headers: { cookie: adminLogin.cookie },
    payload: {
      type: 'promotion',
      slug: 'promocion-caducada-smoke',
      title: 'Promoción caducada',
      status: 'published',
      ends_at: '2000-01-01T00:00:00.000Z',
    },
  });
  assert.equal(expiredPromotion.statusCode, 201, expiredPromotion.body);
  const expiredPromotionId = String(expiredPromotion.json().entry.id);

  const publicContent = await app.inject({ method: 'GET', url: '/api/v1/public/content?type=news' });
  assert.equal(publicContent.statusCode, 200, publicContent.body);
  assert.ok(publicContent.json().entries.some((entry: { id: string }) => entry.id === contentId));

  const publicPromotions = await app.inject({ method: 'GET', url: '/api/v1/public/content?type=promotion' });
  assert.equal(publicPromotions.statusCode, 200, publicPromotions.body);
  const publicPromotionIds = publicPromotions.json().entries.map((entry: { id: string }) => entry.id);
  assert.ok(!publicPromotionIds.includes(futurePromotionId), 'Future content must stay hidden until starts_at');
  assert.ok(!publicPromotionIds.includes(expiredPromotionId), 'Expired content must stay hidden after ends_at');

  const publicSetting = await app.inject({
    method: 'PUT',
    url: '/api/v1/admin/settings/home.hero',
    headers: { cookie: adminLogin.cookie },
    payload: { value_json: { title: 'Portada gestionada' }, description: 'Cabecera principal', is_public: true },
  });
  assert.equal(publicSetting.statusCode, 200, publicSetting.body);

  const privateSetting = await app.inject({
    method: 'PUT',
    url: '/api/v1/admin/settings/internal.note',
    headers: { cookie: adminLogin.cookie },
    payload: { value_json: { text: 'Solo administración' }, is_public: false },
  });
  assert.equal(privateSetting.statusCode, 200, privateSetting.body);

  const publicSettings = await app.inject({ method: 'GET', url: '/api/v1/public/site-settings' });
  assert.equal(publicSettings.statusCode, 200, publicSettings.body);
  assert.equal(publicSettings.json().settings['home.hero'].title, 'Portada gestionada');
  assert.equal(publicSettings.json().settings['internal.note'], undefined);

  const invalidMedia = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/media/reserve',
    headers: { cookie: adminLogin.cookie },
    payload: { original_filename: 'unsafe.svg', mime_type: 'image/svg+xml', byte_size: 120, sha256: 'a'.repeat(64) },
  });
  assert.equal(invalidMedia.statusCode, 400, invalidMedia.body);

  const reserveMedia = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/media/reserve',
    headers: { cookie: adminLogin.cookie },
    payload: { original_filename: 'olivar.webp', mime_type: 'image/webp', byte_size: 2048, sha256: 'b'.repeat(64) },
  });
  assert.equal(reserveMedia.statusCode, 201, reserveMedia.body);
  const mediaId = String(reserveMedia.json().asset.id);
  assert.equal(reserveMedia.json().asset.status, 'reserved');

  const completeMedia = await app.inject({
    method: 'POST',
    url: `/api/v1/admin/media/${mediaId}/complete`,
    headers: { cookie: adminLogin.cookie },
  });
  assert.equal(completeMedia.statusCode, 200, completeMedia.body);
  assert.equal(completeMedia.json().asset.status, 'uploaded');
  assert.equal(completeMedia.json().asset.public_path, `/api/v1/public/media/${mediaId}`);

  const mediaList = await app.inject({ method: 'GET', url: '/api/v1/admin/media', headers: { cookie: adminLogin.cookie } });
  assert.equal(mediaList.statusCode, 200, mediaList.body);
  assert.ok(mediaList.json().assets.some((asset: { id: string }) => asset.id === mediaId));

  const publicMedia = await app.inject({ method: 'GET', url: `/api/v1/public/media/${mediaId}` });
  assert.equal(publicMedia.statusCode, 302, publicMedia.body);
  assert.match(String(publicMedia.headers.location), /^https:\/\/media\.invalid\/read\//);

  const grantEditor = await app.inject({
    method: 'PUT',
    url: `/api/v1/admin/platform-access/${farmerUserId}`,
    headers: { cookie: adminLogin.cookie },
    payload: { role: 'editor', status: 'active' },
  });
  assert.equal(grantEditor.statusCode, 200, grantEditor.body);

  const editorSession = await app.inject({ method: 'GET', url: '/api/v1/admin/session', headers: { cookie: farmerLogin.cookie } });
  assert.equal(editorSession.statusCode, 200, editorSession.body);
  assert.equal(editorSession.json().platform_access.role, 'editor');
  assert.equal(editorSession.json().platform_access.source, 'database');

  const editorCreates = await app.inject({
    method: 'POST',
    url: '/api/v1/admin/content',
    headers: { cookie: farmerLogin.cookie },
    payload: { type: 'event', slug: 'evento-editor-smoke', title: 'Evento editor', status: 'draft' },
  });
  assert.equal(editorCreates.statusCode, 201, editorCreates.body);

  const editorCannotSuspend = await app.inject({
    method: 'PATCH',
    url: `/api/v1/admin/users/${adminUserId}`,
    headers: { cookie: farmerLogin.cookie },
    payload: { status: 'suspended' },
  });
  assert.equal(editorCannotSuspend.statusCode, 403, editorCannotSuspend.body);

  const audit = await app.inject({ method: 'GET', url: '/api/v1/admin/audit', headers: { cookie: adminLogin.cookie } });
  assert.equal(audit.statusCode, 200, audit.body);
  const actions = audit.json().entries.map((entry: { action: string }) => entry.action);
  assert.ok(actions.includes('content.created'));
  assert.ok(actions.includes('platform_access.changed'));
  assert.ok(actions.includes('setting.updated'));
  assert.ok(actions.includes('media.reserved'));
  assert.ok(actions.includes('media.uploaded'));

  console.log('ADMIN_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
