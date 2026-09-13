import fs from 'node:fs';
import path from 'node:path';

const catalogPath = path.resolve('data/routes/sierra-magina-master-catalog.json');
const auditPath = path.resolve('data/routes/sierra-magina-municipality-audit.json');
const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
const municipalityAudit = JSON.parse(fs.readFileSync(auditPath, 'utf8'));

const errors = [];
const warnings = [];

const requiredMunicipalityCount = catalog?.completeness_policy?.municipalities_required ?? 16;
const requiredOfficialCoreCount = catalog?.completeness_policy?.official_core_minimum ?? 17;
const municipalities = Array.isArray(catalog.municipalities) ? catalog.municipalities : [];
const auditedMunicipalities = Array.isArray(municipalityAudit.municipalities) ? municipalityAudit.municipalities : [];
const routes = Array.isArray(catalog.routes) ? catalog.routes : [];

if (municipalities.length !== requiredMunicipalityCount) {
  errors.push(`Expected ${requiredMunicipalityCount} municipalities, found ${municipalities.length}.`);
}

const normalizedMunicipalities = new Set(municipalities.map((name) => name.trim().toLowerCase()));
if (normalizedMunicipalities.size !== municipalities.length) {
  errors.push('Municipality catalog contains duplicates.');
}

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

  if (!Array.isArray(route.municipalities) || route.municipalities.length === 0) {
    errors.push(`${route.slug}: at least one municipality is required.`);
  } else {
    for (const municipality of route.municipalities) {
      if (!normalizedMunicipalities.has(String(municipality).trim().toLowerCase())) {
        errors.push(`${route.slug}: unknown municipality "${municipality}".`);
      }
    }
  }

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

const covered = new Set(routes.flatMap((route) => route.municipalities ?? []).map((name) => name.trim().toLowerCase()));
const coveragePercent = municipalities.length ? Math.round((covered.size / municipalities.length) * 100) : 0;
const missingRouteCoverage = municipalities.filter((name) => !covered.has(name.trim().toLowerCase()));
const documentedCount = routes.filter((route) => route.documented).length;
const tracksFoundCount = routes.filter((route) => route.track_found).length;
const validatedCount = routes.filter((route) => route.track_validated).length;
const publishableCount = routes.filter((route) => route.publishable).length;

console.log('Sierra Mágina route catalog');
console.log(`- routes: ${routes.length}`);
console.log(`- official core: ${officialCore.length}/${requiredOfficialCoreCount}`);
console.log(`- municipalities audited: ${auditedNames.size}/${municipalities.length}`);
console.log(`- municipalities represented by current route records: ${covered.size}/${municipalities.length} (${coveragePercent}%)`);
if (missingRouteCoverage.length) console.log(`- municipalities without a current route record: ${missingRouteCoverage.join(', ')}`);
console.log(`- documented: ${documentedCount}/${routes.length}`);
console.log(`- tracks found: ${tracksFoundCount}/${routes.length}`);
console.log(`- validated tracks: ${validatedCount}/${routes.length}`);
console.log(`- publishable: ${publishableCount}/${routes.length}`);

if (warnings.length) {
  console.log(`- pending warnings: ${warnings.length}`);
}

if (errors.length) {
  console.error('\nRoute catalog validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nRoute catalog structure is valid. Pending warnings are catalog work, not invented completions.');
