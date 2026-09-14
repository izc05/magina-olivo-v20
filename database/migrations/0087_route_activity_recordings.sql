BEGIN;

CREATE TABLE route_activity_recordings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  route_id UUID REFERENCES routes(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'recording'
    CHECK (status IN ('recording','paused','completed')),
  visibility TEXT NOT NULL DEFAULT 'private'
    CHECK (visibility IN ('private')),
  current_segment INTEGER NOT NULL DEFAULT 1 CHECK (current_segment >= 1),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  distance_m NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (distance_m >= 0),
  elevation_gain_m NUMERIC(12,2) CHECK (elevation_gain_m IS NULL OR elevation_gain_m >= 0),
  duration_seconds INTEGER NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
  points_count INTEGER NOT NULL DEFAULT 0 CHECK (points_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status = 'completed' AND completed_at IS NOT NULL) OR status <> 'completed')
);

CREATE UNIQUE INDEX route_activity_recordings_one_active_per_user_idx
  ON route_activity_recordings(user_id)
  WHERE status IN ('recording','paused');
CREATE INDEX route_activity_recordings_user_history_idx
  ON route_activity_recordings(user_id, started_at DESC);
CREATE INDEX route_activity_recordings_route_idx
  ON route_activity_recordings(route_id, started_at DESC)
  WHERE route_id IS NOT NULL;

CREATE TABLE route_activity_points (
  id BIGSERIAL PRIMARY KEY,
  recording_id UUID NOT NULL REFERENCES route_activity_recordings(id) ON DELETE CASCADE,
  segment INTEGER NOT NULL CHECK (segment >= 1),
  sequence BIGINT NOT NULL CHECK (sequence >= 1),
  recorded_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  location geometry(Point, 4326) NOT NULL,
  altitude_m NUMERIC(10,2),
  horizontal_accuracy_m NUMERIC(10,2) CHECK (horizontal_accuracy_m IS NULL OR horizontal_accuracy_m >= 0),
  vertical_accuracy_m NUMERIC(10,2) CHECK (vertical_accuracy_m IS NULL OR vertical_accuracy_m >= 0),
  UNIQUE(recording_id, sequence),
  CHECK (ST_SRID(location) = 4326)
);

CREATE INDEX route_activity_points_recording_sequence_idx
  ON route_activity_points(recording_id, segment, sequence);
CREATE INDEX route_activity_points_location_gist
  ON route_activity_points USING GIST(location);

COMMENT ON TABLE route_activity_recordings IS 'Explicit opt-in foreground GPS recordings. Private by default; never started by the server or in the background.';
COMMENT ON TABLE route_activity_points IS 'Exact GPS samples retained only after the user explicitly starts Grabar recorrido. Deleting a recording deletes all samples by cascade.';
COMMENT ON COLUMN route_activity_recordings.current_segment IS 'Incremented after each pause/resume so duration and distance never bridge a paused or suspended interval.';
COMMENT ON COLUMN route_activity_recordings.visibility IS 'V1 recordings are private only; public sharing is intentionally not enabled.';

COMMIT;
