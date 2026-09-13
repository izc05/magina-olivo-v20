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

COMMIT;
