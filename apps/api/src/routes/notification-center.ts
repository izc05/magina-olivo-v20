import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const querySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

type NotificationRow = {
  id: string;
  field_id: string | null;
  field_name: string | null;
  kind: string;
  source_type: string;
  title: string;
  body: string;
  status: 'pending' | 'dispatched' | 'suppressed' | 'failed';
  created_at: Date | string;
  dispatched_at: Date | string | null;
  action_path: string | null;
};

type NotificationCounts = {
  total: number | string;
  pending: number | string;
  dispatched: number | string;
  suppressed: number | string;
  failed: number | string;
};

function asIsoString(value: Date | string) {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toISOString();
}

function safeInternalPath(value: string | null) {
  if (!value) return null;
  const candidate = value.trim();
  if (!candidate || candidate.startsWith('//') || candidate.includes('\\')) return null;
  if (/^[a-z][a-z0-9+.-]*:/i.test(candidate)) return null;
  const segments = candidate.split('/');
  if (segments.includes('..')) return null;
  return `/${candidate.replace(/^\/+/, '')}`;
}

export function registerNotificationCenterRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/notifications', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const parsed = querySchema.safeParse(request.query ?? {});
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_notification_query', issues: parsed.error.issues });
    }

    const [history, countsResult] = await Promise.all([
      sql<NotificationRow>`
        SELECT
          ni.id,
          ni.field_id,
          f.name AS field_name,
          ni.kind,
          ni.source_type,
          ni.title,
          ni.body,
          ni.status,
          ni.created_at,
          ni.dispatched_at,
          ni.payload_json->>'path' AS action_path
        FROM notification_intents ni
        LEFT JOIN fields f
          ON f.id = ni.field_id
         AND f.workspace_id = ni.workspace_id
        WHERE ni.user_id = ${context.userId}::uuid
          AND ni.workspace_id = ${context.workspaceId}::uuid
        ORDER BY ni.created_at DESC, ni.id DESC
        LIMIT ${parsed.data.limit}
      `.execute(database),
      sql<NotificationCounts>`
        SELECT
          count(*)::int AS total,
          count(*) FILTER (WHERE status = 'pending')::int AS pending,
          count(*) FILTER (WHERE status = 'dispatched')::int AS dispatched,
          count(*) FILTER (WHERE status = 'suppressed')::int AS suppressed,
          count(*) FILTER (WHERE status = 'failed')::int AS failed
        FROM notification_intents
        WHERE user_id = ${context.userId}::uuid
          AND workspace_id = ${context.workspaceId}::uuid
      `.execute(database),
    ]);

    const counts = countsResult.rows[0];
    return {
      items: history.rows.map((row) => ({
        id: row.id,
        field_id: row.field_id,
        field_name: row.field_name,
        kind: row.kind,
        source_type: row.source_type,
        title: row.title,
        body: row.body,
        status: row.status,
        created_at: asIsoString(row.created_at),
        dispatched_at: row.dispatched_at ? asIsoString(row.dispatched_at) : null,
        action_path: safeInternalPath(row.action_path),
      })),
      counts: {
        total: Number(counts?.total ?? 0),
        pending: Number(counts?.pending ?? 0),
        dispatched: Number(counts?.dispatched ?? 0),
        suppressed: Number(counts?.suppressed ?? 0),
        failed: Number(counts?.failed ?? 0),
      },
      semantics: 'delivery_history_not_read_state',
    };
  });
}
