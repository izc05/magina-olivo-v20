BEGIN;

CREATE TABLE workspace_plan_subscriptions (
  workspace_id UUID PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
  plan_code TEXT NOT NULL CHECK (plan_code IN ('free','pro','professional')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','trialing','paused','cancelled')),
  source TEXT NOT NULL DEFAULT 'internal' CHECK (source IN ('internal','manual','billing')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  current_period_end TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (current_period_end IS NULL OR current_period_end > started_at)
);

CREATE INDEX workspace_plan_subscriptions_status_idx
  ON workspace_plan_subscriptions(status, plan_code);

CREATE TABLE plan_interest_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
  target_plan TEXT NOT NULL CHECK (target_plan IN ('pro','professional')),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','contacted','converted','cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, target_plan)
);

CREATE INDEX plan_interest_requests_workspace_status_idx
  ON plan_interest_requests(workspace_id, status, updated_at DESC);

COMMIT;
