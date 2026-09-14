BEGIN;

ALTER TABLE mill_reward_products
  ADD COLUMN required_level INTEGER NOT NULL DEFAULT 1
    REFERENCES mi_olivo_levels(level),
  ADD CONSTRAINT mill_reward_products_required_level_check
    CHECK (required_level BETWEEN 1 AND 10);

CREATE INDEX mill_reward_products_required_level_idx
  ON mill_reward_products(status, required_level, business_id);

CREATE OR REPLACE FUNCTION mi_olivo_lifetime_xp(p_user_id UUID)
RETURNS INTEGER AS $$
  SELECT COALESCE(SUM(
    CASE WHEN points > 0 AND event_type <> 'reward_refund' THEN points ELSE 0 END
  ), 0)::int
  FROM mi_olivo_ledger
  WHERE user_id = p_user_id;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION mi_olivo_current_level(p_user_id UUID)
RETURNS INTEGER AS $$
  SELECT COALESCE(MAX(level), 1)::int
  FROM mi_olivo_levels
  WHERE min_xp <= mi_olivo_lifetime_xp(p_user_id);
$$ LANGUAGE sql STABLE;

-- Defense in depth: the HTTP layer gives a friendly 409 before the redemption
-- transaction starts, while this trigger is the final authority. Because the
-- redemption row already exists inside the same transaction when olives are
-- spent, the trigger can resolve the product requirement and reject a bypass.
CREATE OR REPLACE FUNCTION guard_mi_olivo_reward_spend()
RETURNS trigger AS $$
DECLARE
  current_balance INTEGER;
  required_reward_level INTEGER;
  current_user_level INTEGER;
BEGIN
  IF NEW.event_type = 'reward_redemption' AND NEW.points < 0 THEN
    INSERT INTO mi_olivo_wallet_guards (user_id)
    VALUES (NEW.user_id)
    ON CONFLICT (user_id) DO NOTHING;

    PERFORM 1
    FROM mi_olivo_wallet_guards
    WHERE user_id = NEW.user_id
    FOR UPDATE;

    SELECT COALESCE(SUM(points), 0)::int
      INTO current_balance
    FROM mi_olivo_ledger
    WHERE user_id = NEW.user_id;

    IF current_balance + NEW.points < 0 THEN
      RAISE EXCEPTION 'insufficient_olives';
    END IF;

    IF NEW.source_type = 'mill_reward_redemption' AND NEW.source_id IS NOT NULL THEN
      SELECT p.required_level
        INTO required_reward_level
      FROM mill_reward_redemptions r
      JOIN mill_reward_products p ON p.id = r.product_id
      WHERE r.id::text = NEW.source_id
        AND r.user_id = NEW.user_id
      LIMIT 1;

      IF required_reward_level IS NOT NULL THEN
        current_user_level := mi_olivo_current_level(NEW.user_id);
        IF current_user_level < required_reward_level THEN
          RAISE EXCEPTION 'reward_level_locked';
        END IF;
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON COLUMN mill_reward_products.required_level IS
  'Minimum permanent Mi Olivo level required before a user may reserve this reward.';
COMMENT ON FUNCTION mi_olivo_lifetime_xp(UUID) IS
  'Permanent Mi Olivo XP: positive earned points excluding reward refunds.';
COMMENT ON FUNCTION mi_olivo_current_level(UUID) IS
  'Current permanent Mi Olivo level derived from lifetime XP.';

COMMIT;
