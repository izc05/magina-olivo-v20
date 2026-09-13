BEGIN;

CREATE TABLE routes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  name TEXT NOT NULL,
  municipality_id UUID REFERENCES territory_municipalities(id) ON DELETE SET NULL,
  place_id UUID REFERENCES territory_places(id) ON DELETE SET NULL,
  route_type TEXT NOT NULL DEFAULT 'hiking'
    CHECK (route_type IN ('hiking','mtb','cycling','trail','family','mixed')),
  tags TEXT[] NOT NULL DEFAULT '{}',
  short_description TEXT,
  description TEXT,
  distance_m INTEGER CHECK (distance_m IS NULL OR distance_m >= 0),
  duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
  elevation_gain_m INTEGER CHECK (elevation_gain_m IS NULL OR elevation_gain_m >= 0),
  elevation_loss_m INTEGER CHECK (elevation_loss_m IS NULL OR elevation_loss_m >= 0),
  min_altitude_m INTEGER,
  max_altitude_m INTEGER,
  difficulty TEXT CHECK (difficulty IS NULL OR difficulty IN ('easy','moderate','hard','very_hard')),
  circular BOOLEAN NOT NULL DEFAULT false,
  family_friendly BOOLEAN NOT NULL DEFAULT false,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','review','published','archived')),
  validation_status TEXT NOT NULL DEFAULT 'unverified'
    CHECK (validation_status IN ('unverified','editorial','official')),
  track_status TEXT NOT NULL DEFAULT 'missing'
    CHECK (track_status IN ('missing','uploaded','validated','rejected')),
  access_notes TEXT,
  safety_notes TEXT,
  water_notes TEXT,
  shade_level TEXT CHECK (shade_level IS NULL OR shade_level IN ('none','low','medium','high')),
  mobile_coverage TEXT CHECK (mobile_coverage IS NULL OR mobile_coverage IN ('unknown','none','partial','good')),
  recommended_seasons TEXT[] NOT NULL DEFAULT '{}',
  restrictions TEXT,
  source_summary TEXT,
  last_verified_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  CHECK (max_altitude_m IS NULL OR min_altitude_m IS NULL OR max_altitude_m >= min_altitude_m),
  CHECK (status <> 'published' OR track_status = 'validated')
);

CREATE INDEX routes_public_idx
  ON routes(status, route_type, difficulty, name);
CREATE INDEX routes_municipality_idx
  ON routes(municipality_id, status, name);
CREATE INDEX routes_place_idx
  ON routes(place_id, status, name);

CREATE TABLE route_tracks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
  geometry geometry(Geometry, 4326) NOT NULL,
  geometry_type TEXT NOT NULL CHECK (geometry_type IN ('LineString','MultiLineString')),
  original_format TEXT CHECK (original_format IS NULL OR original_format IN ('gpx','geojson','kml','manual')),
  original_asset_url TEXT,
  checksum TEXT,
  bbox JSONB,
  distance_m INTEGER CHECK (distance_m IS NULL OR distance_m >= 0),
  source_name TEXT,
  source_url TEXT,
  validation_status TEXT NOT NULL DEFAULT 'uploaded'
    CHECK (validation_status IN ('uploaded','validated','rejected')),
  validation_notes TEXT,
  validated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  validated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (route_id, version),
  CHECK (GeometryType(geometry) IN ('LINESTRING','MULTILINESTRING')),
  CHECK (ST_SRID(geometry) = 4326)
);

CREATE INDEX route_tracks_route_idx
  ON route_tracks(route_id, version DESC);
CREATE INDEX route_tracks_geometry_gist
  ON route_tracks USING GIST(geometry);
CREATE UNIQUE INDEX route_tracks_one_validated_idx
  ON route_tracks(route_id)
  WHERE validation_status = 'validated';

CREATE TABLE route_elevation_samples (
  id BIGSERIAL PRIMARY KEY,
  route_track_id UUID NOT NULL REFERENCES route_tracks(id) ON DELETE CASCADE,
  sample_order INTEGER NOT NULL CHECK (sample_order >= 0),
  distance_m INTEGER NOT NULL CHECK (distance_m >= 0),
  elevation_m NUMERIC(8,2) NOT NULL,
  location geometry(Point, 4326),
  grade_percent NUMERIC(7,3),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (route_track_id, sample_order)
);

CREATE INDEX route_elevation_track_distance_idx
  ON route_elevation_samples(route_track_id, distance_m);
CREATE INDEX route_elevation_location_gist
  ON route_elevation_samples USING GIST(location);

CREATE TABLE route_points (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN (
    'start','finish','viewpoint','water','parking','recreation_area',
    'heritage','cave','bridge','rest','photo_spot','warning','other'
  )),
  location geometry(Point, 4326) NOT NULL,
  distance_m INTEGER CHECK (distance_m IS NULL OR distance_m >= 0),
  elevation_m NUMERIC(8,2),
  description TEXT,
  safety_note TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX route_points_route_idx
  ON route_points(route_id, active, sort_order, distance_m);
CREATE INDEX route_points_location_gist
  ON route_points USING GIST(location);

CREATE TABLE route_media (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  route_point_id UUID REFERENCES route_points(id) ON DELETE SET NULL,
  kind TEXT NOT NULL CHECK (kind IN (
    'photo','hero_image','real_video','drone_video','ai_image','ai_video',
    'map_animation','elevation_animation','thumbnail'
  )),
  origin TEXT NOT NULL CHECK (origin IN ('real','official','licensed','ai_generated')),
  url TEXT NOT NULL,
  poster_url TEXT,
  alt_text TEXT,
  caption TEXT,
  credit TEXT,
  source_url TEXT,
  license_notes TEXT,
  ai_generated BOOLEAN NOT NULL DEFAULT false,
  ai_provider TEXT,
  ai_model TEXT,
  ai_disclosure TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (NOT ai_generated OR origin = 'ai_generated'),
  CHECK (origin <> 'ai_generated' OR ai_generated)
);

CREATE INDEX route_media_route_idx
  ON route_media(route_id, active, sort_order, created_at);
CREATE INDEX route_media_point_idx
  ON route_media(route_point_id)
  WHERE route_point_id IS NOT NULL;

CREATE TABLE route_sources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  source_name TEXT NOT NULL,
  external_id TEXT,
  source_url TEXT NOT NULL,
  source_kind TEXT NOT NULL DEFAULT 'reference'
    CHECK (source_kind IN ('official','reference','track','media','editorial')),
  license_notes TEXT,
  retrieved_at TIMESTAMPTZ,
  raw_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX route_sources_route_idx
  ON route_sources(route_id, active, source_kind);
CREATE UNIQUE INDEX route_sources_external_unique_idx
  ON route_sources(source_name, external_id)
  WHERE external_id IS NOT NULL;

CREATE TABLE route_segments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT,
  start_distance_m INTEGER CHECK (start_distance_m IS NULL OR start_distance_m >= 0),
  end_distance_m INTEGER CHECK (end_distance_m IS NULL OR end_distance_m >= 0),
  duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
  difficulty TEXT CHECK (difficulty IS NULL OR difficulty IN ('easy','moderate','hard','very_hard')),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (end_distance_m IS NULL OR start_distance_m IS NULL OR end_distance_m >= start_distance_m)
);

CREATE INDEX route_segments_route_idx
  ON route_segments(route_id, sort_order);

COMMENT ON TABLE routes IS 'Premium territorial routes. Published navigable routes require a validated real track.';
COMMENT ON TABLE route_tracks IS 'Canonical PostGIS route geometry. Technical route data must never be invented by AI.';
COMMENT ON TABLE route_media IS 'Editorial media with explicit provenance; AI media cannot be presented as an exact record of current trail conditions.';

COMMIT;
