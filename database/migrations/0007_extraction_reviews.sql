BEGIN;

CREATE TABLE extraction_reviews (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  extraction_run_id UUID NOT NULL REFERENCES extraction_runs(id) ON DELETE CASCADE,
  confirmed_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
  corrections JSONB NOT NULL DEFAULT '{}'::jsonb,
  reviewed_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX extraction_reviews_run_idx
  ON extraction_reviews(extraction_run_id, created_at DESC);

COMMIT;
