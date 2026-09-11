import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const preferenceSchema = z.object({
  enabled: z.boolean(),
  notify_overdue_invoices: z.boolean().default(false),
  overdue_invoice_days: z.number().int().min(1).max(365).default(7),
  notify_expired_quotes: z.boolean().default(false),
  notify_quote_followup: z.boolean().default(false),
  quote_followup_days: z.number().int().min(1).max(90).default(7),
  notify_unbilled_work: z.boolean().default(false),
  unbilled_work_days: z.number().int().min(1).max(365).default(14),
});

const defaults = {
  enabled: false,
  notify_overdue_invoices: false,
  overdue_invoice_days: 7,
  notify_expired_quotes: false,
  notify_quote_followup: false,
  quote_followup_days: 7,
  notify_unbilled_work: false,
  unbilled_work_days: 14,
};

export function registerCommercialNotificationRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/commercial-notifications/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql`
      SELECT enabled, notify_overdue_invoices, overdue_invoice_days,
             notify_expired_quotes, notify_quote_followup, quote_followup_days,
             notify_unbilled_work, unbilled_work_days
      FROM commercial_notification_preferences
      WHERE user_id = ${context.userId}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    return result.rows[0] ?? defaults;
  });

  app.put('/api/v1/commercial-notifications/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = preferenceSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_preferences', issues: parsed.error.issues });
    const input = parsed.data;
    const result = await sql`
      INSERT INTO commercial_notification_preferences (
        user_id, workspace_id, enabled,
        notify_overdue_invoices, overdue_invoice_days,
        notify_expired_quotes, notify_quote_followup, quote_followup_days,
        notify_unbilled_work, unbilled_work_days, updated_at
      ) VALUES (
        ${context.userId}::uuid, ${context.workspaceId}::uuid, ${input.enabled},
        ${input.notify_overdue_invoices}, ${input.overdue_invoice_days},
        ${input.notify_expired_quotes}, ${input.notify_quote_followup}, ${input.quote_followup_days},
        ${input.notify_unbilled_work}, ${input.unbilled_work_days}, now()
      )
      ON CONFLICT (user_id, workspace_id)
      DO UPDATE SET
        enabled = EXCLUDED.enabled,
        notify_overdue_invoices = EXCLUDED.notify_overdue_invoices,
        overdue_invoice_days = EXCLUDED.overdue_invoice_days,
        notify_expired_quotes = EXCLUDED.notify_expired_quotes,
        notify_quote_followup = EXCLUDED.notify_quote_followup,
        quote_followup_days = EXCLUDED.quote_followup_days,
        notify_unbilled_work = EXCLUDED.notify_unbilled_work,
        unbilled_work_days = EXCLUDED.unbilled_work_days,
        updated_at = now()
      RETURNING enabled, notify_overdue_invoices, overdue_invoice_days,
                notify_expired_quotes, notify_quote_followup, quote_followup_days,
                notify_unbilled_work, unbilled_work_days
    `.execute(database);

    await sql`
      UPDATE notification_intents
      SET status = 'suppressed'
      WHERE user_id = ${context.userId}::uuid
        AND workspace_id = ${context.workspaceId}::uuid
        AND status = 'pending'
        AND kind IN ('professional_invoice_overdue','professional_quote_expired','professional_quote_followup','professional_work_unbilled')
        AND (
          ${input.enabled} = false
          OR (kind = 'professional_invoice_overdue' AND ${input.notify_overdue_invoices} = false)
          OR (kind = 'professional_quote_expired' AND ${input.notify_expired_quotes} = false)
          OR (kind = 'professional_quote_followup' AND ${input.notify_quote_followup} = false)
          OR (kind = 'professional_work_unbilled' AND ${input.notify_unbilled_work} = false)
        )
    `.execute(database);

    return result.rows[0];
  });
}
