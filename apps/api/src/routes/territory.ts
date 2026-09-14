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

type MunicipalityDirectoryRow = {
  id: string;
  ine_code: string;
  aemet_code: string | null;
  name: string;
  slug: string;
  province_name: string;
  center: unknown | null;
  official_website: string;
  electronic_office_url: string | null;
  transparency_url: string | null;
  tourism_url: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  postal_code: string | null;
  source_url: string;
  verified_at: string;
  places: unknown;
  content_counts: unknown;
  related_content?: unknown;
};

const publishedContentWindow = sql`c.status = 'published'
  AND (c.starts_at IS NULL OR c.starts_at <= now())
  AND (c.ends_at IS NULL OR c.ends_at >= now())`;

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

  app.get('/api/v1/public/territory/municipalities', async (_request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const result = await sql<MunicipalityDirectoryRow>`
      SELECT m.id, m.ine_code, m.aemet_code, m.name, m.slug, m.province_name,
             CASE WHEN m.center IS NULL THEN NULL ELSE ST_AsGeoJSON(m.center)::json END AS center,
             d.official_website, d.electronic_office_url, d.transparency_url, d.tourism_url,
             d.phone, d.email, d.address, d.postal_code, d.source_url,
             d.verified_at::text,
             COALESCE((
               SELECT json_agg(json_build_object('id', p.id, 'name', p.name, 'slug', p.slug, 'kind', p.kind) ORDER BY p.is_default_for_municipality DESC, p.name)
               FROM territory_places p
               WHERE p.municipality_id = m.id AND p.public_enabled = true
             ), '[]'::json) AS places,
             json_build_object(
               'place', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'place' AND c.content_json->>'municipality_id' = m.id::text),
               'mill', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'mill' AND c.content_json->>'municipality_id' = m.id::text),
               'directory', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'directory' AND c.content_json->>'municipality_id' = m.id::text),
               'news', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'news' AND c.content_json->>'municipality_id' = m.id::text),
               'event', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'event' AND c.content_json->>'municipality_id' = m.id::text)
             ) AS content_counts
      FROM territory_municipalities m
      JOIN territory_municipality_directory d ON d.municipality_id = m.id
      WHERE m.active = true AND d.public_enabled = true
      ORDER BY m.name
    `.execute(database);
    return { municipalities: result.rows };
  });

  app.get('/api/v1/public/territory/municipalities/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const slug = (request.params as { slug?: string }).slug?.trim().toLowerCase();
    if (!slug || !placeSlugPattern.test(slug)) return reply.code(400).send({ error: 'invalid_municipality_slug' });

    const result = await sql<MunicipalityDirectoryRow>`
      SELECT m.id, m.ine_code, m.aemet_code, m.name, m.slug, m.province_name,
             CASE WHEN m.center IS NULL THEN NULL ELSE ST_AsGeoJSON(m.center)::json END AS center,
             d.official_website, d.electronic_office_url, d.transparency_url, d.tourism_url,
             d.phone, d.email, d.address, d.postal_code, d.source_url,
             d.verified_at::text,
             COALESCE((
               SELECT json_agg(json_build_object('id', p.id, 'name', p.name, 'slug', p.slug, 'kind', p.kind) ORDER BY p.is_default_for_municipality DESC, p.name)
               FROM territory_places p
               WHERE p.municipality_id = m.id AND p.public_enabled = true
             ), '[]'::json) AS places,
             json_build_object(
               'place', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'place' AND c.content_json->>'municipality_id' = m.id::text),
               'mill', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'mill' AND c.content_json->>'municipality_id' = m.id::text),
               'directory', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'directory' AND c.content_json->>'municipality_id' = m.id::text),
               'news', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'news' AND c.content_json->>'municipality_id' = m.id::text),
               'event', (SELECT count(*)::int FROM cms_entries c WHERE ${publishedContentWindow} AND c.type = 'event' AND c.content_json->>'municipality_id' = m.id::text)
             ) AS content_counts,
             COALESCE((
               SELECT json_agg(json_build_object(
                 'id', c.id,
                 'type', c.type,
                 'slug', c.slug,
                 'title', c.title,
                 'summary', c.summary,
                 'content_json', c.content_json,
                 'featured', c.featured,
                 'starts_at', c.starts_at,
                 'ends_at', c.ends_at,
                 'media_url', c.media_url,
                 'external_url', c.external_url,
                 'sort_order', c.sort_order,
                 'published_at', c.published_at
               ) ORDER BY c.featured DESC, c.sort_order DESC, c.published_at DESC NULLS LAST, c.updated_at DESC)
               FROM cms_entries c
               WHERE ${publishedContentWindow}
                 AND c.type IN ('place', 'mill', 'directory', 'news', 'event')
                 AND c.content_json->>'municipality_id' = m.id::text
             ), '[]'::json) AS related_content
      FROM territory_municipalities m
      JOIN territory_municipality_directory d ON d.municipality_id = m.id
      WHERE m.slug = ${slug} AND m.active = true AND d.public_enabled = true
      LIMIT 1
    `.execute(database);

    const municipality = result.rows[0];
    if (!municipality) return reply.code(404).send({ error: 'municipality_not_found' });
    return { municipality };
  });
}
