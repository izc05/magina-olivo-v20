BEGIN;

CREATE TABLE radar_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source TEXT NOT NULL CHECK (source IN ('aemet_national_mosaic')),
  product TEXT NOT NULL CHECK (product IN ('reflectivity')),
  crs TEXT NOT NULL DEFAULT 'EPSG:4326' CHECK (crs = 'EPSG:4326'),
  observed_at TIMESTAMPTZ,
  fetched_at TIMESTAMPTZ NOT NULL,
  asset_format TEXT NOT NULL CHECK (asset_format IN ('gif','png','jpeg','geotiff','unknown')),
  analysis_ready BOOLEAN NOT NULL DEFAULT false,
  content_type TEXT NOT NULL,
  byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
  sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  source_url TEXT NOT NULL,
  storage_key TEXT,
  status TEXT NOT NULL DEFAULT 'fetched' CHECK (status IN ('fetched','stored','processed','failed')),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  error_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, product, sha256)
);

CREATE INDEX radar_snapshots_observed_idx ON radar_snapshots(observed_at DESC NULLS LAST);
CREATE INDEX radar_snapshots_fetched_idx ON radar_snapshots(fetched_at DESC);
CREATE INDEX radar_snapshots_analysis_ready_idx ON radar_snapshots(analysis_ready, status, fetched_at DESC);

COMMENT ON TABLE radar_snapshots IS 'Immutable-ish metadata for AEMET radar assets. Standard images remain visual-only; only validated georeferenced assets may become analysis_ready.';

COMMIT;
