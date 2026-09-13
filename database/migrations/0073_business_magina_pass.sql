BEGIN;

CREATE TABLE magina_pass_programs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  name TEXT NOT NULL,
  description TEXT,
  program_type TEXT NOT NULL DEFAULT 'points'
    CHECK (program_type IN ('points','stamps','challenge')),
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','published','archived')),
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  default_checkin_points INTEGER NOT NULL DEFAULT 10 CHECK (default_checkin_points > 0),
  default_cooldown_hours INTEGER NOT NULL DEFAULT 20 CHECK (default_cooldown_hours BETWEEN 1 AND 720),
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE TABLE magina_pass_businesses (
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  checkin_points INTEGER CHECK (checkin_points IS NULL OR checkin_points > 0),
  cooldown_hours INTEGER CHECK (cooldown_hours IS NULL OR cooldown_hours BETWEEN 1 AND 720),
  featured_stop BOOLEAN NOT NULL DEFAULT false,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (program_id, business_id)
);

CREATE INDEX magina_pass_businesses_lookup_idx
  ON magina_pass_businesses(business_id, active, program_id);

CREATE TABLE magina_pass_wallets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  points_balance INTEGER NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
  total_points_earned INTEGER NOT NULL DEFAULT 0 CHECK (total_points_earned >= 0),
  total_checkins INTEGER NOT NULL DEFAULT 0 CHECK (total_checkins >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (program_id, user_id)
);

CREATE INDEX magina_pass_wallets_user_idx
  ON magina_pass_wallets(user_id, updated_at DESC);

CREATE TABLE magina_pass_qr_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  label TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX magina_pass_qr_business_idx
  ON magina_pass_qr_tokens(program_id, business_id, active);

CREATE TABLE magina_pass_checkins (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID NOT NULL REFERENCES magina_pass_wallets(id) ON DELETE CASCADE,
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  business_id UUID REFERENCES businesses(id) ON DELETE SET NULL,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_type TEXT NOT NULL DEFAULT 'business_qr'
    CHECK (source_type IN ('business_qr','route','event','admin')),
  source_key TEXT,
  qr_token_id UUID REFERENCES magina_pass_qr_tokens(id) ON DELETE SET NULL,
  points_awarded INTEGER NOT NULL CHECK (points_awarded > 0),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX magina_pass_checkins_user_time_idx
  ON magina_pass_checkins(user_id, program_id, occurred_at DESC);
CREATE INDEX magina_pass_checkins_business_time_idx
  ON magina_pass_checkins(program_id, business_id, occurred_at DESC)
  WHERE business_id IS NOT NULL;

CREATE TABLE magina_pass_rewards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  business_id UUID REFERENCES businesses(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  description TEXT,
  points_cost INTEGER NOT NULL CHECK (points_cost > 0),
  reward_type TEXT NOT NULL DEFAULT 'benefit'
    CHECK (reward_type IN ('benefit','discount','gift','experience','offer')),
  redemption_instructions TEXT,
  max_redemptions INTEGER CHECK (max_redemptions IS NULL OR max_redemptions > 0),
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','published','archived')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX magina_pass_rewards_public_idx
  ON magina_pass_rewards(program_id, status, points_cost, valid_until);

CREATE TABLE magina_pass_redemptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reward_id UUID NOT NULL REFERENCES magina_pass_rewards(id) ON DELETE RESTRICT,
  wallet_id UUID NOT NULL REFERENCES magina_pass_wallets(id) ON DELETE CASCADE,
  program_id UUID NOT NULL REFERENCES magina_pass_programs(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  business_id UUID REFERENCES businesses(id) ON DELETE SET NULL,
  points_spent INTEGER NOT NULL CHECK (points_spent > 0),
  status TEXT NOT NULL DEFAULT 'issued'
    CHECK (status IN ('issued','redeemed','cancelled','expired')),
  redemption_code_hash TEXT NOT NULL UNIQUE CHECK (redemption_code_hash ~ '^[0-9a-f]{64}$'),
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  redeemed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX magina_pass_redemptions_user_idx
  ON magina_pass_redemptions(user_id, program_id, issued_at DESC);

COMMENT ON TABLE magina_pass_qr_tokens IS 'Only SHA-256 hashes are stored. Raw QR validation tokens are returned once when generated.';
COMMENT ON TABLE magina_pass_checkins IS 'Territorial passport events. Future route/event integrations may use source_type without coupling this schema to those modules.';
COMMENT ON TABLE magina_pass_redemptions IS 'Reward redemptions deduct wallet points transactionally and use one-time redemption codes.';

COMMIT;