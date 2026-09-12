BEGIN;

CREATE TABLE territory_municipalities (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ine_code TEXT NOT NULL UNIQUE CHECK (ine_code ~ '^[0-9]{5}$'),
  aemet_code TEXT UNIQUE CHECK (aemet_code IS NULL OR aemet_code ~ '^[0-9]{5}$'),
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  province_code TEXT NOT NULL DEFAULT '23',
  province_name TEXT NOT NULL DEFAULT 'Jaén',
  center geometry(Point, 4326),
  active BOOLEAN NOT NULL DEFAULT true,
  weather_enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE territory_places (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  municipality_id UUID NOT NULL REFERENCES territory_municipalities(id) ON DELETE RESTRICT,
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  kind TEXT NOT NULL DEFAULT 'municipal_seat' CHECK (kind IN ('municipal_seat','locality','hamlet','other')),
  center geometry(Point, 4326),
  is_default_for_municipality BOOLEAN NOT NULL DEFAULT false,
  public_enabled BOOLEAN NOT NULL DEFAULT true,
  hero_asset_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX territory_places_municipality_idx ON territory_places(municipality_id);
CREATE INDEX territory_municipalities_center_gist ON territory_municipalities USING GIST(center);
CREATE INDEX territory_places_center_gist ON territory_places USING GIST(center);

ALTER TABLE fields
  ADD COLUMN municipality_id UUID REFERENCES territory_municipalities(id) ON DELETE SET NULL,
  ADD COLUMN place_id UUID REFERENCES territory_places(id) ON DELETE SET NULL;

CREATE INDEX fields_municipality_id_idx ON fields(municipality_id);
CREATE INDEX fields_place_id_idx ON fields(place_id);

INSERT INTO territory_municipalities (ine_code, aemet_code, name, slug)
VALUES
  ('23001', '23001', 'Albanchez de Mágina', 'albanchez-de-magina'),
  ('23902', '23902', 'Bedmar y Garcíez', 'bedmar-y-garciez'),
  ('23044', '23044', 'Huelma', 'huelma'),
  ('23052', '23052', 'Jimena', 'jimena'),
  ('23053', '23053', 'Jódar', 'jodar')
ON CONFLICT (ine_code) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Albanchez de Mágina', 'albanchez-de-magina', 'municipal_seat', true
FROM territory_municipalities WHERE ine_code = '23001'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Bedmar', 'bedmar', 'municipal_seat', true
FROM territory_municipalities WHERE ine_code = '23902'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Garcíez', 'garciez', 'locality', false
FROM territory_municipalities WHERE ine_code = '23902'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Huelma', 'huelma', 'municipal_seat', true
FROM territory_municipalities WHERE ine_code = '23044'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Jimena', 'jimena', 'municipal_seat', true
FROM territory_municipalities WHERE ine_code = '23052'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality)
SELECT id, 'Jódar', 'jodar', 'municipal_seat', true
FROM territory_municipalities WHERE ine_code = '23053'
ON CONFLICT (slug) DO NOTHING;

UPDATE fields f
SET municipality_id = m.id
FROM territory_municipalities m
WHERE f.municipality_id IS NULL
  AND lower(trim(f.municipality)) = lower(m.name);

UPDATE fields f
SET place_id = p.id
FROM territory_places p
WHERE f.place_id IS NULL
  AND lower(trim(f.municipality)) = lower(p.name);

COMMENT ON TABLE territory_municipalities IS 'Official administrative municipality used for INE/AEMET and other external integrations.';
COMMENT ON TABLE territory_places IS 'User-facing town/locality. A visible place may share one official municipality with other localities, e.g. Bedmar and Garcíez.';

COMMIT;
