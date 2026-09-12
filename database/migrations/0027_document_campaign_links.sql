BEGIN;

ALTER TABLE attachment_links
  ADD COLUMN campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL;

CREATE INDEX attachment_links_campaign_idx
  ON attachment_links(workspace_id, campaign_id, created_at DESC)
  WHERE campaign_id IS NOT NULL;

COMMIT;
