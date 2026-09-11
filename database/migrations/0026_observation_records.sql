BEGIN;

CREATE TABLE observation_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  observation_type TEXT NOT NULL,
  notes TEXT NOT NULL,
  severity TEXT CHECK (severity IS NULL OR severity IN ('low','medium','high')),
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE INDEX observation_records_field_date_idx
  ON observation_records(field_id, occurred_at DESC);

CREATE INDEX observation_records_workspace_type_idx
  ON observation_records(workspace_id, observation_type, occurred_at DESC);

COMMIT;
