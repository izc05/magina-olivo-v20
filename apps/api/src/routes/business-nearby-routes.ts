import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireDatabase } from '../http/helpers.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const radiusMeters = 15_000;

type NearbyRouteRow = {
  id: string;
  slug: string;
  name: string;
  route_type: string;
  difficulty: string | null;
  distance_m: number | null;
  duration_minutes: number | null;
  elevation_gain_m: number | null;
  short_description: string | null;
  municipality_name: string | null;
  place_name: string | null;
  hero_url: string | null;
  business_distance_m: number | null;
  distance_basis: 'spatial' | 'same_place' | 'same_municipality';
};

function serialize(row: NearbyRouteRow) {
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    routeType: row.route_type,
    difficulty: row.difficulty,
    distanceMeters: row.distance_m === null ? null : Number(row.distance_m),
    durationMinutes: row.duration_minutes === null ? null : Number(row.duration_minutes),
    elevationGainMeters: row.elevation_gain_m === null ? null : Number(row.elevation_gain_m),
    shortDescription: row.short_description,
    territory: {
      municipalityName: row.municipality_name,
      placeName: row.place_name,
    },
    heroUrl: row.hero_url,
    businessDistanceMeters: row.business_distance_m === null ? null : Number(row.business_distance_m),
    distanceBasis: row.distance_basis,
  };
}

export function registerBusinessNearbyRouteRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/businesses/:slug/nearby-routes', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;

    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_slug' });

    const businessResult = await sql<{
      id: string;
      municipality_id: string | null;
      place_id: string | null;
      has_location: boolean;
    }>`
      SELECT id, municipality_id, place_id, (location IS NOT NULL) AS has_location
      FROM businesses
      WHERE slug = ${params.data.slug} AND status = 'published'
      LIMIT 1
    `.execute(database);

    const business = businessResult.rows[0];
    if (!business) return reply.code(404).send({ error: 'business_not_found' });

    let rows: NearbyRouteRow[] = [];

    if (business.has_location) {
      const result = await sql<NearbyRouteRow>`
        SELECT r.id, r.slug, r.name, r.route_type, r.difficulty, r.distance_m,
               r.duration_minutes, r.elevation_gain_m, r.short_description,
               m.name AS municipality_name, p.name AS place_name,
               (
                 SELECT rm.url
                 FROM route_media rm
                 WHERE rm.route_id = r.id AND rm.active = true
                   AND rm.kind IN ('hero_image', 'thumbnail', 'photo')
                 ORDER BY CASE rm.kind WHEN 'hero_image' THEN 0 WHEN 'thumbnail' THEN 1 ELSE 2 END,
                          rm.sort_order, rm.created_at
                 LIMIT 1
               ) AS hero_url,
               MIN(ST_Distance(rt.geometry::geography, b.location::geography)) AS business_distance_m,
               'spatial'::text AS distance_basis
        FROM businesses b
        JOIN routes r ON r.status = 'published' AND r.track_status = 'validated'
        JOIN route_tracks rt ON rt.route_id = r.id AND rt.validation_status = 'validated'
        LEFT JOIN territory_municipalities m ON m.id = r.municipality_id
        LEFT JOIN territory_places p ON p.id = r.place_id
        WHERE b.id = ${business.id}::uuid
          AND b.location IS NOT NULL
          AND ST_DWithin(rt.geometry::geography, b.location::geography, ${radiusMeters})
        GROUP BY r.id, m.name, p.name
        ORDER BY business_distance_m ASC, r.name
        LIMIT 8
      `.execute(database);
      rows = result.rows;
    } else if (business.place_id || business.municipality_id) {
      const result = await sql<NearbyRouteRow>`
        SELECT r.id, r.slug, r.name, r.route_type, r.difficulty, r.distance_m,
               r.duration_minutes, r.elevation_gain_m, r.short_description,
               m.name AS municipality_name, p.name AS place_name,
               (
                 SELECT rm.url
                 FROM route_media rm
                 WHERE rm.route_id = r.id AND rm.active = true
                   AND rm.kind IN ('hero_image', 'thumbnail', 'photo')
                 ORDER BY CASE rm.kind WHEN 'hero_image' THEN 0 WHEN 'thumbnail' THEN 1 ELSE 2 END,
                          rm.sort_order, rm.created_at
                 LIMIT 1
               ) AS hero_url,
               NULL::double precision AS business_distance_m,
               CASE
                 WHEN ${business.place_id}::uuid IS NOT NULL AND r.place_id = ${business.place_id}::uuid THEN 'same_place'
                 ELSE 'same_municipality'
               END::text AS distance_basis
        FROM routes r
        LEFT JOIN territory_municipalities m ON m.id = r.municipality_id
        LEFT JOIN territory_places p ON p.id = r.place_id
        WHERE r.status = 'published'
          AND r.track_status = 'validated'
          AND (
            (${business.place_id}::uuid IS NOT NULL AND r.place_id = ${business.place_id}::uuid)
            OR (${business.municipality_id}::uuid IS NOT NULL AND r.municipality_id = ${business.municipality_id}::uuid)
          )
        ORDER BY
          CASE WHEN ${business.place_id}::uuid IS NOT NULL AND r.place_id = ${business.place_id}::uuid THEN 0 ELSE 1 END,
          r.name
        LIMIT 8
      `.execute(database);
      rows = result.rows;
    }

    return {
      routes: rows.map(serialize),
      meta: {
        radiusMeters: business.has_location ? radiusMeters : null,
        distanceNote: business.has_location
          ? 'La distancia se calcula en línea recta desde la ubicación publicada de la empresa hasta el trazado validado. No representa distancia por carretera.'
          : 'La empresa no tiene coordenadas publicadas; las rutas se relacionan solo por localidad o municipio y no se muestra una distancia inventada.',
      },
    };
  });
}
