CREATE TABLE IF NOT EXISTS financial_notification_preferences (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  notify_settlements BOOLEAN NOT NULL DEFAULT FALSE,
  settlement_min_eur NUMERIC(12,2) NOT NULL DEFAULT 1000 CHECK (settlement_min_eur >= 0),
  notify_document_review BOOLEAN NOT NULL DEFAULT FALSE,
  notify_ocr_failure BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS financial_notification_preferences_workspace_idx
  ON financial_notification_preferences (workspace_id);
