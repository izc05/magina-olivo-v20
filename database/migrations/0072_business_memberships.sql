BEGIN;

CREATE TABLE business_memberships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL DEFAULT 'manager'
    CHECK (role IN ('owner','manager','editor','analyst')),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','revoked')),
  granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
  granted_from_claim_id UUID REFERENCES business_claims(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (business_id, user_id)
);

CREATE INDEX business_memberships_user_idx
  ON business_memberships(user_id, status, business_id);
CREATE INDEX business_memberships_business_idx
  ON business_memberships(business_id, status, role);

COMMENT ON TABLE business_memberships IS 'Verified access to one business. Commercial plan, sponsorship and verification remain platform-admin concerns.';

COMMIT;