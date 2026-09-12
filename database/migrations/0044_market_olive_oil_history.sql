CREATE TABLE IF NOT EXISTS market_olive_oil_weekly (
  source_key text NOT NULL,
  category text NOT NULL CHECK (category IN ('virgen-extra', 'virgen', 'lampante')),
  period_week smallint NOT NULL CHECK (period_week BETWEEN 1 AND 53),
  period_start date NOT NULL,
  period_end date NOT NULL,
  price_eur_kg numeric(8,4) NOT NULL CHECK (price_eur_kg > 0 AND price_eur_kg < 100),
  revision text NOT NULL,
  snapshot_published_on date NOT NULL,
  validated_through date NOT NULL,
  source_name text NOT NULL,
  source_url text NOT NULL,
  market_level text NOT NULL,
  status text NOT NULL DEFAULT 'validated' CHECK (status IN ('validated', 'provisional', 'superseded')),
  ingested_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_key, category, period_start),
  CHECK (period_end >= period_start),
  CHECK (validated_through >= period_end)
);

CREATE INDEX IF NOT EXISTS market_olive_oil_weekly_recent_idx
  ON market_olive_oil_weekly (source_key, period_end DESC, category)
  WHERE status = 'validated';

CREATE INDEX IF NOT EXISTS market_olive_oil_weekly_revision_idx
  ON market_olive_oil_weekly (revision, period_end DESC);

COMMENT ON TABLE market_olive_oil_weekly IS
  'Validated weekly olive-oil origin-price history used by the public Aceite y Mercado module.';
COMMENT ON COLUMN market_olive_oil_weekly.price_eur_kg IS
  'Oil price reference in EUR/kg at the source market level; never an olive-grower settlement price.';
COMMENT ON COLUMN market_olive_oil_weekly.revision IS
  'Source snapshot revision that most recently validated this weekly observation.';
COMMENT ON COLUMN market_olive_oil_weekly.snapshot_published_on IS
  'Publication date of the source snapshot that most recently validated this observation.';

-- Bootstrap the eight-week series already validated against the live Observatorio
-- publication for week 36. Future ingestion uses the same natural key and can
-- update corrected historical values without creating duplicates.
INSERT INTO market_olive_oil_weekly (
  source_key,
  category,
  period_week,
  period_start,
  period_end,
  price_eur_kg,
  revision,
  snapshot_published_on,
  validated_through,
  source_name,
  source_url,
  market_level,
  status
)
VALUES
  ('junta-andalucia-observatorio', 'virgen-extra', 29, '2026-07-13', '2026-07-19', 3.7600, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 30, '2026-07-20', '2026-07-26', 3.6000, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 31, '2026-07-27', '2026-08-02', 3.7000, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 32, '2026-08-03', '2026-08-09', 3.6300, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 33, '2026-08-10', '2026-08-16', 3.4400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 34, '2026-08-17', '2026-08-23', 3.4900, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 35, '2026-08-24', '2026-08-30', 3.7000, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen-extra', 36, '2026-08-31', '2026-09-06', 3.4200, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),

  ('junta-andalucia-observatorio', 'virgen', 29, '2026-07-13', '2026-07-19', 3.3400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 30, '2026-07-20', '2026-07-26', 3.2500, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 31, '2026-07-27', '2026-08-02', 3.2400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 32, '2026-08-03', '2026-08-09', 3.1600, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 33, '2026-08-10', '2026-08-16', 3.2800, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 34, '2026-08-17', '2026-08-23', 3.2400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 35, '2026-08-24', '2026-08-30', 3.2900, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'virgen', 36, '2026-08-31', '2026-09-06', 3.2500, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),

  ('junta-andalucia-observatorio', 'lampante', 29, '2026-07-13', '2026-07-19', 3.0500, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 30, '2026-07-20', '2026-07-26', 2.9900, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 31, '2026-07-27', '2026-08-02', 3.0000, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 32, '2026-08-03', '2026-08-09', 3.0100, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 33, '2026-08-10', '2026-08-16', 3.0400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 34, '2026-08-17', '2026-08-23', 3.0900, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 35, '2026-08-24', '2026-08-30', 3.1400, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated'),
  ('junta-andalucia-observatorio', 'lampante', 36, '2026-08-31', '2026-09-06', 3.1700, 'junta-andalucia-olive-oil-2026-w36-v1', '2026-09-09', '2026-09-06', 'Observatorio de Precios y Mercados · Junta de Andalucía', 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp', 'Almazara o bodega · Andalucía', 'validated')
ON CONFLICT (source_key, category, period_start)
DO UPDATE SET
  period_week = EXCLUDED.period_week,
  period_end = EXCLUDED.period_end,
  price_eur_kg = EXCLUDED.price_eur_kg,
  revision = EXCLUDED.revision,
  snapshot_published_on = EXCLUDED.snapshot_published_on,
  validated_through = EXCLUDED.validated_through,
  source_name = EXCLUDED.source_name,
  source_url = EXCLUDED.source_url,
  market_level = EXCLUDED.market_level,
  status = EXCLUDED.status,
  ingested_at = now();
