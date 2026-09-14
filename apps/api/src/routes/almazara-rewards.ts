import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const editableRoles = new Set(['owner', 'manager', 'editor']);
const scannerRoles = new Set(['owner', 'manager', 'editor']);
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

const productSchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  title: z.string().trim().min(2).max(180),
  description: z.string().trim().max(3_000).nullable().optional(),
  imageUrl: z.string().url().max(2_000).nullable().optional(),
  volumeMl: z.number().int().positive().max(20_000).nullable().optional(),
  oliveCost: z.number().int().min(1).max(1_000_000),
  stockTotal: z.number().int().min(0).max(1_000_000),
  maxPerUser: z.number().int().min(1).max(100).nullable().optional(),
  status: z.enum(['draft', 'published', 'paused', 'archived']).default('draft'),
  startsAt: z.coerce.date().nullable().optional(),
  endsAt: z.coerce.date().nullable().optional(),
}).superRefine((value, context) => {
  if (value.startsAt && value.endsAt && value.endsAt < value.startsAt) {
    context.addIssue({ code: 'custom', path: ['endsAt'], message: 'reward_ends_before_start' });
  }
});

const millProfileSchema = z.object({
  millKind: z.enum(['cooperativa', 'almazara', 'productor']).default('almazara'),
  oliveVarieties: z.array(z.string().trim().min(1).max(80)).max(20).default([]),
  certifications: z.array(z.record(z.string(), z.unknown())).max(30).default([]),
  services: z.array(z.record(z.string(), z.unknown())).max(30).default([]),
  hasShop: z.boolean().default(false),
  acceptsVisits: z.boolean().default(false),
  campaignNotes: z.string().trim().max(2_000).nullable().optional(),
});

async function membership(database: DatabaseClient, businessId: string, userId: string) {
  const result = await sql<{ role: 'owner' | 'manager' | 'editor' | 'analyst' }>`
    SELECT role FROM business_memberships
    WHERE business_id = ${businessId}::uuid
      AND user_id = ${userId}::uuid
      AND status = 'active'
    LIMIT 1
  `.execute(database);
  return result.rows[0] ?? null;
}

export function registerAlmazaraRewardRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/almazaras', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const query = z.object({ q: z.string().trim().max(120).optional(), limit: z.coerce.number().int().min(1).max(100).default(50) }).safeParse(request.query ?? {});
    if (!query.success) return reply.code(400).send({ error: 'invalid_almazara_filters' });
    const q = query.data.q || null;
    const result = await sql<{
      id: string; slug: string; name: string; short_description: string | null; municipality_name: string | null;
      address: string | null; phone: string | null; website: string | null; logo_url: string | null; cover_image_url: string | null;
      mill_kind: string | null; olive_varieties: string[] | null; certifications: unknown; services: unknown;
      has_shop: boolean | null; accepts_visits: boolean | null; reward_count: number;
    }>`
      SELECT b.id, b.slug, b.name, b.short_description, m.name AS municipality_name,
             b.address, b.phone, b.website, b.logo_url, b.cover_image_url,
             mp.mill_kind, mp.olive_varieties, mp.certifications, mp.services, mp.has_shop, mp.accepts_visits,
             COUNT(rp.id) FILTER (
               WHERE rp.status = 'published'
                 AND (rp.starts_at IS NULL OR rp.starts_at <= now())
                 AND (rp.ends_at IS NULL OR rp.ends_at >= now())
                 AND rp.stock_total > rp.stock_reserved + rp.stock_redeemed
             )::int AS reward_count
      FROM businesses b
      JOIN business_category_links bcl ON bcl.business_id = b.id
      JOIN business_categories bc ON bc.id = bcl.category_id AND bc.slug = 'cooperativas-almazaras'
      LEFT JOIN territory_municipalities m ON m.id = b.municipality_id
      LEFT JOIN business_mill_profiles mp ON mp.business_id = b.id
      LEFT JOIN mill_reward_products rp ON rp.business_id = b.id
      WHERE b.status = 'published'
        AND (${q}::text IS NULL OR b.name ILIKE '%' || ${q}::text || '%' OR COALESCE(m.name, '') ILIKE '%' || ${q}::text || '%')
      GROUP BY b.id, m.name, mp.business_id
      ORDER BY b.sponsored DESC, b.featured DESC, b.priority DESC, b.name
      LIMIT ${query.data.limit}
    `.execute(database);
    return { almazaras: result.rows.map((row) => ({
      id: row.id, slug: row.slug, name: row.name, shortDescription: row.short_description,
      municipalityName: row.municipality_name, address: row.address, phone: row.phone, website: row.website,
      logoUrl: row.logo_url, coverImageUrl: row.cover_image_url, millKind: row.mill_kind,
      oliveVarieties: row.olive_varieties ?? [], certifications: Array.isArray(row.certifications) ? row.certifications : [],
      services: Array.isArray(row.services) ? row.services : [], hasShop: row.has_shop ?? false,
      acceptsVisits: row.accepts_visits ?? false, rewardCount: Number(row.reward_count),
    })) };
  });

  app.get('/api/v1/public/almazaras/:slug/rewards', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = z.object({ slug: z.string().regex(slugPattern) }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_almazara_slug' });
    const result = await sql<{
      id: string; business_id: string; business_name: string; slug: string; title: string; description: string | null;
      image_url: string | null; volume_ml: number | null; olive_cost: number; stock_total: number; stock_reserved: number;
      stock_redeemed: number; max_per_user: number | null; starts_at: Date | null; ends_at: Date | null;
    }>`
      SELECT rp.id, rp.business_id, b.name AS business_name, rp.slug, rp.title, rp.description, rp.image_url,
             rp.volume_ml, rp.olive_cost, rp.stock_total, rp.stock_reserved, rp.stock_redeemed,
             rp.max_per_user, rp.starts_at, rp.ends_at
      FROM mill_reward_products rp
      JOIN businesses b ON b.id = rp.business_id
      WHERE b.slug = ${params.data.slug} AND b.status = 'published' AND rp.status = 'published'
        AND (rp.starts_at IS NULL OR rp.starts_at <= now())
        AND (rp.ends_at IS NULL OR rp.ends_at >= now())
      ORDER BY rp.olive_cost, rp.title
    `.execute(database);
    if (!result.rows.length) {
      const exists = await sql<{ exists: boolean }>`SELECT EXISTS(SELECT 1 FROM businesses WHERE slug=${params.data.slug} AND status='published') AS exists`.execute(database);
      if (!exists.rows[0]?.exists) return reply.code(404).send({ error: 'almazara_not_found' });
    }
    return { rewards: result.rows.map((row) => ({
      id: row.id, businessId: row.business_id, businessName: row.business_name, slug: row.slug, title: row.title,
      description: row.description, imageUrl: row.image_url, volumeMl: row.volume_ml, oliveCost: row.olive_cost,
      availableStock: Math.max(0, row.stock_total - row.stock_reserved - row.stock_redeemed), maxPerUser: row.max_per_user,
      startsAt: row.starts_at, endsAt: row.ends_at,
    })) };
  });

  app.post('/api/v1/almazara-rewards/:id/redeem', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_reward_id' });

    try {
      const redemption = await database.transaction().execute(async (trx) => {
        const productResult = await sql<{
          id: string; business_id: string; title: string; olive_cost: number; stock_total: number; stock_reserved: number;
          stock_redeemed: number; max_per_user: number | null; business_name: string;
        }>`
          SELECT rp.id, rp.business_id, rp.title, rp.olive_cost, rp.stock_total, rp.stock_reserved,
                 rp.stock_redeemed, rp.max_per_user, b.name AS business_name
          FROM mill_reward_products rp JOIN businesses b ON b.id = rp.business_id
          WHERE rp.id = ${params.data.id}::uuid AND rp.status = 'published' AND b.status = 'published'
            AND (rp.starts_at IS NULL OR rp.starts_at <= now())
            AND (rp.ends_at IS NULL OR rp.ends_at >= now())
          FOR UPDATE OF rp
        `.execute(trx);
        const product = productResult.rows[0];
        if (!product) throw new Error('reward_not_available');
        if (product.stock_reserved + product.stock_redeemed >= product.stock_total) throw new Error('reward_out_of_stock');

        if (product.max_per_user) {
          const countResult = await sql<{ total: number }>`
            SELECT COUNT(*)::int AS total FROM mill_reward_redemptions
            WHERE user_id=${userId}::uuid AND product_id=${product.id}::uuid AND status IN ('reserved','redeemed')
          `.execute(trx);
          if ((countResult.rows[0]?.total ?? 0) >= product.max_per_user) throw new Error('reward_user_limit_reached');
        }

        const balanceResult = await sql<{ balance: number }>`
          SELECT COALESCE(SUM(points), 0)::int AS balance FROM mi_olivo_ledger WHERE user_id=${userId}::uuid
        `.execute(trx);
        const balance = Math.max(0, balanceResult.rows[0]?.balance ?? 0);
        if (balance < product.olive_cost) throw new Error('insufficient_olives');

        const inserted = await sql<{ id: string; redemption_code: string; expires_at: Date }>`
          INSERT INTO mill_reward_redemptions (user_id, business_id, product_id, olives_spent, expires_at)
          VALUES (${userId}::uuid, ${product.business_id}::uuid, ${product.id}::uuid, ${product.olive_cost}, now() + interval '7 days')
          RETURNING id::text, redemption_code::text, expires_at
        `.execute(trx);
        const row = inserted.rows[0];
        if (!row) throw new Error('redemption_create_failed');

        await sql`
          INSERT INTO mi_olivo_ledger (user_id, workspace_id, event_type, source_type, source_id, points, reason, rule_version, idempotency_key)
          VALUES (${userId}::uuid, NULL, 'reward_redemption', 'mill_reward_redemption', ${row.id}, ${-product.olive_cost}, ${`Canje: ${product.title}`}, 'mi-olivo-rewards-v1', ${`mi-olivo-rewards-v1:redeem:${row.id}`})
        `.execute(trx);
        await sql`UPDATE mill_reward_products SET stock_reserved=stock_reserved+1, updated_at=now() WHERE id=${product.id}::uuid`.execute(trx);
        await sql`INSERT INTO mill_reward_redemption_audit (redemption_id, actor_user_id, event_type) VALUES (${row.id}::uuid, ${userId}::uuid, 'created')`.execute(trx);
        return { id: row.id, code: row.redemption_code, expiresAt: row.expires_at, productTitle: product.title, businessName: product.business_name, olivesSpent: product.olive_cost };
      });
      return reply.code(201).send({ redemption: { ...redemption, status: 'reserved', qrPayload: `magina-olivo://reward/${redemption.code}` } });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'reward_redemption_failed';
      const status = message === 'insufficient_olives' || message === 'reward_user_limit_reached' ? 409 : message === 'reward_out_of_stock' ? 409 : message === 'reward_not_available' ? 404 : 500;
      return reply.code(status).send({ error: message });
    }
  });

  app.get('/api/v1/my/almazara-redemptions', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const result = await sql<{
      id: string; redemption_code: string; status: string; olives_spent: number; expires_at: Date; redeemed_at: Date | null;
      created_at: Date; product_title: string; business_name: string;
    }>`
      SELECT r.id::text, r.redemption_code::text, r.status, r.olives_spent, r.expires_at, r.redeemed_at, r.created_at,
             p.title AS product_title, b.name AS business_name
      FROM mill_reward_redemptions r
      JOIN mill_reward_products p ON p.id=r.product_id JOIN businesses b ON b.id=r.business_id
      WHERE r.user_id=${userId}::uuid ORDER BY r.created_at DESC LIMIT 100
    `.execute(database);
    return { redemptions: result.rows.map((row) => ({
      id: row.id, code: row.redemption_code, status: row.status, olivesSpent: row.olives_spent,
      expiresAt: row.expires_at, redeemedAt: row.redeemed_at, createdAt: row.created_at,
      productTitle: row.product_title, businessName: row.business_name,
      qrPayload: row.status === 'reserved' ? `magina-olivo://reward/${row.redemption_code}` : null,
    })) };
  });

  app.put('/api/v1/my/businesses/:id/mill-profile', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const body = millProfileSchema.safeParse(request.body);
    if (!params.success || !body.success) return reply.code(400).send({ error: 'invalid_mill_profile' });
    const access = await membership(database, params.data.id, userId);
    if (!access || !editableRoles.has(access.role)) return reply.code(403).send({ error: 'business_edit_denied' });
    const value = body.data;
    await sql`
      INSERT INTO business_mill_profiles (business_id, mill_kind, olive_varieties, certifications, services, has_shop, accepts_visits, campaign_notes)
      VALUES (${params.data.id}::uuid, ${value.millKind}, ${value.oliveVarieties}::text[], ${JSON.stringify(value.certifications)}::jsonb,
              ${JSON.stringify(value.services)}::jsonb, ${value.hasShop}, ${value.acceptsVisits}, ${value.campaignNotes ?? null})
      ON CONFLICT (business_id) DO UPDATE SET mill_kind=EXCLUDED.mill_kind, olive_varieties=EXCLUDED.olive_varieties,
        certifications=EXCLUDED.certifications, services=EXCLUDED.services, has_shop=EXCLUDED.has_shop,
        accepts_visits=EXCLUDED.accepts_visits, campaign_notes=EXCLUDED.campaign_notes, updated_at=now()
    `.execute(database);
    return { ok: true };
  });

  app.get('/api/v1/my/businesses/:id/almazara-rewards', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_id' });
    const access = await membership(database, params.data.id, userId);
    if (!access) return reply.code(403).send({ error: 'business_access_denied' });
    const products = await sql`SELECT * FROM mill_reward_products WHERE business_id=${params.data.id}::uuid ORDER BY updated_at DESC`.execute(database);
    const redemptions = await sql`
      SELECT r.id, r.status, r.olives_spent, r.expires_at, r.redeemed_at, r.created_at, p.title AS product_title
      FROM mill_reward_redemptions r JOIN mill_reward_products p ON p.id=r.product_id
      WHERE r.business_id=${params.data.id}::uuid ORDER BY r.created_at DESC LIMIT 200
    `.execute(database);
    return { role: access.role, products: products.rows, redemptions: redemptions.rows };
  });

  app.post('/api/v1/my/businesses/:id/almazara-rewards', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const body = productSchema.safeParse(request.body);
    if (!params.success || !body.success) return reply.code(400).send({ error: 'invalid_reward_product', issues: body.success ? undefined : body.error.issues });
    const access = await membership(database, params.data.id, userId);
    if (!access || !editableRoles.has(access.role)) return reply.code(403).send({ error: 'business_edit_denied' });
    const value = body.data;
    const result = await sql<{ id: string }>`
      INSERT INTO mill_reward_products (business_id, slug, title, description, image_url, volume_ml, olive_cost, stock_total, max_per_user, status, starts_at, ends_at, created_by, updated_by)
      VALUES (${params.data.id}::uuid, ${value.slug}, ${value.title}, ${value.description ?? null}, ${value.imageUrl ?? null}, ${value.volumeMl ?? null},
              ${value.oliveCost}, ${value.stockTotal}, ${value.maxPerUser ?? null}, ${value.status}, ${value.startsAt ?? null}, ${value.endsAt ?? null}, ${userId}::uuid, ${userId}::uuid)
      RETURNING id::text
    `.execute(database);
    return reply.code(201).send({ reward: { id: result.rows[0]?.id } });
  });

  app.post('/api/v1/my/businesses/:id/almazara-redemptions/:code/redeem', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = z.object({ id: z.string().uuid(), code: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_redemption_code' });
    const access = await membership(database, params.data.id, userId);
    if (!access || !scannerRoles.has(access.role)) return reply.code(403).send({ error: 'redemption_scan_denied' });

    try {
      const result = await database.transaction().execute(async (trx) => {
        const found = await sql<{ id: string; product_id: string; status: string; expires_at: Date; title: string }>`
          SELECT r.id::text, r.product_id::text, r.status, r.expires_at, p.title
          FROM mill_reward_redemptions r JOIN mill_reward_products p ON p.id=r.product_id
          WHERE r.business_id=${params.data.id}::uuid AND r.redemption_code=${params.data.code}::uuid
          FOR UPDATE OF r
        `.execute(trx);
        const row = found.rows[0];
        if (!row) throw new Error('redemption_not_found');
        if (row.status !== 'reserved') throw new Error('redemption_not_redeemable');
        if (new Date(row.expires_at).getTime() < Date.now()) {
          await sql`UPDATE mill_reward_redemptions SET status='expired', updated_at=now() WHERE id=${row.id}::uuid`.execute(trx);
          await sql`UPDATE mill_reward_products SET stock_reserved=GREATEST(0,stock_reserved-1), updated_at=now() WHERE id=${row.product_id}::uuid`.execute(trx);
          await sql`INSERT INTO mill_reward_redemption_audit (redemption_id, actor_user_id, event_type) VALUES (${row.id}::uuid, ${userId}::uuid, 'expired')`.execute(trx);
          return { expired: true as const, id: row.id, productTitle: row.title };
        }
        await sql`
          UPDATE mill_reward_redemptions SET status='redeemed', redeemed_at=now(), redeemed_by=${userId}::uuid, updated_at=now()
          WHERE id=${row.id}::uuid
        `.execute(trx);
        await sql`
          UPDATE mill_reward_products SET stock_reserved=GREATEST(0,stock_reserved-1), stock_redeemed=stock_redeemed+1, updated_at=now()
          WHERE id=${row.product_id}::uuid
        `.execute(trx);
        await sql`INSERT INTO mill_reward_redemption_audit (redemption_id, actor_user_id, event_type) VALUES (${row.id}::uuid, ${userId}::uuid, 'redeemed')`.execute(trx);
        return { expired: false as const, id: row.id, productTitle: row.title };
      });
      if (result.expired) return reply.code(409).send({ error: 'redemption_expired' });
      return { redemption: { id: result.id, productTitle: result.productTitle, status: 'redeemed' } };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'redemption_failed';
      const status = message === 'redemption_not_found' ? 404 : message === 'redemption_not_redeemable' ? 409 : 500;
      return reply.code(status).send({ error: message });
    }
  });
}
