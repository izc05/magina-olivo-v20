BEGIN;

-- Completa el catálogo territorial de Sierra Mágina hasta 16 municipios.
-- Los códigos INE se contrastan con INE. aemet_code se deja NULL hasta
-- validarlo explícitamente contra el catálogo vigente de AEMET; no se infiere.
INSERT INTO territory_municipalities (ine_code, aemet_code, name, slug)
VALUES
  ('23015', NULL, 'Bélmez de la Moraleda', 'belmez-de-la-moraleda'),
  ('23017', NULL, 'Cabra del Santo Cristo', 'cabra-del-santo-cristo'),
  ('23018', NULL, 'Cambil', 'cambil'),
  ('23019', NULL, 'Campillo de Arenas', 'campillo-de-arenas'),
  ('23901', NULL, 'Cárcheles', 'carcheles'),
  ('23038', NULL, 'La Guardia de Jaén', 'la-guardia-de-jaen'),
  ('23054', NULL, 'Larva', 'larva'),
  ('23058', NULL, 'Mancha Real', 'mancha-real'),
  ('23064', NULL, 'Noalejo', 'noalejo'),
  ('23067', NULL, 'Pegalajar', 'pegalajar'),
  ('23090', NULL, 'Torres', 'torres')
ON CONFLICT (ine_code) DO UPDATE
SET
  name = EXCLUDED.name,
  slug = EXCLUDED.slug,
  active = true,
  updated_at = now();

COMMENT ON TABLE territory_municipalities IS
  'Official administrative municipalities. Sierra Mágina catalog expanded to its 16 target municipalities; external provider codes remain independently verified.';

COMMIT;
