CREATE TABLE IF NOT EXISTS home_priority_preferences (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  economic_weight TEXT NOT NULL DEFAULT 'normal' CHECK (economic_weight IN ('normal','reduced')),
  document_weight TEXT NOT NULL DEFAULT 'normal' CHECK (document_weight IN ('normal','reduced')),
  show_low_priority BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS home_priority_preferences_workspace_idx
  ON home_priority_preferences (workspace_id);
