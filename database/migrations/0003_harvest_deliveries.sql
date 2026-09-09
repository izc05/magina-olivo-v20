BEGIN;

CREATE TABLE harvest_deliveries (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  cooperative_or_mill TEXT,
  delivery_at TIMESTAMPTZ NOT NULL,
  ticket_number TEXT,
  total_kg NUMERIC(14,3) NOT NULL CHECK (total_kg > 0),
  source TEXT NOT NULL CHECK (source IN ('manual','ocr','import')),
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(workspace_id, client_operation_id)
);

CREATE TABLE harvest_delivery_fields (
  delivery_id UUID NOT NULL REFERENCES harvest_deliveries(id) ON DELETE CASCADE,
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  kg NUMERIC(14,3) NOT NULL CHECK (kg > 0),
  PRIMARY KEY(delivery_id, field_id)
);

CREATE TABLE delivery_results (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  delivery_id UUID NOT NULL REFERENCES harvest_deliveries(id) ON DELETE CASCADE,
  result_date DATE NOT NULL,
  yield_percent NUMERIC(6,3) NOT NULL CHECK (yield_percent >= 0 AND yield_percent <= 100),
  moisture_percent NUMERIC(6,3),
  acidity_percent NUMERIC(6,3),
  status TEXT NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed','superseded','voided')),
  supersedes_id UUID REFERENCES delivery_results(id) ON DELETE SET NULL,
  client_operation_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(workspace_id, client_operation_id)
);

CREATE UNIQUE INDEX delivery_results_one_confirmed_idx
  ON delivery_results(delivery_id)
  WHERE status = 'confirmed';

CREATE INDEX harvest_deliveries_workspace_date_idx ON harvest_deliveries(workspace_id, delivery_at DESC);
CREATE INDEX harvest_delivery_fields_field_idx ON harvest_delivery_fields(field_id, delivery_id);
CREATE INDEX delivery_results_delivery_idx ON delivery_results(delivery_id, created_at DESC);

COMMIT;
