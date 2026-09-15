import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const workTypeSchema = z.enum([
  'pruning','shredding','harvest','treatment','fertilization','irrigation',
  'mowing','tillage','transport','manual-work','machinery-work','other',
]);

const listSchema = z.object({
  q: z.string().trim().max(120).optional(),
  workspace_id: z.string().uuid().optional(),
  field_id: z.string().uuid().optional(),
  campaign_id: z.string().uuid().optional(),
  type: workTypeSchema.optional(),
  performed_for: z.enum(['all','self','third-party']).default('all'),
  payment_status: z.enum(['all','not-applicable','pending','partial','paid']).default('all'),
  from: z.string().regex(datePattern).optional(),
  to: z.string().regex(datePattern).optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
}).refine((value) => !value.from || !value.to || value.from <= value.to, { path: ['to'], message: 'invalid_range' });

const updateSchema = z.object({
  title: z.string().trim().min(1).max(180).optional(),
  occurred_on: z.string().regex(datePattern).optional(),
  type: workTypeSchema.optional(),
  notes: z.string().trim().max(5000).nullable().optional(),
  campaign_id: z.string().uuid().nullable().optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

type WorkRow = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  field_id: string | null;
  field_name: string | null;
  customer_site_id: string | null;
  customer_site_name: string | null;
  campaign_id: string | null;
  campaign_name: string | null;
  type: string;
  occurred_on: string;
  title: string;
  notes: string | null;
  performed_for: 'self' | 'third-party';
  customer_party_id: string | null;
  customer_name: string | null;
  quoted_amount_eur: number | null;
  charge_eur: number | null;
  collected_eur: number | null;
  payment_status: string;
  invoice_reference: string | null;
  created_by: string;
  created_by_name: string | null;
  participants_count: number;
  resources_count: number;
  participant_cost_eur: number;
  resource_cost_eur: number;
  collection_total_eur: number;
  created_at: string | Date;
  updated_at: string | Date;
};

async function readWork(database: DatabaseClient, workId: string) {
  const result = await sql<WorkRow>`
    SELECT wr.id::text, wr.workspace_id::text, w.name AS workspace_name,
           wr.field_id::text, f.name AS field_name,
           wr.customer_site_id::text, cs.name AS customer_site_name,
           wr.campaign_id::text, c.name AS campaign_name,
           wr.type, wr.occurred_on::text, wr.title, wr.notes, wr.performed_for,
           wr.customer_party_id::text, p.display_name AS customer_name,
           wr.quoted_amount_eur::double precision, wr.charge_eur::double precision,
           wr.collected_eur::double precision, wr.payment_status, wr.invoice_reference,
           wr.created_by::text, u.display_name AS created_by_name,
           (SELECT count(*)::int FROM work_participants wp WHERE wp.work_id=wr.id) AS participants_count,
           (SELECT count(*)::int FROM work_resources rs WHERE rs.work_id=wr.id) AS resources_count,
           COALESCE((SELECT sum(wp.cost_eur) FROM work_participants wp WHERE wp.work_id=wr.id),0)::double precision AS participant_cost_eur,
           COALESCE((SELECT sum(rs.cost_eur) FROM work_resources rs WHERE rs.work_id=wr.id),0)::double precision AS resource_cost_eur,
           COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc WHERE wc.work_id=wr.id),0)::double precision AS collection_total_eur,
           wr.created_at, wr.updated_at
    FROM work_records wr
    JOIN workspaces w ON w.id=wr.workspace_id
    LEFT JOIN fields f ON f.id=wr.field_id AND f.workspace_id=wr.workspace_id
    LEFT JOIN customer_sites cs ON cs.id=wr.customer_site_id AND cs.workspace_id=wr.workspace_id
    LEFT JOIN campaigns c ON c.id=wr.campaign_id AND c.workspace_id=wr.workspace_id
    LEFT JOIN parties p ON p.id=wr.customer_party_id AND p.workspace_id=wr.workspace_id
    LEFT JOIN users u ON u.id=wr.created_by
    WHERE wr.id=${workId}::uuid
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

export function registerAdminWorkActivityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/works', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = listSchema.safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error', issues: parsed.error.issues });
    const input = parsed.data;
    const term = input.q ? `%${input.q}%` : null;

    const result = await sql<WorkRow>`
      SELECT wr.id::text, wr.workspace_id::text, w.name AS workspace_name,
             wr.field_id::text, f.name AS field_name,
             wr.customer_site_id::text, cs.name AS customer_site_name,
             wr.campaign_id::text, c.name AS campaign_name,
             wr.type, wr.occurred_on::text, wr.title, wr.notes, wr.performed_for,
             wr.customer_party_id::text, p.display_name AS customer_name,
             wr.quoted_amount_eur::double precision, wr.charge_eur::double precision,
             wr.collected_eur::double precision, wr.payment_status, wr.invoice_reference,
             wr.created_by::text, u.display_name AS created_by_name,
             (SELECT count(*)::int FROM work_participants wp WHERE wp.work_id=wr.id) AS participants_count,
             (SELECT count(*)::int FROM work_resources rs WHERE rs.work_id=wr.id) AS resources_count,
             COALESCE((SELECT sum(wp.cost_eur) FROM work_participants wp WHERE wp.work_id=wr.id),0)::double precision AS participant_cost_eur,
             COALESCE((SELECT sum(rs.cost_eur) FROM work_resources rs WHERE rs.work_id=wr.id),0)::double precision AS resource_cost_eur,
             COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc WHERE wc.work_id=wr.id),0)::double precision AS collection_total_eur,
             wr.created_at, wr.updated_at
      FROM work_records wr
      JOIN workspaces w ON w.id=wr.workspace_id
      LEFT JOIN fields f ON f.id=wr.field_id AND f.workspace_id=wr.workspace_id
      LEFT JOIN customer_sites cs ON cs.id=wr.customer_site_id AND cs.workspace_id=wr.workspace_id
      LEFT JOIN campaigns c ON c.id=wr.campaign_id AND c.workspace_id=wr.workspace_id
      LEFT JOIN parties p ON p.id=wr.customer_party_id AND p.workspace_id=wr.workspace_id
      LEFT JOIN users u ON u.id=wr.created_by
      WHERE (${input.workspace_id ?? null}::uuid IS NULL OR wr.workspace_id=${input.workspace_id ?? null}::uuid)
        AND (${input.field_id ?? null}::uuid IS NULL OR wr.field_id=${input.field_id ?? null}::uuid)
        AND (${input.campaign_id ?? null}::uuid IS NULL OR wr.campaign_id=${input.campaign_id ?? null}::uuid)
        AND (${input.type ?? null}::text IS NULL OR wr.type=${input.type ?? null})
        AND (${input.performed_for}='all' OR wr.performed_for=${input.performed_for})
        AND (${input.payment_status}='all' OR wr.payment_status=${input.payment_status})
        AND (${input.from ?? null}::date IS NULL OR wr.occurred_on >= ${input.from ?? null}::date)
        AND (${input.to ?? null}::date IS NULL OR wr.occurred_on <= ${input.to ?? null}::date)
        AND (${term}::text IS NULL OR wr.title ILIKE ${term} OR w.name ILIKE ${term} OR f.name ILIKE ${term} OR cs.name ILIKE ${term} OR p.display_name ILIKE ${term})
      ORDER BY wr.occurred_on DESC, wr.created_at DESC
      LIMIT ${input.limit}
    `.execute(auth.database);

    const counts = await sql<{
      total_30d: number;
      self_30d: number;
      third_party_30d: number;
      pending_receivables: number;
      pending_eur: number;
      cost_30d: number;
    }>`
      SELECT
        count(*) FILTER (WHERE wr.occurred_on >= current_date - interval '30 days')::int AS total_30d,
        count(*) FILTER (WHERE wr.occurred_on >= current_date - interval '30 days' AND wr.performed_for='self')::int AS self_30d,
        count(*) FILTER (WHERE wr.occurred_on >= current_date - interval '30 days' AND wr.performed_for='third-party')::int AS third_party_30d,
        count(*) FILTER (WHERE wr.performed_for='third-party' AND GREATEST(COALESCE(wr.charge_eur,0)-COALESCE(wr.collected_eur,0),0)>0)::int AS pending_receivables,
        COALESCE(sum(GREATEST(COALESCE(wr.charge_eur,0)-COALESCE(wr.collected_eur,0),0)) FILTER (WHERE wr.performed_for='third-party'),0)::double precision AS pending_eur,
        COALESCE(sum(
          COALESCE((SELECT sum(wp.cost_eur) FROM work_participants wp WHERE wp.work_id=wr.id),0)
          + COALESCE((SELECT sum(rs.cost_eur) FROM work_resources rs WHERE rs.work_id=wr.id),0)
        ) FILTER (WHERE wr.occurred_on >= current_date - interval '30 days'),0)::double precision AS cost_30d
      FROM work_records wr
    `.execute(auth.database);

    return { works: result.rows, counts: counts.rows[0] ?? { total_30d: 0, self_30d: 0, third_party_30d: 0, pending_receivables: 0, pending_eur: 0, cost_30d: 0 } };
  });

  app.get('/api/v1/admin/works/:workId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const params = z.object({ workId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_work_id' });
    const work = await readWork(auth.database, params.data.workId);
    if (!work) return reply.code(404).send({ error: 'work_not_found' });

    const [participants, resources, collections] = await Promise.all([
      sql`
        SELECT id::text, party_id::text, display_name, role, quantity::double precision, unit,
               rate_eur::double precision, cost_eur::double precision, created_at
        FROM work_participants WHERE work_id=${work.id}::uuid ORDER BY created_at, id
      `.execute(auth.database),
      sql`
        SELECT id::text, kind, machinery_id::text, material_id::text, supplier_party_id::text,
               name, quantity::double precision, unit, unit_cost_eur::double precision,
               cost_eur::double precision, created_at
        FROM work_resources WHERE work_id=${work.id}::uuid ORDER BY created_at, id
      `.execute(auth.database),
      sql`
        SELECT id::text, collected_on::text, amount_eur::double precision, method, reference, notes, created_at
        FROM work_collections WHERE work_id=${work.id}::uuid ORDER BY collected_on DESC, created_at DESC
      `.execute(auth.database),
    ]);

    return { work, participants: participants.rows, resources: resources.rows, collections: collections.rows };
  });

  app.patch('/api/v1/admin/works/:workId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ workId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_work_id' });
    const input = parseBody(updateSchema, request.body, reply);
    if (!input) return;

    const current = await readWork(auth.database, params.data.workId);
    if (!current) return reply.code(404).send({ error: 'work_not_found' });

    const campaignSupplied = Object.prototype.hasOwnProperty.call(input, 'campaign_id');
    const notesSupplied = Object.prototype.hasOwnProperty.call(input, 'notes');
    const nextCampaignId = campaignSupplied ? input.campaign_id ?? null : current.campaign_id;
    const nextDate = input.occurred_on ?? current.occurred_on;

    if (nextCampaignId) {
      const campaign = await sql<{ id: string; start_date: string; end_date: string | null }>`
        SELECT id::text, start_date::text, end_date::text
        FROM campaigns
        WHERE id=${nextCampaignId}::uuid AND workspace_id=${current.workspace_id}::uuid
        LIMIT 1
      `.execute(auth.database);
      const row = campaign.rows[0];
      if (!row) return reply.code(409).send({ error: 'campaign_not_in_work_workspace' });
      if (nextDate < row.start_date || (row.end_date && nextDate > row.end_date)) {
        return reply.code(409).send({ error: 'work_date_outside_campaign' });
      }
    }

    await sql`
      UPDATE work_records
      SET title=COALESCE(${input.title ?? null}, title),
          occurred_on=COALESCE(${input.occurred_on ?? null}::date, occurred_on),
          type=COALESCE(${input.type ?? null}, type),
          notes=CASE WHEN ${notesSupplied} THEN ${input.notes ?? null} ELSE notes END,
          campaign_id=CASE WHEN ${campaignSupplied} THEN ${input.campaign_id ?? null}::uuid ELSE campaign_id END,
          updated_at=now()
      WHERE id=${current.id}::uuid
    `.execute(auth.database);

    const work = await readWork(auth.database, current.id);
    if (!work) return reply.code(404).send({ error: 'work_not_found_after_update' });
    await auditAdminAction(auth.database, auth.access, 'work.updated', 'work_record', current.id, {
      workspace_id: current.workspace_id,
      immutable_links_preserved: true,
      from: { title: current.title, occurred_on: current.occurred_on, type: current.type, notes: current.notes, campaign_id: current.campaign_id },
      to: { title: work.title, occurred_on: work.occurred_on, type: work.type, notes: work.notes, campaign_id: work.campaign_id },
    });
    return { work };
  });
}
