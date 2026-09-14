BEGIN;

ALTER TABLE community_comments
  ADD COLUMN parent_comment_id UUID REFERENCES community_comments(id) ON DELETE SET NULL;

ALTER TABLE community_comments
  ADD CONSTRAINT community_comments_no_self_reply
  CHECK (parent_comment_id IS NULL OR parent_comment_id <> id);

CREATE INDEX community_comments_parent_idx
  ON community_comments(parent_comment_id, created_at ASC)
  WHERE parent_comment_id IS NOT NULL;

CREATE TABLE community_activity_state (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01 00:00:00+00',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN community_comments.parent_comment_id IS 'Optional one-level public reply relationship. The API rejects replies to replies.';
COMMENT ON TABLE community_activity_state IS 'Account-scoped read marker for Community social activity. It is deliberately independent from workspaces, farms and the agricultural notification center.';

COMMIT;
