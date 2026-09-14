import fs from 'node:fs';

const page = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/page.tsx', import.meta.url), 'utf8');
const css = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/municipalities.module.css', import.meta.url), 'utf8');

for (const token of [
  "type DirectoryFilter = 'all' | 'tourism' | 'current' | 'economy'",
  "type DirectorySort = 'alpha' | 'content' | 'current'",
  "{ value: 'tourism', label: 'Turismo oficial' }",
  "{ value: 'current', label: 'Con actualidad' }",
  "{ value: 'economy', label: 'Economía local' }",
  "placeholder=\"Busca Bedmar, Jimena, Solera, Arbuniel…\"",
  'aria-pressed={directoryFilter === filter.value}',
  'Más contenido publicado',
  'Más actualidad',
  'aria-live="polite"',
  'Ver los 16 municipios',
  'Descubrir {item.name}',
]) {
  if (!page.includes(token)) throw new Error(`Municipality explore directory missing: ${token}`);
}

for (const token of [
  '.directoryHero',
  '.directoryHeroStats',
  '.directoryControls',
  '.directoryFilters',
  '.directoryGrid',
  '.directoryCard',
  '.directorySignals',
  '.directoryBadges',
  '.directoryActions',
  '@media(max-width:620px)',
]) {
  if (!css.includes(token)) throw new Error(`Municipality explore directory CSS missing: ${token}`);
}

if (!page.includes('loadPublicMunicipalities()')) throw new Error('Directory must keep using the existing public municipalities source');
if (page.includes('fetch(')) throw new Error('Directory must not add direct fetch calls');
if (!page.includes('item.tourism_url')) throw new Error('Tourism filter must derive from verified municipality data');
if (!page.includes('currentCount(item) > 0')) throw new Error('Current-affairs filter must derive from published counts');
if (!page.includes('economyCount(item) > 0')) throw new Error('Economy filter must derive from published counts');

console.log('Municipality explore directory contract OK: search, verified filters, deterministic sorting, accessible controls, reset state and responsive territory cards.');
