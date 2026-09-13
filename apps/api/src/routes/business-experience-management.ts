import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const editableRoles = new Set(['owner', 'manager', 'editor']);
const experienceTypes = ['aove_tasting','mill_visit','guided_tour','workshop','gastronomy','nature','culture','family','wellness','other'] as const;
const experienceStatuses = ['draft','published','archived'] as const;

const experiencePatchSchema = z.object({
  title: z.string().trim().min(2).max(180).optional(),
  summary: z.string().trim().max(400).nullable().optional(),
  description: z.string().trim().max(8000).nullable().optional(),
  experienceType: z.enum(experienceTypes).optional(),
  durationMinutes: z.number().int().min(1).max(10080).nullable().optional(),
  minPartySize: z.number().int().min(1).max(1000).optional(),
  maxPartySize: z.number().int().min(1).max(1000).nullable().optional(),
  priceCents: z.number().int().min(0).nullable().optional(),
  currency: z.string().trim().regex(/^[A-Z]{3}$/).optional(),
  bookingMode: z.enum(['request','external','contact']).optional(),
  bookingUrl: z.string().url().max(2000).nullable().optional(),
  meetingPointText: z.string().trim().max(600).nullable().optional(),
  languages: z.array(z.string().trim().min(2).max(20)).max(20).optional(),
  includes: z.array(z.string().trim().min(1).max(240)).max(30).optional(),
  excludes: z.array(z.string().trim().min(1).max(240)).max(30).optional(),
  cancellationPolicy: z.string().trim().max(3000).nullable().optional(),
  coverImageUrl: z.string().url().max(2000).nullable().optional(),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  status: z.enum(experienceStatuses).optional(),
  sortOrder: z.number().int().min(0).max(10000).optional(),
}).superRefine((value, context) => {
  if (!Object.keys(value).length) context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
});

const slotPatchSchema = z.object({
  startsAt: z.coerce.date().optional(),
  endsAt: z.coerce.date().nullable().optional(),
  capacity: z.number().int().min(1).max(10000).optional(),
  priceOverrideCents: z.number().int().min(0).nullable().optional(),
  status: z.enum(['open','full','cancelled','hidden']).optional(),
}).superRefine((value, context) => {
  if (!Object.keys(value).length) context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
});

type ExperienceRow = {
  id: string;
  business_id: string;
  min_party_size: number;
  max_party_size: number | null;
  booking_mode: 'request'|'external'|'contact';
  booking_url: string | null;
  valid_from: Date | null;
  valid_until: Date | null;
  status: 'draft'|'published'|'archived';
};

async function membership(database: DatabaseClient, businessId: string, userId: string) {
  const result = await sql<{ role: 'owner'|'manager'|'editor'|'analyst' }>`
    SELECT role FROM business_memberships
    WHERE business_id=${businessId}::uuid AND user_id=${userId}::uuid AND status='active'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function readExperience(database: DatabaseClient, id: string) {
  const result = await sql<ExperienceRow>`
    SELECT id,business_id,min_party_size,max_party_size,booking_mode,booking_url,valid_from,valid_until,status
    FROM business_experiences WHERE id=${id}::uuid LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

function validateMergedExperience(current: ExperienceRow, patch: z.infer<typeof experiencePatchSchema>, reply: FastifyReply) {
  const min = patch.minPartySize ?? current.min_party_size;
  const max = patch.maxPartySize !== undefined ? patch.maxPartySize : current.max_party_size;
  if (max !== null && max < min) {
    void reply.code(400).send({ error: 'max_party_below_min_party' });
    return false;
  }
  const mode = patch.bookingMode ?? current.booking_mode;
  const url = patch.bookingUrl !== undefined ? patch.bookingUrl : current.booking_url;
  if (mode === 'external' && !url) {
    void reply.code(400).send({ error: 'booking_url_required_for_external_mode' });
    return false;
  }
  const from = patch.validFrom !== undefined ? patch.validFrom : current.valid_from;
  const until = patch.validUntil !== undefined ? patch.validUntil : current.valid_until;
  if (from && until && until < from) {
    void reply.code(400).send({ error: 'experience_valid_until_before_start' });
    return false;
  }
  return true;
}

async function updateExperience(database: DatabaseClient, id: string, userId: string, patch: z.infer<typeof experiencePatchSchema>) {
  const current = await readExperience(database, id);
  if (!current) return null;
  await sql`
    UPDATE business_experiences SET
      title=COALESCE(${patch.title ?? null},title),
      summary=CASE WHEN ${patch.summary === undefined} THEN summary ELSE ${patch.summary ?? null} END,
      description=CASE WHEN ${patch.description === undefined} THEN description ELSE ${patch.description ?? null} END,
      experience_type=COALESCE(${patch.experienceType ?? null},experience_type),
      duration_minutes=CASE WHEN ${patch.durationMinutes === undefined} THEN duration_minutes ELSE ${patch.durationMinutes ?? null} END,
      min_party_size=COALESCE(${patch.minPartySize ?? null},min_party_size),
      max_party_size=CASE WHEN ${patch.maxPartySize === undefined} THEN max_party_size ELSE ${patch.maxPartySize ?? null} END,
      price_cents=CASE WHEN ${patch.priceCents === undefined} THEN price_cents ELSE ${patch.priceCents ?? null} END,
      currency=COALESCE(${patch.currency ?? null},currency),
      booking_mode=COALESCE(${patch.bookingMode ?? null},booking_mode),
      booking_url=CASE WHEN ${patch.bookingUrl === undefined} THEN booking_url ELSE ${patch.bookingUrl ?? null} END,
      meeting_point_text=CASE WHEN ${patch.meetingPointText === undefined} THEN meeting_point_text ELSE ${patch.meetingPointText ?? null} END,
      languages=CASE WHEN ${patch.languages === undefined} THEN languages ELSE ${patch.languages === undefined ? null : JSON.stringify(patch.languages)}::jsonb END,
      includes=CASE WHEN ${patch.includes === undefined} THEN includes ELSE ${patch.includes === undefined ? null : JSON.stringify(patch.includes)}::jsonb END,
      excludes=CASE WHEN ${patch.excludes === undefined} THEN excludes ELSE ${patch.excludes === undefined ? null : JSON.stringify(patch.excludes)}::jsonb END,
      cancellation_policy=CASE WHEN ${patch.cancellationPolicy === undefined} THEN cancellation_policy ELSE ${patch.cancellationPolicy ?? null} END,
      cover_image_url=CASE WHEN ${patch.coverImageUrl === undefined} THEN cover_image_url ELSE ${patch.coverImageUrl ?? null} END,
      valid_from=CASE WHEN ${patch.validFrom === undefined} THEN valid_from ELSE ${patch.validFrom ?? null} END,
      valid_until=CASE WHEN ${patch.validUntil === undefined} THEN valid_until ELSE ${patch.validUntil ?? null} END,
      status=COALESCE(${patch.status ?? null},status),
      sort_order=COALESCE(${patch.sortOrder ?? null},sort_order),
      published_at=CASE WHEN ${patch.status ?? null}='published' AND published_at IS NULL THEN now() ELSE published_at END,
      updated_by=${userId}::uuid, updated_at=now()
    WHERE id=${id}::uuid
  `.execute(database);
  return current;
}

async function updateSlot(database: DatabaseClient, id: string, patch: z.infer<typeof slotPatchSchema>) {
  return database.transaction().execute(async (trx) => {
    const result = await sql<{ id:string; experience_id:string; starts_at:Date; ends_at:Date|null; capacity:number; confirmed_count:number; status:string }>`
      SELECT id,experience_id,starts_at,ends_at,capacity,confirmed_count,status
      FROM business_experience_slots WHERE id=${id}::uuid FOR UPDATE
    `.execute(trx);
    const current = result.rows[0];
    if (!current) return { error:'experience_slot_not_found' as const };
    const capacity = patch.capacity ?? current.capacity;
    if (capacity < current.confirmed_count) return { error:'capacity_below_confirmed_count' as const, confirmedCount:current.confirmed_count };
    const start = patch.startsAt ?? current.starts_at;
    const end = patch.endsAt !== undefined ? patch.endsAt : current.ends_at;
    if (end && end <= start) return { error:'slot_end_before_start' as const };
    const status = patch.status ?? current.status;
    if (status === 'cancelled' && current.confirmed_count > 0) return { error:'confirmed_bookings_must_be_resolved_before_cancelling_slot' as const, confirmedCount:current.confirmed_count };
    await sql`
      UPDATE business_experience_slots SET
        starts_at=COALESCE(${patch.startsAt ?? null},starts_at),
        ends_at=CASE WHEN ${patch.endsAt === undefined} THEN ends_at ELSE ${patch.endsAt ?? null} END,
        capacity=COALESCE(${patch.capacity ?? null},capacity),
        price_override_cents=CASE WHEN ${patch.priceOverrideCents === undefined} THEN price_override_cents ELSE ${patch.priceOverrideCents ?? null} END,
        status=CASE
          WHEN ${status}='open' AND ${capacity}<=confirmed_count THEN 'full'
          WHEN ${status}='full' AND ${capacity}>confirmed_count THEN 'open'
          ELSE ${status}
        END,
        updated_at=now()
      WHERE id=${id}::uuid
    `.execute(trx);
    return { ok:true as const, experienceId:current.experience_id };
  });
}

function slotReply(result: Awaited<ReturnType<typeof updateSlot>>, reply: FastifyReply) {
  if ('error' in result) {
    if (result.error === 'experience_slot_not_found') return reply.code(404).send({ error:result.error });
    return reply.code(409).send(result);
  }
  return reply.send({ ok:true });
}

export function registerBusinessExperienceManagementRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.patch('/api/v1/admin/business-experiences/:id', async (request, reply) => {
    const auth=await requirePlatformAccess(request,reply,db,'editor'); if(!auth)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params); const parsed=experiencePatchSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_patch',issues:parsed.success?undefined:parsed.error.issues});
    const current=await readExperience(auth.database,params.data.id); if(!current)return reply.code(404).send({error:'experience_not_found'});
    if(!validateMergedExperience(current,parsed.data,reply))return;
    await updateExperience(auth.database,params.data.id,auth.access.userId,parsed.data);
    await auditAdminAction(auth.database,auth.access,'business.experience_updated','business_experience',params.data.id,{status:parsed.data.status??current.status});
    return {ok:true};
  });

  app.patch('/api/v1/admin/business-experience-slots/:id', async (request, reply) => {
    const auth=await requirePlatformAccess(request,reply,db,'editor'); if(!auth)return;
    const params=z.object({id:z.string().uuid()}).safeParse(request.params); const parsed=slotPatchSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_slot_patch'});
    const result=await updateSlot(auth.database,params.data.id,parsed.data);
    if('ok' in result)await auditAdminAction(auth.database,auth.access,'business.experience_slot_updated','business_experience_slot',params.data.id,{status:parsed.data.status??null,capacity:parsed.data.capacity??null});
    return slotReply(result,reply);
  });

  app.patch('/api/v1/my/businesses/:businessId/experiences/:id', async (request, reply) => {
    const database=requireDatabase(db,reply); if(!database)return; const userId=requireAuthenticatedUser(request,reply); if(!userId)return;
    const params=z.object({businessId:z.string().uuid(),id:z.string().uuid()}).safeParse(request.params); const parsed=experiencePatchSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_patch'});
    const access=await membership(database,params.data.businessId,userId); if(!access||!editableRoles.has(access.role))return reply.code(403).send({error:'business_edit_denied'});
    const current=await readExperience(database,params.data.id); if(!current||current.business_id!==params.data.businessId)return reply.code(404).send({error:'experience_not_found'});
    if(!validateMergedExperience(current,parsed.data,reply))return;
    await updateExperience(database,params.data.id,userId,parsed.data); return {ok:true};
  });

  app.patch('/api/v1/my/businesses/:businessId/experience-slots/:id', async (request, reply) => {
    const database=requireDatabase(db,reply); if(!database)return; const userId=requireAuthenticatedUser(request,reply); if(!userId)return;
    const params=z.object({businessId:z.string().uuid(),id:z.string().uuid()}).safeParse(request.params); const parsed=slotPatchSchema.safeParse(request.body);
    if(!params.success||!parsed.success)return reply.code(400).send({error:'invalid_experience_slot_patch'});
    const access=await membership(database,params.data.businessId,userId); if(!access||!editableRoles.has(access.role))return reply.code(403).send({error:'business_edit_denied'});
    const owns=await sql<{id:string}>`SELECT s.id FROM business_experience_slots s JOIN business_experiences e ON e.id=s.experience_id WHERE s.id=${params.data.id}::uuid AND e.business_id=${params.data.businessId}::uuid LIMIT 1`.execute(database);
    if(!owns.rows[0])return reply.code(404).send({error:'experience_slot_not_found'});
    return slotReply(await updateSlot(database,params.data.id,parsed.data),reply);
  });
}
