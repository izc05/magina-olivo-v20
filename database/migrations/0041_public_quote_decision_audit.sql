ALTER TABLE professional_public_share_links
  ADD COLUMN IF NOT EXISTS delivery_id uuid REFERENCES professional_document_deliveries(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS professional_public_share_links_delivery_idx
  ON professional_public_share_links (workspace_id, delivery_id)
  WHERE delivery_id IS NOT NULL;

ALTER TABLE professional_quote_decisions
  ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE professional_quote_decisions
  ADD COLUMN IF NOT EXISTS share_link_id uuid REFERENCES professional_public_share_links(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS decision_source text NOT NULL DEFAULT 'internal' CHECK (decision_source IN ('internal','public_link')),
  ADD COLUMN IF NOT EXISTS requester_ip inet,
  ADD COLUMN IF NOT EXISTS user_agent text,
  ADD COLUMN IF NOT EXISTS customer_name text;

CREATE INDEX IF NOT EXISTS professional_quote_decisions_share_idx
  ON professional_quote_decisions (share_link_id, decided_at DESC)
  WHERE share_link_id IS NOT NULL;

COMMENT ON COLUMN professional_public_share_links.delivery_id IS 'Concrete prepared/confirmed delivery associated with this public link when available.';
COMMENT ON COLUMN professional_quote_decisions.requester_ip IS 'Minimal audit metadata for public quote decisions. Treat as personal data and apply normal retention/privacy controls.';
COMMENT ON COLUMN professional_quote_decisions.user_agent IS 'Truncated user-agent captured for public decision audit; not used as identity proof.';
