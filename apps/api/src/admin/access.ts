import type { FastifyReply, FastifyRequest } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import type { PlatformAdminRole } from '../db/types.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

export type PlatformAccess = {
  userId: string;
  role: PlatformAdminRole;
  source: 'database' | 'bootstrap';
};

const roleRank: Record<PlatformAdminRole, number> = {
  support: 10,
  editor: 20,
  admin: 30,
  super_admin: 40,
};

function configuredBootstrapEmails() {
  return new Set((process.env.ADMIN_BOOTSTRAP_EMAILS ?? '')
    .split(',')
    .map((email) => email.trim().toLowerCase())
    .filter(Boolean));
}

export function roleAtLeast(role: PlatformAdminRole, minimum: PlatformAdminRole) {
  return roleRank[role] >= roleRank[minimum];
}

export async function resolvePlatformAccess(db: DatabaseClient, userId: string): Promise<PlatformAccess | null> {
  const stored = await db.selectFrom('platform_admins')
    .select(['role', 'status'])
    .where('user_id', '=', userId)
    .executeTakeFirst();

  if (stored?.status === 'active') {
    return { userId, role: stored.role, source: 'database' };
  }

  const user = await db.selectFrom('users')
    .select(['primary_email', 'status'])
    .where('id', '=', userId)
    .executeTakeFirst();

  if (!user || user.status !== 'active' || !user.primary_email) return null;
  if (!configuredBootstrapEmails().has(user.primary_email.toLowerCase())) return null;

  return { userId, role: 'super_admin', source: 'bootstrap' };
}

export async function requirePlatformAccess(
  request: FastifyRequest,
  reply: FastifyReply,
  db: DatabaseClient | null,
  minimum: PlatformAdminRole = 'support',
) {
  const database = requireDatabase(db, reply);
  if (!database) return null;
  const userId = requireAuthenticatedUser(request, reply);
  if (!userId) return null;

  const access = await resolvePlatformAccess(database, userId);
  if (!access) {
    void reply.code(403).send({ error: 'platform_admin_required' });
    return null;
  }
  if (!roleAtLeast(access.role, minimum)) {
    void reply.code(403).send({ error: 'platform_admin_role_required', minimum_role: minimum });
    return null;
  }

  return { database, access };
}

export async function auditAdminAction(
  db: DatabaseClient,
  access: PlatformAccess,
  action: string,
  targetType: string,
  targetId: string | null,
  metadata: Record<string, unknown> = {},
) {
  await db.insertInto('admin_audit_log').values({
    actor_user_id: access.userId,
    actor_role: access.source === 'bootstrap' ? 'bootstrap' : access.role,
    action,
    target_type: targetType,
    target_id: targetId,
    metadata,
  }).execute();
}
