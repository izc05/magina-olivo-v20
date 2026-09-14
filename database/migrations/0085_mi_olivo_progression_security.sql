BEGIN;

-- Serialize all spend operations for one user so two simultaneous reward
-- reservations cannot spend the same olive balance on different products.
CREATE TABLE mi_olivo_wallet_guards (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Progression is based on lifetime XP, never on the spendable balance.
-- Spending olives therefore cannot make an olive tree lose a level.
CREATE TABLE mi_olivo_levels (
  level INTEGER PRIMARY KEY CHECK (level BETWEEN 1 AND 20),
  slug TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  min_xp INTEGER NOT NULL UNIQUE CHECK (min_xp >= 0),
  tree_stage INTEGER NOT NULL CHECK (tree_stage BETWEEN 1 AND 10),
  badge_title TEXT NOT NULL,
  description TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO mi_olivo_levels (level, slug, name, min_xp, tree_stage, badge_title, description) VALUES
  (1,  'brote',               'Brote',               0,    1,  'Primer brote',        'El comienzo de tu historia en Mágina.'),
  (2,  'rama-nueva',          'Rama nueva',          100,  2,  'Rama nueva',          'Tu olivo empieza a ganar forma y constancia.'),
  (3,  'olivo-joven',         'Olivo joven',         250,  3,  'Olivo joven',         'Primeras raíces firmes y una copa en crecimiento.'),
  (4,  'olivo-arraigado',     'Olivo arraigado',     450,  4,  'Raíces de Mágina',    'Tu progreso ya forma parte de tu rutina.'),
  (5,  'olivo-en-flor',       'Olivo en flor',       700,  5,  'Floración',           'El árbol entra en una nueva etapa visual.'),
  (6,  'olivo-de-cosecha',    'Olivo de cosecha',    1000, 6,  'Primera cosecha',     'La copa madura y aparecen más frutos.'),
  (7,  'olivo-maduro',        'Olivo maduro',        1400, 7,  'Olivo maduro',        'Un árbol consolidado por actividad útil y real.'),
  (8,  'olivo-centenario',    'Olivo centenario',    1900, 8,  'Centenario',          'Un símbolo de continuidad, territorio y memoria.'),
  (9,  'guardian-del-olivar', 'Guardián del Olivar', 2500, 9,  'Guardián del Olivar', 'Has construido una trayectoria excepcional.'),
  (10, 'leyenda-de-magina',   'Leyenda de Mágina',   3200, 10, 'Leyenda de Mágina',   'La máxima etapa de esta temporada de Mi Olivo.');

CREATE TABLE mi_olivo_achievement_definitions (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT NOT NULL DEFAULT 'olive',
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO mi_olivo_achievement_definitions (id, title, description, icon, sort_order) VALUES
  ('first-roots',       'Primeras raíces',     'Registra tu primera actividad real.', 'roots', 10),
  ('field-constancy',   'Constancia',          'Organiza diez actividades reales.', 'calendar', 20),
  ('first-harvest',     'Primera cosecha',     'Registra tu primera entrega de cosecha.', 'harvest', 30),
  ('magina-explorer',   'Raíces en Mágina',    'Descubre tu primer pueblo de Sierra Mágina.', 'map', 40),
  ('olive-500-xp',      'Copa creciente',      'Alcanza 500 XP históricos.', 'tree', 50),
  ('olive-1000-xp',     'Olivo de cosecha',    'Alcanza 1.000 XP históricos.', 'tree', 60),
  ('first-reservation', 'Primer premio',       'Reserva tu primera recompensa de una almazara.', 'gift', 70),
  ('first-redemption',  'Del olivo a la mesa', 'Recoge tu primera recompensa con QR.', 'qr', 80);

CREATE TABLE mi_olivo_user_achievements (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  achievement_id TEXT NOT NULL REFERENCES mi_olivo_achievement_definitions(id) ON DELETE CASCADE,
  source_id TEXT,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, achievement_id)
);

CREATE INDEX mi_olivo_user_achievements_date_idx
  ON mi_olivo_user_achievements(user_id, unlocked_at DESC);

-- New QR reservations may use a high-entropy opaque token. Only its digest is
-- persisted so the raw token never needs to be stored in the database.
ALTER TABLE mill_reward_redemptions
  ADD COLUMN qr_token_hash TEXT,
  ADD COLUMN product_title_snapshot TEXT,
  ADD COLUMN business_name_snapshot TEXT;

CREATE UNIQUE INDEX mill_reward_redemptions_qr_token_hash_uq
  ON mill_reward_redemptions(qr_token_hash)
  WHERE qr_token_hash IS NOT NULL;

CREATE TABLE mill_reward_stock_audit (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id UUID NOT NULL REFERENCES mill_reward_products(id) ON DELETE CASCADE,
  redemption_id UUID REFERENCES mill_reward_redemptions(id) ON DELETE SET NULL,
  actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL CHECK (event_type IN ('reserved','released','redeemed','manual_adjustment')),
  delta_reserved INTEGER NOT NULL DEFAULT 0,
  delta_redeemed INTEGER NOT NULL DEFAULT 0,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX mill_reward_stock_audit_product_date_idx
  ON mill_reward_stock_audit(product_id, created_at DESC);
CREATE INDEX mill_reward_stock_audit_redemption_idx
  ON mill_reward_stock_audit(redemption_id, created_at DESC)
  WHERE redemption_id IS NOT NULL;

-- Defense in depth for olive spending. The API performs a friendly balance
-- check, but this trigger is authoritative and serializes concurrent spends
-- even when they target different reward products.
CREATE OR REPLACE FUNCTION guard_mi_olivo_reward_spend()
RETURNS trigger AS $$
DECLARE
  current_balance INTEGER;
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
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mi_olivo_reward_spend_guard_trigger
BEFORE INSERT ON mi_olivo_ledger
FOR EACH ROW EXECUTE FUNCTION guard_mi_olivo_reward_spend();

-- Batch expiry is callable from API/worker/admin surfaces. It locks rows with
-- SKIP LOCKED so multiple workers can safely run it. The refund trigger from
-- 0084 returns the olives; this function releases physical stock atomically.
CREATE OR REPLACE FUNCTION expire_stale_mill_reward_reservations(p_limit INTEGER DEFAULT 500)
RETURNS INTEGER AS $$
DECLARE
  item RECORD;
  expired_count INTEGER := 0;
  safe_limit INTEGER := GREATEST(1, LEAST(COALESCE(p_limit, 500), 5000));
BEGIN
  FOR item IN
    SELECT r.id, r.product_id
    FROM mill_reward_redemptions r
    WHERE r.status = 'reserved' AND r.expires_at < now()
    ORDER BY r.expires_at ASC
    LIMIT safe_limit
    FOR UPDATE OF r SKIP LOCKED
  LOOP
    UPDATE mill_reward_redemptions
      SET status = 'expired', updated_at = now()
      WHERE id = item.id AND status = 'reserved';

    IF FOUND THEN
      UPDATE mill_reward_products
        SET stock_reserved = GREATEST(0, stock_reserved - 1), updated_at = now()
        WHERE id = item.product_id;

      INSERT INTO mill_reward_redemption_audit (redemption_id, actor_user_id, event_type, metadata)
        VALUES (item.id, NULL, 'expired', '{"source":"expiry_sweep"}'::jsonb);

      INSERT INTO mill_reward_stock_audit (
        product_id, redemption_id, actor_user_id, event_type, delta_reserved, metadata
      ) VALUES (
        item.product_id, item.id, NULL, 'released', -1, '{"reason":"expired"}'::jsonb
      );

      expired_count := expired_count + 1;
    END IF;
  END LOOP;

  RETURN expired_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE mi_olivo_levels IS 'Mi Olivo thresholds. Level depends on lifetime positive XP, never spendable balance.';
COMMENT ON TABLE mi_olivo_wallet_guards IS 'Per-user transaction mutex for all olive-spending operations.';
COMMENT ON COLUMN mill_reward_redemptions.qr_token_hash IS 'Optional SHA-256 hex hash of the opaque QR bearer token; raw token is never stored.';
COMMENT ON FUNCTION expire_stale_mill_reward_reservations(INTEGER) IS 'Atomically expires stale reward reservations, refunds olives through the 0084 trigger and releases physical stock.';

COMMIT;
