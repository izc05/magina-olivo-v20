import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import {
  confirmExtractionFieldSchema,
  createDocumentSchema,
  requestOcrSchema,
  uuidSchema,
} from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';
import type { StoragePort } from '../storage/port.js';
import { StorageNotConfiguredError } from '../storage/port.js';
import type { OcrQueuePort } from '../ocr/port.js';
import { OcrQueueNotConfiguredError } from '../ocr/port.js';

async function domainRecordBelongsToWorkspace(db: DatabaseClient, workspaceId: string, domainType: string, recordId: string) {
  switch (domainType) {
    case 'irrigation': return Boolean(await db.selectFrom('irrigation_records').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'treatment': return Boolean(await db.selectFrom('treatment_records').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'fertilization': return Boolean(await db.selectFrom('fertilization_records').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'pruning': return Boolean(await db.selectFrom('pruning_records').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'expense': return Boolean(await db.selectFrom('expense_records').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'harvest_delivery': return Boolean(await db.selectFrom('harvest_deliveries').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    case 'harvest_result': return Boolean(await db.selectFrom('delivery_results').select('id').where('id', '=', recordId).where('workspace_id', '=', workspaceId).executeTakeFirst());
    default: return false;
  }
}

export function registerDocumentRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  storage: StoragePort,
  ocrQueue: OcrQueuePort,
) {
  app.post('/api/v1/documents', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const input = parseBody(createDocumentSchema, request.body, reply);
    if (!input) return;

    const existing = await database.selectFrom('documents').selectAll()
      .where('workspace_id', '=', context.workspaceId)
      .where('client_operation_id', '=', input.client_operation_id)
      .executeTakeFirst();
    if (existing) {
      const version = await database.selectFrom('document_versions').selectAll()
        .where('document_id', '=', existing.id)
        .orderBy('version_no', 'desc')
        .executeTakeFirst();
      return reply.code(200).send({ replayed: true, document: existing, version });
    }

    if (input.field_id) {
      const field = await fieldBelongsToWorkspace(database, input.field_id, context.workspaceId);
      if (!field) return reply.code(404).send({ error: 'field_not_found' });
    }
    if (input.domain_record_id && input.domain_type) {
      const allowed = await domainRecordBelongsToWorkspace(database, context.workspaceId, input.domain_type, input.domain_record_id);
      if (!allowed) return reply.code(404).send({ error: 'domain_record_not_found' });
    }

    const documentId = input.entity_id ?? randomUUID();
    const versionId = input.version_id ?? randomUUID();

    let upload;
    try {
      upload = await storage.reserveUpload({
        workspaceId: context.workspaceId,
        documentId,
        versionId,
        originalFilename: input.original_filename,
        mimeType: input.mime_type,
        byteSize: input.byte_size,
        sha256: input.sha256.toLowerCase(),
      });
    } catch (error) {
      if (error instanceof StorageNotConfiguredError) {
        return reply.code(503).send({ error: 'storage_not_configured' });
      }
      throw error;
    }

    const saved = await database.transaction().execute(async (trx) => {
      const document = await trx.insertInto('documents').values({
        id: documentId,
        workspace_id: context.workspaceId,
        client_operation_id: input.client_operation_id,
        kind: input.kind,
        title: input.title,
        status: 'active',
        created_by: context.userId,
        archived_at: null,
      }).returningAll().executeTakeFirstOrThrow();

      const version = await trx.insertInto('document_versions').values({
        id: versionId,
        document_id: documentId,
        version_no: 1,
        storage_key: upload.storageKey,
        original_filename: input.original_filename,
        mime_type: input.mime_type,
        byte_size: input.byte_size,
        sha256: input.sha256.toLowerCase(),
        created_by: context.userId,
      }).returningAll().executeTakeFirstOrThrow();

      const link = await trx.insertInto('attachment_links').values({
        workspace_id: context.workspaceId,
        document_id: documentId,
        field_id: input.field_id ?? null,
        domain_type: input.domain_type ?? null,
        domain_record_id: input.domain_record_id ?? null,
        relation: input.relation,
      }).returningAll().executeTakeFirstOrThrow();

      return { document, version, link };
    });

    return reply.code(201).send({ replayed: false, ...saved, upload });
  });

  app.get('/api/v1/fields/:fieldId/documents', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const rawFieldId = (request.params as { fieldId?: string }).fieldId;
    const parsedFieldId = uuidSchema.safeParse(rawFieldId);
    if (!parsedFieldId.success) return reply.code(400).send({ error: 'invalid_field_id' });
    const field = await fieldBelongsToWorkspace(database, parsedFieldId.data, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const documents = await database.selectFrom('attachment_links as al')
      .innerJoin('documents as d', 'd.id', 'al.document_id')
      .select([
        'd.id', 'd.kind', 'd.title', 'd.status', 'd.created_at',
        'al.domain_type', 'al.domain_record_id', 'al.relation',
      ])
      .where('al.workspace_id', '=', context.workspaceId)
      .where('al.field_id', '=', parsedFieldId.data)
      .where('d.status', '=', 'active')
      .orderBy('d.created_at', 'desc')
      .execute();

    return { field: { id: field.id, name: field.name }, documents };
  });

  app.post('/api/v1/documents/:documentId/ocr', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const rawDocumentId = (request.params as { documentId?: string }).documentId;
    const parsedDocumentId = uuidSchema.safeParse(rawDocumentId);
    if (!parsedDocumentId.success) return reply.code(400).send({ error: 'invalid_document_id' });
    const input = parseBody(requestOcrSchema, request.body, reply);
    if (!input) return;

    const version = await database.selectFrom('document_versions as dv')
      .innerJoin('documents as d', 'd.id', 'dv.document_id')
      .select(['dv.id', 'dv.storage_key', 'dv.mime_type', 'd.id as document_id'])
      .where('d.id', '=', parsedDocumentId.data)
      .where('d.workspace_id', '=', context.workspaceId)
      .where('dv.id', '=', input.document_version_id)
      .executeTakeFirst();
    if (!version) return reply.code(404).send({ error: 'document_version_not_found' });

    const ocrRunId = randomUUID();
    await database.insertInto('ocr_runs').values({
      id: ocrRunId,
      document_version_id: version.id,
      provider: input.preferred_provider,
      provider_version: null,
      status: 'queued',
      raw_text: null,
      confidence: null,
      error_code: null,
      error_message: null,
      started_at: null,
      completed_at: null,
    }).execute();

    try {
      const queued = await ocrQueue.enqueue({
        ocrRunId,
        documentVersionId: version.id,
        storageKey: version.storage_key,
        mimeType: version.mime_type,
        preferredProvider: input.preferred_provider,
      });
      return reply.code(202).send({ ocr_run_id: ocrRunId, job_id: queued.jobId, status: 'queued' });
    } catch (error) {
      await database.updateTable('ocr_runs').set({
        status: 'failed',
        error_code: 'queue_unavailable',
        error_message: error instanceof Error ? error.message : 'OCR queue unavailable',
        completed_at: new Date().toISOString(),
      }).where('id', '=', ocrRunId).execute();
      if (error instanceof OcrQueueNotConfiguredError) {
        return reply.code(503).send({ error: 'ocr_queue_not_configured', ocr_run_id: ocrRunId });
      }
      throw error;
    }
  });

  app.post('/api/v1/extractions/:extractionId/reviews', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;

    const rawExtractionId = (request.params as { extractionId?: string }).extractionId;
    const parsedExtractionId = uuidSchema.safeParse(rawExtractionId);
    if (!parsedExtractionId.success) return reply.code(400).send({ error: 'invalid_extraction_id' });
    const input = parseBody(confirmExtractionFieldSchema, request.body, reply);
    if (!input) return;
    if (input.extraction_run_id !== parsedExtractionId.data) {
      return reply.code(400).send({ error: 'extraction_id_mismatch' });
    }

    const extraction = await database.selectFrom('extraction_runs as er')
      .innerJoin('ocr_runs as oru', 'oru.id', 'er.ocr_run_id')
      .innerJoin('document_versions as dv', 'dv.id', 'oru.document_version_id')
      .innerJoin('documents as d', 'd.id', 'dv.document_id')
      .select(['er.id', 'er.data_json', 'er.confidence_json', 'er.status', 'd.id as document_id'])
      .where('er.id', '=', parsedExtractionId.data)
      .where('d.workspace_id', '=', context.workspaceId)
      .executeTakeFirst();
    if (!extraction) return reply.code(404).send({ error: 'extraction_not_found' });

    const review = await database.insertInto('extraction_reviews').values({
      id: randomUUID(),
      workspace_id: context.workspaceId,
      extraction_run_id: extraction.id,
      confirmed_fields: input.confirmed_fields,
      corrections: input.corrections ?? {},
      reviewed_by: context.userId,
    }).returningAll().executeTakeFirstOrThrow();

    return reply.code(201).send({
      review,
      extraction: {
        id: extraction.id,
        status: extraction.status,
        original_data: extraction.data_json,
        confidence: extraction.confidence_json,
      },
    });
  });
}
