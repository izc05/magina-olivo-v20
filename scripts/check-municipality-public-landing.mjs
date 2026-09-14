import fs from 'node:fs';

const detail = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/[slug]/municipality-detail-client.tsx', import.meta.url), 'utf8');
const css = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/municipalities.module.css', import.meta.url), 'utf8');

const requiredDetailSnippets = [
  "const essentialDiscoveries = useMemo(() => {",
  "discoveries.filter((entry) => entry.featured).forEach(add)",
  "(['heritage', 'nature', 'tourism'] as DiscoveryRole[])",
  "return picks;",
  "const heroMedia = profile?.media_url || discoveries.find((entry) => entry.media_url)?.media_url || null;",
  "Lo imprescindible de {item.name}",
  "id=\"imprescindibles\"",
  "href=\"#descubrir\"",
  "setDiscoveryFilter(role ?? 'all')",
  "setDiscoveryFilter('heritage')",
  "setDiscoveryFilter('nature')",
  "setDiscoveryFilter('tourism')",
  "discoveryCounts.heritage",
  "discoveryCounts.nature",
  "discoveryCounts.tourism",
  "entry.media_url ?",
  "String(index + 1).padStart(2, '0')",
  "Turismo oficial ↗",
];

for (const snippet of requiredDetailSnippets) {
  if (!detail.includes(snippet)) throw new Error(`Municipality public landing is missing: ${snippet}`);
}

if (detail.includes('Math.random(')) throw new Error('Municipality essentials must be deterministic, not random');
if (detail.includes('fetch(')) throw new Error('Municipality landing must reuse the existing public territory source, not add arbitrary fetches');
if (!detail.includes('loadPublicMunicipality(slug)')) throw new Error('Municipality landing must reuse the canonical public municipality load');
if (!detail.includes('const discoveries = useMemo(() => places.filter((entry) => discoveryRole(entry))')) throw new Error('Essentials must derive from explicitly classified published municipality places');

for (const cssClass of [
  '.detailHeroWithMedia',
  '.essentialsSection',
  '.essentialsGrid',
  '.essentialCard',
  '.essentialFallback',
  '.discoveryOverview',
]) {
  if (!css.includes(cssClass)) throw new Error(`Municipality landing styles are missing: ${cssClass}`);
}

if (!css.includes('@media(max-width:900px)') || !css.includes('@media(max-width:620px)')) {
  throw new Error('Municipality landing must keep tablet and mobile responsive rules');
}
if (!css.includes('.essentialCard:focus-visible')) throw new Error('Municipality essentials must preserve visible keyboard focus');

console.log('Municipality public landing contract OK: deterministic essentials, editorial media reuse, category overview, real discovery filtering and responsive accessibility.');
