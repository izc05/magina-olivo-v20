import fs from 'node:fs';

const migration = fs.readFileSync(new URL('../database/migrations/0061_municipalities_directory.sql', import.meta.url), 'utf8');
const publicApi = fs.readFileSync(new URL('../apps/api/src/routes/territory.ts', import.meta.url), 'utf8');
const adminApi = fs.readFileSync(new URL('../apps/api/src/routes/admin-territory.ts', import.meta.url), 'utf8');
const explore = fs.readFileSync(new URL('../apps/web/src/app/explorar/explore-public-client.tsx', import.meta.url), 'utf8');

const expected = [
  ['23001','Albanchez de Mágina','https://www.albanchezdemagina.es/'],
  ['23902','Bedmar y Garcíez','https://www.bedmargarciez.es/'],
  ['23015','Bélmez de la Moraleda','https://www.belmezdelamoraleda.es/'],
  ['23017','Cabra del Santo Cristo','https://aytocabradelsantocristo.com/'],
  ['23018','Cambil','https://cambil-arbuniel.es/'],
  ['23019','Campillo de Arenas','https://www.campillodearenas.es/'],
  ['23901','Cárcheles','https://www.carcheles.es/'],
  ['23038','La Guardia de Jaén','https://laguardiadejaen.com/'],
  ['23044','Huelma','https://www.aytohuelma.es/'],
  ['23052','Jimena','https://www.jimena.es/'],
  ['23053','Jódar','https://www.jodar.es/'],
  ['23054','Larva','https://larva.es/'],
  ['23058','Mancha Real','https://www.manchareal.es/'],
  ['23064','Noalejo','https://www.noalejo.es/'],
  ['23067','Pegalajar','https://ayto-pegalajar.org/'],
  ['23090','Torres','https://www.ayuntamientodetorres.com/'],
];

for (const [ine, name, website] of expected) {
  if (!migration.includes(`'${ine}'`) || !migration.includes(`'${name}'`) || !migration.includes(`'${website}'`)) {
    throw new Error(`Missing canonical municipality seed: ${ine} ${name}`);
  }
}

const uniqueCodes = new Set(expected.map(([ine]) => ine));
if (uniqueCodes.size !== 16) throw new Error(`Expected 16 unique INE codes, got ${uniqueCodes.size}`);
if (!migration.includes('territory_municipality_directory')) throw new Error('Directory table is missing');
if (!migration.includes("DATE '2026-09-13'")) throw new Error('Verification date is missing');
if (!publicApi.includes('/api/v1/public/territory/municipalities')) throw new Error('Public municipality collection endpoint is missing');
if (!publicApi.includes('/api/v1/public/territory/municipalities/:slug')) throw new Error('Public municipality detail endpoint is missing');
if (!adminApi.includes('/api/v1/admin/territory/municipalities/:id/directory')) throw new Error('Admin municipality endpoint is missing');
if (!adminApi.includes('territory.municipality_directory_changed')) throw new Error('Admin audit event is missing');
if (!explore.includes("href: '/ayuntamientos'")) throw new Error('Explore does not link the municipality directory');

console.log('Municipality directory contract OK: 16 canonical municipalities, verified websites, public/admin API and Explore entry.');
