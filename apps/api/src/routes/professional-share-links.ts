import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import type { StoragePort } from '../storage/port.js';

const entityTypeSchema = z.enum(['professional_quote', 'professional_invoice']);
const createSchema = z.object({
  entity_type: entityTypeSchema,
  entity_id: z.string().uuid(),
  document_id: z.string().uuid(),
  expires_in_days: z.number().int().min(1).max(30).default(7),
});

function tokenHash(token: string) {
  return createHash('sha256').update(token).digest('hex');
}

export function registerProfessionalShareLinkRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  app.post('/api/v1/professional/share-links', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = createSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_share_link', issues: parsed.error.issues });
    const input = parsed.data;

    const expectedDomain = input.entity_type;
    const document = await sql<{ storage_key: string }>`
      SELECT dv.storage_key
      FROM documents d
      JOIN attachment_links al ON al.document_id=d.id AND al.workspace_id=d.workspace_id
      JOIN LATERAL (
        SELECT storage_key FROM document_versions
        WHERE document_id=d.id AND upload_status='uploaded' AND integrity_status='verified'
        ORDER BY version_no DESC LIMIT 1
      ) dv ON true
      WHERE d.id=${input.document_id}::uuid
        AND d.workspace_id=${context.workspaceId}::uuid
        AND d.status='active'
        AND al.domain_type=${expectedDomain}
        AND al.domain_record_id=${input.entity_id}::uuid
      LIMIT 1
    `.execute(database);
    if (!document.rows[0]) return reply.code(409).send({ error: 'verified_document_not_linked_to_entity' });

    const token = randomBytes(32).toString('base64url');
    const id = randomUUID();
    const result = await sql`
      INSERT INTO professional_public_share_links (
        id, workspace_id, entity_type, entity_id, document_id, token_hash, expires_at, created_by
      ) VALUES (
        ${id}::uuid, ${context.workspaceId}::uuid, ${input.entity_type}, ${input.entity_id}::uuid,
        ${input.document_id}::uuid, ${tokenHash(token)}, now() + (${input.expires_in_days}::int * interval '1 day'), ${context.userId}::uuid
      ) RETURNING id, expires_at, created_at
    `.execute(database);
    return reply.code(201).send({ share: result.rows[0], token, path: `/api/public/v1/professional/share/${token}` });
  });

  app.get('/api/v1/professional/share-links', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { entityType?: string; entityId?: string };
    const type = entityTypeSchema.safeParse(query.entityType);
    const id = z.string().uuid().safeParse(query.entityId);
    if (!type.success || !id.success) return reply.code(400).send({ error: 'entity_type_and_id_required' });

    const result = await sql`
      SELECT id, entity_type, entity_id, document_id, expires_at, revoked_at, access_count, last_accessed_at, created_at
      FROM professional_public_share_links
      WHERE workspace_id=${context.workspaceId}::uuid AND entity_type=${type.data} AND entity_id=${id.data}::uuid
      ORDER BY created_at DESC LIMIT 50
    `.execute(database);
    return { links: result.rows };
  });

  app.delete('/api/v1/professional/share-links/:shareId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const id = z.string().uuid().safeParse((request.params as { shareId?: string }).shareId);
    if (!id.success) return reply.code(400).send({ error: 'invalid_share_id' });
    const result = await sql`
      UPDATE professional_public_share_links SET revoked_at=COALESCE(revoked_at, now())
      WHERE id=${id.data}::uuid AND workspace_id=${context.workspaceId}::uuid
      RETURNING id, revoked_at
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'share_link_not_found' });
    return { share: result.rows[0] };
  });

  app.get('/api/public/v1/professional/share/:token', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const token = z.string().min(30).max(100).safeParse((request.params as { token?: string }).token);
    if (!token.success) return reply.code(404).send({ error: 'share_link_not_found' });

    const result = await sql<{ id: string; storage_key: string }>`
      SELECT psl.id, dv.storage_key
      FROM professional_public_share_links psl
      JOIN documents d ON d.id=psl.document_id AND d.workspace_id=psl.workspace_id AND d.status='active'
      JOIN LATERAL (
        SELECT storage_key FROM document_versions
        WHERE document_id=d.id AND upload_status='uploaded' AND integrity_status='verified'
        ORDER BY version_no DESC LIMIT 1
      ) dv ON true
      WHERE psl.token_hash=${tokenHash(token.data)}
        AND psl.revoked_at IS NULL
        AND psl.expires_at > now()
      LIMIT 1
    `.execute(database);
    const share = result.rows[0];
    if (!share) return reply.code(404).send({ error: 'share_link_not_found_or_expired' });

    await sql`UPDATE professional_public_share_links SET access_count=access_count+1, last_accessed_at=now() WHERE id=${share.id}::uuid`.execute(database);
    const readUrl = await storage.createReadUrl(share.storage_key, 300);
    return reply.redirect(readUrl);
  });
}
