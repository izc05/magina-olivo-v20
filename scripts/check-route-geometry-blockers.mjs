import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const blockers = readJson('data/routes/sierra-magina-geometry-blockers.json');
const closure = readJson('data/routes/sierra-magina-catalog-closure.json');

const errors = [];
const rows = Array.isArray(blockers.blockers) ? blockers.blockers : [];
const requiredIds = new Set([
  'junta-rediam-wfs',
  'mancha-real-campusfortalezas-tls',
  'diputacion-r4-r5-r8-no-public-artifact',
  'municipal-comarca-no-public-artifact',
]);

const ids = new Set();
for (const row of rows) {
  if (!row.id || !row.authority || !row.affected_scope || !row.geometry_state || !row.blocker || !row.required_next_action) {
    errors.push('Every geometry blocker row requires id, authority, affected_scope, geometry_state, blocker and required_next_action.');
    continue;
  }
  if (ids.has(row.id)) errors.push(`Duplicate geometry blocker id: ${row.id}.`);
  ids.add(row.id);
  if (!row.unsafe_workaround_forbidden) errors.push(`${row.id}: unsafe workaround prohibition is required.`);
  if (!Array.isArray(row.evidence) || row.evidence.length === 0) errors.push(`${row.id}: at least one durable evidence file is required.`);
  for (const evidencePath of row.evidence ?? []) {
    if (!fs.existsSync(path.resolve(evidencePath))) errors.push(`${row.id}: evidence file does not exist: ${evidencePath}.`);
  }
}

for (const id of requiredIds) {
  if (!ids.has(id)) errors.push(`Missing required geometry blocker: ${id}.`);
}
if (rows.length !== requiredIds.size) errors.push(`Expected exactly ${requiredIds.size} current blocker groups, found ${rows.length}.`);

if (blockers?.closure?.public_source_discovery_complete_for_current_scope !== true) {
  errors.push('Geometry blocker matrix must preserve public-source discovery closure for the current scope.');
}
if (blockers?.closure?.all_geometry_validated !== false) {
  errors.push('Geometry blocker matrix cannot claim all geometry is validated while blocker groups remain active.');
}
if (blockers?.closure?.publication_gate_must_remain_open !== true) {
  errors.push('Geometry blocker matrix must keep the publication gate open while blockers remain active.');
}
if (closure?.publication_gate?.complete !== false || closure?.publication_gate?.status !== 'open') {
  errors.push('Catalog publication gate cannot close while authoritative geometry blockers remain active.');
}

const rediam = rows.find((row) => row.id === 'junta-rediam-wfs');
if (rediam && !String(rediam.affected_scope).includes('17')) errors.push('REDIAM blocker must continue to represent the 17-route official core scope.');
const mancha = rows.find((row) => row.id === 'mancha-real-campusfortalezas-tls');
if (mancha && !String(mancha.affected_scope).includes('3')) errors.push('Mancha Real TLS blocker must continue to represent the three located municipal KML routes.');

console.log('Sierra Mágina authoritative geometry blockers');
console.log(`- active blocker groups: ${rows.length}`);
for (const row of rows) console.log(`- ${row.id}: ${row.geometry_state}`);
console.log(`- public-source discovery closed: ${blockers.closure.public_source_discovery_complete_for_current_scope}`);
console.log(`- publication gate open: ${closure.publication_gate.status === 'open' && closure.publication_gate.complete === false}`);

if (errors.length) {
  console.error('\nGeometry blocker validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nGeometry blockers are explicit, evidence-backed, and the publication gate remains correctly open.');
