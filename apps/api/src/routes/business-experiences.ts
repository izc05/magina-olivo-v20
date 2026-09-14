import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requirePlatformAccess, auditAdminAction } from '../admin/access.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const experienceTypes = ['aove_tasting','mill_visit','guided_tour','workshop','gastronomy','nature','culture','family','wellness','other'] as const;
const experienceStatuses = ['draft','published','archived'] as const;
const bookingStatuses = ['pending','confirmed','declined','cancelled','completed','no_show'] as const;
const editableRoles = new Set(['owner', 'manager', 'editor']);
const bookingRoles = new Set(['owner', 'manager']);

const experienceCoreSchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  title: z.string().trim().min(2).max(180),
  summary: z.string().trim().max(400).nullable().optional(),
  description: z.string().trim().max(8000).nullable().optional(),
  experienceType: z.enum(experienceTypes).default('other'),
  durationMinutes: z.number().int().min(1).max(10080).nullable().optional(),
  minPartySize: z.number().int().min(1).max(1000).default(1),
  maxPartySize: z.number().int().min(1).max(1000).nullable().optional(),
  priceCents: z.number().int().min(0).nullable().optional(),
  currency: z.string().trim().regex(/^[A-Z]{3}$/).default('EUR'),
  bookingMode: z.enum(['request','external','contact']).default('request'),
  bookingUrl: z.string().url().max(2000).nullable().optional(),
  meetingPointText: z.string().trim().max(600).nullable().optional(),
  location: z.object({ latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180) }).nullable().optional(),
  languages: z.array(z.string().trim().min(2).max(20)).max(20).default([]),
  includes: z.array(z.string().trim().min(1).max(240)).max(30).default([]),
  excludes: z.array(z.string().trim().min(1).max(240)).max(30).default([]),
  cancellationPolicy: z.string().trim().max(3000).nullable().optional(),
  coverImageUrl: z.string().url().max(2000).nullable().optional(),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  status: z.enum(experienceStatuses).default('draft'),
  sortOrder: z.number().int().min(0).max(10000).default(0),
});

function validateExperience(value: z.infer<typeof experienceCoreSchema>, context: z.RefinementCtx) {
  if (value.maxPartySize !== null && value.maxPartySize !== undefined && value.maxPartySize < value.minPartySize) {
    context.addIssue({ code: 'custom', path: ['maxPartySize'], message: 'max_party_below_min_party' });
  }
  if (value.validFrom && value.validUntil && value.validUntil < value.validFrom) {
    context.addIssue({ code: 'custom', path: ['validUntil'], message: 'experience_valid_until_before_start' });
  }
  if (value.bookingMode === 'external' && !value.bookingUrl) {
    context.addIssue({ code: 'custom', path: ['bookingUrl'], message: 'booking_url_required_for_external_mode' });
  }
}

const experienceCreateSchema = experienceCoreSchema.superRefine(validateExperience);
const experiencePatchSchema = experienceCoreSchema.partial().superRefine((value, context) => {
  if (!Object.keys(value).length) context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
});
const slotSchema = z.object({
  startsAt: z.coerce.date(),
  endsAt: z.coerce.date().nullable().optional(),
  capacity: z.number().int().min(1).max(10000),
  priceOverrideCents: z.number().int().min(0).nullable().optional(),
  status: z.enum(['open','full','cancelled','hidden']).default('open'),
}).superRefine((value, context) => {
  if (value.endsAt && value.endsAt <= value.startsAt) context.addIssue({ code: 'custom', path: ['endsAt'], message: 'slot_end_before_start' });
});
const bookingRequestSchema = z.object({
  slotId: z.string().uuid().nullable().optional(),
  contactName: z.string().trim().min(2).max(120),
  contactEmail: z.string().trim().email().max(254).nullable().optional(),
  contactPhone: z.string().trim().min(6).max(40).nullable().optional(),
  partySize: z.number().int().min(1).max(1000).default(1),
  requestedFor: z.coerce.date().nullable().optional(),
  message: z.string().trim().max(2000).nullable().optional(),
  consentBusinessContact: z.literal(true),
  sourceContext: z.string().trim().max(80).nullable().optional(),
  sourceKey: z.string().trim().max(160).nullable().optional(),
}).superRefine((value, context) => {
  if (!value.contactEmail && !value.contactPhone) context.addIssue({ code: 'custom', message: 'email_or_phone_required' });
});
const bookingPatchSchema = z.object({ status: z.enum(bookingStatuses) });

async function membership(database: DatabaseClient, businessId: string, userId: string) {
  const result = await sql<{ role: 'owner'|'manager'|'editor'|'analyst' }>`
    SELECT role FROM business_memberships
    WHERE business_id=${businessId}::uuid AND user_id=${userId}::uuid AND status='active'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function changeBookingStatus(database: DatabaseClient, bookingId: string, nextStatus: typeof bookingStatuses[number]) {
  return database.transaction().execute(async (trx) => {
    const bookingResult = await sql<{
      id:string; status:typeof bookingStatuses[number]; slot_id:string|null; party_size:number;
      business_lead_id:string|null; business_id:string;
    }>`
      SELECT eb.id, eb.status, eb.slot_id, eb.party_size, eb.business_lead_id, e.business_id
      FROM business_experience_bookings eb
      JOIN business_experiences e ON e.id=eb.experience_id
      WHERE eb.id=${bookingId}::uuid FOR UPDATE
    `.execute(trx);
    const booking = bookingResult.rows[0];
    if (!booking) return { error: 'booking_not_found' as const };
    if (booking.status === nextStatus) return { ok: true as const, businessId: booking.business_id };

    if (booking.slot_id && booking.status !== 'confirmed' && nextStatus === 'confirmed') {
      const slotResult = await sql<{ capacity:number; confirmed_count:number; status:string }>`
        SELECT capacity, confirmed_count, status FROM business_experience_slots
        WHERE id=${booking.slot_id}::uuid FOR UPDATE
      `.execute(trx);
      const slot = slotResult.rows[0];
      if (!slot || slot.status === 'cancelled' || slot.status === 'hidden') return { error: 'slot_unavailable' as const };
      if (slot.confirmed_count + booking.party_size > slot.capacity) return { error: 'slot_capacity_exceeded' as const };
      const nextCount = slot.confirmed_count + booking.party_size;
      await sql`UPDATE business_experience_slots SET confirmed_count=${nextCount}, status=CASE WHEN ${nextCount}>=capacity THEN 'full' ELSE 'open' END, updated_at=now() WHERE id=${booking.slot_id}::uuid`.execute(trx);
    }

    if (booking.slot_id && booking.status === 'confirmed' && nextStatus !== 'confirmed') {
      const slotResult = await sql<{ confirmed_count:number; status:string }>`SELECT confirmed_count,status FROM business_experience_slots WHERE id=${booking.slot_id}::uuid FOR UPDATE`.execute(trx);
      const slot = slotResult.rows[0];
      if (slot) {
        const nextCount = Math.max(0, slot.confirmed_count - booking.party_size);
        await sql`UPDATE business_experience_slots SET confirmed_count=${nextCount}, status=CASE WHEN status='full' THEN 'open' ELSE status END, updated_at=now() WHERE id=${booking.slot_id}::uuid`.execute(trx);
      }
    }

    await sql`
      UPDATE business_experience_bookings SET status=${nextStatus}, updated_at=now(),
        resolved_at=CASE WHEN ${nextStatus} IN ('declined','cancelled','completed','no_show') THEN now() ELSE resolved_at END
      WHERE id=${bookingId}::uuid
    `.execute(trx);
    if (booking.business_lead_id) {
      const leadStatus = nextStatus === 'confirmed' ? 'qualified'
        : nextStatus === 'completed' ? 'won'
        : ['declined','cancelled','no_show'].includes(nextStatus) ? 'lost' : null;
      if (leadStatus) await sql`UPDATE business_leads SET status=${leadStatus}, resolved_at=CASE WHEN ${leadStatus} IN ('won','lost') THEN now() ELSE resolved_at END, updated_at=now() WHERE id=${booking.business_lead_id}::uuid`.execute(trx);
    }
    return { ok: true as const, businessId: booking.business_id };
  });
}

function bookingStatusReply(result: Awaited<ReturnType<typeof changeBookingStatus>>, reply: FastifyReply) {
  if ('error' in result) {
    if (result.error === 'booking_not_found') return reply.code(404).send({ error: result.error });
    if (result.error === 'slot_capacity_exceeded') return reply.code(409).send({ error: result.error });
    return reply.code(409).send({ error: result.error });
  }
  return reply.send({ ok: true });
}

function registerPublicRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/experiences', async (request, reply) => {
    const database = requireDatabase(db, reply); if (!database) return;
    const parsed = z.object({
      q:z.string().trim().max(120).optional(), type:z.enum(experienceTypes).optional(), businessSlug:z.string().regex(slugPattern).optional(),
      municipalityId:z.string().uuid().optional(), limit:z.coerce.number().int().min(1).max(100).default(40), offset:z.coerce.number().int().min(0).default(0),
    }).safeParse(request.query ?? {});
    if (!parsed.success) return reply.code(400).send({ error:'invalid_experience_filters', issues:parsed.error.issues });
    const f=parsed.data; const q=f.q ?? null; const type=f.type ?? null; const businessSlug=f.businessSlug ?? null; const municipalityId=f.municipalityId ?? null;
    const rows = await sql<{
      id:string; slug:string; title:string; summary:string|null; experience_type:string; duration_minutes:number|null; min_party_size:number; max_party_size:number|null;
      price_cents:number|null; currency:string; booking_mode:string; cover_image_url:string|null; business_id:string; business_slug:string; business_name:string; municipality_name:string|null;
      next_slot:Date|null; available_slots:number;
    }>`
      SELECT e.id,e.slug,e.title,e.summary,e.experience_type,e.duration_minutes,e.min_party_size,e.max_party_size,e.price_cents,e.currency,e.booking_mode,e.cover_image_url,
             b.id AS business_id,b.slug AS business_slug,b.name AS business_name,m.name AS municipality_name,
             (SELECT min(s.starts_at) FROM business_experience_slots s WHERE s.experience_id=e.id AND s.status='open' AND s.starts_at>=now() AND s.confirmed_count<s.capacity) AS next_slot,
             (SELECT count(*)::int FROM business_experience_slots s WHERE s.experience_id=e.id AND s.status='open' AND s.starts_at>=now() AND s.confirmed_count<s.capacity) AS available_slots
      FROM business_experiences e JOIN businesses b ON b.id=e.business_id
      LEFT JOIN territory_municipalities m ON m.id=b.municipality_id
      WHERE e.status='published' AND b.status='published'
        AND (e.valid_from IS NULL OR e.valid_from<=now()) AND (e.valid_until IS NULL OR e.valid_until>=now())
        AND (${q}::text IS NULL OR e.title ILIKE '%'||${q}::text||'%' OR COALESCE(e.summary,'') ILIKE '%'||${q}::text||'%' OR b.name ILIKE '%'||${q}::text||'%')
        AND (${type}::text IS NULL OR e.experience_type=${type}::text)
        AND (${businessSlug}::text IS NULL OR b.slug=${businessSlug}::text)
        AND (${municipalityId}::uuid IS NULL OR b.municipality_id=${municipalityId}::uuid)
      ORDER BY e.sort_order,b.name,e.title LIMIT ${f.limit} OFFSET ${f.offset}
    `.execute(database);
    return { experiences: rows.rows, meta:{ limit:f.limit,offset:f.offset,count:rows.rows.length } };
  });

  app.get('/api/v1/public/businesses/:businessSlug/experiences/:slug', async (request, reply) => {
    const database=requireDatabase(db,reply); if(!database)return;
    const params=z.object({ businessSlug:z.string().regex(slugPattern), slug:z.string().regex(slugPattern) }).safeParse(request.params);
    if(!params.success)return reply.code(400).send({error:'invalid_experience_slug'});
    const exp=await sql<any>`
      SELECT e.*, b.slug AS business_slug,b.name AS business_name,b.logo_url AS business_logo,
             CASE WHEN e.location IS NULL THEN NULL ELSE ST_Y(e.location) END AS latitude,
             CASE WHEN e.location IS NULL THEN NULL ELSE ST_X(e.location) END AS longitude
      FROM business_experiences e JOIN businesses b ON b.id=e.business_id
      WHERE b.slug=${params.data.businessSlug} AND e.slug=${params.data.slug} AND b.status='published' AND e.status='published'
        AND (e.valid_from IS NULL OR e.valid_from<=now()) AND (e.valid_until IS NULL OR e.valid_until>=now()) LIMIT 1
    `.execute(database);
    const row=exp.rows[0]; if(!row)return reply.code(404).send({error:'experience_not_found'});
    const slots=await sql<any>`SELECT id,starts_at,ends_at,capacity,confirmed_count,price_override_cents,status FROM business_experience_slots WHERE experience_id=${row.id}::uuid AND status IN ('open','full') AND starts_at>=now() ORDER BY starts_at LIMIT 100`.execute(database);
    return { experience:{...row, location:row.latitude===null?null:{latitude:Number(row.latitude),longitude:Number(row.longitude)}, slots:slots.rows.map((s:any)=>({...s,availablePlaces:Math.max(0,s.capacity-s.confirmed_count)}))} };
  });

  app.post('/api/v1/public/businesses/:businessSlug/experiences/:slug/bookings', async (request, reply) => {
    const database=requireDatabase(db,reply); if(!database)return;
    const params=z.object({ businessSlug:z.string().regex(slugPattern), slug:z.string().regex(slugPattern) }).safeParse(request.params);
    const parsed=bookingRequestSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_booking',issues:parsed.success?undefined:parsed.error.issues});
    const input=parsed.data;
    const expResult=await sql<{id:string;business_id:string;title:string;min_party_size:number;max_party_size:number|null;price_cents:number|null;currency:string;booking_mode:string}>`
      SELECT e.id,e.business_id,e.title,e.min_party_size,e.max_party_size,e.price_cents,e.currency,e.booking_mode
      FROM business_experiences e JOIN businesses b ON b.id=e.business_id
      WHERE b.slug=${params.data.businessSlug} AND e.slug=${params.data.slug} AND e.status='published' AND b.status='published' LIMIT 1
    `.execute(database);
    const exp=expResult.rows[0]; if(!exp)return reply.code(404).send({error:'experience_not_found'});
    if(exp.booking_mode!=='request')return reply.code(409).send({error:'experience_not_requestable'});
    if(input.partySize<exp.min_party_size||(exp.max_party_size!==null&&input.partySize>exp.max_party_size))return reply.code(400).send({error:'party_size_out_of_range'});
    if(input.slotId){
      const slot=await sql<{id:string;capacity:number;confirmed_count:number;status:string;starts_at:Date}>`SELECT id,capacity,confirmed_count,status,starts_at FROM business_experience_slots WHERE id=${input.slotId}::uuid AND experience_id=${exp.id}::uuid LIMIT 1`.execute(database);
      const s=slot.rows[0]; if(!s)return reply.code(404).send({error:'experience_slot_not_found'});
      if(!['open','full'].includes(s.status)||s.confirmed_count+input.partySize>s.capacity)return reply.code(409).send({error:'slot_capacity_unavailable'});
    }
    const userId=readAuthenticatedUserId(request);
    const created=await database.transaction().execute(async(trx)=>{
      const lead=await sql<{id:string}>`
        INSERT INTO business_leads(business_id,experience_id,kind,contact_name,contact_email,contact_phone,message,requested_for,party_size,source_context,source_key,status,estimated_value_cents,currency,consent_business_contact)
        VALUES(${exp.business_id}::uuid,${exp.id}::uuid,'booking',${input.contactName},${input.contactEmail??null},${input.contactPhone??null},${input.message??null},${input.requestedFor??null},${input.partySize},${input.sourceContext??'experience'},${input.sourceKey??params.data.slug},'new',${exp.price_cents===null?null:exp.price_cents*input.partySize},${exp.currency},true)
        RETURNING id
      `.execute(trx);
      const leadId=lead.rows[0]?.id;
      const booking=await sql<{id:string}>`
        INSERT INTO business_experience_bookings(experience_id,slot_id,business_lead_id,user_id,contact_name,contact_email,contact_phone,party_size,requested_for,message,status,total_cents,currency,source_context,source_key)
        VALUES(${exp.id}::uuid,${input.slotId??null}::uuid,${leadId??null}::uuid,${userId}::uuid,${input.contactName},${input.contactEmail??null},${input.contactPhone??null},${input.partySize},${input.requestedFor??null},${input.message??null},'pending',${exp.price_cents===null?null:exp.price_cents*input.partySize},${exp.currency},${input.sourceContext??'experience'},${input.sourceKey??params.data.slug}) RETURNING id
      `.execute(trx);
      await sql`INSERT INTO business_events(business_id,experience_id,event_type,source_context,source_key,metadata) VALUES(${exp.business_id}::uuid,${exp.id}::uuid,'experience_booking_submit',${input.sourceContext??'experience'},${input.sourceKey??params.data.slug},${JSON.stringify({partySize:input.partySize})}::jsonb)`.execute(trx);
      return {bookingId:booking.rows[0]?.id,leadId};
    });
    return reply.code(201).send({ booking:{id:created.bookingId,status:'pending'}, leadId:created.leadId, message:'Solicitud enviada. La plaza queda pendiente hasta que la empresa la confirme.' });
  });
}

function registerAdminRoutes(app:FastifyInstance, db:DatabaseClient|null){
  app.get('/api/v1/admin/business-experiences',async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db); if(!auth)return;
    const experiences=await sql<any>`SELECT e.*,b.name AS business_name,b.slug AS business_slug FROM business_experiences e JOIN businesses b ON b.id=e.business_id ORDER BY e.updated_at DESC LIMIT 500`.execute(auth.database);
    const slots=await sql<any>`SELECT s.*,e.title AS experience_title,b.name AS business_name FROM business_experience_slots s JOIN business_experiences e ON e.id=s.experience_id JOIN businesses b ON b.id=e.business_id WHERE s.starts_at>=now()-interval '7 days' ORDER BY s.starts_at LIMIT 500`.execute(auth.database);
    const bookings=await sql<any>`SELECT eb.*,e.title AS experience_title,b.name AS business_name,s.starts_at AS slot_starts_at FROM business_experience_bookings eb JOIN business_experiences e ON e.id=eb.experience_id JOIN businesses b ON b.id=e.business_id LEFT JOIN business_experience_slots s ON s.id=eb.slot_id ORDER BY CASE eb.status WHEN 'pending' THEN 0 WHEN 'confirmed' THEN 1 ELSE 2 END, eb.created_at DESC LIMIT 500`.execute(auth.database);
    return {experiences:experiences.rows,slots:slots.rows,bookings:bookings.rows};
  });
  app.post('/api/v1/admin/businesses/:id/experiences',async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'editor'); if(!auth)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params); const parsed=experienceCreateSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience'});
    const i=parsed.data;
    const result=await sql<{id:string}>`INSERT INTO business_experiences(business_id,slug,title,summary,description,experience_type,duration_minutes,min_party_size,max_party_size,price_cents,currency,booking_mode,booking_url,meeting_point_text,location,languages,includes,excludes,cancellation_policy,cover_image_url,valid_from,valid_until,status,sort_order,created_by,updated_by,published_at) VALUES(${params.data.id}::uuid,${i.slug},${i.title},${i.summary??null},${i.description??null},${i.experienceType},${i.durationMinutes??null},${i.minPartySize},${i.maxPartySize??null},${i.priceCents??null},${i.currency},${i.bookingMode},${i.bookingUrl??null},${i.meetingPointText??null},${i.location?sql`ST_SetSRID(ST_MakePoint(${i.location.longitude},${i.location.latitude}),4326)`:null},${JSON.stringify(i.languages)}::jsonb,${JSON.stringify(i.includes)}::jsonb,${JSON.stringify(i.excludes)}::jsonb,${i.cancellationPolicy??null},${i.coverImageUrl??null},${i.validFrom??null},${i.validUntil??null},${i.status},${i.sortOrder},${auth.access.userId}::uuid,${auth.access.userId}::uuid,CASE WHEN ${i.status}='published' THEN now() ELSE NULL END) RETURNING id`.execute(auth.database);
    const id=result.rows[0]?.id; if(!id)return reply.code(500).send({error:'experience_create_failed'});
    await auditAdminAction(auth.database,auth.access,'business.experience_created','business_experience',id,{businessId:params.data.id,title:i.title});
    return reply.code(201).send({experience:{id}});
  });
  app.post('/api/v1/admin/business-experiences/:id/slots',async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'editor');if(!auth)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params);const parsed=slotSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_slot'});const i=parsed.data;
    const row=await sql<{id:string}>`INSERT INTO business_experience_slots(experience_id,starts_at,ends_at,capacity,price_override_cents,status) VALUES(${params.data.id}::uuid,${i.startsAt},${i.endsAt??null},${i.capacity},${i.priceOverrideCents??null},${i.status}) RETURNING id`.execute(auth.database);
    return reply.code(201).send({slot:{id:row.rows[0]?.id}});
  });
  app.patch('/api/v1/admin/business-experience-bookings/:id',async(request,reply)=>{
    const auth=await requirePlatformAccess(request,reply,db,'editor');if(!auth)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params);const parsed=bookingPatchSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_booking_status'});
    const result=await changeBookingStatus(auth.database,params.data.id,parsed.data.status);
    if('ok'in result)await auditAdminAction(auth.database,auth.access,'business.experience_booking_status','business_experience_booking',params.data.id,{status:parsed.data.status});
    return bookingStatusReply(result,reply);
  });
}

function registerOwnerRoutes(app:FastifyInstance,db:DatabaseClient|null){
  app.get('/api/v1/my/businesses/:id/experiences',async(request,reply)=>{
    const database=requireDatabase(db,reply);if(!database)return;const userId=requireAuthenticatedUser(request,reply);if(!userId)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params);if(!params.success)return reply.code(400).send({error:'invalid_business_id'});
    const access=await membership(database,params.data.id,userId);if(!access)return reply.code(403).send({error:'business_access_denied'});
    const experiences=await sql<any>`SELECT * FROM business_experiences WHERE business_id=${params.data.id}::uuid ORDER BY updated_at DESC`.execute(database);
    const bookings=await sql<any>`SELECT eb.*,e.title AS experience_title,s.starts_at AS slot_starts_at FROM business_experience_bookings eb JOIN business_experiences e ON e.id=eb.experience_id LEFT JOIN business_experience_slots s ON s.id=eb.slot_id WHERE e.business_id=${params.data.id}::uuid ORDER BY CASE eb.status WHEN 'pending' THEN 0 WHEN 'confirmed' THEN 1 ELSE 2 END,eb.created_at DESC LIMIT 300`.execute(database);
    const slots=await sql<any>`SELECT s.*,e.title AS experience_title FROM business_experience_slots s JOIN business_experiences e ON e.id=s.experience_id WHERE e.business_id=${params.data.id}::uuid AND s.starts_at>=now()-interval '7 days' ORDER BY s.starts_at LIMIT 300`.execute(database);
    return {role:access.role,experiences:experiences.rows,bookings:bookings.rows,slots:slots.rows};
  });
  app.post('/api/v1/my/businesses/:id/experiences',async(request,reply)=>{
    const database=requireDatabase(db,reply);if(!database)return;const userId=requireAuthenticatedUser(request,reply);if(!userId)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params);const parsed=experienceCreateSchema.safeParse(request.body);if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience'});
    const access=await membership(database,params.data.id,userId);if(!access||!editableRoles.has(access.role))return reply.code(403).send({error:'business_edit_denied'});const i=parsed.data;
    const row=await sql<{id:string}>`INSERT INTO business_experiences(business_id,slug,title,summary,description,experience_type,duration_minutes,min_party_size,max_party_size,price_cents,currency,booking_mode,booking_url,meeting_point_text,location,languages,includes,excludes,cancellation_policy,cover_image_url,valid_from,valid_until,status,sort_order,created_by,updated_by,published_at) VALUES(${params.data.id}::uuid,${i.slug},${i.title},${i.summary??null},${i.description??null},${i.experienceType},${i.durationMinutes??null},${i.minPartySize},${i.maxPartySize??null},${i.priceCents??null},${i.currency},${i.bookingMode},${i.bookingUrl??null},${i.meetingPointText??null},${i.location?sql`ST_SetSRID(ST_MakePoint(${i.location.longitude},${i.location.latitude}),4326)`:null},${JSON.stringify(i.languages)}::jsonb,${JSON.stringify(i.includes)}::jsonb,${JSON.stringify(i.excludes)}::jsonb,${i.cancellationPolicy??null},${i.coverImageUrl??null},${i.validFrom??null},${i.validUntil??null},${i.status},${i.sortOrder},${userId}::uuid,${userId}::uuid,CASE WHEN ${i.status}='published' THEN now() ELSE NULL END) RETURNING id`.execute(database);
    return reply.code(201).send({experience:{id:row.rows[0]?.id}});
  });
  app.post('/api/v1/my/businesses/:businessId/experiences/:experienceId/slots',async(request,reply)=>{
    const database=requireDatabase(db,reply);if(!database)return;const userId=requireAuthenticatedUser(request,reply);if(!userId)return;
    const params=z.object({businessId:z.string().uuid(),experienceId:z.string().uuid()}).safeParse(request.params);const parsed=slotSchema.safeParse(request.body);if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_slot'});
    const access=await membership(database,params.data.businessId,userId);if(!access||!editableRoles.has(access.role))return reply.code(403).send({error:'business_edit_denied'});
    const owns=await sql<{id:string}>`SELECT id FROM business_experiences WHERE id=${params.data.experienceId}::uuid AND business_id=${params.data.businessId}::uuid LIMIT 1`.execute(database);if(!owns.rows[0])return reply.code(404).send({error:'experience_not_found'});const i=parsed.data;
    const row=await sql<{id:string}>`INSERT INTO business_experience_slots(experience_id,starts_at,ends_at,capacity,price_override_cents,status) VALUES(${params.data.experienceId}::uuid,${i.startsAt},${i.endsAt??null},${i.capacity},${i.priceOverrideCents??null},${i.status}) RETURNING id`.execute(database);return reply.code(201).send({slot:{id:row.rows[0]?.id}});
  });
  app.patch('/api/v1/my/businesses/:businessId/experience-bookings/:id',async(request,reply)=>{
    const database=requireDatabase(db,reply);if(!database)return;const userId=requireAuthenticatedUser(request,reply);if(!userId)return;
    const params=z.object({businessId:z.string().uuid(),id:z.string().uuid()}).safeParse(request.params);const parsed=bookingPatchSchema.safeParse(request.body);if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_booking_status'});
    const access=await membership(database,params.data.businessId,userId);if(!access||!bookingRoles.has(access.role))return reply.code(403).send({error:'booking_management_denied'});
    const owns=await sql<{id:string}>`SELECT eb.id FROM business_experience_bookings eb JOIN business_experiences e ON e.id=eb.experience_id WHERE eb.id=${params.data.id}::uuid AND e.business_id=${params.data.businessId}::uuid LIMIT 1`.execute(database);if(!owns.rows[0])return reply.code(404).send({error:'booking_not_found'});
    const result=await changeBookingStatus(database,params.data.id,parsed.data.status);return bookingStatusReply(result,reply);
  });
}

export function registerBusinessExperienceRoutes(app:FastifyInstance,db:DatabaseClient|null){registerPublicRoutes(app,db);registerAdminRoutes(app,db);registerOwnerRoutes(app,db);}
