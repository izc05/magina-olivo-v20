BEGIN;

CREATE TABLE harvest_settlements (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
  client_operation_id UUID NOT NULL,
  counterparty_name TEXT,
  settlement_number TEXT,
  settled_on DATE NOT NULL,
  basis TEXT NOT NULL DEFAULT 'olive_kg' CHECK (basis IN ('olive_kg','oil_kg','fixed')),
  unit_price_eur NUMERIC(12,6),
  gross_eur NUMERIC(12,2) NOT NULL CHECK (gross_eur >= 0),
  deductions_eur NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (deductions_eur >= 0),
  net_eur NUMERIC(12,2) NOT NULL CHECK (net_eur >= 0),
  notes TEXT,
  status TEXT NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed','superseded','voided')),
  supersedes_id UUID REFERENCES harvest_settlements(id) ON DELETE SET NULL,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id),
  CHECK (ABS((gross_eur - deductions_eur) - net_eur) <= 0.02)
);
CREATE INDEX harvest_settlements_workspace_campaign_idx ON harvest_settlements(workspace_id, campaign_id, settled_on DESC);

CREATE TABLE harvest_settlement_deliveries (
  settlement_id UUID NOT NULL REFERENCES harvest_settlements(id) ON DELETE CASCADE,
  delivery_id UUID NOT NULL REFERENCES harvest_deliveries(id) ON DELETE RESTRICT,
  allocated_net_eur NUMERIC(12,2) CHECK (allocated_net_eur IS NULL OR allocated_net_eur >= 0),
  PRIMARY KEY (settlement_id, delivery_id)
);
CREATE INDEX harvest_settlement_deliveries_delivery_idx ON harvest_settlement_deliveries(delivery_id);

CREATE TABLE harvest_collections (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  settlement_id UUID NOT NULL REFERENCES harvest_settlements(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  collected_on DATE NOT NULL,
  amount_eur NUMERIC(12,2) NOT NULL CHECK (amount_eur > 0),
  method TEXT CHECK (method IS NULL OR method IN ('cash','bank','card','bizum','other')),
  reference TEXT,
  notes TEXT,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);
CREATE INDEX harvest_collections_settlement_idx ON harvest_collections(settlement_id, collected_on DESC);

COMMIT;
