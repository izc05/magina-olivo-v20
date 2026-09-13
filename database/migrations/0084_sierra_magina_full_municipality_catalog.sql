BEGIN;

-- Completa el catálogo canónico de Sierra Mágina hasta los 16 municipios
-- publicados por Diputación de Jaén. Los nuevos municipios quedan sin
-- integración meteorológica hasta verificar su código AEMET de forma expresa.
INSERT INTO territory_municipalities (
  ine_code, aemet_code, name, slug, active, weather_enabled
)
VALUES
  ('23015', NULL, 'Bélmez de la Moraleda', 'belmez-de-la-moraleda', true, false),
  ('23017', NULL, 'Cabra del Santo Cristo', 'cabra-del-santo-cristo', true, false),
  ('23018', NULL, 'Cambil', 'cambil', true, false),
  ('23019', NULL, 'Campillo de Arenas', 'campillo-de-arenas', true, false),
  ('23901', NULL, 'Cárcheles', 'carcheles', true, false),
  ('23038', NULL, 'La Guardia de Jaén', 'la-guardia-de-jaen', true, false),
  ('23054', NULL, 'Larva', 'larva', true, false),
  ('23058', NULL, 'Mancha Real', 'mancha-real', true, false),
  ('23064', NULL, 'Noalejo', 'noalejo', true, false),
  ('23067', NULL, 'Pegalajar', 'pegalajar', true, false),
  ('23090', NULL, 'Torres', 'torres', true, false)
ON CONFLICT (ine_code) DO NOTHING;

-- Una cabecera pública canónica por municipio. Cárcheles tiene su sede
-- administrativa en Carchelejo; Cárchel se publica además como localidad.
INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Bélmez de la Moraleda', 'belmez-de-la-moraleda', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23015'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Cabra del Santo Cristo', 'cabra-del-santo-cristo', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23017'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Cambil', 'cambil', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23018'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Campillo de Arenas', 'campillo-de-arenas', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23019'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Carchelejo', 'carchelejo', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23901'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Cárchel', 'carchel', 'locality', false, true
FROM territory_municipalities WHERE ine_code = '23901'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'La Guardia de Jaén', 'la-guardia-de-jaen', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23038'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Larva', 'larva', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23054'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Mancha Real', 'mancha-real', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23058'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Noalejo', 'noalejo', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23064'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Pegalajar', 'pegalajar', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23067'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO territory_places (municipality_id, name, slug, kind, is_default_for_municipality, public_enabled)
SELECT id, 'Torres', 'torres', 'municipal_seat', true, true
FROM territory_municipalities WHERE ine_code = '23090'
ON CONFLICT (slug) DO NOTHING;

-- Web municipal y sede electrónica: solo destinos contrastados en fuentes
-- institucionales o en la propia titularidad legal de cada portal.
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Bélmez de la Moraleda', 'https://www.belmezdelamoraleda.es/', 'https://www.belmezdelamoraleda.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23015'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Bélmez de la Moraleda', 'https://belmezdelamoraleda.sedelectronica.es/info.0', 'https://belmezdelamoraleda.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23015'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Cabra del Santo Cristo', 'https://aytocabradelsantocristo.com/', 'https://aytocabradelsantocristo.com/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23017'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Cabra del Santo Cristo', 'https://cabradelsantocristo.sedelectronica.es/info.0', 'https://cabradelsantocristo.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23017'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Cambil', 'https://cambil-arbuniel.es/', 'https://www.dipujaen.es/export/files/dipujaen/presidencia/Folleto_X_Feria_Pueblos_Jaen2026.pdf', now(), 10
FROM territory_municipalities WHERE ine_code = '23018'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Cambil', 'https://cambil.sedelectronica.es/info.0', 'https://cambil.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23018'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Campillo de Arenas', 'https://www.campillodearenas.es/', 'https://www.campillodearenas.es/contacto/', now(), 10
FROM territory_municipalities WHERE ine_code = '23019'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Campillo de Arenas', 'https://campillodearenas.sedelectronica.es/info.0', 'https://campillodearenas.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23019'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Cárcheles', 'https://www.carcheles.es/', 'https://www.carcheles.es/contacto/', now(), 10
FROM territory_municipalities WHERE ine_code = '23901'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Cárcheles', 'https://carcheles.sedelectronica.es/info.0', 'https://carcheles.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23901'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de La Guardia de Jaén', 'https://laguardiadejaen.com/', 'https://laguardiadejaen.com/portal-transparencia/informacion-institucional/', now(), 10
FROM territory_municipalities WHERE ine_code = '23038'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de La Guardia de Jaén', 'https://laguardiadejaen.sedelectronica.es/info.0', 'https://laguardiadejaen.com/tramites/', now(), 20
FROM territory_municipalities WHERE ine_code = '23038'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Larva', 'https://www.larva.es/', 'https://www.larva.es/', now(), 10
FROM territory_municipalities WHERE ine_code = '23054'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Larva', 'https://larva.sedelectronica.es/info.0', 'https://larva.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23054'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Mancha Real', 'https://www.manchareal.es/', 'https://www.manchareal.es/', now(), 10
FROM territory_municipalities WHERE ine_code = '23058'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Mancha Real', 'https://manchareal.sedelectronica.es/info.0', 'https://manchareal.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23058'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Noalejo', 'https://www.noalejo.es/', 'https://www.noalejo.es/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23064'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Noalejo', 'https://noalejo.sedelectronica.es/info.0', 'https://noalejo.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23064'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Pegalajar', 'https://ayto-pegalajar.es/', 'https://ayto-pegalajar.es/politica-de-privacidad/', now(), 10
FROM territory_municipalities WHERE ine_code = '23067'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Pegalajar', 'https://pegalajar.sedelectronica.es/info.0', 'https://pegalajar.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23067'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'town_hall', 'Ayuntamiento de Torres', 'https://www.ayuntamientodetorres.com/', 'https://www.ayuntamientodetorres.com/aviso-legal/', now(), 10
FROM territory_municipalities WHERE ine_code = '23090'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;
INSERT INTO territory_municipality_official_links (municipality_id, kind, label, url, source_url, verified_at, sort_order)
SELECT id, 'electronic_office', 'Sede electrónica de Torres', 'https://aytorres.sedelectronica.es/info.0', 'https://aytorres.sedelectronica.es/info.0', now(), 20
FROM territory_municipalities WHERE ine_code = '23090'
ON CONFLICT (municipality_id, kind, url) DO NOTHING;

COMMIT;
