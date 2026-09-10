import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, requireContext, requireDatabase } from '../http/helpers.js';

export function registerHarvestFieldCommercialRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/fields/:fieldId/harvest-commercial-summary', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsedFieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const fieldId = parsedFieldId.data;
    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const rows = await sql<{
      settlement_id: string;
      settled_on: string;
      counterparty_name: string | null;
      settlement_number: string | null;
      basis: 'olive_kg' | 'oil_kg' | 'fixed';
      settlement_net_eur: number;
      settlement_gross_eur: number;
      settlement_deductions_eur: number;
      settlement_collected_eur: number;
      total_linked_kg: number;
      field_linked_kg: number;
    }>`
      SELECT
        hs.id AS settlement_id,
        hs.settled_on,
        hs.counterparty_name,
        hs.settlement_number,
        hs.basis,
        hs.net_eur::double precision AS settlement_net_eur,
        hs.gross_eur::double precision AS settlement_gross_eur,
        hs.deductions_eur::double precision AS settlement_deductions_eur,
        COALESCE((SELECT SUM(hc.amount_eur) FROM harvest_collections hc WHERE hc.settlement_id = hs.id), 0)::double precision AS settlement_collected_eur,
        COALESCE(SUM(hdf_all.kg), 0)::double precision AS total_linked_kg,
        COALESCE(SUM(CASE WHEN hdf_all.field_id = ${fieldId}::uuid THEN hdf_all.kg ELSE 0 END), 0)::double precision AS field_linked_kg
      FROM harvest_settlements hs
      JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
      JOIN harvest_delivery_fields hdf_all ON hdf_all.delivery_id = hsd.delivery_id
      WHERE hs.workspace_id = ${context.workspaceId}::uuid
        AND hs.status = 'confirmed'
      GROUP BY hs.id
      HAVING SUM(CASE WHEN hdf_all.field_id = ${fieldId}::uuid THEN hdf_all.kg ELSE 0 END) > 0
      ORDER BY hs.settled_on DESC, hs.created_at DESC
    `.execute(database);

    const settlements = rows.rows.map((row) => {
      const ratio = row.total_linked_kg > 0 ? row.field_linked_kg / row.total_linked_kg : 0;
      const attributableNet = row.settlement_net_eur * ratio;
      const attributableGross = row.settlement_gross_eur * ratio;
      const attributableDeductions = row.settlement_deductions_eur * ratio;
      const attributableCollected = row.settlement_collected_eur * ratio;
      return {
        id: row.settlement_id,
        settled_on: row.settled_on,
        counterparty_name: row.counterparty_name,
        settlement_number: row.settlement_number,
        settlement_basis: row.basis,
        allocation_basis: 'linked_delivery_olive_kg_share' as const,
        allocation_status: 'derived_estimate' as const,
        field_kg: row.field_linked_kg,
        settlement_total_linked_kg: row.total_linked_kg,
        share_percent: ratio * 100,
        gross_eur: attributableGross,
        deductions_eur: attributableDeductions,
        net_eur: attributableNet,
        collected_eur: attributableCollected,
        pending_eur: Math.max(attributableNet - attributableCollected, 0),
      };
    });

    const totals = settlements.reduce((acc, item) => ({
      accrued_eur: acc.accrued_eur + item.net_eur,
      collected_eur: acc.collected_eur + item.collected_eur,
      pending_eur: acc.pending_eur + item.pending_eur,
    }), { accrued_eur: 0, collected_eur: 0, pending_eur: 0 });

    return {
      field: { id: field.id, name: field.name },
      allocation_notice: 'Importes atribuidos proporcionalmente por kg de aceituna de la finca dentro de las entregas incluidas. Son una estimación derivada hasta disponer de líneas de liquidación explícitas por finca.',
      ...totals,
      settlements,
    };
  });
}
