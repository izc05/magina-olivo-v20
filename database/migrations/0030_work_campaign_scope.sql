BEGIN;

CREATE OR REPLACE FUNCTION enforce_work_campaign_workspace_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.campaign_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM campaigns c
    WHERE c.id = NEW.campaign_id
      AND c.workspace_id = NEW.workspace_id
  ) THEN
    RAISE EXCEPTION 'work campaign must belong to the same workspace'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS work_records_campaign_workspace_scope ON work_records;
CREATE TRIGGER work_records_campaign_workspace_scope
BEFORE INSERT OR UPDATE OF campaign_id, workspace_id ON work_records
FOR EACH ROW
EXECUTE FUNCTION enforce_work_campaign_workspace_scope();

COMMIT;
