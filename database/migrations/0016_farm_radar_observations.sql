BEGIN;

ALTER TABLE fields
  ADD CONSTRAINT fields_id_workspace_unique UNIQUE (id, workspace_id);

CREATE TABLE farm_radar_observations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  field_id UUID NOT NULL,
  radar_snapshot_id UUID NOT NULL REFERENCES radar_snapshots(id) ON DELETE CASCADE,
  analysis_version TEXT NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  coverage_status TEXT NOT NULL CHECK (coverage_status IN ('covered','partial','outside','unavailable')),
  precipitation_detected BOOLEAN,
  nearest_echo_distance_km NUMERIC(8,3) CHECK (nearest_echo_distance_km IS NULL OR nearest_echo_distance_km >= 0),
  direction_degrees NUMERIC(6,2) CHECK (direction_degrees IS NULL OR (direction_degrees >= 0 AND direction_degrees < 360)),
  direction_label TEXT CHECK (direction_label IS NULL OR direction_label IN ('N','NE','E','SE','S','SW','W','NW','OVER_FIELD')),
  reflectivity_dbz_min NUMERIC(7,2),
  reflectivity_dbz_max NUMERIC(7,2),
  representative_dbz NUMERIC(7,2),
  analysis_radius_km NUMERIC(7,3) NOT NULL CHECK (analysis_radius_km > 0 AND analysis_radius_km <= 100),
  quality_flags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT farm_radar_observations_field_workspace_fk
    FOREIGN KEY (field_id, workspace_id)
    REFERENCES fields(id, workspace_id)
    ON DELETE CASCADE,
  CONSTRAINT farm_radar_observations_echo_consistency
    CHECK (
      precipitation_detected IS DISTINCT FROM false
      OR (
        nearest_echo_distance_km IS NULL
        AND direction_degrees IS NULL
        AND direction_label IS NULL
        AND reflectivity_dbz_min IS NULL
        AND reflectivity_dbz_max IS NULL
        AND representative_dbz IS NULL
      )
    ),
  UNIQUE (field_id, radar_snapshot_id, analysis_version)
);

CREATE INDEX farm_radar_observations_workspace_observed_idx
  ON farm_radar_observations(workspace_id, observed_at DESC);
CREATE INDEX farm_radar_observations_field_observed_idx
  ON farm_radar_observations(field_id, observed_at DESC);
CREATE INDEX farm_radar_observations_snapshot_idx
  ON farm_radar_observations(radar_snapshot_id);

COMMENT ON TABLE farm_radar_observations IS
  'Private rebuildable projection linking a validated radar snapshot to a field. It stores observed reflectivity context, never forecast/nowcast claims.';
COMMENT ON COLUMN farm_radar_observations.metadata_json IS
  'Technical analysis provenance only. Do not copy private field geometry into this projection.';

COMMIT;
