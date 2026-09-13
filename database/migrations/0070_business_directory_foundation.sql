BEGIN;

CREATE TABLE business_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id UUID REFERENCES business_categories(id) ON DELETE SET NULL,
  slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  name TEXT NOT NULL,
  description TEXT,
  icon_key TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX business_categories_parent_idx
  ON business_categories(parent_id, active, sort_order, name);

CREATE TABLE businesses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  name TEXT NOT NULL,
  legal_name TEXT,
  short_description TEXT,
  description TEXT,
  municipality_id UUID REFERENCES territory_municipalities(id) ON DELETE SET NULL,
  place_id UUID REFERENCES territory_places(id) ON DELETE SET NULL,
  address TEXT,
  location geometry(Point, 4326),
  phone TEXT,
  whatsapp TEXT,
  email TEXT,
  website TEXT,
  social_links JSONB NOT NULL DEFAULT '{}'::jsonb,
  opening_hours JSONB NOT NULL DEFAULT '{}'::jsonb,
  logo_url TEXT,
  cover_image_url TEXT,
  verification_status TEXT NOT NULL DEFAULT 'unverified'
    CHECK (verification_status IN ('unverified','pending','verified','rejected')),
  verified_at TIMESTAMPTZ,
  verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','published','archived')),
  commercial_plan TEXT NOT NULL DEFAULT 'free'
    CHECK (commercial_plan IN ('free','featured','premium','sponsor')),
  featured BOOLEAN NOT NULL DEFAULT false,
  sponsored BOOLEAN NOT NULL DEFAULT false,
  priority INTEGER NOT NULL DEFAULT 0 CHECK (priority >= 0),
  campaign_start TIMESTAMPTZ,
  campaign_end TIMESTAMPTZ,
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  CHECK (campaign_end IS NULL OR campaign_start IS NULL OR campaign_end >= campaign_start),
  CHECK (verified_at IS NULL OR verification_status = 'verified')
);

CREATE INDEX businesses_public_idx
  ON businesses(status, sponsored, featured, priority DESC, name);
CREATE INDEX businesses_municipality_idx
  ON businesses(municipality_id, status, name);
CREATE INDEX businesses_place_idx
  ON businesses(place_id, status, name);
CREATE INDEX businesses_location_gist
  ON businesses USING GIST(location);

CREATE TABLE business_category_links (
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  category_id UUID NOT NULL REFERENCES business_categories(id) ON DELETE CASCADE,
  is_primary BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (business_id, category_id)
);

CREATE INDEX business_category_links_category_idx
  ON business_category_links(category_id, business_id);

CREATE UNIQUE INDEX business_one_primary_category_idx
  ON business_category_links(business_id)
  WHERE is_primary;

CREATE TABLE business_media (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('logo','cover','photo','video','document')),
  url TEXT NOT NULL,
  thumbnail_url TEXT,
  alt_text TEXT,
  credit TEXT,
  origin TEXT NOT NULL DEFAULT 'owned'
    CHECK (origin IN ('owned','official','licensed','external_reference','ai_generated')),
  source_url TEXT,
  ai_generated BOOLEAN NOT NULL DEFAULT false,
  ai_disclosure TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (NOT ai_generated OR origin = 'ai_generated')
);

CREATE INDEX business_media_business_idx
  ON business_media(business_id, active, sort_order, created_at);

CREATE TABLE business_sources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
  source_name TEXT NOT NULL,
  external_id TEXT,
  source_url TEXT,
  license_notes TEXT,
  raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  fetched_at TIMESTAMPTZ,
  last_synced_at TIMESTAMPTZ,
  sync_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (sync_status IN ('pending','ok','stale','error','disabled')),
  sync_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX business_sources_external_unique_idx
  ON business_sources(source_name, external_id)
  WHERE external_id IS NOT NULL;
CREATE INDEX business_sources_business_idx
  ON business_sources(business_id, source_name, updated_at DESC);

CREATE TABLE business_claims (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  claimant_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  claimant_name TEXT NOT NULL,
  claimant_email TEXT NOT NULL,
  claimant_phone TEXT,
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','needs_info','approved','rejected','cancelled')),
  admin_notes TEXT,
  reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX business_claims_review_idx
  ON business_claims(status, created_at DESC);
CREATE INDEX business_claims_business_idx
  ON business_claims(business_id, created_at DESC);

INSERT INTO business_categories (slug, name, sort_order)
VALUES
  ('aove-productores', 'AOVE y productores', 10),
  ('cooperativas-almazaras', 'Cooperativas y almazaras', 20),
  ('agricultura-servicios', 'Agricultura y servicios agrícolas', 30),
  ('maquinaria-talleres', 'Maquinaria y talleres', 40),
  ('riego-viveros-suministros', 'Riego, viveros y suministros', 50),
  ('restauracion', 'Restauración', 60),
  ('alojamientos', 'Alojamientos', 70),
  ('turismo-activo', 'Turismo activo y guías', 80),
  ('comercio-local', 'Comercio local', 90),
  ('construccion-mantenimiento', 'Construcción y mantenimiento', 100),
  ('servicios-profesionales', 'Servicios profesionales', 110)
ON CONFLICT (slug) DO NOTHING;

COMMENT ON TABLE businesses IS 'Territorial business/service directory. Paid promotion must always be disclosed in the UI.';
COMMENT ON TABLE business_sources IS 'External provenance kept separate from editorial business data so synchronisation cannot silently overwrite reviewed fields.';
COMMENT ON TABLE business_claims IS 'Ownership/management claims awaiting administrative verification.';

COMMIT;
