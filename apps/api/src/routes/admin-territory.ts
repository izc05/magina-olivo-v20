import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const placeKindSchema = z.enum(['municipal_seat', 'locality', 'hamlet', 'other']);

const updatePlaceSchema = z.object({
  public_enabled: z.boolean().optional(),
  kind: placeKindSchema.optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'at_least_one_change_required' });

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
             count(p.id) FILTER (WHERE p.public_enabled = true)::int AS public_place_count
      FROM territory_municipalities m
      LEFT JOIN territory_places p ON p.municipality_id = m.id
      GROUP BY m.id
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
}
