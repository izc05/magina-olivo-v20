import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { googleSignInSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';
import type { GoogleIdentityVerifier } from '../auth/google.js';
import { GoogleIdentityNotConfiguredError } from '../auth/google.js';
import { issueSession, revokeSession, SESSION_COOKIE_NAME, SESSION_TTL_DAYS } from '../auth/session.js';

function cookieOptions(expires?: Date) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production' || process.env.AUTH_COOKIE_SECURE === 'true',
    sameSite: 'lax' as const,
    path: '/',
    maxAge: SESSION_TTL_DAYS * 24 * 60 * 60,
    expires,
  };
}

async function activeMemberships(db: DatabaseClient, userId: string) {
  return db.selectFrom('workspace_memberships as wm')
    .innerJoin('workspaces as w', 'w.id', 'wm.workspace_id')
    .select([
      'w.id as workspace_id',
      'w.name as workspace_name',
      'w.type as workspace_type',
      'wm.role',
    ])
    .where('wm.user_id', '=', userId)
    .where('wm.status', '=', 'active')
    .orderBy('wm.created_at', 'asc')
    .execute();
}

export function registerAuthRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  googleVerifier: GoogleIdentityVerifier,
) {
  app.post('/api/v1/auth/google', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const input = parseBody(googleSignInSchema, request.body, reply);
    if (!input) return;

    let claims;
    try {
      claims = await googleVerifier.verify(input.credential);
    } catch (error) {
      if (error instanceof GoogleIdentityNotConfiguredError) {
        return reply.code(503).send({ error: 'google_auth_not_configured' });
      }
      request.log.warn({ err: error }, 'Google credential verification failed');
      return reply.code(401).send({ error: 'invalid_google_credential' });
    }

    const existingIdentity = await database.selectFrom('auth_identities as ai')
      .innerJoin('users as u', 'u.id', 'ai.user_id')
      .select([
        'ai.id as identity_id',
        'ai.user_id',
        'u.display_name',
        'u.primary_email',
        'u.avatar_url',
        'u.status',
      ])
      .where('ai.provider', '=', 'google')
      .where('ai.provider_subject', '=', claims.subject)
      .executeTakeFirst();

    let userId: string;
    let created = false;

    if (existingIdentity) {
      if (existingIdentity.status !== 'active') {
        return reply.code(403).send({ error: 'user_not_active' });
      }
      userId = existingIdentity.user_id;
      const now = new Date();
      await database.transaction().execute(async (trx) => {
        await trx.updateTable('auth_identities').set({
          email: claims.email,
          email_verified: claims.emailVerified,
          provider_data: {
            hosted_domain: claims.hostedDomain,
            given_name: claims.givenName,
            family_name: claims.familyName,
          },
          updated_at: now,
          last_seen_at: now,
        }).where('id', '=', existingIdentity.identity_id).execute();

        await trx.updateTable('users').set({
          display_name: claims.displayName,
          avatar_url: claims.pictureUrl,
          updated_at: now,
          last_login_at: now,
        }).where('id', '=', userId).execute();
      });
    } else {
      if (claims.email) {
        const conflictingUser = await database.selectFrom('users')
          .select(['id'])
          .where('status', '<>', 'deleted')
          .where(sql<boolean>`lower(primary_email) = lower(${claims.email})`)
          .executeTakeFirst();
        if (conflictingUser) {
          return reply.code(409).send({ error: 'account_link_required' });
        }
      }

      const createdEntities = await database.transaction().execute(async (trx) => {
        const now = new Date();
        const user = await trx.insertInto('users').values({
          primary_email: claims.emailVerified ? claims.email : null,
          display_name: claims.displayName,
          avatar_url: claims.pictureUrl,
          status: 'active',
          last_login_at: now,
        }).returning(['id']).executeTakeFirstOrThrow();

        await trx.insertInto('auth_identities').values({
          user_id: user.id,
          provider: 'google',
          provider_subject: claims.subject,
          email: claims.email,
          email_verified: claims.emailVerified,
          provider_data: {
            hosted_domain: claims.hostedDomain,
            given_name: claims.givenName,
            family_name: claims.familyName,
          },
          updated_at: now,
          last_seen_at: now,
        }).execute();

        const workspace = await trx.insertInto('workspaces').values({
          name: 'Mi campo',
          type: 'family',
          updated_at: now,
        }).returning(['id']).executeTakeFirstOrThrow();

        await trx.insertInto('workspace_memberships').values({
          workspace_id: workspace.id,
          user_id: user.id,
          role: 'owner',
          status: 'active',
          invited_by: null,
          updated_at: now,
        }).execute();

        return { userId: user.id };
      });

      userId = createdEntities.userId;
      created = true;
    }

    const user = await database.selectFrom('users')
      .select(['id', 'display_name', 'primary_email', 'avatar_url'])
      .where('id', '=', userId)
      .executeTakeFirstOrThrow();
    const memberships = await activeMemberships(database, userId);
    const session = await issueSession(database, userId, request.headers['user-agent'] ?? null);

    reply.setCookie(SESSION_COOKIE_NAME, session.token, cookieOptions(session.expiresAt));
    return reply.code(created ? 201 : 200).send({
      created,
      user,
      workspaces: memberships,
      selected_workspace_id: memberships[0]?.workspace_id ?? null,
      session_expires_at: session.expiresAt.toISOString(),
    });
  });

  app.get('/api/v1/auth/session', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const user = await database.selectFrom('users')
      .select(['id', 'display_name', 'primary_email', 'avatar_url', 'status'])
      .where('id', '=', userId)
      .executeTakeFirst();
    if (!user || user.status !== 'active') return reply.code(401).send({ error: 'invalid_session' });

    const memberships = await activeMemberships(database, userId);
    return { user, workspaces: memberships };
  });

  app.post('/api/v1/auth/logout', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const token = request.cookies?.[SESSION_COOKIE_NAME];
    if (token) await revokeSession(database, token);
    reply.clearCookie(SESSION_COOKIE_NAME, { path: '/' });
    return reply.code(204).send();
  });
}
