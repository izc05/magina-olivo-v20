BEGIN;

CREATE TABLE irrigation_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  duration_hours NUMERIC(10,2),
  water_m3 NUMERIC(14,3),
  cost_eur NUMERIC(12,2),
  notes TEXT,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE TABLE treatment_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  reason TEXT NOT NULL,
  product_name TEXT NOT NULL,
  dose TEXT,
  quantity TEXT,
  applicator TEXT,
  equipment TEXT,
  cost_eur NUMERIC(12,2),
  notes TEXT,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE TABLE fertilization_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  product_name TEXT NOT NULL,
  quantity_kg NUMERIC(14,3),
  application_method TEXT,
  composition TEXT,
  cost_eur NUMERIC(12,2),
  supplier TEXT,
  notes TEXT,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE TABLE pruning_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  pruning_type TEXT NOT NULL,
  workers INTEGER,
  hours NUMERIC(10,2),
  cost_eur NUMERIC(12,2),
  notes TEXT,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE TABLE expense_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_on DATE NOT NULL,
  category TEXT NOT NULL,
  concept TEXT NOT NULL,
  amount_eur NUMERIC(12,2) NOT NULL,
  notes TEXT,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE TABLE cost_ledger_projection (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  occurred_on DATE NOT NULL,
  domain_type TEXT NOT NULL,
  domain_record_id UUID NOT NULL,
  category TEXT NOT NULL,
  amount_eur NUMERIC(12,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(domain_type, domain_record_id)
);

CREATE TABLE farm_timeline_projection (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  occurred_at TIMESTAMPTZ NOT NULL,
  domain_type TEXT NOT NULL,
  domain_record_id UUID NOT NULL,
  title TEXT NOT NULL,
  summary TEXT,
  icon_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(domain_type, domain_record_id)
);

CREATE TABLE scheduled_events (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID REFERENCES fields(id) ON DELETE CASCADE,
  source_domain_type TEXT,
  source_domain_record_id UUID,
  title TEXT NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'planned' CHECK (status IN ('planned','completed','postponed','cancelled')),
  source TEXT NOT NULL CHECK (source IN ('manual','domain_followup','smart')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX irrigation_records_field_date_idx ON irrigation_records(field_id, occurred_at DESC);
CREATE INDEX treatment_records_field_date_idx ON treatment_records(field_id, occurred_at DESC);
CREATE INDEX fertilization_records_field_date_idx ON fertilization_records(field_id, occurred_at DESC);
CREATE INDEX pruning_records_field_date_idx ON pruning_records(field_id, occurred_at DESC);
CREATE INDEX expense_records_field_date_idx ON expense_records(field_id, occurred_on DESC);
CREATE INDEX cost_ledger_field_date_idx ON cost_ledger_projection(field_id, occurred_on DESC);
CREATE INDEX timeline_field_date_idx ON farm_timeline_projection(field_id, occurred_at DESC);
CREATE INDEX scheduled_events_field_date_idx ON scheduled_events(field_id, scheduled_at);

COMMIT;
