BEGIN;

CREATE TABLE route_reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  title TEXT CHECK (title IS NULL OR char_length(title) <= 180),
  body TEXT CHECK (body IS NULL OR char_length(body) <= 8000),
  visited_on DATE,
  completed BOOLEAN NOT NULL DEFAULT true,
  difficulty_vote TEXT CHECK (difficulty_vote IS NULL OR difficulty_vote IN ('easy','moderate','hard','very_hard')),
  moderation_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (moderation_status IN ('pending','approved','rejected','hidden')),
  moderation_note TEXT,
  moderated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  moderated_at TIMESTAMPTZ,
  helpful_count INTEGER NOT NULL DEFAULT 0 CHECK (helpful_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE (route_id, user_id)
);

CREATE INDEX route_reviews_public_idx
  ON route_reviews(route_id, moderation_status, published_at DESC, created_at DESC);
CREATE INDEX route_reviews_moderation_idx
  ON route_reviews(moderation_status, created_at DESC);

CREATE TABLE route_review_media (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  review_id UUID NOT NULL REFERENCES route_reviews(id) ON DELETE CASCADE,
  media_asset_id UUID NOT NULL REFERENCES platform_media_assets(id) ON DELETE RESTRICT,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL DEFAULT 'photo' CHECK (kind IN ('photo','video')),
  caption TEXT CHECK (caption IS NULL OR char_length(caption) <= 1500),
  captured_at TIMESTAMPTZ,
  location geometry(Point, 4326),
  moderation_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (moderation_status IN ('pending','approved','rejected','hidden')),
  moderation_note TEXT,
  moderated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  moderated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (location IS NULL OR ST_SRID(location) = 4326)
);

CREATE UNIQUE INDEX route_review_media_asset_unique_idx ON route_review_media(media_asset_id);
CREATE INDEX route_review_media_review_idx ON route_review_media(review_id, moderation_status, created_at);
CREATE INDEX route_review_media_location_gist ON route_review_media USING GIST(location) WHERE location IS NOT NULL;

CREATE TABLE route_condition_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  condition_kind TEXT NOT NULL CHECK (condition_kind IN (
    'clear','muddy','wet','snow','ice','blocked','damaged','closed','fire_risk','flooded','other'
  )),
  severity TEXT NOT NULL DEFAULT 'info' CHECK (severity IN ('info','caution','warning','critical')),
  note TEXT CHECK (note IS NULL OR char_length(note) <= 4000),
  observed_at TIMESTAMPTZ NOT NULL,
  location geometry(Point, 4326),
  expires_at TIMESTAMPTZ,
  moderation_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (moderation_status IN ('pending','approved','rejected','hidden')),
  moderation_note TEXT,
  moderated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  moderated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (expires_at IS NULL OR expires_at > observed_at),
  CHECK (location IS NULL OR ST_SRID(location) = 4326)
);

CREATE INDEX route_conditions_public_idx
  ON route_condition_reports(route_id, moderation_status, observed_at DESC);
CREATE INDEX route_conditions_moderation_idx
  ON route_condition_reports(moderation_status, created_at DESC);
CREATE INDEX route_conditions_location_gist
  ON route_condition_reports USING GIST(location) WHERE location IS NOT NULL;

CREATE TABLE route_community_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type TEXT NOT NULL CHECK (target_type IN ('review','review_media','condition_report')),
  target_id UUID NOT NULL,
  reason TEXT NOT NULL CHECK (reason IN ('spam','abuse','unsafe','privacy','copyright','false_information','other')),
  details TEXT CHECK (details IS NULL OR char_length(details) <= 3000),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewing','resolved','dismissed')),
  resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (reporter_user_id, target_type, target_id)
);

CREATE INDEX route_community_reports_queue_idx
  ON route_community_reports(status, created_at DESC);

CREATE TABLE route_favorites (
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (route_id, user_id)
);

CREATE INDEX route_favorites_user_idx ON route_favorites(user_id, created_at DESC);

CREATE TABLE route_completions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  completed_at TIMESTAMPTZ NOT NULL,
  source TEXT NOT NULL DEFAULT 'manual'
    CHECK (source IN ('manual','recorded_gpx','garmin','suunto','coros','apple_watch','other')),
  activity_external_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (route_id, user_id, completed_at)
);

CREATE INDEX route_completions_route_idx ON route_completions(route_id, completed_at DESC);
CREATE INDEX route_completions_user_idx ON route_completions(user_id, completed_at DESC);

CREATE TABLE route_sponsorships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID REFERENCES routes(id) ON DELETE CASCADE,
  sponsor_name TEXT NOT NULL,
  sponsor_logo_url TEXT,
  sponsor_url TEXT,
  headline TEXT,
  description TEXT,
  cta_label TEXT,
  cta_url TEXT,
  promo_code TEXT,
  placement TEXT NOT NULL DEFAULT 'route_sidebar'
    CHECK (placement IN ('route_hero','route_sidebar','after_map','nearby_services','route_download','collection')),
  billing_model TEXT NOT NULL DEFAULT 'flat'
    CHECK (billing_model IN ('flat','cpm','cpc','affiliate')),
  price_cents INTEGER CHECK (price_cents IS NULL OR price_cents >= 0),
  currency CHAR(3) NOT NULL DEFAULT 'EUR',
  priority INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','scheduled','active','paused','ended')),
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  disclosure TEXT NOT NULL DEFAULT 'Patrocinado',
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX route_sponsorships_active_idx
  ON route_sponsorships(route_id, placement, status, priority DESC, starts_at, ends_at);

CREATE TABLE route_sponsorship_events (
  id BIGSERIAL PRIMARY KEY,
  sponsorship_id UUID NOT NULL REFERENCES route_sponsorships(id) ON DELETE CASCADE,
  route_id UUID REFERENCES routes(id) ON DELETE SET NULL,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL CHECK (event_type IN (
    'impression','click','website','call','whatsapp','directions','booking','affiliate_conversion'
  )),
  session_key TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX route_sponsorship_events_analytics_idx
  ON route_sponsorship_events(sponsorship_id, event_type, occurred_at DESC);
CREATE INDEX route_sponsorship_events_route_idx
  ON route_sponsorship_events(route_id, occurred_at DESC);

COMMENT ON TABLE route_reviews IS 'One moderated route review per user; repeat visits and current conditions are represented separately.';
COMMENT ON TABLE route_condition_reports IS 'Community observations are not official route closures; UI must distinguish community reports from official restrictions.';
COMMENT ON TABLE route_sponsorships IS 'Commercial placements are explicit and must render their disclosure; payment never changes official safety/technical route data.';

COMMIT;