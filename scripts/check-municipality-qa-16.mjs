import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const must = (condition, message) => { if (!condition) throw new Error(message); };

const seo = read('apps/web/src/lib/municipality-seo.ts');
const publicPage = read('apps/web/src/app/ayuntamientos/[slug]/page.tsx');
const publicClient = read('apps/web/src/app/ayuntamientos/[slug]/municipality-detail-client.tsx');
const publicSource = read('apps/web/src/lib/public-territory-source.ts');
const nav = read('apps/web/src/components/municipality-admin-nav.tsx');
const context = read('apps/web/src/lib/municipality-admin-context.ts');
const preview = read('apps/web/src/app/admin/ayuntamientos/preview/page.tsx');
const history = read('apps/web/src/app/admin/ayuntamientos/historial/page.tsx');
const gaps = read('apps/web/src/app/admin/ayuntamientos/huecos/page.tsx');
const roadmap = read('docs/V20_MUNICIPAL_COMPLETION_ROADMAP.md');
const qaDoc = read('docs/MUNICIPALITIES_QA_16.md');
const workflow = read('.github/workflows/v20-municipalities-directory.yml');

const expectedSlugs = [
  'albanchez-de-magina',
  'bedmar-y-garciez',
  'belmez-de-la-moraleda',
  'cabra-del-santo-cristo',
  'cambil',
  'campillo-de-arenas',
  'carcheles',
  'la-guardia-de-jaen',
  'huelma',
  'jimena',
  'jodar',
  'larva',
  'mancha-real',
  'noalejo',
  'pegalajar',
  'torres',
];

const catalogMatch = seo.match(/MUNICIPALITY_STATIC_CATALOG\s*=\s*\[([\s\S]*?)\]\s*as const/);
must(catalogMatch, 'No se ha encontrado MUNICIPALITY_STATIC_CATALOG');
const slugs = [...catalogMatch[1].matchAll(/slug:\s*'([^']+)'/g)].map((match) => match[1]);
must(slugs.length === 16, `Se esperaban 16 municipios y hay ${slugs.length}`);
must(new Set(slugs).size === 16, 'Hay slugs municipales duplicados');
must(expectedSlugs.every((slug) => slugs.includes(slug)), 'El catálogo municipal no contiene exactamente los 16 slugs canónicos');

must(publicPage.includes('generateStaticParams()'), 'Falta generateStaticParams municipal');
must(publicPage.includes('MUNICIPALITY_STATIC_CATALOG.map'), 'generateStaticParams no deriva del catálogo canónico');
must(publicPage.includes('generateMetadata'), 'Falta generateMetadata municipal');
must(publicPage.includes('municipalityMetadata(slug'), 'La metadata municipal no reutiliza el helper canónico');
must(seo.includes("robots:"), 'La metadata municipal no define robots');
must(seo.includes('openGraph:'), 'La metadata municipal no define Open Graph');
must(seo.includes('twitter:'), 'La metadata municipal no define Twitter Card');
must(!seo.includes('Math.random'), 'La metadata municipal no puede depender de aleatoriedad');

must(publicSource.includes('municipality_id'), 'La fuente pública municipal debe conservar municipality_id');
must(publicClient.includes('Lo imprescindible de'), 'Falta la sección pública de imprescindibles');
must(publicClient.includes('Patrimonio, naturaleza y lugares para descubrir'), 'Falta la sección pública de descubrimiento');
must(publicClient.includes('Ayuntamiento'), 'Falta la sección pública institucional');
must(publicClient.includes('Economía local'), 'Falta la sección pública de economía local');
must(publicClient.includes('Actualidad'), 'Falta la sección pública de actualidad');
must(publicClient.includes('No se ha podido cargar este ayuntamiento'), 'Falta estado de error/vacío de la ficha pública');

const expectedAdminSections = [
  ['ficha', '/admin/ayuntamientos'],
  ['contenido', '/admin/ayuntamientos/contenido'],
  ['portada', '/admin/ayuntamientos/editorial'],
  ['preview', '/admin/ayuntamientos/preview'],
  ['patrimonio', '/admin/ayuntamientos/patrimonio'],
  ['actualidad', '/admin/ayuntamientos/actualidad'],
  ['cobertura', '/admin/ayuntamientos/cobertura'],
  ['huecos', '/admin/ayuntamientos/huecos'],
  ['historial', '/admin/ayuntamientos/historial'],
];
for (const [key, route] of expectedAdminSections) {
  must(nav.includes(`key: '${key}'`), `Falta la sección Admin ${key}`);
  must(nav.includes(`path: '${route}'`), `Falta la ruta Admin ${route}`);
}
must(nav.includes("aria-current={active === section.key ? 'page'"), 'La navegación Admin debe exponer aria-current');
must(context.includes("searchParams.set('municipio'"), 'El contexto municipal debe persistir con ?municipio=');
must(context.includes("searchParams.get('municipio')"), 'El contexto municipal debe poder leerse desde ?municipio=');

for (const [name, source] of [['Preview', preview], ['Historial', history], ['Huecos', gaps]]) {
  must(!source.includes('adminApi.createContent('), `${name} no debe crear contenido`);
  must(!source.includes('adminApi.updateContent('), `${name} no debe modificar contenido`);
  must(!source.includes('adminApi.updateTerritoryPlace('), `${name} no debe modificar territorio`);
  must(!source.includes('fetch('), `${name} no debe saltarse adminApi con fetch directo`);
}
must(preview.includes('Resultado público actual'), 'Preview debe distinguir el resultado público real');
must(history.includes('adminApi.audit()'), 'Historial debe reutilizar adminApi.audit()');
must(!history.includes('JSON.stringify(entry.metadata'), 'Historial no debe renderizar metadata bruta');
must(gaps.includes('Solo huecos'), 'Huecos debe permitir filtrar señales ausentes');
must(gaps.includes("municipality_id"), 'Huecos debe vincular contenido por municipality_id');

const priorContracts = [
  'check-municipalities-directory.mjs',
  'check-municipality-discovery-experience.mjs',
  'check-municipality-public-landing.mjs',
  'check-municipality-explore-directory.mjs',
  'check-municipality-admin-control-center.mjs',
  'check-municipality-editorial-control.mjs',
  'check-municipality-content-editor.mjs',
  'check-municipality-admin-navigation.mjs',
  'check-municipality-seo-readiness.mjs',
  'check-municipality-editorial-preview.mjs',
  'check-municipality-audit-history.mjs',
  'check-municipality-gap-matrix.mjs',
];
for (const contract of priorContracts) must(workflow.includes(contract), `El workflow municipal ya no ejecuta ${contract}`);
must(workflow.includes('check-municipality-qa-16.mjs'), 'El workflow municipal no ejecuta el QA final 16/16');

for (const slug of expectedSlugs) must(qaDoc.includes(`\`${slug}\``), `La documentación QA no enumera ${slug}`);
must(roadmap.includes('Fase 5'), 'El roadmap municipal no conserva la Fase 5');
must(roadmap.includes('Fase 6'), 'El roadmap municipal no conserva la Fase 6');

console.log('Municipality QA 16/16 contract: OK');
