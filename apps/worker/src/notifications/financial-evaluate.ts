import { randomUUID } from 'node:crypto';
import type { Pool } from 'pg';
import type { FinancialAlertEvaluateJobPayload } from '@magina/contracts';

const ruleVersion = 'financial-attention-notify-v1';

type PreferenceRow = {
  user_id: string;
  workspace_id: string;
  notify_settlements: boolean;
  settlement_min_eur: string | number;
  notify_document_review: boolean;
  notify_ocr_failure: boolean;
};

export async function runFinancialAlertEvaluationJob(pool: Pool, payload: FinancialAlertEvaluateJobPayload) {
  const preferences = await pool.query<PreferenceRow>(`
    SELECT user_id, workspace_id, notify_settlements, settlement_min_eur,
           notify_document_review, notify_ocr_failure
    FROM financial_notification_preferences
    WHERE enabled = TRUE
    ORDER BY updated_at ASC
    LIMIT $1
  `, [payload.limit_users]);

  let created = 0;
  let skipped = 0;

  for (const preference of preferences.rows) {
    if (preference.notify_settlements) {
      const settlements = await pool.query<{
        id: string;
        settlement_number: string | null;
        counterparty_name: string | null;
        pending_eur: string | number;
      }>(`
        SELECT hs.id, hs.settlement_number, hs.counterparty_name,
               GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) AS pending_eur
        FROM harvest_settlements hs
        WHERE hs.workspace_id = $1::uuid
          AND hs.status = 'confirmed'
          AND GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) >= $2::numeric
        ORDER BY hs.settled_on DESC
        LIMIT 100
      `, [preference.workspace_id, Number(preference.settlement_min_eur)]);

      for (const settlement of settlements.rows) {
        const pending = Number(settlement.pending_eur);
        const result = await pool.query<{ id: string }>(`
          INSERT INTO notification_intents (
            id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
            title, body, payload_json, dedupe_key, status
          ) VALUES (
            $1::uuid, $2::uuid, $3::uuid, NULL,
            'financial_collection_pending', 'push', 'harvest_settlement', $4::uuid,
            'Mágina · cobro pendiente', $5,
            $6::jsonb, $7, 'pending'
          )
          ON CONFLICT (dedupe_key) DO NOTHING
          RETURNING id
        `, [
          randomUUID(), preference.user_id, preference.workspace_id, settlement.id,
          `${settlement.settlement_number ? `Liquidación ${settlement.settlement_number}` : 'Liquidación'}${settlement.counterparty_name ? ` · ${settlement.counterparty_name}` : ''}: ${pending.toLocaleString('es-ES', { maximumFractionDigits: 2 })} € pendientes.`,
          JSON.stringify({ path: 'mi-campo/campana/', pending_eur: pending, rule_version: ruleVersion }),
          `financial:${preference.user_id}:settlement:${settlement.id}:${ruleVersion}`,
        ]);
        if (result.rows[0]) created += 1;
        else skipped += 1;
      }
    }

    if (preference.notify_document_review || preference.notify_ocr_failure) {
      const documents = await pool.query<{
        id: string;
        title: string;
        field_id: string | null;
        ocr_status: string | null;
        extraction_status: string | null;
        review_id: string | null;
      }>(`
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
        WHERE d.workspace_id = $1::uuid
          AND d.status = 'active'
          AND d.kind IN ('invoice','purchase_receipt','delivery_ticket','yield_result','settlement_statement','collection_receipt')
          AND (ocr.status = 'failed' OR (er.status = 'needs_review' AND rev.id IS NULL))
        ORDER BY d.id, d.created_at DESC
        LIMIT 100
      `, [preference.workspace_id]);

      for (const document of documents.rows) {
        const status = document.ocr_status === 'failed' ? 'ocr_failed' : 'needs_review';
        const shouldNotify = status === 'ocr_failed' ? preference.notify_ocr_failure : preference.notify_document_review;
        if (!shouldNotify) { skipped += 1; continue; }
        const result = await pool.query<{ id: string }>(`
          INSERT INTO notification_intents (
            id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
            title, body, payload_json, dedupe_key, status
          ) VALUES (
            $1::uuid, $2::uuid, $3::uuid, $4::uuid,
            $5, 'push', 'document', $6::uuid,
            $7, $8, $9::jsonb, $10, 'pending'
          )
          ON CONFLICT (dedupe_key) DO NOTHING
          RETURNING id
        `, [
          randomUUID(), preference.user_id, preference.workspace_id, document.field_id,
          status === 'ocr_failed' ? 'document_ocr_failed' : 'document_review_pending', document.id,
          status === 'ocr_failed' ? 'Mágina · documento sin leer' : 'Mágina · revisión pendiente',
          status === 'ocr_failed' ? `${document.title}: el OCR ha fallado y requiere revisión.` : `${document.title}: hay datos extraídos pendientes de confirmar.`,
          JSON.stringify({ path: 'mi-campo/', document_id: document.id, status, rule_version: ruleVersion }),
          `financial:${preference.user_id}:document:${document.id}:${status}:${ruleVersion}`,
        ]);
        if (result.rows[0]) created += 1;
        else skipped += 1;
      }
    }
  }

  return { users: preferences.rowCount ?? preferences.rows.length, created, skipped, ruleVersion };
}
