import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireDatabase } from '../http/helpers.js';
import { requiresSponsoredDisclosure, type BusinessPlan } from '../domain/business-directory.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const booleanQuery = z.enum(['true', 'false']).transform((value) => value === 'true');

const listQuerySchema = z.object({
  q: z.string().trim().max(120).optional(),
  municipalityId: z.string().uuid().optional(),
  placeId: z.string().uuid().optional(),
  category: z.string().trim().regex(slugPattern).optional(),
  featured: booleanQuery.optional(),
  sponsored: booleanQuery.optional(),
  lat: z.coerce.number().min(-90).max(90).optional(),
  lng: z.coerce.number().min(-180).max(180).optional(),
  radiusKm: z.coerce.number().positive().max(150).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
  offset: z.coerce.number().int().min(0).max(10_000).default(0),
}).superRefine((value, context) => {
  const hasLat = value.lat !== undefined;
  const hasLng = value.lng !== undefined;
  if (hasLat !== hasLng) {
    context.addIssue({ code: 'custom', message: 'lat_and_lng_required_together' });
  }
  if (value.radiusKm !== undefined && (!hasLat || !hasLng)) {
    context.addIssue({ code: 'custom', message: 'coordinates_required_for_radius' });
  }
});

const claimSchema = z.object({
  claimantName: z.string().trim().min(2).max(120),
  claimantEmail: z.string().trim().email().max(254),
  claimantPhone: z.string().trim().max(40).optional(),
  relationship: z.string().trim().min(2).max(120),
  message: z.string().trim().max(1_500).optional(),
  evidenceUrls: z.array(z.string().url().max(2_000)).max(8).default([]),
});

type BusinessListRow = {
  id: string;
  slug: string;
  name: string;
  short_description: string | null;
  municipality_id: string | null;
  municipality_name: string | null;
  municipality_slug: string | null;
  place_id: string | null;
  place_name: string | null;
  place_slug: string | null;
  address: string | null;
  longitude: number | null;
  latitude: number | null;
  distance_km: number | null;
  verification_status: 'unverified' | 'pending' | 'verified' | 'rejected';
  commercial_plan: BusinessPlan;
  featured: boolean;
  sponsored: boolean;
  active_featured: boolean;
  active_sponsored: boolean;
  logo_url: string | null;
  cover_image_url: string | null;
  categories: unknown;
};

type BusinessDetailRow = BusinessListRow & {
  legal_name: string | null;
  description: string | null;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  website: string | null;
  social_links: unknown;
  opening_hours: unknown;
  published_at: Date | null;
  updated_at: Date;
  media: unknown;
  sources: unknown;
};

function campaignExpression() {
  return sql<boolean>`(
    (b.campaign_start IS NULL OR b.campaign_start <= now())
    AND (b.campaign_end IS NULL OR b.campaign_end >= now())
  )`;
}

function serializeListRow(row: BusinessListRow) {
  const disclosure = requiresSponsoredDisclosure({
    sponsored: row.active_sponsored,
    commercialPlan: row.active_sponsored ? 'sponsor' : row.commercial_plan,
  });
  const placementLabel = disclosure ? 'Patrocinado' : row.active_featured ? 'Destacado' : null;

  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    shortDescription: row.short_description,
    territory: {
      municipalityId: row.municipality_id,
      municipalityName: row.municipality_name,
      municipalitySlug: row.municipality_slug,
      placeId: row.place_id,
      placeName: row.place_name,
      placeSlug: row.place_slug,
    },
    address: row.address,
    location: row.longitude === null || row.latitude === null
      ? null
      : { longitude: Number(row.longitude), latitude: Number(row.latitude) },
    distanceKm: row.distance_km === null ? null : Number(row.distance_km),
    categories: Array.isArray(row.categories) ? row.categories : [],
    verificationStatus: row.verification_status,
    verified: row.verification_status === 'verified',
    placement: {
      label: placementLabel,
      sponsored: row.active_sponsored,
      featured: row.active_featured,
    },
    logoUrl: row.logo_url,
    coverImageUrl: row.cover_image_url,
  };
}

export function registerBusinessDirectoryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/business-categories', async (_request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;

    const result = await sql<{
      id: string;
      parent_id: string | null;
      slug: string;
      name: string;
      description: string | null;
      icon_key: string | null;
      business_count: number;
    }>`
      SELECT c.id, c.parent_id, c.slug, c.name, c.description, c.icon_key,
             count(DISTINCT bcl.business_id) FILTER (WHERE b.status = 'published')::int AS business_count
      FROM business_categories c
      LEFT JOIN business_category_links bcl ON bcl.category_id = c.id
      LEFT JOIN businesses b ON b.id = bcl.business_id
      WHERE c.active = true
      GROUP BY c.id
      ORDER BY c.sort_order, c.name
    `.execute(database);

    return { categories: result.rows };
  });

  app.get('/api/v1/public/businesses', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;

    const parsed = listQuerySchema.safeParse(request.query ?? {});
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_business_filters', issues: parsed.error.issues });
    }
    const filters = parsed.data;
    const q = filters.q?.trim() || null;
    const municipalityId = filters.municipalityId ?? null;
    const placeId = filters.placeId ?? null;
    const category = filters.category ?? null;
    const featured = filters.featured ?? null;
    const sponsored = filters.sponsored ?? null;
    const lat = filters.lat ?? null;
    const lng = filters.lng ?? null;
    const radiusMeters = filters.radiusKm === undefined ? null : filters.radiusKm * 1_000;
    const activeCampaign = campaignExpression();

    const result = await sql<BusinessListRow>`
      SELECT b.id, b.slug, b.name, b.short_description,
             b.municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             b.place_id, p.name AS place_name, p.slug AS place_slug,
             b.address,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_X(b.location) END AS longitude,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_Y(b.location) END AS latitude,
             CASE
               WHEN b.location IS NULL OR ${lat}::double precision IS NULL OR ${lng}::double precision IS NULL THEN NULL
               ELSE ST_Distance(
                 b.location::geography,
                 ST_SetSRID(ST_MakePoint(${lng}::double precision, ${lat}::double precision), 4326)::geography
               ) / 1000.0
             END AS distance_km,
             b.verification_status, b.commercial_plan, b.featured, b.sponsored,
             (b.featured AND ${activeCampaign}) AS active_featured,
             ((b.sponsored OR b.commercial_plan = 'sponsor') AND ${activeCampaign}) AS active_sponsored,
             b.logo_url, b.cover_image_url,
             COALESCE((
               SELECT jsonb_agg(
                 jsonb_build_object('slug', c.slug, 'name', c.name, 'primary', bcl.is_primary)
                 ORDER BY bcl.is_primary DESC, c.sort_order, c.name
               )
               FROM business_category_links bcl
               JOIN business_categories c ON c.id = bcl.category_id AND c.active = true
               WHERE bcl.business_id = b.id
             ), '[]'::jsonb) AS categories
      FROM businesses b
      LEFT JOIN territory_municipalities m ON m.id = b.municipality_id
      LEFT JOIN territory_places p ON p.id = b.place_id
      WHERE b.status = 'published'
        AND (${q}::text IS NULL OR b.name ILIKE '%' || ${q}::text || '%'
             OR COALESCE(b.short_description, '') ILIKE '%' || ${q}::text || '%'
             OR COALESCE(b.description, '') ILIKE '%' || ${q}::text || '%')
        AND (${municipalityId}::uuid IS NULL OR b.municipality_id = ${municipalityId}::uuid)
        AND (${placeId}::uuid IS NULL OR b.place_id = ${placeId}::uuid)
        AND (${category}::text IS NULL OR EXISTS (
          SELECT 1
          FROM business_category_links category_link
          JOIN business_categories category_row ON category_row.id = category_link.category_id
          WHERE category_link.business_id = b.id AND category_row.slug = ${category}::text
        ))
        AND (${featured}::boolean IS NULL OR b.featured = ${featured}::boolean)
        AND (${sponsored}::boolean IS NULL OR b.sponsored = ${sponsored}::boolean)
        AND (${radiusMeters}::double precision IS NULL OR (
          b.location IS NOT NULL
          AND ST_DWithin(
            b.location::geography,
            ST_SetSRID(ST_MakePoint(${lng}::double precision, ${lat}::double precision), 4326)::geography,
            ${radiusMeters}::double precision
          )
        ))
      ORDER BY
        CASE
          WHEN (b.sponsored OR b.commercial_plan = 'sponsor') AND ${activeCampaign} THEN 0
          WHEN b.featured AND ${activeCampaign} THEN 1
          ELSE 2
        END,
        CASE WHEN ${lat}::double precision IS NOT NULL AND b.location IS NOT NULL
          THEN ST_Distance(
            b.location::geography,
            ST_SetSRID(ST_MakePoint(${lng}::double precision, ${lat}::double precision), 4326)::geography
          )
          ELSE NULL
        END NULLS LAST,
        b.priority DESC,
        b.name
      LIMIT ${filters.limit}
      OFFSET ${filters.offset}
    `.execute(database);

    const items = result.rows.map(serializeListRow);
    return {
      businesses: items,
      meta: {
        limit: filters.limit,
        offset: filters.offset,
        count: items.length,
        sponsoredDisclosure: 'Las posiciones comerciales se muestran siempre con la etiqueta Patrocinado o Destacado.',
      },
    };
  });

  app.get('/api/v1/public/businesses/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const parsed = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_slug' });
    const activeCampaign = campaignExpression();

    const result = await sql<BusinessDetailRow>`
      SELECT b.id, b.slug, b.name, b.legal_name, b.short_description, b.description,
             b.municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             b.place_id, p.name AS place_name, p.slug AS place_slug,
             b.address,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_X(b.location) END AS longitude,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_Y(b.location) END AS latitude,
             NULL::double precision AS distance_km,
             b.phone, b.whatsapp, b.email, b.website, b.social_links, b.opening_hours,
             b.verification_status, b.commercial_plan, b.featured, b.sponsored,
             (b.featured AND ${activeCampaign}) AS active_featured,
             ((b.sponsored OR b.commercial_plan = 'sponsor') AND ${activeCampaign}) AS active_sponsored,
             b.logo_url, b.cover_image_url, b.published_at, b.updated_at,
             COALESCE((
               SELECT jsonb_agg(
                 jsonb_build_object('slug', c.slug, 'name', c.name, 'primary', bcl.is_primary)
                 ORDER BY bcl.is_primary DESC, c.sort_order, c.name
               )
               FROM business_category_links bcl
               JOIN business_categories c ON c.id = bcl.category_id AND c.active = true
               WHERE bcl.business_id = b.id
             ), '[]'::jsonb) AS categories,
             COALESCE((
               SELECT jsonb_agg(
                 jsonb_build_object(
                   'id', bm.id,
                   'kind', bm.kind,
                   'url', bm.url,
                   'thumbnailUrl', bm.thumbnail_url,
                   'altText', bm.alt_text,
                   'credit', bm.credit,
                   'origin', bm.origin,
                   'sourceUrl', bm.source_url,
                   'aiGenerated', bm.ai_generated,
                   'aiDisclosure', bm.ai_disclosure
                 ) ORDER BY bm.sort_order, bm.created_at
               )
               FROM business_media bm
               WHERE bm.business_id = b.id AND bm.active = true
             ), '[]'::jsonb) AS media,
             COALESCE((
               SELECT jsonb_agg(
                 jsonb_build_object(
                   'sourceName', bs.source_name,
                   'sourceUrl', bs.source_url,
                   'fetchedAt', bs.fetched_at,
                   'lastSyncedAt', bs.last_synced_at,
                   'syncStatus', bs.sync_status
                 ) ORDER BY bs.updated_at DESC
               )
               FROM business_sources bs
               WHERE bs.business_id = b.id
             ), '[]'::jsonb) AS sources
      FROM businesses b
      LEFT JOIN territory_municipalities m ON m.id = b.municipality_id
      LEFT JOIN territory_places p ON p.id = b.place_id
      WHERE b.slug = ${parsed.data.slug} AND b.status = 'published'
      LIMIT 1
    `.execute(database);

    const row = result.rows[0];
    if (!row) return reply.code(404).send({ error: 'business_not_found' });

    return {
      business: {
        ...serializeListRow(row),
        legalName: row.legal_name,
        description: row.description,
        contact: {
          phone: row.phone,
          whatsapp: row.whatsapp,
          email: row.email,
          website: row.website,
          socialLinks: row.social_links ?? {},
        },
        openingHours: row.opening_hours ?? {},
        media: Array.isArray(row.media) ? row.media : [],
        sources: Array.isArray(row.sources) ? row.sources : [],
        publishedAt: row.published_at,
        updatedAt: row.updated_at,
      },
    };
  });

  app.post('/api/v1/public/businesses/:slug/claims', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });
    const body = claimSchema.safeParse(request.body);
    if (!body.success) return reply.code(400).send({ error: 'invalid_business_claim', issues: body.error.issues });

    const target = await sql<{ id: string; name: string }>`
      SELECT id, name FROM businesses
      WHERE slug = ${params.data.slug} AND status = 'published'
      LIMIT 1
    `.execute(database);
    const business = target.rows[0];
    if (!business) return reply.code(404).send({ error: 'business_not_found' });

    const duplicate = await sql<{ id: string }>`
      SELECT id
      FROM business_claims
      WHERE business_id = ${business.id}::uuid
        AND lower(claimant_email) = lower(${body.data.claimantEmail})
        AND status IN ('pending', 'needs_info')
      LIMIT 1
    `.execute(database);
    if (duplicate.rows[0]) {
      return reply.code(409).send({ error: 'active_claim_already_exists' });
    }

    const evidence = {
      relationship: body.data.relationship,
      message: body.data.message ?? null,
      urls: body.data.evidenceUrls,
    };
    const inserted = await sql<{ id: string; created_at: Date }>`
      INSERT INTO business_claims (
        business_id, claimant_name, claimant_email, claimant_phone, evidence
      ) VALUES (
        ${business.id}::uuid,
        ${body.data.claimantName},
        ${body.data.claimantEmail.toLowerCase()},
        ${body.data.claimantPhone ?? null},
        ${JSON.stringify(evidence)}::jsonb
      )
      RETURNING id, created_at
    `.execute(database);

    return reply.code(201).send({
      claim: {
        id: inserted.rows[0]?.id,
        status: 'pending',
        createdAt: inserted.rows[0]?.created_at,
      },
      message: 'Solicitud recibida. La ficha no se verificará hasta completar la revisión administrativa.',
    });
  });
}
