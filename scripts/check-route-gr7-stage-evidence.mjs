import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const stages = readJson('data/routes/sierra-magina-gr7-stage-track-sources.json');
const evidence = readJson('data/routes/sierra-magina-gr7-stage-validation-evidence.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');
const log = readJson('data/routes/sierra-magina-track-validation-log.json');

const errors = [];
const expectedStages = [8, 9, 10, 11, 12];
const stageByNumber = new Map((stages.stages ?? []).map((row) => [row.stage, row]));
const evidenceByStage = new Map((evidence.stages ?? []).map((row) => [row.stage, row]));
const gr7 = (homologated.routes ?? []).find((row) => row.code === 'GR-7');
const logRecord = (log.records ?? []).find((row) => row.route_ref === 'GR-7');

for (const stageNumber of expectedStages) {
  const row = stageByNumber.get(stageNumber);
  const proof = evidenceByStage.get(stageNumber);
  if (!row) errors.push(`Missing GR-7 stage source ${stageNumber}.`);
  if (!proof) errors.push(`Missing GR-7 durable evidence ${stageNumber}.`);
  if (!row || !proof) continue;
  if (row.track_found !== true || row.track_validated !== true) errors.push(`GR-7 stage ${stageNumber}: track_found and track_validated must be true.`);
  if (!row.archive_sha256 || !row.track_file_sha256 || !row.validated_at) errors.push(`GR-7 stage ${stageNumber}: source row lacks validation hashes/date.`);
  if (row.archive_sha256 !== proof.archive_sha256) errors.push(`GR-7 stage ${stageNumber}: archive SHA mismatch.`);
  if (row.track_file_sha256 !== proof.track_file_sha256) errors.push(`GR-7 stage ${stageNumber}: track-file SHA mismatch.`);
  if (row.calculated_length_m !== proof.calculated_length_m) errors.push(`GR-7 stage ${stageNumber}: calculated length mismatch.`);
  if (proof.status !== 'geometry_validated') errors.push(`GR-7 stage ${stageNumber}: durable evidence must be geometry_validated.`);
  const expected = Math.round(row.official_distance_km * 1000);
  const ratio = proof.calculated_length_m / expected;
  if (ratio < 0.75 || ratio > 1.25) errors.push(`GR-7 stage ${stageNumber}: geometry length ratio ${ratio.toFixed(4)} requires review.`);
}

if ((stageByNumber.size !== 5) || (evidenceByStage.size !== 5)) errors.push('GR-7 Sierra Magina validation must contain exactly stages 8-12.');
if (!gr7) errors.push('GR-7 homologated record is missing.');
if (gr7 && gr7.track_validated !== true) errors.push('GR-7 homologated record must be track_validated after stages 8-12 validate.');
if (gr7 && gr7.publishable === true) errors.push('GR-7 must not become publishable automatically from geometry validation.');
if (gr7 && (!gr7.maintenance_note || !String(gr7.maintenance_note).includes('1998'))) errors.push('GR-7 maintenance warning must retain the no-maintenance-since-1998 notice.');
if (!logRecord || logRecord.status !== 'geometry_validated') errors.push('GR-7 validation log must record geometry_validated status.');
if (!logRecord?.validation_method || !logRecord?.validated_at) errors.push('GR-7 validation log requires method and date.');

console.log('Sierra Mágina GR-7 stage evidence');
console.log(`- stages validated: ${evidenceByStage.size}/5`);
console.log(`- maintenance warning retained: ${Boolean(gr7?.maintenance_note)}`);
console.log(`- publishable: ${Boolean(gr7?.publishable)}`);

if (errors.length) {
  console.error('\nGR-7 stage evidence check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nGR-7 stages 8-12 are evidence-backed and safety warnings remain independent.');
