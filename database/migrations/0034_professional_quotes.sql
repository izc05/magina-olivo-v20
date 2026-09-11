BEGIN;

CREATE TABLE professional_quotes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  customer_party_id UUID NOT NULL REFERENCES parties(id) ON DELETE RESTRICT,
  customer_site_id UUID REFERENCES customer_sites(id) ON DELETE SET NULL,
  client_operation_id UUID NOT NULL,
  quote_number TEXT,
  title TEXT NOT NULL,
  issued_on DATE,
  valid_until DATE,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','sent','accepted','rejected','expired','converted')),
  subtotal_eur NUMERIC(12,2) NOT NULL CHECK (subtotal_eur >= 0),
  tax_eur NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (tax_eur >= 0),
  total_eur NUMERIC(12,2) NOT NULL CHECK (total_eur >= 0),
  notes TEXT,
  accepted_at TIMESTAMPTZ,
  rejected_at TIMESTAMPTZ,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id),
  CHECK (abs((subtotal_eur + tax_eur) - total_eur) < 0.01),
  CHECK (status <> 'accepted' OR accepted_at IS NOT NULL),
  CHECK (status <> 'rejected' OR rejected_at IS NOT NULL)
);

CREATE UNIQUE INDEX professional_quotes_number_uidx
  ON professional_quotes(workspace_id, quote_number)
  WHERE quote_number IS NOT NULL;

CREATE INDEX professional_quotes_customer_idx
  ON professional_quotes(workspace_id, customer_party_id, created_at DESC);

CREATE TABLE professional_quote_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quote_id UUID NOT NULL REFERENCES professional_quotes(id) ON DELETE CASCADE,
  description TEXT NOT NULL,
  quantity NUMERIC(12,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit TEXT,
  unit_price_eur NUMERIC(12,2) NOT NULL CHECK (unit_price_eur >= 0),
  line_total_eur NUMERIC(12,2) NOT NULL CHECK (line_total_eur >= 0),
  sort_order INTEGER NOT NULL DEFAULT 0,
  CHECK (abs((quantity * unit_price_eur) - line_total_eur) < 0.02)
);

ALTER TABLE work_records
  ADD COLUMN professional_quote_id UUID REFERENCES professional_quotes(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX work_records_professional_quote_uidx
  ON work_records(professional_quote_id)
  WHERE professional_quote_id IS NOT NULL;

COMMIT;