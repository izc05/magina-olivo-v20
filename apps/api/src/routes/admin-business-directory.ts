import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { BUSINESS_PLANS, BUSINESS_STATUSES, BUSINESS_VERIFICATION_STATUSES } from '../domain/business-directory.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const nullableUrl = z.union([z.string().url().max(2_000), z.literal(''), z.null()]).optional();
const nullableEmail = z.union([z.string().email().max(254), z.literal(''), z.null()]).optional();
const nullableText = (max: number) => z.union([z.string().trim().max(max), z.null()]).optional();

const locationSchema = z.object({
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
}).nullable();

const businessCreateSchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  name: z.string().trim().min(2).max(180),
  legalName: nullableText(220),
  shortDescription: nullableText(320),
  description: nullableText(8_000),
  municipalityId: z.string().uuid().nullable().optional(),
  placeId: z.string().uuid().nullable().optional(),
  address: nullableText(400),
  location: locationSchema.optional(),
  phone: nullableText(40),
  whatsapp: nullableText(40),
  email: nullableEmail,
  website: nullableUrl,
  socialLinks: z.record(z.string(), z.string().max(2_000)).optional(),
  openingHours: z.record(z.string(), z.unknown()).optional(),
  logoUrl: nullableUrl,
  coverImageUrl: nullableUrl,
  status: z.enum(BUSINESS_STATUSES).default('draft'),
  verificationStatus: z.enum(BUSINESS_VERIFICATION_STATUSES).default('unverified'),
  commercialPlan: z.enum(BUSINESS_PLANS).default('free'),
  featured: z.boolean().default(false),
  sponsored: z.boolean().default(false),
  priority: z.number().int().min(0).max(10_000).default(0),
  campaignStart: z.coerce.date().nullable().optional(),
  campaignEnd: z.coerce.date().nullable().optional(),
  categorySlugs: z.array(z.string().regex(slugPattern)).max(20).default([]),
  primaryCategorySlug: z.string().regex(slugPattern).nullable().optional(),
}).superRefine((value, context) => {
  if (value.campaignStart && value.campaignEnd && value.campaignEnd < value.campaignStart) {
    context.addIssue({ code: 'custom', path: ['campaignEnd'], message: 'campaign_end_before_start' });
  }
  if (value.primaryCategorySlug && !value.categorySlugs.includes(value.primaryCategorySlug)) {
    context.addIssue({ code: 'custom', path: ['primaryCategorySlug'], message: 'primary_category_must_be_selected' });
  }
});

const businessPatchSchema = businessCreateSchema.partial().superRefine((value, context) => {
  if (Object.keys(value).length === 0) {
    context.addIssue({ code: 'custom', message: 'at_least_one_change_required' });
  }
  if (value.campaignStart && value.campaignEnd && value.campaignEnd < value.campaignStart) {
    context.addIssue({ code: 'custom', path: ['campaignEnd'], message: 'campaign_end_before_start' });
  }
  if (value.primaryCategorySlug && value.categorySlugs && !value.categorySlugs.includes(value.primaryCategorySlug)) {
    context.addIssue({ code: 'custom', path: ['primaryCategorySlug'], message: 'primary_category_must_be_selected' });
  }
});

const claimReviewSchema = z.object({
  status: z.enum(['needs_info', 'approved', 'rejected', 'cancelled']),
  adminNotes: z.string().trim().max(2_000).nullable().optional(),
});

const mediaSchema = z.object({
  kind: z.enum(['logo', 'cover', 'photo', 'video', 'document']),
  url: z.string().url().max(2_000),
  thumbnailUrl: nullableUrl,
  altText: nullableText(300),
  credit: nullableText(300),
  origin: z.enum(['owned', 'official', 'licensed', 'external_reference', 'ai_generated']).default('owned'),
  sourceUrl: nullableUrl,
  aiGenerated: z.boolean().default(false),
  aiDisclosure: nullableText(500),
  sortOrder: z.number().int().min(0).max(10_000).default(0),
}).superRefine((value, context) => {
  if (value.aiGenerated && value.origin !== 'ai_generated') {
    context.addIssue({ code: 'custom', path: ['origin'], message: 'ai_media_requires_ai_generated_origin' });
  }
  if (value.origin === 'ai_generated' && !value.aiGenerated) {
    context.addIssue({ code: 'custom', path: ['aiGenerated'], message: 'ai_generated_origin_requires_disclosure_flag' });
  }
  if (value.aiGenerated && !value.aiDisclosure) {
    context.addIssue({ code: 'custom', path: ['aiDisclosure'], message: 'ai_media_requires_disclosure' });
  }
});

const sourceSchema = z.object({
  sourceName: z.string().trim().min(2).max(160),
  externalId: nullableText(240),
  sourceUrl: nullableUrl,
  licenseNotes: nullableText(1_000),
  rawData: z.record(z.string(), z.unknown()).default({}),
  fetchedAt: z.coerce.date().nullable().optional(),
});

type AdminBusinessRow = {
  id: string;
  slug: string;
  name: string;
  legal_name: string | null;
  short_description: string | null;
  description: string | null;
  municipality_id: string | null;
  municipality_name: string | null;
  place_id: string | null;
  place_name: string | null;
  address: string | null;
  longitude: number | null;
  latitude: number | null;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  website: string | null;
  social_links: unknown;
  opening_hours: unknown;
  logo_url: string | null;
  cover_image_url: string | null;
  verification_status: string;
  status: string;
  commercial_plan: string;
  featured: boolean;
  sponsored: boolean;
  priority: number;
  campaign_start: Date | null;
  campaign_end: Date | null;
  published_at: Date | null;
  created_at: Date;
  updated_at: Date;
  categories: unknown;
  media_count: number;
  source_count: number;
  pending_claim_count: number;
};

function emptyToNull(value: string | null | undefined) {
  if (value === undefined) return undefined;
  return value === '' ? null : value;
}

async function ensureTerritory(
  database: DatabaseClient,
  municipalityId: string | null | undefined,
  placeId: string | null | undefined,
) {
  if (!placeId) return { ok: true as const, municipalityId: municipalityId ?? null };
  const result = await sql<{ municipality_id: string }>`
    SELECT municipality_id FROM territory_places WHERE id = ${placeId}::uuid LIMIT 1
  `.execute(database);
  const place = result.rows[0];
  if (!place) return { ok: false as const, error: 'territory_place_not_found' };
  if (municipalityId && place.municipality_id !== municipalityId) {
    return { ok: false as const, error: 'place_municipality_mismatch' };
  }
  return { ok: true as const, municipalityId: place.municipality_id };
}

async function replaceCategories(
  database: DatabaseClient,
  businessId: string,
  categorySlugs: string[],
  primaryCategorySlug: string | null | undefined,
) {
  const uniqueSlugs = [...new Set(categorySlugs)];
  if (uniqueSlugs.length === 0) {
    await sql`DELETE FROM business_category_links WHERE business_id = ${businessId}::uuid`.execute(database);
    return { ok: true as const };
  }

  const categories = await sql<{ id: string; slug: string }>`
    SELECT id, slug FROM business_categories
    WHERE active = true AND slug = ANY(${sql.val(uniqueSlugs)}::text[])
  `.execute(database);
  if (categories.rows.length !== uniqueSlugs.length) {
    const found = new Set(categories.rows.map((category) => category.slug));
    return {
      ok: false as const,
      error: 'unknown_business_category',
      missing: uniqueSlugs.filter((slug) => !found.has(slug)),
    };
  }

  await database.transaction().execute(async (trx) => {
    await sql`DELETE FROM business_category_links WHERE business_id = ${businessId}::uuid`.execute(trx);
    for (const category of categories.rows) {
      await sql`
        INSERT INTO business_category_links (business_id, category_id, is_primary)
        VALUES (${businessId}::uuid, ${category.id}::uuid, ${category.slug === primaryCategorySlug})
      `.execute(trx);
    }
  });
  return { ok: true as const };
}

async function businessCatalog(database: DatabaseClient) {
  const [businesses, categories, claims] = await Promise.all([
    sql<AdminBusinessRow>`
      SELECT b.id, b.slug, b.name, b.legal_name, b.short_description, b.description,
             b.municipality_id, m.name AS municipality_name,
             b.place_id, p.name AS place_name, b.address,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_X(b.location) END AS longitude,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_Y(b.location) END AS latitude,
             b.phone, b.whatsapp, b.email, b.website, b.social_links, b.opening_hours,
             b.logo_url, b.cover_image_url,
             b.verification_status, b.status, b.commercial_plan,
             b.featured, b.sponsored, b.priority, b.campaign_start, b.campaign_end,
             b.published_at, b.created_at, b.updated_at,
             COALESCE((
               SELECT jsonb_agg(jsonb_build_object('slug', c.slug, 'name', c.name, 'primary', l.is_primary)
                                ORDER BY l.is_primary DESC, c.sort_order, c.name)
               FROM business_category_links l
               JOIN business_categories c ON c.id = l.category_id
               WHERE l.business_id = b.id
             ), '[]'::jsonb) AS categories,
             (SELECT count(*)::int FROM business_media bm WHERE bm.business_id = b.id AND bm.active = true) AS media_count,
             (SELECT count(*)::int FROM business_sources bs WHERE bs.business_id = b.id) AS source_count,
             (SELECT count(*)::int FROM business_claims bc
               WHERE bc.business_id = b.id AND bc.status IN ('pending','needs_info')) AS pending_claim_count
      FROM businesses b
      LEFT JOIN territory_municipalities m ON m.id = b.municipality_id
      LEFT JOIN territory_places p ON p.id = b.place_id
      ORDER BY CASE b.status WHEN 'published' THEN 0 WHEN 'draft' THEN 1 ELSE 2 END,
               b.updated_at DESC, b.name
    `.execute(database),
    sql<{
      id: string; parent_id: string | null; slug: string; name: string; description: string | null;
      icon_key: string | null; sort_order: number; active: boolean; business_count: number;
    }>`
      SELECT c.*,
             count(l.business_id)::int AS business_count
      FROM business_categories c
      LEFT JOIN business_category_links l ON l.category_id = c.id
      GROUP BY c.id
      ORDER BY c.sort_order, c.name
    `.execute(database),
    sql<{
      id: string; business_id: string; business_name: string; claimant_name: string; claimant_email: string;
      claimant_phone: string | null; evidence: unknown; status: string; admin_notes: string | null;
      reviewed_at: Date | null; created_at: Date; updated_at: Date;
    }>`
      SELECT bc.id, bc.business_id, b.name AS business_name,
             bc.claimant_name, bc.claimant_email, bc.claimant_phone, bc.evidence,
             bc.status, bc.admin_notes, bc.reviewed_at, bc.created_at, bc.updated_at
      FROM business_claims bc
      JOIN businesses b ON b.id = bc.business_id
      ORDER BY CASE bc.status WHEN 'pending' THEN 0 WHEN 'needs_info' THEN 1 ELSE 2 END, bc.created_at DESC
      LIMIT 300
    `.execute(database),
  ]);

  const rows = businesses.rows.map((business) => ({
    id: business.id,
    slug: business.slug,
    name: business.name,
    legalName: business.legal_name,
    shortDescription: business.short_description,
    description: business.description,
    municipalityId: business.municipality_id,
    municipalityName: business.municipality_name,
    placeId: business.place_id,
    placeName: business.place_name,
    address: business.address,
    location: business.longitude === null || business.latitude === null
      ? null
      : { longitude: Number(business.longitude), latitude: Number(business.latitude) },
    phone: business.phone,
    whatsapp: business.whatsapp,
    email: business.email,
    website: business.website,
    socialLinks: business.social_links ?? {},
    openingHours: business.opening_hours ?? {},
    logoUrl: business.logo_url,
    coverImageUrl: business.cover_image_url,
    verificationStatus: business.verification_status,
    status: business.status,
    commercialPlan: business.commercial_plan,
    featured: business.featured,
    sponsored: business.sponsored,
    priority: business.priority,
    campaignStart: business.campaign_start,
    campaignEnd: business.campaign_end,
    publishedAt: business.published_at,
    createdAt: business.created_at,
    updatedAt: business.updated_at,
    categories: Array.isArray(business.categories) ? business.categories : [],
    mediaCount: business.media_count,
    sourceCount: business.source_count,
    pendingClaimCount: business.pending_claim_count,
  }));

  return {
    businesses: rows,
    categories: categories.rows,
    claims: claims.rows,
    stats: {
      total: rows.length,
      published: rows.filter((row) => row.status === 'published').length,
      draft: rows.filter((row) => row.status === 'draft').length,
      verified: rows.filter((row) => row.verificationStatus === 'verified').length,
      sponsored: rows.filter((row) => row.sponsored || row.commercialPlan === 'sponsor').length,
      pendingClaims: claims.rows.filter((claim) => claim.status === 'pending' || claim.status === 'needs_info').length,
    },
  };
}

export function registerAdminBusinessDirectoryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/business-directory', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    return businessCatalog(auth.database);
  });

  app.post('/api/v1/admin/businesses', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const parsed = businessCreateSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business', issues: parsed.error.issues });
    const input = parsed.data;

    const territory = await ensureTerritory(auth.database, input.municipalityId, input.placeId);
    if (!territory.ok) return reply.code(400).send({ error: territory.error });

    const inserted = await sql<{ id: string }>`
      INSERT INTO businesses (
        slug, name, legal_name, short_description, description,
        municipality_id, place_id, address, location,
        phone, whatsapp, email, website, social_links, opening_hours,
        logo_url, cover_image_url, verification_status, status,
        commercial_plan, featured, sponsored, priority, campaign_start, campaign_end,
        created_by, updated_by, published_at
      ) VALUES (
        ${input.slug}, ${input.name}, ${emptyToNull(input.legalName)}, ${emptyToNull(input.shortDescription)}, ${emptyToNull(input.description)},
        ${territory.municipalityId}::uuid, ${input.placeId ?? null}::uuid, ${emptyToNull(input.address)},
        CASE WHEN ${input.location?.longitude ?? null}::double precision IS NULL THEN NULL
             ELSE ST_SetSRID(ST_MakePoint(${input.location?.longitude ?? null}, ${input.location?.latitude ?? null}), 4326) END,
        ${emptyToNull(input.phone)}, ${emptyToNull(input.whatsapp)}, ${emptyToNull(input.email)?.toLowerCase() ?? null},
        ${emptyToNull(input.website)}, ${JSON.stringify(input.socialLinks ?? {})}::jsonb, ${JSON.stringify(input.openingHours ?? {})}::jsonb,
        ${emptyToNull(input.logoUrl)}, ${emptyToNull(input.coverImageUrl)}, ${input.verificationStatus}, ${input.status},
        ${input.commercialPlan}, ${input.featured}, ${input.sponsored}, ${input.priority},
        ${input.campaignStart ?? null}, ${input.campaignEnd ?? null},
        ${auth.access.userId}::uuid, ${auth.access.userId}::uuid,
        CASE WHEN ${input.status} = 'published' THEN now() ELSE NULL END
      )
      RETURNING id
    `.execute(auth.database);
    const id = inserted.rows[0]?.id;
    if (!id) return reply.code(500).send({ error: 'business_create_failed' });

    const categories = await replaceCategories(auth.database, id, input.categorySlugs, input.primaryCategorySlug);
    if (!categories.ok) {
      await sql`DELETE FROM businesses WHERE id = ${id}::uuid`.execute(auth.database);
      return reply.code(400).send(categories);
    }

    await auditAdminAction(auth.database, auth.access, 'business.created', 'business', id, {
      slug: input.slug,
      name: input.name,
      status: input.status,
      commercialPlan: input.commercialPlan,
      sponsored: input.sponsored,
      featured: input.featured,
    });
    const catalog = await businessCatalog(auth.database);
    return reply.code(201).send({ business: catalog.businesses.find((business) => business.id === id) ?? null });
  });

  app.patch('/api/v1/admin/businesses/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });
    const parsed = businessPatchSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_patch', issues: parsed.error.issues });
    const input = parsed.data;

    const existing = await sql<{
      id: string; status: string; municipality_id: string | null; place_id: string | null;
      campaign_start: Date | null; campaign_end: Date | null;
    }>`SELECT id, status, municipality_id, place_id, campaign_start, campaign_end
       FROM businesses WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    const current = existing.rows[0];
    if (!current) return reply.code(404).send({ error: 'business_not_found' });

    const targetMunicipality = input.municipalityId === undefined ? current.municipality_id : input.municipalityId;
    const targetPlace = input.placeId === undefined ? current.place_id : input.placeId;
    const territory = await ensureTerritory(auth.database, targetMunicipality, targetPlace);
    if (!territory.ok) return reply.code(400).send({ error: territory.error });

    const nextCampaignStart = input.campaignStart === undefined ? current.campaign_start : input.campaignStart;
    const nextCampaignEnd = input.campaignEnd === undefined ? current.campaign_end : input.campaignEnd;
    if (nextCampaignStart && nextCampaignEnd && nextCampaignEnd < nextCampaignStart) {
      return reply.code(400).send({ error: 'campaign_end_before_start' });
    }

    await sql`
      UPDATE businesses SET
        slug = CASE WHEN ${input.slug !== undefined} THEN ${input.slug ?? ''} ELSE slug END,
        name = CASE WHEN ${input.name !== undefined} THEN ${input.name ?? ''} ELSE name END,
        legal_name = CASE WHEN ${input.legalName !== undefined} THEN ${emptyToNull(input.legalName)} ELSE legal_name END,
        short_description = CASE WHEN ${input.shortDescription !== undefined} THEN ${emptyToNull(input.shortDescription)} ELSE short_description END,
        description = CASE WHEN ${input.description !== undefined} THEN ${emptyToNull(input.description)} ELSE description END,
        municipality_id = ${territory.municipalityId}::uuid,
        place_id = ${targetPlace}::uuid,
        address = CASE WHEN ${input.address !== undefined} THEN ${emptyToNull(input.address)} ELSE address END,
        location = CASE
          WHEN ${input.location !== undefined} THEN
            CASE WHEN ${input.location?.longitude ?? null}::double precision IS NULL THEN NULL
                 ELSE ST_SetSRID(ST_MakePoint(${input.location?.longitude ?? null}, ${input.location?.latitude ?? null}), 4326) END
          ELSE location END,
        phone = CASE WHEN ${input.phone !== undefined} THEN ${emptyToNull(input.phone)} ELSE phone END,
        whatsapp = CASE WHEN ${input.whatsapp !== undefined} THEN ${emptyToNull(input.whatsapp)} ELSE whatsapp END,
        email = CASE WHEN ${input.email !== undefined} THEN ${emptyToNull(input.email)?.toLowerCase() ?? null} ELSE email END,
        website = CASE WHEN ${input.website !== undefined} THEN ${emptyToNull(input.website)} ELSE website END,
        social_links = CASE WHEN ${input.socialLinks !== undefined} THEN ${JSON.stringify(input.socialLinks ?? {})}::jsonb ELSE social_links END,
        opening_hours = CASE WHEN ${input.openingHours !== undefined} THEN ${JSON.stringify(input.openingHours ?? {})}::jsonb ELSE opening_hours END,
        logo_url = CASE WHEN ${input.logoUrl !== undefined} THEN ${emptyToNull(input.logoUrl)} ELSE logo_url END,
        cover_image_url = CASE WHEN ${input.coverImageUrl !== undefined} THEN ${emptyToNull(input.coverImageUrl)} ELSE cover_image_url END,
        verification_status = CASE WHEN ${input.verificationStatus !== undefined} THEN ${input.verificationStatus ?? 'unverified'} ELSE verification_status END,
        verified_at = CASE
          WHEN ${input.verificationStatus === 'verified'} THEN COALESCE(verified_at, now())
          WHEN ${input.verificationStatus !== undefined && input.verificationStatus !== 'verified'} THEN NULL
          ELSE verified_at END,
        verified_by = CASE
          WHEN ${input.verificationStatus === 'verified'} THEN ${auth.access.userId}::uuid
          WHEN ${input.verificationStatus !== undefined && input.verificationStatus !== 'verified'} THEN NULL
          ELSE verified_by END,
        status = CASE WHEN ${input.status !== undefined} THEN ${input.status ?? 'draft'} ELSE status END,
        commercial_plan = CASE WHEN ${input.commercialPlan !== undefined} THEN ${input.commercialPlan ?? 'free'} ELSE commercial_plan END,
        featured = CASE WHEN ${input.featured !== undefined} THEN ${input.featured ?? false} ELSE featured END,
        sponsored = CASE WHEN ${input.sponsored !== undefined} THEN ${input.sponsored ?? false} ELSE sponsored END,
        priority = CASE WHEN ${input.priority !== undefined} THEN ${input.priority ?? 0} ELSE priority END,
        campaign_start = ${nextCampaignStart},
        campaign_end = ${nextCampaignEnd},
        published_at = CASE
          WHEN ${input.status === 'published'} AND published_at IS NULL THEN now()
          WHEN ${input.status === 'draft'} THEN NULL
          ELSE published_at END,
        updated_by = ${auth.access.userId}::uuid,
        updated_at = now()
      WHERE id = ${params.data.id}::uuid
    `.execute(auth.database);

    if (input.categorySlugs !== undefined) {
      const categories = await replaceCategories(
        auth.database,
        params.data.id,
        input.categorySlugs,
        input.primaryCategorySlug,
      );
      if (!categories.ok) return reply.code(400).send(categories);
    }

    await auditAdminAction(auth.database, auth.access, 'business.updated', 'business', params.data.id, {
      fields: Object.keys(input),
      commercial: {
        commercialPlan: input.commercialPlan,
        sponsored: input.sponsored,
        featured: input.featured,
        priority: input.priority,
        campaignStart: input.campaignStart,
        campaignEnd: input.campaignEnd,
      },
    });
    const catalog = await businessCatalog(auth.database);
    return { business: catalog.businesses.find((business) => business.id === params.data.id) ?? null };
  });

  app.post('/api/v1/admin/businesses/:id/media', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });
    const parsed = mediaSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_media', issues: parsed.error.issues });
    const input = parsed.data;

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_media (
        business_id, kind, url, thumbnail_url, alt_text, credit, origin,
        source_url, ai_generated, ai_disclosure, sort_order
      ) VALUES (
        ${params.data.id}::uuid, ${input.kind}, ${input.url}, ${emptyToNull(input.thumbnailUrl)},
        ${emptyToNull(input.altText)}, ${emptyToNull(input.credit)}, ${input.origin},
        ${emptyToNull(input.sourceUrl)}, ${input.aiGenerated}, ${emptyToNull(input.aiDisclosure)}, ${input.sortOrder}
      ) RETURNING id
    `.execute(auth.database);
    const mediaId = inserted.rows[0]?.id;
    if (!mediaId) return reply.code(500).send({ error: 'business_media_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'business.media_added', 'business_media', mediaId, {
      businessId: params.data.id,
      kind: input.kind,
      origin: input.origin,
      aiGenerated: input.aiGenerated,
    });
    return reply.code(201).send({ media: { id: mediaId } });
  });

  app.post('/api/v1/admin/businesses/:id/sources', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });
    const parsed = sourceSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_source', issues: parsed.error.issues });
    const input = parsed.data;

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_sources (
        business_id, source_name, external_id, source_url, license_notes, raw_data,
        fetched_at, last_synced_at, sync_status
      ) VALUES (
        ${params.data.id}::uuid, ${input.sourceName}, ${emptyToNull(input.externalId)},
        ${emptyToNull(input.sourceUrl)}, ${emptyToNull(input.licenseNotes)}, ${JSON.stringify(input.rawData)}::jsonb,
        ${input.fetchedAt ?? null}, now(), 'ok'
      )
      ON CONFLICT (source_name, external_id) WHERE external_id IS NOT NULL
      DO UPDATE SET
        business_id = EXCLUDED.business_id,
        source_url = EXCLUDED.source_url,
        license_notes = EXCLUDED.license_notes,
        raw_data = EXCLUDED.raw_data,
        fetched_at = EXCLUDED.fetched_at,
        last_synced_at = now(),
        sync_status = 'ok',
        sync_error = NULL,
        updated_at = now()
      RETURNING id
    `.execute(auth.database);
    const sourceId = inserted.rows[0]?.id;
    if (!sourceId) return reply.code(500).send({ error: 'business_source_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'business.source_upserted', 'business_source', sourceId, {
      businessId: params.data.id,
      sourceName: input.sourceName,
      externalId: input.externalId ?? null,
    });
    return reply.code(201).send({ source: { id: sourceId } });
  });

  app.patch('/api/v1/admin/business-claims/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_claim_id' });
    const parsed = claimReviewSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_claim_review', issues: parsed.error.issues });

    const claim = await sql<{ business_id: string; status: string }>`
      SELECT business_id, status FROM business_claims WHERE id = ${params.data.id}::uuid LIMIT 1
    `.execute(auth.database);
    const current = claim.rows[0];
    if (!current) return reply.code(404).send({ error: 'business_claim_not_found' });
    if (!['pending', 'needs_info'].includes(current.status)) {
      return reply.code(409).send({ error: 'business_claim_already_resolved' });
    }

    await auth.database.transaction().execute(async (trx) => {
      await sql`
        UPDATE business_claims
        SET status = ${parsed.data.status}, admin_notes = ${parsed.data.adminNotes ?? null},
            reviewed_by = ${auth.access.userId}::uuid, reviewed_at = now(), updated_at = now()
        WHERE id = ${params.data.id}::uuid
      `.execute(trx);
      if (parsed.data.status === 'approved') {
        await sql`
          UPDATE businesses
          SET verification_status = 'verified', verified_at = now(), verified_by = ${auth.access.userId}::uuid,
              updated_by = ${auth.access.userId}::uuid, updated_at = now()
          WHERE id = ${current.business_id}::uuid
        `.execute(trx);
      }
    });

    await auditAdminAction(auth.database, auth.access, 'business.claim_reviewed', 'business_claim', params.data.id, {
      businessId: current.business_id,
      from: current.status,
      to: parsed.data.status,
    });
    return { ok: true, claimId: params.data.id, status: parsed.data.status };
  });
}
