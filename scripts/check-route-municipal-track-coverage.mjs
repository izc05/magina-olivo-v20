import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));

const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const located = readJson('data/routes/sierra-magina-municipal-track-sources.json');
const accessAudit = readJson('data/routes/sierra-magina-municipal-kml-access-audit.json');
const absenceAudit = readJson('data/routes/sierra-magina-municipal-track-discovery-audit.json');

const errors = [];
const comarcaRoutes = (catalog.routes ?? []).filter((route) => route.layer === 'official_comarca');
const comarcaBySlug = new Map(comarcaRoutes.map((route) => [route.slug, route]));
const locatedRows = Array.isArray(located.sources) ? located.sources : [];
const accessRows = Array.isArray(accessAudit.routes) ? accessAudit.routes : [];
const absenceSources = Array.isArray(absenceAudit.sources) ? absenceAudit.sources : [];

const locatedSlugs = new Set();
for (const row of locatedRows) {
  if (!row.slug || !row.source_page_url || !row.artifact_url) {
    errors.push('Every located municipal track row requires slug, source_page_url and artifact_url.');
    continue;
  }
  if (locatedSlugs.has(row.slug)) errors.push(`Duplicate located municipal track slug: ${row.slug}.`);
  locatedSlugs.add(row.slug);
  const masterRoute = comarcaBySlug.get(row.slug);
  if (!masterRoute) errors.push(`Located municipal track references non-comarca route: ${row.slug}.`);
  if (row.track_found !== true) errors.push(`${row.slug}: authoritative located artifact requires track_found=true in the municipal source registry.`);
  if (row.track_validated !== false) errors.push(`${row.slug}: geometry must remain unvalidated until strict-TLS fetch and parse succeeds.`);
  if (!/^https:\/\/.+\.(kml|kmz|gpx)$/i.test(row.artifact_url)) errors.push(`${row.slug}: artifact URL is not an explicit HTTPS GPX/KML/KMZ resource.`);

  if (masterRoute) {
    if (masterRoute.track_found !== true) errors.push(`${row.slug}: master catalog must preserve track_found=true for the located authoritative artifact.`);
    if (masterRoute.track_url !== row.artifact_url) errors.push(`${row.slug}: master catalog track_url must match the authoritative municipal registry artifact URL.`);
    if (masterRoute.track_validated !== false) errors.push(`${row.slug}: master catalog must remain track_validated=false until geometry audit succeeds.`);
    if (masterRoute.track_validation_state !== 'official_kml_located_pending_external_fetch') {
      errors.push(`${row.slug}: master catalog must preserve official_kml_located_pending_external_fetch while strict-TLS fetch is blocked.`);
    }
    if (masterRoute.publishable !== false) errors.push(`${row.slug}: master catalog cannot be publishable before municipal KML geometry validation succeeds.`);
  }
}

if (locatedSlugs.size !== 3) errors.push(`Expected 3 located Mancha Real municipal KML artifacts, found ${locatedSlugs.size}.`);

const accessBySlug = new Map(accessRows.map((row) => [row.slug, row]));
for (const slug of locatedSlugs) {
  const row = accessBySlug.get(slug);
  if (!row) {
    errors.push(`${slug}: missing durable municipal KML access-audit row.`);
    continue;
  }
  if (row.validation_status !== 'pending_external_fetch' || row.error_class !== 'ssl_certificate_hostname_mismatch') {
    errors.push(`${slug}: expected preserved strict-TLS hostname-mismatch blocker until a successful re-audit supersedes it.`);
  }
}
if (accessAudit?.policy?.tls_verification_must_not_be_disabled_to_claim_validation !== true) {
  errors.push('Municipal KML access policy must forbid disabling TLS verification to claim geometry validation.');
}

const absentSlugs = new Set();
for (const source of absenceSources) {
  if (!source.municipality || !source.authority || !source.source_url || source.authoritative_track_status !== 'not_located') {
    errors.push('Every municipal absence source requires municipality, authority, source_url and authoritative_track_status=not_located.');
  }
  if (!Array.isArray(source.artifact_urls) || source.artifact_urls.length !== 0) {
    errors.push(`${source.municipality ?? 'unknown'}: absence audit must not contain an artifact URL.`);
  }
  for (const slug of source.route_slugs ?? []) {
    if (absentSlugs.has(slug)) errors.push(`Duplicate absent municipal route slug: ${slug}.`);
    absentSlugs.add(slug);
    const route = comarcaBySlug.get(slug);
    if (!route) errors.push(`Municipal absence audit references non-comarca route: ${slug}.`);
    if (locatedSlugs.has(slug)) errors.push(`${slug}: cannot be both located and not_located.`);
    if (route?.track_found) errors.push(`${slug}: route with audited no-public-authoritative-track disposition cannot have track_found=true in master catalog.`);
    if (route?.track_url) errors.push(`${slug}: route with audited no-public-authoritative-track disposition cannot have track_url in master catalog.`);
    if (route?.publishable) errors.push(`${slug}: route with no authoritative public track artifact cannot be publishable.`);
    if (route?.track_validated) errors.push(`${slug}: route with no authoritative public track artifact cannot have track_validated=true.`);
  }
}

if (absenceSources.length !== 5) errors.push(`Expected 5 remaining municipal source-set audits, found ${absenceSources.length}.`);
if (absentSlugs.size !== 10) errors.push(`Expected 10 comarca route records covered by audited public-track absence, found ${absentSlugs.size}.`);
if (absenceAudit?.policy?.community_tracks_do_not_count_as_official !== true ||
    absenceAudit?.policy?.guessed_download_urls_forbidden !== true) {
  errors.push('Municipal absence audit must preserve community-track exclusion and guessed-URL prohibition.');
}

const covered = new Set([...locatedSlugs, ...absentSlugs]);
for (const route of comarcaRoutes) {
  if (!covered.has(route.slug)) errors.push(`official_comarca route lacks municipal track-discovery disposition: ${route.slug}.`);
}
for (const slug of covered) {
  if (!comarcaBySlug.has(slug)) errors.push(`Municipal track-discovery disposition references unknown official_comarca route: ${slug}.`);
}

console.log('Sierra Mágina municipal/comarca track coverage');
console.log(`- official_comarca routes: ${comarcaRoutes.length}`);
console.log(`- authoritative track artifacts located: ${locatedSlugs.size}`);
console.log(`- audited no-public-authoritative-track routes: ${absentSlugs.size}`);
console.log(`- discovery disposition coverage: ${covered.size}/${comarcaRoutes.length}`);
console.log(`- master/registry located-track sync: ${locatedSlugs.size}/${locatedSlugs.size}`);
console.log(`- municipal geometries validated: 0/${locatedSlugs.size}`);
console.log('- current located-artifact blocker: strict TLS hostname mismatch on archivos.campusfortalezas.com');

if (errors.length) {
  console.error('\nMunicipal/comarca track coverage validation failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('\nMunicipal/comarca public track-discovery coverage is closed 13/13 and master/registry state is synchronized.');
