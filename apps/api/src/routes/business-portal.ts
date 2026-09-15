import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const editableRoles = new Set(['owner', 'manager', 'editor']);
const leadRoles = new Set(['owner', 'manager']);

const claimSchema = z.object({
  claimantPhone: z.string().trim().max(40).optional(),
  relationship: z.string().trim().min(2).max(120),
  message: z.string().trim().max(1_500).optional(),
});

const profilePatchSchema = z.object({
  shortDescription: z.string().trim().max(320).nullable().optional(),
  description: z.string().trim().max(8_000).nullable().optional(),
  address: z.string().trim().max(400).nullable().optional(),
  phone: z.string().trim().max(40).nullable().optional(),
  whatsapp: z.string().trim().max(40).nullable().optional(),
  email: z.union([z.string().trim().email().max(254), z.literal(''), z.null()]).optional(),
  website: z.union([z.string().url().max(2_000), z.literal(''), z.null()]).optional(),
  socialLinks: z.record(z.string(), z.string().max(2_000)).optional(),
  openingHours: z.record(z.string(), z.unknown()).optional(),
  logoUrl: z.union([z.string().url().max(2_000), z.literal(''), z.null()]).optional(),
  coverImageUrl: z.union([z.string().url().max(2_000), z.literal(''), z.null()]).optional(),
}).superRefine((value, context) => {
  if (Object.keys(value).length === 0) context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
});

const offerSchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  title: z.string().trim().min(2).max(180),
  summary: z.string().trim().max(400).nullable().optional(),
  description: z.string().trim().max(5_000).nullable().optional(),
  offerType: z.enum(['promotion', 'discount', 'fixed_price', 'bundle', 'gift', 'experience', 'seasonal']).default('promotion'),
  originalPriceCents: z.number().int().min(0).nullable().optional(),
  offerPriceCents: z.number().int().min(0).nullable().optional(),
  currency: z.string().trim().regex(/^[A-Z]{3}$/).default('EUR'),
  promoCode: z.string().trim().max(80).nullable().optional(),
  redemptionMode: z.enum(['contact', 'request', 'external_link', 'show_code']).default('request'),
  redemptionUrl: z.string().url().max(2_000).nullable().optional(),
  terms: z.string().trim().max(2_000).nullable().optional(),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  maxRedemptions: z.number().int().min(1).nullable().optional(),
  status: z.enum(['draft', 'published', 'archived']).default('draft'),
}).superRefine((value, context) => {
  if (value.validFrom && value.validUntil && value.validUntil < value.validFrom) {
    context.addIssue({ code: 'custom', path: ['validUntil'], message: 'offer_valid_until_before_start' });
  }
  if (value.offerPriceCents !== null && value.offerPriceCents !== undefined
    && value.originalPriceCents !== null && value.originalPriceCents !== undefined
    && value.offerPriceCents > value.originalPriceCents) {
    context.addIssue({ code: 'custom', path: ['offerPriceCents'], message: 'offer_price_above_original_price' });
  }
});

const leadPatchSchema = z.object({
  status: z.enum(['contacted', 'qualified', 'won', 'lost', 'spam']),
  estimatedValueCents: z.number().int().min(0).nullable().optional(),
  actualValueCents: z.number().int().min(0).nullable().optional(),
});

async function membership(database: DatabaseClient, businessId: string, userId: string) {
  const result = await sql<{ role: 'owner' | 'manager' | 'editor' | 'analyst' }>`
    SELECT role
    FROM business_memberships
    WHERE business_id = ${businessId}::uuid
      AND user_id = ${userId}::uuid
      AND status = 'active'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

export function registerBusinessPortalRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/my/businesses', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const result = await sql<{
      id: string; slug: string; name: string; short_description: string | null; status: string;
      verification_status: string; commercial_plan: string; role: string;
      municipality_name: string | null; place_name: string | null; logo_url: string | null;
    }>`
      SELECT b.id, b.slug, b.name, b.short_description, b.status,
             b.verification_status, b.commercial_plan, bm.role,
             m.name AS municipality_name, p.name AS place_name, b.logo_url
      FROM business_memberships bm
      JOIN businesses b ON b.id = bm.business_id
      LEFT JOIN territory_municipalities m ON m.id = b.municipality_id
      LEFT JOIN territory_places p ON p.id = b.place_id
      WHERE bm.user_id = ${userId}::uuid AND bm.status = 'active'
      ORDER BY b.name
    `.execute(database);

    return { businesses: result.rows };
  });

  app.post('/api/v1/my/businesses/:slug/claims', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });
    const parsed = claimSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_claim', issues: parsed.error.issues });

    const [businessResult, userResult] = await Promise.all([
      sql<{ id: string }>`SELECT id FROM businesses WHERE slug = ${params.data.slug} AND status = 'published' LIMIT 1`.execute(database),
      sql<{ display_name: string | null; primary_email: string }>`SELECT display_name, primary_email FROM users WHERE id = ${userId}::uuid LIMIT 1`.execute(database),
    ]);
    const business = businessResult.rows[0];
    const user = userResult.rows[0];
    if (!business) return reply.code(404).send({ error: 'business_not_found' });
    if (!user) return reply.code(401).send({ error: 'authenticated_user_not_found' });

    const existing = await sql<{ id: string }>`
      SELECT id FROM business_claims
      WHERE business_id = ${business.id}::uuid AND claimant_user_id = ${userId}::uuid
        AND status IN ('pending', 'needs_info', 'approved')
      LIMIT 1
    `.execute(database);
    if (existing.rows[0]) return reply.code(409).send({ error: 'business_claim_already_exists' });

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_claims (
        business_id, claimant_user_id, claimant_name, claimant_email, claimant_phone, evidence
      ) VALUES (
        ${business.id}::uuid, ${userId}::uuid, ${user.display_name || user.primary_email},
        ${user.primary_email.toLowerCase()}, ${parsed.data.claimantPhone ?? null},
        ${JSON.stringify({ relationship: parsed.data.relationship, message: parsed.data.message ?? null })}::jsonb
      ) RETURNING id
    `.execute(database);
    return reply.code(201).send({ claim: { id: inserted.rows[0]?.id, status: 'pending' } });
  });

  app.get('/api/v1/my/businesses/:id/dashboard', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const query = z.object({ days: z.coerce.number().int().min(1).max(365).default(30) }).safeParse(request.query ?? {});
    if (!params.success || !query.success) return reply.code(400).send({ error: 'invalid_business_dashboard_request' });
    const access = await membership(database, params.data.id, userId);
    if (!access) return reply.code(403).send({ error: 'business_access_denied' });
    const since = new Date(Date.now() - query.data.days * 86_400_000);

    const [business, metrics, leads, offers] = await Promise.all([
      sql<{
        id: string; slug: string; name: string; short_description: string | null; description: string | null;
        address: string | null; phone: string | null; whatsapp: string | null; email: string | null;
        website: string | null; social_links: unknown; opening_hours: unknown; logo_url: string | null;
        cover_image_url: string | null; commercial_plan: string; verification_status: string; status: string;
      }>`
        SELECT id, slug, name, short_description, description, address, phone, whatsapp, email, website,
               social_links, opening_hours, logo_url, cover_image_url, commercial_plan, verification_status, status
        FROM businesses WHERE id = ${params.data.id}::uuid LIMIT 1
      `.execute(database),
      sql<{ profile_views: number; contact_clicks: number; leads: number; won_leads: number; won_value_cents: number }>`
        SELECT
          (SELECT count(*)::int FROM business_events e WHERE e.business_id = ${params.data.id}::uuid AND e.event_type='profile_view' AND e.occurred_at >= ${since}) AS profile_views,
          (SELECT count(*)::int FROM business_events e WHERE e.business_id = ${params.data.id}::uuid AND e.event_type IN ('phone_click','whatsapp_click','email_click','website_click','directions_click') AND e.occurred_at >= ${since}) AS contact_clicks,
          (SELECT count(*)::int FROM business_leads l WHERE l.business_id = ${params.data.id}::uuid AND l.status <> 'spam' AND l.created_at >= ${since}) AS leads,
          (SELECT count(*)::int FROM business_leads l WHERE l.business_id = ${params.data.id}::uuid AND l.status='won' AND l.created_at >= ${since}) AS won_leads,
          COALESCE((SELECT sum(l.actual_value_cents)::bigint FROM business_leads l WHERE l.business_id = ${params.data.id}::uuid AND l.status='won' AND l.created_at >= ${since}),0)::bigint AS won_value_cents
      `.execute(database),
      sql<{
        id: string; kind: string; contact_name: string; contact_email: string | null; contact_phone: string | null;
        message: string | null; requested_for: Date | null; party_size: number | null; status: string;
        actual_value_cents: number | null; currency: string; created_at: Date; offer_title: string | null;
      }>`
        SELECT l.id, l.kind, l.contact_name, l.contact_email, l.contact_phone, l.message,
               l.requested_for, l.party_size, l.status, l.actual_value_cents, l.currency, l.created_at,
               o.title AS offer_title
        FROM business_leads l LEFT JOIN business_offers o ON o.id = l.offer_id
        WHERE l.business_id = ${params.data.id}::uuid AND l.created_at >= ${since}
        ORDER BY CASE l.status WHEN 'new' THEN 0 WHEN 'qualified' THEN 1 WHEN 'contacted' THEN 2 ELSE 3 END, l.created_at DESC
        LIMIT 200
      `.execute(database),
      sql<{
        id: string; slug: string; title: string; summary: string | null; offer_type: string;
        offer_price_cents: number | null; currency: string; status: string; valid_until: Date | null;
      }>`
        SELECT id, slug, title, summary, offer_type, offer_price_cents, currency, status, valid_until
        FROM business_offers WHERE business_id = ${params.data.id}::uuid
        ORDER BY CASE status WHEN 'published' THEN 0 WHEN 'draft' THEN 1 ELSE 2 END, updated_at DESC
        LIMIT 100
      `.execute(database),
    ]);

    const row = business.rows[0];
    if (!row) return reply.code(404).send({ error: 'business_not_found' });
    const metric = metrics.rows[0] ?? { profile_views: 0, contact_clicks: 0, leads: 0, won_leads: 0, won_value_cents: 0 };
    return {
      role: access.role,
      periodDays: query.data.days,
      business: {
        id: row.id, slug: row.slug, name: row.name, shortDescription: row.short_description,
        description: row.description, address: row.address, phone: row.phone, whatsapp: row.whatsapp,
        email: row.email, website: row.website, socialLinks: row.social_links ?? {}, openingHours: row.opening_hours ?? {},
        logoUrl: row.logo_url, coverImageUrl: row.cover_image_url, commercialPlan: row.commercial_plan,
        verificationStatus: row.verification_status, status: row.status,
      },
      metrics: {
        profileViews: Number(metric.profile_views), contactClicks: Number(metric.contact_clicks), leads: Number(metric.leads),
        wonLeads: Number(metric.won_leads), wonValueCents: Number(metric.won_value_cents),
      },
      leads: leads.rows,
      offers: offers.rows,
    };
  });

  app.patch('/api/v1/my/businesses/:id/profile', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const parsed = profilePatchSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_business_profile_patch' });
    const access = await membership(database, params.data.id, userId);
    if (!access || !editableRoles.has(access.role)) return reply.code(403).send({ error: 'business_edit_denied' });
    const input = parsed.data;

    await sql`
      UPDATE businesses SET
        short_description = CASE WHEN ${input.shortDescription !== undefined} THEN ${input.shortDescription ?? null} ELSE short_description END,
        description = CASE WHEN ${input.description !== undefined} THEN ${input.description ?? null} ELSE description END,
        address = CASE WHEN ${input.address !== undefined} THEN ${input.address ?? null} ELSE address END,
        phone = CASE WHEN ${input.phone !== undefined} THEN ${input.phone ?? null} ELSE phone END,
        whatsapp = CASE WHEN ${input.whatsapp !== undefined} THEN ${input.whatsapp ?? null} ELSE whatsapp END,
        email = CASE WHEN ${input.email !== undefined} THEN ${input.email || null} ELSE email END,
        website = CASE WHEN ${input.website !== undefined} THEN ${input.website || null} ELSE website END,
        social_links = CASE WHEN ${input.socialLinks !== undefined} THEN ${JSON.stringify(input.socialLinks ?? {})}::jsonb ELSE social_links END,
        opening_hours = CASE WHEN ${input.openingHours !== undefined} THEN ${JSON.stringify(input.openingHours ?? {})}::jsonb ELSE opening_hours END,
        logo_url = CASE WHEN ${input.logoUrl !== undefined} THEN ${input.logoUrl || null} ELSE logo_url END,
        cover_image_url = CASE WHEN ${input.coverImageUrl !== undefined} THEN ${input.coverImageUrl || null} ELSE cover_image_url END,
        updated_by = ${userId}::uuid, updated_at = now()
      WHERE id = ${params.data.id}::uuid
    `.execute(database);
    return { ok: true };
  });

  app.post('/api/v1/my/businesses/:id/offers', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const parsed = offerSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_business_offer' });
    const access = await membership(database, params.data.id, userId);
    if (!access || !editableRoles.has(access.role)) return reply.code(403).send({ error: 'business_edit_denied' });
    const input = parsed.data;

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_offers (
        business_id, slug, title, summary, description, offer_type, original_price_cents, offer_price_cents,
        currency, promo_code, redemption_mode, redemption_url, terms, valid_from, valid_until,
        max_redemptions, status, created_by, updated_by, published_at
      ) VALUES (
        ${params.data.id}::uuid, ${input.slug}, ${input.title}, ${input.summary ?? null}, ${input.description ?? null},
        ${input.offerType}, ${input.originalPriceCents ?? null}, ${input.offerPriceCents ?? null}, ${input.currency},
        ${input.promoCode ?? null}, ${input.redemptionMode}, ${input.redemptionUrl ?? null}, ${input.terms ?? null},
        ${input.validFrom ?? null}, ${input.validUntil ?? null}, ${input.maxRedemptions ?? null}, ${input.status},
        ${userId}::uuid, ${userId}::uuid, CASE WHEN ${input.status}='published' THEN now() ELSE NULL END
      ) RETURNING id
    `.execute(database);
    return reply.code(201).send({ offer: { id: inserted.rows[0]?.id } });
  });

  app.patch('/api/v1/my/businesses/:id/leads/:leadId', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid(), leadId: z.string().uuid() }).safeParse(request.params);
    const parsed = leadPatchSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_business_lead_patch' });
    const access = await membership(database, params.data.id, userId);
    if (!access || !leadRoles.has(access.role)) return reply.code(403).send({ error: 'business_lead_edit_denied' });

    const result = await sql<{ id: string }>`
      UPDATE business_leads SET
        status = ${parsed.data.status},
        estimated_value_cents = CASE WHEN ${parsed.data.estimatedValueCents !== undefined} THEN ${parsed.data.estimatedValueCents ?? null} ELSE estimated_value_cents END,
        actual_value_cents = CASE WHEN ${parsed.data.actualValueCents !== undefined} THEN ${parsed.data.actualValueCents ?? null} ELSE actual_value_cents END,
        resolved_at = CASE WHEN ${parsed.data.status} IN ('won','lost','spam') THEN now() ELSE NULL END,
        updated_at = now()
      WHERE id = ${params.data.leadId}::uuid AND business_id = ${params.data.id}::uuid
      RETURNING id
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'business_lead_not_found' });
    return { ok: true, leadId: result.rows[0].id, status: parsed.data.status };
  });
}
