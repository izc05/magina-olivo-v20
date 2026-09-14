import fs from 'node:fs';

const migration = fs.readFileSync(new URL('../database/migrations/0061_municipalities_directory.sql', import.meta.url), 'utf8');
const tourismMigration = fs.readFileSync(new URL('../database/migrations/0062_municipality_tourism_urls.sql', import.meta.url), 'utf8');
const publicApi = fs.readFileSync(new URL('../apps/api/src/routes/territory.ts', import.meta.url), 'utf8');
const adminApi = fs.readFileSync(new URL('../apps/api/src/routes/admin-territory.ts', import.meta.url), 'utf8');
const explore = fs.readFileSync(new URL('../apps/web/src/app/explorar/explore-public-client.tsx', import.meta.url), 'utf8');
const municipalityDetail = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/[slug]/municipality-detail-client.tsx', import.meta.url), 'utf8');
const publicSource = fs.readFileSync(new URL('../apps/web/src/lib/public-territory-source.ts', import.meta.url), 'utf8');
const currentAffairsAdmin = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/actualidad/page.tsx', import.meta.url), 'utf8');
const heritageAdmin = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/patrimonio/page.tsx', import.meta.url), 'utf8');
const heritageCatalog = fs.readFileSync(new URL('../apps/web/src/lib/municipality-heritage-catalog.ts', import.meta.url), 'utf8');
const discoveryCatalog = fs.readFileSync(new URL('../apps/web/src/lib/municipality-discovery-catalog.ts', import.meta.url), 'utf8');
const municipalitiesAdmin = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/page.tsx', import.meta.url), 'utf8');
const municipalityAdminContext = fs.readFileSync(new URL('../apps/web/src/lib/municipality-admin-context.ts', import.meta.url), 'utf8');
const coverageAdmin = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/cobertura/page.tsx', import.meta.url), 'utf8');

const expected = [
  ['23001','Albanchez de Mágina','https://www.albanchezdemagina.es/'],['23902','Bedmar y Garcíez','https://www.bedmargarciez.es/'],['23015','Bélmez de la Moraleda','https://www.belmezdelamoraleda.es/'],['23017','Cabra del Santo Cristo','https://aytocabradelsantocristo.com/'],['23018','Cambil','https://cambil-arbuniel.es/'],['23019','Campillo de Arenas','https://www.campillodearenas.es/'],['23901','Cárcheles','https://www.carcheles.es/'],['23038','La Guardia de Jaén','https://laguardiadejaen.com/'],['23044','Huelma','https://www.aytohuelma.es/'],['23052','Jimena','https://www.jimena.es/'],['23053','Jódar','https://www.jodar.es/'],['23054','Larva','https://larva.es/'],['23058','Mancha Real','https://www.manchareal.es/'],['23064','Noalejo','https://www.noalejo.es/'],['23067','Pegalajar','https://ayto-pegalajar.org/'],['23090','Torres','https://www.ayuntamientodetorres.com/'],
];
const expectedTourism = [
  ['23001','https://www.albanchezdemagina.es/turismo/'],['23902','https://www.bedmargarciez.es/turismo/servicios-turisticos/'],['23015','https://www.belmezdelamoraleda.es/turismo/servicios-turisticos/'],['23018','https://cambil-arbuniel.es/turismo/'],['23019','https://www.campillodearenas.es/turismo/servicios-turisticos/'],['23901','https://www.carcheles.es/turismo/'],['23038','https://laguardiadejaen.com/tu-ciudad/informacion-turistica/'],['23052','https://jimenaturismo.grupofortalezas.com/'],['23053','https://www.jodar.es/turismo/'],['23054','https://www.larva.es/turismo/'],['23058','https://mancharealturismo.es/'],['23064','https://www.noalejo.es/turismo/'],['23067','https://ayto-pegalajar.org/descubre-pegalajar/'],['23090','https://www.torresturismo.es/'],
];
const expectedHeritage = [
  ['albanchez-de-magina','castillo-de-albanchez-de-magina'],['bedmar-y-garciez','castillos-viejo-y-nuevo-de-bedmar'],['belmez-de-la-moraleda','castillo-de-belmez'],['cabra-del-santo-cristo','parroquia-santuario-santo-cristo-de-burgos'],['cambil','castillo-de-mata-bejid'],['campillo-de-arenas','castillo-de-arenas'],['carcheles','ruinas-del-castillejo-de-carchel'],['la-guardia-de-jaen','castillo-de-la-guardia-de-jaen'],['huelma','castillo-de-solera'],['jimena','cueva-de-la-graja'],['jodar','castillo-de-jodar'],['larva','cerro-de-castellon'],['mancha-real','iglesia-san-juan-evangelista-mancha-real'],['noalejo','iglesia-nuestra-senora-de-la-asuncion-noalejo'],['pegalajar','fuente-de-la-reja-charca-y-huerta'],['torres','palacio-marqueses-de-camarasa'],
];
const expectedDiscovery = [
  ['albanchez-de-magina','fuente-de-la-seda-albanchez'],['bedmar-y-garciez','nacimiento-del-rio-cuadros'],['belmez-de-la-moraleda','barranco-del-arroyo-garganton'],['cabra-del-santo-cristo','puente-arroyo-salado'],['cambil','mata-bejid-y-chopos-singulares'],['campillo-de-arenas','desfiladero-de-puerta-arenas'],['carcheles','paraje-cazalla-carcheles'],['la-guardia-de-jaen','plaza-monumental-isabel-ii'],['huelma','iglesia-inmaculada-concepcion-huelma'],['jimena','pinar-de-canava'],['jodar','las-quebradas-jodar'],['larva','pozuelo-laguna-cueva-del-joso'],['mancha-real','pena-del-aguila-mancha-real'],['noalejo','navalcan-noalejo'],['pegalajar','cueva-de-aro-pegalajar'],['torres','manantial-de-fuenmayor'],
];

for (const [ine, name, website] of expected) if (!migration.includes(`'${ine}'`) || !migration.includes(`'${name}'`) || !migration.includes(`'${website}'`)) throw new Error(`Missing canonical municipality seed: ${ine} ${name}`);
for (const [ine, url] of expectedTourism) if (!tourismMigration.includes(`'${ine}'`) || !tourismMigration.includes(`'${url}'`)) throw new Error(`Missing verified tourism URL seed: ${ine} ${url}`);
if (expectedTourism.length !== 14) throw new Error(`Expected 14 verified tourism URL seeds, got ${expectedTourism.length}`);
if (!tourismMigration.includes('Cabra del Santo Cristo and Huelma intentionally remain NULL')) throw new Error('Tourism migration must document intentionally unverified municipalities');
for (const [municipalitySlug, resourceSlug] of expectedHeritage) if (!heritageCatalog.includes(`municipalitySlug: '${municipalitySlug}'`) || !heritageCatalog.includes(`slug: '${resourceSlug}'`)) throw new Error(`Missing verified heritage catalog item: ${municipalitySlug} ${resourceSlug}`);
if (expectedHeritage.length !== 16) throw new Error(`Expected 16 heritage catalog items, got ${expectedHeritage.length}`);
if (!heritageCatalog.includes("const VERIFIED_AT = '2026-09-13'")) throw new Error('Heritage catalog verification date is missing');
if (!heritageCatalog.includes('sourceUrl:') || !heritageCatalog.includes('sourceLabel:')) throw new Error('Heritage catalog must preserve source provenance');
for (const [municipalitySlug, resourceSlug] of expectedDiscovery) if (!discoveryCatalog.includes(`municipalitySlug: '${municipalitySlug}'`) || !discoveryCatalog.includes(`slug: '${resourceSlug}'`)) throw new Error(`Missing verified discovery catalog item: ${municipalitySlug} ${resourceSlug}`);
if (expectedDiscovery.length !== 16) throw new Error(`Expected 16 second-layer discovery items, got ${expectedDiscovery.length}`);
if (!discoveryCatalog.includes("const VERIFIED_AT = '2026-09-13'")) throw new Error('Discovery catalog verification date is missing');
if (!discoveryCatalog.includes("role: 'nature'") || !discoveryCatalog.includes("role: 'heritage'")) throw new Error('Discovery catalog must contain nature and heritage classifications');
if (!discoveryCatalog.includes('sourceUrl:') || !discoveryCatalog.includes('sourceLabel:')) throw new Error('Discovery catalog must preserve source provenance');
const uniqueCodes = new Set(expected.map(([ine]) => ine));
if (uniqueCodes.size !== 16) throw new Error(`Expected 16 unique INE codes, got ${uniqueCodes.size}`);
if (!migration.includes('territory_municipality_directory')) throw new Error('Directory table is missing');
if (!migration.includes("DATE '2026-09-13'") || !tourismMigration.includes("DATE '2026-09-13'")) throw new Error('Verification date is missing');
if (!publicApi.includes('/api/v1/public/territory/municipalities')) throw new Error('Public municipality collection endpoint is missing');
if (!publicApi.includes('/api/v1/public/territory/municipalities/:slug')) throw new Error('Public municipality detail endpoint is missing');
if (!publicApi.includes("c.content_json->>'municipality_id' = m.id::text")) throw new Error('Municipality CMS aggregation must use the explicit canonical municipality link');
if (!publicApi.includes("c.type IN ('place', 'mill', 'directory', 'news', 'event')")) throw new Error('Municipality hub content types are incomplete');
if (!publicApi.includes('starts_at IS NULL') || !publicApi.includes('ends_at IS NULL')) throw new Error('Municipality hub must respect publication windows');
if (!adminApi.includes('/api/v1/admin/territory/municipalities/:id/directory')) throw new Error('Admin municipality endpoint is missing');
if (!adminApi.includes('territory.municipality_directory_changed')) throw new Error('Admin audit event is missing');
if (!explore.includes("href: '/ayuntamientos'")) throw new Error('Explore does not link the municipality directory');
if (!publicSource.includes('related_content') || !publicSource.includes('content_counts')) throw new Error('Public municipality client contract is incomplete');
for (const section of ['Descubre el municipio', 'Patrimonio, naturaleza y lugares para descubrir', 'Ayuntamiento', 'Olivar y economía local', 'Actualidad local']) if (!municipalityDetail.includes(section)) throw new Error(`Municipality detail is missing section: ${section}`);
if (!municipalityDetail.includes("municipalityRole(entry) === 'profile'")) throw new Error('Municipality profile must prefer an explicitly classified profile entry');
for (const role of ['heritage', 'nature', 'tourism']) if (!municipalityDetail.includes(`'${role}'`)) throw new Error(`Municipality detail is missing discovery role: ${role}`);
if (!municipalityDetail.includes('No mostramos contenido por coincidencias de texto')) throw new Error('Municipality detail must document explicit-only news/event linkage');
if (!municipalitiesAdmin.includes('/admin/ayuntamientos/actualidad')) throw new Error('Municipality admin must expose the current-affairs linker');
if (!municipalitiesAdmin.includes('/admin/ayuntamientos/cobertura')) throw new Error('Municipality admin must expose the coverage dashboard');
if (!municipalitiesAdmin.includes('/admin/ayuntamientos/patrimonio')) throw new Error('Municipality admin must expose heritage and tourism administration');
if (!municipalitiesAdmin.includes('readMunicipalitySlug') || !municipalityAdminContext.includes('window.location.hash')) throw new Error('Municipality admin must preserve direct municipality anchors through the shared resolver');
if (!currentAffairsAdmin.includes("entry.type === 'news' || entry.type === 'event'")) throw new Error('Current-affairs linker must be restricted to news/events');
if (!currentAffairsAdmin.includes('municipality_id') || !currentAffairsAdmin.includes('municipality_name') || !currentAffairsAdmin.includes('municipality_slug')) throw new Error('Current-affairs linker must persist the canonical municipality identity');
if (!currentAffairsAdmin.includes('Ámbito general')) throw new Error('Current-affairs linker must support removing the municipality relation');
if (!heritageAdmin.includes("entry.type === 'place'")) throw new Error('Heritage administration must reuse CMS place entries');
for (const role of ['profile', 'heritage', 'nature', 'tourism']) if (!heritageAdmin.includes(`'${role}'`)) throw new Error(`Heritage administration is missing municipality_role: ${role}`);
if (!heritageAdmin.includes('municipality_id') || !heritageAdmin.includes('municipality_role')) throw new Error('Heritage administration must persist explicit municipality identity and role');
if (!heritageAdmin.includes('MUNICIPALITY_HERITAGE_CATALOG') || !heritageAdmin.includes('MUNICIPALITY_DISCOVERY_CATALOG')) throw new Error('Heritage administration must combine both verified catalogs');
if (!heritageAdmin.includes('OFFICIAL_DISCOVERY_CATALOG')) throw new Error('Admin importer must operate on the combined official discovery catalog');
if (!heritageAdmin.includes("status: 'published'")) throw new Error('New verified heritage imports must be published explicitly');
if (!heritageAdmin.includes('existingBySlug')) throw new Error('Heritage importer must be idempotent by CMS slug');
if (!heritageAdmin.includes('source_url') || !heritageAdmin.includes('verified_at')) throw new Error('Heritage importer must persist provenance fields');
if (!heritageAdmin.includes('adminApi.createContent') || !heritageAdmin.includes('adminApi.updateContent')) throw new Error('Heritage importer must use authenticated CMS write paths');
for (const signal of ['Ficha pública', 'Web oficial', 'Teléfono', 'Email', 'Dirección', 'Sede / transparencia / turismo', 'Localidades públicas', 'Perfil editorial', 'Cooperativas / empresas', 'Noticias / eventos']) if (!coverageAdmin.includes(signal)) throw new Error(`Coverage dashboard is missing signal: ${signal}`);
if (!coverageAdmin.includes("entry.status !== 'published'")) throw new Error('Coverage dashboard must ignore unpublished CMS entries');
if (!coverageAdmin.includes('entry.starts_at') || !coverageAdmin.includes('entry.ends_at')) throw new Error('Coverage dashboard must respect CMS publication windows');
if (!coverageAdmin.includes('Solo con huecos')) throw new Error('Coverage dashboard must allow filtering incomplete municipalities');
console.log('Municipality directory contract OK: 16 canonical municipalities, 14 verified tourism URLs, 32 audited discovery seeds across two layers, authenticated idempotent CMS importer, public/admin API, CMS hub aggregation and coverage dashboard.');
