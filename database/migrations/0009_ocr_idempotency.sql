BEGIN;

ALTER TABLE ocr_runs
  ADD COLUMN workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
  ADD COLUMN client_operation_id UUID;

UPDATE ocr_runs AS o
SET workspace_id = d.workspace_id
FROM document_versions AS dv
JOIN documents AS d ON d.id = dv.document_id
WHERE dv.id = o.document_version_id;

UPDATE ocr_runs
SET client_operation_id = id
WHERE client_operation_id IS NULL;

ALTER TABLE ocr_runs
  ALTER COLUMN workspace_id SET NOT NULL,
  ALTER COLUMN client_operation_id SET NOT NULL;

CREATE UNIQUE INDEX ocr_runs_workspace_operation_uidx
  ON ocr_runs(workspace_id, client_operation_id);

COMMIT;
