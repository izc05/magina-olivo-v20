BEGIN;

CREATE TABLE work_collections (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  work_id UUID NOT NULL REFERENCES work_records(id) ON DELETE CASCADE,
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

CREATE INDEX work_collections_work_date_idx
  ON work_collections(work_id, collected_on DESC, created_at DESC);

INSERT INTO work_collections (
  id, workspace_id, work_id, client_operation_id, collected_on,
  amount_eur, method, reference, notes, created_by
)
SELECT
  gen_random_uuid(), wr.workspace_id, wr.id, gen_random_uuid(), wr.occurred_on,
  wr.collected_eur, NULL, wr.invoice_reference,
  'Migrado desde el acumulado histórico de cobro', wr.created_by
FROM work_records wr
WHERE wr.performed_for = 'third-party'
  AND COALESCE(wr.collected_eur, 0) > 0
  AND NOT EXISTS (SELECT 1 FROM work_collections wc WHERE wc.work_id = wr.id);

COMMIT;
