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

CREATE OR REPLACE FUNCTION grant_business_owner_from_approved_claim()
RETURNS trigger AS $$
BEGIN
  IF NEW.status = 'approved' AND NEW.claimant_user_id IS NOT NULL
     AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
    INSERT INTO business_memberships (
      business_id, user_id, role, status, granted_by, granted_from_claim_id, updated_at
    ) VALUES (
      NEW.business_id, NEW.claimant_user_id, 'owner', 'active', NEW.reviewed_by, NEW.id, now()
    )
    ON CONFLICT (business_id, user_id) DO UPDATE SET
      role = 'owner',
      status = 'active',
      granted_by = EXCLUDED.granted_by,
      granted_from_claim_id = EXCLUDED.granted_from_claim_id,
      updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS business_claim_grant_owner_access ON business_claims;
CREATE TRIGGER business_claim_grant_owner_access
AFTER INSERT OR UPDATE OF status ON business_claims
FOR EACH ROW EXECUTE FUNCTION grant_business_owner_from_approved_claim();

COMMENT ON TABLE business_memberships IS 'Verified access to one business. Commercial plan, sponsorship and verification remain platform-admin concerns.';
COMMENT ON FUNCTION grant_business_owner_from_approved_claim() IS 'Approved authenticated ownership claims grant scoped owner access to that business only.';

COMMIT;