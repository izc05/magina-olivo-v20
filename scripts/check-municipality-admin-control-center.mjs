import fs from 'node:fs';

const page = fs.readFileSync('apps/web/src/app/admin/ayuntamientos/page.tsx', 'utf8');
const css = fs.readFileSync('apps/web/src/app/admin/ayuntamientos/municipalities-admin.module.css', 'utf8');

const checks = [
  ['control center title', page.includes('Centro de control municipal')],
  ['summary public metric', page.includes('Publicados') && page.includes('stats.publicCount')],
  ['summary hidden metric', page.includes('Ocultos') && page.includes('stats.hiddenCount')],
  ['summary incomplete metric', page.includes('Con huecos') && page.includes('stats.incompleteCount')],
  ['tourism metric', page.includes('Turismo oficial') && page.includes('stats.tourismCount')],
  ['search', page.includes('Buscar municipio o INE') && page.includes('setQuery')],
  ['filters', page.includes("'public'") && page.includes("'hidden'") && page.includes("'incomplete'")],
  ['filter accessibility', page.includes('aria-pressed={listFilter === value}')],
  ['completion calculation', page.includes('completenessFields') && page.includes('selectedCompletion.percent')],
  ['public visibility control', page.includes('Visible públicamente') && page.includes('public_enabled')],
  ['public preview', page.includes('Ver ficha pública ↗') && page.includes('/ayuntamientos/${selected.slug}')],
  ['coverage action', page.includes('Revisar cobertura')],
  ['heritage action', page.includes('Patrimonio y turismo')],
  ['news action', page.includes('Noticias y eventos')],
  ['canonical admin API', page.includes('/api/v1/admin/territory/municipalities/${id}/directory')],
  ['responsive layout', css.includes('@media(max-width:820px)') && css.includes('@media(max-width:620px)')],
  ['focus visible', css.includes(':focus-visible')],
  ['published state styling', css.includes("data-enabled='false'")],
];

const failed = checks.filter(([, ok]) => !ok);
if (failed.length) {
  console.error('Municipality Admin control center contract failed:');
  for (const [name] of failed) console.error(`- ${name}`);
  process.exit(1);
}

console.log('Municipality Admin control center contract OK: governance, visibility, completeness, navigation and responsive layout.');
