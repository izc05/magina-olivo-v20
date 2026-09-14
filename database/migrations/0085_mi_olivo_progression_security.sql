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

-- New QR reservations use a high-entropy opaque token. Only the SHA-256 hash
-- is persisted, so a database read cannot be turned directly into a valid QR.
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

COMMENT ON TABLE mi_olivo_levels IS 'Mi Olivo thresholds. Level depends on lifetime positive XP, never spendable balance.';
COMMENT ON TABLE mi_olivo_wallet_guards IS 'Per-user transaction mutex for all olive-spending operations.';
COMMENT ON COLUMN mill_reward_redemptions.qr_token_hash IS 'SHA-256 hex hash of the opaque QR bearer token; raw token is never stored.';

COMMIT;
