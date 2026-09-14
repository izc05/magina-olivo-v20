import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const audit = readJson('data/routes/sierra-magina-official-core-technical-audit.json');
const enrichment = readJson('data/routes/sierra-magina-official-core-enrichment.json');

const errors = [];
const core = (catalog.routes ?? []).filter((route) => route.layer === 'official_core');
const coreSlugs = new Set(core.map((route) => route.slug));
const auditRows = new Map((audit.routes ?? []).map((route) => [route.slug, route]));
const overrides = Array.isArray(enrichment.overrides) ? enrichment.overrides : [];
const overrideSlugs = new Set();

if (core.length !== 17) errors.push(`Expected exactly 17 official_core routes, found ${core.length}.`);
if ((audit.routes ?? []).length !== 17) errors.push(`Expected exactly 17 technical audit rows, found ${(audit.routes ?? []).length}.`);

for (const slug of coreSlugs) {
  if (!auditRows.has(slug)) errors.push(`Missing technical audit row for ${slug}.`);
}

for (const row of overrides) {
  if (!row.slug || !row.documentation_level || !row.verified_at) {
    errors.push('Every enrichment override requires slug, documentation_level and verified_at.');
    continue;
  }
  if (!coreSlugs.has(row.slug)) errors.push(`Enrichment references non-core route: ${row.slug}.`);
  if (overrideSlugs.has(row.slug)) errors.push(`Duplicate enrichment override: ${row.slug}.`);
  overrideSlugs.add(row.slug);

  const audited = auditRows.get(row.slug);
  if (!audited) continue;
  if (!String(audited.status ?? '').startsWith('verified') && !String(audited.status ?? '').startsWith('partial')) {
    errors.push(`${row.slug}: enrichment exists without verified/partial official technical audit evidence.`);
  }
  if ('track_validated' in row || 'publishable' in row) {
    errors.push(`${row.slug}: enrichment must never override track validation or publication state.`);
  }
}

const effective = core.map((route) => ({
  ...route,
  ...(overrides.find((row) => row.slug === route.slug) ?? {}),
}));

const documented = effective.filter((route) => route.documented).length;
const fullSheets = (audit.routes ?? []).filter((route) => route.status === 'verified_technical_sheet').length;
const partialOfficial = (audit.routes ?? []).filter((route) => String(route.status).startsWith('partial')).length;
const unresolved = (audit.routes ?? []).filter((route) => String(route.status).startsWith('pending')).map((route) => route.slug);

if (fullSheets !== 17) {
  errors.push(`Official Junta technical-sheet closure regressed: expected 17/17 verified_technical_sheet rows, found ${fullSheets}/17.`);
}
if (partialOfficial !== 0) {
  errors.push(`Official Junta technical-sheet closure cannot contain partial rows; found ${partialOfficial}.`);
}
if (unresolved.length) {
  errors.push(`Official Junta technical-sheet closure contains unresolved rows: ${unresolved.join(', ')}.`);
}

console.log('Sierra Mágina official-core enrichment');
console.log(`- official core: ${core.length}/17`);
console.log(`- effective documented: ${documented}/17`);
console.log(`- full official technical sheets: ${fullSheets}/17`);
console.log(`- partial official technical evidence: ${partialOfficial}/17`);
if (unresolved.length) console.log(`- unresolved technical rows: ${unresolved.join(', ')}`);

if (errors.length) {
  console.error('\nOfficial-core enrichment validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nOfficial-core enrichment structure is valid and the Junta technical-sheet baseline is closed 17/17.');
