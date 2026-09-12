import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function registerFinancialAttentionRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/financial-attention', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const query = request.query as { fieldId?: string; limit?: string };
    const fieldId = query.fieldId?.trim() || null;
    if (fieldId && !uuidPattern.test(fieldId)) return reply.code(400).send({ error: 'invalid_field_id' });
    const parsedLimit = Number(query.limit ?? 6);
    const limit = Number.isFinite(parsedLimit) ? Math.max(1, Math.min(12, Math.trunc(parsedLimit))) : 6;

    const settlements = await sql<{
      id: string;
      settlement_number: string | null;
      counterparty_name: string | null;
      settled_on: string;
      net_eur: number | string;
      collected_eur: number | string;
      pending_eur: number | string;
      field_names: string | null;
    }>`
      SELECT
        hs.id,
        hs.settlement_number,
        hs.counterparty_name,
        hs.settled_on,
        hs.net_eur,
        COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0) AS collected_eur,
        GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) AS pending_eur,
        STRING_AGG(DISTINCT f.name, ', ' ORDER BY f.name) AS field_names
      FROM harvest_settlements hs
      JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
      JOIN harvest_delivery_fields hdf ON hdf.delivery_id = hsd.delivery_id
      JOIN fields f ON f.id = hdf.field_id AND f.workspace_id = hs.workspace_id
      WHERE hs.workspace_id = ${context.workspaceId}::uuid
        AND hs.status = 'confirmed'
        AND (${fieldId}::text IS NULL OR f.id = ${fieldId}::uuid)
      GROUP BY hs.id
      HAVING GREATEST(hs.net_eur - COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0), 0) > 0.009
      ORDER BY hs.settled_on DESC, hs.created_at DESC
      LIMIT ${limit}
    `.execute(database);

    const documents = await sql<{
      id: string;
      title: string;
      kind: string;
      created_at: string;
      field_id: string;
      field_name: string;
      ocr_status: string | null;
      extraction_status: string | null;
      review_id: string | null;
    }>`
      SELECT DISTINCT ON (d.id)
        d.id,
        d.title,
        d.kind,
        d.created_at,
        al.field_id,
        f.name AS field_name,
        ocr.status AS ocr_status,
        er.status AS extraction_status,
        rev.id AS review_id
      FROM documents d
      JOIN attachment_links al ON al.document_id = d.id AND al.workspace_id = d.workspace_id
      JOIN fields f ON f.id = al.field_id AND f.workspace_id = d.workspace_id
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
        AND (${fieldId}::text IS NULL OR al.field_id = ${fieldId}::uuid)
        AND d.kind IN ('invoice','purchase_receipt','delivery_ticket','yield_result','settlement_statement','collection_receipt')
        AND (
          ocr.id IS NULL
          OR ocr.status IN ('queued','processing','failed')
          OR (ocr.status = 'succeeded' AND er.id IS NULL)
          OR (er.status = 'needs_review' AND rev.id IS NULL)
        )
      ORDER BY d.id, d.created_at DESC
      LIMIT ${limit}
    `.execute(database);

    const settlementItems = settlements.rows.map((row) => ({
      type: 'collection_pending' as const,
      id: row.id,
      title: row.settlement_number ? `Liquidación ${row.settlement_number}` : 'Liquidación pendiente de cobro',
      subtitle: [row.counterparty_name, row.field_names].filter(Boolean).join(' · '),
      pending_eur: Number(row.pending_eur),
      net_eur: Number(row.net_eur),
      collected_eur: Number(row.collected_eur),
      date: row.settled_on,
      action: 'review_collection',
    }));

    const documentItems = documents.rows.map((row) => {
      let status: 'needs_ocr' | 'processing' | 'ocr_failed' | 'needs_review' | 'unstructured' = 'needs_ocr';
      if (row.ocr_status === 'queued' || row.ocr_status === 'processing') status = 'processing';
      else if (row.ocr_status === 'failed') status = 'ocr_failed';
      else if (row.ocr_status === 'succeeded' && !row.extraction_status) status = 'unstructured';
      else if (row.extraction_status === 'needs_review' && !row.review_id) status = 'needs_review';
      return {
        type: 'document_attention' as const,
        id: row.id,
        title: row.title,
        subtitle: row.field_name,
        field_id: row.field_id,
        kind: row.kind,
        status,
        date: row.created_at,
        action: 'review_document',
      };
    });

    return {
      generated_at: new Date().toISOString(),
      field_id: fieldId,
      counts: {
        pending_settlements: settlementItems.length,
        pending_documents: documentItems.length,
      },
      pending_collection_eur: settlementItems.reduce((sum, item) => sum + item.pending_eur, 0),
      settlements: settlementItems,
      documents: documentItems,
      semantics: 'financial_and_document_attention_without_automatic_mutation',
    };
  });
}
