CREATE TABLE IF NOT EXISTS professional_document_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  entity_type text NOT NULL CHECK (entity_type IN ('professional_quote','professional_invoice')),
  entity_id uuid NOT NULL,
  document_id uuid REFERENCES documents(id) ON DELETE SET NULL,
  client_operation_id uuid NOT NULL,
  channel text NOT NULL CHECK (channel IN ('email','whatsapp','share','link','other')),
  recipient text,
  status text NOT NULL DEFAULT 'prepared' CHECK (status IN ('prepared','confirmed_sent','cancelled')),
  prepared_at timestamptz NOT NULL DEFAULT now(),
  confirmed_sent_at timestamptz,
  note text,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE INDEX IF NOT EXISTS professional_document_deliveries_entity_idx
  ON professional_document_deliveries (workspace_id, entity_type, entity_id, created_at DESC);

CREATE TABLE IF NOT EXISTS professional_quote_decisions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  quote_id uuid NOT NULL REFERENCES professional_quotes(id) ON DELETE CASCADE,
  delivery_id uuid REFERENCES professional_document_deliveries(id) ON DELETE SET NULL,
  decision text NOT NULL CHECK (decision IN ('accepted','rejected')),
  decided_at timestamptz NOT NULL DEFAULT now(),
  note text,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS professional_quote_decisions_quote_idx
  ON professional_quote_decisions (workspace_id, quote_id, decided_at DESC);

COMMENT ON TABLE professional_document_deliveries IS 'Audit trail of commercial document sharing. prepared does not imply successful receipt.';
COMMENT ON TABLE professional_quote_decisions IS 'Quote acceptance/rejection events optionally tied to the concrete delivery/version shown to the customer.';
