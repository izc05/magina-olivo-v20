BEGIN;

CREATE TABLE community_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK (category IN ('campo','preguntas','plagas','maquinaria','cosecha','pueblos','gastronomia','rutas')),
  body TEXT NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
  municipality_id UUID REFERENCES territory_municipalities(id) ON DELETE SET NULL,
  media_url TEXT CHECK (media_url IS NULL OR (media_url LIKE '/%' AND media_url !~ '[[:space:]]')),
  status TEXT NOT NULL DEFAULT 'published' CHECK (status IN ('published','hidden','deleted')),
  moderation_reason TEXT CHECK (moderation_reason IS NULL OR char_length(moderation_reason) <= 500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  edited_at TIMESTAMPTZ
);

CREATE INDEX community_posts_feed_idx
  ON community_posts(status, created_at DESC, id DESC);
CREATE INDEX community_posts_category_feed_idx
  ON community_posts(category, status, created_at DESC);
CREATE INDEX community_posts_municipality_feed_idx
  ON community_posts(municipality_id, status, created_at DESC)
  WHERE municipality_id IS NOT NULL;
CREATE INDEX community_posts_author_idx
  ON community_posts(author_user_id, created_at DESC);

CREATE TABLE community_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 1000),
  status TEXT NOT NULL DEFAULT 'published' CHECK (status IN ('published','hidden','deleted')),
  moderation_reason TEXT CHECK (moderation_reason IS NULL OR char_length(moderation_reason) <= 500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  edited_at TIMESTAMPTZ
);

CREATE INDEX community_comments_post_idx
  ON community_comments(post_id, status, created_at ASC);
CREATE INDEX community_comments_author_idx
  ON community_comments(author_user_id, created_at DESC);

CREATE TABLE community_reactions (
  post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reaction TEXT NOT NULL DEFAULT 'like' CHECK (reaction IN ('like')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (post_id, user_id, reaction)
);

CREATE INDEX community_reactions_user_idx
  ON community_reactions(user_id, created_at DESC);

CREATE TABLE community_bookmarks (
  post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (post_id, user_id)
);

CREATE INDEX community_bookmarks_user_idx
  ON community_bookmarks(user_id, created_at DESC);

CREATE TABLE community_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type TEXT NOT NULL CHECK (target_type IN ('post','comment')),
  post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
  comment_id UUID REFERENCES community_comments(id) ON DELETE CASCADE,
  reason TEXT NOT NULL CHECK (reason IN ('spam','abuse','privacy','dangerous','misinformation','other')),
  details TEXT CHECK (details IS NULL OR char_length(details) <= 500),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewed','dismissed','actioned')),
  reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (
    (target_type = 'post' AND post_id IS NOT NULL AND comment_id IS NULL)
    OR (target_type = 'comment' AND comment_id IS NOT NULL AND post_id IS NULL)
  )
);

CREATE UNIQUE INDEX community_reports_unique_open_post_idx
  ON community_reports(reporter_user_id, post_id)
  WHERE target_type = 'post' AND status = 'open';
CREATE UNIQUE INDEX community_reports_unique_open_comment_idx
  ON community_reports(reporter_user_id, comment_id)
  WHERE target_type = 'comment' AND status = 'open';
CREATE INDEX community_reports_moderation_queue_idx
  ON community_reports(status, created_at ASC);

COMMENT ON TABLE community_posts IS 'Public Mágina community feed. Exact farm geometry and private workspace data must never be copied into this surface by default.';
COMMENT ON TABLE community_reports IS 'User reports reviewed through platform moderation. Admin actions remain auditable through admin_audit_log.';

COMMIT;
