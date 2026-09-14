import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const offerCoreSchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  title: z.string().trim().min(2).max(180),
  summary: z.string().trim().max(400).nullable().optional(),
  description: z.string().trim().max(5_000).nullable().optional(),
  offerType: z.enum(['promotion', 'discount', 'fixed_price', 'bundle', 'gift', 'experience', 'seasonal']).optional(),
  originalPriceCents: z.number().int().min(0).nullable().optional(),
  offerPriceCents: z.number().int().min(0).nullable().optional(),
  currency: z.string().trim().regex(/^[A-Z]{3}$/).optional(),
  promoCode: z.string().trim().max(80).nullable().optional(),
  redemptionMode: z.enum(['contact', 'request', 'external_link', 'show_code']).optional(),
  redemptionUrl: z.string().url().max(2_000).nullable().optional(),
  terms: z.string().trim().max(2_000).nullable().optional(),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  maxRedemptions: z.number().int().min(1).nullable().optional(),
  status: z.enum(['draft', 'published', 'archived']).optional(),
  sortOrder: z.number().int().min(0).max(10_000).optional(),
});

function validateOfferDatesAndPrices(value: z.infer<typeof offerCoreSchema>, context: z.RefinementCtx) {
  if (value.validFrom && value.validUntil && value.validUntil < value.validFrom) {
    context.addIssue({ code: 'custom', path: ['validUntil'], message: 'offer_valid_until_before_start' });
  }
  if (value.offerPriceCents !== null && value.offerPriceCents !== undefined
    && value.originalPriceCents !== null && value.originalPriceCents !== undefined
    && value.offerPriceCents > value.originalPriceCents) {
    context.addIssue({ code: 'custom', path: ['offerPriceCents'], message: 'offer_price_above_original_price' });
  }
}

const offerCreateSchema = offerCoreSchema.superRefine(validateOfferDatesAndPrices);
const offerPatchSchema = offerCoreSchema.partial().superRefine((value, context) => {
  if (Object.keys(value).length === 0) context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
  validateOfferDatesAndPrices(value as z.infer<typeof offerCoreSchema>, context);
});

const leadPatchSchema = z.object({
  status: z.enum(['new', 'contacted', 'qualified', 'won', 'lost', 'spam']),
  estimatedValueCents: z.number().int().min(0).nullable().optional(),
  actualValueCents: z.number().int().min(0).nullable().optional(),
});

const dashboardQuerySchema = z.object({
  businessId: z.string().uuid().optional(),
  days: z.coerce.number().int().min(1).max(365).default(30),
});

type BusinessMetricRow = {
  id: string;
  slug: string;
  name: string;
  commercial_plan: string;
  profile_views: number;
  contact_clicks: number;
  leads: number;
  won_leads: number;
  won_value_cents: number;
  active_offers: number;
};

type LeadRow = {
  id: string;
  business_id: string;
  business_name: string;
  offer_id: string | null;
  offer_title: string | null;
  kind: string;
  contact_name: string;
  contact_email: string | null;
  contact_phone: string | null;
  message: string | null;
  requested_for: Date | null;
  party_size: number | null;
  source_context: string | null;
  source_key: string | null;
  status: string;
  estimated_value_cents: number | null;
  actual_value_cents: number | null;
  currency: string;
  created_at: Date;
  updated_at: Date;
};

type OfferAdminRow = {
  id: string;
  business_id: string;
  business_name: string;
  slug: string;
  title: string;
  summary: string | null;
  description: string | null;
  offer_type: string;
  original_price_cents: number | null;
  offer_price_cents: number | null;
  currency: string;
  promo_code: string | null;
  redemption_mode: string;
  redemption_url: string | null;
  terms: string | null;
  valid_from: Date | null;
  valid_until: Date | null;
  max_redemptions: number | null;
  status: string;
  sort_order: number;
  published_at: Date | null;
  created_at: Date;
  updated_at: Date;
};

export function registerAdminBusinessRevenueRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/business-revenue', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = dashboardQuerySchema.safeParse(request.query ?? {});
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_revenue_filters', issues: parsed.error.issues });
    const businessId = parsed.data.businessId ?? null;
    const since = new Date(Date.now() - parsed.data.days * 86_400_000);

    const [metrics, leads, offers] = await Promise.all([
      sql<BusinessMetricRow>`
        SELECT b.id, b.slug, b.name, b.commercial_plan,
          (SELECT count(*)::int FROM business_events e
            WHERE e.business_id = b.id AND e.event_type = 'profile_view' AND e.occurred_at >= ${since}) AS profile_views,
          (SELECT count(*)::int FROM business_events e
            WHERE e.business_id = b.id
              AND e.event_type IN ('phone_click','whatsapp_click','email_click','website_click','directions_click')
              AND e.occurred_at >= ${since}) AS contact_clicks,
          (SELECT count(*)::int FROM business_leads l
            WHERE l.business_id = b.id AND l.created_at >= ${since} AND l.status <> 'spam') AS leads,
          (SELECT count(*)::int FROM business_leads l
            WHERE l.business_id = b.id AND l.created_at >= ${since} AND l.status = 'won') AS won_leads,
          COALESCE((SELECT sum(l.actual_value_cents)::bigint FROM business_leads l
            WHERE l.business_id = b.id AND l.created_at >= ${since} AND l.status = 'won'), 0)::bigint AS won_value_cents,
          (SELECT count(*)::int FROM business_offers o
            WHERE o.business_id = b.id AND o.status = 'published'
              AND (o.valid_from IS NULL OR o.valid_from <= now())
              AND (o.valid_until IS NULL OR o.valid_until >= now())) AS active_offers
        FROM businesses b
        WHERE (${businessId}::uuid IS NULL OR b.id = ${businessId}::uuid)
        ORDER BY leads DESC, contact_clicks DESC, profile_views DESC, b.name
      `.execute(auth.database),
      sql<LeadRow>`
        SELECT l.id, l.business_id, b.name AS business_name,
               l.offer_id, o.title AS offer_title, l.kind, l.contact_name, l.contact_email, l.contact_phone,
               l.message, l.requested_for, l.party_size, l.source_context, l.source_key,
               l.status, l.estimated_value_cents, l.actual_value_cents, l.currency,
               l.created_at, l.updated_at
        FROM business_leads l
        JOIN businesses b ON b.id = l.business_id
        LEFT JOIN business_offers o ON o.id = l.offer_id
        WHERE (${businessId}::uuid IS NULL OR l.business_id = ${businessId}::uuid)
          AND l.created_at >= ${since}
        ORDER BY CASE l.status WHEN 'new' THEN 0 WHEN 'qualified' THEN 1 WHEN 'contacted' THEN 2 ELSE 3 END,
                 l.created_at DESC
        LIMIT 300
      `.execute(auth.database),
      sql<OfferAdminRow>`
        SELECT o.id, o.business_id, b.name AS business_name, o.slug, o.title, o.summary, o.description,
               o.offer_type, o.original_price_cents, o.offer_price_cents, o.currency, o.promo_code,
               o.redemption_mode, o.redemption_url, o.terms, o.valid_from, o.valid_until,
               o.max_redemptions, o.status, o.sort_order, o.published_at, o.created_at, o.updated_at
        FROM business_offers o
        JOIN businesses b ON b.id = o.business_id
        WHERE (${businessId}::uuid IS NULL OR o.business_id = ${businessId}::uuid)
        ORDER BY CASE o.status WHEN 'published' THEN 0 WHEN 'draft' THEN 1 ELSE 2 END,
                 o.sort_order, o.updated_at DESC
        LIMIT 300
      `.execute(auth.database),
    ]);

    return {
      periodDays: parsed.data.days,
      since,
      businesses: metrics.rows.map((row) => ({
        id: row.id,
        slug: row.slug,
        name: row.name,
        commercialPlan: row.commercial_plan,
        profileViews: Number(row.profile_views),
        contactClicks: Number(row.contact_clicks),
        leads: Number(row.leads),
        wonLeads: Number(row.won_leads),
        wonValueCents: Number(row.won_value_cents),
        activeOffers: Number(row.active_offers),
      })),
      leads: leads.rows,
      offers: offers.rows,
    };
  });

  app.post('/api/v1/admin/businesses/:id/offers', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });
    const parsed = offerCreateSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_offer', issues: parsed.error.issues });
    const input = parsed.data;

    const target = await sql<{ id: string }>`SELECT id FROM businesses WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    if (!target.rows[0]) return reply.code(404).send({ error: 'business_not_found' });

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_offers (
        business_id, slug, title, summary, description, offer_type,
        original_price_cents, offer_price_cents, currency, promo_code,
        redemption_mode, redemption_url, terms, valid_from, valid_until,
        max_redemptions, status, sort_order, created_by, updated_by, published_at
      ) VALUES (
        ${params.data.id}::uuid, ${input.slug}, ${input.title}, ${input.summary ?? null}, ${input.description ?? null},
        ${input.offerType ?? 'promotion'}, ${input.originalPriceCents ?? null}, ${input.offerPriceCents ?? null},
        ${input.currency ?? 'EUR'}, ${input.promoCode ?? null}, ${input.redemptionMode ?? 'contact'},
        ${input.redemptionUrl ?? null}, ${input.terms ?? null}, ${input.validFrom ?? null}, ${input.validUntil ?? null},
        ${input.maxRedemptions ?? null}, ${input.status ?? 'draft'}, ${input.sortOrder ?? 0},
        ${auth.access.userId}::uuid, ${auth.access.userId}::uuid,
        CASE WHEN ${input.status ?? 'draft'} = 'published' THEN now() ELSE NULL END
      ) RETURNING id
    `.execute(auth.database);
    const id = inserted.rows[0]?.id;
    if (!id) return reply.code(500).send({ error: 'business_offer_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'business.offer_created', 'business_offer', id, { businessId: params.data.id, title: input.title });
    return reply.code(201).send({ offer: { id } });
  });

  app.patch('/api/v1/admin/business-offers/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_offer_id' });
    const parsed = offerPatchSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_offer_patch', issues: parsed.error.issues });
    const input = parsed.data;

    const current = await sql<{ id: string; valid_from: Date | null; valid_until: Date | null; original_price_cents: number | null; offer_price_cents: number | null }>`
      SELECT id, valid_from, valid_until, original_price_cents, offer_price_cents
      FROM business_offers WHERE id = ${params.data.id}::uuid LIMIT 1
    `.execute(auth.database);
    const row = current.rows[0];
    if (!row) return reply.code(404).send({ error: 'business_offer_not_found' });
    const nextFrom = input.validFrom === undefined ? row.valid_from : input.validFrom;
    const nextUntil = input.validUntil === undefined ? row.valid_until : input.validUntil;
    const nextOriginal = input.originalPriceCents === undefined ? row.original_price_cents : input.originalPriceCents;
    const nextOffer = input.offerPriceCents === undefined ? row.offer_price_cents : input.offerPriceCents;
    if (nextFrom && nextUntil && nextUntil < nextFrom) return reply.code(400).send({ error: 'offer_valid_until_before_start' });
    if (nextOffer !== null && nextOriginal !== null && nextOffer > nextOriginal) return reply.code(400).send({ error: 'offer_price_above_original_price' });

    await sql`
      UPDATE business_offers SET
        slug = CASE WHEN ${input.slug !== undefined} THEN ${input.slug ?? ''} ELSE slug END,
        title = CASE WHEN ${input.title !== undefined} THEN ${input.title ?? ''} ELSE title END,
        summary = CASE WHEN ${input.summary !== undefined} THEN ${input.summary ?? null} ELSE summary END,
        description = CASE WHEN ${input.description !== undefined} THEN ${input.description ?? null} ELSE description END,
        offer_type = CASE WHEN ${input.offerType !== undefined} THEN ${input.offerType ?? 'promotion'} ELSE offer_type END,
        original_price_cents = ${nextOriginal}, offer_price_cents = ${nextOffer},
        currency = CASE WHEN ${input.currency !== undefined} THEN ${input.currency ?? 'EUR'} ELSE currency END,
        promo_code = CASE WHEN ${input.promoCode !== undefined} THEN ${input.promoCode ?? null} ELSE promo_code END,
        redemption_mode = CASE WHEN ${input.redemptionMode !== undefined} THEN ${input.redemptionMode ?? 'contact'} ELSE redemption_mode END,
        redemption_url = CASE WHEN ${input.redemptionUrl !== undefined} THEN ${input.redemptionUrl ?? null} ELSE redemption_url END,
        terms = CASE WHEN ${input.terms !== undefined} THEN ${input.terms ?? null} ELSE terms END,
        valid_from = ${nextFrom}, valid_until = ${nextUntil},
        max_redemptions = CASE WHEN ${input.maxRedemptions !== undefined} THEN ${input.maxRedemptions ?? null} ELSE max_redemptions END,
        status = CASE WHEN ${input.status !== undefined} THEN ${input.status ?? 'draft'} ELSE status END,
        sort_order = CASE WHEN ${input.sortOrder !== undefined} THEN ${input.sortOrder ?? 0} ELSE sort_order END,
        published_at = CASE
          WHEN ${input.status === 'published'} AND published_at IS NULL THEN now()
          WHEN ${input.status === 'draft'} THEN NULL
          ELSE published_at END,
        updated_by = ${auth.access.userId}::uuid, updated_at = now()
      WHERE id = ${params.data.id}::uuid
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'business.offer_updated', 'business_offer', params.data.id, { fields: Object.keys(input) });
    return { ok: true, offerId: params.data.id };
  });

  app.patch('/api/v1/admin/business-leads/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_lead_id' });
    const parsed = leadPatchSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_lead_patch', issues: parsed.error.issues });
    const exists = await sql<{ id: string }>`SELECT id FROM business_leads WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    if (!exists.rows[0]) return reply.code(404).send({ error: 'business_lead_not_found' });

    await sql`
      UPDATE business_leads SET
        status = ${parsed.data.status},
        estimated_value_cents = CASE WHEN ${parsed.data.estimatedValueCents !== undefined} THEN ${parsed.data.estimatedValueCents ?? null} ELSE estimated_value_cents END,
        actual_value_cents = CASE WHEN ${parsed.data.actualValueCents !== undefined} THEN ${parsed.data.actualValueCents ?? null} ELSE actual_value_cents END,
        resolved_at = CASE WHEN ${parsed.data.status} IN ('won','lost','spam') THEN now() ELSE NULL END,
        updated_at = now()
      WHERE id = ${params.data.id}::uuid
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'business.lead_updated', 'business_lead', params.data.id, parsed.data);
    return { ok: true, leadId: params.data.id, status: parsed.data.status };
  });
}
