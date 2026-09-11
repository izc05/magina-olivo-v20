CREATE TABLE IF NOT EXISTS commercial_notification_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  enabled boolean NOT NULL DEFAULT false,
  notify_overdue_invoices boolean NOT NULL DEFAULT false,
  overdue_invoice_days integer NOT NULL DEFAULT 7 CHECK (overdue_invoice_days BETWEEN 1 AND 365),
  notify_expired_quotes boolean NOT NULL DEFAULT false,
  notify_quote_followup boolean NOT NULL DEFAULT false,
  quote_followup_days integer NOT NULL DEFAULT 7 CHECK (quote_followup_days BETWEEN 1 AND 90),
  notify_unbilled_work boolean NOT NULL DEFAULT false,
  unbilled_work_days integer NOT NULL DEFAULT 14 CHECK (unbilled_work_days BETWEEN 1 AND 365),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS commercial_notification_preferences_enabled_idx
  ON commercial_notification_preferences (workspace_id, user_id)
  WHERE enabled = true;
