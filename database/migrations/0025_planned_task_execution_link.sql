BEGIN;

ALTER TABLE scheduled_events
  ADD COLUMN IF NOT EXISTS task_kind TEXT,
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS created_by UUID,
  ADD COLUMN IF NOT EXISTS completed_domain_type TEXT,
  ADD COLUMN IF NOT EXISTS completed_domain_record_id UUID,
  ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

ALTER TABLE scheduled_events
  ADD CONSTRAINT scheduled_events_task_kind_check
  CHECK (task_kind IS NULL OR task_kind IN ('treatment','irrigation','fertilization','pruning','harvest','work','observation','other'));

CREATE INDEX IF NOT EXISTS scheduled_events_workspace_status_date_idx
  ON scheduled_events(workspace_id, status, scheduled_at);

CREATE INDEX IF NOT EXISTS scheduled_events_execution_idx
  ON scheduled_events(completed_domain_type, completed_domain_record_id)
  WHERE completed_domain_record_id IS NOT NULL;

COMMENT ON COLUMN scheduled_events.task_kind IS
  'User-facing planned activity kind. It drives agronomic context and registrar prefill without creating a completed domain record.';
COMMENT ON COLUMN scheduled_events.completed_domain_record_id IS
  'Set only after a real domain record exists. A planned task must not be considered executed merely because its scheduled time passed.';

COMMIT;
