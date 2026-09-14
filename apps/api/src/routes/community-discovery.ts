import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireDatabase } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';

const categories = ['campo','preguntas','plagas','maquinaria','cosecha','pueblos','gastronomia','rutas'] as const;
const sortModes = ['recent','most_commented','most_liked'] as const;

const discoverQuerySchema = z.object({
  q: z.string().trim().min(2).max(120).optional(),
  category: z.enum(categories).optional(),
  municipality: z.string().trim().min(1).max(120).optional(),
  sort: z.enum(sortModes).default('recent'),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

const memberParamsSchema = z.object({ id: z.string().uuid() });

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

type DiscoveryRow = {
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

type MemberRow = {
  id: string;
  display_name: string;
  avatar_url: string | null;
  municipality: string | null;
  bio: string | null;
  public_role: string | null;
  published_posts: number;
  published_comments: number;
  likes_received: number;
};

export function registerCommunityDiscoveryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/community/discover', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const query = parseQuery(discoverQuerySchema, request.query, reply);
    if (!query) return;

    const viewerId = readAuthenticatedUserId(request) ?? null;
    const search = query.q ?? null;
    const category = query.category ?? null;
    const municipality = query.municipality ?? null;
    const sort = query.sort;

    const result = await sql<DiscoveryRow>`
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
        AND (${search}::text IS NULL OR p.body ILIKE '%' || ${search} || '%')
        AND (${category}::text IS NULL OR p.category = ${category})
        AND (${municipality}::text IS NULL OR m.slug = ${municipality})
      ORDER BY
        CASE WHEN ${sort} = 'most_commented' THEN (
          SELECT COUNT(*) FROM community_comments c WHERE c.post_id = p.id AND c.status = 'published'
        ) END DESC,
        CASE WHEN ${sort} = 'most_liked' THEN (
          SELECT COUNT(*) FROM community_reactions r WHERE r.post_id = p.id AND r.reaction = 'like'
        ) END DESC,
        p.created_at DESC,
        p.id DESC
      LIMIT ${query.limit}
    `.execute(database);

    return {
      items: result.rows,
      query: { q: search, category, municipality, sort },
      sort_modes: sortModes,
      categories,
    };
  });

  app.get('/api/v1/public/community/members/:id', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const params = parseQuery(memberParamsSchema, request.params, reply);
    if (!params) return;

    const result = await sql<MemberRow>`
      SELECT
        u.id::text AS id,
        COALESCE(up.display_name_override, u.display_name) AS display_name,
        u.avatar_url,
        up.municipality,
        up.bio,
        up.public_role,
        (SELECT COUNT(*)::int FROM community_posts p
          WHERE p.author_user_id = u.id AND p.status = 'published') AS published_posts,
        (SELECT COUNT(*)::int FROM community_comments c
          JOIN community_posts p ON p.id = c.post_id
          WHERE c.author_user_id = u.id AND c.status = 'published' AND p.status = 'published') AS published_comments,
        (SELECT COUNT(*)::int FROM community_reactions r
          JOIN community_posts p ON p.id = r.post_id
          WHERE p.author_user_id = u.id AND p.status = 'published' AND r.reaction = 'like') AS likes_received
      FROM users u
      JOIN user_profiles up ON up.user_id = u.id AND up.visibility = 'public'
      WHERE u.id = ${params.id}::uuid
        AND u.status = 'active'
      LIMIT 1
    `.execute(database);

    const member = result.rows[0];
    if (!member) return reply.code(404).send({ error: 'community_member_not_found' });
    return { member };
  });
}
