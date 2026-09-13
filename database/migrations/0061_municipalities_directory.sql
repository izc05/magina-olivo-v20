BEGIN;

CREATE TABLE territory_municipality_directory (
  municipality_id UUID PRIMARY KEY REFERENCES territory_municipalities(id) ON DELETE CASCADE,
  official_website TEXT NOT NULL CHECK (official_website ~ '^https://'),
  electronic_office_url TEXT CHECK (electronic_office_url IS NULL OR electronic_office_url ~ '^https://'),
  transparency_url TEXT CHECK (transparency_url IS NULL OR transparency_url ~ '^https://'),
  tourism_url TEXT CHECK (tourism_url IS NULL OR tourism_url ~ '^https://'),
  phone TEXT,
  email TEXT,
  address TEXT,
  postal_code TEXT,
  source_url TEXT NOT NULL CHECK (source_url ~ '^https://'),
  verified_at DATE NOT NULL,
  public_enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE territory_municipality_directory IS 'Verified institutional directory for the canonical Sierra Magina municipalities. Contact/editorial data is kept separate from GIS and weather fields.';

INSERT INTO territory_municipalities (ine_code, aemet_code, name, slug, province_code, province_name, active, weather_enabled)
VALUES
  ('23001','23001','Albanchez de Mágina','albanchez-de-magina','23','Jaén',true,true),
  ('23902','23902','Bedmar y Garcíez','bedmar-y-garciez','23','Jaén',true,true),
  ('23015','23015','Bélmez de la Moraleda','belmez-de-la-moraleda','23','Jaén',true,true),
  ('23017','23017','Cabra del Santo Cristo','cabra-del-santo-cristo','23','Jaén',true,true),
  ('23018','23018','Cambil','cambil','23','Jaén',true,true),
  ('23019','23019','Campillo de Arenas','campillo-de-arenas','23','Jaén',true,true),
  ('23901','23901','Cárcheles','carcheles','23','Jaén',true,true),
  ('23038','23038','La Guardia de Jaén','la-guardia-de-jaen','23','Jaén',true,true),
  ('23044','23044','Huelma','huelma','23','Jaén',true,true),
  ('23052','23052','Jimena','jimena','23','Jaén',true,true),
  ('23053','23053','Jódar','jodar','23','Jaén',true,true),
  ('23054','23054','Larva','larva','23','Jaén',true,true),
  ('23058','23058','Mancha Real','mancha-real','23','Jaén',true,true),
  ('23064','23064','Noalejo','noalejo','23','Jaén',true,true),
  ('23067','23067','Pegalajar','pegalajar','23','Jaén',true,true),
  ('23090','23090','Torres','torres','23','Jaén',true,true)
ON CONFLICT (ine_code) DO UPDATE SET
  aemet_code = EXCLUDED.aemet_code,
  name = EXCLUDED.name,
  slug = EXCLUDED.slug,
  active = true,
  weather_enabled = EXCLUDED.weather_enabled,
  updated_at = now();

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT m.id, seed.name, seed.slug, 'municipal_seat', true, true
FROM (VALUES
  ('23001','Albanchez de Mágina','albanchez-de-magina'),
  ('23902','Bedmar','bedmar'),
  ('23015','Bélmez de la Moraleda','belmez-de-la-moraleda'),
  ('23017','Cabra del Santo Cristo','cabra-del-santo-cristo'),
  ('23018','Cambil','cambil'),
  ('23019','Campillo de Arenas','campillo-de-arenas'),
  ('23901','Cárcheles','carcheles'),
  ('23038','La Guardia de Jaén','la-guardia-de-jaen'),
  ('23044','Huelma','huelma'),
  ('23052','Jimena','jimena'),
  ('23053','Jódar','jodar'),
  ('23054','Larva','larva'),
  ('23058','Mancha Real','mancha-real'),
  ('23064','Noalejo','noalejo'),
  ('23067','Pegalajar','pegalajar'),
  ('23090','Torres','torres')
) AS seed(ine_code,name,slug)
JOIN territory_municipalities m ON m.ine_code = seed.ine_code
ON CONFLICT (slug) DO UPDATE SET public_enabled = true;

INSERT INTO territory_municipality_directory
  (municipality_id, official_website, electronic_office_url, phone, email, address, postal_code, source_url, verified_at)
SELECT m.id, seed.website, seed.sede, seed.phone, seed.email, seed.address, seed.postal_code, seed.source_url, DATE '2026-09-13'
FROM (VALUES
 ('23001','https://www.albanchezdemagina.es/',NULL,'953 347 009','ayuntamiento@albanchezdemagina.es',NULL,NULL,'https://www.albanchezdemagina.es/'),
 ('23902','https://www.bedmargarciez.es/',NULL,'953 760 002','alcaldia@bedmargarciez.es',NULL,NULL,'https://www.bedmargarciez.es/'),
 ('23015','https://www.belmezdelamoraleda.es/',NULL,'953 394 002','belmez@dipujaen.es',NULL,NULL,'https://www.belmezdelamoraleda.es/'),
 ('23017','https://aytocabradelsantocristo.com/',NULL,'953 397 002',NULL,'Plaza de la Constitución, 1',NULL,'https://aytocabradelsantocristo.com/servicios-publicos/'),
 ('23018','https://cambil-arbuniel.es/',NULL,'953 300 427',NULL,NULL,NULL,'https://cambil-arbuniel.es/'),
 ('23019','https://www.campillodearenas.es/',NULL,'953 309 004','ayuntamiento@campillodearenas.es',NULL,NULL,'https://www.campillodearenas.es/'),
 ('23901','https://www.carcheles.es/',NULL,'953 302 003','ayuntamiento@carcheles.es',NULL,NULL,'https://www.carcheles.es/'),
 ('23038','https://laguardiadejaen.com/','https://laguardiadejaen.sedelectronica.es/','953 327 100','ayuntamiento@laguardiadejaen.es','Plaza San Pedro, s/n',NULL,'https://laguardiadejaen.com/'),
 ('23044','https://www.aytohuelma.es/',NULL,'953 390 210',NULL,'Plaza de España, 1','23560','https://www.aytohuelma.es/'),
 ('23052','https://www.jimena.es/',NULL,'953 357 001','ayuntamientodejimena@gmail.com',NULL,NULL,'https://www.jimena.es/'),
 ('23053','https://www.jodar.es/',NULL,'953 785 086','ayuntamiento@jodar.es',NULL,NULL,'https://www.jodar.es/'),
 ('23054','https://larva.es/',NULL,'953 781 500','ayuntamiento@larva.es',NULL,NULL,'https://larva.es/'),
 ('23058','https://www.manchareal.es/',NULL,'953 350 157','ayuntamiento@manchareal.es',NULL,NULL,'https://www.manchareal.es/'),
 ('23064','https://www.noalejo.es/',NULL,'953 306 134','noalejoayuntamiento@gmail.com',NULL,NULL,'https://www.noalejo.es/'),
 ('23067','https://ayto-pegalajar.org/','https://pegalajar.sedelectronica.es/','953 360 003','ayuntamiento@pegalajar.es','Plaza de la Constitución, 10','23110','https://ayto-pegalajar.org/'),
 ('23090','https://www.ayuntamientodetorres.com/',NULL,'953 363 011','comunicacionaytorres@gmail.com',NULL,NULL,'https://www.ayuntamientodetorres.com/')
) AS seed(ine_code,website,sede,phone,email,address,postal_code,source_url)
JOIN territory_municipalities m ON m.ine_code = seed.ine_code
ON CONFLICT (municipality_id) DO UPDATE SET
  official_website = EXCLUDED.official_website,
  electronic_office_url = EXCLUDED.electronic_office_url,
  phone = EXCLUDED.phone,
  email = EXCLUDED.email,
  address = EXCLUDED.address,
  postal_code = EXCLUDED.postal_code,
  source_url = EXCLUDED.source_url,
  verified_at = EXCLUDED.verified_at,
  public_enabled = true,
  updated_at = now();

COMMIT;
