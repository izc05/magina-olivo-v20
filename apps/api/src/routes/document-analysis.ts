import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

export function registerDocumentAnalysisRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/documents/:documentId/analysis', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const documentId = (request.params as { documentId?: string }).documentId;
    if (!documentId) return reply.code(404).send({ error: 'document_not_found' });

    const document = await database.selectFrom('documents')
      .select(['id', 'kind', 'title', 'status'])
      .where('id', '=', documentId)
      .where('workspace_id', '=', context.workspaceId)
      .where('status', '=', 'active')
      .executeTakeFirst();
    if (!document) return reply.code(404).send({ error: 'document_not_found' });

    const version = await database.selectFrom('document_versions')
      .select(['id', 'version_no', 'original_filename', 'mime_type', 'upload_status', 'integrity_status'])
      .where('document_id', '=', document.id)
      .orderBy('version_no', 'desc')
      .executeTakeFirst();

    if (!version) return reply.send({ document, version: null, ocr: null, extraction: null, review: null });

    const ocr = await database.selectFrom('ocr_runs')
      .select(['id', 'provider', 'provider_version', 'status', 'confidence', 'error_code', 'error_message', 'created_at', 'completed_at'])
      .where('document_version_id', '=', version.id)
      .orderBy('created_at', 'desc')
      .executeTakeFirst();

    if (!ocr) return reply.send({ document, version, ocr: null, extraction: null, review: null });

    const extraction = await database.selectFrom('extraction_runs')
      .select(['id', 'document_type', 'schema_version', 'status', 'data_json', 'confidence_json', 'created_at', 'completed_at'])
      .where('ocr_run_id', '=', ocr.id)
      .orderBy('created_at', 'desc')
      .executeTakeFirst();

    const review = extraction
      ? await database.selectFrom('extraction_reviews')
        .select(['id', 'confirmed_fields', 'corrections', 'reviewed_by', 'created_at'])
        .where('workspace_id', '=', context.workspaceId)
        .where('extraction_run_id', '=', extraction.id)
        .orderBy('created_at', 'desc')
        .executeTakeFirst()
      : null;

    return reply.send({ document, version, ocr, extraction: extraction ?? null, review: review ?? null });
  });
}
