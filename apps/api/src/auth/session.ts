import { createHash, randomBytes } from 'node:crypto';
import type { DatabaseClient } from '../db/client.js';

export const SESSION_COOKIE_NAME = 'magina_session';
export const SESSION_TTL_DAYS = 30;

export function hashSessionToken(token: string) {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}

export function createSessionToken() {
  return randomBytes(32).toString('base64url');
}

export async function issueSession(db: DatabaseClient, userId: string, userAgent: string | null) {
  const token = createSessionToken();
  const tokenHash = hashSessionToken(token);
  const expiresAt = new Date(Date.now() + SESSION_TTL_DAYS * 24 * 60 * 60 * 1000);

  const session = await db.insertInto('user_sessions').values({
    user_id: userId,
    token_hash: tokenHash,
    expires_at: expiresAt.toISOString(),
    revoked_at: null,
    user_agent: userAgent,
  }).returning(['id', 'expires_at']).executeTakeFirstOrThrow();

  return { token, sessionId: session.id, expiresAt };
}

export async function revokeSession(db: DatabaseClient, token: string) {
  const tokenHash = hashSessionToken(token);
  await db.updateTable('user_sessions')
    .set({ revoked_at: new Date().toISOString() })
    .where('token_hash', '=', tokenHash)
    .where('revoked_at', 'is', null)
    .execute();
}
