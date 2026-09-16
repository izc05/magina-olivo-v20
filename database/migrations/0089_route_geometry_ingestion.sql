BEGIN;

ALTER TABLE route_tracks
  DROP CONSTRAINT IF EXISTS route_tracks_original_format_check;

ALTER TABLE route_tracks
  ADD CONSTRAINT route_tracks_original_format_check
  CHECK (original_format IS NULL OR original_format IN (
    'gpx','geojson','kml','gml','kmz','manual'
  ));

ALTER TABLE route_tracks
  ADD COLUMN source_authority TEXT,
  ADD COLUMN source_crs TEXT CHECK (source_crs IS NULL OR source_crs IN ('EPSG:4326','EPSG:25830')),
  ADD COLUMN ingestion_method TEXT NOT NULL DEFAULT 'legacy'
    CHECK (ingestion_method IN ('legacy','official_fetch','manual_upload')),
  ADD COLUMN original_asset_key TEXT,
  ADD COLUMN original_size_bytes BIGINT CHECK (original_size_bytes IS NULL OR original_size_bytes >= 0),
  ADD COLUMN source_sha256 TEXT CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$'),
  ADD COLUMN source_retrieved_at TIMESTAMPTZ,
  ADD COLUMN normalized_at TIMESTAMPTZ,
  ADD COLUMN published_distance_m INTEGER CHECK (published_distance_m IS NULL OR published_distance_m > 0),
  ADD COLUMN distance_delta_percent NUMERIC(8,3)
    CHECK (distance_delta_percent IS NULL OR distance_delta_percent >= 0),
  ADD COLUMN ingestion_metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE UNIQUE INDEX route_tracks_route_source_sha256_unique_idx
  ON route_tracks(route_id, source_sha256)
  WHERE source_sha256 IS NOT NULL;

CREATE TABLE route_geometry_ingestion_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  route_track_id UUID REFERENCES route_tracks(id) ON DELETE SET NULL,
  actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL CHECK (event_type IN (
    'fetch_attempt','fetch_blocked','fetch_failed','asset_stored','normalized',
    'distance_mismatch','persisted','validated','rejected','manual_upload'
  )),
  source_authority TEXT,
  source_url TEXT,
  source_format TEXT CHECK (source_format IS NULL OR source_format IN ('gpx','geojson','kml','gml','kmz','manual')),
  source_crs TEXT CHECK (source_crs IS NULL OR source_crs IN ('EPSG:4326','EPSG:25830')),
  source_sha256 TEXT CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$'),
  http_status INTEGER CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
  attempt_count INTEGER CHECK (attempt_count IS NULL OR attempt_count > 0),
  message TEXT,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX route_geometry_ingestion_events_route_idx
  ON route_geometry_ingestion_events(route_id, created_at DESC);
CREATE INDEX route_geometry_ingestion_events_track_idx
  ON route_geometry_ingestion_events(route_track_id, created_at DESC)
  WHERE route_track_id IS NOT NULL;
CREATE INDEX route_geometry_ingestion_events_type_idx
  ON route_geometry_ingestion_events(event_type, created_at DESC);

COMMENT ON COLUMN route_tracks.source_sha256 IS
  'SHA-256 of the original official/manual source bytes before normalization; used for idempotency and provenance.';
COMMENT ON COLUMN route_tracks.original_asset_key IS
  'Private object-storage key for the immutable original source asset. Public clients consume normalized geometry instead.';
COMMENT ON COLUMN route_tracks.distance_delta_percent IS
  'Absolute percentage difference between normalized track distance and the authority-published distance, when available.';
COMMENT ON TABLE route_geometry_ingestion_events IS
  'Append-only operational audit for route geometry ingestion, including blocked official downloads and manual fallback uploads.';

COMMIT;
