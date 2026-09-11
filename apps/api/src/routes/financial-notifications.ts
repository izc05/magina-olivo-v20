import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import type { NotificationDispatchQueuePort } from '../notifications/port.js';

const preferenceSchema = z.object({
  enabled: z.boolean(),
  notify_settlements: z.boolean().default(false),
  settlement_min_eur: z.number().finite().nonnegative().max(1_000_000).default(1000),
  notify_document_review: z.boolean().default(false),
  notify_ocr_failure: z.boolean().default(false),
});

const ruleVersion = 'financial-attention-notify-v1';

export function registerFinancialNotificationRoutes(app: FastifyInstance, db: DatabaseClient | null, queue: NotificationDispatchQueuePort) {
  app.get('/api/v1/financial-notifications/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql<{
      enabled: boolean;
      notify_settlements: boolean;
      settlement_min_eur: number | string;
      notify_document_review: boolean;
      notify_ocr_failure: boolean;
    }>`
      SELECT enabled, notify_settlements, settlement_min_eur, notify_document_review, notify_ocr_failure
      FROM financial_notification_preferences
      WHERE user_id = ${context.userId}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const row = result.rows[0];
    return row ? { ...row, settlement_min_eur: Number(row.settlement_min_eur) } : {
      enabled: false,
      notify_settlements: false,
      settlement_min_eur: 1000,
      notify_document_review: false,
      notify_ocr_failure: false,
    };
  });

  app.put('/api/v1/financial-notifications/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = preferenceSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_preferences', issues: parsed.error.issues });
    const input = parsed.data;
    const result = await sql<{
      enabled: boolean;
      notify_settlements: boolean;
      settlement_min_eur: number | string;
      notify_document_review: boolean;
      notify_ocr_failure: boolean;
    }>`
      INSERT INTO financial_notification_preferences (
        user_id, workspace_id, enabled, notify_settlements, settlement_min_eur,
        notify_document_review, notify_ocr_failure, updated_at
      ) VALUES (
        ${context.userId}::uuid, ${context.workspaceId}::uuid, ${input.enabled}, ${input.notify_settlements},
        ${input.settlement_min_eur}, ${input.notify_document_review}, ${input.notify_ocr_failure}, now()
      )
      ON CONFLICT (user_id, workspace_id)
      DO UPDATE SET
        enabled = EXCLUDED.enabled,
        notify_settlements = EXCLUDED.notify_settlements,
        settlement_min_eur = EXCLUDED.settlement_min_eur,
        notify_document_review = EXCLUDED.notify_document_review,
        notify_ocr_failure = EXCLUDED.notify_ocr_failure,
        updated_at = now()
      RETURNING enabled, notify_settlements, settlement_min_eur, notify_document_review, notify_ocr_failure
    `.execute(database);
    const row = result.rows[0];
    return { ...row, settlement_min_eur: Number(row.settlement_min_eur) };
  });

  app.post('/api/v1/financial-notifications/evaluate', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const preferencesResult = await sql<{
      enabled: boolean;
      notify_settlements: boolean;
      settlement_min_eur: number | string;
      notify_document_review: boolean;
      notify_ocr_failure: boolean;
    }>`
      SELECT enabled, notify_settlements, settlement_min_eur, notify_document_review, notify_ocr_failure
      FROM financial_notification_preferences
      WHERE user_id = ${context.userId}::uuid AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);
    const preferences = preferencesResult.rows[0];
    if (!preferences?.enabled) return { evaluated: 0, created: 0, skipped: 0, reason: 'alerts_not_enabled', rule_version: ruleVersion };

    let created = 0;
    let skipped = 0;
    const intentIds: string[] = [];

    if (preferences.notify_settlements) {
      const settlements = await sql<{
        id: string;
        settlement_number: string | null;
        counterparty_name: string | null;
        pending_eur: number | string;
      }>`
        SELECT hs.id, hs.settlement_number, hs.counterparty_name,
               GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) AS pending_eur
        FROM harvest_settlements hs
        WHERE hs.workspace_id = ${context.workspaceId}::uuid
          AND hs.status = 'confirmed'
          AND GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) >= ${Number(preferences.settlement_min_eur)}
        ORDER BY hs.settled_on DESC
        LIMIT 100
      `.execute(database);

      for (const settlement of settlements.rows) {
        const pending = Number(settlement.pending_eur);
        const intentId = randomUUID();
        const dedupeKey = `financial:${context.userId}:settlement:${settlement.id}:${ruleVersion}`;
        const inserted = await sql<{ id: string }>`
          INSERT INTO notification_intents (
            id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
            title, body, payload_json, dedupe_key, status
          ) VALUES (
            ${intentId}::uuid, ${context.userId}::uuid, ${context.workspaceId}::uuid, NULL,
            'financial_collection_pending', 'push', 'harvest_settlement', ${settlement.id}::uuid,
            'Mágina · cobro pendiente',
            ${`${settlement.settlement_number ? `Liquidación ${settlement.settlement_number}` : 'Liquidación'}${settlement.counterparty_name ? ` · ${settlement.counterparty_name}` : ''}: ${pending.toLocaleString('es-ES', { maximumFractionDigits: 2 })} € pendientes.`},
            ${JSON.stringify({ path: 'mi-campo/campana/', pending_eur: pending, rule_version: ruleVersion })}::jsonb,
            ${dedupeKey}, 'pending'
          )
          ON CONFLICT (dedupe_key) DO NOTHING
          RETURNING id
        `.execute(database);
        const id = inserted.rows[0]?.id;
        if (!id) { skipped += 1; continue; }
        created += 1;
        intentIds.push(id);
      }
    }

    if (preferences.notify_document_review || preferences.notify_ocr_failure) {
      const documents = await sql<{
        id: string;
        title: string;
        field_id: string | null;
        ocr_status: string | null;
        extraction_status: string | null;
        review_id: string | null;
      }>`
        SELECT DISTINCT ON (d.id)
          d.id, d.title, al.field_id,
          ocr.status AS ocr_status,
          er.status AS extraction_status,
          rev.id AS review_id
        FROM documents d
        JOIN attachment_links al ON al.document_id = d.id AND al.workspace_id = d.workspace_id
        LEFT JOIN LATERAL (
          SELECT dv.* FROM document_versions dv WHERE dv.document_id = d.id ORDER BY dv.version_no DESC LIMIT 1
        ) dv ON true
        LEFT JOIN LATERAL (
          SELECT o.* FROM ocr_runs o WHERE o.document_version_id = dv.id ORDER BY o.created_at DESC LIMIT 1
        ) ocr ON true
        LEFT JOIN LATERAL (
          SELECT e.* FROM extraction_runs e WHERE e.ocr_run_id = ocr.id ORDER BY e.created_at DESC LIMIT 1
        ) er ON true
        LEFT JOIN LATERAL (
          SELECT r.* FROM extraction_reviews r WHERE r.extraction_run_id = er.id AND r.workspace_id = d.workspace_id ORDER BY r.created_at DESC LIMIT 1
        ) rev ON true
        WHERE d.workspace_id = ${context.workspaceId}::uuid
          AND d.status = 'active'
          AND d.kind IN ('invoice','purchase_receipt','delivery_ticket','yield_result','settlement_statement','collection_receipt')
          AND (ocr.status = 'failed' OR (er.status = 'needs_review' AND rev.id IS NULL))
        ORDER BY d.id, d.created_at DESC
        LIMIT 100
      `.execute(database);

      for (const document of documents.rows) {
        const status = document.ocr_status === 'failed' ? 'ocr_failed' : 'needs_review';
        const shouldNotify = status === 'ocr_failed' ? preferences.notify_ocr_failure : preferences.notify_document_review;
        if (!shouldNotify) { skipped += 1; continue; }
        const intentId = randomUUID();
        const dedupeKey = `financial:${context.userId}:document:${document.id}:${status}:${ruleVersion}`;
        const inserted = await sql<{ id: string }>`
          INSERT INTO notification_intents (
            id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
            title, body, payload_json, dedupe_key, status
          ) VALUES (
            ${intentId}::uuid, ${context.userId}::uuid, ${context.workspaceId}::uuid, ${document.field_id ?? null}::uuid,
            ${status === 'ocr_failed' ? 'document_ocr_failed' : 'document_review_pending'}, 'push', 'document', ${document.id}::uuid,
            ${status === 'ocr_failed' ? 'Mágina · documento sin leer' : 'Mágina · revisión pendiente'},
            ${status === 'ocr_failed' ? `${document.title}: el OCR ha fallado y requiere revisión.` : `${document.title}: hay datos extraídos pendientes de confirmar.`},
            ${JSON.stringify({ path: 'mi-campo/', document_id: document.id, status, rule_version: ruleVersion })}::jsonb,
            ${dedupeKey}, 'pending'
          )
          ON CONFLICT (dedupe_key) DO NOTHING
          RETURNING id
        `.execute(database);
        const id = inserted.rows[0]?.id;
        if (!id) { skipped += 1; continue; }
        created += 1;
        intentIds.push(id);
      }
    }

    for (const id of intentIds) {
      try {
        await queue.enqueue({ version: 1, intent_id: id, limit: 8 });
      } catch (error) {
        request.log.warn({ err: error, intentId: id }, 'Financial notification queue unavailable; pending intent remains stored');
      }
    }

    return { evaluated: created + skipped, created, skipped, intent_ids: intentIds, rule_version: ruleVersion };
  });
}
