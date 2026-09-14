import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

type ProgressRow = { balance: number; xp: number };
type LevelRow = {
  level: number;
  slug: string;
  name: string;
  min_xp: number;
  tree_stage: number;
  badge_title: string;
  description: string;
};
type AchievementRow = {
  id: string;
  title: string;
  description: string;
  icon: string;
  unlocked_at: Date | null;
};
type HistoryRow = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: Date;
};

async function reconcileAchievements(database: DatabaseClient, userId: string, xp: number) {
  await sql`
    WITH candidates(achievement_id, source_id) AS (
      SELECT 'first-roots'::text, MIN(id::text)
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid AND event_type='first_activity'
      HAVING COUNT(*) > 0
      UNION ALL
      SELECT 'field-constancy', MIN(id::text)
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid AND event_type='ten_activities'
      HAVING COUNT(*) > 0
      UNION ALL
      SELECT 'first-harvest', MIN(id::text)
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid AND event_type='first_harvest_delivery'
      HAVING COUNT(*) > 0
      UNION ALL
      SELECT 'magina-explorer', MIN(id::text)
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid AND event_type='territory_viewed'
      HAVING COUNT(*) > 0
      UNION ALL
      SELECT 'olive-500-xp', 'xp:500' WHERE ${xp}::int >= 500
      UNION ALL
      SELECT 'olive-1000-xp', 'xp:1000' WHERE ${xp}::int >= 1000
      UNION ALL
      SELECT 'first-reservation', MIN(id::text)
      FROM mill_reward_redemptions
      WHERE user_id=${userId}::uuid
      HAVING COUNT(*) > 0
      UNION ALL
      SELECT 'first-redemption', MIN(id::text)
      FROM mill_reward_redemptions
      WHERE user_id=${userId}::uuid AND status='redeemed'
      HAVING COUNT(*) > 0
    )
    INSERT INTO mi_olivo_user_achievements (user_id, achievement_id, source_id)
    SELECT ${userId}::uuid, achievement_id, source_id
    FROM candidates
    ON CONFLICT (user_id, achievement_id) DO NOTHING
  `.execute(database);
}

export function registerMiOlivoProgressionRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/mi-olivo/progression', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const progressResult = await sql<ProgressRow>`
      SELECT
        COALESCE(SUM(points), 0)::int AS balance,
        COALESCE(SUM(
          CASE WHEN points > 0 AND event_type <> 'reward_refund' THEN points ELSE 0 END
        ), 0)::int AS xp
      FROM mi_olivo_ledger
      WHERE user_id=${userId}::uuid
    `.execute(database);
    const raw = progressResult.rows[0] ?? { balance: 0, xp: 0 };
    const balance = Math.max(0, Number(raw.balance));
    const xp = Math.max(0, Number(raw.xp));

    await reconcileAchievements(database, userId, xp);

    const [levelsResult, achievementsResult, historyResult, redemptionResult] = await Promise.all([
      sql<LevelRow>`
        SELECT level, slug, name, min_xp, tree_stage, badge_title, description
        FROM mi_olivo_levels
        ORDER BY level
      `.execute(database),
      sql<AchievementRow>`
        SELECT d.id, d.title, d.description, d.icon, ua.unlocked_at
        FROM mi_olivo_achievement_definitions d
        LEFT JOIN mi_olivo_user_achievements ua
          ON ua.achievement_id=d.id AND ua.user_id=${userId}::uuid
        WHERE d.active=true
        ORDER BY d.sort_order, d.id
      `.execute(database),
      sql<HistoryRow>`
        SELECT id::text, event_type, points, reason, created_at
        FROM mi_olivo_ledger
        WHERE user_id=${userId}::uuid
        ORDER BY created_at DESC
        LIMIT 30
      `.execute(database),
      sql<{ reserved: number; redeemed: number; cancelled: number; expired: number }>`
        SELECT
          count(*) FILTER (WHERE status='reserved')::int AS reserved,
          count(*) FILTER (WHERE status='redeemed')::int AS redeemed,
          count(*) FILTER (WHERE status='cancelled')::int AS cancelled,
          count(*) FILTER (WHERE status='expired')::int AS expired
        FROM mill_reward_redemptions
        WHERE user_id=${userId}::uuid
      `.execute(database),
    ]);

    const levels = levelsResult.rows;
    const current = [...levels].reverse().find((item) => xp >= item.min_xp) ?? levels[0];
    const currentIndex = current ? levels.findIndex((item) => item.level === current.level) : -1;
    const next = currentIndex >= 0 ? levels[currentIndex + 1] ?? null : levels[0] ?? null;
    const floor = current?.min_xp ?? 0;
    const target = next?.min_xp ?? floor;
    const span = Math.max(1, target - floor);
    const currentIntoLevel = Math.max(0, xp - floor);
    const percent = next ? Math.min(100, Math.round((currentIntoLevel / span) * 100)) : 100;
    const redemptionStats = redemptionResult.rows[0] ?? { reserved: 0, redeemed: 0, cancelled: 0, expired: 0 };

    return {
      currency: {
        name: 'aceitunas',
        balance,
        spendable: true,
        transferable: false,
        cash_value: false,
        blockchain: false,
      },
      xp,
      current_level: current ? {
        level: current.level,
        slug: current.slug,
        name: current.name,
        min_xp: current.min_xp,
        tree_stage: current.tree_stage,
        badge_title: current.badge_title,
        description: current.description,
      } : null,
      next_level: next ? {
        level: next.level,
        slug: next.slug,
        name: next.name,
        min_xp: next.min_xp,
        tree_stage: next.tree_stage,
        badge_title: next.badge_title,
        description: next.description,
      } : null,
      progress: {
        current: currentIntoLevel,
        target: next ? span : currentIntoLevel,
        percent,
        xp_to_next: next ? Math.max(0, next.min_xp - xp) : 0,
      },
      levels: levels.map((item) => ({
        level: item.level,
        slug: item.slug,
        name: item.name,
        min_xp: item.min_xp,
        tree_stage: item.tree_stage,
        badge_title: item.badge_title,
        description: item.description,
        unlocked: xp >= item.min_xp,
      })),
      achievements: achievementsResult.rows.map((item) => ({
        id: item.id,
        title: item.title,
        description: item.description,
        icon: item.icon,
        unlocked: Boolean(item.unlocked_at),
        unlocked_at: item.unlocked_at,
      })),
      redemptions: redemptionStats,
      history: historyResult.rows,
    };
  });
}
