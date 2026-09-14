BEGIN;

CREATE TABLE business_experiences (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  slug TEXT NOT NULL CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  title TEXT NOT NULL,
  summary TEXT,
  description TEXT,
  experience_type TEXT NOT NULL DEFAULT 'other'
    CHECK (experience_type IN (
      'aove_tasting','mill_visit','guided_tour','workshop','gastronomy',
      'nature','culture','family','wellness','other'
    )),
  duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes > 0),
  min_party_size INTEGER NOT NULL DEFAULT 1 CHECK (min_party_size > 0),
  max_party_size INTEGER CHECK (max_party_size IS NULL OR max_party_size >= min_party_size),
  price_cents INTEGER CHECK (price_cents IS NULL OR price_cents >= 0),
  currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
  booking_mode TEXT NOT NULL DEFAULT 'request'
    CHECK (booking_mode IN ('request','external','contact')),
  booking_url TEXT,
  meeting_point_text TEXT,
  location geometry(Point, 4326),
  languages JSONB NOT NULL DEFAULT '[]'::jsonb,
  includes JSONB NOT NULL DEFAULT '[]'::jsonb,
  excludes JSONB NOT NULL DEFAULT '[]'::jsonb,
  cancellation_policy TEXT,
  cover_image_url TEXT,
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','archived')),
  sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE (business_id, slug),
  CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
  CHECK (booking_mode <> 'external' OR booking_url IS NOT NULL)
);

CREATE INDEX business_experiences_public_idx
  ON business_experiences(status, experience_type, valid_until, sort_order, updated_at DESC);
CREATE INDEX business_experiences_business_idx
  ON business_experiences(business_id, status, updated_at DESC);
CREATE INDEX business_experiences_location_gist
  ON business_experiences USING GIST(location);

CREATE TABLE business_experience_slots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  experience_id UUID NOT NULL REFERENCES business_experiences(id) ON DELETE CASCADE,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ,
  capacity INTEGER NOT NULL CHECK (capacity > 0),
  confirmed_count INTEGER NOT NULL DEFAULT 0 CHECK (confirmed_count >= 0 AND confirmed_count <= capacity),
  price_override_cents INTEGER CHECK (price_override_cents IS NULL OR price_override_cents >= 0),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','full','cancelled','hidden')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX business_experience_slots_public_idx
  ON business_experience_slots(experience_id, status, starts_at);
CREATE INDEX business_experience_slots_start_idx
  ON business_experience_slots(starts_at, status);

CREATE TABLE business_experience_bookings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  experience_id UUID NOT NULL REFERENCES business_experiences(id) ON DELETE CASCADE,
  slot_id UUID REFERENCES business_experience_slots(id) ON DELETE SET NULL,
  business_lead_id UUID REFERENCES business_leads(id) ON DELETE SET NULL,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  contact_name TEXT NOT NULL,
  contact_email TEXT,
  contact_phone TEXT,
  party_size INTEGER NOT NULL DEFAULT 1 CHECK (party_size > 0),
  requested_for TIMESTAMPTZ,
  message TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','confirmed','declined','cancelled','completed','no_show')),
  total_cents INTEGER CHECK (total_cents IS NULL OR total_cents >= 0),
  currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
  source_context TEXT,
  source_key TEXT,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (contact_email IS NOT NULL OR contact_phone IS NOT NULL)
);

CREATE INDEX business_experience_bookings_experience_idx
  ON business_experience_bookings(experience_id, status, created_at DESC);
CREATE INDEX business_experience_bookings_slot_idx
  ON business_experience_bookings(slot_id, status, created_at DESC)
  WHERE slot_id IS NOT NULL;
CREATE INDEX business_experience_bookings_user_idx
  ON business_experience_bookings(user_id, created_at DESC)
  WHERE user_id IS NOT NULL;

ALTER TABLE business_leads
  ADD COLUMN experience_id UUID REFERENCES business_experiences(id) ON DELETE SET NULL;
CREATE INDEX business_leads_experience_idx
  ON business_leads(experience_id, created_at DESC)
  WHERE experience_id IS NOT NULL;

ALTER TABLE business_events
  ADD COLUMN experience_id UUID REFERENCES business_experiences(id) ON DELETE SET NULL;
CREATE INDEX business_events_experience_time_idx
  ON business_events(experience_id, occurred_at DESC)
  WHERE experience_id IS NOT NULL;

ALTER TABLE business_events DROP CONSTRAINT IF EXISTS business_events_event_type_check;
ALTER TABLE business_events ADD CONSTRAINT business_events_event_type_check CHECK (event_type IN (
  'directory_impression','profile_view','phone_click','whatsapp_click','email_click',
  'website_click','directions_click','offer_view','offer_redeem','lead_submit',
  'experience_view','experience_booking_submit'
));

COMMENT ON TABLE business_experiences IS 'Bookable/requestable territorial experiences offered by businesses. Payment settlement is intentionally out of scope.';
COMMENT ON TABLE business_experience_slots IS 'Capacity-controlled scheduled occurrences for business experiences.';
COMMENT ON TABLE business_experience_bookings IS 'Booking requests and confirmed attendance linked to the business lead funnel.';

COMMIT;
