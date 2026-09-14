import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const evidence = readJson('data/routes/sierra-magina-external-kmz-validation-evidence.json');
const log = readJson('data/routes/sierra-magina-track-validation-log.json');
const intermodal = readJson('data/routes/sierra-magina-intermodal-hiking.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');

const errors = [];
const evidenceByRef = new Map((evidence.routes ?? []).map((row) => [row.route_ref, row]));
const logByRef = new Map((log.records ?? []).map((row) => [row.route_ref, row]));
const intermodalByCode = new Map((intermodal.routes ?? []).map((row) => [row.code, row]));
const homologatedByCode = new Map((homologated.routes ?? []).map((row) => [row.code, row]));

for (const [ref, row] of evidenceByRef) {
  if (!row.sha256 || !row.point_count || !row.calculated_length_m || !row.status) {
    errors.push(`${ref}: durable KMZ evidence requires sha256, point_count, calculated_length_m and status.`);
    continue;
  }
  const auditRecord = logByRef.get(ref);
  if (!auditRecord) {
    errors.push(`${ref}: durable evidence has no track validation log record.`);
    continue;
  }

  if (row.status === 'geometry_validated') {
    if (auditRecord.status !== 'geometry_validated') errors.push(`${ref}: durable evidence is validated but validation log is ${auditRecord.status}.`);
    if (!auditRecord.validated_at || !auditRecord.validation_method) errors.push(`${ref}: validated evidence requires dated validation method in log.`);
    if (auditRecord.evidence?.sha256 !== row.sha256) errors.push(`${ref}: SHA-256 differs between durable evidence and validation log.`);
  }

  if (row.status === 'manual_review_required' && auditRecord.status === 'geometry_validated') {
    errors.push(`${ref}: manual-review evidence cannot be geometry_validated in log.`);
  }

  const route = intermodalByCode.get(ref) ?? homologatedByCode.get(ref);
  if (route) {
    const shouldBeValidated = row.status === 'geometry_validated';
    if (Boolean(route.track_validated) !== shouldBeValidated) {
      errors.push(`${ref}: route track_validated=${Boolean(route.track_validated)} but durable evidence status=${row.status}.`);
    }
  }
}

const expectedRefs = ['R1','R2','R3','R6','R7','R9','PR-A 350','SL-A 135'];
for (const ref of expectedRefs) {
  if (!evidenceByRef.has(ref)) errors.push(`Missing durable KMZ evidence for ${ref}.`);
}

const validated = [...evidenceByRef.values()].filter((row) => row.status === 'geometry_validated').length;
const manual = [...evidenceByRef.values()].filter((row) => row.status === 'manual_review_required').length;
console.log('Sierra Mágina external KMZ durable evidence');
console.log(`- evidence records: ${evidenceByRef.size}`);
console.log(`- geometry validated: ${validated}`);
console.log(`- manual review required: ${manual}`);

if (errors.length) {
  console.error('\nExternal KMZ evidence check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nExternal KMZ evidence is internally consistent.');
