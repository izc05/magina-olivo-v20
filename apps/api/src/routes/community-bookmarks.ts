import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const categories = ['campo','preguntas','plagas','maquinaria','cosecha','pueblos','gastronomia','rutas'] as const;
const querySchema = z.object({
  category: z.enum(categories).optional(),
  municipality: z.string().trim().min(1).max(120).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

type SavedRow = {
  id: string;
  category: string;
  body: string;
  media_url: string | null;
  created_at: Date | string;
  edited_at: Date | string | null;
  author_id: string;
  author_name: string;
  author_avatar_url: string | null;
  municipality_slug: string | null;
  municipality_name: string | null;
  reaction_count: number;
  comment_count: number;
  viewer_liked: boolean;
  viewer_bookmarked: boolean;
  bookmarked_at: Date | string;
};

function parseQuery(value: unknown, reply: FastifyReply) {
  const result = querySchema.safeParse(value);
  if (!result.success) {
    void reply.code(400).send({
      error: 'validation_error',
      issues: result.error.issues.map((issue) => ({ path: issue.path.join('.'), message: issue.message })),
    });
    return null;
  }
  return result.data;
}

export function registerCommunityBookmarkRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/community/bookmarks', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const query = parseQuery(request.query, reply);
    if (!query) return;
    const category = query.category ?? null;
    const municipality = query.municipality ?? null;

    const result = await sql<SavedRow>`
      SELECT
        p.id::text AS id,
        p.category,
        p.body,
        p.media_url,
        p.created_at,
        p.edited_at,
        u.id::text AS author_id,
        CASE
          WHEN up.visibility = 'public' AND up.display_name_override IS NOT NULL THEN up.display_name_override
          ELSE u.display_name
        END AS author_name,
        CASE WHEN up.visibility = 'public' THEN u.avatar_url ELSE NULL END AS author_avatar_url,
        m.slug AS municipality_slug,
        m.name AS municipality_name,
        (SELECT COUNT(*)::int FROM community_reactions r WHERE r.post_id = p.id AND r.reaction = 'like') AS reaction_count,
        (SELECT COUNT(*)::int FROM community_comments c WHERE c.post_id = p.id AND c.status = 'published') AS comment_count,
        EXISTS(
          SELECT 1 FROM community_reactions r
          WHERE r.post_id = p.id AND r.user_id = ${userId}::uuid AND r.reaction = 'like'
        ) AS viewer_liked,
        true AS viewer_bookmarked,
        b.created_at AS bookmarked_at
      FROM community_bookmarks b
      JOIN community_posts p ON p.id = b.post_id AND p.status = 'published'
      JOIN users u ON u.id = p.author_user_id AND u.status = 'active'
      LEFT JOIN user_profiles up ON up.user_id = u.id
      LEFT JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE b.user_id = ${userId}::uuid
        AND (${category}::text IS NULL OR p.category = ${category})
        AND (${municipality}::text IS NULL OR m.slug = ${municipality})
      ORDER BY b.created_at DESC, p.id DESC
      LIMIT ${query.limit}
    `.execute(database);

    return { items: result.rows, categories };
  });
}
