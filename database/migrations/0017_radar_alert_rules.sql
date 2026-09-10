BEGIN;

CREATE TABLE radar_alert_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  radius_km NUMERIC(7,3) NOT NULL DEFAULT 10 CHECK (radius_km > 0 AND radius_km <= 80),
  min_dbz NUMERIC(7,2) NOT NULL DEFAULT 12 CHECK (min_dbz >= 12 AND min_dbz <= 80),
  cooldown_minutes INTEGER NOT NULL DEFAULT 60 CHECK (cooldown_minutes BETWEEN 15 AND 1440),
  last_triggered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT radar_alert_rules_field_workspace_fk
    FOREIGN KEY (field_id, workspace_id)
    REFERENCES fields(id, workspace_id)
    ON DELETE CASCADE,
  UNIQUE (user_id, field_id)
);

CREATE INDEX radar_alert_rules_workspace_field_idx
  ON radar_alert_rules(workspace_id, field_id)
  WHERE enabled = true;
CREATE INDEX radar_alert_rules_user_idx
  ON radar_alert_rules(user_id)
  WHERE enabled = true;

CREATE TABLE notification_intents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID,
  kind TEXT NOT NULL,
  channel TEXT NOT NULL DEFAULT 'push' CHECK (channel IN ('push')),
  source_type TEXT NOT NULL,
  source_record_id UUID NOT NULL,
  title TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160),
  body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 500),
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  dedupe_key TEXT NOT NULL UNIQUE CHECK (char_length(dedupe_key) BETWEEN 16 AND 240),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','dispatched','suppressed','failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispatched_at TIMESTAMPTZ,
  CONSTRAINT notification_intents_field_workspace_fk
    FOREIGN KEY (field_id, workspace_id)
    REFERENCES fields(id, workspace_id)
    ON DELETE CASCADE
);

CREATE INDEX notification_intents_pending_idx
  ON notification_intents(status, created_at)
  WHERE status = 'pending';
CREATE INDEX notification_intents_user_kind_idx
  ON notification_intents(user_id, kind, created_at DESC);

COMMENT ON TABLE radar_alert_rules IS
  'Per-user observational radar rules. They trigger only from validated observed reflectivity; they do not predict arrival time.';
COMMENT ON COLUMN radar_alert_rules.last_triggered_at IS
  'Atomic cooldown gate timestamp. It records rule evaluation output, not push delivery.';
COMMENT ON TABLE notification_intents IS
  'Transport-neutral notification outbox. Creating an intent does not mean a push has been sent.';

COMMIT;
