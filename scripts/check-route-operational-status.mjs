import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const statuses = readJson('data/routes/sierra-magina-operational-status.json');

const errors = [];
const allowed = new Set(statuses?.policy?.status_values ?? []);
const routes = Array.isArray(catalog.routes) ? catalog.routes : [];
const knownSlugs = new Set(routes.map((route) => route.slug));
const seen = new Set();

for (const row of statuses.routes ?? []) {
  if (!row.slug || !row.status || !row.authority || !row.source_url || !row.verified_at) {
    errors.push('Every operational status row requires slug, status, authority, source_url and verified_at.');
    continue;
  }
  if (!knownSlugs.has(row.slug)) errors.push(`Operational status references unknown route: ${row.slug}.`);
  if (!allowed.has(row.status)) errors.push(`${row.slug}: unsupported operational status ${row.status}.`);
  if (seen.has(row.slug)) errors.push(`Duplicate operational status row: ${row.slug}.`);
  seen.add(row.slug);
}

for (const route of routes) {
  if (route.operational_status === 'temporarily_closed') {
    const matching = (statuses.routes ?? []).find((row) => row.slug === route.slug && row.status === 'temporarily_closed');
    if (!matching) errors.push(`${route.slug}: master catalog says temporarily_closed but authoritative operational registry has no matching closure.`);
  }
}

const closed = (statuses.routes ?? []).filter((row) => row.status === 'temporarily_closed').map((row) => row.slug);
console.log('Sierra Mágina operational route status');
console.log(`- authoritative status rows: ${(statuses.routes ?? []).length}`);
console.log(`- temporarily closed: ${closed.length}${closed.length ? ` (${closed.join(', ')})` : ''}`);
console.log('- absence from registry does not imply open');

if (errors.length) {
  console.error('\nOperational route status validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nOperational status structure is valid.');
