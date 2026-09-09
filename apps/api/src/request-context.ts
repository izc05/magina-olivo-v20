import type { FastifyRequest } from 'fastify';

export type RequestContext = {
  workspaceId: string;
  userId: string;
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function readRequestContext(request: FastifyRequest): RequestContext | null {
  const workspaceId = request.headers['x-workspace-id'];
  const userId = request.headers['x-user-id'];
  if (typeof workspaceId !== 'string' || typeof userId !== 'string') return null;
  if (!uuidPattern.test(workspaceId) || !uuidPattern.test(userId)) return null;
  return { workspaceId, userId };
}

export const prototypeAuthWarning = 'x-workspace-id/x-user-id are temporary development context headers; replace with authenticated membership context before production.';
