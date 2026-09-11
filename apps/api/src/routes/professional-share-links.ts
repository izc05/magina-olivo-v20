import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyReply } from 'fastify';
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
  delivery_id: z.string().uuid().optional(),
  expires_in_days: z.number().int().min(1).max(30).default(7),
});
const publicDecisionSchema = z.object({
  decision: z.enum(['accepted', 'rejected']),
  customer_name: z.string().trim().min(1).max(200).optional(),
  note: z.string().trim().max(2000).optional(),
});

function tokenHash(token: string) {
  return createHash('sha256').update(token).digest('hex');
}

function publicUserAgent(request: { headers: Record<string, unknown> }) {
  const value = request.headers['user-agent'];
  return typeof value === 'string' ? value.slice(0, 512) : null;
}

type PublicShareAccessState = {
  id: string;
  revoked_at: Date | null;
  expires_at: Date;
};

async function readPublicShareAccessState(database: DatabaseClient, token: string) {
  const result = await sql<PublicShareAccessState>`
    SELECT id, revoked_at, expires_at
    FROM professional_public_share_links
    WHERE token_hash=${tokenHash(token)}
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function requireLivePublicShare(database: DatabaseClient, token: string, reply: FastifyReply) {
  const share = await readPublicShareAccessState(database, token);
  if (!share) {
    reply.code(404).send({ error: 'share_link_not_found' });
    return null;
  }
  if (share.revoked_at) {
    reply.code(410).send({ error: 'share_link_revoked' });
    return null;
  }
  if (share.expires_at.getTime() <= Date.now()) {
    reply.code(410).send({ error: 'share_link_expired' });
    return null;
  }
  return share;
}

export function registerProfessionalShareLinkRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  app.post('/api/v1/professional/share-links', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = createSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_share_link', issues: parsed.error.issues });
    const input = parsed.data;

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
        AND al.domain_type=${input.entity_type}
        AND al.domain_record_id=${input.entity_id}::uuid
      LIMIT 1
    `.execute(database);
    if (!document.rows[0]) return reply.code(409).send({ error: 'verified_document_not_linked_to_entity' });

    if (input.delivery_id) {
      const delivery = await sql<{ id: string }>`
        SELECT id FROM professional_document_deliveries
        WHERE id=${input.delivery_id}::uuid
          AND workspace_id=${context.workspaceId}::uuid
          AND entity_type=${input.entity_type}
          AND entity_id=${input.entity_id}::uuid
          AND document_id=${input.document_id}::uuid
          AND status IN ('prepared','confirmed_sent')
      `.execute(database);
      if (!delivery.rows[0]) return reply.code(409).send({ error: 'delivery_not_linked_to_document' });
    }

    const token = randomBytes(32).toString('base64url');
    const id = randomUUID();
    const result = await sql`
      INSERT INTO professional_public_share_links (
        id, workspace_id, entity_type, entity_id, document_id, delivery_id, token_hash, expires_at, created_by
      ) VALUES (
        ${id}::uuid, ${context.workspaceId}::uuid, ${input.entity_type}, ${input.entity_id}::uuid,
        ${input.document_id}::uuid, ${input.delivery_id ?? null}::uuid,
        ${tokenHash(token)}, now() + (${input.expires_in_days}::int * interval '1 day'), ${context.userId}::uuid
      ) RETURNING id, delivery_id, expires_at, created_at
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
      SELECT id, entity_type, entity_id, document_id, delivery_id, expires_at, revoked_at, access_count, last_accessed_at, created_at
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

  app.get('/api/public/v1/professional/share/:token/info', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const token = z.string().min(30).max(100).safeParse((request.params as { token?: string }).token);
    if (!token.success) return reply.code(404).send({ error: 'share_link_not_found' });
    const liveShare = await requireLivePublicShare(database, token.data, reply);
    if (!liveShare) return;

    const result = await sql<{
      id: string; entity_type: string; entity_id: string; document_id: string; delivery_id: string | null;
      expires_at: Date; access_count: number; status: string | null; title: string | null; number: string | null;
      issued_on: string | null; valid_until: string | null; total_eur: number | string | null;
      issuer_snapshot_json: Record<string, unknown> | null; customer_snapshot_json: Record<string, unknown> | null;
      decision: string | null; decided_at: Date | null; delivery_status: string | null;
      quote_in_date: boolean | null;
    }>`
      SELECT psl.id, psl.entity_type, psl.entity_id, psl.document_id, psl.delivery_id,
             psl.expires_at, psl.access_count,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.status ELSE pi.status END AS status,
             pq.title,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.quote_number ELSE pi.invoice_number END AS number,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.issued_on::text ELSE pi.issued_on::text END AS issued_on,
             pq.valid_until::text,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.total_eur ELSE pi.total_eur END AS total_eur,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.issuer_snapshot_json ELSE pi.issuer_snapshot_json END AS issuer_snapshot_json,
             CASE WHEN psl.entity_type='professional_quote' THEN pq.customer_snapshot_json ELSE pi.customer_snapshot_json END AS customer_snapshot_json,
             pqd.decision, pqd.decided_at,
             pdd.status AS delivery_status,
             CASE WHEN psl.entity_type='professional_quote' THEN (pq.valid_until IS NULL OR pq.valid_until >= current_date) ELSE NULL END AS quote_in_date
      FROM professional_public_share_links psl
      LEFT JOIN professional_quotes pq ON psl.entity_type='professional_quote' AND pq.id=psl.entity_id AND pq.workspace_id=psl.workspace_id
      LEFT JOIN professional_invoices pi ON psl.entity_type='professional_invoice' AND pi.id=psl.entity_id AND pi.workspace_id=psl.workspace_id
      LEFT JOIN professional_document_deliveries pdd ON pdd.id=psl.delivery_id AND pdd.workspace_id=psl.workspace_id
      LEFT JOIN LATERAL (
        SELECT decision, decided_at FROM professional_quote_decisions
        WHERE share_link_id=psl.id AND decision_source='public_link'
        ORDER BY decided_at DESC LIMIT 1
      ) pqd ON true
      WHERE psl.id=${liveShare.id}::uuid
        AND psl.revoked_at IS NULL
        AND psl.expires_at > now()
      LIMIT 1
    `.execute(database);
    const share = result.rows[0];
    if (!share) return reply.code(410).send({ error: 'share_link_no_longer_live' });
    return {
      share: {
        entity_type: share.entity_type,
        document_id: share.document_id,
        expires_at: share.expires_at,
        status: share.status,
        title: share.title,
        number: share.number,
        issued_on: share.issued_on,
        valid_until: share.valid_until,
        total_eur: Number(share.total_eur ?? 0),
        issuer: share.issuer_snapshot_json,
        customer: share.customer_snapshot_json,
        decision: share.decision,
        decided_at: share.decided_at,
        can_decide: share.entity_type === 'professional_quote' && share.delivery_status === 'confirmed_sent' && !share.decision && share.quote_in_date !== false,
        file_path: `/api/public/v1/professional/share/${token.data}/file`,
      },
    };
  });

  app.get('/api/public/v1/professional/share/:token/file', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const token = z.string().min(30).max(100).safeParse((request.params as { token?: string }).token);
    if (!token.success) return reply.code(404).send({ error: 'share_link_not_found' });
    const liveShare = await requireLivePublicShare(database, token.data, reply);
    if (!liveShare) return;
    const result = await sql<{ id: string; storage_key: string }>`
      SELECT psl.id, dv.storage_key
      FROM professional_public_share_links psl
      JOIN documents d ON d.id=psl.document_id AND d.workspace_id=psl.workspace_id AND d.status='active'
      JOIN LATERAL (
        SELECT storage_key FROM document_versions
        WHERE document_id=d.id AND upload_status='uploaded' AND integrity_status='verified'
        ORDER BY version_no DESC LIMIT 1
      ) dv ON true
      WHERE psl.id=${liveShare.id}::uuid
        AND psl.revoked_at IS NULL
        AND psl.expires_at > now()
      LIMIT 1
    `.execute(database);
    const share = result.rows[0];
    if (!share) return reply.code(410).send({ error: 'share_link_no_longer_live' });
    await sql`UPDATE professional_public_share_links SET access_count=access_count+1, last_accessed_at=now() WHERE id=${share.id}::uuid`.execute(database);
    const readUrl = await storage.createReadUrl(share.storage_key, 300);
    return reply.redirect(readUrl);
  });

  app.post('/api/public/v1/professional/share/:token/decision', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const token = z.string().min(30).max(100).safeParse((request.params as { token?: string }).token);
    const body = publicDecisionSchema.safeParse(request.body);
    if (!token.success || !body.success) return reply.code(400).send({ error: 'invalid_public_decision' });
    const liveShare = await requireLivePublicShare(database, token.data, reply);
    if (!liveShare) return;

    const result = await database.transaction().execute(async (trx) => {
      const locked = await sql<{
        id: string; workspace_id: string; entity_id: string; document_id: string; delivery_id: string | null;
        entity_type: string; delivery_status: string | null; quote_status: string | null; quote_in_date: boolean | null;
      }>`
        SELECT psl.id, psl.workspace_id, psl.entity_id, psl.document_id, psl.delivery_id, psl.entity_type,
               pdd.status AS delivery_status, pq.status AS quote_status,
               CASE WHEN psl.entity_type='professional_quote' THEN (pq.valid_until IS NULL OR pq.valid_until >= current_date) ELSE NULL END AS quote_in_date
        FROM professional_public_share_links psl
        LEFT JOIN professional_document_deliveries pdd ON pdd.id=psl.delivery_id AND pdd.workspace_id=psl.workspace_id
        LEFT JOIN professional_quotes pq ON pq.id=psl.entity_id AND pq.workspace_id=psl.workspace_id
        WHERE psl.id=${liveShare.id}::uuid
          AND psl.revoked_at IS NULL
          AND psl.expires_at > now()
        FOR UPDATE OF psl
      `.execute(trx);
      const share = locked.rows[0];
      if (!share) return { error: 'share_link_no_longer_live' as const };
      if (share.entity_type !== 'professional_quote') return { error: 'decisions_only_supported_for_quotes' as const };
      if (share.delivery_status !== 'confirmed_sent' || !share.delivery_id) return { error: 'delivery_not_confirmed' as const };
      if (share.quote_status === 'converted') return { error: 'converted_quote_is_immutable' as const };
      if (share.quote_in_date === false) return { error: 'quote_expired' as const };

      const existing = await sql<{ id: string; decision: string }>`
        SELECT id, decision FROM professional_quote_decisions
        WHERE share_link_id=${share.id}::uuid AND decision_source='public_link'
        LIMIT 1
      `.execute(trx);
      if (existing.rows[0]) return { replayed: true, decision: existing.rows[0] };

      const decisionId = randomUUID();
      const decision = await sql`
        INSERT INTO professional_quote_decisions (
          id, workspace_id, quote_id, delivery_id, share_link_id, decision, note,
          decision_source, requester_ip, user_agent, customer_name, created_by
        ) VALUES (
          ${decisionId}::uuid, ${share.workspace_id}::uuid, ${share.entity_id}::uuid, ${share.delivery_id}::uuid,
          ${share.id}::uuid, ${body.data.decision}, ${body.data.note ?? null}, 'public_link',
          ${request.ip}::inet, ${publicUserAgent(request as unknown as { headers: Record<string, unknown> })}, ${body.data.customer_name ?? null}, NULL
        ) RETURNING id, decision, decided_at
      `.execute(trx);
      await sql`
        UPDATE professional_quotes
        SET status=${body.data.decision},
            accepted_at=CASE WHEN ${body.data.decision}='accepted' THEN now() ELSE accepted_at END,
            rejected_at=CASE WHEN ${body.data.decision}='rejected' THEN now() ELSE rejected_at END,
            updated_at=now()
        WHERE id=${share.entity_id}::uuid AND workspace_id=${share.workspace_id}::uuid
      `.execute(trx);
      return { replayed: false, decision: decision.rows[0] };
    });

    if ('error' in result) {
      const status = result.error === 'share_link_no_longer_live' ? 410 : 409;
      return reply.code(status).send(result);
    }
    return reply.code(result.replayed ? 200 : 201).send(result);
  });

  // Backwards-compatible direct PDF route used by links generated before the public viewer existed.
  app.get('/api/public/v1/professional/share/:token', async (request, reply) => {
    const token = z.string().min(30).max(100).safeParse((request.params as { token?: string }).token);
    if (!token.success) return reply.code(404).send({ error: 'share_link_not_found' });
    return reply.redirect(`/api/public/v1/professional/share/${token.data}/file`);
  });
}
