import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const intermodal = readJson('data/routes/sierra-magina-intermodal-hiking.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');
const validationLog = readJson('data/routes/sierra-magina-track-validation-log.json');

const errors = [];
const records = Array.isArray(validationLog.records) ? validationLog.records : [];
const byRef = new Map();

for (const record of records) {
  const ref = String(record.route_ref ?? '').trim();
  if (!ref || !record.status || !record.source) {
    errors.push('Every track validation record requires route_ref, source and status.');
    continue;
  }
  if (byRef.has(ref)) errors.push(`Duplicate track validation record: ${ref}.`);
  byRef.set(ref, record);
}

const assertValidatedEvidence = (ref, route) => {
  if (!route.track_validated) return;
  const record = byRef.get(ref);
  if (!record) {
    errors.push(`${ref}: track_validated=true requires a track validation log record.`);
    return;
  }
  if (record.status !== 'geometry_validated') {
    errors.push(`${ref}: track_validated=true requires validation log status=geometry_validated.`);
  }
  if (!record.validated_at || !record.validation_method) {
    errors.push(`${ref}: validated geometry requires validated_at and validation_method evidence.`);
  }
};

for (const route of catalog.routes ?? []) assertValidatedEvidence(route.slug, route);
for (const route of intermodal.routes ?? []) assertValidatedEvidence(route.code, route);
for (const route of homologated.routes ?? []) assertValidatedEvidence(route.code, route);

for (const route of intermodal.routes ?? []) {
  const record = byRef.get(route.code);
  if (!record) errors.push(`${route.code}: every R1-R9 route requires an explicit track audit record.`);
  if (route.track_found && !route.track_url) errors.push(`${route.code}: track_found=true requires track_url.`);
  if (!route.track_found && route.track_url) errors.push(`${route.code}: track_url is present while track_found=false.`);
  if (record && record.status === 'geometry_validated' && !route.track_validated) {
    errors.push(`${route.code}: validation log says geometry_validated but route track_validated is false.`);
  }
}

for (const route of homologated.routes ?? []) {
  if (route.track_validated) assertValidatedEvidence(route.code, route);
}

console.log('Sierra Mágina track validation evidence');
console.log(`- audit records: ${records.length}`);
console.log(`- geometry validated: ${records.filter((record) => record.status === 'geometry_validated').length}`);
console.log(`- linked/pending inspection: ${records.filter((record) => record.status === 'linked_not_geometry_validated').length}`);
console.log(`- official tracks not located: ${records.filter((record) => record.status === 'official_track_not_located').length}`);

if (errors.length) {
  console.error('\nTrack validation evidence check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nTrack validation evidence is internally consistent.');
