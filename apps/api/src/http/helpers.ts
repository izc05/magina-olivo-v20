import type { FastifyReply, FastifyRequest } from 'fastify';
import type { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { readRequestContext } from '../request-context.js';

export function requireContext(request: FastifyRequest, reply: FastifyReply) {
  const context = readRequestContext(request);
  if (!context) {
    void reply.code(401).send({
      error: 'missing_development_context',
      message: 'Temporary development headers x-workspace-id and x-user-id are required.',
    });
    return null;
  }
  return context;
}

export function requireDatabase(db: DatabaseClient | null, reply: FastifyReply) {
  if (!db) {
    void reply.code(503).send({ error: 'database_unavailable', message: 'DATABASE_URL is not configured.' });
    return null;
  }
  return db;
}

export function parseBody<T extends z.ZodTypeAny>(schema: T, body: unknown, reply: FastifyReply): z.infer<T> | null {
  const result = schema.safeParse(body);
  if (!result.success) {
    void reply.code(400).send({
      error: 'validation_error',
      issues: result.error.issues.map((issue) => ({ path: issue.path.join('.'), message: issue.message })),
    });
    return null;
  }
  return result.data;
}

export async function fieldBelongsToWorkspace(db: DatabaseClient, fieldId: string, workspaceId: string) {
  return db.selectFrom('fields')
    .select(['id', 'name'])
    .where('id', '=', fieldId)
    .where('workspace_id', '=', workspaceId)
    .where('status', '=', 'active')
    .executeTakeFirst();
}
