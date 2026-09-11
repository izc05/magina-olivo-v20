import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { createProfessionalQuoteSchema, convertProfessionalQuoteSchema, updateProfessionalQuoteStatusSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';
import { writeDomainEffects } from '../domain/effects.js';

export function registerProfessionalQuoteRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/quotes', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { customerId?: string };
    const customer = query.customerId ? uuidSchema.safeParse(query.customerId) : null;
    if (customer && !customer.success) return reply.code(400).send({ error: 'invalid_customer_id' });

    const result = await sql`
      SELECT pq.*, p.display_name AS customer_name, cs.name AS site_name,
             wr.id AS work_id, wr.title AS work_title, wr.charge_eur::double precision AS work_charge_eur,
             COALESCE((SELECT SUM(COALESCE(wp.cost_eur,0)) FROM work_participants wp WHERE wp.work_id = wr.id),0)::double precision +
             COALESCE((SELECT SUM(COALESCE(wr2.cost_eur,0)) FROM work_resources wr2 WHERE wr2.work_id = wr.id),0)::double precision AS actual_cost_eur,
             pi.id AS invoice_id, pi.invoice_number, pi.total_eur::double precision AS invoice_total_eur
      FROM professional_quotes pq
      JOIN parties p ON p.id = pq.customer_party_id
      LEFT JOIN customer_sites cs ON cs.id = pq.customer_site_id
      LEFT JOIN work_records wr ON wr.professional_quote_id = pq.id
      LEFT JOIN professional_invoice_works piw ON piw.work_id = wr.id
      LEFT JOIN professional_invoices pi ON pi.id = piw.invoice_id AND pi.status <> 'void'
      WHERE pq.workspace_id = ${context.workspaceId}::uuid
        ${customer?.success ? sql`AND pq.customer_party_id = ${customer.data}::uuid` : sql``}
      ORDER BY pq.created_at DESC
      LIMIT 200
    `.execute(database);
    return { quotes: result.rows };
  });

  app.post('/api/v1/professional/quotes', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createProfessionalQuoteSchema, request.body, reply);
    if (!input) return;

    const replay = await sql`SELECT * FROM professional_quotes WHERE workspace_id=${context.workspaceId}::uuid AND client_operation_id=${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, quote: replay.rows[0] });

    const customer = await sql<{ id: string }>`SELECT id FROM parties WHERE id=${input.customer_party_id}::uuid AND workspace_id=${context.workspaceId}::uuid AND active=TRUE AND 'customer'=ANY(roles)`.execute(database);
    if (!customer.rows[0]) return reply.code(404).send({ error: 'customer_not_found' });
    if (input.customer_site_id) {
      const site = await sql<{ id: string }>`SELECT id FROM customer_sites WHERE id=${input.customer_site_id}::uuid AND workspace_id=${context.workspaceId}::uuid AND customer_party_id=${input.customer_party_id}::uuid AND active=TRUE`.execute(database);
      if (!site.rows[0]) return reply.code(404).send({ error: 'customer_site_not_found' });
    }

    const quoteId = input.entity_id ?? randomUUID();
    const saved = await database.transaction().execute(async (trx) => {
      const quote = await sql`
        INSERT INTO professional_quotes (
          id, workspace_id, customer_party_id, customer_site_id, client_operation_id, quote_number,
          title, issued_on, valid_until, status, subtotal_eur, tax_eur, total_eur, notes, created_by
        ) VALUES (
          ${quoteId}::uuid, ${context.workspaceId}::uuid, ${input.customer_party_id}::uuid, ${input.customer_site_id ?? null}::uuid,
          ${input.client_operation_id}::uuid, ${input.quote_number ?? null}, ${input.title}, ${input.issued_on ?? null}::date,
          ${input.valid_until ?? null}::date, ${input.status}, ${input.subtotal_eur}, ${input.tax_eur}, ${input.total_eur}, ${input.notes ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);
      for (let index = 0; index < input.lines.length; index += 1) {
        const line = input.lines[index];
        await sql`INSERT INTO professional_quote_lines (quote_id, description, quantity, unit, unit_price_eur, line_total_eur, sort_order) VALUES (${quoteId}::uuid, ${line.description}, ${line.quantity}, ${line.unit ?? null}, ${line.unit_price_eur}, ${line.line_total_eur}, ${index})`.execute(trx);
      }
      return quote.rows[0];
    });
    return reply.code(201).send({ replayed: false, quote: saved });
  });

  app.patch('/api/v1/professional/quotes/:quoteId/status', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const quoteId = uuidSchema.safeParse((request.params as { quoteId?: string }).quoteId);
    if (!quoteId.success) return reply.code(400).send({ error: 'invalid_quote_id' });
    const input = parseBody(updateProfessionalQuoteStatusSchema, request.body, reply);
    if (!input) return;

    const current = await sql<{ status: string }>`SELECT status FROM professional_quotes WHERE id=${quoteId.data}::uuid AND workspace_id=${context.workspaceId}::uuid`.execute(database);
    const existing = current.rows[0];
    if (!existing) return reply.code(404).send({ error: 'quote_not_found' });
    if (existing.status === 'converted') return reply.code(409).send({ error: 'converted_quote_is_immutable' });
    if (existing.status === 'rejected' && input.status !== 'sent') return reply.code(409).send({ error: 'rejected_quote_must_be_resent_before_acceptance' });

    const result = await sql`
      UPDATE professional_quotes
      SET status=${input.status},
          accepted_at=CASE WHEN ${input.status}='accepted' THEN now() ELSE accepted_at END,
          rejected_at=CASE WHEN ${input.status}='rejected' THEN now() ELSE rejected_at END,
          updated_at=now()
      WHERE id=${quoteId.data}::uuid AND workspace_id=${context.workspaceId}::uuid
      RETURNING *
    `.execute(database);
    return { quote: result.rows[0] };
  });

  app.post('/api/v1/professional/quotes/:quoteId/convert', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const quoteId = uuidSchema.safeParse((request.params as { quoteId?: string }).quoteId);
    if (!quoteId.success) return reply.code(400).send({ error: 'invalid_quote_id' });
    const input = parseBody(convertProfessionalQuoteSchema, request.body, reply);
    if (!input) return;

    const replay = await sql`SELECT * FROM work_records WHERE workspace_id=${context.workspaceId}::uuid AND client_operation_id=${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, work: replay.rows[0] });

    const saved = await database.transaction().execute(async (trx) => {
      const locked = await sql<{
        id: string; status: string; customer_party_id: string; customer_site_id: string | null; title: string; total_eur: number | string;
      }>`SELECT id,status,customer_party_id,customer_site_id,title,total_eur FROM professional_quotes WHERE id=${quoteId.data}::uuid AND workspace_id=${context.workspaceId}::uuid FOR UPDATE`.execute(trx);
      const quote = locked.rows[0];
      if (!quote) return { error: 'quote_not_found' as const };
      if (quote.status !== 'accepted') return { error: 'quote_must_be_accepted' as const, status: quote.status };

      if (input.customer_site_id) {
        const site = await sql<{ id: string }>`SELECT id FROM customer_sites WHERE id=${input.customer_site_id}::uuid AND workspace_id=${context.workspaceId}::uuid AND customer_party_id=${quote.customer_party_id}::uuid AND active=TRUE`.execute(trx);
        if (!site.rows[0]) return { error: 'customer_site_not_found' as const };
      }
      if (input.field_id) {
        const field = await sql<{ id: string }>`SELECT id FROM fields WHERE id=${input.field_id}::uuid AND workspace_id=${context.workspaceId}::uuid AND status='active'`.execute(trx);
        if (!field.rows[0]) return { error: 'field_not_found' as const };
      }
      if (input.campaign_id) {
        const campaign = await sql<{ id: string }>`SELECT id FROM campaigns WHERE id=${input.campaign_id}::uuid AND workspace_id=${context.workspaceId}::uuid`.execute(trx);
        if (!campaign.rows[0]) return { error: 'campaign_not_found' as const };
      }

      const workId = input.entity_id ?? randomUUID();
      const participantCost = input.participants.reduce((sum, item) => sum + (item.cost_eur ?? ((item.quantity ?? 0) * (item.rate_eur ?? 0))), 0);
      const resourceCost = input.resources.reduce((sum, item) => sum + (item.cost_eur ?? ((item.quantity ?? 0) * (item.unit_cost_eur ?? 0))), 0);
      const totalCost = participantCost + resourceCost;
      const work = await sql`
        INSERT INTO work_records (
          id, workspace_id, field_id, customer_site_id, campaign_id, client_operation_id, professional_quote_id,
          type, occurred_on, title, notes, performed_for, customer_party_id,
          quoted_amount_eur, charge_eur, collected_eur, payment_status, created_by
        ) VALUES (
          ${workId}::uuid, ${context.workspaceId}::uuid, ${input.field_id ?? null}::uuid,
          ${input.customer_site_id ?? quote.customer_site_id}::uuid, ${input.campaign_id ?? null}::uuid, ${input.client_operation_id}::uuid,
          ${quote.id}::uuid, ${input.type}, ${input.occurred_on}::date, ${input.title ?? quote.title}, ${input.notes ?? null},
          'third-party', ${quote.customer_party_id}::uuid, ${Number(quote.total_eur)}, ${Number(quote.total_eur)}, 0, 'pending', ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);

      for (const participant of input.participants) {
        const cost = participant.cost_eur ?? ((participant.quantity ?? 0) * (participant.rate_eur ?? 0));
        await sql`INSERT INTO work_participants (work_id, party_id, crew_id, display_name, role, quantity, unit, rate_eur, cost_eur) VALUES (${workId}::uuid, ${participant.party_id ?? null}::uuid, ${participant.crew_id ?? null}::uuid, ${participant.display_name}, ${participant.role ?? null}, ${participant.quantity ?? null}, ${participant.unit ?? null}, ${participant.rate_eur ?? null}, ${cost})`.execute(trx);
      }
      for (const resource of input.resources) {
        const cost = resource.cost_eur ?? ((resource.quantity ?? 0) * (resource.unit_cost_eur ?? 0));
        await sql`INSERT INTO work_resources (work_id, kind, machinery_id, material_id, supplier_party_id, name, quantity, unit, unit_cost_eur, cost_eur) VALUES (${workId}::uuid, ${resource.kind}, ${resource.machinery_id ?? null}::uuid, ${resource.material_id ?? null}::uuid, ${resource.supplier_party_id ?? null}::uuid, ${resource.name}, ${resource.quantity ?? null}, ${resource.unit ?? null}, ${resource.unit_cost_eur ?? null}, ${cost})`.execute(trx);
      }

      if (input.field_id) {
        await writeDomainEffects(trx, {
          workspaceId: context.workspaceId,
          fieldId: input.field_id,
          campaignId: input.campaign_id ?? null,
          domainType: 'work',
          domainRecordId: workId,
          occurredAt: `${input.occurred_on}T12:00:00.000Z`,
          title: input.title ?? quote.title,
          summary: `Trabajo desde presupuesto · ${Number(quote.total_eur).toFixed(2)} € facturable · ${totalCost.toFixed(2)} € coste`,
          iconKey: 'work',
          cost: totalCost > 0 ? { amountEur: totalCost, category: 'work' } : undefined,
        });
      }

      await sql`UPDATE professional_quotes SET status='converted', updated_at=now() WHERE id=${quote.id}::uuid`.execute(trx);
      return { work: work.rows[0], total_cost_eur: totalCost };
    });

    if ('error' in saved && typeof saved.error === 'string') {
      const code = saved.error === 'quote_not_found' || saved.error.endsWith('_not_found') ? 404 : 409;
      return reply.code(code).send(saved);
    }
    return reply.code(201).send({ replayed: false, ...saved });
  });
}
