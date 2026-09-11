import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const entityTypeSchema = z.enum(['professional_quote', 'professional_invoice']);
const channelSchema = z.enum(['email', 'whatsapp', 'share', 'link', 'other']);
const createDeliverySchema = z.object({
  client_operation_id: z.string().uuid(),
  entity_type: entityTypeSchema,
  entity_id: z.string().uuid(),
  document_id: z.string().uuid().optional(),
  channel: channelSchema,
  recipient: z.string().trim().max(320).optional(),
  note: z.string().trim().max(2000).optional(),
});
const confirmSchema = z.object({ confirmed_sent: z.boolean() });
const decisionSchema = z.object({
  decision: z.enum(['accepted', 'rejected']),
  delivery_id: z.string().uuid().optional(),
  note: z.string().trim().max(2000).optional(),
});

async function entityExists(db: DatabaseClient, workspaceId: string, type: z.infer<typeof entityTypeSchema>, id: string) {
  if (type === 'professional_quote') {
    const result = await sql<{ id: string }>`SELECT id FROM professional_quotes WHERE id=${id}::uuid AND workspace_id=${workspaceId}::uuid`.execute(db);
    return Boolean(result.rows[0]);
  }
  const result = await sql<{ id: string }>`SELECT id FROM professional_invoices WHERE id=${id}::uuid AND workspace_id=${workspaceId}::uuid`.execute(db);
  return Boolean(result.rows[0]);
}

export function registerProfessionalDeliveryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/deliveries', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { entityType?: string; entityId?: string };
    const type = entityTypeSchema.safeParse(query.entityType);
    const id = z.string().uuid().safeParse(query.entityId);
    if (!type.success || !id.success) return reply.code(400).send({ error: 'entity_type_and_id_required' });

    const result = await sql`
      SELECT pdd.*, d.title AS document_title, d.kind AS document_kind
      FROM professional_document_deliveries pdd
      LEFT JOIN documents d ON d.id = pdd.document_id AND d.workspace_id = pdd.workspace_id
      WHERE pdd.workspace_id=${context.workspaceId}::uuid
        AND pdd.entity_type=${type.data}
        AND pdd.entity_id=${id.data}::uuid
      ORDER BY pdd.created_at DESC
      LIMIT 100
    `.execute(database);
    return { deliveries: result.rows, semantics: { prepared: 'share flow opened or prepared; not proof of receipt', confirmed_sent: 'user explicitly confirmed the document was sent' } };
  });

  app.post('/api/v1/professional/deliveries', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = createDeliverySchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_delivery', issues: parsed.error.issues });
    const input = parsed.data;

    const replay = await sql`SELECT * FROM professional_document_deliveries WHERE workspace_id=${context.workspaceId}::uuid AND client_operation_id=${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, delivery: replay.rows[0] });
    if (!(await entityExists(database, context.workspaceId, input.entity_type, input.entity_id))) return reply.code(404).send({ error: 'commercial_entity_not_found' });

    if (input.document_id) {
      const expectedDomain = input.entity_type;
      const doc = await sql<{ id: string }>`
        SELECT d.id
        FROM documents d
        JOIN attachment_links al ON al.document_id=d.id AND al.workspace_id=d.workspace_id
        WHERE d.id=${input.document_id}::uuid
          AND d.workspace_id=${context.workspaceId}::uuid
          AND d.status='active'
          AND al.domain_type=${expectedDomain}
          AND al.domain_record_id=${input.entity_id}::uuid
        LIMIT 1
      `.execute(database);
      if (!doc.rows[0]) return reply.code(409).send({ error: 'document_not_linked_to_commercial_entity' });
    }

    const id = randomUUID();
    const result = await sql`
      INSERT INTO professional_document_deliveries (
        id, workspace_id, entity_type, entity_id, document_id, client_operation_id,
        channel, recipient, status, note, created_by
      ) VALUES (
        ${id}::uuid, ${context.workspaceId}::uuid, ${input.entity_type}, ${input.entity_id}::uuid,
        ${input.document_id ?? null}::uuid, ${input.client_operation_id}::uuid,
        ${input.channel}, ${input.recipient ?? null}, 'prepared', ${input.note ?? null}, ${context.userId}::uuid
      ) RETURNING *
    `.execute(database);
    return reply.code(201).send({ replayed: false, delivery: result.rows[0] });
  });

  app.patch('/api/v1/professional/deliveries/:deliveryId/confirm', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const id = z.string().uuid().safeParse((request.params as { deliveryId?: string }).deliveryId);
    const body = confirmSchema.safeParse(request.body);
    if (!id.success || !body.success) return reply.code(400).send({ error: 'invalid_confirmation' });

    const status = body.data.confirmed_sent ? 'confirmed_sent' : 'cancelled';
    const result = await sql`
      UPDATE professional_document_deliveries
      SET status=${status}, confirmed_sent_at=CASE WHEN ${body.data.confirmed_sent} THEN now() ELSE NULL END, updated_at=now()
      WHERE id=${id.data}::uuid AND workspace_id=${context.workspaceId}::uuid AND status='prepared'
      RETURNING *
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'prepared_delivery_not_found' });
    return { delivery: result.rows[0] };
  });

  app.post('/api/v1/professional/quotes/:quoteId/decision', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const quoteId = z.string().uuid().safeParse((request.params as { quoteId?: string }).quoteId);
    const input = decisionSchema.safeParse(request.body);
    if (!quoteId.success || !input.success) return reply.code(400).send({ error: 'invalid_quote_decision' });

    const result = await database.transaction().execute(async (trx) => {
      const quote = await sql<{ id: string; status: string }>`SELECT id,status FROM professional_quotes WHERE id=${quoteId.data}::uuid AND workspace_id=${context.workspaceId}::uuid FOR UPDATE`.execute(trx);
      if (!quote.rows[0]) return { error: 'quote_not_found' as const };
      if (quote.rows[0].status === 'converted') return { error: 'converted_quote_is_immutable' as const };

      if (input.data.delivery_id) {
        const delivery = await sql<{ id: string }>`
          SELECT id FROM professional_document_deliveries
          WHERE id=${input.data.delivery_id}::uuid
            AND workspace_id=${context.workspaceId}::uuid
            AND entity_type='professional_quote'
            AND entity_id=${quoteId.data}::uuid
            AND status='confirmed_sent'
        `.execute(trx);
        if (!delivery.rows[0]) return { error: 'confirmed_quote_delivery_not_found' as const };
      }

      const decisionId = randomUUID();
      const decision = await sql`
        INSERT INTO professional_quote_decisions (
          id, workspace_id, quote_id, delivery_id, decision, note, created_by
        ) VALUES (
          ${decisionId}::uuid, ${context.workspaceId}::uuid, ${quoteId.data}::uuid,
          ${input.data.delivery_id ?? null}::uuid, ${input.data.decision}, ${input.data.note ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);
      await sql`
        UPDATE professional_quotes
        SET status=${input.data.decision},
            accepted_at=CASE WHEN ${input.data.decision}='accepted' THEN now() ELSE accepted_at END,
            rejected_at=CASE WHEN ${input.data.decision}='rejected' THEN now() ELSE rejected_at END,
            updated_at=now()
        WHERE id=${quoteId.data}::uuid
      `.execute(trx);
      return { decision: decision.rows[0] };
    });

    if ('error' in result) return reply.code(result.error === 'quote_not_found' ? 404 : 409).send(result);
    return reply.code(201).send(result);
  });
}
