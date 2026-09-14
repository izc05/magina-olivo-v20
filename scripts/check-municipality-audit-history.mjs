import fs from 'node:fs';

const page = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/historial/page.tsx', import.meta.url), 'utf8');
const nav = fs.readFileSync(new URL('../apps/web/src/components/municipality-admin-nav.tsx', import.meta.url), 'utf8');
const docs = fs.readFileSync(new URL('../docs/MUNICIPALITIES_AUDIT_HISTORY.md', import.meta.url), 'utf8');

if (!nav.includes("key: 'historial'") || !nav.includes('/admin/ayuntamientos/historial')) throw new Error('Municipality workspace must expose Historial');
if (!page.includes('MunicipalityAdminNav') || !page.includes('active="historial"')) throw new Error('History must stay inside the municipal workspace');
if (!page.includes('adminApi.audit()') || !page.includes('adminApi.content()') || !page.includes('adminApi.territoryCatalog()')) throw new Error('History must reuse existing canonical Admin sources');
if (!page.includes('readMunicipalitySlug') || !page.includes('replaceMunicipalityContext')) throw new Error('History must preserve municipality URL context');
if (!page.includes("field(entry, 'municipality_id')")) throw new Error('History must correlate CMS entries through the explicit municipality id');
if (!page.includes('municipalTargetIds.has(entry.target_id)')) throw new Error('History must correlate audit target ids with municipal targets');
if (!page.includes('metadataMentions(entry.metadata, [municipality.id, municipality.slug])')) throw new Error('History must support canonical identity correlation in audit metadata');
if (!page.includes('actor_user_id.slice(0, 8)')) throw new Error('History should expose only an abbreviated actor identifier');
if (page.includes('adminApi.users()')) throw new Error('Municipality history must not fetch the user directory');
if (!page.includes("['type', 'slug', 'status', 'name']")) throw new Error('History must restrict rendered audit metadata to the safe allowlist');
if (page.includes('JSON.stringify(entry.metadata')) throw new Error('History must not render raw audit metadata');
if (!page.includes('ventana de auditoría disponible')) throw new Error('History must explain audit-window limitations');
if (page.includes('adminApi.updateContent') || page.includes('adminApi.createContent') || page.includes('apiFetch(')) throw new Error('History must remain read-only and reuse adminApi');
if (!docs.includes('sin endpoint nuevo') || !docs.includes('metadata completa del evento no se renderiza')) throw new Error('History documentation must preserve reuse and privacy rules');

console.log('Municipality audit history contract OK: existing audit reuse, canonical correlation, privacy-safe rendering and read-only workspace integration.');
