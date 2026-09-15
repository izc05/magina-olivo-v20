import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';
import type { OcrJob, OcrQueuePort } from '../ocr/port.js';

const databaseUrl=process.env.DATABASE_URL;if(!databaseUrl)throw new Error('DATABASE_URL is required for admin documents smoke test.');
const db=createDatabase(databaseUrl);
const claims:GoogleIdentityClaims={subject:'documents-admin-subject',email:'documents-admin@magina.test',emailVerified:true,displayName:'Admin Documents',pictureUrl:null,givenName:'Admin',familyName:'Documents',hostedDomain:'magina.test'};
const verifier:GoogleIdentityVerifier={async verify(){return claims;}};
const queued:OcrJob[]=[]; const queue:OcrQueuePort={async enqueue(job){queued.push(job);return{jobId:`job-${job.ocr_run_id}`};}};
const app=buildApp({db,googleVerifier:verifier,ocrQueue:queue}); const credential='synthetic-documents-admin-token-'.padEnd(140,'d');
async function login(){const response=await app.inject({method:'POST',url:'/api/v1/auth/google',payload:{credential}});assert.ok(response.statusCode===200||response.statusCode===201,response.body);const cookieHeader=response.headers['set-cookie'];assert.equal(typeof cookieHeader,'string');return{body:response.json(),cookie:String(cookieHeader).split(';',1)[0]};}

try{
 await app.ready(); const loginResult=await login(); const userId=loginResult.body.user.id as string;
 const workspace=await db.insertInto('workspaces').values({name:'Workspace Documentos Admin',type:'family'}).returningAll().executeTakeFirstOrThrow();
 const fieldId=randomUUID(); await db.insertInto('fields').values({id:fieldId,workspace_id:workspace.id,client_operation_id:randomUUID(),name:'Finca Documento Smoke',crop:'olivar',status:'active'}).execute();
 const documentId=randomUUID(); const versionId=randomUUID(); const failedRunId=randomUUID();
 await sql`INSERT INTO documents(id,workspace_id,client_operation_id,kind,title,status,created_by) VALUES(${documentId}::uuid,${workspace.id}::uuid,${randomUUID()}::uuid,'albaran','Albarán OCR Smoke','active',${userId}::uuid)`.execute(db);
 await sql`INSERT INTO document_versions(id,document_id,version_no,storage_key,original_filename,mime_type,byte_size,sha256,created_by,upload_status,integrity_status,uploaded_at) VALUES(${versionId}::uuid,${documentId}::uuid,1,'private/test/document.pdf','document.pdf','application/pdf',128,${'a'.repeat(64)},${userId}::uuid,'uploaded','verified',now())`.execute(db);
 await sql`INSERT INTO attachment_links(workspace_id,document_id,field_id,relation) VALUES(${workspace.id}::uuid,${documentId}::uuid,${fieldId}::uuid,'attachment')`.execute(db);
 await sql`INSERT INTO ocr_runs(id,workspace_id,client_operation_id,document_version_id,provider,status,raw_text,error_code,error_message,completed_at) VALUES(${failedRunId}::uuid,${workspace.id}::uuid,${randomUUID()}::uuid,${versionId}::uuid,'tesseract','failed','TEXTO PRIVADO QUE NO DEBE SALIR','ocr_failed','fallo sintético',now())`.execute(db);

 const list=await app.inject({method:'GET',url:`/api/v1/admin/documents?workspace_id=${workspace.id}&ocr_status=failed`,headers:{cookie:loginResult.cookie}});assert.equal(list.statusCode,200,list.body);const item=list.json().documents.find((row:{id:string})=>row.id===documentId);assert.ok(item,list.body);assert.equal(item.latest_ocr_status,'failed');assert.equal(item.latest_ocr_error_code,'ocr_failed');assert.equal('raw_text' in item,false);assert.equal('storage_key' in item,false);
 const detail=await app.inject({method:'GET',url:`/api/v1/admin/documents/${documentId}`,headers:{cookie:loginResult.cookie}});assert.equal(detail.statusCode,200,detail.body);assert.equal(detail.json().ocr_runs.length,1);assert.equal('raw_text' in detail.json().ocr_runs[0],false);assert.equal('storage_key' in detail.json().versions[0],false);

 const archive=await app.inject({method:'PATCH',url:`/api/v1/admin/documents/${documentId}/status`,headers:{cookie:loginResult.cookie},payload:{status:'archived'}});assert.equal(archive.statusCode,200,archive.body);assert.equal(archive.json().document.status,'archived');
 const restore=await app.inject({method:'PATCH',url:`/api/v1/admin/documents/${documentId}/status`,headers:{cookie:loginResult.cookie},payload:{status:'active'}});assert.equal(restore.statusCode,200,restore.body);assert.equal(restore.json().document.status,'active');

 const retry=await app.inject({method:'POST',url:`/api/v1/admin/documents/${documentId}/ocr/retry`,headers:{cookie:loginResult.cookie},payload:{provider:'tesseract'}});assert.equal(retry.statusCode,202,retry.body);assert.equal(retry.json().status,'queued');assert.equal(queued.length,1);assert.equal(queued[0]?.document_version_id,versionId);assert.notEqual(retry.json().ocr_run_id,failedRunId);
 const runs=await sql<{id:string;status:string;raw_text:string|null}>`SELECT id::text,status,raw_text FROM ocr_runs WHERE document_version_id=${versionId}::uuid ORDER BY created_at`.execute(db);assert.equal(runs.rows.length,2);assert.ok(runs.rows.some((row)=>row.id===failedRunId&&row.status==='failed'&&row.raw_text==='TEXTO PRIVADO QUE NO DEBE SALIR'));assert.ok(runs.rows.some((row)=>row.id===retry.json().ocr_run_id&&row.status==='queued'&&row.raw_text===null));
 const audit=await app.inject({method:'GET',url:'/api/v1/admin/audit',headers:{cookie:loginResult.cookie}});assert.equal(audit.statusCode,200,audit.body);const actions=audit.json().entries.map((entry:{action:string})=>entry.action);assert.ok(actions.includes('document.status_updated'));assert.ok(actions.includes('ocr.retry_queued'));
 console.log('ADMIN_DOCUMENTS_SMOKE_OK');
}finally{await app.close();await db.destroy();}
