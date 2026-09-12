BEGIN;

CREATE TABLE crews (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  name TEXT NOT NULL,
  leader_party_id UUID REFERENCES parties(id) ON DELETE SET NULL,
  default_rate_eur NUMERIC(12,2),
  default_rate_unit TEXT CHECK (default_rate_unit IS NULL OR default_rate_unit IN ('hours','days','jornales','units','fixed')),
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);
CREATE INDEX crews_workspace_active_idx ON crews(workspace_id, active, name);

CREATE TABLE crew_members (
  crew_id UUID NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
  party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
  role TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (crew_id, party_id)
);
CREATE INDEX crew_members_party_idx ON crew_members(party_id);

ALTER TABLE work_participants
  ADD COLUMN crew_id UUID REFERENCES crews(id) ON DELETE SET NULL;
CREATE INDEX work_participants_crew_idx ON work_participants(crew_id) WHERE crew_id IS NOT NULL;

COMMIT;
