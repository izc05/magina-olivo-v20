BEGIN;

CREATE TABLE territory_municipality_official_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  municipality_id UUID NOT NULL REFERENCES territory_municipalities(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('town_hall','electronic_office','transparency','tourism','other_official')),
  label TEXT NOT NULL CHECK (length(trim(label)) > 0),
  url TEXT NOT NULL CHECK (url ~ '^https?://'),
  source_url TEXT CHECK (source_url IS NULL OR source_url ~ '^https?://'),
  verified_at TIMESTAMPTZ,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (municipality_id, kind, url)
);

CREATE INDEX territory_municipality_official_links_public_idx
  ON territory_municipality_official_links(municipality_id, active, verified_at, sort_order);

COMMENT ON TABLE territory_municipality_official_links IS 'Verified official municipal links. Public APIs expose only active rows with verified_at set.';
COMMENT ON COLUMN territory_municipality_official_links.source_url IS 'Source used to verify that the destination is an official public-service link.';

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Albanchez de Mágina', 'https://www.albanchezdemagina.es/', 'https://www.albanchezdemagina.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23001'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Albanchez de Mágina', 'https://albanchezdemagina.sedelectronica.es/info.0', 'https://albanchezdemagina.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23001'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Bedmar y Garcíez', 'https://www.bedmargarciez.es/', 'https://www.bedmargarciez.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23902'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Bedmar y Garcíez', 'https://bedmargarciez.sedelectronica.es/info.0', 'https://bedmargarciez.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23902'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Huelma', 'https://www.aytohuelma.es/', 'https://www.aytohuelma.es/', now(), 10
FROM territory_municipalities WHERE ine_code = '23044'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Huelma', 'https://aytohuelma.sedelectronica.es/info.0', 'https://aytohuelma.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23044'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Jimena', 'https://www.jimena.es/', 'https://www.jimena.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23052'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Jimena', 'https://jimena.sedelectronica.es/info.0', 'https://jimena.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23052'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Jódar', 'https://www.jodar.es/', 'https://www.jodar.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23053'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Jódar', 'https://jodar.sedelectronica.es/info.0', 'https://jodar.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23053'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

COMMIT;
