import fs from 'node:fs';

const page = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/preview/page.tsx', import.meta.url), 'utf8');
const nav = fs.readFileSync(new URL('../apps/web/src/components/municipality-admin-nav.tsx', import.meta.url), 'utf8');
const docs = fs.readFileSync(new URL('../docs/MUNICIPALITIES_EDITORIAL_PREVIEW.md', import.meta.url), 'utf8');

if (!nav.includes("key: 'preview'") || !nav.includes("/admin/ayuntamientos/preview")) throw new Error('Municipality workspace navigation must expose Preview');
if (!page.includes('MunicipalityAdminNav') || !page.includes('active="preview"')) throw new Error('Preview must stay inside the municipal Admin workspace');
if (!page.includes('readMunicipalitySlug') || !page.includes('replaceMunicipalityContext')) throw new Error('Preview must preserve municipality URL context');
if (!page.includes('adminApi.content()') || !page.includes('adminApi.territoryCatalog()')) throw new Error('Draft preview must reuse canonical Admin data sources');
if (!page.includes("municipality_role") || !page.includes("=== 'profile'")) throw new Error('Preview must use the explicit municipality profile');
if (!page.includes("['heritage', 'nature', 'tourism']")) throw new Error('Preview must restrict discoveries to municipal discovery roles');
if (!page.includes('picks.length >= 3')) throw new Error('Preview must cap essentials at three');
if (!page.includes('discoveries.filter((entry) => entry.featured).forEach(add)')) throw new Error('Preview essentials must prefer explicit featured entries');
if (!page.includes("(['heritage', 'nature', 'tourism'] as Role[]).forEach")) throw new Error('Preview essentials must use role diversity after featured entries');
if (!page.includes('BORRADOR EDITORIAL') || !page.includes('RESULTADO PÚBLICO ACTUAL')) throw new Error('Preview must visually distinguish draft from current public result');
if (!page.includes('entry.status') || !page.includes('profile.status')) throw new Error('Preview must disclose CMS publication state');
if (!page.includes('<iframe') || !page.includes('src={`/ayuntamientos/${municipality.slug}`}')) throw new Error('Public comparison must load the real public municipality route');
if (!page.includes('5</strong>') && !page.includes('/5')) throw new Error('Preview must expose editorial preflight');
if (page.includes('adminApi.updateContent') || page.includes('adminApi.createContent')) throw new Error('Preview must be read-only');
if (!docs.includes('no hay botón de publicación') || !docs.includes('no se hacen mutaciones')) throw new Error('Preview documentation must state the read-only contract');

console.log('Municipality editorial preview contract OK: URL context, read-only CMS draft, max-three essentials and real public comparison are protected.');
