import { createHash, randomBytes } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';

const programSchema = z.object({
  slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  name: z.string().trim().min(2).max(180),
  description: z.string().trim().max(5000).nullable().optional(),
  programType: z.enum(['points', 'stamps', 'challenge']).default('points'),
  defaultCheckinPoints: z.number().int().min(1).max(10000).default(10),
  defaultCooldownHours: z.number().int().min(1).max(720).default(20),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  status: z.enum(['draft', 'published', 'archived']).default('draft'),
}).superRefine((value, context) => {
  if (value.validFrom && value.validUntil && value.validUntil < value.validFrom) {
    context.addIssue({ code: 'custom', path: ['validUntil'], message: 'valid_until_before_start' });
  }
});

const stopSchema = z.object({
  businessId: z.string().uuid(),
  checkinPoints: z.number().int().min(1).max(10000).nullable().optional(),
  cooldownHours: z.number().int().min(1).max(720).nullable().optional(),
  featuredStop: z.boolean().default(false),
  active: z.boolean().default(true),
  sortOrder: z.number().int().min(0).max(10000).default(0),
});

const rewardSchema = z.object({
  businessId: z.string().uuid().nullable().optional(),
  title: z.string().trim().min(2).max(180),
  description: z.string().trim().max(3000).nullable().optional(),
  pointsCost: z.number().int().min(1).max(100000),
  rewardType: z.enum(['benefit', 'discount', 'gift', 'experience', 'offer']).default('benefit'),
  redemptionInstructions: z.string().trim().max(1500).nullable().optional(),
  maxRedemptions: z.number().int().min(1).nullable().optional(),
  validFrom: z.coerce.date().nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
  status: z.enum(['draft', 'published', 'archived']).default('draft'),
}).superRefine((value, context) => {
  if (value.validFrom && value.validUntil && value.validUntil < value.validFrom) {
    context.addIssue({ code: 'custom', path: ['validUntil'], message: 'valid_until_before_start' });
  }
});

const qrSchema = z.object({
  label: z.string().trim().max(120).nullable().optional(),
  validUntil: z.coerce.date().nullable().optional(),
});

function hashToken(value: string) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

export function registerAdminBusinessPassRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/magina-pass', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const [programs, stops, rewards] = await Promise.all([
      sql<any>`
        SELECT p.*,
          (SELECT count(*)::int FROM magina_pass_businesses pb WHERE pb.program_id=p.id AND pb.active=true) AS business_count,
          (SELECT count(*)::int FROM magina_pass_wallets w WHERE w.program_id=p.id) AS wallet_count,
          (SELECT count(*)::int FROM magina_pass_checkins c WHERE c.program_id=p.id) AS checkin_count,
          (SELECT count(*)::int FROM magina_pass_redemptions r WHERE r.program_id=p.id AND r.status IN ('issued','redeemed')) AS redemption_count
        FROM magina_pass_programs p ORDER BY p.updated_at DESC
      `.execute(auth.database),
      sql<any>`
        SELECT pb.program_id, pb.business_id, b.name AS business_name, b.slug AS business_slug,
               pb.checkin_points, pb.cooldown_hours, pb.featured_stop, pb.active, pb.sort_order,
               (SELECT count(*)::int FROM magina_pass_qr_tokens qt WHERE qt.program_id=pb.program_id AND qt.business_id=pb.business_id AND qt.active=true AND qt.revoked_at IS NULL) AS active_qr_count
        FROM magina_pass_businesses pb JOIN businesses b ON b.id=pb.business_id
        ORDER BY pb.program_id, pb.featured_stop DESC, pb.sort_order, b.name
      `.execute(auth.database),
      sql<any>`
        SELECT r.*, b.name AS business_name,
               (SELECT count(*)::int FROM magina_pass_redemptions rd WHERE rd.reward_id=r.id AND rd.status IN ('issued','redeemed')) AS redemption_count
        FROM magina_pass_rewards r LEFT JOIN businesses b ON b.id=r.business_id
        ORDER BY r.program_id, r.points_cost, r.title
      `.execute(auth.database),
    ]);
    return { programs: programs.rows, stops: stops.rows, rewards: rewards.rows };
  });

  app.post('/api/v1/admin/magina-pass/programs', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const parsed = programSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_pass_program', issues: parsed.error.issues });
    const input = parsed.data;
    const result = await sql<{ id: string }>`
      INSERT INTO magina_pass_programs (
        slug, name, description, program_type, status, valid_from, valid_until,
        default_checkin_points, default_cooldown_hours, created_by, updated_by, published_at
      ) VALUES (
        ${input.slug}, ${input.name}, ${input.description ?? null}, ${input.programType}, ${input.status},
        ${input.validFrom ?? null}, ${input.validUntil ?? null}, ${input.defaultCheckinPoints}, ${input.defaultCooldownHours},
        ${auth.access.userId}::uuid, ${auth.access.userId}::uuid,
        CASE WHEN ${input.status}='published' THEN now() ELSE NULL END
      ) RETURNING id
    `.execute(auth.database);
    const id = result.rows[0]?.id;
    if (!id) return reply.code(500).send({ error: 'pass_program_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'magina_pass.program_created', 'magina_pass_program', id, { slug: input.slug, status: input.status });
    return reply.code(201).send({ program: { id } });
  });

  app.post('/api/v1/admin/magina-pass/programs/:id/businesses', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const parsed = stopSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_pass_stop' });
    const input = parsed.data;
    await sql`
      INSERT INTO magina_pass_businesses (
        program_id, business_id, checkin_points, cooldown_hours, featured_stop, active, sort_order
      ) VALUES (
        ${params.data.id}::uuid, ${input.businessId}::uuid, ${input.checkinPoints ?? null},
        ${input.cooldownHours ?? null}, ${input.featuredStop}, ${input.active}, ${input.sortOrder}
      ) ON CONFLICT (program_id, business_id) DO UPDATE SET
        checkin_points=EXCLUDED.checkin_points, cooldown_hours=EXCLUDED.cooldown_hours,
        featured_stop=EXCLUDED.featured_stop, active=EXCLUDED.active, sort_order=EXCLUDED.sort_order,
        updated_at=now()
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'magina_pass.business_upserted', 'magina_pass_program', params.data.id, { businessId: input.businessId });
    return { ok: true };
  });

  app.post('/api/v1/admin/magina-pass/programs/:id/businesses/:businessId/qr', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid(), businessId: z.string().uuid() }).safeParse(request.params);
    const parsed = qrSchema.safeParse(request.body ?? {});
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_pass_qr_request' });

    const stop = await sql<{ exists: boolean }>`
      SELECT true AS exists FROM magina_pass_businesses
      WHERE program_id=${params.data.id}::uuid AND business_id=${params.data.businessId}::uuid AND active=true
      LIMIT 1
    `.execute(auth.database);
    if (!stop.rows[0]) return reply.code(404).send({ error: 'pass_business_not_active' });

    const rawToken = randomBytes(24).toString('base64url');
    const tokenHash = hashToken(rawToken);
    const result = await sql<{ id: string }>`
      INSERT INTO magina_pass_qr_tokens (
        program_id, business_id, token_hash, label, active, valid_from, valid_until, created_by
      ) VALUES (
        ${params.data.id}::uuid, ${params.data.businessId}::uuid, ${tokenHash}, ${parsed.data.label ?? null},
        true, now(), ${parsed.data.validUntil ?? null}, ${auth.access.userId}::uuid
      ) RETURNING id
    `.execute(auth.database);
    const id = result.rows[0]?.id;
    await auditAdminAction(auth.database, auth.access, 'magina_pass.qr_created', 'magina_pass_qr_token', id ?? null, { programId: params.data.id, businessId: params.data.businessId });
    return reply.code(201).send({
      qr: { id, code: rawToken, payload: `MAGINA_PASS:${rawToken}` },
      warning: 'El token QR se devuelve una sola vez. Solo se almacena su hash SHA-256.',
    });
  });

  app.post('/api/v1/admin/magina-pass/programs/:id/rewards', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const parsed = rewardSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'invalid_pass_reward' });
    const input = parsed.data;
    const result = await sql<{ id: string }>`
      INSERT INTO magina_pass_rewards (
        program_id, business_id, title, description, points_cost, reward_type,
        redemption_instructions, max_redemptions, valid_from, valid_until, status
      ) VALUES (
        ${params.data.id}::uuid, ${input.businessId ?? null}::uuid, ${input.title}, ${input.description ?? null},
        ${input.pointsCost}, ${input.rewardType}, ${input.redemptionInstructions ?? null}, ${input.maxRedemptions ?? null},
        ${input.validFrom ?? null}, ${input.validUntil ?? null}, ${input.status}
      ) RETURNING id
    `.execute(auth.database);
    const id = result.rows[0]?.id;
    if (!id) return reply.code(500).send({ error: 'pass_reward_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'magina_pass.reward_created', 'magina_pass_reward', id, { programId: params.data.id, title: input.title });
    return reply.code(201).send({ reward: { id } });
  });
}
