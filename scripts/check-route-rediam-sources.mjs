import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const rediam = readJson('data/routes/sierra-magina-rediam-track-sources.json');

const errors = [];
const coreSlugs = new Set((catalog.routes ?? []).filter((route) => route.layer === 'official_core').map((route) => route.slug));
const codes = new Set();
const slugs = new Set();

if (rediam.service !== 'REDIAM_WFS_Patrimonio_Natural') errors.push('Unexpected REDIAM WFS service.');
if (rediam.typename !== 'senderos:senderos') errors.push('Unexpected REDIAM typename.');
if (rediam.srsname !== 'EPSG:25830') errors.push('Unexpected REDIAM source CRS.');

for (const row of rediam.routes ?? []) {
  if (!row.slug || !row.codigoequi || !row.source_page || !Array.isArray(row.formats_exposed)) {
    errors.push('Every REDIAM row requires slug, codigoequi, source_page and formats_exposed.');
    continue;
  }
  if (!coreSlugs.has(row.slug)) errors.push(`REDIAM source references non-core route: ${row.slug}.`);
  if (codes.has(row.codigoequi)) errors.push(`Duplicate REDIAM CODIGOEQUI: ${row.codigoequi}.`);
  if (slugs.has(row.slug)) errors.push(`Duplicate REDIAM route slug: ${row.slug}.`);
  codes.add(row.codigoequi);
  slugs.add(row.slug);
  if (row.track_found !== true) errors.push(`${row.slug}: REDIAM source row must represent a located track source.`);
  if (row.track_validated !== false) errors.push(`${row.slug}: REDIAM discovery registry must not assert geometry validation.`);
  if (!row.formats_exposed.some((format) => ['KML', 'GML', 'GPX'].includes(format))) {
    errors.push(`${row.slug}: no supported official geometry format recorded.`);
  }
}

console.log('Sierra Mágina REDIAM track source registry');
console.log(`- official core routes with REDIAM identifier: ${slugs.size}/17`);
console.log(`- unique CODIGOEQUI values: ${codes.size}`);
console.log('- geometry validation remains a separate gate');

if (errors.length) {
  console.error('\nREDIAM track source validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nREDIAM track source registry is valid.');
