BEGIN;

CREATE TABLE mi_olivo_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mi_olivo_ledger (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL,
  source_type TEXT NOT NULL,
  source_id TEXT,
  points INTEGER NOT NULL CHECK (points <> 0),
  reason TEXT NOT NULL,
  rule_version TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  reversal_of UUID REFERENCES mi_olivo_ledger(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT mi_olivo_ledger_reversal_check CHECK (
    (points > 0 AND reversal_of IS NULL) OR
    (points < 0 AND reversal_of IS NOT NULL)
  ),
  UNIQUE (user_id, idempotency_key)
);

CREATE INDEX mi_olivo_ledger_user_date_idx
  ON mi_olivo_ledger(user_id, created_at DESC);
CREATE INDEX mi_olivo_ledger_workspace_idx
  ON mi_olivo_ledger(workspace_id, created_at DESC)
  WHERE workspace_id IS NOT NULL;
CREATE INDEX mi_olivo_ledger_event_idx
  ON mi_olivo_ledger(user_id, event_type);

COMMIT;
