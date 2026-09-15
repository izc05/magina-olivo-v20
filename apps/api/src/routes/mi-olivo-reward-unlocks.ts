import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const editableRoles = new Set(['owner', 'manager', 'editor']);
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const rewardRedeemPath = /^\/api\/v1\/almazara-rewards\/([0-9a-f-]{36})\/redeem$/i;

type BusinessRole = 'owner' | 'manager' | 'editor' | 'analyst';

type LevelState = {
  xp: number;
  balance: number;
  current_level: number;
  current_level_name: string;
};

async function membership(database: DatabaseClient, businessId: string, userId: string) {
  const result = await sql<{ role: BusinessRole }>`
    SELECT role
    FROM business_memberships
    WHERE business_id=${businessId}::uuid
      AND user_id=${userId}::uuid
      AND status='active'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

async function levelState(database: DatabaseClient, userId: string): Promise<LevelState> {
  const result = await sql<LevelState>`
    WITH progress AS (
      SELECT
        mi_olivo_lifetime_xp(${userId}::uuid)::int AS xp,
        GREATEST(COALESCE(SUM(points), 0), 0)::int AS balance
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid
    ), current_level AS (
      SELECT l.level, l.name
      FROM mi_olivo_levels l, progress p
      WHERE l.min_xp <= p.xp
      ORDER BY l.level DESC
      LIMIT 1
    )
    SELECT
      p.xp,
      p.balance,
      COALESCE(c.level, 1)::int AS current_level,
      COALESCE(c.name, 'Brote') AS current_level_name
    FROM progress p
    LEFT JOIN current_level c ON true
  `.execute(database);
  return result.rows[0] ?? { xp: 0, balance: 0, current_level: 1, current_level_name: 'Brote' };
}

export function registerMiOlivoRewardUnlockRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  // Friendly guard for the existing redemption endpoint. The 0088 database
  // trigger repeats the check inside the transaction as the final authority.
  app.addHook('preHandler', async (request, reply) => {
    if (request.method !== 'POST') return;
    const pathname = request.url.split('?', 1)[0] ?? request.url;
    const match = pathname.match(rewardRedeemPath);
    if (!match?.[1]) return;

    const database = requireDatabase(db, reply);
    if (!database) return reply;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return reply;

    const product = await sql<{ required_level: number; level_name: string }>`
      SELECT p.required_level, l.name AS level_name
      FROM mill_reward_products p
      JOIN mi_olivo_levels l ON l.level=p.required_level
      WHERE p.id=${match[1]}::uuid
      LIMIT 1
    `.execute(database);
    const required = product.rows[0];
    if (!required || required.required_level <= 1) return;

    const current = await levelState(database, userId);
    if (current.current_level < required.required_level) {
      return reply.code(409).send({
        error: 'reward_level_locked',
        currentLevel: current.current_level,
        currentLevelName: current.current_level_name,
        requiredLevel: required.required_level,
        requiredLevelName: required.level_name,
        xp: current.xp,
      });
    }
  });

  app.get('/api/v1/public/almazaras/:slug/reward-unlocks', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_almazara_slug' });

    const result = await sql<{
      reward_id: string;
      required_level: number;
      required_level_name: string;
      min_xp: number;
    }>`
      SELECT p.id::text AS reward_id,
             p.required_level,
             l.name AS required_level_name,
             l.min_xp
      FROM mill_reward_products p
      JOIN businesses b ON b.id=p.business_id
      JOIN mi_olivo_levels l ON l.level=p.required_level
      WHERE b.slug=${params.data.slug}
        AND b.status='published'
        AND p.status='published'
        AND (p.starts_at IS NULL OR p.starts_at <= now())
        AND (p.ends_at IS NULL OR p.ends_at >= now())
      ORDER BY p.olive_cost, p.title
    `.execute(database);

    return {
      rewards: result.rows.map((row) => ({
        rewardId: row.reward_id,
        requiredLevel: row.required_level,
        requiredLevelName: row.required_level_name,
        minXp: row.min_xp,
      })),
    };
  });

  app.get('/api/v1/my/almazaras/:slug/reward-unlocks', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_almazara_slug' });

    const current = await levelState(database, userId);
    const rewards = await sql<{
      reward_id: string;
      required_level: number;
      required_level_name: string;
      min_xp: number;
    }>`
      SELECT p.id::text AS reward_id,
             p.required_level,
             l.name AS required_level_name,
             l.min_xp
      FROM mill_reward_products p
      JOIN businesses b ON b.id=p.business_id
      JOIN mi_olivo_levels l ON l.level=p.required_level
      WHERE b.slug=${params.data.slug}
        AND b.status='published'
        AND p.status='published'
        AND (p.starts_at IS NULL OR p.starts_at <= now())
        AND (p.ends_at IS NULL OR p.ends_at >= now())
      ORDER BY p.olive_cost, p.title
    `.execute(database);

    return {
      xp: current.xp,
      balance: current.balance,
      currentLevel: current.current_level,
      currentLevelName: current.current_level_name,
      rewards: rewards.rows.map((row) => ({
        rewardId: row.reward_id,
        requiredLevel: row.required_level,
        requiredLevelName: row.required_level_name,
        minXp: row.min_xp,
        unlocked: current.current_level >= row.required_level,
      })),
    };
  });

  // Companion metadata for /mi-olivo/canjes. The mature redemption endpoint
  // remains untouched; this only exposes pickup details from the business profile.
  app.get('/api/v1/my/almazara-redemption-pickups', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const result = await sql<{
      redemption_id: string;
      business_slug: string;
      address: string | null;
      municipality_name: string | null;
      phone: string | null;
      longitude: number | null;
      latitude: number | null;
    }>`
      SELECT r.id::text AS redemption_id,
             b.slug AS business_slug,
             b.address,
             m.name AS municipality_name,
             b.phone,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_X(b.location) END AS longitude,
             CASE WHEN b.location IS NULL THEN NULL ELSE ST_Y(b.location) END AS latitude
      FROM mill_reward_redemptions r
      JOIN businesses b ON b.id=r.business_id
      LEFT JOIN territory_municipalities m ON m.id=b.municipality_id
      WHERE r.user_id=${userId}::uuid
      ORDER BY r.created_at DESC
      LIMIT 100
    `.execute(database);

    return {
      pickups: result.rows.map((row) => ({
        redemptionId: row.redemption_id,
        businessSlug: row.business_slug,
        address: row.address,
        municipalityName: row.municipality_name,
        phone: row.phone,
        longitude: row.longitude === null ? null : Number(row.longitude),
        latitude: row.latitude === null ? null : Number(row.latitude),
      })),
    };
  });

  app.get('/api/v1/my/businesses/:id/almazara-reward-unlocks', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });

    const access = await membership(database, params.data.id, userId);
    if (!access) return reply.code(403).send({ error: 'business_access_denied' });

    const result = await sql<{
      reward_id: string;
      title: string;
      status: string;
      required_level: number;
      required_level_name: string;
      min_xp: number;
    }>`
      SELECT p.id::text AS reward_id,
             p.title,
             p.status,
             p.required_level,
             l.name AS required_level_name,
             l.min_xp
      FROM mill_reward_products p
      JOIN mi_olivo_levels l ON l.level=p.required_level
      WHERE p.business_id=${params.data.id}::uuid
      ORDER BY p.updated_at DESC, p.title
    `.execute(database);

    return {
      role: access.role,
      rewards: result.rows.map((row) => ({
        rewardId: row.reward_id,
        title: row.title,
        status: row.status,
        requiredLevel: row.required_level,
        requiredLevelName: row.required_level_name,
        minXp: row.min_xp,
      })),
    };
  });

  app.put('/api/v1/my/businesses/:id/almazara-rewards/:rewardId/unlock', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid(), rewardId: z.string().uuid() }).safeParse(request.params);
    const body = z.object({ requiredLevel: z.number().int().min(1).max(10) }).safeParse(request.body);
    if (!params.success || !body.success) return reply.code(400).send({ error: 'invalid_reward_unlock' });

    const access = await membership(database, params.data.id, userId);
    if (!access || !editableRoles.has(access.role)) return reply.code(403).send({ error: 'business_edit_denied' });

    const result = await sql<{ id: string }>`
      UPDATE mill_reward_products
      SET required_level=${body.data.requiredLevel}, updated_by=${userId}::uuid, updated_at=now()
      WHERE id=${params.data.rewardId}::uuid
        AND business_id=${params.data.id}::uuid
      RETURNING id::text
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'reward_not_available' });

    const level = await sql<{ level: number; name: string; min_xp: number }>`
      SELECT level, name, min_xp
      FROM mi_olivo_levels
      WHERE level=${body.data.requiredLevel}
      LIMIT 1
    `.execute(database);

    return {
      reward: {
        id: result.rows[0].id,
        requiredLevel: level.rows[0]?.level ?? body.data.requiredLevel,
        requiredLevelName: level.rows[0]?.name ?? null,
        minXp: level.rows[0]?.min_xp ?? null,
      },
    };
  });
}
