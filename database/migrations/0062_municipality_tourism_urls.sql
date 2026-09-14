BEGIN;

-- Verified municipal tourism references, reviewed 2026-09-13.
-- Cabra del Santo Cristo and Huelma intentionally remain NULL until a sufficiently
-- clear official tourism destination is verified. Do not infer or fabricate URLs.
UPDATE territory_municipality_directory AS d
SET tourism_url = seed.tourism_url,
    verified_at = DATE '2026-09-13',
    updated_at = now()
FROM territory_municipalities AS m
JOIN (VALUES
  ('23001', 'https://www.albanchezdemagina.es/turismo/'),
  ('23902', 'https://www.bedmargarciez.es/turismo/servicios-turisticos/'),
  ('23015', 'https://www.belmezdelamoraleda.es/turismo/servicios-turisticos/'),
  ('23018', 'https://cambil-arbuniel.es/turismo/'),
  ('23019', 'https://www.campillodearenas.es/turismo/servicios-turisticos/'),
  ('23901', 'https://www.carcheles.es/turismo/'),
  ('23038', 'https://laguardiadejaen.com/tu-ciudad/informacion-turistica/'),
  ('23052', 'https://jimenaturismo.grupofortalezas.com/'),
  ('23053', 'https://www.jodar.es/turismo/'),
  ('23054', 'https://www.larva.es/turismo/'),
  ('23058', 'https://mancharealturismo.es/'),
  ('23064', 'https://www.noalejo.es/turismo/'),
  ('23067', 'https://ayto-pegalajar.org/descubre-pegalajar/'),
  ('23090', 'https://www.torresturismo.es/')
) AS seed(ine_code, tourism_url)
  ON m.ine_code = seed.ine_code
WHERE d.municipality_id = m.id;

COMMIT;
