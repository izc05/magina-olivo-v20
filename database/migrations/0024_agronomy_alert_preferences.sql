BEGIN;

CREATE TABLE agronomy_alert_preferences (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT false,
  notify_caution BOOLEAN NOT NULL DEFAULT false,
  notify_avoid BOOLEAN NOT NULL DEFAULT true,
  lead_hours INTEGER NOT NULL DEFAULT 24 CHECK (lead_hours BETWEEN 1 AND 168),
  quiet_start TIME,
  quiet_end TIME,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, workspace_id)
);

COMMENT ON TABLE agronomy_alert_preferences IS
  'Explicit per-user opt-in for task/weather advisories. Disabled by default to avoid unsolicited or repetitive notifications.';
COMMENT ON COLUMN agronomy_alert_preferences.notify_caution IS
  'Caution-level alerts are opt-in separately because they are more frequent than avoid-level alerts.';

COMMIT;
