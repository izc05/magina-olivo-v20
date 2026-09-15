import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const placeKindSchema = z.enum(['municipal_seat', 'locality', 'hamlet', 'other']);
const nullableUrl = z.string().url().startsWith('https://').nullable();
const nullableText = z.string().trim().max(500).nullable();

const updatePlaceSchema = z.object({
  public_enabled: z.boolean().optional(),
  kind: placeKindSchema.optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'at_least_one_change_required' });

const updateMunicipalityDirectorySchema = z.object({
  official_website: z.string().url().startsWith('https://').optional(),
  electronic_office_url: nullableUrl.optional(),
  transparency_url: nullableUrl.optional(),
  tourism_url: nullableUrl.optional(),
  phone: nullableText.optional(),
  email: z.string().email().nullable().optional(),
  address: nullableText.optional(),
  postal_code: z.string().trim().max(10).nullable().optional(),
  source_url: z.string().url().startsWith('https://').optional(),
  verified_at: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  public_enabled: z.boolean().optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'at_least_one_change_required' });

type MunicipalityDirectoryProfile = {
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
  public_enabled: boolean;
};

type MunicipalityRow = {
  id: string;
  ine_code: string;
  aemet_code: string | null;
  name: string;
  slug: string;
  province_code: string;
  province_name: string;
  active: boolean;
  weather_enabled: boolean;
  center: unknown | null;
  place_count: number;
  public_place_count: number;
  directory: MunicipalityDirectoryProfile | null;
};

type PlaceRow = {
  id: string;
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  name: string;
  slug: string;
  kind: 'municipal_seat' | 'locality' | 'hamlet' | 'other';
  center: unknown | null;
  is_default_for_municipality: boolean;
  public_enabled: boolean;
  hero_asset_key: string | null;
  field_count: number;
  editorial_count: number;
};

async function territoryCatalog(database: DatabaseClient) {
  const [municipalities, places] = await Promise.all([
    sql<MunicipalityRow>`
      SELECT m.id, m.ine_code, m.aemet_code, m.name, m.slug,
             m.province_code, m.province_name, m.active, m.weather_enabled,
             CASE WHEN m.center IS NULL THEN NULL ELSE ST_AsGeoJSON(m.center)::json END AS center,
             count(p.id)::int AS place_count,
             count(p.id) FILTER (WHERE p.public_enabled = true)::int AS public_place_count,
             CASE WHEN d.municipality_id IS NULL THEN NULL ELSE json_build_object(
               'official_website', d.official_website,
               'electronic_office_url', d.electronic_office_url,
               'transparency_url', d.transparency_url,
               'tourism_url', d.tourism_url,
               'phone', d.phone,
               'email', d.email,
               'address', d.address,
               'postal_code', d.postal_code,
               'source_url', d.source_url,
               'verified_at', d.verified_at,
               'public_enabled', d.public_enabled
             ) END AS directory
      FROM territory_municipalities m
      LEFT JOIN territory_places p ON p.municipality_id = m.id
      LEFT JOIN territory_municipality_directory d ON d.municipality_id = m.id
      GROUP BY m.id, d.municipality_id
      ORDER BY m.name
    `.execute(database),
    sql<PlaceRow>`
      SELECT p.id, p.municipality_id, m.name AS municipality_name, m.slug AS municipality_slug,
             p.name, p.slug, p.kind, p.is_default_for_municipality, p.public_enabled, p.hero_asset_key,
             CASE WHEN p.center IS NULL THEN NULL ELSE ST_AsGeoJSON(p.center)::json END AS center,
             (SELECT count(*)::int FROM fields f WHERE f.place_id = p.id AND f.status = 'active') AS field_count,
             (SELECT count(*)::int
                FROM cms_entries c
               WHERE c.status <> 'archived'
                 AND c.type IN ('place', 'mill', 'directory')
                 AND c.content_json ->> 'territory_place_id' = p.id::text) AS editorial_count
      FROM territory_places p
      JOIN territory_municipalities m ON m.id = p.municipality_id
      ORDER BY m.name, p.is_default_for_municipality DESC, p.name
    `.execute(database),
  ]);

  return { municipalities: municipalities.rows, places: places.rows };
}

export function registerAdminTerritoryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/territory/catalog', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    return territoryCatalog(auth.database);
  });

  app.patch('/api/v1/admin/territory/places/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;

    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_place_id' });
    const input = parseBody(updatePlaceSchema, request.body, reply);
    if (!input) return;

    const existing = await auth.database.selectFrom('territory_places')
      .select(['id', 'name', 'slug', 'kind', 'public_enabled'])
      .where('id', '=', params.data.id)
      .executeTakeFirst();
    if (!existing) return reply.code(404).send({ error: 'territory_place_not_found' });

    const nextKind = input.kind ?? existing.kind;
    const nextVisibility = input.public_enabled ?? existing.public_enabled;
    await auth.database.updateTable('territory_places').set({
      kind: nextKind,
      public_enabled: nextVisibility,
      updated_at: new Date(),
    }).where('id', '=', existing.id).execute();

    await auditAdminAction(auth.database, auth.access, 'territory.place_changed', 'territory_place', existing.id, {
      name: existing.name,
      slug: existing.slug,
      from: { kind: existing.kind, public_enabled: existing.public_enabled },
      to: { kind: nextKind, public_enabled: nextVisibility },
    });

    const catalog = await territoryCatalog(auth.database);
    return { place: catalog.places.find((place) => place.id === existing.id) ?? null };
  });

  app.patch('/api/v1/admin/territory/municipalities/:id/directory', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_municipality_id' });
    const input = parseBody(updateMunicipalityDirectorySchema, request.body, reply);
    if (!input) return;

    const municipality = await auth.database.selectFrom('territory_municipalities')
      .select(['id', 'name', 'slug'])
      .where('id', '=', params.data.id)
      .executeTakeFirst();
    if (!municipality) return reply.code(404).send({ error: 'territory_municipality_not_found' });

    const existingResult = await sql<MunicipalityDirectoryProfile>`
      SELECT official_website, electronic_office_url, transparency_url, tourism_url,
             phone, email, address, postal_code, source_url, verified_at::text, public_enabled
      FROM territory_municipality_directory
      WHERE municipality_id = ${municipality.id}
      LIMIT 1
    `.execute(auth.database);
    const existing = existingResult.rows[0];
    if (!existing) return reply.code(409).send({ error: 'municipality_directory_not_seeded' });

    const next: MunicipalityDirectoryProfile = {
      official_website: input.official_website ?? existing.official_website,
      electronic_office_url: input.electronic_office_url === undefined ? existing.electronic_office_url : input.electronic_office_url,
      transparency_url: input.transparency_url === undefined ? existing.transparency_url : input.transparency_url,
      tourism_url: input.tourism_url === undefined ? existing.tourism_url : input.tourism_url,
      phone: input.phone === undefined ? existing.phone : input.phone,
      email: input.email === undefined ? existing.email : input.email,
      address: input.address === undefined ? existing.address : input.address,
      postal_code: input.postal_code === undefined ? existing.postal_code : input.postal_code,
      source_url: input.source_url ?? existing.source_url,
      verified_at: input.verified_at ?? existing.verified_at,
      public_enabled: input.public_enabled ?? existing.public_enabled,
    };

    await sql`
      UPDATE territory_municipality_directory
      SET official_website = ${next.official_website},
          electronic_office_url = ${next.electronic_office_url},
          transparency_url = ${next.transparency_url},
          tourism_url = ${next.tourism_url},
          phone = ${next.phone}, email = ${next.email}, address = ${next.address},
          postal_code = ${next.postal_code}, source_url = ${next.source_url},
          verified_at = ${next.verified_at}::date, public_enabled = ${next.public_enabled},
          updated_at = now()
      WHERE municipality_id = ${municipality.id}
    `.execute(auth.database);

    await auditAdminAction(auth.database, auth.access, 'territory.municipality_directory_changed', 'territory_municipality', municipality.id, {
      name: municipality.name,
      slug: municipality.slug,
      from: existing,
      to: next,
    });

    const catalog = await territoryCatalog(auth.database);
    return { municipality: catalog.municipalities.find((item) => item.id === municipality.id) ?? null };
  });
}
