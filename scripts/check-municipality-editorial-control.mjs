import fs from 'node:fs';

const editorialPath = 'apps/web/src/app/admin/ayuntamientos/editorial/page.tsx';
const territoryPath = 'apps/api/src/routes/territory.ts';
const editorial = fs.readFileSync(editorialPath, 'utf8');
const territory = fs.readFileSync(territoryPath, 'utf8');

const requireText = (source, needle, label) => {
  if (!source.includes(needle)) throw new Error(`Missing ${label}: ${needle}`);
};

requireText(editorial, "adminApi.content()", 'CMS source');
requireText(editorial, "adminApi.territoryCatalog()", 'municipality source');
requireText(editorial, "municipality_role", 'municipality role classification');
requireText(editorial, "role === 'profile'", 'profile role');
requireText(editorial, "entry.featured", 'featured editorial state');
requireText(editorial, "sort_order", 'editorial priority');
requireText(editorial, "media_url", 'hero/discovery media');
requireText(editorial, "featuredCount >= 3", 'three essentials maximum');
requireText(editorial, "Solo puede haber tres imprescindibles destacados", 'featured guard feedback');
requireText(editorial, "Ver resultado público", 'public preview');
requireText(editorial, "Perfil principal", 'profile editor');
requireText(editorial, "Lo imprescindible", 'essential editor');
requireText(editorial, "status: event.target.value as CmsEntryStatus", 'publication state control');
requireText(editorial, "adminApi.updateContent", 'audited CMS mutation path');

if (/fetch\s*\(/.test(editorial)) throw new Error('Editorial control must use adminApi rather than direct fetch.');
if (/Math\.random/.test(editorial)) throw new Error('Editorial priority must remain deterministic.');

requireText(territory, "ORDER BY c.featured DESC, c.sort_order DESC", 'public featured/priority ordering');
requireText(territory, "'featured', c.featured", 'featured public projection');
requireText(territory, "'sort_order', c.sort_order", 'sort order public projection');
requireText(territory, "'media_url', c.media_url", 'media public projection');

console.log('Municipality editorial control contract OK: CMS profile/hero, max-three essentials, priority, publishing and public ordering are wired to canonical data.');
