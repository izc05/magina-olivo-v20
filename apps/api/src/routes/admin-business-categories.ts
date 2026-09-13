import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const categorySchema = z.object({
  slug: z.string().trim().regex(slugPattern),
  name: z.string().trim().min(2).max(120),
  description: z.string().trim().max(1_000).nullable().optional(),
  iconKey: z.string().trim().max(120).nullable().optional(),
  parentId: z.string().uuid().nullable().optional(),
  sortOrder: z.number().int().min(0).max(10_000).default(0),
  active: z.boolean().default(true),
});

const categoryPatchSchema = categorySchema.partial().refine((value) => Object.keys(value).length > 0, {
  message: 'at_least_one_change_required',
});

export function registerAdminBusinessCategoryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/admin/business-categories', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const parsed = categorySchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_category', issues: parsed.error.issues });
    const input = parsed.data;

    const inserted = await sql<{ id: string }>`
      INSERT INTO business_categories (slug, name, description, icon_key, parent_id, sort_order, active)
      VALUES (${input.slug}, ${input.name}, ${input.description ?? null}, ${input.iconKey ?? null},
              ${input.parentId ?? null}::uuid, ${input.sortOrder}, ${input.active})
      RETURNING id
    `.execute(auth.database);
    const id = inserted.rows[0]?.id;
    if (!id) return reply.code(500).send({ error: 'business_category_create_failed' });
    await auditAdminAction(auth.database, auth.access, 'business.category_created', 'business_category', id, {
      slug: input.slug,
      name: input.name,
    });
    return reply.code(201).send({ category: { id, ...input } });
  });

  app.patch('/api/v1/admin/business-categories/:id', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_business_category_id' });
    const parsed = categoryPatchSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_category_patch', issues: parsed.error.issues });
    const input = parsed.data;
    if (input.parentId === params.data.id) return reply.code(400).send({ error: 'business_category_cannot_parent_itself' });

    const existing = await sql<{ id: string }>`SELECT id FROM business_categories WHERE id = ${params.data.id}::uuid LIMIT 1`.execute(auth.database);
    if (!existing.rows[0]) return reply.code(404).send({ error: 'business_category_not_found' });

    await sql`
      UPDATE business_categories SET
        slug = CASE WHEN ${input.slug !== undefined} THEN ${input.slug ?? ''} ELSE slug END,
        name = CASE WHEN ${input.name !== undefined} THEN ${input.name ?? ''} ELSE name END,
        description = CASE WHEN ${input.description !== undefined} THEN ${input.description ?? null} ELSE description END,
        icon_key = CASE WHEN ${input.iconKey !== undefined} THEN ${input.iconKey ?? null} ELSE icon_key END,
        parent_id = CASE WHEN ${input.parentId !== undefined} THEN ${input.parentId ?? null}::uuid ELSE parent_id END,
        sort_order = CASE WHEN ${input.sortOrder !== undefined} THEN ${input.sortOrder ?? 0} ELSE sort_order END,
        active = CASE WHEN ${input.active !== undefined} THEN ${input.active ?? true} ELSE active END,
        updated_at = now()
      WHERE id = ${params.data.id}::uuid
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'business.category_updated', 'business_category', params.data.id, {
      fields: Object.keys(input),
    });
    return { ok: true, categoryId: params.data.id };
  });
}
