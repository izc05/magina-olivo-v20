import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { updateWorkCommercialSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerWorkCommercialRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.patch('/api/v1/works/:workId/commercial', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsedWorkId = uuidSchema.safeParse((request.params as { workId?: string }).workId);
    if (!parsedWorkId.success) return reply.code(400).send({ error: 'invalid_work_id' });
    const input = parseBody(updateWorkCommercialSchema, request.body, reply);
    if (!input) return;

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
    const collected = input.collected_eur ?? work.collected_eur ?? undefined;
    if (charge !== undefined && collected !== undefined && collected > charge) {
      return reply.code(400).send({ error: 'collected_exceeds_charge' });
    }
    if (input.payment_status === 'paid' && charge !== undefined && collected !== charge) {
      return reply.code(400).send({ error: 'paid_requires_full_collection' });
    }

    const result = await sql`
      UPDATE work_records
      SET quoted_amount_eur = COALESCE(${input.quoted_amount_eur ?? null}, quoted_amount_eur),
          charge_eur = COALESCE(${input.charge_eur ?? null}, charge_eur),
          collected_eur = COALESCE(${input.collected_eur ?? null}, collected_eur),
          payment_status = ${input.payment_status},
          invoice_reference = COALESCE(${input.invoice_reference ?? null}, invoice_reference),
          updated_at = now()
      WHERE id = ${parsedWorkId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
      RETURNING *
    `.execute(database);

    return { work: result.rows[0] };
  });

  app.get('/api/v1/works/receivables', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const result = await sql`
      SELECT wr.*, p.display_name AS customer_name,
             GREATEST(COALESCE(wr.charge_eur, 0) - COALESCE(wr.collected_eur, 0), 0)::double precision AS pending_eur
      FROM work_records wr
      LEFT JOIN parties p ON p.id = wr.customer_party_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.performed_for = 'third-party'
        AND wr.payment_status IN ('pending','partial')
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
    `.execute(database);

    return { receivables: result.rows };
  });
}
