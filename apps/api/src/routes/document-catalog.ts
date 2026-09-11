import type { FastifyInstance } from 'fastify';
import { documentKindSchema, uuidSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, requireContext, requireDatabase } from '../http/helpers.js';

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
}
