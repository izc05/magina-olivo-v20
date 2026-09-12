import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerHarvestSettlementCandidateRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/harvest-settlement-candidates', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const query = request.query as { campaignId?: string };
    let campaignId: string | null = null;
    if (query.campaignId) {
      const parsed = uuidSchema.safeParse(query.campaignId);
      if (!parsed.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
      campaignId = parsed.data;
    } else {
      const active = await database.selectFrom('campaigns').select('id')
        .where('workspace_id', '=', context.workspaceId)
        .where('status', '=', 'active')
        .orderBy('start_date', 'desc')
        .executeTakeFirst();
      campaignId = active?.id ?? null;
    }

    const result = await sql<{
      id: string;
      campaign_id: string | null;
      delivery_at: string;
      cooperative_or_mill: string | null;
      ticket_number: string | null;
      total_kg: number;
      fields: unknown;
      settlement_id: string | null;
      settlement_number: string | null;
    }>`
      SELECT hd.id,
             hd.campaign_id,
             hd.delivery_at,
             hd.cooperative_or_mill,
             hd.ticket_number,
             hd.total_kg::double precision,
             COALESCE(json_agg(json_build_object('field_id', f.id, 'field_name', f.name, 'kg', hdf.kg::double precision)
               ORDER BY f.name) FILTER (WHERE f.id IS NOT NULL), '[]'::json) AS fields,
             hs.id AS settlement_id,
             hs.settlement_number
      FROM harvest_deliveries hd
      LEFT JOIN harvest_delivery_fields hdf ON hdf.delivery_id = hd.id
      LEFT JOIN fields f ON f.id = hdf.field_id
      LEFT JOIN harvest_settlement_deliveries hsd ON hsd.delivery_id = hd.id
      LEFT JOIN harvest_settlements hs ON hs.id = hsd.settlement_id AND hs.status = 'confirmed'
      WHERE hd.workspace_id = ${context.workspaceId}::uuid
        AND (${campaignId}::uuid IS NULL OR hd.campaign_id = ${campaignId}::uuid)
      GROUP BY hd.id, hs.id, hs.settlement_number
      ORDER BY hd.delivery_at DESC
    `.execute(database);

    return { campaign_id: campaignId, deliveries: result.rows };
  });
}
