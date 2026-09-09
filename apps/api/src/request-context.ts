import type { FastifyRequest } from 'fastify';
import type { DatabaseClient } from './db/client.js';
import { hashSessionToken, SESSION_COOKIE_NAME } from './auth/session.js';

export type WorkspaceRole = 'owner' | 'admin' | 'manager' | 'member' | 'worker' | 'viewer' | 'development';

export type RequestContext = {
  workspaceId: string;
  userId: string;
  role: WorkspaceRole;
};

declare module 'fastify' {
  interface FastifyRequest {
    authenticatedUserId?: string;
    authSessionId?: string;
    authContext?: RequestContext;
  }
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function developmentContext(request: FastifyRequest): RequestContext | null {
  if (process.env.ALLOW_DEV_AUTH_HEADERS !== 'true') return null;
  const workspaceId = request.headers['x-workspace-id'];
  const userId = request.headers['x-user-id'];
  if (typeof workspaceId !== 'string' || typeof userId !== 'string') return null;
  if (!uuidPattern.test(workspaceId) || !uuidPattern.test(userId)) return null;
  return { workspaceId, userId, role: 'development' };
}

export async function hydrateRequestAuthentication(request: FastifyRequest, db: DatabaseClient | null) {
  const dev = developmentContext(request);
  if (dev) {
    request.authenticatedUserId = dev.userId;
    request.authContext = dev;
    return;
  }

  if (!db) return;
  const token = request.cookies?.[SESSION_COOKIE_NAME];
  if (!token) return;

  const now = new Date().toISOString();
  const session = await db.selectFrom('user_sessions as s')
    .innerJoin('users as u', 'u.id', 's.user_id')
    .select(['s.id as session_id', 's.user_id', 'u.status as user_status'])
    .where('s.token_hash', '=', hashSessionToken(token))
    .where('s.revoked_at', 'is', null)
    .where('s.expires_at', '>', now)
    .where('u.status', '=', 'active')
    .executeTakeFirst();

  if (!session) return;
  request.authenticatedUserId = session.user_id;
  request.authSessionId = session.session_id;

  const workspaceId = request.headers['x-workspace-id'];
  if (typeof workspaceId !== 'string' || !uuidPattern.test(workspaceId)) return;

  const membership = await db.selectFrom('workspace_memberships')
    .select(['role'])
    .where('workspace_id', '=', workspaceId)
    .where('user_id', '=', session.user_id)
    .where('status', '=', 'active')
    .executeTakeFirst();

  if (!membership) return;
  request.authContext = {
    workspaceId,
    userId: session.user_id,
    role: membership.role,
  };
}

export function readRequestContext(request: FastifyRequest): RequestContext | null {
  return request.authContext ?? developmentContext(request);
}

export function readAuthenticatedUserId(request: FastifyRequest) {
  return request.authenticatedUserId ?? developmentContext(request)?.userId ?? null;
}

export const prototypeAuthWarning = 'Authenticated sessions and workspace memberships are enforced by default. Temporary x-user-id/x-workspace-id headers work only when ALLOW_DEV_AUTH_HEADERS=true.';
