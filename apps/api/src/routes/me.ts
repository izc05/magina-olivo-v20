import type { FastifyInstance } from 'fastify';
import { updateMyPreferencesSchema, updateMyProfileSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

async function readMe(db: DatabaseClient, userId: string) {
  const user = await db.selectFrom('users')
    .select(['id', 'display_name', 'primary_email', 'avatar_url', 'status'])
    .where('id', '=', userId)
    .executeTakeFirstOrThrow();

  const profile = await db.selectFrom('user_profiles')
    .selectAll()
    .where('user_id', '=', userId)
    .executeTakeFirst();

  const preferences = await db.selectFrom('user_preferences')
    .selectAll()
    .where('user_id', '=', userId)
    .executeTakeFirst();

  return {
    user: {
      id: user.id,
      display_name: profile?.display_name_override ?? user.display_name,
      provider_display_name: user.display_name,
      primary_email: user.primary_email,
      avatar_url: user.avatar_url,
    },
    profile: {
      municipality: profile?.municipality ?? null,
      bio: profile?.bio ?? null,
      public_role: profile?.public_role ?? null,
      visibility: profile?.visibility ?? 'private',
    },
    preferences: {
      theme: preferences?.theme ?? 'system',
      unit_system: preferences?.unit_system ?? 'metric',
      preferred_municipality: preferences?.preferred_municipality ?? null,
      locale: preferences?.locale ?? 'es-ES',
      community_notifications: preferences?.community_notifications ?? true,
      weather_alerts: preferences?.weather_alerts ?? true,
    },
  };
}

export function registerMeRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/me', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    return readMe(database, userId);
  });

  app.patch('/api/v1/me/profile', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const input = parseBody(updateMyProfileSchema, request.body, reply);
    if (!input) return;

    const existing = await database.selectFrom('user_profiles')
      .select(['user_id'])
      .where('user_id', '=', userId)
      .executeTakeFirst();

    const values = {
      ...(input.display_name !== undefined ? { display_name_override: input.display_name } : {}),
      ...(input.municipality !== undefined ? { municipality: input.municipality } : {}),
      ...(input.bio !== undefined ? { bio: input.bio } : {}),
      ...(input.public_role !== undefined ? { public_role: input.public_role } : {}),
      ...(input.visibility !== undefined ? { visibility: input.visibility } : {}),
      updated_at: new Date(),
    };

    if (existing) {
      await database.updateTable('user_profiles').set(values).where('user_id', '=', userId).execute();
    } else {
      await database.insertInto('user_profiles').values({
        user_id: userId,
        display_name_override: input.display_name ?? null,
        municipality: input.municipality ?? null,
        bio: input.bio ?? null,
        public_role: input.public_role ?? null,
        visibility: input.visibility ?? 'private',
        updated_at: new Date(),
      }).execute();
    }

    return readMe(database, userId);
  });

  app.patch('/api/v1/me/preferences', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const input = parseBody(updateMyPreferencesSchema, request.body, reply);
    if (!input) return;

    const existing = await database.selectFrom('user_preferences')
      .select(['user_id'])
      .where('user_id', '=', userId)
      .executeTakeFirst();

    const values = {
      ...input,
      updated_at: new Date(),
    };

    if (existing) {
      await database.updateTable('user_preferences').set(values).where('user_id', '=', userId).execute();
    } else {
      await database.insertInto('user_preferences').values({
        user_id: userId,
        theme: input.theme ?? 'system',
        unit_system: input.unit_system ?? 'metric',
        preferred_municipality: input.preferred_municipality ?? null,
        locale: input.locale ?? 'es-ES',
        community_notifications: input.community_notifications ?? true,
        weather_alerts: input.weather_alerts ?? true,
        updated_at: new Date(),
      }).execute();
    }

    return readMe(database, userId);
  });
}
