BEGIN;

CREATE TABLE professional_invoices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  customer_party_id UUID NOT NULL REFERENCES parties(id) ON DELETE RESTRICT,
  client_operation_id UUID NOT NULL,
  invoice_number TEXT,
  issued_on DATE,
  due_on DATE,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','issued','void')),
  subtotal_eur NUMERIC(12,2) NOT NULL CHECK (subtotal_eur >= 0),
  tax_eur NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (tax_eur >= 0),
  total_eur NUMERIC(12,2) NOT NULL CHECK (total_eur >= 0),
  notes TEXT,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id),
  CHECK (abs((subtotal_eur + tax_eur) - total_eur) < 0.01),
  CHECK (status <> 'issued' OR (invoice_number IS NOT NULL AND issued_on IS NOT NULL))
);

CREATE UNIQUE INDEX professional_invoices_number_uidx
  ON professional_invoices(workspace_id, invoice_number)
  WHERE invoice_number IS NOT NULL AND status <> 'void';

CREATE INDEX professional_invoices_customer_idx
  ON professional_invoices(workspace_id, customer_party_id, issued_on DESC NULLS LAST, created_at DESC);

CREATE TABLE professional_invoice_works (
  invoice_id UUID NOT NULL REFERENCES professional_invoices(id) ON DELETE CASCADE,
  work_id UUID NOT NULL REFERENCES work_records(id) ON DELETE RESTRICT,
  amount_eur NUMERIC(12,2) NOT NULL CHECK (amount_eur >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (invoice_id, work_id)
);

CREATE INDEX professional_invoice_works_work_idx ON professional_invoice_works(work_id);

COMMIT;