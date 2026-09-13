import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';

const booleanQuery = z.enum(['true', 'false']).transform((value) => value === 'true');
const nearbyBusinessRadiusM = 15_000;

const publicRouteQuery = z.object({
  q: z.string().trim().min(1).max(120).optional(),
  municipality_id: z.string().uuid().optional(),
  place_id: z.string().uuid().optional(),
  type: z.enum(['hiking', 'mtb', 'cycling', 'trail', 'family', 'mixed']).optional(),
  difficulty: z.enum(['easy', 'moderate', 'hard', 'very_hard']).optional(),
  circular: booleanQuery.optional(),
  family_friendly: booleanQuery.optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
});

type NearbyBusinessRow = {
  id: string;
  slug: string;
  name: string;
  short_description: string | null;
  address: string | null;
  phone: string | null;
  whatsapp: string | null;
  website: string | null;
  logo_url: string | null;
  cover_image_url: string | null;
  verification_status: string;
  municipality_id: string | null;
  municipality_name: string | null;
  municipality_slug: string | null;
  place_id: string | null;
  place_name: string | null;
  place_slug: string | null;
  longitude: number | null;
  latitude: number | null;
  distance_m: number | null;
  distance_basis: 'spatial' | 'same_place' | 'same_municipality';
  active_featured: boolean;
  active_sponsored: boolean;
  categories: unknown;
};

function serializeNearbyBusiness(row: NearbyBusinessRow) {
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    shortDescription: row.short_description,
    address: row.address,
    contact: { phone: row.phone, whatsapp: row.whatsapp, website: row.website },
    territory: {
      municipalityId: row.municipality_id,
      municipalityName: row.municipality_name,
      municipalitySlug: row.municipality_slug,
      placeId: row.place_id,
      placeName: row.place_name,
      placeSlug: row.place_slug,
    },
    location: row.longitude === null || row.latitude === null
      ? null
      : { longitude: Number(row.longitude), latitude: Number(row.latitude) },
    distanceMeters: row.distance_m === null ? null : Number(row.distance_m),
    distanceBasis: row.distance_basis,
    categories: Array.isArray(row.categories) ? row.categories : [],
    verified: row.verification_status === 'verified',
    logoUrl: row.logo_url,
    coverImageUrl: row.cover_image_url,
    placement: {
      sponsored: row.active_sponsored,
      featured: row.active_featured,
      label: row.active_sponsored ? 'Patrocinado' : row.active_featured ? 'Destacado' : null,
    },
  };
}

export function registerRoutesExploreRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/routes', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const parsed = publicRouteQuery.safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_route_filters' });
    const filters = parsed.data;

    const result = await sql<{
      id: string;
      slug: string;
      name: string;
      route_type: string;
      difficulty: string | null;
      distance_m: number | null;
      duration_minutes: number | null;
      elevation_gain_m: number | null;
      elevation_loss_m: number | null;
      circular: boolean;
      family_friendly: boolean;
      short_description: string | null;
      municipality_name: string | null;
      place_name: string | null;
      hero_url: string | null;
    }>`
      SELECT r.id, r.slug, r.name, r.route_type, r.difficulty, r.distance_m,
             r.duration_minutes, r.elevation_gain_m, r.elevation_loss_m,
             r.circular, r.family_friendly, r.short_description,
             m.name AS municipality_name, p.name AS place_name,
             (
               SELECT rm.url
               FROM route_media rm
               WHERE rm.route_id = r.id
                 AND rm.active = true
                 AND rm.kind IN ('hero_image', 'photo', 'thumbnail')
               ORDER BY CASE rm.kind WHEN 'hero_image' THEN 0 WHEN 'thumbnail' THEN 1 ELSE 2 END,
                        rm.sort_order, rm.created_at
               LIMIT 1
             ) AS hero_url
      FROM routes r
      LEFT JOIN territory_municipalities m ON m.id = r.municipality_id
      LEFT JOIN territory_places p ON p.id = r.place_id
      WHERE r.status = 'published'
        AND r.track_status = 'validated'
        AND (${filters.q ?? null}::text IS NULL OR r.name ILIKE '%' || ${filters.q ?? null} || '%' OR coalesce(r.short_description, '') ILIKE '%' || ${filters.q ?? null} || '%')
        AND (${filters.municipality_id ?? null}::uuid IS NULL OR r.municipality_id = ${filters.municipality_id ?? null}::uuid)
        AND (${filters.place_id ?? null}::uuid IS NULL OR r.place_id = ${filters.place_id ?? null}::uuid)
        AND (${filters.type ?? null}::text IS NULL OR r.route_type = ${filters.type ?? null})
        AND (${filters.difficulty ?? null}::text IS NULL OR r.difficulty = ${filters.difficulty ?? null})
        AND (${filters.circular ?? null}::boolean IS NULL OR r.circular = ${filters.circular ?? null})
        AND (${filters.family_friendly ?? null}::boolean IS NULL OR r.family_friendly = ${filters.family_friendly ?? null})
      ORDER BY r.published_at DESC NULLS LAST, r.name
      LIMIT ${filters.limit}
    `.execute(db);

    return { routes: result.rows };
  });

  app.get('/api/v1/public/routes/:slug', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const params = z.object({ slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_slug' });

    const routeResult = await sql<Record<string, unknown>>`
      SELECT r.id, r.slug, r.name, r.route_type, r.tags, r.short_description, r.description,
             r.distance_m, r.duration_minutes, r.elevation_gain_m, r.elevation_loss_m,
             r.min_altitude_m, r.max_altitude_m, r.difficulty, r.circular, r.family_friendly,
             r.validation_status, r.track_status, r.access_notes, r.safety_notes, r.water_notes,
             r.shade_level, r.mobile_coverage, r.recommended_seasons, r.restrictions,
             r.source_summary, r.last_verified_at, r.published_at,
             m.id AS municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             p.id AS place_id, p.name AS place_name, p.slug AS place_slug
      FROM routes r
      LEFT JOIN territory_municipalities m ON m.id = r.municipality_id
      LEFT JOIN territory_places p ON p.id = r.place_id
      WHERE r.slug = ${params.data.slug}
        AND r.status = 'published'
        AND r.track_status = 'validated'
      LIMIT 1
    `.execute(db);
    const route = routeResult.rows[0];
    if (!route) return reply.code(404).send({ error: 'route_not_found' });
    const routeId = route.id as string;
    const municipalityId = typeof route.municipality_id === 'string' ? route.municipality_id : null;
    const placeId = typeof route.place_id === 'string' ? route.place_id : null;
    const routeType = typeof route.route_type === 'string' ? route.route_type : null;

    const [trackResult, elevationResult, pointsResult, mediaResult, sourcesResult, segmentsResult, relatedResult, nearbyBusinessResult] = await Promise.all([
      sql<Record<string, unknown>>`
        SELECT id, version, geometry_type, original_format, distance_m, source_name, source_url,
               validation_status, validated_at, ST_AsGeoJSON(geometry)::json AS geometry, bbox
        FROM route_tracks
        WHERE route_id = ${routeId}::uuid AND validation_status = 'validated'
        LIMIT 1
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT es.sample_order, es.distance_m, es.elevation_m,
               es.grade_percent,
               CASE WHEN es.location IS NULL THEN NULL ELSE ST_Y(es.location) END AS latitude,
               CASE WHEN es.location IS NULL THEN NULL ELSE ST_X(es.location) END AS longitude
        FROM route_elevation_samples es
        JOIN route_tracks rt ON rt.id = es.route_track_id
        WHERE rt.route_id = ${routeId}::uuid AND rt.validation_status = 'validated'
        ORDER BY es.sample_order
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, name, kind, distance_m, elevation_m, description, safety_note, sort_order,
               ST_Y(location) AS latitude, ST_X(location) AS longitude
        FROM route_points
        WHERE route_id = ${routeId}::uuid AND active = true
        ORDER BY sort_order, distance_m NULLS LAST, name
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, route_point_id, kind, origin, url, poster_url, alt_text, caption, credit,
               source_url, license_notes, ai_generated, ai_provider, ai_model, ai_disclosure, sort_order
        FROM route_media
        WHERE route_id = ${routeId}::uuid AND active = true
        ORDER BY sort_order, created_at
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, source_name, external_id, source_url, source_kind, license_notes, retrieved_at
        FROM route_sources
        WHERE route_id = ${routeId}::uuid AND active = true
        ORDER BY source_kind, source_name
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT id, title, description, start_distance_m, end_distance_m, duration_minutes,
               difficulty, sort_order
        FROM route_segments
        WHERE route_id = ${routeId}::uuid
        ORDER BY sort_order, start_distance_m NULLS LAST
      `.execute(db),
      sql<Record<string, unknown>>`
        SELECT rr.id, rr.slug, rr.name, rr.route_type, rr.difficulty, rr.distance_m,
               rr.duration_minutes, rr.elevation_gain_m, rr.short_description,
               tm.name AS municipality_name,
               (
                 SELECT rm.url
                 FROM route_media rm
                 WHERE rm.route_id = rr.id AND rm.active = true
                   AND rm.kind IN ('hero_image', 'photo', 'thumbnail')
                 ORDER BY CASE rm.kind WHEN 'hero_image' THEN 0 WHEN 'thumbnail' THEN 1 ELSE 2 END,
                          rm.sort_order, rm.created_at
                 LIMIT 1
               ) AS hero_url
        FROM routes rr
        LEFT JOIN territory_municipalities tm ON tm.id = rr.municipality_id
        WHERE rr.id <> ${routeId}::uuid
          AND rr.status = 'published'
          AND rr.track_status = 'validated'
          AND (
            (${municipalityId}::uuid IS NOT NULL AND rr.municipality_id = ${municipalityId}::uuid)
            OR (${routeType}::text IS NOT NULL AND rr.route_type = ${routeType})
          )
        ORDER BY
          CASE WHEN ${municipalityId}::uuid IS NOT NULL AND rr.municipality_id = ${municipalityId}::uuid THEN 0 ELSE 1 END,
          CASE WHEN ${routeType}::text IS NOT NULL AND rr.route_type = ${routeType} THEN 0 ELSE 1 END,
          rr.published_at DESC NULLS LAST,
          rr.name
        LIMIT 4
      `.execute(db),
      sql<NearbyBusinessRow>`
        WITH validated_track AS (
          SELECT geometry
          FROM route_tracks
          WHERE route_id = ${routeId}::uuid AND validation_status = 'validated'
          LIMIT 1
        )
        SELECT b.id, b.slug, b.name, b.short_description, b.address,
               b.phone, b.whatsapp, b.website, b.logo_url, b.cover_image_url,
               b.verification_status,
               b.municipality_id, tm.name AS municipality_name, tm.slug AS municipality_slug,
               b.place_id, tp.name AS place_name, tp.slug AS place_slug,
               CASE WHEN b.location IS NULL THEN NULL ELSE ST_X(b.location) END AS longitude,
               CASE WHEN b.location IS NULL THEN NULL ELSE ST_Y(b.location) END AS latitude,
               CASE
                 WHEN b.location IS NOT NULL AND ST_DWithin(b.location::geography, vt.geometry::geography, ${nearbyBusinessRadiusM})
                   THEN ST_Distance(b.location::geography, vt.geometry::geography)
                 ELSE NULL
               END AS distance_m,
               CASE
                 WHEN b.location IS NOT NULL AND ST_DWithin(b.location::geography, vt.geometry::geography, ${nearbyBusinessRadiusM}) THEN 'spatial'
                 WHEN ${placeId}::uuid IS NOT NULL AND b.place_id = ${placeId}::uuid THEN 'same_place'
                 ELSE 'same_municipality'
               END AS distance_basis,
               (b.featured
                 AND (b.campaign_start IS NULL OR b.campaign_start <= now())
                 AND (b.campaign_end IS NULL OR b.campaign_end >= now())) AS active_featured,
               ((b.sponsored OR b.commercial_plan = 'sponsor')
                 AND (b.campaign_start IS NULL OR b.campaign_start <= now())
                 AND (b.campaign_end IS NULL OR b.campaign_end >= now())) AS active_sponsored,
               COALESCE((
                 SELECT jsonb_agg(
                   jsonb_build_object('slug', bc.slug, 'name', bc.name, 'primary', bcl.is_primary)
                   ORDER BY bcl.is_primary DESC, bc.sort_order, bc.name
                 )
                 FROM business_category_links bcl
                 JOIN business_categories bc ON bc.id = bcl.category_id AND bc.active = true
                 WHERE bcl.business_id = b.id
               ), '[]'::jsonb) AS categories
        FROM businesses b
        CROSS JOIN validated_track vt
        LEFT JOIN territory_municipalities tm ON tm.id = b.municipality_id
        LEFT JOIN territory_places tp ON tp.id = b.place_id
        WHERE b.status = 'published'
          AND (
            (b.location IS NOT NULL AND ST_DWithin(b.location::geography, vt.geometry::geography, ${nearbyBusinessRadiusM}))
            OR (${placeId}::uuid IS NOT NULL AND b.place_id = ${placeId}::uuid)
            OR (${municipalityId}::uuid IS NOT NULL AND b.municipality_id = ${municipalityId}::uuid)
          )
        ORDER BY
          CASE
            WHEN b.location IS NOT NULL AND ST_DWithin(b.location::geography, vt.geometry::geography, ${nearbyBusinessRadiusM}) THEN 0
            WHEN ${placeId}::uuid IS NOT NULL AND b.place_id = ${placeId}::uuid THEN 1
            ELSE 2
          END,
          CASE WHEN b.location IS NOT NULL AND ST_DWithin(b.location::geography, vt.geometry::geography, ${nearbyBusinessRadiusM})
            THEN ST_Distance(b.location::geography, vt.geometry::geography)
            ELSE NULL
          END NULLS LAST,
          b.priority DESC,
          b.name
        LIMIT 40
      `.execute(db),
    ]);

    const nearbyBusinesses = nearbyBusinessResult.rows.map(serializeNearbyBusiness);
    const organicBusinesses = nearbyBusinesses.filter((business) => !business.placement.sponsored).slice(0, 12);
    const sponsoredBusinesses = nearbyBusinesses.filter((business) => business.placement.sponsored).slice(0, 4);

    return {
      route,
      track: trackResult.rows[0] ?? null,
      elevation: elevationResult.rows,
      points: pointsResult.rows,
      media: mediaResult.rows,
      sources: sourcesResult.rows,
      segments: segmentsResult.rows,
      related: relatedResult.rows,
      nearbyBusinesses: {
        radiusMeters: nearbyBusinessRadiusM,
        organic: organicBusinesses,
        sponsored: sponsoredBusinesses,
        disclosure: 'Los negocios patrocinados se muestran por separado y no modifican los datos técnicos, de seguridad ni las fuentes de la ruta.',
        distanceNote: 'La distancia espacial mide la separación más corta entre la ubicación del negocio y el trazado validado; no representa distancia por carretera.',
      },
    };
  });
}
