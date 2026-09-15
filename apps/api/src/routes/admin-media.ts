import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { StoragePort } from '../storage/port.js';
import { StorageNotConfiguredError } from '../storage/port.js';

const MAX_MEDIA_BYTES = 10 * 1024 * 1024;
const mediaMimeSchema = z.enum(['image/jpeg', 'image/png', 'image/webp', 'image/avif']);
const sha256Schema = z.string().regex(/^[0-9a-fA-F]{64}$/).transform((value) => value.toLowerCase());

const reserveMediaSchema = z.object({
  original_filename: z.string().trim().min(1).max(255),
  mime_type: mediaMimeSchema,
  byte_size: z.number().int().positive().max(MAX_MEDIA_BYTES),
  sha256: sha256Schema,
});

const uuidParamsSchema = z.object({ id: z.string().uuid() });

type MediaAssetRow = {
  id: string;
  storage_key: string;
  original_filename: string;
  mime_type: string;
  byte_size: string | number;
  sha256: string;
  status: 'reserved' | 'uploaded' | 'failed' | 'archived';
  created_by: string | null;
  created_at: Date;
  uploaded_at: Date | null;
  storage_etag: string | null;
  storage_checksum_sha256: string | null;
};

function publicAsset(asset: MediaAssetRow) {
  return {
    id: asset.id,
    original_filename: asset.original_filename,
    mime_type: asset.mime_type,
    byte_size: Number(asset.byte_size),
    status: asset.status,
    created_by: asset.created_by,
    created_at: asset.created_at,
    uploaded_at: asset.uploaded_at,
    public_path: `/api/v1/public/media/${asset.id}`,
  };
}

function expectedChecksumBase64(sha256Hex: string) {
  return Buffer.from(sha256Hex, 'hex').toString('base64');
}

async function findMedia(db: DatabaseClient, id: string) {
  const result = await sql<MediaAssetRow>`
    SELECT id, storage_key, original_filename, mime_type, byte_size, sha256, status,
           created_by, created_at, uploaded_at, storage_etag, storage_checksum_sha256
    FROM platform_media_assets
    WHERE id = ${id}::uuid
    LIMIT 1
  `.execute(db);
  return result.rows[0] ?? null;
}

export function registerAdminMediaRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  app.get('/api/v1/admin/media', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;

    const result = await sql<MediaAssetRow>`
      SELECT id, storage_key, original_filename, mime_type, byte_size, sha256, status,
             created_by, created_at, uploaded_at, storage_etag, storage_checksum_sha256
      FROM platform_media_assets
      WHERE status <> 'archived'
      ORDER BY created_at DESC
      LIMIT 200
    `.execute(auth.database);

    return { assets: result.rows.map(publicAsset) };
  });

  app.post('/api/v1/admin/media/reserve', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const parsed = reserveMediaSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error', details: parsed.error.flatten() });

    const assetId = randomUUID();
    const versionId = randomUUID();
    let upload;
    try {
      upload = await storage.reserveUpload({
        workspaceId: 'platform-media',
        documentId: assetId,
        versionId,
        originalFilename: parsed.data.original_filename,
        mimeType: parsed.data.mime_type,
        byteSize: parsed.data.byte_size,
        sha256: parsed.data.sha256,
      });
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }

    const insert = await sql<MediaAssetRow>`
      INSERT INTO platform_media_assets (
        id, storage_key, original_filename, mime_type, byte_size, sha256, status, created_by
      ) VALUES (
        ${assetId}::uuid,
        ${upload.storageKey},
        ${parsed.data.original_filename},
        ${parsed.data.mime_type},
        ${parsed.data.byte_size},
        ${parsed.data.sha256},
        'reserved',
        ${auth.access.userId}::uuid
      )
      RETURNING id, storage_key, original_filename, mime_type, byte_size, sha256, status,
                created_by, created_at, uploaded_at, storage_etag, storage_checksum_sha256
    `.execute(auth.database);
    const asset = insert.rows[0]!;

    await auditAdminAction(auth.database, auth.access, 'media.reserved', 'platform_media_asset', asset.id, {
      filename: asset.original_filename,
      mime_type: asset.mime_type,
      byte_size: Number(asset.byte_size),
    });

    return reply.code(201).send({ asset: publicAsset(asset), upload });
  });

  app.post('/api/v1/admin/media/:id/complete', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = uuidParamsSchema.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_media_id' });

    const asset = await findMedia(auth.database, params.data.id);
    if (!asset || asset.status === 'archived') return reply.code(404).send({ error: 'media_not_found' });
    if (asset.status === 'uploaded') return { replayed: true, asset: publicAsset(asset) };

    let object;
    try {
      object = await storage.headObject(asset.storage_key);
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }

    if (!object.exists) return reply.code(409).send({ error: 'upload_not_found' });
    if (object.byteSize != null && Number(object.byteSize) !== Number(asset.byte_size)) {
      await sql`UPDATE platform_media_assets SET status = 'failed' WHERE id = ${asset.id}::uuid`.execute(auth.database);
      return reply.code(422).send({ error: 'upload_size_mismatch' });
    }
    if (object.mimeType && object.mimeType !== asset.mime_type) {
      await sql`UPDATE platform_media_assets SET status = 'failed' WHERE id = ${asset.id}::uuid`.execute(auth.database);
      return reply.code(422).send({ error: 'upload_mime_mismatch' });
    }

    const expectedChecksum = expectedChecksumBase64(asset.sha256);
    if (object.checksumSha256 && object.checksumSha256 !== expectedChecksum) {
      await sql`
        UPDATE platform_media_assets
        SET status = 'failed', storage_checksum_sha256 = ${object.checksumSha256}, storage_etag = ${object.etag ?? null}
        WHERE id = ${asset.id}::uuid
      `.execute(auth.database);
      return reply.code(422).send({ error: 'upload_checksum_mismatch' });
    }

    const completed = await sql<MediaAssetRow>`
      UPDATE platform_media_assets
      SET status = 'uploaded', uploaded_at = now(), storage_etag = ${object.etag ?? null},
          storage_checksum_sha256 = ${object.checksumSha256 ?? null}
      WHERE id = ${asset.id}::uuid
      RETURNING id, storage_key, original_filename, mime_type, byte_size, sha256, status,
                created_by, created_at, uploaded_at, storage_etag, storage_checksum_sha256
    `.execute(auth.database);
    const saved = completed.rows[0]!;

    await auditAdminAction(auth.database, auth.access, 'media.uploaded', 'platform_media_asset', saved.id, {
      filename: saved.original_filename,
    });
    return { replayed: false, asset: publicAsset(saved) };
  });

  app.delete('/api/v1/admin/media/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = uuidParamsSchema.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_media_id' });

    const asset = await findMedia(auth.database, params.data.id);
    if (!asset) return reply.code(404).send({ error: 'media_not_found' });
    await sql`UPDATE platform_media_assets SET status = 'archived' WHERE id = ${asset.id}::uuid`.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'media.archived', 'platform_media_asset', asset.id, {
      filename: asset.original_filename,
    });
    return reply.code(204).send();
  });

  app.get('/api/v1/public/media/:id', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const params = uuidParamsSchema.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_media_id' });
    const asset = await findMedia(db, params.data.id);
    if (!asset || asset.status !== 'uploaded') return reply.code(404).send({ error: 'media_not_found' });

    try {
      const readUrl = await storage.createReadUrl(asset.storage_key, 10 * 60);
      return reply.code(302).header('location', readUrl).header('cache-control', 'public, max-age=300').send();
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }
  });
}
