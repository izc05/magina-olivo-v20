BEGIN;

-- AEMET publica estos municipios con identificadores que coinciden con su
-- código municipal de cinco dígitos. Se habilita meteorología únicamente tras
-- haber contrastado cada municipio en la predicción oficial de AEMET.
UPDATE territory_municipalities
SET aemet_code = ine_code,
    weather_enabled = true,
    updated_at = now()
WHERE ine_code IN (
  '23015', -- Bélmez de la Moraleda
  '23017', -- Cabra del Santo Cristo
  '23018', -- Cambil
  '23019', -- Campillo de Arenas
  '23901', -- Cárcheles
  '23038', -- La Guardia de Jaén
  '23054', -- Larva
  '23058', -- Mancha Real
  '23064', -- Noalejo
  '23067', -- Pegalajar
  '23090'  -- Torres
);

-- Invariante defensivo: los 16 municipios activos del catálogo de Sierra
-- Mágina deben quedar con integración meteorológica explícita y código AEMET.
DO $$
DECLARE
  missing_count integer;
BEGIN
  SELECT count(*)::int
    INTO missing_count
    FROM territory_municipalities
   WHERE active = true
     AND (aemet_code IS NULL OR weather_enabled IS NOT TRUE);

  IF missing_count <> 0 THEN
    RAISE EXCEPTION 'Sierra Magina weather catalog incomplete: % active municipalities without AEMET', missing_count;
  END IF;
END $$;

COMMIT;
