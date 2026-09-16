import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const rediam = readJson('data/routes/sierra-magina-rediam-track-sources.json');
const queue = readJson('data/routes/sierra-magina-geometry-validation-queue.json');

const errors = [];
const core = new Set((catalog.routes ?? []).filter((route) => route.layer === 'official_core').map((route) => route.slug));
const rediamBySlug = new Map((rediam.routes ?? []).map((route) => [route.slug, route]));
const queueRows = queue?.rediam?.queue ?? [];
const pending = queue?.rediam?.pending_source_identification ?? [];

if (queue?.rediam?.expected_core_routes !== core.size) {
  errors.push(`Geometry queue expected_core_routes=${queue?.rediam?.expected_core_routes} but master core has ${core.size}.`);
}
if (queue?.rediam?.sources_located !== rediamBySlug.size) {
  errors.push(`Geometry queue sources_located=${queue?.rediam?.sources_located} but REDIAM registry has ${rediamBySlug.size}.`);
}
if (queueRows.length !== rediamBySlug.size) {
  errors.push(`Geometry queue has ${queueRows.length} rows but REDIAM registry has ${rediamBySlug.size}.`);
}
if (rediamBySlug.size + pending.length !== core.size) {
  errors.push(`REDIAM located (${rediamBySlug.size}) + pending (${pending.length}) must equal official core (${core.size}).`);
}

const seen = new Set();
for (const row of queueRows) {
  if (!row.slug || !row.codigoequi || !row.status || !row.next) {
    errors.push('Every geometry queue row requires slug, codigoequi, status and next.');
    continue;
  }
  if (seen.has(row.slug)) errors.push(`Duplicate geometry queue slug: ${row.slug}.`);
  seen.add(row.slug);
  if (!core.has(row.slug)) errors.push(`Geometry queue references non-core route: ${row.slug}.`);
  const source = rediamBySlug.get(row.slug);
  if (!source) {
    errors.push(`Geometry queue route missing from REDIAM registry: ${row.slug}.`);
    continue;
  }
  if (String(source.codigoequi) !== String(row.codigoequi)) {
    errors.push(`${row.slug}: queue CODIGOEQUI ${row.codigoequi} differs from REDIAM ${source.codigoequi}.`);
  }
  if (row.status === 'geometry_validated' && source.track_validated !== true) {
    errors.push(`${row.slug}: queue cannot be geometry_validated while REDIAM track_validated is false.`);
  }
}

for (const slug of pending) {
  if (!core.has(slug)) errors.push(`Pending REDIAM source is not an official-core route: ${slug}.`);
  if (rediamBySlug.has(slug)) errors.push(`Pending REDIAM source ${slug} is already present in the registry.`);
}

const duplicateCodes = new Set();
const codes = new Set();
for (const source of rediam.routes ?? []) {
  const code = String(source.codigoequi ?? '');
  if (!code) errors.push(`${source.slug}: REDIAM source requires codigoequi.`);
  if (codes.has(code)) duplicateCodes.add(code);
  codes.add(code);
}
for (const code of duplicateCodes) errors.push(`Duplicate REDIAM CODIGOEQUI: ${code}.`);

if (errors.length) {
  console.error('Geometry validation queue check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(`Geometry validation queue is consistent: ${rediamBySlug.size}/${core.size} REDIAM sources located, ${pending.length} pending, ${queue?.rediam?.geometry_validated ?? 0} validated.`);
