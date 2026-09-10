import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const placeSlugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

type MunicipalityTarget = {
  municipality_id: string;
  municipality_name: string;
  aemet_code: string | null;
};

async function respondWithForecast(
  database: DatabaseClient,
  provider: MunicipalityWeatherProvider,
  target: MunicipalityTarget,
  reply: Parameters<FastifyInstance['get']>[1] extends (...args: infer A) => unknown ? A[1] : never,
) {
  if (!target.aemet_code) return reply.code(409).send({ error: 'weather_not_configured_for_municipality' });
  try {
    const result = await getCachedMunicipalityForecast(database, provider, target.municipality_id, target.aemet_code);
    return reply.send({
      municipality: {
        id: target.municipality_id,
        name: target.municipality_name,
        aemet_code: target.aemet_code,
      },
      forecast: result.forecast,
      cache_status: result.cacheStatus,
      fetched_at: result.fetchedAt,
      stale: result.cacheStatus === 'stale',
    });
  } catch (error) {
    reply.log?.error?.({ err: error }, 'Unable to load AEMET forecast');
    return reply.code(502).send({ error: 'weather_upstream_unavailable' });
  }
}

export function registerWeatherRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  provider: MunicipalityWeatherProvider,
) {
  app.get('/api/v1/public/weather/places/:slug/daily', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const slug = (request.params as { slug?: string }).slug?.trim().toLowerCase();
    if (!slug || !placeSlugPattern.test(slug)) return reply.code(400).send({ error: 'invalid_place_slug' });

    const result = await sql<MunicipalityTarget>`
      SELECT m.id AS municipality_id, m.name AS municipality_name, m.aemet_code
      FROM territory_places p
      JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.slug = ${slug}
        AND p.public_enabled = true
        AND m.active = true
        AND m.weather_enabled = true
      LIMIT 1
    `.execute(database);
    const target = result.rows[0];
    if (!target) return reply.code(404).send({ error: 'place_not_found' });
    return respondWithForecast(database, provider, target, reply);
  });

  app.get('/api/v1/fields/:fieldId/weather/daily', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId) return reply.code(404).send({ error: 'field_not_found' });

    const result = await sql<MunicipalityTarget>`
      SELECT m.id AS municipality_id, m.name AS municipality_name, m.aemet_code
      FROM fields f
      LEFT JOIN territory_municipalities m ON m.id = f.municipality_id
      WHERE f.id = ${fieldId}::uuid
        AND f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
      LIMIT 1
    `.execute(database);
    const target = result.rows[0];
    if (!target) return reply.code(404).send({ error: 'field_not_found' });
    if (!target.municipality_id) return reply.code(409).send({ error: 'field_municipality_not_resolved' });
    return respondWithForecast(database, provider, target, reply);
  });
}
