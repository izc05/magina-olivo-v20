BEGIN;

CREATE TABLE route_adventures (
  route_id UUID PRIMARY KEY REFERENCES routes(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT false,
  title TEXT NOT NULL DEFAULT 'Modo Aventura',
  intro TEXT,
  completion_message TEXT,
  progression_mode TEXT NOT NULL DEFAULT 'free' CHECK (progression_mode IN ('free','linear')),
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (char_length(title) BETWEEN 1 AND 160),
  CHECK (intro IS NULL OR char_length(intro) <= 4000),
  CHECK (completion_message IS NULL OR char_length(completion_message) <= 4000)
);

CREATE TABLE route_adventure_checkpoints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  route_point_id UUID REFERENCES route_points(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  description TEXT,
  kind TEXT NOT NULL DEFAULT 'landmark'
    CHECK (kind IN ('landmark','trivia','observation','photo','collection','rest')),
  location geometry(Point, 4326) NOT NULL,
  distance_m NUMERIC(12,2) CHECK (distance_m IS NULL OR distance_m >= 0),
  unlock_radius_m INTEGER NOT NULL DEFAULT 60 CHECK (unlock_radius_m BETWEEN 10 AND 500),
  points INTEGER NOT NULL DEFAULT 100 CHECK (points BETWEEN 0 AND 10000),
  is_required BOOLEAN NOT NULL DEFAULT true,
  question TEXT,
  answer_options JSONB NOT NULL DEFAULT '[]'::jsonb,
  correct_answer_key TEXT,
  hint TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (char_length(title) BETWEEN 1 AND 180),
  CHECK (description IS NULL OR char_length(description) <= 4000),
  CHECK (question IS NULL OR char_length(question) <= 1000),
  CHECK (hint IS NULL OR char_length(hint) <= 1000),
  CHECK (jsonb_typeof(answer_options) = 'array'),
  CHECK ((question IS NULL AND correct_answer_key IS NULL) OR (question IS NOT NULL AND correct_answer_key IS NOT NULL)),
  CHECK (ST_SRID(location) = 4326)
);

CREATE INDEX route_adventure_checkpoints_route_idx
  ON route_adventure_checkpoints(route_id, active, sort_order, distance_m);
CREATE INDEX route_adventure_checkpoints_location_gist
  ON route_adventure_checkpoints USING GIST(location);
CREATE UNIQUE INDEX route_adventure_checkpoints_route_point_unique_idx
  ON route_adventure_checkpoints(route_id, route_point_id)
  WHERE route_point_id IS NOT NULL;

CREATE TABLE route_adventure_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','completed','abandoned')),
  score INTEGER NOT NULL DEFAULT 0 CHECK (score >= 0),
  last_distance_m NUMERIC(12,2) CHECK (last_distance_m IS NULL OR last_distance_m >= 0),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status = 'completed' AND completed_at IS NOT NULL) OR status <> 'completed')
);

CREATE UNIQUE INDEX route_adventure_runs_one_active_idx
  ON route_adventure_runs(route_id, user_id)
  WHERE status = 'active';
CREATE INDEX route_adventure_runs_user_idx
  ON route_adventure_runs(user_id, started_at DESC);
CREATE INDEX route_adventure_runs_route_idx
  ON route_adventure_runs(route_id, started_at DESC);

CREATE TABLE route_adventure_unlocks (
  run_id UUID NOT NULL REFERENCES route_adventure_runs(id) ON DELETE CASCADE,
  checkpoint_id UUID NOT NULL REFERENCES route_adventure_checkpoints(id) ON DELETE CASCADE,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  answer_key TEXT,
  is_correct BOOLEAN,
  awarded_points INTEGER NOT NULL DEFAULT 0 CHECK (awarded_points >= 0),
  distance_to_checkpoint_m NUMERIC(12,2) CHECK (distance_to_checkpoint_m IS NULL OR distance_to_checkpoint_m >= 0),
  PRIMARY KEY (run_id, checkpoint_id)
);

CREATE INDEX route_adventure_unlocks_checkpoint_idx
  ON route_adventure_unlocks(checkpoint_id, unlocked_at DESC);

CREATE FUNCTION enforce_route_adventure_publish_ready()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  publish_ready BOOLEAN;
BEGIN
  IF NEW.enabled THEN
    SELECT EXISTS (
      SELECT 1
      FROM routes r
      WHERE r.id = NEW.route_id
        AND r.status = 'published'
        AND r.track_status = 'validated'
        AND EXISTS (
          SELECT 1 FROM route_tracks rt
          WHERE rt.route_id = r.id AND rt.validation_status = 'validated'
        )
        AND EXISTS (
          SELECT 1 FROM route_adventure_checkpoints cp
          WHERE cp.route_id = r.id AND cp.active = true AND cp.is_required = true
        )
        AND NOT EXISTS (
          SELECT 1 FROM route_condition_reports rc
          WHERE rc.route_id = r.id
            AND rc.moderation_status = 'approved'
            AND rc.severity = 'critical'
            AND rc.condition_kind IN ('closed','blocked','fire_risk','flooded')
            AND (rc.expires_at IS NULL OR rc.expires_at > now())
        )
    ) INTO publish_ready;

    IF NOT publish_ready THEN
      RAISE EXCEPTION 'route_adventure_not_ready_for_publication'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER route_adventures_publish_ready_trg
  BEFORE INSERT OR UPDATE OF enabled, route_id ON route_adventures
  FOR EACH ROW EXECUTE FUNCTION enforce_route_adventure_publish_ready();

CREATE FUNCTION hold_route_adventure_for_critical_condition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.moderation_status = 'approved'
     AND NEW.severity = 'critical'
     AND NEW.condition_kind IN ('closed','blocked','fire_risk','flooded')
     AND (NEW.expires_at IS NULL OR NEW.expires_at > now()) THEN
    UPDATE route_adventures
    SET enabled = false, updated_at = now()
    WHERE route_id = NEW.route_id AND enabled = true;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER route_condition_reports_adventure_hold_trg
  AFTER INSERT OR UPDATE OF moderation_status, severity, condition_kind, expires_at
  ON route_condition_reports
  FOR EACH ROW EXECUTE FUNCTION hold_route_adventure_for_critical_condition();

COMMENT ON TABLE route_adventures IS 'Optional gamified layer for a validated published route. progression_mode free allows any checkpoint order; linear requires previous mandatory stages.';
COMMENT ON TABLE route_adventure_checkpoints IS 'Geolocated adventure checkpoints; answers and unlocks are validated by the API. Public payloads must never expose correct_answer_key.';
COMMENT ON INDEX route_adventure_checkpoints_route_point_unique_idx IS 'A real route POI can seed at most one adventure checkpoint per route, making bulk POI import idempotent.';
COMMENT ON FUNCTION enforce_route_adventure_publish_ready() IS 'Prevents enabling an adventure unless its route is published, has a real validated track, at least one active required checkpoint, and no approved active critical closure/block/fire/flood safety hold.';
COMMENT ON FUNCTION hold_route_adventure_for_critical_condition() IS 'Automatically disables an enabled adventure when a moderator approves an active critical closure, blockage, fire-risk or flood report. Reactivation is intentionally manual after review.';
COMMENT ON TABLE route_adventure_runs IS 'User game sessions kept separate from route_completions so game progress cannot be mistaken for verified physical completion.';
COMMENT ON TABLE route_adventure_unlocks IS 'Stores checkpoint result and proximity distance only; the user GPS coordinate used for validation is not retained.';

COMMIT;