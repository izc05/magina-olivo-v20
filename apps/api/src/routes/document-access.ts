import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';
import type { StoragePort } from '../storage/port.js';
import { StorageNotConfiguredError } from '../storage/port.js';

export function registerDocumentAccessRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  app.get('/api/v1/documents/:documentId/read-url', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const documentId = (request.params as { documentId?: string }).documentId;
    if (!documentId) return reply.code(404).send({ error: 'document_not_found' });

    const version = await database.selectFrom('document_versions as dv')
      .innerJoin('documents as d', 'd.id', 'dv.document_id')
      .select(['d.id as document_id', 'd.title', 'dv.id as version_id', 'dv.storage_key', 'dv.mime_type', 'dv.original_filename'])
      .where('d.id', '=', documentId)
      .where('d.workspace_id', '=', context.workspaceId)
      .where('d.status', '=', 'active')
      .where('dv.upload_status', '=', 'uploaded')
      .where('dv.integrity_status', '!=', 'failed')
      .orderBy('dv.version_no', 'desc')
      .executeTakeFirst();

    if (!version) return reply.code(404).send({ error: 'document_version_not_ready' });

    try {
      const url = await storage.createReadUrl(version.storage_key, 300);
      return reply.send({
        document_id: version.document_id,
        version_id: version.version_id,
        title: version.title,
        original_filename: version.original_filename,
        mime_type: version.mime_type,
        expires_in_seconds: 300,
        url,
      });
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) return reply.code(503).send({ error: 'storage_not_configured' });
      throw error;
    }
  });
}
