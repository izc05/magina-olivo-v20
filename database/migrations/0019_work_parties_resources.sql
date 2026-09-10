BEGIN;

CREATE TABLE parties (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('person','organization')),
  display_name TEXT NOT NULL,
  legal_name TEXT,
  tax_id TEXT,
  phone TEXT,
  email TEXT,
  roles TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);
CREATE INDEX parties_workspace_active_idx ON parties(workspace_id, active, display_name);

CREATE TABLE machinery (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  name TEXT NOT NULL,
  category TEXT,
  ownership TEXT NOT NULL CHECK (ownership IN ('owned','rented','third-party-service')),
  owner_party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  registration_or_serial TEXT,
  default_rate_eur NUMERIC(12,2),
  default_rate_unit TEXT CHECK (default_rate_unit IS NULL OR default_rate_unit IN ('hours','days','jornales','units','fixed')),
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);
CREATE INDEX machinery_workspace_active_idx ON machinery(workspace_id, active, name);

CREATE TABLE materials (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  name TEXT NOT NULL,
  category TEXT,
  default_unit TEXT,
  default_unit_cost_eur NUMERIC(12,2),
  supplier_party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);
CREATE INDEX materials_workspace_active_idx ON materials(workspace_id, active, name);

CREATE TABLE work_records (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  client_operation_id UUID NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('pruning','shredding','harvest','treatment','fertilization','irrigation','mowing','tillage','transport','manual-work','machinery-work','other')),
  occurred_on DATE NOT NULL,
  title TEXT NOT NULL,
  notes TEXT,
  performed_for TEXT NOT NULL DEFAULT 'self' CHECK (performed_for IN ('self','third-party')),
  customer_party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  quoted_amount_eur NUMERIC(12,2),
  charge_eur NUMERIC(12,2),
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id),
  CHECK (performed_for = 'self' OR customer_party_id IS NOT NULL)
);
CREATE INDEX work_records_field_date_idx ON work_records(field_id, occurred_on DESC);
CREATE INDEX work_records_workspace_date_idx ON work_records(workspace_id, occurred_on DESC);
CREATE INDEX work_records_customer_idx ON work_records(customer_party_id) WHERE customer_party_id IS NOT NULL;

CREATE TABLE work_participants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_id UUID NOT NULL REFERENCES work_records(id) ON DELETE CASCADE,
  party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  display_name TEXT NOT NULL,
  role TEXT,
  quantity NUMERIC(12,3),
  unit TEXT CHECK (unit IS NULL OR unit IN ('hours','days','jornales','units','fixed')),
  rate_eur NUMERIC(12,2),
  cost_eur NUMERIC(12,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX work_participants_work_idx ON work_participants(work_id);

CREATE TABLE work_resources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_id UUID NOT NULL REFERENCES work_records(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('machinery','material','service')),
  machinery_id UUID REFERENCES machinery(id) ON DELETE SET NULL,
  material_id UUID REFERENCES materials(id) ON DELETE SET NULL,
  supplier_party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  name TEXT NOT NULL,
  quantity NUMERIC(12,3),
  unit TEXT,
  unit_cost_eur NUMERIC(12,2),
  cost_eur NUMERIC(12,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX work_resources_work_idx ON work_resources(work_id);

COMMIT;
