BEGIN;

CREATE TABLE route_activity_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  route_id UUID REFERENCES routes(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused','completed')),
  distance_m NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (distance_m >= 0),
  active_seconds INTEGER NOT NULL DEFAULT 0 CHECK (active_seconds >= 0),
  active_started_at TIMESTAMPTZ,
  point_count INTEGER NOT NULL DEFAULT 0 CHECK (point_count >= 0),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status = 'active' AND active_started_at IS NOT NULL) OR status <> 'active'),
  CHECK ((status = 'completed' AND ended_at IS NOT NULL) OR status <> 'completed')
);

CREATE UNIQUE INDEX route_activity_sessions_one_open_user_idx
  ON route_activity_sessions(user_id)
  WHERE status IN ('active','paused');
CREATE INDEX route_activity_sessions_user_history_idx
  ON route_activity_sessions(user_id, started_at DESC);
CREATE INDEX route_activity_sessions_route_idx
  ON route_activity_sessions(route_id, started_at DESC)
  WHERE route_id IS NOT NULL;

CREATE TABLE route_activity_points (
  id BIGSERIAL PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES route_activity_sessions(id) ON DELETE CASCADE,
  recorded_at TIMESTAMPTZ NOT NULL,
  location geometry(Point, 4326) NOT NULL,
  accuracy_m NUMERIC(8,2) NOT NULL CHECK (accuracy_m >= 0 AND accuracy_m <= 200),
  altitude_m NUMERIC(10,2),
  segment_distance_m NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (segment_distance_m >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ST_SRID(location) = 4326)
);

CREATE INDEX route_activity_points_session_time_idx
  ON route_activity_points(session_id, recorded_at, id);
CREATE INDEX route_activity_points_location_gist
  ON route_activity_points USING GIST(location);

COMMENT ON TABLE route_activity_sessions IS 'Explicit opt-in foreground activity recordings. They are private to the authenticated owner and separate from gamified route completion metrics.';
COMMENT ON TABLE route_activity_points IS 'Exact GPS samples stored only after the user explicitly starts recording. Public APIs must never expose these points.';
COMMENT ON INDEX route_activity_sessions_one_open_user_idx IS 'A user can have at most one active or paused recording, preventing accidental parallel GPS sessions.';

COMMIT;
