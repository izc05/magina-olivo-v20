import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';

const statusSchema = z.enum(['reserved', 'redeemed', 'cancelled', 'expired']);

export function registerAdminAlmazaraRewardRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/almazara-rewards', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const query = z.object({
      status: statusSchema.optional(),
      businessId: z.string().uuid().optional(),
      limit: z.coerce.number().int().min(1).max(500).default(200),
    }).safeParse(request.query ?? {});
    if (!query.success) return reply.code(400).send({ error: 'invalid_almazara_reward_filters' });
    const status = query.data.status ?? null;
    const businessId = query.data.businessId ?? null;

    const [products, redemptions, stats] = await Promise.all([
      sql<{
        id: string; business_id: string; business_name: string; slug: string; title: string; olive_cost: number;
        stock_total: number; stock_reserved: number; stock_redeemed: number; status: string; updated_at: Date;
      }>`
        SELECT p.id, p.business_id, b.name AS business_name, p.slug, p.title, p.olive_cost,
               p.stock_total, p.stock_reserved, p.stock_redeemed, p.status, p.updated_at
        FROM mill_reward_products p JOIN businesses b ON b.id=p.business_id
        WHERE (${businessId}::uuid IS NULL OR p.business_id=${businessId}::uuid)
        ORDER BY b.name, p.updated_at DESC
      `.execute(auth.database),
      sql<{
        id: string; business_id: string; business_name: string; product_title: string; user_id: string;
        olives_spent: number; status: string; expires_at: Date; redeemed_at: Date | null; created_at: Date;
      }>`
        SELECT r.id, r.business_id, b.name AS business_name, p.title AS product_title, r.user_id,
               r.olives_spent, r.status, r.expires_at, r.redeemed_at, r.created_at
        FROM mill_reward_redemptions r
        JOIN businesses b ON b.id=r.business_id
        JOIN mill_reward_products p ON p.id=r.product_id
        WHERE (${status}::text IS NULL OR r.status=${status}::text)
          AND (${businessId}::uuid IS NULL OR r.business_id=${businessId}::uuid)
        ORDER BY r.created_at DESC LIMIT ${query.data.limit}
      `.execute(auth.database),
      sql<{ reserved: number; redeemed: number; cancelled: number; expired: number; olives_spent: number }>`
        SELECT
          count(*) FILTER (WHERE status='reserved')::int AS reserved,
          count(*) FILTER (WHERE status='redeemed')::int AS redeemed,
          count(*) FILTER (WHERE status='cancelled')::int AS cancelled,
          count(*) FILTER (WHERE status='expired')::int AS expired,
          COALESCE(sum(olives_spent) FILTER (WHERE status='redeemed'),0)::int AS olives_spent
        FROM mill_reward_redemptions
        WHERE (${businessId}::uuid IS NULL OR business_id=${businessId}::uuid)
      `.execute(auth.database),
    ]);

    const row = stats.rows[0] ?? { reserved: 0, redeemed: 0, cancelled: 0, expired: 0, olives_spent: 0 };
    return {
      products: products.rows.map((item) => ({ ...item, available_stock: Math.max(0, item.stock_total - item.stock_reserved - item.stock_redeemed) })),
      redemptions: redemptions.rows,
      stats: row,
    };
  });

  app.post('/api/v1/admin/almazara-rewards/:id/pause', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_reward_id' });
    const updated = await sql<{ id: string; title: string }>`
      UPDATE mill_reward_products SET status='paused', updated_by=${auth.access.userId}::uuid, updated_at=now()
      WHERE id=${params.data.id}::uuid AND status <> 'archived'
      RETURNING id::text, title
    `.execute(auth.database);
    const reward = updated.rows[0];
    if (!reward) return reply.code(404).send({ error: 'reward_not_found' });
    await auditAdminAction(
      auth.database,
      auth.access,
      'almazara_reward.paused',
      'mill_reward_product',
      reward.id,
      { title: reward.title },
    );
    return { reward: { id: reward.id, status: 'paused' } };
  });
}
