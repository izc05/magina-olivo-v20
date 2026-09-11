import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { createWorkCollectionSchema, updateWorkCommercialSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

function derivePaymentStatus(charge: number | undefined, collected: number | undefined) {
  if (charge === undefined || charge <= 0) return 'pending' as const;
  if ((collected ?? 0) >= charge) return 'paid' as const;
  if ((collected ?? 0) > 0) return 'partial' as const;
  return 'pending' as const;
}

export function registerWorkCommercialRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.patch('/api/v1/works/:workId/commercial', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsedWorkId = uuidSchema.safeParse((request.params as { workId?: string }).workId);
    if (!parsedWorkId.success) return reply.code(400).send({ error: 'invalid_work_id' });
    const input = parseBody(updateWorkCommercialSchema, request.body, reply);
    if (!input) return;
    if (input.collected_eur !== undefined) {
      return reply.code(400).send({ error: 'use_work_collections_endpoint' });
    }

    const existing = await sql<{
      id: string;
      performed_for: string;
      quoted_amount_eur: number | null;
      charge_eur: number | null;
      collected_eur: number | null;
    }>`
      SELECT id, performed_for, quoted_amount_eur::double precision, charge_eur::double precision, collected_eur::double precision
      FROM work_records
      WHERE id = ${parsedWorkId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);

    const work = existing.rows[0];
    if (!work) return reply.code(404).send({ error: 'work_not_found' });
    if (work.performed_for !== 'third-party') return reply.code(409).send({ error: 'commercial_state_only_for_third_party_work' });

    const charge = input.charge_eur ?? work.charge_eur ?? undefined;
    const collected = work.collected_eur ?? undefined;
    if (charge !== undefined && collected !== undefined && collected > charge) {
      return reply.code(400).send({ error: 'charge_below_collected_amount' });
    }

    const paymentStatus = derivePaymentStatus(charge, collected);
    const result = await sql`
      UPDATE work_records
      SET quoted_amount_eur = COALESCE(${input.quoted_amount_eur ?? null}, quoted_amount_eur),
          charge_eur = COALESCE(${input.charge_eur ?? null}, charge_eur),
          payment_status = ${paymentStatus},
          invoice_reference = COALESCE(${input.invoice_reference ?? null}, invoice_reference),
          updated_at = now()
      WHERE id = ${parsedWorkId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
      RETURNING *
    `.execute(database);

    return { work: result.rows[0], payment_status_derived: true };
  });

  app.post('/api/v1/works/:workId/collections', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsedWorkId = uuidSchema.safeParse((request.params as { workId?: string }).workId);
    if (!parsedWorkId.success) return reply.code(400).send({ error: 'invalid_work_id' });
    const input = parseBody(createWorkCollectionSchema, request.body, reply);
    if (!input) return;

    const replay = await sql`
      SELECT * FROM work_collections
      WHERE workspace_id = ${context.workspaceId}::uuid
        AND client_operation_id = ${input.client_operation_id}::uuid
    `.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, collection: replay.rows[0] });

    const saved = await database.transaction().execute(async (trx) => {
      const locked = await sql<{
        id: string;
        performed_for: string;
        charge_eur: number | null;
      }>`
        SELECT id, performed_for, charge_eur::double precision
        FROM work_records
        WHERE id = ${parsedWorkId.data}::uuid
          AND workspace_id = ${context.workspaceId}::uuid
        FOR UPDATE
      `.execute(trx);
      const work = locked.rows[0];
      if (!work) return { error: 'work_not_found' as const };
      if (work.performed_for !== 'third-party') return { error: 'collections_only_for_third_party_work' as const };
      if (work.charge_eur === null || work.charge_eur <= 0) return { error: 'collection_requires_charge' as const };

      const already = await sql<{ collected_eur: number }>`
        SELECT COALESCE(SUM(amount_eur), 0)::double precision AS collected_eur
        FROM work_collections
        WHERE work_id = ${parsedWorkId.data}::uuid
      `.execute(trx);
      const currentCollected = already.rows[0]?.collected_eur ?? 0;
      const nextCollected = currentCollected + input.amount_eur;
      if (nextCollected > work.charge_eur + 0.001) return { error: 'collection_exceeds_pending_amount' as const };

      const collectionId = input.entity_id ?? randomUUID();
      const collection = await sql`
        INSERT INTO work_collections (
          id, workspace_id, work_id, client_operation_id, collected_on,
          amount_eur, method, reference, notes, created_by
        ) VALUES (
          ${collectionId}::uuid, ${context.workspaceId}::uuid, ${parsedWorkId.data}::uuid,
          ${input.client_operation_id}::uuid, ${input.collected_on}::date,
          ${input.amount_eur}, ${input.method ?? null}, ${input.reference ?? null}, ${input.notes ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);

      const paymentStatus = derivePaymentStatus(work.charge_eur, nextCollected);
      await sql`
        UPDATE work_records
        SET collected_eur = ${nextCollected}, payment_status = ${paymentStatus}, updated_at = now()
        WHERE id = ${parsedWorkId.data}::uuid
      `.execute(trx);

      return {
        collection: collection.rows[0],
        collected_eur: nextCollected,
        pending_eur: Math.max(work.charge_eur - nextCollected, 0),
        payment_status: paymentStatus,
      };
    });

    if ('error' in saved) {
      const status = saved.error === 'work_not_found' ? 404 : 409;
      return reply.code(status).send({ error: saved.error });
    }
    return reply.code(201).send({ replayed: false, ...saved });
  });

  app.get('/api/v1/works/:workId/collections', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsedWorkId = uuidSchema.safeParse((request.params as { workId?: string }).workId);
    if (!parsedWorkId.success) return reply.code(400).send({ error: 'invalid_work_id' });

    const work = await sql<{ id: string }>`
      SELECT id FROM work_records
      WHERE id = ${parsedWorkId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    if (!work.rows[0]) return reply.code(404).send({ error: 'work_not_found' });

    const collections = await sql`
      SELECT id, collected_on::text, amount_eur::double precision, method, reference, notes, created_at
      FROM work_collections
      WHERE workspace_id = ${context.workspaceId}::uuid AND work_id = ${parsedWorkId.data}::uuid
      ORDER BY collected_on DESC, created_at DESC
    `.execute(database);
    return { collections: collections.rows };
  });

  app.get('/api/v1/works/receivables', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const result = await sql`
      SELECT wr.*, p.display_name AS customer_name,
             COALESCE((SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id), 0)::double precision AS collected_from_movements_eur,
             GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE((SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id), 0), 0)::double precision AS pending_eur
      FROM work_records wr
      LEFT JOIN parties p ON p.id = wr.customer_party_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
        AND GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE((SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id), 0), 0) > 0
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
    `.execute(database);

    return { receivables: result.rows };
  });
}
