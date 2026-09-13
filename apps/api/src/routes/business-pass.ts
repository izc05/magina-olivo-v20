import { createHash, randomBytes } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const checkinSchema = z.object({ code: z.string().trim().min(16).max(300) });
const rewardParamsSchema = z.object({ id: z.string().uuid() });
const slugParamsSchema = z.object({ slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/) });

function hashToken(value: string) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function rawRedemptionCode() {
  return randomBytes(12).toString('base64url');
}

type ProgramRow = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  program_type: string;
  valid_from: Date | null;
  valid_until: Date | null;
  participating_businesses: number;
  rewards: number;
};

type RewardRow = {
  id: string;
  business_id: string | null;
  business_name: string | null;
  title: string;
  description: string | null;
  points_cost: number;
  reward_type: string;
  redemption_instructions: string | null;
  valid_from: Date | null;
  valid_until: Date | null;
};

export function registerBusinessPassRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/magina-pass/programs', async (_request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const result = await sql<ProgramRow>`
      SELECT p.id, p.slug, p.name, p.description, p.program_type, p.valid_from, p.valid_until,
             (SELECT count(*)::int FROM magina_pass_businesses pb WHERE pb.program_id=p.id AND pb.active=true) AS participating_businesses,
             (SELECT count(*)::int FROM magina_pass_rewards r WHERE r.program_id=p.id AND r.status='published'
               AND (r.valid_from IS NULL OR r.valid_from <= now()) AND (r.valid_until IS NULL OR r.valid_until >= now())) AS rewards
      FROM magina_pass_programs p
      WHERE p.status='published'
        AND (p.valid_from IS NULL OR p.valid_from <= now())
        AND (p.valid_until IS NULL OR p.valid_until >= now())
      ORDER BY p.published_at DESC NULLS LAST, p.name
    `.execute(database);
    return { programs: result.rows };
  });

  app.get('/api/v1/public/magina-pass/programs/:slug', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = slugParamsSchema.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_pass_slug' });

    const program = await sql<ProgramRow>`
      SELECT p.id, p.slug, p.name, p.description, p.program_type, p.valid_from, p.valid_until,
             (SELECT count(*)::int FROM magina_pass_businesses pb WHERE pb.program_id=p.id AND pb.active=true) AS participating_businesses,
             (SELECT count(*)::int FROM magina_pass_rewards r WHERE r.program_id=p.id AND r.status='published') AS rewards
      FROM magina_pass_programs p
      WHERE p.slug=${params.data.slug} AND p.status='published'
        AND (p.valid_from IS NULL OR p.valid_from <= now())
        AND (p.valid_until IS NULL OR p.valid_until >= now())
      LIMIT 1
    `.execute(database);
    const current = program.rows[0];
    if (!current) return reply.code(404).send({ error: 'pass_program_not_found' });

    const [stops, rewards] = await Promise.all([
      sql<{
        business_id: string; slug: string; name: string; short_description: string | null;
        place_name: string | null; municipality_name: string | null; logo_url: string | null;
        featured_stop: boolean; checkin_points: number;
      }>`
        SELECT b.id AS business_id, b.slug, b.name, b.short_description,
               tp.name AS place_name, tm.name AS municipality_name, b.logo_url,
               pb.featured_stop,
               COALESCE(pb.checkin_points, p.default_checkin_points)::int AS checkin_points
        FROM magina_pass_businesses pb
        JOIN magina_pass_programs p ON p.id=pb.program_id
        JOIN businesses b ON b.id=pb.business_id
        LEFT JOIN territory_places tp ON tp.id=b.place_id
        LEFT JOIN territory_municipalities tm ON tm.id=b.municipality_id
        WHERE pb.program_id=${current.id}::uuid AND pb.active=true AND b.status='published'
        ORDER BY pb.featured_stop DESC, pb.sort_order, b.name
      `.execute(database),
      sql<RewardRow>`
        SELECT r.id, r.business_id, b.name AS business_name, r.title, r.description,
               r.points_cost, r.reward_type, r.redemption_instructions, r.valid_from, r.valid_until
        FROM magina_pass_rewards r
        LEFT JOIN businesses b ON b.id=r.business_id
        WHERE r.program_id=${current.id}::uuid AND r.status='published'
          AND (r.valid_from IS NULL OR r.valid_from <= now())
          AND (r.valid_until IS NULL OR r.valid_until >= now())
        ORDER BY r.points_cost, r.title
      `.execute(database),
    ]);

    return { program: current, stops: stops.rows, rewards: rewards.rows };
  });

  app.get('/api/v1/my/magina-pass', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const [wallets, checkins, redemptions] = await Promise.all([
      sql<{
        id: string; program_id: string; program_slug: string; program_name: string;
        points_balance: number; total_points_earned: number; total_checkins: number; updated_at: Date;
      }>`
        SELECT w.id, w.program_id, p.slug AS program_slug, p.name AS program_name,
               w.points_balance, w.total_points_earned, w.total_checkins, w.updated_at
        FROM magina_pass_wallets w JOIN magina_pass_programs p ON p.id=w.program_id
        WHERE w.user_id=${userId}::uuid
        ORDER BY w.updated_at DESC
      `.execute(database),
      sql<{
        id: string; program_id: string; business_name: string | null; source_type: string;
        points_awarded: number; occurred_at: Date;
      }>`
        SELECT c.id, c.program_id, b.name AS business_name, c.source_type, c.points_awarded, c.occurred_at
        FROM magina_pass_checkins c LEFT JOIN businesses b ON b.id=c.business_id
        WHERE c.user_id=${userId}::uuid
        ORDER BY c.occurred_at DESC LIMIT 100
      `.execute(database),
      sql<{
        id: string; program_id: string; reward_title: string; business_name: string | null;
        points_spent: number; status: string; issued_at: Date; redeemed_at: Date | null; expires_at: Date | null;
      }>`
        SELECT rd.id, rd.program_id, r.title AS reward_title, b.name AS business_name,
               rd.points_spent, rd.status, rd.issued_at, rd.redeemed_at, rd.expires_at
        FROM magina_pass_redemptions rd
        JOIN magina_pass_rewards r ON r.id=rd.reward_id
        LEFT JOIN businesses b ON b.id=rd.business_id
        WHERE rd.user_id=${userId}::uuid
        ORDER BY rd.issued_at DESC LIMIT 100
      `.execute(database),
    ]);
    return { wallets: wallets.rows, checkins: checkins.rows, redemptions: redemptions.rows };
  });

  app.post('/api/v1/my/magina-pass/checkins', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const parsed = checkinSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_pass_checkin', issues: parsed.error.issues });
    const tokenHash = hashToken(parsed.data.code);

    try {
      const outcome = await database.transaction().execute(async (trx) => {
        await sql`SELECT pg_advisory_xact_lock(hashtext(${`magina-pass:${userId}`}))`.execute(trx);
        const tokenResult = await sql<{
          token_id: string; program_id: string; business_id: string; program_name: string; business_name: string;
          points: number; cooldown_hours: number;
        }>`
          SELECT qt.id AS token_id, p.id AS program_id, b.id AS business_id,
                 p.name AS program_name, b.name AS business_name,
                 COALESCE(pb.checkin_points, p.default_checkin_points)::int AS points,
                 COALESCE(pb.cooldown_hours, p.default_cooldown_hours)::int AS cooldown_hours
          FROM magina_pass_qr_tokens qt
          JOIN magina_pass_programs p ON p.id=qt.program_id
          JOIN magina_pass_businesses pb ON pb.program_id=qt.program_id AND pb.business_id=qt.business_id
          JOIN businesses b ON b.id=qt.business_id
          WHERE qt.token_hash=${tokenHash} AND qt.active=true AND qt.revoked_at IS NULL
            AND (qt.valid_from IS NULL OR qt.valid_from <= now())
            AND (qt.valid_until IS NULL OR qt.valid_until >= now())
            AND p.status='published'
            AND (p.valid_from IS NULL OR p.valid_from <= now())
            AND (p.valid_until IS NULL OR p.valid_until >= now())
            AND pb.active=true AND b.status='published'
          LIMIT 1
        `.execute(trx);
        const token = tokenResult.rows[0];
        if (!token) return { error: 'pass_qr_not_available' as const };

        const recent = await sql<{ occurred_at: Date }>`
          SELECT occurred_at FROM magina_pass_checkins
          WHERE user_id=${userId}::uuid AND program_id=${token.program_id}::uuid
            AND business_id=${token.business_id}::uuid
            AND occurred_at > now() - make_interval(hours => ${token.cooldown_hours})
          ORDER BY occurred_at DESC LIMIT 1
        `.execute(trx);
        if (recent.rows[0]) {
          return { error: 'pass_checkin_cooldown' as const, nextAfter: new Date(recent.rows[0].occurred_at.getTime() + token.cooldown_hours * 3_600_000) };
        }

        const walletResult = await sql<{ id: string }>`
          INSERT INTO magina_pass_wallets (program_id, user_id)
          VALUES (${token.program_id}::uuid, ${userId}::uuid)
          ON CONFLICT (program_id, user_id) DO UPDATE SET updated_at=now()
          RETURNING id
        `.execute(trx);
        const walletId = walletResult.rows[0]?.id;
        if (!walletId) throw new Error('pass_wallet_upsert_failed');

        await sql`
          INSERT INTO magina_pass_checkins (
            wallet_id, program_id, business_id, user_id, source_type, source_key, qr_token_id, points_awarded
          ) VALUES (
            ${walletId}::uuid, ${token.program_id}::uuid, ${token.business_id}::uuid,
            ${userId}::uuid, 'business_qr', ${token.business_id}, ${token.token_id}::uuid, ${token.points}
          )
        `.execute(trx);
        const wallet = await sql<{ points_balance: number; total_points_earned: number; total_checkins: number }>`
          UPDATE magina_pass_wallets SET
            points_balance=points_balance + ${token.points},
            total_points_earned=total_points_earned + ${token.points},
            total_checkins=total_checkins + 1,
            updated_at=now()
          WHERE id=${walletId}::uuid
          RETURNING points_balance, total_points_earned, total_checkins
        `.execute(trx);
        return { token, wallet: wallet.rows[0] };
      });

      if ('error' in outcome) return reply.code(409).send(outcome);
      return reply.code(201).send({
        checkin: { businessId: outcome.token.business_id, businessName: outcome.token.business_name, pointsAwarded: outcome.token.points },
        wallet: outcome.wallet,
      });
    } catch (error) {
      request.log.error({ err: error }, 'magina pass checkin failed');
      return reply.code(500).send({ error: 'pass_checkin_failed' });
    }
  });

  app.post('/api/v1/my/magina-pass/rewards/:id/redeem', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = rewardParamsSchema.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_pass_reward' });

    const rawCode = rawRedemptionCode();
    const codeHash = hashToken(rawCode);
    const result = await database.transaction().execute(async (trx) => {
      await sql`SELECT pg_advisory_xact_lock(hashtext(${`magina-pass-redeem:${userId}`}))`.execute(trx);
      const rewardResult = await sql<{
        id: string; program_id: string; business_id: string | null; title: string; points_cost: number;
        max_redemptions: number | null; valid_until: Date | null;
      }>`
        SELECT id, program_id, business_id, title, points_cost, max_redemptions, valid_until
        FROM magina_pass_rewards
        WHERE id=${params.data.id}::uuid AND status='published'
          AND (valid_from IS NULL OR valid_from <= now())
          AND (valid_until IS NULL OR valid_until >= now())
        LIMIT 1
      `.execute(trx);
      const reward = rewardResult.rows[0];
      if (!reward) return { error: 'pass_reward_not_available' as const };

      if (reward.max_redemptions !== null) {
        const used = await sql<{ count: number }>`
          SELECT count(*)::int AS count FROM magina_pass_redemptions
          WHERE reward_id=${reward.id}::uuid AND status IN ('issued','redeemed')
        `.execute(trx);
        if ((used.rows[0]?.count ?? 0) >= reward.max_redemptions) return { error: 'pass_reward_exhausted' as const };
      }

      const walletResult = await sql<{ id: string; points_balance: number }>`
        SELECT id, points_balance FROM magina_pass_wallets
        WHERE program_id=${reward.program_id}::uuid AND user_id=${userId}::uuid
        FOR UPDATE
      `.execute(trx);
      const wallet = walletResult.rows[0];
      if (!wallet || wallet.points_balance < reward.points_cost) return { error: 'pass_insufficient_points' as const };

      await sql`
        UPDATE magina_pass_wallets
        SET points_balance=points_balance - ${reward.points_cost}, updated_at=now()
        WHERE id=${wallet.id}::uuid
      `.execute(trx);
      const redemption = await sql<{ id: string }>`
        INSERT INTO magina_pass_redemptions (
          reward_id, wallet_id, program_id, user_id, business_id, points_spent,
          redemption_code_hash, expires_at
        ) VALUES (
          ${reward.id}::uuid, ${wallet.id}::uuid, ${reward.program_id}::uuid, ${userId}::uuid,
          ${reward.business_id}::uuid, ${reward.points_cost}, ${codeHash},
          LEAST(COALESCE(${reward.valid_until}, now() + interval '30 days'), now() + interval '30 days')
        ) RETURNING id
      `.execute(trx);
      return { reward, redemptionId: redemption.rows[0]?.id, balance: wallet.points_balance - reward.points_cost };
    });

    if ('error' in result) return reply.code(409).send(result);
    return reply.code(201).send({
      redemption: {
        id: result.redemptionId,
        rewardTitle: result.reward.title,
        code: rawCode,
        pointsSpent: result.reward.points_cost,
        pointsBalance: result.balance,
      },
      warning: 'El código de canje se muestra una sola vez. No se almacena en texto plano.',
    });
  });
}
