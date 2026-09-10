BEGIN;

CREATE TABLE customer_sites (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  customer_party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  municipality TEXT,
  address TEXT,
  external_reference TEXT,
  canonical_field_id UUID REFERENCES fields(id) ON DELETE SET NULL,
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE INDEX customer_sites_workspace_customer_idx
  ON customer_sites(workspace_id, customer_party_id, active, name);

ALTER TABLE work_records
  ALTER COLUMN field_id DROP NOT NULL,
  ADD COLUMN customer_site_id UUID REFERENCES customer_sites(id) ON DELETE SET NULL;

ALTER TABLE work_records
  DROP CONSTRAINT IF EXISTS work_records_check,
  ADD CONSTRAINT work_records_destination_check CHECK (
    (
      performed_for = 'self'
      AND field_id IS NOT NULL
      AND customer_site_id IS NULL
    )
    OR
    (
      performed_for = 'third-party'
      AND customer_party_id IS NOT NULL
      AND ((field_id IS NOT NULL)::int + (customer_site_id IS NOT NULL)::int) = 1
    )
  );

CREATE INDEX work_records_customer_site_idx
  ON work_records(customer_site_id)
  WHERE customer_site_id IS NOT NULL;

COMMIT;
