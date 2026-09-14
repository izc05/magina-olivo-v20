BEGIN;

ALTER TABLE mi_olivo_ledger
  DROP CONSTRAINT mi_olivo_ledger_reversal_check;

ALTER TABLE mi_olivo_ledger
  ADD CONSTRAINT mi_olivo_ledger_reversal_check CHECK (
    (points > 0 AND reversal_of IS NULL) OR
    (points < 0 AND reversal_of IS NOT NULL) OR
    (points < 0 AND reversal_of IS NULL AND event_type = 'reward_redemption')
  );

CREATE TABLE business_mill_profiles (
  business_id UUID PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
  mill_kind TEXT NOT NULL DEFAULT 'almazara' CHECK (mill_kind IN ('cooperativa','almazara','productor')),
  olive_varieties TEXT[] NOT NULL DEFAULT '{}',
  certifications JSONB NOT NULL DEFAULT '[]'::jsonb,
  services JSONB NOT NULL DEFAULT '[]'::jsonb,
  has_shop BOOLEAN NOT NULL DEFAULT false,
  accepts_visits BOOLEAN NOT NULL DEFAULT false,
  campaign_notes TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mill_reward_products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  slug TEXT NOT NULL CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  title TEXT NOT NULL,
  description TEXT,
  image_url TEXT,
  volume_ml INTEGER CHECK (volume_ml IS NULL OR volume_ml > 0),
  olive_cost INTEGER NOT NULL CHECK (olive_cost > 0),
  stock_total INTEGER NOT NULL DEFAULT 0 CHECK (stock_total >= 0),
  stock_reserved INTEGER NOT NULL DEFAULT 0 CHECK (stock_reserved >= 0),
  stock_redeemed INTEGER NOT NULL DEFAULT 0 CHECK (stock_redeemed >= 0),
  max_per_user INTEGER CHECK (max_per_user IS NULL OR max_per_user > 0),
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','paused','archived')),
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (business_id, slug),
  CHECK (stock_reserved + stock_redeemed <= stock_total),
  CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX mill_reward_products_public_idx
  ON mill_reward_products(status, business_id, olive_cost, updated_at DESC);

CREATE TABLE mill_reward_redemptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES mill_reward_products(id) ON DELETE RESTRICT,
  olives_spent INTEGER NOT NULL CHECK (olives_spent > 0),
  redemption_code UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  status TEXT NOT NULL DEFAULT 'reserved' CHECK (status IN ('reserved','redeemed','cancelled','expired')),
  expires_at TIMESTAMPTZ NOT NULL,
  redeemed_at TIMESTAMPTZ,
  redeemed_by UUID REFERENCES users(id) ON DELETE SET NULL,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status = 'redeemed' AND redeemed_at IS NOT NULL) OR status <> 'redeemed')
);

CREATE INDEX mill_reward_redemptions_user_idx
  ON mill_reward_redemptions(user_id, created_at DESC);
CREATE INDEX mill_reward_redemptions_business_idx
  ON mill_reward_redemptions(business_id, status, created_at DESC);
CREATE UNIQUE INDEX mill_reward_one_active_product_per_user_idx
  ON mill_reward_redemptions(user_id, product_id)
  WHERE status = 'reserved';

CREATE TABLE mill_reward_redemption_audit (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  redemption_id UUID NOT NULL REFERENCES mill_reward_redemptions(id) ON DELETE CASCADE,
  actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL CHECK (event_type IN ('created','redeemed','cancelled','expired')),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX mill_reward_redemption_audit_idx
  ON mill_reward_redemption_audit(redemption_id, created_at);

COMMENT ON TABLE business_mill_profiles IS 'Almazara/cooperative specific public profile layered on the shared business directory.';
COMMENT ON TABLE mill_reward_products IS 'Physical AOVE rewards exchangeable for Mi Olivo olives. Stock is controlled by the participating business.';
COMMENT ON TABLE mill_reward_redemptions IS 'Single-use redemption token represented as QR payload. Reserved tokens expire and can only be redeemed once.';
COMMENT ON CONSTRAINT mi_olivo_ledger_reversal_check ON mi_olivo_ledger IS 'Negative ledger entries remain reversals except for explicit reward_redemption spends.';

COMMIT;
