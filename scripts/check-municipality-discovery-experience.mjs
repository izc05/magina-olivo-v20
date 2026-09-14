import fs from 'node:fs';

const detail = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/[slug]/municipality-detail-client.tsx', import.meta.url), 'utf8');
const styles = fs.readFileSync(new URL('../apps/web/src/app/ayuntamientos/municipalities.module.css', import.meta.url), 'utf8');

for (const role of ['heritage', 'nature', 'tourism']) {
  if (!detail.includes(`'${role}'`)) throw new Error(`Missing public discovery role: ${role}`);
}

for (const label of ['Todo', 'Patrimonio', 'Naturaleza', 'Turismo']) {
  if (!detail.includes(`label: '${label}'`)) throw new Error(`Missing public discovery filter: ${label}`);
}

if (!detail.includes("type DiscoveryFilter = 'all' | DiscoveryRole")) throw new Error('Discovery filter type is missing');
if (!detail.includes('setDiscoveryFilter')) throw new Error('Discovery filters are not interactive');
if (!detail.includes('aria-pressed={discoveryFilter === filter.value}')) throw new Error('Discovery filters must expose aria-pressed');
if (!detail.includes('discoveryCounts')) throw new Error('Discovery category counters are missing');
if (!detail.includes('filteredDiscoveries')) throw new Error('Filtered discovery list is missing');
if (!detail.includes('Mostrando <strong>{filteredDiscoveries.length}</strong>')) throw new Error('Discovery live summary is missing');
if (!detail.includes('aria-live="polite"')) throw new Error('Discovery summary must be announced accessibly');
if (!detail.includes('Sin recursos en esta categoría')) throw new Error('Per-filter empty state is missing');

for (const field of ['source_url', 'source_label', 'verified_at']) {
  if (!detail.includes(field)) throw new Error(`Public discovery cards must expose provenance field: ${field}`);
}
if (!detail.includes('Fuente verificada')) throw new Error('Visible provenance label is missing');
if (!detail.includes('Verificada {sourceVerified}')) throw new Error('Visible source verification date is missing');

for (const className of ['discoveryToolbar', 'discoveryFilter', 'discoveryFilterActive', 'discoverySummary', 'provenance']) {
  if (!styles.includes(`.${className}`)) throw new Error(`Missing discovery experience style: ${className}`);
}
if (!styles.includes(':focus-visible')) throw new Error('Discovery filter keyboard focus style is missing');
if (!styles.includes('grid-template-columns:repeat(2,minmax(0,1fr))')) throw new Error('Mobile discovery filter layout is missing');

console.log('Municipality discovery public experience contract OK: filters, counters, accessible state, provenance and responsive layout.');
