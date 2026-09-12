import { randomUUID } from 'node:crypto';
import type { Pool } from 'pg';
import type { CommercialAlertEvaluateJobPayload } from '@magina/contracts';

const ruleVersion = 'commercial-attention-notify-v1';

type PreferenceRow = {
  user_id: string;
  workspace_id: string;
  notify_overdue_invoices: boolean;
  overdue_invoice_days: number;
  notify_expired_quotes: boolean;
  notify_quote_followup: boolean;
  quote_followup_days: number;
  notify_unbilled_work: boolean;
  unbilled_work_days: number;
};

async function insertIntent(pool: Pool, input: {
  userId: string;
  workspaceId: string;
  kind: string;
  sourceType: string;
  sourceRecordId: string;
  title: string;
  body: string;
  payload: Record<string, unknown>;
  dedupeKey: string;
}) {
  return pool.query<{ id: string }>(`
    INSERT INTO notification_intents (
      id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
      title, body, payload_json, dedupe_key, status
    ) VALUES (
      $1::uuid, $2::uuid, $3::uuid, NULL, $4, 'push', $5, $6::uuid,
      $7, $8, $9::jsonb, $10, 'pending'
    )
    ON CONFLICT (dedupe_key) DO NOTHING
    RETURNING id
  `, [randomUUID(), input.userId, input.workspaceId, input.kind, input.sourceType, input.sourceRecordId, input.title, input.body, JSON.stringify(input.payload), input.dedupeKey]);
}

export async function runCommercialAlertEvaluationJob(pool: Pool, payload: CommercialAlertEvaluateJobPayload) {
  const preferences = await pool.query<PreferenceRow>(`
    SELECT user_id, workspace_id,
           notify_overdue_invoices, overdue_invoice_days,
           notify_expired_quotes, notify_quote_followup, quote_followup_days,
           notify_unbilled_work, unbilled_work_days
    FROM commercial_notification_preferences
    WHERE enabled = TRUE
    ORDER BY updated_at ASC
    LIMIT $1
  `, [payload.limit_users]);

  let created = 0;
  let skipped = 0;

  for (const pref of preferences.rows) {
    if (pref.notify_overdue_invoices) {
      const rows = await pool.query<{ id: string; invoice_number: string | null; customer_name: string; pending_eur: string | number; overdue_days: number }>(`
        SELECT pi.id, pi.invoice_number, p.display_name AS customer_name,
               GREATEST(pi.total_eur - COALESCE((
                 SELECT SUM(wc.amount_eur)
                 FROM professional_invoice_works piw
                 JOIN work_collections wc ON wc.work_id = piw.work_id
                 WHERE piw.invoice_id = pi.id
               ), 0), 0)::double precision AS pending_eur,
               (CURRENT_DATE - pi.due_on)::int AS overdue_days
        FROM professional_invoices pi
        JOIN parties p ON p.id = pi.customer_party_id
        WHERE pi.workspace_id = $1::uuid
          AND pi.status = 'issued'
          AND pi.due_on IS NOT NULL
          AND CURRENT_DATE - pi.due_on >= $2::int
          AND pi.total_eur > COALESCE((
            SELECT SUM(wc.amount_eur)
            FROM professional_invoice_works piw
            JOIN work_collections wc ON wc.work_id = piw.work_id
            WHERE piw.invoice_id = pi.id
          ), 0)
        ORDER BY overdue_days DESC
        LIMIT 100
      `, [pref.workspace_id, pref.overdue_invoice_days]);

      for (const row of rows.rows) {
        const pending = Number(row.pending_eur);
        const result = await insertIntent(pool, {
          userId: pref.user_id, workspaceId: pref.workspace_id,
          kind: 'professional_invoice_overdue', sourceType: 'professional_invoice', sourceRecordId: row.id,
          title: 'Mágina · factura vencida',
          body: `${row.invoice_number ? `Factura ${row.invoice_number}` : 'Factura'} · ${row.customer_name}: ${pending.toLocaleString('es-ES', { maximumFractionDigits: 2 })} € pendientes desde hace ${row.overdue_days} días.`,
          payload: { path: `mi-campo/profesional/`, overdue_days: row.overdue_days, pending_eur: pending, rule_version: ruleVersion },
          dedupeKey: `commercial:${pref.user_id}:invoice:${row.id}:${row.overdue_days >= 30 ? '30plus' : 'overdue'}:${ruleVersion}`,
        });
        if (result.rows[0]) created += 1; else skipped += 1;
      }
    }

    if (pref.notify_expired_quotes || pref.notify_quote_followup) {
      const rows = await pool.query<{ id: string; quote_number: string | null; customer_name: string; total_eur: string | number; sent_days: number; expired_days: number }>(`
        SELECT pq.id, pq.quote_number, p.display_name AS customer_name, pq.total_eur::double precision,
               GREATEST(CURRENT_DATE - COALESCE(pq.issued_on, pq.created_at::date), 0)::int AS sent_days,
               CASE WHEN pq.valid_until IS NULL THEN 0 ELSE GREATEST(CURRENT_DATE - pq.valid_until, 0)::int END AS expired_days
        FROM professional_quotes pq
        JOIN parties p ON p.id = pq.customer_party_id
        WHERE pq.workspace_id = $1::uuid
          AND pq.status = 'sent'
        ORDER BY COALESCE(pq.valid_until, pq.issued_on, pq.created_at::date) ASC
        LIMIT 100
      `, [pref.workspace_id]);

      for (const row of rows.rows) {
        const expired = row.expired_days > 0;
        const followup = !expired && row.sent_days >= pref.quote_followup_days;
        if ((expired && !pref.notify_expired_quotes) || (followup && !pref.notify_quote_followup) || (!expired && !followup)) { skipped += 1; continue; }
        const statusKey = expired ? `expired-${Math.min(Math.floor(row.expired_days / 7), 8)}` : `followup-${Math.floor(row.sent_days / pref.quote_followup_days)}`;
        const result = await insertIntent(pool, {
          userId: pref.user_id, workspaceId: pref.workspace_id,
          kind: expired ? 'professional_quote_expired' : 'professional_quote_followup',
          sourceType: 'professional_quote', sourceRecordId: row.id,
          title: expired ? 'Mágina · presupuesto fuera de plazo' : 'Mágina · presupuesto sin respuesta',
          body: `${row.quote_number ? `Presupuesto ${row.quote_number}` : 'Presupuesto'} · ${row.customer_name}: ${Number(row.total_eur).toLocaleString('es-ES', { maximumFractionDigits: 2 })} €${expired ? ` · ${row.expired_days} días fuera de plazo.` : ` · enviado hace ${row.sent_days} días.`}`,
          payload: { path: `mi-campo/profesional/presupuestos/?quoteId=${encodeURIComponent(row.id)}`, expired_days: row.expired_days, sent_days: row.sent_days, rule_version: ruleVersion },
          dedupeKey: `commercial:${pref.user_id}:quote:${row.id}:${statusKey}:${ruleVersion}`,
        });
        if (result.rows[0]) created += 1; else skipped += 1;
      }
    }

    if (pref.notify_unbilled_work) {
      const rows = await pool.query<{ id: string; title: string; customer_name: string; charge_eur: string | number; age_days: number }>(`
        SELECT wr.id, wr.title, p.display_name AS customer_name, COALESCE(wr.charge_eur, 0)::double precision AS charge_eur,
               (CURRENT_DATE - wr.occurred_on)::int AS age_days
        FROM work_records wr
        JOIN parties p ON p.id = wr.customer_party_id
        WHERE wr.workspace_id = $1::uuid
          AND wr.performed_for = 'third-party'
          AND COALESCE(wr.charge_eur, 0) > 0
          AND CURRENT_DATE - wr.occurred_on >= $2::int
          AND NOT EXISTS (
            SELECT 1 FROM professional_invoice_works piw
            JOIN professional_invoices pi ON pi.id = piw.invoice_id
            WHERE piw.work_id = wr.id AND pi.status <> 'void'
          )
        ORDER BY age_days DESC
        LIMIT 100
      `, [pref.workspace_id, pref.unbilled_work_days]);

      for (const row of rows.rows) {
        const result = await insertIntent(pool, {
          userId: pref.user_id, workspaceId: pref.workspace_id,
          kind: 'professional_work_unbilled', sourceType: 'work', sourceRecordId: row.id,
          title: 'Mágina · trabajo sin facturar',
          body: `${row.title} · ${row.customer_name}: ${Number(row.charge_eur).toLocaleString('es-ES', { maximumFractionDigits: 2 })} € · ${row.age_days} días sin factura.`,
          payload: { path: 'mi-campo/profesional/facturas/nueva/', work_id: row.id, age_days: row.age_days, rule_version: ruleVersion },
          dedupeKey: `commercial:${pref.user_id}:work:${row.id}:${Math.floor(row.age_days / pref.unbilled_work_days)}:${ruleVersion}`,
        });
        if (result.rows[0]) created += 1; else skipped += 1;
      }
    }
  }

  return { users: preferences.rowCount ?? preferences.rows.length, created, skipped, ruleVersion };
}
