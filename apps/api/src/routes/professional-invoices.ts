import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { createProfessionalInvoiceSchema, updateProfessionalInvoiceSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

export function registerProfessionalInvoiceRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/invoice-candidates', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { customerId?: string };
    if (!query.customerId) return reply.code(400).send({ error: 'customer_id_required' });
    const customer = uuidSchema.safeParse(query.customerId);
    if (!customer.success) return reply.code(400).send({ error: 'invalid_customer_id' });

    const result = await sql`
      SELECT wr.id, wr.occurred_on::text, wr.title, wr.charge_eur::double precision,
             wr.quoted_amount_eur::double precision, wr.invoice_reference,
             cs.name AS site_name,
             COALESCE((SELECT SUM(wc.amount_eur) FROM work_collections wc WHERE wc.work_id = wr.id), 0)::double precision AS collected_eur
      FROM work_records wr
      LEFT JOIN customer_sites cs ON cs.id = wr.customer_site_id
      WHERE wr.workspace_id = ${context.workspaceId}::uuid
        AND wr.customer_party_id = ${customer.data}::uuid
        AND wr.performed_for = 'third-party'
        AND NOT EXISTS (
          SELECT 1 FROM professional_invoice_works piw
          JOIN professional_invoices pi ON pi.id = piw.invoice_id
          WHERE piw.work_id = wr.id AND pi.status <> 'void'
        )
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
    `.execute(database);
    return { works: result.rows };
  });

  app.get('/api/v1/professional/invoices', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { customerId?: string };
    const customer = query.customerId ? uuidSchema.safeParse(query.customerId) : null;
    if (customer && !customer.success) return reply.code(400).send({ error: 'invalid_customer_id' });

    const result = await sql`
      SELECT pi.*, p.display_name AS customer_name,
             COALESCE((SELECT COUNT(*) FROM professional_invoice_works piw WHERE piw.invoice_id = pi.id), 0)::int AS work_count,
             COALESCE((SELECT SUM(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id = piw.work_id WHERE piw.invoice_id = pi.id), 0)::double precision AS collected_eur
      FROM professional_invoices pi
      JOIN parties p ON p.id = pi.customer_party_id
      WHERE pi.workspace_id = ${context.workspaceId}::uuid
        ${customer?.success ? sql`AND pi.customer_party_id = ${customer.data}::uuid` : sql``}
      ORDER BY pi.issued_on DESC NULLS LAST, pi.created_at DESC
      LIMIT 200
    `.execute(database);

    return { invoices: result.rows.map((row) => {
      const value = row as Record<string, unknown> & { total_eur: number | string; collected_eur: number | string };
      const total = Number(value.total_eur ?? 0);
      const collected = Number(value.collected_eur ?? 0);
      return { ...value, pending_eur: Math.max(total - collected, 0) };
    }) };
  });

  app.post('/api/v1/professional/invoices', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createProfessionalInvoiceSchema, request.body, reply);
    if (!input) return;

    const replay = await sql`SELECT * FROM professional_invoices WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, invoice: replay.rows[0] });

    const totalFromWorks = input.works.reduce((sum, item) => sum + item.amount_eur, 0);
    if (Math.abs(totalFromWorks - input.total_eur) > 0.01) {
      return reply.code(400).send({ error: 'invoice_work_amounts_do_not_match_total' });
    }

    const ids = input.works.map((item) => item.work_id);
    const invoiceId = input.entity_id ?? randomUUID();
    const saved = await database.transaction().execute(async (trx) => {
      const works = await sql<{
        id: string; customer_party_id: string | null; performed_for: string; charge_eur: number | string | null; active_invoice_id: string | null;
      }>`
        SELECT wr.id, wr.customer_party_id, wr.performed_for, wr.charge_eur,
               (SELECT piw.invoice_id FROM professional_invoice_works piw JOIN professional_invoices pi ON pi.id = piw.invoice_id WHERE piw.work_id = wr.id AND pi.status <> 'void' LIMIT 1) AS active_invoice_id
        FROM work_records wr
        WHERE wr.workspace_id = ${context.workspaceId}::uuid AND wr.id = ANY(${ids}::uuid[])
        FOR UPDATE
      `.execute(trx);

      if (works.rows.length !== ids.length) throw new Error('work_not_found');
      for (const work of works.rows) {
        if (work.performed_for !== 'third-party' || work.customer_party_id !== input.customer_party_id) throw new Error(`invoice_work_customer_mismatch:${work.id}`);
        if (work.active_invoice_id) throw new Error(`work_already_invoiced:${work.id}`);
      }

      const invoice = await sql`
        INSERT INTO professional_invoices (
          id, workspace_id, customer_party_id, client_operation_id, invoice_number,
          issued_on, due_on, status, subtotal_eur, tax_eur, total_eur, notes, created_by
        ) VALUES (
          ${invoiceId}::uuid, ${context.workspaceId}::uuid, ${input.customer_party_id}::uuid,
          ${input.client_operation_id}::uuid, ${input.invoice_number ?? null}, ${input.issued_on ?? null}::date,
          ${input.due_on ?? null}::date, ${input.status}, ${input.subtotal_eur}, ${input.tax_eur}, ${input.total_eur},
          ${input.notes ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);

      for (const item of input.works) {
        await sql`INSERT INTO professional_invoice_works (invoice_id, work_id, amount_eur) VALUES (${invoiceId}::uuid, ${item.work_id}::uuid, ${item.amount_eur})`.execute(trx);
      }
      if (input.status === 'issued' && input.invoice_number) {
        await sql`UPDATE work_records SET invoice_reference = ${input.invoice_number}, updated_at = now() WHERE id = ANY(${ids}::uuid[])`.execute(trx);
      }
      return invoice.rows[0];
    }).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : '';
      if (message === 'work_not_found') return { __error: 'work_not_found' } as const;
      if (message.startsWith('invoice_work_customer_mismatch:')) return { __error: 'invoice_work_customer_mismatch', work_id: message.split(':')[1] } as const;
      if (message.startsWith('work_already_invoiced:')) return { __error: 'work_already_invoiced', work_id: message.split(':')[1] } as const;
      throw error;
    });

    if (saved && typeof saved === 'object' && '__error' in saved) {
      if (saved.__error === 'work_not_found') return reply.code(404).send({ error: saved.__error });
      return reply.code(409).send(saved);
    }
    return reply.code(201).send({ replayed: false, invoice: saved });
  });

  app.patch('/api/v1/professional/invoices/:invoiceId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsedId = uuidSchema.safeParse((request.params as { invoiceId?: string }).invoiceId);
    if (!parsedId.success) return reply.code(400).send({ error: 'invalid_invoice_id' });
    const input = parseBody(updateProfessionalInvoiceSchema, request.body, reply);
    if (!input) return;

    const existing = await sql<{ invoice_number: string | null; issued_on: string | null; status: string }>`
      SELECT invoice_number, issued_on::text, status FROM professional_invoices
      WHERE id = ${parsedId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const current = existing.rows[0];
    if (!current) return reply.code(404).send({ error: 'invoice_not_found' });
    if (current.status === 'void') return reply.code(409).send({ error: 'void_invoice_is_immutable' });

    const nextStatus = input.status ?? current.status;
    const nextNumber = input.invoice_number ?? current.invoice_number;
    const nextIssued = input.issued_on ?? current.issued_on;
    if (nextStatus === 'issued' && (!nextNumber || !nextIssued)) return reply.code(400).send({ error: 'issued_invoice_requires_number_and_date' });

    const result = await database.transaction().execute(async (trx) => {
      const invoice = await sql`
        UPDATE professional_invoices
        SET invoice_number = COALESCE(${input.invoice_number ?? null}, invoice_number),
            issued_on = COALESCE(${input.issued_on ?? null}::date, issued_on),
            due_on = COALESCE(${input.due_on ?? null}::date, due_on),
            status = ${nextStatus}, notes = COALESCE(${input.notes ?? null}, notes), updated_at = now()
        WHERE id = ${parsedId.data}::uuid AND workspace_id = ${context.workspaceId}::uuid
        RETURNING *
      `.execute(trx);
      if (nextStatus === 'issued' && nextNumber) {
        await sql`UPDATE work_records wr SET invoice_reference = ${nextNumber}, updated_at = now() FROM professional_invoice_works piw WHERE piw.invoice_id = ${parsedId.data}::uuid AND piw.work_id = wr.id`.execute(trx);
      }
      if (nextStatus === 'void') {
        await sql`UPDATE work_records wr SET invoice_reference = NULL, updated_at = now() FROM professional_invoice_works piw WHERE piw.invoice_id = ${parsedId.data}::uuid AND piw.work_id = wr.id AND wr.invoice_reference = ${current.invoice_number}`.execute(trx);
      }
      return invoice.rows[0];
    });
    return { invoice: result };
  });
}
