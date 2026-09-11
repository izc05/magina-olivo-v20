CREATE TABLE IF NOT EXISTS professional_public_share_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  entity_type text NOT NULL CHECK (entity_type IN ('professional_quote','professional_invoice')),
  entity_id uuid NOT NULL,
  document_id uuid NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  access_count integer NOT NULL DEFAULT 0 CHECK (access_count >= 0),
  last_accessed_at timestamptz,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS professional_public_share_links_entity_idx
  ON professional_public_share_links (workspace_id, entity_type, entity_id, created_at DESC);

COMMENT ON TABLE professional_public_share_links IS 'Revocable public links to a specific archived commercial PDF. Only SHA-256 token hashes are stored.';
