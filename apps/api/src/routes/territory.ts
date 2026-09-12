import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireDatabase } from '../http/helpers.js';

const placeSlugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

type TerritoryPlaceRow = {
  id: string;
  name: string;
  slug: string;
  kind: string;
  hero_asset_key: string | null;
  center: unknown | null;
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  ine_code: string;
  aemet_code: string | null;
  province_name: string;
};

export function registerTerritoryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/territory/places', async (_request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const result = await sql<TerritoryPlaceRow>`
      SELECT p.id, p.name, p.slug, p.kind, p.hero_asset_key,
             CASE WHEN p.center IS NULL THEN NULL ELSE ST_AsGeoJSON(p.center)::json END AS center,
             m.id AS municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             m.ine_code, m.aemet_code, m.province_name
      FROM territory_places p
      JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.public_enabled = true AND m.active = true
      ORDER BY p.name
    `.execute(database);
    return { places: result.rows };
  });

  app.get('/api/v1/public/territory/places/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const slug = (request.params as { slug?: string }).slug?.trim().toLowerCase();
    if (!slug || !placeSlugPattern.test(slug)) return reply.code(400).send({ error: 'invalid_place_slug' });

    const result = await sql<TerritoryPlaceRow>`
      SELECT p.id, p.name, p.slug, p.kind, p.hero_asset_key,
             CASE WHEN p.center IS NULL THEN NULL ELSE ST_AsGeoJSON(p.center)::json END AS center,
             m.id AS municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             m.ine_code, m.aemet_code, m.province_name
      FROM territory_places p
      JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.slug = ${slug}
        AND p.public_enabled = true
        AND m.active = true
      LIMIT 1
    `.execute(database);

    const place = result.rows[0];
    if (!place) return reply.code(404).send({ error: 'place_not_found' });
    return { place };
  });
}
