import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import type { OcrQueuePort } from '../ocr/port.js';
import { OcrQueueNotConfiguredError } from '../ocr/port.js';

const listSchema = z.object({
  q: z.string().trim().max(120).optional(),
  workspace_id: z.string().uuid().optional(),
  document_status: z.enum(['all','active','archived']).default('all'),
  upload_status: z.enum(['all','reserved','uploaded','failed']).default('all'),
  ocr_status: z.enum(['all','none','queued','processing','succeeded','failed']).default('all'),
  limit: z.coerce.number().int().min(1).max(500).default(200),
});

const statusSchema = z.object({ status: z.enum(['active','archived']) });
const retrySchema = z.object({ provider: z.string().trim().min(1).max(80).optional() });

type DocumentRow = {
  id:string; workspace_id:string; workspace_name:string; kind:string; title:string; status:'active'|'archived';
  created_by:string; created_by_name:string|null; created_at:string|Date; archived_at:string|Date|null;
  version_id:string|null; version_no:number|null; original_filename:string|null; mime_type:string|null; byte_size:number|null;
  upload_status:string|null; integrity_status:string|null; uploaded_at:string|Date|null;
  latest_ocr_id:string|null; latest_ocr_provider:string|null; latest_ocr_status:string|null;
  latest_ocr_error_code:string|null; latest_ocr_error_message:string|null; latest_ocr_created_at:string|Date|null;
  links_count:number;
};

async function readDocument(db: DatabaseClient, documentId:string) {
  const result = await sql<DocumentRow>`
    SELECT d.id::text, d.workspace_id::text, w.name AS workspace_name, d.kind, d.title, d.status,
           d.created_by::text, u.display_name AS created_by_name, d.created_at, d.archived_at,
           dv.id::text AS version_id, dv.version_no, dv.original_filename, dv.mime_type,
           dv.byte_size::double precision AS byte_size, dv.upload_status, dv.integrity_status, dv.uploaded_at,
           o.id::text AS latest_ocr_id, o.provider AS latest_ocr_provider, o.status AS latest_ocr_status,
           o.error_code AS latest_ocr_error_code, o.error_message AS latest_ocr_error_message, o.created_at AS latest_ocr_created_at,
           (SELECT count(*)::int FROM attachment_links al WHERE al.document_id=d.id) AS links_count
    FROM documents d
    JOIN workspaces w ON w.id=d.workspace_id
    LEFT JOIN users u ON u.id=d.created_by
    LEFT JOIN LATERAL (
      SELECT * FROM document_versions x WHERE x.document_id=d.id ORDER BY x.version_no DESC LIMIT 1
    ) dv ON true
    LEFT JOIN LATERAL (
      SELECT id, provider, status, error_code, error_message, created_at
      FROM ocr_runs x WHERE x.document_version_id=dv.id ORDER BY x.created_at DESC LIMIT 1
    ) o ON true
    WHERE d.id=${documentId}::uuid
    LIMIT 1
  `.execute(db);
  return result.rows[0] ?? null;
}

export function registerAdminDocumentRoutes(app: FastifyInstance, db: DatabaseClient | null, ocrQueue: OcrQueuePort) {
  app.get('/api/v1/admin/documents', async (request, reply) => {
    const auth=await requirePlatformAccess(request,reply,db); if(!auth)return;
    const parsed=listSchema.safeParse(request.query); if(!parsed.success)return reply.code(400).send({error:'validation_error',issues:parsed.error.issues});
    const input=parsed.data; const term=input.q?`%${input.q}%`:null;
    const result=await sql<DocumentRow>`
      SELECT d.id::text, d.workspace_id::text, w.name AS workspace_name, d.kind, d.title, d.status,
             d.created_by::text, u.display_name AS created_by_name, d.created_at, d.archived_at,
             dv.id::text AS version_id, dv.version_no, dv.original_filename, dv.mime_type,
             dv.byte_size::double precision AS byte_size, dv.upload_status, dv.integrity_status, dv.uploaded_at,
             o.id::text AS latest_ocr_id, o.provider AS latest_ocr_provider, o.status AS latest_ocr_status,
             o.error_code AS latest_ocr_error_code, o.error_message AS latest_ocr_error_message, o.created_at AS latest_ocr_created_at,
             (SELECT count(*)::int FROM attachment_links al WHERE al.document_id=d.id) AS links_count
      FROM documents d JOIN workspaces w ON w.id=d.workspace_id LEFT JOIN users u ON u.id=d.created_by
      LEFT JOIN LATERAL (SELECT * FROM document_versions x WHERE x.document_id=d.id ORDER BY x.version_no DESC LIMIT 1) dv ON true
      LEFT JOIN LATERAL (SELECT id,provider,status,error_code,error_message,created_at FROM ocr_runs x WHERE x.document_version_id=dv.id ORDER BY x.created_at DESC LIMIT 1) o ON true
      WHERE (${input.workspace_id??null}::uuid IS NULL OR d.workspace_id=${input.workspace_id??null}::uuid)
        AND (${input.document_status}='all' OR d.status=${input.document_status})
        AND (${input.upload_status}='all' OR dv.upload_status=${input.upload_status})
        AND (${input.ocr_status}='all' OR (${input.ocr_status}='none' AND o.id IS NULL) OR o.status=${input.ocr_status})
        AND (${term}::text IS NULL OR d.title ILIKE ${term} OR w.name ILIKE ${term} OR dv.original_filename ILIKE ${term})
      ORDER BY d.created_at DESC LIMIT ${input.limit}
    `.execute(auth.database);
    const counts=await sql<{active:number;archived:number;upload_failed:number;ocr_failed:number;ocr_pending:number}>`
      SELECT count(*) FILTER(WHERE d.status='active')::int active, count(*) FILTER(WHERE d.status='archived')::int archived,
      count(*) FILTER(WHERE dv.upload_status='failed')::int upload_failed,
      count(*) FILTER(WHERE o.status='failed')::int ocr_failed,
      count(*) FILTER(WHERE o.status IN ('queued','processing'))::int ocr_pending
      FROM documents d
      LEFT JOIN LATERAL (SELECT * FROM document_versions x WHERE x.document_id=d.id ORDER BY x.version_no DESC LIMIT 1) dv ON true
      LEFT JOIN LATERAL (SELECT status FROM ocr_runs x WHERE x.document_version_id=dv.id ORDER BY x.created_at DESC LIMIT 1) o ON true
    `.execute(auth.database);
    return {documents:result.rows,counts:counts.rows[0]??{active:0,archived:0,upload_failed:0,ocr_failed:0,ocr_pending:0}};
  });

  app.get('/api/v1/admin/documents/:documentId', async (request, reply) => {
    const auth=await requirePlatformAccess(request,reply,db); if(!auth)return;
    const p=z.object({documentId:z.string().uuid()}).safeParse(request.params); if(!p.success)return reply.code(400).send({error:'invalid_document_id'});
    const document=await readDocument(auth.database,p.data.documentId); if(!document)return reply.code(404).send({error:'document_not_found'});
    const [versions,links,ocrRuns,extractions]=await Promise.all([
      sql`SELECT id::text,version_no,original_filename,mime_type,byte_size::double precision,upload_status,integrity_status,uploaded_at,created_at FROM document_versions WHERE document_id=${document.id}::uuid ORDER BY version_no DESC`.execute(auth.database),
      sql`SELECT id::text,field_id::text,domain_type,domain_record_id::text,relation,created_at FROM attachment_links WHERE document_id=${document.id}::uuid ORDER BY created_at DESC`.execute(auth.database),
      sql`SELECT id::text,document_version_id::text,provider,provider_version,status,confidence::double precision,error_code,error_message,created_at,started_at,completed_at FROM ocr_runs WHERE workspace_id=${document.workspace_id}::uuid AND document_version_id IN (SELECT id FROM document_versions WHERE document_id=${document.id}::uuid) ORDER BY created_at DESC`.execute(auth.database),
      sql`SELECT er.id::text,er.ocr_run_id::text,er.document_type,er.schema_version,er.status,er.created_at,er.completed_at FROM extraction_runs er JOIN ocr_runs o ON o.id=er.ocr_run_id JOIN document_versions dv ON dv.id=o.document_version_id WHERE dv.document_id=${document.id}::uuid ORDER BY er.created_at DESC`.execute(auth.database),
    ]);
    return {document,versions:versions.rows,links:links.rows,ocr_runs:ocrRuns.rows,extractions:extractions.rows};
  });

  app.patch('/api/v1/admin/documents/:documentId/status', async (request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'admin'); if(!auth)return;
    const p=z.object({documentId:z.string().uuid()}).safeParse(request.params); if(!p.success)return reply.code(400).send({error:'invalid_document_id'});
    const b=statusSchema.safeParse(request.body); if(!b.success)return reply.code(400).send({error:'validation_error',issues:b.error.issues});
    const current=await readDocument(auth.database,p.data.documentId); if(!current)return reply.code(404).send({error:'document_not_found'});
    await sql`UPDATE documents SET status=${b.data.status}, archived_at=CASE WHEN ${b.data.status}='archived' THEN COALESCE(archived_at,now()) ELSE NULL END WHERE id=${current.id}::uuid`.execute(auth.database);
    await auditAdminAction(auth.database,auth.access,'document.status_updated','document',current.id,{from:current.status,to:b.data.status});
    return {document:await readDocument(auth.database,current.id)};
  });

  app.post('/api/v1/admin/documents/:documentId/ocr/retry', async (request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'admin'); if(!auth)return;
    const p=z.object({documentId:z.string().uuid()}).safeParse(request.params); if(!p.success)return reply.code(400).send({error:'invalid_document_id'});
    const b=retrySchema.safeParse(request.body??{}); if(!b.success)return reply.code(400).send({error:'validation_error',issues:b.error.issues});
    const current=await readDocument(auth.database,p.data.documentId); if(!current||!current.version_id)return reply.code(404).send({error:'document_version_not_found'});
    if(current.upload_status!=='uploaded'||current.integrity_status==='failed')return reply.code(409).send({error:'document_upload_not_ready'});
    const previous=await sql<{provider:string|null}>`SELECT provider FROM ocr_runs WHERE document_version_id=${current.version_id}::uuid ORDER BY created_at DESC LIMIT 1`.execute(auth.database);
    const provider=b.data.provider??previous.rows[0]?.provider??'tesseract';
    const runId=randomUUID();
    const version=await sql<{storage_key:string;mime_type:string;sha256:string}>`SELECT storage_key,mime_type,sha256 FROM document_versions WHERE id=${current.version_id}::uuid`.execute(auth.database);
    const v=version.rows[0]; if(!v)return reply.code(404).send({error:'document_version_not_found'});
    await sql`INSERT INTO ocr_runs(id,workspace_id,client_operation_id,document_version_id,provider,status) VALUES(${runId}::uuid,${current.workspace_id}::uuid,${randomUUID()}::uuid,${current.version_id}::uuid,${provider},'queued')`.execute(auth.database);
    try {
      const queued=await ocrQueue.enqueue({version:1,ocr_run_id:runId,document_version_id:current.version_id,storage_key:v.storage_key,mime_type:v.mime_type,expected_sha256_hex:v.sha256,preferred_provider:provider as never});
      await auditAdminAction(auth.database,auth.access,'ocr.retry_queued','ocr_run',runId,{document_id:current.id,document_version_id:current.version_id,provider,previous_ocr_run_id:current.latest_ocr_id});
      return reply.code(202).send({ocr_run_id:runId,job_id:queued.jobId,status:'queued'});
    } catch(error) {
      await sql`UPDATE ocr_runs SET status='failed',error_code='queue_unavailable',error_message=${error instanceof Error?error.message:'OCR queue unavailable'},completed_at=now() WHERE id=${runId}::uuid`.execute(auth.database);
      await auditAdminAction(auth.database,auth.access,'ocr.retry_failed','ocr_run',runId,{document_id:current.id,provider});
      if(error instanceof OcrQueueNotConfiguredError)return reply.code(503).send({error:'ocr_queue_not_configured',ocr_run_id:runId});
      throw error;
    }
  });
}
