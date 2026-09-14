import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

export function registerMyTownRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/me/towns', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const rows = await database
      .selectFrom('user_followed_towns as f')
      .innerJoin('territory_municipalities as m', 'm.id', 'f.municipality_id')
      .select(['m.id', 'm.slug', 'm.name', 'f.created_at'])
      .where('f.user_id', '=', userId)
      .where('m.active', '=', true)
      .orderBy('m.name', 'asc')
      .execute();

    return { towns: rows };
  });

  app.put<{ Params: { slug: string } }>('/api/v1/me/towns/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const slug = request.params.slug.trim().toLowerCase();
    const municipality = await database
      .selectFrom('territory_municipalities')
      .select(['id', 'slug', 'name'])
      .where('slug', '=', slug)
      .where('active', '=', true)
      .executeTakeFirst();

    if (!municipality) return reply.code(404).send({ error: 'municipality_not_found' });

    await database
      .insertInto('user_followed_towns')
      .values({ user_id: userId, municipality_id: municipality.id })
      .onConflict((oc) => oc.columns(['user_id', 'municipality_id']).doNothing())
      .execute();

    return { town: municipality, followed: true };
  });

  app.delete<{ Params: { slug: string } }>('/api/v1/me/towns/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const slug = request.params.slug.trim().toLowerCase();
    const municipality = await database
      .selectFrom('territory_municipalities')
      .select(['id'])
      .where('slug', '=', slug)
      .executeTakeFirst();

    if (!municipality) return reply.code(404).send({ error: 'municipality_not_found' });

    await database
      .deleteFrom('user_followed_towns')
      .where('user_id', '=', userId)
      .where('municipality_id', '=', municipality.id)
      .execute();

    return reply.code(204).send();
  });
}
