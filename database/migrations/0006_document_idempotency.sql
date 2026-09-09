BEGIN;

ALTER TABLE documents
  ADD COLUMN client_operation_id UUID;

UPDATE documents
SET client_operation_id = id
WHERE client_operation_id IS NULL;

ALTER TABLE documents
  ALTER COLUMN client_operation_id SET NOT NULL;

CREATE UNIQUE INDEX documents_workspace_client_operation_uidx
  ON documents(workspace_id, client_operation_id);

COMMIT;
