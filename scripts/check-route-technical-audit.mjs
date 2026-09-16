import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));
const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const audit = readJson('data/routes/sierra-magina-official-core-technical-audit.json');

const errors = [];
const officialCore = (catalog.routes ?? []).filter((route) => route.layer === 'official_core');
const rows = Array.isArray(audit.routes) ? audit.routes : [];
const expected = audit.expected_routes ?? 17;

if (rows.length !== expected) errors.push(`Expected ${expected} official technical audit rows, found ${rows.length}.`);

const slugs = new Set();
for (const row of rows) {
  if (!row.slug || !row.status || !row.source_status) {
    errors.push('Every official technical audit row requires slug, status and source_status.');
    continue;
  }
  if (slugs.has(row.slug)) errors.push(`Duplicate technical audit slug: ${row.slug}.`);
  slugs.add(row.slug);
  if (!Array.isArray(row.verified_fields) || row.verified_fields.length === 0) {
    errors.push(`${row.slug}: verified_fields must be a non-empty array.`);
  }
}

for (const route of officialCore) {
  if (!slugs.has(route.slug)) errors.push(`Missing official technical audit row: ${route.slug}.`);
}
for (const slug of slugs) {
  if (!officialCore.some((route) => route.slug === slug)) errors.push(`Technical audit contains non-core slug: ${slug}.`);
}

const complete = rows.filter((row) => row.status === 'verified_technical_sheet' || row.status === 'verified_core_fields').length;
const partial = rows.filter((row) => row.status === 'partial_technical_fields').length;
const pending = rows.filter((row) => row.status === 'pending_technical_fields').length;

console.log('Sierra Mágina official core technical audit');
console.log(`- audit rows: ${rows.length}/${expected}`);
console.log(`- verified/core-complete: ${complete}`);
console.log(`- partial: ${partial}`);
console.log(`- pending technical fields: ${pending}`);

if (errors.length) {
  console.error('\nOfficial technical audit check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nOfficial technical audit matrix is structurally complete.');
