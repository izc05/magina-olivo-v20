import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl=process.env.DATABASE_URL;if(!databaseUrl)throw new Error('DATABASE_URL is required for admin professional smoke test.');
const db=createDatabase(databaseUrl);
const claims:GoogleIdentityClaims={subject:'professional-admin-subject',email:'professional-admin@magina.test',emailVerified:true,displayName:'Admin Professional',pictureUrl:null,givenName:'Admin',familyName:'Professional',hostedDomain:'magina.test'};
const verifier:GoogleIdentityVerifier={async verify(){return claims;}};
const app=buildApp({db,googleVerifier:verifier});const credential='synthetic-professional-admin-token-'.padEnd(140,'p');
async function login(){const r=await app.inject({method:'POST',url:'/api/v1/auth/google',payload:{credential}});assert.ok(r.statusCode===200||r.statusCode===201,r.body);const h=r.headers['set-cookie'];assert.equal(typeof h,'string');return{body:r.json(),cookie:String(h).split(';',1)[0]};}

try{
 await app.ready();const auth=await login();const userId=auth.body.user.id as string;
 const workspace=await db.insertInto('workspaces').values({name:'Profesional Admin Smoke',type:'professional'}).returningAll().executeTakeFirstOrThrow();
 const customerId=randomUUID();await sql`INSERT INTO parties(id,workspace_id,client_operation_id,kind,display_name,legal_name,tax_id,email,roles,active) VALUES(${customerId}::uuid,${workspace.id}::uuid,${randomUUID()}::uuid,'organization','Cliente Original','Cliente Original SL','B12345678','original@example.test',ARRAY['customer']::text[],TRUE)`.execute(db);
 const fieldId=randomUUID();await db.insertInto('fields').values({id:fieldId,workspace_id:workspace.id,client_operation_id:randomUUID(),name:'Finca Profesional Smoke',crop:'olivar',status:'active'}).execute();
 const workId=randomUUID();await sql`INSERT INTO work_records(id,workspace_id,field_id,client_operation_id,type,occurred_on,title,performed_for,customer_party_id,charge_eur,collected_eur,payment_status,created_by) VALUES(${workId}::uuid,${workspace.id}::uuid,${fieldId}::uuid,${randomUUID()}::uuid,'pruning','2026-09-10','Trabajo Cliente','third-party',${customerId}::uuid,121,0,'pending',${userId}::uuid)`.execute(db);
 const quoteId=randomUUID();await sql`INSERT INTO professional_quotes(id,workspace_id,customer_party_id,client_operation_id,quote_number,title,issued_on,valid_until,status,subtotal_eur,tax_eur,total_eur,created_by) VALUES(${quoteId}::uuid,${workspace.id}::uuid,${customerId}::uuid,${randomUUID()}::uuid,'P-2026-001','Presupuesto Smoke','2026-09-01','2026-09-30','sent',100,21,121,${userId}::uuid)`.execute(db);
 const invoiceId=randomUUID();await sql`INSERT INTO professional_invoices(id,workspace_id,customer_party_id,client_operation_id,status,subtotal_eur,tax_eur,total_eur,created_by) VALUES(${invoiceId}::uuid,${workspace.id}::uuid,${customerId}::uuid,${randomUUID()}::uuid,'draft',100,21,121,${userId}::uuid)`.execute(db);await sql`INSERT INTO professional_invoice_works(invoice_id,work_id,amount_eur) VALUES(${invoiceId}::uuid,${workId}::uuid,121)`.execute(db);

 const overview=await app.inject({method:'GET',url:'/api/v1/admin/professional/overview',headers:{cookie:auth.cookie}});assert.equal(overview.statusCode,200,overview.body);assert.ok(overview.json().metrics.customers>=1);assert.equal(typeof overview.json().metrics.pending_eur,'number');
 const customers=await app.inject({method:'GET',url:`/api/v1/admin/professional/customers?workspace_id=${workspace.id}`,headers:{cookie:auth.cookie}});assert.equal(customers.statusCode,200,customers.body);const customer=customers.json().customers.find((x:{id:string})=>x.id===customerId);assert.ok(customer,customers.body);assert.equal(customer.charged_eur,121);assert.equal(customer.pending_eur,121);
 const beforeSnapshots=await sql<{quote_name:string|null;invoice_name:string|null}>`SELECT (SELECT customer_snapshot_json->>'display_name' FROM professional_quotes WHERE id=${quoteId}::uuid) quote_name,(SELECT customer_snapshot_json->>'display_name' FROM professional_invoices WHERE id=${invoiceId}::uuid) invoice_name`.execute(db);assert.equal(beforeSnapshots.rows[0]?.quote_name,'Cliente Original');assert.equal(beforeSnapshots.rows[0]?.invoice_name,'Cliente Original');

 const editCustomer=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/customers/${customerId}`,headers:{cookie:auth.cookie},payload:{display_name:'Cliente Corregido',email:'nuevo@example.test',notes:'Revisado desde Admin'}});assert.equal(editCustomer.statusCode,200,editCustomer.body);assert.equal(editCustomer.json().customer.display_name,'Cliente Corregido');
 const afterSnapshots=await sql<{quote_name:string|null;invoice_name:string|null}>`SELECT (SELECT customer_snapshot_json->>'display_name' FROM professional_quotes WHERE id=${quoteId}::uuid) quote_name,(SELECT customer_snapshot_json->>'display_name' FROM professional_invoices WHERE id=${invoiceId}::uuid) invoice_name`.execute(db);assert.equal(afterSnapshots.rows[0]?.quote_name,'Cliente Original');assert.equal(afterSnapshots.rows[0]?.invoice_name,'Cliente Original');

 const reject=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/quotes/${quoteId}/status`,headers:{cookie:auth.cookie},payload:{status:'rejected'}});assert.equal(reject.statusCode,200,reject.body);
 const invalidAccept=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/quotes/${quoteId}/status`,headers:{cookie:auth.cookie},payload:{status:'accepted'}});assert.equal(invalidAccept.statusCode,409,invalidAccept.body);assert.equal(invalidAccept.json().error,'rejected_quote_must_be_resent_before_acceptance');
 const resend=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/quotes/${quoteId}/status`,headers:{cookie:auth.cookie},payload:{status:'sent'}});assert.equal(resend.statusCode,200,resend.body);
 const accept=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/quotes/${quoteId}/status`,headers:{cookie:auth.cookie},payload:{status:'accepted'}});assert.equal(accept.statusCode,200,accept.body);assert.equal(accept.json().quote.status,'accepted');

 const invalidIssue=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/invoices/${invoiceId}`,headers:{cookie:auth.cookie},payload:{status:'issued'}});assert.equal(invalidIssue.statusCode,400,invalidIssue.body);assert.equal(invalidIssue.json().error,'issued_invoice_requires_number_and_date');
 const issue=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/invoices/${invoiceId}`,headers:{cookie:auth.cookie},payload:{status:'issued',invoice_number:'F-2026-001',issued_on:'2026-09-12',due_on:'2026-09-30',notes:'Emitida desde soporte'}});assert.equal(issue.statusCode,200,issue.body);assert.equal(issue.json().invoice.status,'issued');
 const workAfterIssue=await sql<{invoice_reference:string|null}>`SELECT invoice_reference FROM work_records WHERE id=${workId}::uuid`.execute(db);assert.equal(workAfterIssue.rows[0]?.invoice_reference,'F-2026-001');
 const voided=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/invoices/${invoiceId}`,headers:{cookie:auth.cookie},payload:{status:'void'}});assert.equal(voided.statusCode,200,voided.body);const workAfterVoid=await sql<{invoice_reference:string|null}>`SELECT invoice_reference FROM work_records WHERE id=${workId}::uuid`.execute(db);assert.equal(workAfterVoid.rows[0]?.invoice_reference,null);
 const immutable=await app.inject({method:'PATCH',url:`/api/v1/admin/professional/invoices/${invoiceId}`,headers:{cookie:auth.cookie},payload:{notes:'No debe cambiar'}});assert.equal(immutable.statusCode,409,immutable.body);assert.equal(immutable.json().error,'void_invoice_is_immutable');

 const financial=await sql<{quote_total:number;invoice_total:number}>`SELECT (SELECT total_eur::double precision FROM professional_quotes WHERE id=${quoteId}::uuid) quote_total,(SELECT total_eur::double precision FROM professional_invoices WHERE id=${invoiceId}::uuid) invoice_total`.execute(db);assert.equal(financial.rows[0]?.quote_total,121);assert.equal(financial.rows[0]?.invoice_total,121);
 const audit=await app.inject({method:'GET',url:'/api/v1/admin/audit',headers:{cookie:auth.cookie}});assert.equal(audit.statusCode,200,audit.body);const actions=audit.json().entries.map((x:{action:string})=>x.action);assert.ok(actions.includes('professional_customer.updated'));assert.ok(actions.includes('professional_quote.status_updated'));assert.ok(actions.includes('professional_invoice.updated'));
 console.log('ADMIN_PROFESSIONAL_SMOKE_OK');
}finally{await app.close();await db.destroy();}
