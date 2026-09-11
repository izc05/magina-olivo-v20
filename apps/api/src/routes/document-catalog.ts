import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { documentKindSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, requireContext, requireDatabase } from '../http/helpers.js';

async function recordBelongsToField(
  db: DatabaseClient,
  workspaceId: string,
  fieldId: string,
  domainType: string,
  recordId: string,
) {
  if (domainType === 'expense') {
    return Boolean(await db.selectFrom('expense_records').select('id')
      .where('id', '=', recordId).where('workspace_id', '=', workspaceId).where('field_id', '=', fieldId).executeTakeFirst());
  }
  if (domainType === 'harvest_delivery') {
    return Boolean(await db.selectFrom('harvest_deliveries as hd')
      .innerJoin('harvest_delivery_fields as hdf', 'hdf.delivery_id', 'hd.id')
      .select('hd.id').where('hd.id', '=', recordId).where('hd.workspace_id', '=', workspaceId).where('hdf.field_id', '=', fieldId).executeTakeFirst());
  }
  if (domainType === 'harvest_result') {
    return Boolean(await db.selectFrom('delivery_results as dr')
      .innerJoin('harvest_deliveries as hd', 'hd.id', 'dr.delivery_id')
      .innerJoin('harvest_delivery_fields as hdf', 'hdf.delivery_id', 'hd.id')
      .select('dr.id').where('dr.id', '=', recordId).where('dr.workspace_id', '=', workspaceId).where('hdf.field_id', '=', fieldId).executeTakeFirst());
  }
  if (domainType === 'harvest_settlement') {
    const result = await sql<{ id: string }>`
      SELECT hs.id
      FROM harvest_settlements hs
      JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
      JOIN harvest_delivery_fields hdf ON hdf.delivery_id = hsd.delivery_id
      WHERE hs.id = ${recordId}::uuid
        AND hs.workspace_id = ${workspaceId}::uuid
        AND hdf.field_id = ${fieldId}::uuid
      LIMIT 1
    `.execute(db);
    return Boolean(result.rows[0]);
  }
  if (domainType === 'harvest_collection') {
    const result = await sql<{ id: string }>`
      SELECT hc.id
      FROM harvest_collections hc
      JOIN harvest_settlements hs ON hs.id = hc.settlement_id
      JOIN harvest_settlement_deliveries hsd ON hsd.settlement_id = hs.id
      JOIN harvest_delivery_fields hdf ON hdf.delivery_id = hsd.delivery_id
      WHERE hc.id = ${recordId}::uuid
        AND hc.workspace_id = ${workspaceId}::uuid
        AND hdf.field_id = ${fieldId}::uuid
      LIMIT 1
    `.execute(db);
    return Boolean(result.rows[0]);
  }
  return false;
}

export function registerDocumentCatalogRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/fields/:fieldId/document-catalog', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = uuidSchema.safeParse((request.params as { fieldId?: string }).fieldId);
    if (!fieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const field = await fieldBelongsToWorkspace(database, fieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const query = request.query as { kind?: string; campaignId?: string; unassigned?: string };
    let builder = database.selectFrom('attachment_links as al')
      .innerJoin('documents as d', 'd.id', 'al.document_id')
      .leftJoin('campaigns as c', 'c.id', 'al.campaign_id')
      .select([
        'd.id', 'd.kind', 'd.title', 'd.status', 'd.created_at',
        'al.domain_type', 'al.domain_record_id', 'al.relation', 'al.campaign_id',
        'c.name as campaign_name',
      ])
      .where('al.workspace_id', '=', context.workspaceId)
      .where('al.field_id', '=', fieldId.data)
      .where('d.status', '=', 'active');

    if (query.kind) {
      const kind = documentKindSchema.safeParse(query.kind);
      if (!kind.success) return reply.code(400).send({ error: 'invalid_document_kind' });
      builder = builder.where('d.kind', '=', kind.data);
    }
    if (query.campaignId) {
      const campaignId = uuidSchema.safeParse(query.campaignId);
      if (!campaignId.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
      builder = builder.where('al.campaign_id', '=', campaignId.data);
    } else if (query.unassigned === 'true') {
      builder = builder.where('al.campaign_id', 'is', null);
    }

    const documents = await builder.orderBy('d.created_at', 'desc').limit(200).execute();
    return { field: { id: field.id, name: field.name }, documents };
  });

  app.patch('/api/v1/documents/:documentId/campaign', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const documentId = uuidSchema.safeParse((request.params as { documentId?: string }).documentId);
    if (!documentId.success) return reply.code(400).send({ error: 'invalid_document_id' });

    const body = request.body as { campaign_id?: string | null } | null;
    const rawCampaignId = body?.campaign_id ?? null;
    let campaignId: string | null = null;
    if (rawCampaignId) {
      const parsedCampaign = uuidSchema.safeParse(rawCampaignId);
      if (!parsedCampaign.success) return reply.code(400).send({ error: 'invalid_campaign_id' });
      const campaign = await database.selectFrom('campaigns').select('id')
        .where('id', '=', parsedCampaign.data)
        .where('workspace_id', '=', context.workspaceId)
        .executeTakeFirst();
      if (!campaign) return reply.code(404).send({ error: 'campaign_not_found' });
      campaignId = campaign.id;
    }

    const document = await database.selectFrom('documents').select('id')
      .where('id', '=', documentId.data)
      .where('workspace_id', '=', context.workspaceId)
      .executeTakeFirst();
    if (!document) return reply.code(404).send({ error: 'document_not_found' });

    const links = await database.updateTable('attachment_links')
      .set({ campaign_id: campaignId })
      .where('workspace_id', '=', context.workspaceId)
      .where('document_id', '=', documentId.data)
      .returning(['id', 'field_id', 'campaign_id', 'domain_type', 'domain_record_id', 'relation'])
      .execute();
    if (!links.length) return reply.code(404).send({ error: 'document_link_not_found' });

    return { document_id: documentId.data, campaign_id: campaignId, links };
  });

  app.post('/api/v1/documents/:documentId/link-domain', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const documentId = uuidSchema.safeParse((request.params as { documentId?: string }).documentId);
    if (!documentId.success) return reply.code(400).send({ error: 'invalid_document_id' });
    const body = request.body as { field_id?: string; domain_type?: string; domain_record_id?: string } | null;
    const fieldId = uuidSchema.safeParse(body?.field_id);
    const recordId = uuidSchema.safeParse(body?.domain_record_id);
    const domainType = body?.domain_type ?? '';
    if (!fieldId.success || !recordId.success || !['expense', 'harvest_delivery', 'harvest_result', 'harvest_settlement', 'harvest_collection'].includes(domainType)) {
      return reply.code(400).send({ error: 'invalid_document_domain_link' });
    }

    const document = await database.selectFrom('documents').select('id')
      .where('id', '=', documentId.data).where('workspace_id', '=', context.workspaceId).where('status', '=', 'active').executeTakeFirst();
    if (!document) return reply.code(404).send({ error: 'document_not_found' });
    const field = await fieldBelongsToWorkspace(database, fieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });
    if (!await recordBelongsToField(database, context.workspaceId, fieldId.data, domainType, recordId.data)) {
      return reply.code(404).send({ error: 'domain_record_not_found_for_field' });
    }

    const existing = await database.selectFrom('attachment_links').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('document_id', '=', documentId.data)
      .where('field_id', '=', fieldId.data)
      .where('domain_type', '=', domainType)
      .where('domain_record_id', '=', recordId.data)
      .executeTakeFirst();
    if (existing) return reply.send({ replayed: true, link: existing });

    const generic = await database.selectFrom('attachment_links').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('document_id', '=', documentId.data)
      .where('field_id', '=', fieldId.data)
      .where('domain_record_id', 'is', null)
      .orderBy('created_at', 'asc')
      .executeTakeFirst();

    if (generic) {
      const link = await database.updateTable('attachment_links').set({
        domain_type: domainType,
        domain_record_id: recordId.data,
        relation: 'source_document',
      }).where('id', '=', generic.id).returningAll().executeTakeFirstOrThrow();
      return reply.code(201).send({ replayed: false, reused_generic_link: true, link });
    }

    const sourceLink = await database.selectFrom('attachment_links').select('campaign_id')
      .where('workspace_id', '=', context.workspaceId).where('document_id', '=', documentId.data).where('field_id', '=', fieldId.data).executeTakeFirst();
    const link = await database.insertInto('attachment_links').values({
      workspace_id: context.workspaceId,
      document_id: documentId.data,
      field_id: fieldId.data,
      domain_type: domainType,
      domain_record_id: recordId.data,
      relation: 'source_document',
      campaign_id: sourceLink?.campaign_id ?? null,
    }).returningAll().executeTakeFirstOrThrow();

    return reply.code(201).send({ replayed: false, reused_generic_link: false, link });
  });
}
