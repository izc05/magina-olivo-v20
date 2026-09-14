import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const municipalityAudit = readJson('data/routes/sierra-magina-municipality-audit.json');
const intermodal = readJson('data/routes/sierra-magina-intermodal-hiking.json');
const homologated = readJson('data/routes/sierra-magina-homologated-trails.json');

const errors = [];
const warnings = [];

const requiredMunicipalityCount = catalog?.completeness_policy?.municipalities_required ?? 16;
const requiredOfficialCoreCount = catalog?.completeness_policy?.official_core_minimum ?? 17;
const municipalities = Array.isArray(catalog.municipalities) ? catalog.municipalities : [];
const auditedMunicipalities = Array.isArray(municipalityAudit.municipalities) ? municipalityAudit.municipalities : [];
const routes = Array.isArray(catalog.routes) ? catalog.routes : [];
const intermodalRoutes = Array.isArray(intermodal.routes) ? intermodal.routes : [];
const homologatedRoutes = Array.isArray(homologated.routes) ? homologated.routes : [];

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
console.log(`- homologated GR/PR/SL records: ${homologatedRoutes.length}`);
console.log(`- municipalities audited: ${auditedNames.size}/${municipalities.length}`);
console.log(`- municipalities represented by current canonical route records: ${covered.size}/${municipalities.length} (${coveragePercent}%)`);
if (missingRouteCoverage.length) console.log(`- municipalities without a current canonical route record: ${missingRouteCoverage.join(', ')}`);
console.log(`- canonical routes documented: ${documentedCount}/${routes.length}`);
console.log(`- canonical tracks found: ${tracksFoundCount}/${routes.length}`);
console.log(`- canonical validated tracks: ${validatedCount}/${routes.length}`);
console.log(`- canonical publishable: ${publishableCount}/${routes.length}`);
console.log(`- intermodal tracks found: ${intermodalTracksFound}/${intermodalRoutes.length}`);
console.log(`- homologated tracks found: ${homologatedTracksFound}/${homologatedRoutes.length}`);

if (warnings.length) console.log(`- pending warnings: ${warnings.length}`);

if (errors.length) {
  console.error('\nRoute catalog validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nRoute catalog structure is valid. Pending warnings are catalog work, not invented completions.');
