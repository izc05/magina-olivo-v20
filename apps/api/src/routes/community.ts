import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';

const categories = ['campo','preguntas','plagas','maquinaria','cosecha','pueblos','gastronomia','rutas'] as const;
const categorySchema = z.enum(categories);
const postBodySchema = z.object({
  category: categorySchema,
  body: z.string().trim().min(1).max(2000),
  municipality_slug: z.string().trim().min(1).max(120).optional().nullable(),
  media_url: z.string().trim().regex(/^\/media\/[A-Za-z0-9_./-]+$/).max(500).optional().nullable(),
});
const commentBodySchema = z.object({
  body: z.string().trim().min(1).max(1000),
  parent_comment_id: z.string().uuid().optional().nullable(),
});
const reportBodySchema = z.object({
  target_type: z.enum(['post','comment']),
  target_id: z.string().uuid(),
  reason: z.enum(['spam','abuse','privacy','dangerous','misinformation','other']),
  details: z.string().trim().max(500).optional().nullable(),
});
const feedQuerySchema = z.object({
  category: categorySchema.optional(),
  municipality: z.string().trim().min(1).max(120).optional(),
  before: z.string().datetime({ offset: true }).optional(),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});
const postParamsSchema = z.object({ id: z.string().uuid() });

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

type FeedRow = {
  id: string;
  category: string;
  body: string;
  media_url: string | null;
  created_at: Date | string;
  edited_at: Date | string | null;
  author_id: string | null;
  author_name: string;
  author_avatar_url: string | null;
  municipality_slug: string | null;
  municipality_name: string | null;
  reaction_count: number;
  comment_count: number;
  viewer_liked: boolean;
  viewer_bookmarked: boolean;
};

type CommentRow = {
  id: string;
  body: string;
  created_at: Date | string;
  edited_at: Date | string | null;
  parent_comment_id: string | null;
  reply_to_author_name: string | null;
  author_id: string | null;
  author_name: string;
  author_avatar_url: string | null;
};

type ParentCommentRow = {
  post_id: string;
  parent_comment_id: string | null;
  status: string;
};

type IdRow = { id: string };
type ExistsRow = { exists: boolean };

async function publishedPostExists(database: DatabaseClient, postId: string) {
  const result = await sql<ExistsRow>`
    SELECT EXISTS(
      SELECT 1 FROM community_posts WHERE id = ${postId}::uuid AND status = 'published'
    ) AS exists
  `.execute(database);
  return result.rows[0]?.exists ?? false;
}

export function registerCommunityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/community', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const query = parseQuery(feedQuerySchema, request.query, reply);
    if (!query) return;
    const viewerId = readAuthenticatedUserId(request) ?? null;
    const category = query.category ?? null;
    const municipality = query.municipality ?? null;
    const before = query.before ?? null;

    const result = await sql<FeedRow>`
      SELECT
        p.id::text AS id,
        p.category,
        p.body,
        p.media_url,
        p.created_at,
        p.edited_at,
        CASE WHEN up.visibility = 'public' THEN u.id::text ELSE NULL END AS author_id,
        CASE
          WHEN up.visibility = 'public' AND up.display_name_override IS NOT NULL THEN up.display_name_override
          WHEN up.visibility = 'public' THEN u.display_name
          ELSE 'Miembro de Mágina'
        END AS author_name,
        CASE WHEN up.visibility = 'public' THEN u.avatar_url ELSE NULL END AS author_avatar_url,
        m.slug AS municipality_slug,
        m.name AS municipality_name,
        (SELECT COUNT(*)::int FROM community_reactions r WHERE r.post_id = p.id AND r.reaction = 'like') AS reaction_count,
        (SELECT COUNT(*)::int FROM community_comments c WHERE c.post_id = p.id AND c.status = 'published') AS comment_count,
        CASE WHEN ${viewerId}::uuid IS NULL THEN false ELSE EXISTS(
          SELECT 1 FROM community_reactions r
          WHERE r.post_id = p.id AND r.user_id = ${viewerId}::uuid AND r.reaction = 'like'
        ) END AS viewer_liked,
        CASE WHEN ${viewerId}::uuid IS NULL THEN false ELSE EXISTS(
          SELECT 1 FROM community_bookmarks b
          WHERE b.post_id = p.id AND b.user_id = ${viewerId}::uuid
        ) END AS viewer_bookmarked
      FROM community_posts p
      JOIN users u ON u.id = p.author_user_id AND u.status = 'active'
      LEFT JOIN user_profiles up ON up.user_id = u.id
      LEFT JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.status = 'published'
        AND (${category}::text IS NULL OR p.category = ${category})
        AND (${municipality}::text IS NULL OR m.slug = ${municipality})
        AND (${before}::timestamptz IS NULL OR p.created_at < ${before}::timestamptz)
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT ${query.limit}
    `.execute(database);

    const nextCursor = result.rows.length === query.limit
      ? new Date(result.rows[result.rows.length - 1]!.created_at).toISOString()
      : null;

    return { items: result.rows, next_cursor: nextCursor, categories };
  });

  app.get('/api/v1/public/community/posts/:id/comments', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    if (!(await publishedPostExists(database, params.id))) return reply.code(404).send({ error: 'community_post_not_found' });

    const result = await sql<CommentRow>`
      SELECT
        c.id::text AS id,
        c.body,
        c.created_at,
        c.edited_at,
        CASE WHEN parent.status = 'published' THEN parent.id::text ELSE NULL END AS parent_comment_id,
        CASE
          WHEN parent.status <> 'published' OR parent.id IS NULL THEN NULL
          WHEN parent_profile.visibility = 'public' AND parent_profile.display_name_override IS NOT NULL THEN parent_profile.display_name_override
          WHEN parent_profile.visibility = 'public' THEN parent_user.display_name
          ELSE 'Miembro de Mágina'
        END AS reply_to_author_name,
        CASE WHEN up.visibility = 'public' THEN u.id::text ELSE NULL END AS author_id,
        CASE
          WHEN up.visibility = 'public' AND up.display_name_override IS NOT NULL THEN up.display_name_override
          WHEN up.visibility = 'public' THEN u.display_name
          ELSE 'Miembro de Mágina'
        END AS author_name,
        CASE WHEN up.visibility = 'public' THEN u.avatar_url ELSE NULL END AS author_avatar_url
      FROM community_comments c
      JOIN users u ON u.id = c.author_user_id AND u.status = 'active'
      LEFT JOIN user_profiles up ON up.user_id = u.id
      LEFT JOIN community_comments parent ON parent.id = c.parent_comment_id
      LEFT JOIN users parent_user ON parent_user.id = parent.author_user_id AND parent_user.status = 'active'
      LEFT JOIN user_profiles parent_profile ON parent_profile.user_id = parent_user.id
      WHERE c.post_id = ${params.id}::uuid
        AND c.status = 'published'
      ORDER BY c.created_at ASC, c.id ASC
      LIMIT 100
    `.execute(database);

    return { items: result.rows };
  });

  app.post('/api/v1/community/posts', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const body = parseBody(postBodySchema, request.body, reply);
    if (!body) return;

    if (body.municipality_slug) {
      const municipality = await sql<ExistsRow>`
        SELECT EXISTS(
          SELECT 1 FROM territory_municipalities
          WHERE slug = ${body.municipality_slug} AND active = true
        ) AS exists
      `.execute(database);
      if (!municipality.rows[0]?.exists) return reply.code(400).send({ error: 'municipality_not_found' });
    }

    const created = await sql<IdRow>`
      INSERT INTO community_posts (author_user_id, category, body, municipality_id, media_url)
      VALUES (
        ${userId}::uuid,
        ${body.category},
        ${body.body},
        (SELECT id FROM territory_municipalities WHERE slug = ${body.municipality_slug ?? null} LIMIT 1),
        ${body.media_url ?? null}
      )
      RETURNING id::text AS id
    `.execute(database);

    return reply.code(201).send({ id: created.rows[0]!.id, status: 'published' });
  });

  app.delete('/api/v1/community/posts/:id', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;

    const result = await sql<IdRow>`
      UPDATE community_posts
      SET status = 'deleted', updated_at = now()
      WHERE id = ${params.id}::uuid
        AND author_user_id = ${userId}::uuid
        AND status <> 'deleted'
      RETURNING id::text AS id
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'community_post_not_found' });
    return reply.code(204).send();
  });

  app.post('/api/v1/community/posts/:id/reactions', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    if (!(await publishedPostExists(database, params.id))) return reply.code(404).send({ error: 'community_post_not_found' });

    await sql`
      INSERT INTO community_reactions (post_id, user_id, reaction)
      VALUES (${params.id}::uuid, ${userId}::uuid, 'like')
      ON CONFLICT (post_id, user_id, reaction) DO NOTHING
    `.execute(database);
    return reply.code(201).send({ active: true });
  });

  app.delete('/api/v1/community/posts/:id/reactions', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    await sql`
      DELETE FROM community_reactions
      WHERE post_id = ${params.id}::uuid AND user_id = ${userId}::uuid AND reaction = 'like'
    `.execute(database);
    return reply.code(204).send();
  });

  app.post('/api/v1/community/posts/:id/bookmark', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    if (!(await publishedPostExists(database, params.id))) return reply.code(404).send({ error: 'community_post_not_found' });
    await sql`
      INSERT INTO community_bookmarks (post_id, user_id)
      VALUES (${params.id}::uuid, ${userId}::uuid)
      ON CONFLICT (post_id, user_id) DO NOTHING
    `.execute(database);
    return reply.code(201).send({ active: true });
  });

  app.delete('/api/v1/community/posts/:id/bookmark', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    await sql`
      DELETE FROM community_bookmarks WHERE post_id = ${params.id}::uuid AND user_id = ${userId}::uuid
    `.execute(database);
    return reply.code(204).send();
  });

  app.post('/api/v1/community/posts/:id/comments', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    const body = parseBody(commentBodySchema, request.body, reply);
    if (!body) return;
    if (!(await publishedPostExists(database, params.id))) return reply.code(404).send({ error: 'community_post_not_found' });

    if (body.parent_comment_id) {
      const parent = await sql<ParentCommentRow>`
        SELECT post_id::text AS post_id, parent_comment_id::text AS parent_comment_id, status
        FROM community_comments
        WHERE id = ${body.parent_comment_id}::uuid
        LIMIT 1
      `.execute(database);
      const parentComment = parent.rows[0];
      if (!parentComment || parentComment.status !== 'published' || parentComment.post_id !== params.id) {
        return reply.code(400).send({ error: 'community_reply_parent_invalid' });
      }
      if (parentComment.parent_comment_id) {
        return reply.code(400).send({ error: 'community_reply_depth_exceeded' });
      }
    }

    const created = await sql<IdRow>`
      INSERT INTO community_comments (post_id, author_user_id, body, parent_comment_id)
      VALUES (${params.id}::uuid, ${userId}::uuid, ${body.body}, ${body.parent_comment_id ?? null}::uuid)
      RETURNING id::text AS id
    `.execute(database);
    return reply.code(201).send({ id: created.rows[0]!.id, status: 'published' });
  });

  app.delete('/api/v1/community/comments/:id', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const params = parseQuery(postParamsSchema, request.params, reply);
    if (!params) return;
    const result = await sql<IdRow>`
      UPDATE community_comments
      SET status = 'deleted', updated_at = now()
      WHERE id = ${params.id}::uuid
        AND author_user_id = ${userId}::uuid
        AND status <> 'deleted'
      RETURNING id::text AS id
    `.execute(database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'community_comment_not_found' });
    return reply.code(204).send();
  });

  app.post('/api/v1/community/reports', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const body = parseBody(reportBodySchema, request.body, reply);
    if (!body) return;

    let exists = false;
    if (body.target_type === 'post') {
      exists = await publishedPostExists(database, body.target_id);
    } else {
      const result = await sql<ExistsRow>`
        SELECT EXISTS(
          SELECT 1 FROM community_comments c
          JOIN community_posts p ON p.id = c.post_id
          WHERE c.id = ${body.target_id}::uuid
            AND c.status = 'published'
            AND p.status = 'published'
        ) AS exists
      `.execute(database);
      exists = result.rows[0]?.exists ?? false;
    }
    if (!exists) return reply.code(404).send({ error: 'community_target_not_found' });

    try {
      const created = await sql<IdRow>`
        INSERT INTO community_reports (
          reporter_user_id, target_type, post_id, comment_id, reason, details
        ) VALUES (
          ${userId}::uuid,
          ${body.target_type},
          ${body.target_type === 'post' ? body.target_id : null}::uuid,
          ${body.target_type === 'comment' ? body.target_id : null}::uuid,
          ${body.reason},
          ${body.details ?? null}
        )
        RETURNING id::text AS id
      `.execute(database);
      return reply.code(201).send({ id: created.rows[0]!.id, status: 'open' });
    } catch (error) {
      const code = (error as { code?: string }).code;
      if (code === '23505') return reply.code(409).send({ error: 'community_report_already_open' });
      throw error;
    }
  });
}
