import type { FastifyInstance, FastifyReply } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireAuthenticatedUser, requireDatabase } from '../http/helpers.js';

const activityQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(30),
});

const highlightsQuerySchema = z.object({
  municipality: z.string().trim().min(1).max(120).optional(),
  limit: z.coerce.number().int().min(1).max(12).default(3),
});

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

type ActivityRow = {
  event_id: string;
  type: 'like' | 'comment' | 'reply';
  created_at: Date | string;
  post_id: string;
  post_excerpt: string;
  actor_id: string | null;
  actor_name: string;
  actor_avatar_url: string | null;
  municipality_slug: string | null;
  municipality_name: string | null;
  unread: boolean;
  unread_count: number;
  last_seen_at: Date | string;
};

type ReadStateRow = { last_seen_at: Date | string };

type HighlightRow = {
  id: string;
  category: string;
  body: string;
  media_url: string | null;
  created_at: Date | string;
  author_id: string | null;
  author_name: string;
  author_avatar_url: string | null;
  municipality_slug: string | null;
  municipality_name: string | null;
  reaction_count: number;
  comment_count: number;
  score: number;
};

export function registerCommunityActivityRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/community/activity', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;
    const query = parseQuery(activityQuerySchema, request.query, reply);
    if (!query) return;

    const result = await sql<ActivityRow>`
      WITH read_state AS (
        SELECT COALESCE(
          (SELECT last_seen_at FROM community_activity_state WHERE user_id = ${userId}::uuid),
          '1970-01-01 00:00:00+00'::timestamptz
        ) AS last_seen_at
      ), events AS (
        SELECT
          ('like:' || r.post_id::text || ':' || r.user_id::text) AS event_id,
          'like'::text AS type,
          r.created_at,
          p.id::text AS post_id,
          left(p.body, 180) AS post_excerpt,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.id::text ELSE NULL END AS actor_id,
          CASE
            WHEN actor_profile.visibility = 'public' AND actor_profile.display_name_override IS NOT NULL THEN actor_profile.display_name_override
            WHEN actor_profile.visibility = 'public' THEN actor.display_name
            ELSE 'Miembro de Mágina'
          END AS actor_name,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.avatar_url ELSE NULL END AS actor_avatar_url,
          municipality.slug AS municipality_slug,
          municipality.name AS municipality_name
        FROM community_reactions r
        JOIN community_posts p ON p.id = r.post_id AND p.status = 'published'
        JOIN users actor ON actor.id = r.user_id AND actor.status = 'active'
        LEFT JOIN user_profiles actor_profile ON actor_profile.user_id = actor.id
        LEFT JOIN territory_municipalities municipality ON municipality.id = p.municipality_id
        WHERE p.author_user_id = ${userId}::uuid
          AND r.user_id <> ${userId}::uuid
          AND r.reaction = 'like'

        UNION ALL

        SELECT
          ('comment:' || c.id::text) AS event_id,
          'comment'::text AS type,
          c.created_at,
          p.id::text AS post_id,
          left(p.body, 180) AS post_excerpt,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.id::text ELSE NULL END AS actor_id,
          CASE
            WHEN actor_profile.visibility = 'public' AND actor_profile.display_name_override IS NOT NULL THEN actor_profile.display_name_override
            WHEN actor_profile.visibility = 'public' THEN actor.display_name
            ELSE 'Miembro de Mágina'
          END AS actor_name,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.avatar_url ELSE NULL END AS actor_avatar_url,
          municipality.slug AS municipality_slug,
          municipality.name AS municipality_name
        FROM community_comments c
        JOIN community_posts p ON p.id = c.post_id AND p.status = 'published'
        JOIN users actor ON actor.id = c.author_user_id AND actor.status = 'active'
        LEFT JOIN community_comments parent ON parent.id = c.parent_comment_id
        LEFT JOIN user_profiles actor_profile ON actor_profile.user_id = actor.id
        LEFT JOIN territory_municipalities municipality ON municipality.id = p.municipality_id
        WHERE p.author_user_id = ${userId}::uuid
          AND c.status = 'published'
          AND c.author_user_id <> ${userId}::uuid
          AND (parent.id IS NULL OR parent.author_user_id <> ${userId}::uuid)

        UNION ALL

        SELECT
          ('reply:' || child.id::text) AS event_id,
          'reply'::text AS type,
          child.created_at,
          p.id::text AS post_id,
          left(p.body, 180) AS post_excerpt,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.id::text ELSE NULL END AS actor_id,
          CASE
            WHEN actor_profile.visibility = 'public' AND actor_profile.display_name_override IS NOT NULL THEN actor_profile.display_name_override
            WHEN actor_profile.visibility = 'public' THEN actor.display_name
            ELSE 'Miembro de Mágina'
          END AS actor_name,
          CASE WHEN actor_profile.visibility = 'public' THEN actor.avatar_url ELSE NULL END AS actor_avatar_url,
          municipality.slug AS municipality_slug,
          municipality.name AS municipality_name
        FROM community_comments child
        JOIN community_comments parent ON parent.id = child.parent_comment_id AND parent.status = 'published'
        JOIN community_posts p ON p.id = child.post_id AND p.status = 'published'
        JOIN users actor ON actor.id = child.author_user_id AND actor.status = 'active'
        LEFT JOIN user_profiles actor_profile ON actor_profile.user_id = actor.id
        LEFT JOIN territory_municipalities municipality ON municipality.id = p.municipality_id
        WHERE parent.author_user_id = ${userId}::uuid
          AND child.status = 'published'
          AND child.author_user_id <> ${userId}::uuid
      ), decorated AS (
        SELECT
          events.*,
          (events.created_at > read_state.last_seen_at) AS unread,
          COUNT(*) FILTER (WHERE events.created_at > read_state.last_seen_at) OVER ()::int AS unread_count,
          read_state.last_seen_at
        FROM events
        CROSS JOIN read_state
      )
      SELECT *
      FROM decorated
      ORDER BY created_at DESC, event_id DESC
      LIMIT ${query.limit}
    `.execute(database);

    const first = result.rows[0];
    const state = await sql<ReadStateRow>`
      SELECT COALESCE(
        (SELECT last_seen_at FROM community_activity_state WHERE user_id = ${userId}::uuid),
        '1970-01-01 00:00:00+00'::timestamptz
      ) AS last_seen_at
    `.execute(database);

    return {
      items: result.rows.map(({ unread_count: _unreadCount, last_seen_at: _lastSeenAt, ...item }) => item),
      unread_count: first?.unread_count ?? 0,
      last_seen_at: first?.last_seen_at ?? state.rows[0]!.last_seen_at,
    };
  });

  app.post('/api/v1/community/activity/read', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const userId = requireAuthenticatedUser(request, reply);
    if (!userId) return;

    const result = await sql<ReadStateRow>`
      INSERT INTO community_activity_state (user_id, last_seen_at, updated_at)
      VALUES (${userId}::uuid, now(), now())
      ON CONFLICT (user_id) DO UPDATE
      SET last_seen_at = excluded.last_seen_at, updated_at = now()
      RETURNING last_seen_at
    `.execute(database);

    return { last_seen_at: result.rows[0]!.last_seen_at };
  });

  app.get('/api/v1/public/community/highlights', async (request, reply) => {
    const database = requireDatabase(db, reply);
    if (!database) return;
    const query = parseQuery(highlightsQuerySchema, request.query, reply);
    if (!query) return;
    const municipality = query.municipality ?? null;

    const result = await sql<HighlightRow>`
      SELECT
        p.id::text AS id,
        p.category,
        p.body,
        p.media_url,
        p.created_at,
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
        (
          (SELECT COUNT(*)::int FROM community_reactions r WHERE r.post_id = p.id AND r.reaction = 'like') * 2
          + (SELECT COUNT(*)::int FROM community_comments c WHERE c.post_id = p.id AND c.status = 'published') * 3
        )::int AS score
      FROM community_posts p
      JOIN users u ON u.id = p.author_user_id AND u.status = 'active'
      LEFT JOIN user_profiles up ON up.user_id = u.id
      LEFT JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.status = 'published'
        AND p.created_at >= now() - interval '30 days'
        AND (${municipality}::text IS NULL OR m.slug = ${municipality})
      ORDER BY score DESC, p.created_at DESC, p.id DESC
      LIMIT ${query.limit}
    `.execute(database);

    return { items: result.rows };
  });
}
