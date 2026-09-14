BEGIN;

CREATE OR REPLACE FUNCTION sync_route_track_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  target_route_id UUID;
  derived_status TEXT;
BEGIN
  target_route_id := COALESCE(NEW.route_id, OLD.route_id);

  SELECT CASE
    WHEN EXISTS (
      SELECT 1 FROM route_tracks
      WHERE route_id = target_route_id AND validation_status = 'validated'
    ) THEN 'validated'
    WHEN EXISTS (
      SELECT 1 FROM route_tracks
      WHERE route_id = target_route_id AND validation_status = 'uploaded'
    ) THEN 'uploaded'
    WHEN EXISTS (
      SELECT 1 FROM route_tracks
      WHERE route_id = target_route_id AND validation_status = 'rejected'
    ) THEN 'rejected'
    ELSE 'missing'
  END INTO derived_status;

  UPDATE routes
  SET track_status = derived_status,
      status = CASE
        WHEN status = 'published' AND derived_status <> 'validated' THEN 'review'
        ELSE status
      END,
      published_at = CASE
        WHEN status = 'published' AND derived_status <> 'validated' THEN NULL
        ELSE published_at
      END,
      updated_at = now()
  WHERE id = target_route_id;

  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS route_tracks_sync_route_status ON route_tracks;
CREATE TRIGGER route_tracks_sync_route_status
AFTER INSERT OR DELETE OR UPDATE OF validation_status ON route_tracks
FOR EACH ROW EXECUTE FUNCTION sync_route_track_status();

CREATE OR REPLACE FUNCTION enforce_route_publication_track()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.status = 'published' THEN
    IF NEW.track_status <> 'validated' OR NOT EXISTS (
      SELECT 1 FROM route_tracks
      WHERE route_id = NEW.id AND validation_status = 'validated'
    ) THEN
      RAISE EXCEPTION 'validated_track_required_for_publish'
        USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS routes_require_validated_track ON routes;
CREATE TRIGGER routes_require_validated_track
BEFORE INSERT OR UPDATE OF status, track_status ON routes
FOR EACH ROW EXECUTE FUNCTION enforce_route_publication_track();

COMMENT ON FUNCTION sync_route_track_status() IS
  'Derives routes.track_status from persisted track validation state and automatically unpublishes invalidated routes.';
COMMENT ON FUNCTION enforce_route_publication_track() IS
  'Prevents publication unless an actual validated route_tracks record exists.';

COMMIT;
