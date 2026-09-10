import { createHash, randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { sql } from 'kysely';
import {
  pushTestSchema,
  registerPushSubscriptionSchema,
  unregisterPushSubscriptionSchema,
} from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, parseBody, requireAuthenticatedUser, requireContext, requireDatabase } from '../http/helpers.js';
import type { NotificationDispatchQueuePort } from '../notifications/port.js';

function endpointHash(endpoint: string) {
  return createHash('sha256').update(endpoint, 'utf8').digest('hex');
}

function requireCookieSession(request: FastifyRequest, reply: Parameters<typeof requireAuthenticatedUser>[1]) {
  if (!request.authSessionId) {
    void reply.code(401).send({
      error: 'cookie_session_required',
      message: 'Web Push subscriptions require a real authenticated cookie session.',
    });
    return null;
  }
  return request.authSessionId;
}

function expirationDate(value: number | null | undefined) {
  if (value == null) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function registerPushRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  queue: NotificationDispatchQueuePort,
  publicVapidKey: string | null,
) {
  app.get('/api/v1/push/config', async () => ({
    configured: Boolean(publicVapidKey),
    public_key: publicVapidKey,
    semantics: 'explicit_opt_in_web_push',
  }));

  app.get('/api/v1/push/status', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const result = await sql<{ active_count: number | string }>`
      SELECT count(*)::int AS active_count
      FROM push_subscriptions ps
      INNER JOIN user_sessions s ON s.id = ps.session_id
      WHERE ps.user_id = ${userId}
        AND ps.status = 'active'
        AND ps.revoked_at IS NULL
        AND (ps.expiration_time IS NULL OR ps.expiration_time > now())
        AND s.revoked_at IS NULL
        AND s.expires_at > now()
    `.execute(database);

    return {
      configured: Boolean(publicVapidKey),
      active_subscriptions: Number(result.rows[0]?.active_count ?? 0),
    };
  });

  app.post('/api/v1/push/subscriptions', async (request, reply) => {
    if (!publicVapidKey) return reply.code(503).send({ error: 'web_push_not_configured' });
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const sessionId = requireCookieSession(request, reply);
    if (!sessionId) return;
    const input = parseBody(registerPushSubscriptionSchema, request.body, reply);
    if (!input) return;

    const hash = endpointHash(input.endpoint);
    const existing = await sql<{
      id: string;
      user_id: string;
      session_revoked_at: Date | null;
      session_expires_at: Date;
    }>`
      SELECT ps.id, ps.user_id, s.revoked_at AS session_revoked_at, s.expires_at AS session_expires_at
      FROM push_subscriptions ps
      INNER JOIN user_sessions s ON s.id = ps.session_id
      WHERE ps.endpoint_hash = ${hash}
      LIMIT 1
    `.execute(database);

    const current = existing.rows[0];
    const currentBindingActive = Boolean(
      current
      && current.session_revoked_at === null
      && new Date(current.session_expires_at).getTime() > Date.now(),
    );
    if (current && current.user_id !== userId && currentBindingActive) {
      return reply.code(409).send({ error: 'push_subscription_bound_to_another_active_account' });
    }

    const expiry = expirationDate(input.expiration_time);
    const inserted = await sql<{ id: string }>`
      INSERT INTO push_subscriptions (
        user_id, session_id, endpoint, endpoint_hash, p256dh, auth_secret,
        expiration_time, user_agent, device_label, status, failure_count,
        last_failure_code, last_seen_at, revoked_at, updated_at
      ) VALUES (
        ${userId}, ${sessionId}, ${input.endpoint}, ${hash}, ${input.keys.p256dh}, ${input.keys.auth},
        ${expiry}, ${request.headers['user-agent'] ?? null}, ${input.device_label ?? null}, 'active', 0,
        NULL, now(), NULL, now()
      )
      ON CONFLICT (endpoint_hash)
      DO UPDATE SET
        user_id = EXCLUDED.user_id,
        session_id = EXCLUDED.session_id,
        endpoint = EXCLUDED.endpoint,
        p256dh = EXCLUDED.p256dh,
        auth_secret = EXCLUDED.auth_secret,
        expiration_time = EXCLUDED.expiration_time,
        user_agent = EXCLUDED.user_agent,
        device_label = EXCLUDED.device_label,
        status = 'active',
        failure_count = 0,
        last_failure_code = NULL,
        last_seen_at = now(),
        revoked_at = NULL,
        updated_at = now()
      RETURNING id
    `.execute(database);

    return reply.code(current ? 200 : 201).send({
      subscription_id: inserted.rows[0]?.id,
      active: true,
    });
  });

  app.delete('/api/v1/push/subscriptions', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const input = parseBody(unregisterPushSubscriptionSchema, request.body, reply);
    if (!input) return;

    await sql`
      UPDATE push_subscriptions
      SET status = 'revoked', revoked_at = now(), updated_at = now()
      WHERE user_id = ${userId} AND endpoint_hash = ${endpointHash(input.endpoint)}
    `.execute(database);
    return reply.code(204).send();
  });

  app.post('/api/v1/push/test', async (request, reply) => {
    const context = requireContext(request, reply);
    if (!context) return;
    const database = requireDatabase(db, reply);
    if (!database) return;
    const input = parseBody(pushTestSchema, request.body, reply);
    if (!input) return;

    if (input.field_id) {
      const field = await fieldBelongsToWorkspace(database, input.field_id, context.workspaceId);
      if (!field) return reply.code(404).send({ error: 'field_not_found' });
    }

    const active = await sql<{ count: number | string }>`
      SELECT count(*)::int AS count
      FROM push_subscriptions ps
      INNER JOIN user_sessions s ON s.id = ps.session_id
      WHERE ps.user_id = ${context.userId}
        AND ps.status = 'active'
        AND ps.revoked_at IS NULL
        AND (ps.expiration_time IS NULL OR ps.expiration_time > now())
        AND s.revoked_at IS NULL
        AND s.expires_at > now()
    `.execute(database);
    if (Number(active.rows[0]?.count ?? 0) === 0) {
      return reply.code(409).send({ error: 'no_active_push_subscription' });
    }

    const intentId = randomUUID();
    const sourceId = randomUUID();
    const path = input.field_id ? `radar/?fieldId=${encodeURIComponent(input.field_id)}` : 'radar/';
    await sql`
      INSERT INTO notification_intents (
        id, user_id, workspace_id, field_id, kind, channel, source_type, source_record_id,
        title, body, payload_json, dedupe_key, status
      ) VALUES (
        ${intentId}, ${context.userId}, ${context.workspaceId}, ${input.field_id ?? null},
        'push_test', 'push', 'push_test', ${sourceId},
        'Mágina Olivo · aviso de prueba',
        'Si ves este aviso, las notificaciones de este dispositivo están funcionando.',
        ${JSON.stringify({ path, semantics: 'user_requested_test' })}::jsonb,
        ${`push-test:${intentId}`}, 'pending'
      )
    `.execute(database);

    let dispatchQueued = false;
    try {
      await queue.enqueue({ version: 1, intent_id: intentId, limit: 1 });
      dispatchQueued = true;
    } catch (error) {
      request.log.warn({ err: error, intentId }, 'Immediate push dispatch could not be queued; scheduled dispatcher may pick it up');
    }

    return reply.code(202).send({
      intent_id: intentId,
      dispatch_queued: dispatchQueued,
      status: 'pending',
    });
  });
}
