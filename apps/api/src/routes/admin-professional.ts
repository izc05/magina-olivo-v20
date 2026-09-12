import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const listSchema = z.object({
  q: z.string().trim().max(120).optional(),
  workspace_id: z.string().uuid().optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
});
const customerUpdateSchema = z.object({
  display_name: z.string().trim().min(1).max(180).optional(),
  legal_name: z.string().trim().max(220).nullable().optional(),
  tax_id: z.string().trim().max(80).nullable().optional(),
  phone: z.string().trim().max(80).nullable().optional(),
  email: z.string().trim().email().max(240).nullable().optional(),
  notes: z.string().trim().max(5000).nullable().optional(),
  active: z.boolean().optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');
const quoteStatusSchema = z.object({ status: z.enum(['draft','sent','accepted','rejected','expired']) });
const invoiceUpdateSchema = z.object({
  invoice_number: z.string().trim().min(1).max(100).optional(),
  issued_on: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  due_on: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
  status: z.enum(['draft','issued','void']).optional(),
  notes: z.string().trim().max(5000).nullable().optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

type CustomerRow = {
  id:string; workspace_id:string; workspace_name:string; kind:string; display_name:string; legal_name:string|null;
  tax_id:string|null; phone:string|null; email:string|null; notes:string|null; active:boolean; created_at:string|Date; updated_at:string|Date;
  work_count:number; quote_count:number; invoice_count:number; charged_eur:number; collected_eur:number; pending_eur:number;
};

async function readCustomer(database:DatabaseClient, customerId:string) {
  const result=await sql<CustomerRow>`
    SELECT p.id::text,p.workspace_id::text,w.name AS workspace_name,p.kind,p.display_name,p.legal_name,p.tax_id,p.phone,p.email,p.notes,p.active,p.created_at,p.updated_at,
      (SELECT count(*)::int FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party') AS work_count,
      (SELECT count(*)::int FROM professional_quotes pq WHERE pq.workspace_id=p.workspace_id AND pq.customer_party_id=p.id) AS quote_count,
      (SELECT count(*)::int FROM professional_invoices pi WHERE pi.workspace_id=p.workspace_id AND pi.customer_party_id=p.id) AS invoice_count,
      COALESCE((SELECT sum(COALESCE(wr.charge_eur,0)) FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party'),0)::double precision AS charged_eur,
      COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id),0)::double precision AS collected_eur,
      GREATEST(COALESCE((SELECT sum(COALESCE(wr.charge_eur,0)) FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party'),0)-COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id),0),0)::double precision AS pending_eur
    FROM parties p JOIN workspaces w ON w.id=p.workspace_id
    WHERE p.id=${customerId}::uuid AND 'customer'=ANY(p.roles)
    LIMIT 1
  `.execute(database);
  return result.rows[0]??null;
}

export function registerAdminProfessionalRoutes(app:FastifyInstance, db:DatabaseClient|null) {
  app.get('/api/v1/admin/professional/overview', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db);if(!auth)return;
    const metrics=await sql<{
      customers:number; open_quotes:number; accepted_quotes:number; issued_invoices:number; overdue_invoices:number;
      billed_eur:number; collected_eur:number; pending_eur:number;
    }>`
      SELECT
        (SELECT count(*)::int FROM parties WHERE active=TRUE AND 'customer'=ANY(roles)) AS customers,
        (SELECT count(*)::int FROM professional_quotes WHERE status IN ('draft','sent','accepted')) AS open_quotes,
        (SELECT count(*)::int FROM professional_quotes WHERE status='accepted') AS accepted_quotes,
        (SELECT count(*)::int FROM professional_invoices WHERE status='issued') AS issued_invoices,
        (SELECT count(*)::int FROM professional_invoices pi WHERE pi.status='issued' AND pi.due_on < current_date AND GREATEST(pi.total_eur-COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0),0)>0) AS overdue_invoices,
        COALESCE((SELECT sum(total_eur) FROM professional_invoices WHERE status='issued'),0)::double precision AS billed_eur,
        COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.performed_for='third-party'),0)::double precision AS collected_eur,
        COALESCE((SELECT sum(GREATEST(pi.total_eur-COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0),0)) FROM professional_invoices pi WHERE pi.status='issued'),0)::double precision AS pending_eur
    `.execute(auth.database);
    return { metrics: metrics.rows[0] };
  });

  app.get('/api/v1/admin/professional/customers', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db);if(!auth)return;
    const parsed=listSchema.safeParse(request.query);if(!parsed.success)return reply.code(400).send({error:'validation_error',issues:parsed.error.issues});
    const input=parsed.data;const term=input.q?`%${input.q}%`:null;
    const result=await sql<CustomerRow>`
      SELECT p.id::text,p.workspace_id::text,w.name AS workspace_name,p.kind,p.display_name,p.legal_name,p.tax_id,p.phone,p.email,p.notes,p.active,p.created_at,p.updated_at,
        (SELECT count(*)::int FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party') AS work_count,
        (SELECT count(*)::int FROM professional_quotes pq WHERE pq.workspace_id=p.workspace_id AND pq.customer_party_id=p.id) AS quote_count,
        (SELECT count(*)::int FROM professional_invoices pi WHERE pi.workspace_id=p.workspace_id AND pi.customer_party_id=p.id) AS invoice_count,
        COALESCE((SELECT sum(COALESCE(wr.charge_eur,0)) FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party'),0)::double precision AS charged_eur,
        COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id),0)::double precision AS collected_eur,
        GREATEST(COALESCE((SELECT sum(COALESCE(wr.charge_eur,0)) FROM work_records wr WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id AND wr.performed_for='third-party'),0)-COALESCE((SELECT sum(wc.amount_eur) FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.workspace_id=p.workspace_id AND wr.customer_party_id=p.id),0),0)::double precision AS pending_eur
      FROM parties p JOIN workspaces w ON w.id=p.workspace_id
      WHERE 'customer'=ANY(p.roles)
        AND (${input.workspace_id??null}::uuid IS NULL OR p.workspace_id=${input.workspace_id??null}::uuid)
        AND (${term}::text IS NULL OR p.display_name ILIKE ${term} OR p.legal_name ILIKE ${term} OR p.email ILIKE ${term} OR p.tax_id ILIKE ${term} OR w.name ILIKE ${term})
      ORDER BY p.active DESC,p.updated_at DESC,p.display_name ASC LIMIT ${input.limit}
    `.execute(auth.database);
    return { customers:result.rows };
  });

  app.get('/api/v1/admin/professional/customers/:customerId', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db);if(!auth)return;
    const p=z.object({customerId:z.string().uuid()}).safeParse(request.params);if(!p.success)return reply.code(400).send({error:'invalid_customer_id'});
    const customer=await readCustomer(auth.database,p.data.customerId);if(!customer)return reply.code(404).send({error:'customer_not_found'});
    const [sites,works,quotes,invoices,collections]=await Promise.all([
      sql`SELECT id::text,name,address_text,municipality_label,province_label,active,notes FROM customer_sites WHERE workspace_id=${customer.workspace_id}::uuid AND customer_party_id=${customer.id}::uuid ORDER BY active DESC,name`.execute(auth.database),
      sql`SELECT id::text,occurred_on::text,title,charge_eur::double precision,payment_status,invoice_reference FROM work_records WHERE workspace_id=${customer.workspace_id}::uuid AND customer_party_id=${customer.id}::uuid AND performed_for='third-party' ORDER BY occurred_on DESC LIMIT 200`.execute(auth.database),
      sql`SELECT id::text,quote_number,title,issued_on::text,valid_until::text,status,subtotal_eur::double precision,tax_eur::double precision,total_eur::double precision,accepted_at,rejected_at,notes FROM professional_quotes WHERE workspace_id=${customer.workspace_id}::uuid AND customer_party_id=${customer.id}::uuid ORDER BY created_at DESC LIMIT 200`.execute(auth.database),
      sql`SELECT pi.id::text,pi.invoice_number,pi.issued_on::text,pi.due_on::text,pi.status,pi.subtotal_eur::double precision,pi.tax_eur::double precision,pi.total_eur::double precision,pi.notes,COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0)::double precision AS collected_eur,GREATEST(pi.total_eur-COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0),0)::double precision AS pending_eur FROM professional_invoices pi WHERE pi.workspace_id=${customer.workspace_id}::uuid AND pi.customer_party_id=${customer.id}::uuid ORDER BY pi.issued_on DESC NULLS LAST,pi.created_at DESC LIMIT 200`.execute(auth.database),
      sql`SELECT wc.id::text,wc.collected_on::text,wc.amount_eur::double precision,wc.method,wc.reference,wc.notes,wr.id::text AS work_id,wr.title AS work_title FROM work_collections wc JOIN work_records wr ON wr.id=wc.work_id WHERE wr.workspace_id=${customer.workspace_id}::uuid AND wr.customer_party_id=${customer.id}::uuid ORDER BY wc.collected_on DESC,wc.created_at DESC LIMIT 200`.execute(auth.database),
    ]);
    return {customer,sites:sites.rows,works:works.rows,quotes:quotes.rows,invoices:invoices.rows,collections:collections.rows};
  });

  app.patch('/api/v1/admin/professional/customers/:customerId', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'admin');if(!auth)return;
    const p=z.object({customerId:z.string().uuid()}).safeParse(request.params);if(!p.success)return reply.code(400).send({error:'invalid_customer_id'});
    const input=parseBody(customerUpdateSchema,request.body,reply);if(!input)return;
    const current=await readCustomer(auth.database,p.data.customerId);if(!current)return reply.code(404).send({error:'customer_not_found'});
    const legalSupplied=Object.prototype.hasOwnProperty.call(input,'legal_name'),taxSupplied=Object.prototype.hasOwnProperty.call(input,'tax_id'),phoneSupplied=Object.prototype.hasOwnProperty.call(input,'phone'),emailSupplied=Object.prototype.hasOwnProperty.call(input,'email'),notesSupplied=Object.prototype.hasOwnProperty.call(input,'notes');
    await sql`UPDATE parties SET display_name=COALESCE(${input.display_name??null},display_name),legal_name=CASE WHEN ${legalSupplied} THEN ${input.legal_name??null} ELSE legal_name END,tax_id=CASE WHEN ${taxSupplied} THEN ${input.tax_id??null} ELSE tax_id END,phone=CASE WHEN ${phoneSupplied} THEN ${input.phone??null} ELSE phone END,email=CASE WHEN ${emailSupplied} THEN ${input.email??null} ELSE email END,notes=CASE WHEN ${notesSupplied} THEN ${input.notes??null} ELSE notes END,active=COALESCE(${input.active??null}::boolean,active),updated_at=now() WHERE id=${current.id}::uuid`.execute(auth.database);
    const customer=await readCustomer(auth.database,current.id);
    await auditAdminAction(auth.database,auth.access,'professional_customer.updated','party',current.id,{workspace_id:current.workspace_id,from:{display_name:current.display_name,legal_name:current.legal_name,tax_id:current.tax_id,phone:current.phone,email:current.email,notes:current.notes,active:current.active},to:customer});
    return {customer};
  });

  app.get('/api/v1/admin/professional/quotes', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db);if(!auth)return;const parsed=listSchema.safeParse(request.query);if(!parsed.success)return reply.code(400).send({error:'validation_error',issues:parsed.error.issues});const input=parsed.data;const term=input.q?`%${input.q}%`:null;
    const result=await sql`SELECT pq.id::text,pq.workspace_id::text,w.name AS workspace_name,pq.customer_party_id::text,p.display_name AS customer_name,pq.quote_number,pq.title,pq.issued_on::text,pq.valid_until::text,pq.status,pq.subtotal_eur::double precision,pq.tax_eur::double precision,pq.total_eur::double precision,pq.accepted_at,pq.rejected_at,pq.created_at FROM professional_quotes pq JOIN workspaces w ON w.id=pq.workspace_id JOIN parties p ON p.id=pq.customer_party_id WHERE (${input.workspace_id??null}::uuid IS NULL OR pq.workspace_id=${input.workspace_id??null}::uuid) AND (${term}::text IS NULL OR pq.title ILIKE ${term} OR pq.quote_number ILIKE ${term} OR p.display_name ILIKE ${term} OR w.name ILIKE ${term}) ORDER BY pq.created_at DESC LIMIT ${input.limit}`.execute(auth.database);return {quotes:result.rows};
  });

  app.patch('/api/v1/admin/professional/quotes/:quoteId/status', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'admin');if(!auth)return;const p=z.object({quoteId:z.string().uuid()}).safeParse(request.params);if(!p.success)return reply.code(400).send({error:'invalid_quote_id'});const b=parseBody(quoteStatusSchema,request.body,reply);if(!b)return;
    const current=await sql<{workspace_id:string;status:string}>`SELECT workspace_id::text,status FROM professional_quotes WHERE id=${p.data.quoteId}::uuid`.execute(auth.database);const row=current.rows[0];if(!row)return reply.code(404).send({error:'quote_not_found'});if(row.status==='converted')return reply.code(409).send({error:'converted_quote_is_immutable'});if(row.status==='rejected'&&b.status!=='sent')return reply.code(409).send({error:'rejected_quote_must_be_resent_before_acceptance'});
    const result=await sql`UPDATE professional_quotes SET status=${b.status},accepted_at=CASE WHEN ${b.status}='accepted' THEN now() ELSE accepted_at END,rejected_at=CASE WHEN ${b.status}='rejected' THEN now() ELSE rejected_at END,updated_at=now() WHERE id=${p.data.quoteId}::uuid RETURNING id::text,status,accepted_at,rejected_at`.execute(auth.database);
    await auditAdminAction(auth.database,auth.access,'professional_quote.status_updated','professional_quote',p.data.quoteId,{workspace_id:row.workspace_id,from:row.status,to:b.status});return {quote:result.rows[0]};
  });

  app.get('/api/v1/admin/professional/invoices', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db);if(!auth)return;const parsed=listSchema.safeParse(request.query);if(!parsed.success)return reply.code(400).send({error:'validation_error',issues:parsed.error.issues});const input=parsed.data;const term=input.q?`%${input.q}%`:null;
    const result=await sql`SELECT pi.id::text,pi.workspace_id::text,w.name AS workspace_name,pi.customer_party_id::text,p.display_name AS customer_name,pi.invoice_number,pi.issued_on::text,pi.due_on::text,pi.status,pi.subtotal_eur::double precision,pi.tax_eur::double precision,pi.total_eur::double precision,pi.notes,COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0)::double precision AS collected_eur,GREATEST(pi.total_eur-COALESCE((SELECT sum(wc.amount_eur) FROM professional_invoice_works piw JOIN work_collections wc ON wc.work_id=piw.work_id WHERE piw.invoice_id=pi.id),0),0)::double precision AS pending_eur,pi.created_at FROM professional_invoices pi JOIN workspaces w ON w.id=pi.workspace_id JOIN parties p ON p.id=pi.customer_party_id WHERE (${input.workspace_id??null}::uuid IS NULL OR pi.workspace_id=${input.workspace_id??null}::uuid) AND (${term}::text IS NULL OR pi.invoice_number ILIKE ${term} OR p.display_name ILIKE ${term} OR w.name ILIKE ${term}) ORDER BY pi.issued_on DESC NULLS LAST,pi.created_at DESC LIMIT ${input.limit}`.execute(auth.database);return {invoices:result.rows};
  });

  app.patch('/api/v1/admin/professional/invoices/:invoiceId', async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'admin');if(!auth)return;const p=z.object({invoiceId:z.string().uuid()}).safeParse(request.params);if(!p.success)return reply.code(400).send({error:'invalid_invoice_id'});const input=parseBody(invoiceUpdateSchema,request.body,reply);if(!input)return;
    const current=await sql<{workspace_id:string;invoice_number:string|null;issued_on:string|null;due_on:string|null;status:string;notes:string|null}>`SELECT workspace_id::text,invoice_number,issued_on::text,due_on::text,status,notes FROM professional_invoices WHERE id=${p.data.invoiceId}::uuid`.execute(auth.database);const row=current.rows[0];if(!row)return reply.code(404).send({error:'invoice_not_found'});if(row.status==='void')return reply.code(409).send({error:'void_invoice_is_immutable'});
    const nextStatus=input.status??row.status,nextNumber=input.invoice_number??row.invoice_number,nextIssued=input.issued_on??row.issued_on;if(nextStatus==='issued'&&(!nextNumber||!nextIssued))return reply.code(400).send({error:'issued_invoice_requires_number_and_date'});const dueSupplied=Object.prototype.hasOwnProperty.call(input,'due_on'),notesSupplied=Object.prototype.hasOwnProperty.call(input,'notes');
    const invoice=await auth.database.transaction().execute(async(trx)=>{const updated=await sql`UPDATE professional_invoices SET invoice_number=COALESCE(${input.invoice_number??null},invoice_number),issued_on=COALESCE(${input.issued_on??null}::date,issued_on),due_on=CASE WHEN ${dueSupplied} THEN ${input.due_on??null}::date ELSE due_on END,status=${nextStatus},notes=CASE WHEN ${notesSupplied} THEN ${input.notes??null} ELSE notes END,updated_at=now() WHERE id=${p.data.invoiceId}::uuid RETURNING id::text,invoice_number,issued_on::text,due_on::text,status,notes,total_eur::double precision`.execute(trx);if(nextStatus==='issued'&&nextNumber)await sql`UPDATE work_records wr SET invoice_reference=${nextNumber},updated_at=now() FROM professional_invoice_works piw WHERE piw.invoice_id=${p.data.invoiceId}::uuid AND piw.work_id=wr.id`.execute(trx);if(nextStatus==='void')await sql`UPDATE work_records wr SET invoice_reference=NULL,updated_at=now() FROM professional_invoice_works piw WHERE piw.invoice_id=${p.data.invoiceId}::uuid AND piw.work_id=wr.id AND wr.invoice_reference=${row.invoice_number}`.execute(trx);return updated.rows[0];});
    await auditAdminAction(auth.database,auth.access,'professional_invoice.updated','professional_invoice',p.data.invoiceId,{workspace_id:row.workspace_id,from:row,to:invoice});return {invoice};
  });
}
