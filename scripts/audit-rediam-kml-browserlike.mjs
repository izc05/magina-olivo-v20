import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));
const registry = readJson('data/routes/sierra-magina-rediam-track-sources.json');
const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const technicalAudit = readJson('data/routes/sierra-magina-official-core-technical-audit.json');

const timeoutMs = Number(process.env.REDIAM_TIMEOUT_MS ?? 20000);
const outPath = process.argv.includes('--write')
  ? (process.argv[process.argv.indexOf('--write') + 1] || 'data/routes/sierra-magina-rediam-kml-browserlike-audit.json')
  : null;

const masterBySlug = new Map((catalog.routes ?? []).map((row) => [row.slug, row]));
const auditBySlug = new Map((technicalAudit.routes ?? []).map((row) => [row.slug, row]));

const expectedDistance = (slug) => {
  const audit = auditBySlug.get(slug);
  if (Number.isFinite(audit?.distance_m)) return audit.distance_m;
  const master = masterBySlug.get(slug);
  return Number.isFinite(master?.distance_m) ? master.distance_m : null;
};

const routeShape = (slug) => auditBySlug.get(slug)?.shape ?? (masterBySlug.get(slug)?.circular === true ? 'circular' : masterBySlug.get(slug)?.circular === false ? 'linear' : null);

const buildOfficialKmlUrl = (codigoequi) => {
  const filter = `<Filter xmlns="http://www.opengis.net/ogc" xmlns:gml="http://www.opengis.net/gml"><PropertyIsEqualTo><PropertyName>CODIGOEQUI</PropertyName><Literal>${codigoequi}</Literal></PropertyIsEqualTo></Filter>`;
  const params = new URLSearchParams();
  params.set('Filter', filter);
  params.set('outputFormat', 'application/vnd.google-earth.kml.xml');
  params.set('request', 'GetFeature');
  params.set('service', 'WFS');
  params.set('srsname', 'EPSG:25830');
  params.set('typename', 'senderos:senderos');
  params.set('version', '1.0.0');
  return `https://www.juntadeandalucia.es/medioambiente/mapwms/REDIAM_WFS_Patrimonio_Natural?+=&${params.toString()}`;
};

const parseKmlLines = (text) => {
  const lines = [];
  for (const match of text.matchAll(/<(?:[A-Za-z0-9_-]+:)?coordinates(?:\s[^>]*)?>([\s\S]*?)<\/(?:[A-Za-z0-9_-]+:)?coordinates>/gi)) {
    const points = match[1]
      .trim()
      .split(/\s+/)
      .map((token) => token.split(',').slice(0, 2).map(Number))
      .filter(([x, y]) => Number.isFinite(x) && Number.isFinite(y));
    if (points.length >= 2) lines.push(points);
  }
  return lines;
};

const planarLength = (points) => {
  let total = 0;
  for (let index = 1; index < points.length; index += 1) total += Math.hypot(points[index][0] - points[index - 1][0], points[index][1] - points[index - 1][1]);
  return total;
};

const haversine = ([lon1, lat1], [lon2, lat2]) => {
  const radius = 6371008.8;
  const toRad = (degrees) => degrees * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * radius * Math.asin(Math.min(1, Math.sqrt(a)));
};

const geographicLength = (points) => {
  let total = 0;
  for (let index = 1; index < points.length; index += 1) total += haversine(points[index - 1], points[index]);
  return total;
};

const detectCoordinateSystem = (points) => {
  if (!points.length) return 'unknown';
  const geographic = points.every(([x, y]) => Math.abs(x) <= 180 && Math.abs(y) <= 90);
  if (geographic) return 'EPSG:4326-like';
  const utm30Spain = points.every(([x, y]) => x >= 100000 && x <= 900000 && y >= 3500000 && y <= 4900000);
  if (utm30Spain) return 'EPSG:25830-like';
  return 'unknown';
};

const fetchKml = async (url, referer) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      redirect: 'follow',
      headers: {
        'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
        accept: 'application/vnd.google-earth.kml+xml,application/xml,text/xml;q=0.9,*/*;q=0.8',
        'accept-language': 'es-ES,es;q=0.9,en;q=0.7',
        referer,
        origin: 'https://www.juntadeandalucia.es',
        'cache-control': 'no-cache',
        pragma: 'no-cache',
      },
    });
    const text = await response.text();
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 220).replace(/\s+/g, ' ')}`);
    return { text, contentType: response.headers.get('content-type') };
  } finally {
    clearTimeout(timer);
  }
};

const results = [];
for (const source of registry.routes ?? []) {
  const expected = expectedDistance(source.slug);
  const result = {
    slug: source.slug,
    codigoequi: String(source.codigoequi),
    expected_distance_m: expected,
    expected_shape: routeShape(source.slug),
    fetch_status: 'pending',
    geometry_validated: false,
  };

  try {
    const url = buildOfficialKmlUrl(source.codigoequi);
    const { text, contentType } = await fetchKml(url, source.source_page);
    const lines = parseKmlLines(text);
    const points = lines.flat();
    const detected = detectCoordinateSystem(points);
    const length = detected === 'EPSG:4326-like'
      ? lines.reduce((sum, line) => sum + geographicLength(line), 0)
      : detected === 'EPSG:25830-like'
        ? lines.reduce((sum, line) => sum + planarLength(line), 0)
        : 0;
    const ratio = expected && length ? length / expected : null;
    const plausible = ratio === null ? null : ratio >= 0.8 && ratio <= 2.2;

    result.fetch_status = 'success';
    result.content_type = contentType;
    result.line_parts = lines.length;
    result.point_count = points.length;
    result.coordinate_system_detected = detected;
    result.calculated_length_m = Math.round(length);
    result.length_ratio_to_sheet = ratio === null ? null : Number(ratio.toFixed(4));
    result.length_plausible_for_review = plausible;
    result.geometry_non_empty = lines.length > 0 && points.length >= 2 && length > 0;
    result.geometry_validated = result.geometry_non_empty && detected !== 'unknown' && plausible !== false;
    result.validation_status = result.geometry_validated ? 'machine_checks_passed_needs_evidence_review' : 'manual_review_required';
  } catch (error) {
    result.fetch_status = 'error';
    result.error = error instanceof Error ? error.message : String(error);
    result.validation_status = 'fetch_failed';
  }

  results.push(result);
  console.log(`${result.slug}: ${result.fetch_status}${result.calculated_length_m ? ` · ${result.calculated_length_m} m` : ''} · ${result.validation_status}`);
}

const report = {
  version: 1,
  generated_at: new Date().toISOString(),
  strategy: 'official KML URL shape with browser-like headers and per-route Junta referer',
  authority: registry.authority,
  service: registry.service,
  summary: {
    routes_expected: registry.routes?.length ?? 0,
    fetch_success: results.filter((row) => row.fetch_status === 'success').length,
    fetch_failed: results.filter((row) => row.fetch_status === 'error').length,
    machine_checks_passed: results.filter((row) => row.geometry_validated === true).length,
  },
  routes: results,
};

if (outPath) {
  fs.writeFileSync(path.resolve(outPath), `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Wrote ${outPath}`);
} else {
  console.log(JSON.stringify(report.summary, null, 2));
}

if (report.summary.fetch_failed > 0) process.exitCode = 2;
