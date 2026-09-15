import fs from 'node:fs';

const page = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/huecos/page.tsx', import.meta.url), 'utf8');
const nav = fs.readFileSync(new URL('../apps/web/src/components/municipality-admin-nav.tsx', import.meta.url), 'utf8');
const docs = fs.readFileSync(new URL('../docs/MUNICIPALITIES_GAP_MATRIX.md', import.meta.url), 'utf8');

if (!nav.includes("key: 'huecos'") || !nav.includes('/admin/ayuntamientos/huecos')) throw new Error('Municipality workspace must expose Huecos');
if (!page.includes('MunicipalityAdminNav') || !page.includes('active="huecos"')) throw new Error('Gap matrix must stay inside the municipality workspace');
if (!page.includes('adminApi.territoryCatalog()') || !page.includes('adminApi.content()')) throw new Error('Gap matrix must reuse canonical Admin sources');
if (!page.includes("field(entry, 'municipality_id')")) throw new Error('Gap matrix must use explicit municipality_id linkage');
if (!page.includes("entry.status !== 'published'")) throw new Error('Gap matrix must ignore unpublished content');
if (!page.includes('entry.starts_at') || !page.includes('entry.ends_at')) throw new Error('Gap matrix must respect publication windows');
for (const signal of ['Perfil principal publicado','Resumen editorial','Imagen hero real','Fuente + verificación','Patrimonio publicado','Naturaleza publicada','Turismo publicado','Actualidad local publicada','Economía local publicada']) {
  if (!page.includes(signal)) throw new Error(`Gap matrix missing signal: ${signal}`);
}
for (const type of ["entry.type === 'news'", "entry.type === 'event'", "entry.type === 'mill'", "entry.type === 'directory'"]) {
  if (!page.includes(type)) throw new Error(`Gap matrix missing content type check: ${type}`);
}
if (!page.includes("sort((a, b) => a.name.localeCompare(b.name, 'es'))")) throw new Error('Municipalities must remain alphabetically ordered');
if (!page.includes('sin nota ni ranking')) throw new Error('UI must explicitly reject subjective scoring');
if (!page.includes('fixHref') || !page.includes('fixLabel')) throw new Error('Every missing signal must be actionable');
if (page.includes('adminApi.updateContent') || page.includes('adminApi.createContent')) throw new Error('Gap matrix must remain read-only');
if (!docs.includes('no existe una nota subjetiva') || !docs.includes('No añade tablas, endpoints, migraciones')) throw new Error('Gap matrix documentation must preserve objective/reuse rules');

console.log('Municipality gap matrix contract OK: 9 objective signals, publication windows, alphabetical order, direct fixes and read-only reuse.');
