import fs from 'node:fs';

const detailPage = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/[slug]/page.tsx', import.meta.url), 'utf8');
const seoHelper = fs.readFileSync(new URL('../apps/web/src/lib/municipality-seo.ts', import.meta.url), 'utf8');
const coverage = fs.readFileSync(new URL('../apps/web/src/app/admin/ayuntamientos/cobertura/page.tsx', import.meta.url), 'utf8');

if (!detailPage.includes('generateMetadata')) throw new Error('Municipality detail must generate per-municipality metadata');
if (!detailPage.includes('loadPublicMunicipality')) throw new Error('Municipality metadata must prefer the public canonical municipality payload');
if (!detailPage.includes('MUNICIPALITY_STATIC_CATALOG')) throw new Error('Static export slugs must come from the shared municipality SEO catalog');
if (!detailPage.includes('catch')) throw new Error('Municipality metadata must have a build-safe fallback when the public API is unavailable');

for (const slug of ['albanchez-de-magina','bedmar-y-garciez','belmez-de-la-moraleda','cabra-del-santo-cristo','cambil','campillo-de-arenas','carcheles','la-guardia-de-jaen','huelma','jimena','jodar','larva','mancha-real','noalejo','pegalajar','torres']) {
  if (!seoHelper.includes(`slug: '${slug}'`)) throw new Error(`Missing static municipality SEO identity: ${slug}`);
}
if (!seoHelper.includes("municipality_role") || !seoHelper.includes("=== 'profile'")) throw new Error('SEO helper must prefer the explicitly classified municipality profile');
if (!seoHelper.includes('profile?.summary')) throw new Error('SEO description must prefer the real profile summary');
if (!seoHelper.includes('profile?.media_url')) throw new Error('Social image must prefer real profile media');
if (!seoHelper.includes("parsed.protocol !== 'https:'") || !seoHelper.includes("parsed.protocol !== 'http:'")) throw new Error('Social images must be restricted to absolute HTTP(S) URLs');
if (!seoHelper.includes('openGraph') || !seoHelper.includes('twitter')) throw new Error('Municipality metadata must expose Open Graph and Twitter metadata');
if (!seoHelper.includes('robots')) throw new Error('Municipality metadata must define indexing behavior for known slugs');
if (seoHelper.includes('Math.random')) throw new Error('Municipality metadata must be deterministic');

for (const signal of ['Perfil principal publicado', 'Resumen para buscadores y portada', 'Imagen real para portada y compartir', 'Fuente y verificación del perfil', 'Al menos un descubrimiento publicado']) {
  if (!coverage.includes(signal)) throw new Error(`Coverage is missing editorial readiness signal: ${signal}`);
}
if (!coverage.includes('editorialReadiness')) throw new Error('Coverage must compute editorial readiness separately from the existing 10 coverage checks');
if (!coverage.includes('readinessCompleted === 5')) throw new Error('Coverage summary must expose municipalities that satisfy all five readiness signals');
if (!coverage.includes('row.completed === row.checks.length && row.readinessCompleted === row.readiness.length')) throw new Error('Incomplete filter must consider both coverage and editorial readiness');
if (!coverage.includes("row.completed}/10 cobertura")) throw new Error('Existing 10-signal coverage contract must remain visible');

console.log('Municipality SEO/readiness contract OK: 16 static identities, canonical metadata enrichment, deterministic fallback, real social media only and separate 5-signal editorial readiness.');
