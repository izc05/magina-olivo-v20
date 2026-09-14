import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const queueQuerySchema = z.object({
  status: z.enum(['open','reviewed','dismissed','actioned']).default('open'),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});
const moderateSchema = z.object({
  target_type: z.enum(['post','comment']),
  target_id: z.string().uuid(),
  action: z.enum(['hide','restore','delete']),
  reason: z.string().trim().min(3).max(500),
  report_id: z.string().uuid().optional().nullable(),
});
const reviewSchema = z.object({
  status: z.enum(['reviewed','dismissed','actioned']),
});
const reportParamsSchema = z.object({ id: z.string().uuid() });

function parseQuery<T extends z.ZodTypeAny>(schema: T, value: unknown, reply: FastifyReply): z.infer<T> | null {
  const result = schema.safeParse(value);
  if (!result.success) {
    void reply.code(400).send({
      error: 'validation_error',
      issues: result.error.issues.map((issue) => ({ path: issue.path.join('.'), message: issue.message })),
    });
    return null;
  }
  return result.data;
}

type ReportRow = {
  id: string;
  target_type: 'post' | 'comment';
  target_id: string;
  reason: string;
  details: string | null;
  status: string;
  created_at: Date | string;
  reporter_name: string;
  target_excerpt: string;
  target_status: string;
};

type IdRow = { id: string };

export function registerAdminCommunityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/community/reports', async (request, reply) => {
    const access = await requirePlatformAccess(request, reply, db, 'support');
    if (!access) return;
    const query = parseQuery(queueQuerySchema, request.query, reply);
    if (!query) return;

    const result = await sql<ReportRow>`
      SELECT
        r.id::text AS id,
        r.target_type,
        COALESCE(r.post_id, r.comment_id)::text AS target_id,
        r.reason,
        r.details,
        r.status,
        r.created_at,
        reporter.display_name AS reporter_name,
        CASE
          WHEN r.target_type = 'post' THEN left(p.body, 280)
          ELSE left(c.body, 280)
        END AS target_excerpt,
        CASE
          WHEN r.target_type = 'post' THEN p.status
          ELSE c.status
        END AS target_status
      FROM community_reports r
      JOIN users reporter ON reporter.id = r.reporter_user_id
      LEFT JOIN community_posts p ON p.id = r.post_id
      LEFT JOIN community_comments c ON c.id = r.comment_id
      WHERE r.status = ${query.status}
      ORDER BY r.created_at ASC
      LIMIT ${query.limit}
    `.execute(access.database);

    return { items: result.rows, status: query.status };
  });

  app.patch('/api/v1/admin/community/reports/:id', async (request, reply) => {
    const access = await requirePlatformAccess(request, reply, db, 'support');
    if (!access) return;
    const params = parseQuery(reportParamsSchema, request.params, reply);
    if (!params) return;
    const body = parseBody(reviewSchema, request.body, reply);
    if (!body) return;

    const result = await sql<IdRow>`
      UPDATE community_reports
      SET status = ${body.status}, reviewed_by = ${access.access.userId}::uuid, reviewed_at = now()
      WHERE id = ${params.id}::uuid
      RETURNING id::text AS id
    `.execute(access.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'community_report_not_found' });

    await auditAdminAction(
      access.database,
      access.access,
      'community.report.review',
      'community_report',
      params.id,
      { status: body.status },
    );
    return { id: params.id, status: body.status };
  });

  app.post('/api/v1/admin/community/moderation', async (request, reply) => {
    const access = await requirePlatformAccess(request, reply, db, 'editor');
    if (!access) return;
    const body = parseBody(moderateSchema, request.body, reply);
    if (!body) return;
    const targetStatus = body.action === 'restore' ? 'published' : body.action === 'delete' ? 'deleted' : 'hidden';

    let updated: IdRow | undefined;
    if (body.target_type === 'post') {
      const result = await sql<IdRow>`
        UPDATE community_posts
        SET status = ${targetStatus}, moderation_reason = ${body.reason}, updated_at = now()
        WHERE id = ${body.target_id}::uuid
        RETURNING id::text AS id
      `.execute(access.database);
      updated = result.rows[0];
    } else {
      const result = await sql<IdRow>`
        UPDATE community_comments
        SET status = ${targetStatus}, moderation_reason = ${body.reason}, updated_at = now()
        WHERE id = ${body.target_id}::uuid
        RETURNING id::text AS id
      `.execute(access.database);
      updated = result.rows[0];
    }
    if (!updated) return reply.code(404).send({ error: 'community_target_not_found' });

    if (body.report_id) {
      await sql`
        UPDATE community_reports
        SET status = 'actioned', reviewed_by = ${access.access.userId}::uuid, reviewed_at = now()
        WHERE id = ${body.report_id}::uuid
      `.execute(access.database);
    } else {
      if (body.target_type === 'post') {
        await sql`
          UPDATE community_reports
          SET status = 'actioned', reviewed_by = ${access.access.userId}::uuid, reviewed_at = now()
          WHERE target_type = 'post' AND post_id = ${body.target_id}::uuid AND status = 'open'
        `.execute(access.database);
      } else {
        await sql`
          UPDATE community_reports
          SET status = 'actioned', reviewed_by = ${access.access.userId}::uuid, reviewed_at = now()
          WHERE target_type = 'comment' AND comment_id = ${body.target_id}::uuid AND status = 'open'
        `.execute(access.database);
      }
    }

    await auditAdminAction(
      access.database,
      access.access,
      `community.${body.target_type}.${body.action}`,
      `community_${body.target_type}`,
      body.target_id,
      { reason: body.reason, report_id: body.report_id ?? null },
    );

    return { id: body.target_id, status: targetStatus };
  });
}
