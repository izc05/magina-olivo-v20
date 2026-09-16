import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const municipalityAudit = readJson('data/routes/sierra-magina-municipality-audit.json');
const intermodal = readJson('data/routes/sierra-magina-intermodal-hiking.json');
const intermodalMissingTrackAudit = readJson('data/routes/sierra-magina-intermodal-missing-track-audit.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');
const sourceRegistry = readJson('data/routes/sierra-magina-official-source-registry.json');
const closure = readJson('data/routes/sierra-magina-catalog-closure.json');

const errors = [];
const warnings = [];

const requiredMunicipalityCount = catalog?.completeness_policy?.municipalities_required ?? 16;
const requiredOfficialCoreCount = catalog?.completeness_policy?.official_core_minimum ?? 17;
const municipalities = Array.isArray(catalog.municipalities) ? catalog.municipalities : [];
const auditedMunicipalities = Array.isArray(municipalityAudit.municipalities) ? municipalityAudit.municipalities : [];
const routes = Array.isArray(catalog.routes) ? catalog.routes : [];
const intermodalRoutes = Array.isArray(intermodal.routes) ? intermodal.routes : [];
const missingIntermodalTrackRows = Array.isArray(intermodalMissingTrackAudit.routes) ? intermodalMissingTrackAudit.routes : [];
const homologatedRoutes = Array.isArray(homologated.routes) ? homologated.routes : [];
const officialSources = Array.isArray(sourceRegistry.sources) ? sourceRegistry.sources : [];

if (municipalities.length !== requiredMunicipalityCount) {
  errors.push(`Expected ${requiredMunicipalityCount} municipalities, found ${municipalities.length}.`);
}

const normalizedMunicipalities = new Set(municipalities.map((name) => name.trim().toLowerCase()));
if (normalizedMunicipalities.size !== municipalities.length) {
  errors.push('Municipality catalog contains duplicates.');
}

const checkMunicipalityReferences = (label, names) => {
  if (!Array.isArray(names) || names.length === 0) {
    errors.push(`${label}: at least one municipality is required.`);
    return;
  }
  for (const municipality of names) {
    if (!normalizedMunicipalities.has(String(municipality).trim().toLowerCase())) {
      errors.push(`${label}: unknown municipality "${municipality}".`);
    }
  }
};

if (auditedMunicipalities.length !== requiredMunicipalityCount) {
  errors.push(`Expected ${requiredMunicipalityCount} municipality audit rows, found ${auditedMunicipalities.length}.`);
}

const auditedNames = new Set();
for (const row of auditedMunicipalities) {
  const normalizedName = String(row.name ?? '').trim().toLowerCase();
  if (!normalizedName || !row.status || !row.source_url) {
    errors.push('Every municipality audit row requires name, status and source_url.');
    continue;
  }
  if (!normalizedMunicipalities.has(normalizedName)) {
    errors.push(`Municipality audit contains unknown municipality "${row.name}".`);
  }
  if (auditedNames.has(normalizedName)) {
    errors.push(`Municipality audit contains duplicate municipality "${row.name}".`);
  }
  auditedNames.add(normalizedName);
}

for (const municipality of municipalities) {
  if (!auditedNames.has(municipality.trim().toLowerCase())) {
    errors.push(`Municipality has not been audited: ${municipality}.`);
  }
}

const sourceCoveredMunicipalities = new Set();
for (const source of officialSources) {
  if (!source.authority || !source.authority_kind || !source.source_url || !source.finding) {
    errors.push('Every official source registry row requires authority, authority_kind, source_url and finding.');
    continue;
  }
  checkMunicipalityReferences(`source:${source.authority}`, source.municipalities);
  for (const municipality of source.municipalities ?? []) {
    sourceCoveredMunicipalities.add(String(municipality).trim().toLowerCase());
  }
}
for (const municipality of municipalities) {
  if (!sourceCoveredMunicipalities.has(municipality.trim().toLowerCase())) {
    errors.push(`Municipality lacks an auditable official/institutional source: ${municipality}.`);
  }
}

const slugs = new Set();
for (const route of routes) {
  if (!route.slug || !route.name) {
    errors.push('Every route must have slug and name.');
    continue;
  }

  if (slugs.has(route.slug)) errors.push(`Duplicate route slug: ${route.slug}`);
  slugs.add(route.slug);

  if (!route.source_name || !route.source_url || !route.source_status) {
    errors.push(`${route.slug}: source_name, source_url and source_status are required.`);
  }

  checkMunicipalityReferences(route.slug, route.municipalities);

  if (route.publishable && !route.track_validated) {
    errors.push(`${route.slug}: publishable routes require track_validated=true.`);
  }
  if (route.track_validated && !route.track_found) {
    errors.push(`${route.slug}: validated track requires track_found=true.`);
  }
  if (route.layer === 'official_core' && route.source_status !== 'official') {
    errors.push(`${route.slug}: official_core route must use an official source.`);
  }

  if (!route.documented) warnings.push(`${route.slug}: technical documentation is still incomplete.`);
  if (!route.track_validated) warnings.push(`${route.slug}: real track is not yet validated.`);
}

const officialCore = routes.filter((route) => route.layer === 'official_core');
if (officialCore.length < requiredOfficialCoreCount) {
  errors.push(`Official Parque Natural baseline is incomplete: expected at least ${requiredOfficialCoreCount} routes, found ${officialCore.length}.`);
}

const expectedIntermodal = intermodal.expected_routes ?? 9;
if (intermodalRoutes.length !== expectedIntermodal) {
  errors.push(`Expected ${expectedIntermodal} official intermodal hiking routes, found ${intermodalRoutes.length}.`);
}
const intermodalCodes = new Set();
for (const route of intermodalRoutes) {
  if (!route.code || !route.slug || !route.name || !route.source_url) {
    errors.push('Every intermodal route requires code, slug, name and source_url.');
    continue;
  }
  if (intermodalCodes.has(route.code)) errors.push(`Duplicate intermodal route code: ${route.code}.`);
  intermodalCodes.add(route.code);
  checkMunicipalityReferences(route.code, route.municipalities);
  if (route.track_validated && !route.track_found) errors.push(`${route.code}: validated track requires track_found=true.`);
}
for (let index = 1; index <= expectedIntermodal; index += 1) {
  if (!intermodalCodes.has(`R${index}`)) errors.push(`Missing intermodal hiking route R${index}.`);
}

const expectedMissingTrackCodes = ['R4', 'R5', 'R8'];
const missingTrackAuditCodes = new Set(missingIntermodalTrackRows.map((row) => row.code));
if (missingIntermodalTrackRows.length !== expectedMissingTrackCodes.length) {
  errors.push(`Expected ${expectedMissingTrackCodes.length} audited intermodal missing-track rows, found ${missingIntermodalTrackRows.length}.`);
}
for (const code of expectedMissingTrackCodes) {
  const auditRow = missingIntermodalTrackRows.find((row) => row.code === code);
  const route = intermodalRoutes.find((row) => row.code === code);
  if (!auditRow) {
    errors.push(`${code}: missing authoritative-track absence audit row.`);
    continue;
  }
  if (!route) {
    errors.push(`${code}: audited missing-track route is absent from intermodal catalog.`);
    continue;
  }
  if (auditRow.authoritative_track_status !== 'not_located' || auditRow.track_artifact_url !== null) {
    errors.push(`${code}: missing-track audit must remain not_located with a null artifact URL until an authoritative track is actually found.`);
  }
  if (!auditRow.source_url || !auditRow.published_document_url || !auditRow.audit_note) {
    errors.push(`${code}: missing-track audit requires source_url, published_document_url and audit_note.`);
  }
  if (auditRow.source_url !== route.source_url) {
    errors.push(`${code}: missing-track audit source URL is out of sync with the intermodal catalog.`);
  }
  if (route.track_found || route.track_validated || route.track_validation_state !== 'official_track_not_located') {
    errors.push(`${code}: cannot be promoted beyond official_track_not_located without authoritative track evidence.`);
  }
  if (Number(auditRow.official_distance_m) !== Number(route.distance_m)) {
    errors.push(`${code}: audited official distance is out of sync with the intermodal catalog.`);
  }
}
for (const row of missingIntermodalTrackRows) {
  if (!expectedMissingTrackCodes.includes(row.code)) {
    errors.push(`Unexpected intermodal missing-track audit row: ${row.code}.`);
  }
}
if (intermodalMissingTrackAudit?.policy?.guessed_track_urls_forbidden !== true ||
    intermodalMissingTrackAudit?.policy?.track_found_requires_authoritative_artifact_url !== true) {
  errors.push('Intermodal missing-track audit must explicitly forbid guessed URLs and require authoritative artifact evidence.');
}

const homologatedCodes = new Set();
for (const route of homologatedRoutes) {
  if (!route.code || !route.name || !route.type || !route.homologation_status || !route.source_url) {
    errors.push('Every homologated route requires code, name, type, homologation_status and source_url.');
    continue;
  }
  if (homologatedCodes.has(route.code)) errors.push(`Duplicate homologated route code: ${route.code}.`);
  homologatedCodes.add(route.code);
  checkMunicipalityReferences(route.code, route.municipalities);
  if (route.publishable && !route.track_validated) errors.push(`${route.code}: publishable homologated routes require track_validated=true.`);
}
for (const requiredCode of ['GR-7', 'PR-A 350', 'SL-A 135']) {
  if (!homologatedCodes.has(requiredCode)) errors.push(`Missing known Sierra Mágina homologated route: ${requiredCode}.`);
}

if (closure?.official_park_inventory?.status !== 'closed') {
  errors.push('Official Parque Natural inventory must remain explicitly closed once the authoritative 17-route baseline is verified.');
}
if (closure?.official_park_inventory?.expected_signposted_trails !== requiredOfficialCoreCount ||
    closure?.official_park_inventory?.catalogued_signposted_trails !== officialCore.length) {
  errors.push('Official Parque Natural closure counts are out of sync with the master catalog.');
}
if (closure?.territorial_scope?.municipalities_expected !== requiredMunicipalityCount ||
    closure?.territorial_scope?.municipalities_audited !== auditedNames.size ||
    closure?.territorial_scope?.official_or_institutional_source_coverage !== sourceCoveredMunicipalities.size) {
  errors.push('Territorial closure counts are out of sync with municipality audit/source coverage.');
}
if (closure?.provincial_hiking_network?.expected_routes !== expectedIntermodal ||
    closure?.provincial_hiking_network?.catalogued_routes !== intermodalRoutes.length) {
  errors.push('Provincial R1-R9 closure counts are out of sync with the intermodal catalog.');
}
if (closure?.publication_gate?.complete === true) {
  const unsafeForClosure = [
    ...routes.filter((route) => route.publishable && !route.track_validated),
    ...intermodalRoutes.filter((route) => route.publishable && !route.track_validated),
    ...homologatedRoutes.filter((route) => route.publishable && !route.track_validated),
  ];
  if (unsafeForClosure.length) errors.push('Publication gate cannot be complete while a publishable route lacks a validated track.');
}

const covered = new Set(routes.flatMap((route) => route.municipalities ?? []).map((name) => name.trim().toLowerCase()));
const coveragePercent = municipalities.length ? Math.round((covered.size / municipalities.length) * 100) : 0;
const missingRouteCoverage = municipalities.filter((name) => !covered.has(name.trim().toLowerCase()));
const documentedCount = routes.filter((route) => route.documented).length;
const tracksFoundCount = routes.filter((route) => route.track_found).length;
const validatedCount = routes.filter((route) => route.track_validated).length;
const publishableCount = routes.filter((route) => route.publishable).length;
const intermodalTracksFound = intermodalRoutes.filter((route) => route.track_found).length;
const homologatedTracksFound = homologatedRoutes.filter((route) => route.track_found).length;

console.log('Sierra Mágina route catalog');
console.log(`- canonical route records: ${routes.length}`);
console.log(`- official Parque Natural core: ${officialCore.length}/${requiredOfficialCoreCount}`);
console.log(`- official intermodal hiking network: ${intermodalRoutes.length}/${expectedIntermodal}`);
console.log(`- intermodal missing-track absences audited: ${missingTrackAuditCodes.size}/${expectedMissingTrackCodes.length}`);
console.log(`- homologated GR/PR/SL records: ${homologatedRoutes.length}`);
console.log(`- municipalities audited: ${auditedNames.size}/${municipalities.length}`);
console.log(`- municipalities backed by official/institutional sources: ${sourceCoveredMunicipalities.size}/${municipalities.length}`);
console.log(`- municipalities represented by current canonical route records: ${covered.size}/${municipalities.length} (${coveragePercent}%)`);
if (missingRouteCoverage.length) console.log(`- municipalities without a current canonical route record: ${missingRouteCoverage.join(', ')}`);
console.log(`- canonical routes documented: ${documentedCount}/${routes.length}`);
console.log(`- canonical tracks found: ${tracksFoundCount}/${routes.length}`);
console.log(`- canonical validated tracks: ${validatedCount}/${routes.length}`);
console.log(`- canonical publishable: ${publishableCount}/${routes.length}`);
console.log(`- intermodal tracks found: ${intermodalTracksFound}/${intermodalRoutes.length}`);
console.log(`- homologated tracks found: ${homologatedTracksFound}/${homologatedRoutes.length}`);
console.log(`- official inventory closure: ${closure.official_park_inventory.status}`);
console.log(`- publication gate complete: ${closure.publication_gate.complete}`);

if (warnings.length) console.log(`- pending warnings: ${warnings.length}`);

if (errors.length) {
  console.error('\nRoute catalog validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nRoute catalog structure is valid. Pending warnings are catalog work, not invented completions.');