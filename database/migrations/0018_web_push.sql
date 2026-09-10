BEGIN;

CREATE TABLE push_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE,
  endpoint TEXT NOT NULL CHECK (endpoint ~ '^https://'),
  endpoint_hash TEXT NOT NULL UNIQUE CHECK (endpoint_hash ~ '^[0-9a-f]{64}$'),
  p256dh TEXT NOT NULL CHECK (char_length(p256dh) BETWEEN 16 AND 512),
  auth_secret TEXT NOT NULL CHECK (char_length(auth_secret) BETWEEN 8 AND 256),
  expiration_time TIMESTAMPTZ,
  user_agent TEXT CHECK (user_agent IS NULL OR char_length(user_agent) <= 1000),
  device_label TEXT CHECK (device_label IS NULL OR char_length(device_label) <= 120),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','expired')),
  failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
  last_failure_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ
);

CREATE INDEX push_subscriptions_user_active_idx
  ON push_subscriptions(user_id, last_seen_at DESC)
  WHERE status = 'active';
CREATE INDEX push_subscriptions_session_active_idx
  ON push_subscriptions(session_id)
  WHERE status = 'active';

CREATE TABLE push_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  notification_intent_id UUID NOT NULL REFERENCES notification_intents(id) ON DELETE CASCADE,
  push_subscription_id UUID NOT NULL REFERENCES push_subscriptions(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','sending','sent','retryable_failed','permanent_failed')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  last_status_code INTEGER,
  last_error_code TEXT,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  claimed_at TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (notification_intent_id, push_subscription_id)
);

CREATE INDEX push_deliveries_due_idx
  ON push_deliveries(status, next_attempt_at)
  WHERE status IN ('pending','retryable_failed','sending');
CREATE INDEX push_deliveries_intent_idx
  ON push_deliveries(notification_intent_id);

COMMENT ON TABLE push_subscriptions IS
  'Private Web Push subscriptions bound to a concrete authenticated session. Revoking the session prevents further delivery on that device binding.';
COMMENT ON COLUMN push_subscriptions.endpoint IS
  'Capability URL required for delivery. Treat as secret: never expose in logs, analytics or public APIs.';
COMMENT ON TABLE push_deliveries IS
  'Per-intent/per-device delivery ledger. notification_intents remains the transport-neutral outbox.';

COMMIT;
