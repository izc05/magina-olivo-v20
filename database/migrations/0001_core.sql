BEGIN;

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE workspaces (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('family','professional','organization')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fields (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  client_operation_id UUID NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  municipality TEXT,
  province TEXT,
  geometry geometry(MultiPolygon, 4326),
  calculated_area_ha NUMERIC(12,4),
  tree_count INTEGER CHECK (tree_count IS NULL OR tree_count > 0),
  crop TEXT NOT NULL DEFAULT 'olivar',
  variety TEXT,
  water_regime TEXT CHECK (water_regime IS NULL OR water_regime IN ('secano','regadio','mixto')),
  planting_year INTEGER,
  tenure_type TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','archived')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, client_operation_id)
);

CREATE INDEX fields_workspace_idx ON fields(workspace_id);
CREATE INDEX fields_geometry_gist ON fields USING GIST(geometry);

CREATE TABLE campaigns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  status TEXT NOT NULL CHECK (status IN ('planned','active','closed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, name)
);

CREATE INDEX campaigns_workspace_status_idx ON campaigns(workspace_id, status);

CREATE TABLE field_land_refs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  field_id UUID NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
  source TEXT NOT NULL CHECK (source IN ('catastro','sigpac','manual','other')),
  reference TEXT,
  geometry geometry(MultiPolygon, 4326),
  area_ha NUMERIC(12,4),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','linked','verified','rejected')),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  checked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX field_land_refs_field_idx ON field_land_refs(field_id);
CREATE INDEX field_land_refs_geometry_gist ON field_land_refs USING GIST(geometry);

COMMIT;
