import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');
const log = readJson('data/routes/sierra-magina-track-validation-log.json');
const equivalences = readJson('data/routes/sierra-magina-canonical-track-equivalences.json');

const errors = [];
const core = new Map((catalog.routes ?? []).filter((route) => route.layer === 'official_core').map((route) => [route.slug, route]));
const homologatedByCode = new Map((homologated.routes ?? []).map((route) => [route.code, route]));
const logByRef = new Map((log.records ?? []).map((record) => [record.route_ref, record]));
const seenCanonical = new Set();

for (const row of equivalences.equivalences ?? []) {
  if (!row.canonical_route_slug || !row.external_route_ref || !row.authority || !row.status || !row.validated_at || !row.sha256) {
    errors.push('Every canonical equivalence requires canonical_route_slug, external_route_ref, authority, status, validated_at and sha256.');
    continue;
  }
  if (seenCanonical.has(row.canonical_route_slug)) errors.push(`Duplicate canonical equivalence: ${row.canonical_route_slug}.`);
  seenCanonical.add(row.canonical_route_slug);

  const canonical = core.get(row.canonical_route_slug);
  if (!canonical) errors.push(`${row.canonical_route_slug}: canonical equivalence must reference an official_core route.`);

  const external = homologatedByCode.get(row.external_route_ref);
  if (!external) {
    errors.push(`${row.external_route_ref}: canonical equivalence must reference a homologated route.`);
  } else {
    if (external.canonical_route_slug !== row.canonical_route_slug) {
      errors.push(`${row.external_route_ref}: canonical_route_slug mismatch (${external.canonical_route_slug} vs ${row.canonical_route_slug}).`);
    }
    if (external.track_validated !== true) errors.push(`${row.external_route_ref}: external source geometry must be validated before canonical equivalence.`);
  }

  const externalEvidence = logByRef.get(row.external_route_ref);
  if (!externalEvidence || externalEvidence.status !== 'geometry_validated') {
    errors.push(`${row.external_route_ref}: external geometry validation evidence is missing.`);
  }
  const canonicalEvidence = logByRef.get(row.canonical_route_slug);
  if (!canonicalEvidence || canonicalEvidence.status !== 'geometry_validated') {
    errors.push(`${row.canonical_route_slug}: canonical geometry validation evidence is missing.`);
  }
  if (canonicalEvidence?.evidence?.sha256 && canonicalEvidence.evidence.sha256 !== row.sha256) {
    errors.push(`${row.canonical_route_slug}: canonical evidence SHA-256 differs from equivalence record.`);
  }
  if (externalEvidence?.evidence?.sha256 && externalEvidence.evidence.sha256 !== row.sha256) {
    errors.push(`${row.external_route_ref}: external evidence SHA-256 differs from equivalence record.`);
  }
}

console.log('Sierra Mágina canonical track equivalences');
console.log(`- validated canonical equivalences: ${(equivalences.equivalences ?? []).length}`);

if (errors.length) {
  console.error('\nCanonical track equivalence check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nCanonical external track equivalences are explicit and evidence-backed.');
