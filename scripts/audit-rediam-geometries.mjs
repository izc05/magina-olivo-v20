import fs from 'node:fs';
import path from 'node:path';

const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.resolve(relativePath), 'utf8'));
const registry = readJson('data/routes/sierra-magina-rediam-track-sources.json');
const catalog = readJson('data/routes/sierra-magina-master-catalog.json');
const technicalAudit = readJson('data/routes/sierra-magina-official-core-technical-audit.json');

const timeoutMs = Number(process.env.REDIAM_TIMEOUT_MS ?? 20000);
const outPath = process.argv.includes('--write')
  ? (process.argv[process.argv.indexOf('--write') + 1] || 'data/routes/sierra-magina-rediam-geometry-audit.json')
  : null;

const masterBySlug = new Map((catalog.routes ?? []).map((row) => [row.slug, row]));
const auditBySlug = new Map((technicalAudit.routes ?? []).map((row) => [row.slug, row]));

const buildWfsUrl = (codigoequi) => {
  const filter = `<Filter xmlns="http://www.opengis.net/ogc" xmlns:gml="http://www.opengis.net/gml"><PropertyIsEqualTo><PropertyName>CODIGOEQUI</PropertyName><Literal>${codigoequi}</Literal></PropertyIsEqualTo></Filter>`;
  const params = new URLSearchParams({
    service: 'WFS',
    version: '1.0.0',
    request: 'GetFeature',
    typename: 'senderos:senderos',
    srsname: 'EPSG:25830',
    outputFormat: 'text/xml; subtype=gml/2.1.2',
    Filter: filter,
  });
  return `https://www.juntadeandalucia.es/medioambiente/mapwms/REDIAM_WFS_Patrimonio_Natural?${params.toString()}`;
};

const parseNumber = (value) => {
  const number = Number(String(value).replace(',', '.'));
  return Number.isFinite(number) ? number : null;
};

const parseCoordinatePairs = (xml) => {
  const lines = [];

  for (const match of xml.matchAll(/<gml:coordinates(?:\s[^>]*)?>([\s\S]*?)<\/gml:coordinates>/gi)) {
    const points = match[1]
      .trim()
      .split(/\s+/)
      .map((token) => token.split(',').slice(0, 2).map(parseNumber))
      .filter(([x, y]) => x !== null && y !== null);
    if (points.length >= 2) lines.push(points);
  }

  for (const match of xml.matchAll(/<gml:posList(?:\s[^>]*)?>([\s\S]*?)<\/gml:posList>/gi)) {
    const values = match[1].trim().split(/\s+/).map(parseNumber).filter((value) => value !== null);
    const points = [];
    for (let index = 0; index + 1 < values.length; index += 2) points.push([values[index], values[index + 1]]);
    if (points.length >= 2) lines.push(points);
  }

  if (!lines.length) {
    const xValues = [...xml.matchAll(/<gml:X>([^<]+)<\/gml:X>/gi)].map((m) => parseNumber(m[1]));
    const yValues = [...xml.matchAll(/<gml:Y>([^<]+)<\/gml:Y>/gi)].map((m) => parseNumber(m[1]));
    const points = [];
    for (let index = 0; index < Math.min(xValues.length, yValues.length); index += 1) {
      if (xValues[index] !== null && yValues[index] !== null) points.push([xValues[index], yValues[index]]);
    }
    if (points.length >= 2) lines.push(points);
  }

  return lines;
};

const lineLength = (points) => {
  let total = 0;
  for (let index = 1; index < points.length; index += 1) {
    total += Math.hypot(points[index][0] - points[index - 1][0], points[index][1] - points[index - 1][1]);
  }
  return total;
};

const utm30ToWgs84 = (easting, northing) => {
  const a = 6378137;
  const eccSquared = 0.00669438;
  const k0 = 0.9996;
  const eccPrimeSquared = eccSquared / (1 - eccSquared);
  const e1 = (1 - Math.sqrt(1 - eccSquared)) / (1 + Math.sqrt(1 - eccSquared));
  const x = easting - 500000;
  const y = northing;
  const longOrigin = -3;
  const m = y / k0;
  const mu = m / (a * (1 - eccSquared / 4 - (3 * eccSquared ** 2) / 64 - (5 * eccSquared ** 3) / 256));
  const phi1Rad = mu
    + ((3 * e1) / 2 - (27 * e1 ** 3) / 32) * Math.sin(2 * mu)
    + ((21 * e1 ** 2) / 16 - (55 * e1 ** 4) / 32) * Math.sin(4 * mu)
    + ((151 * e1 ** 3) / 96) * Math.sin(6 * mu);
  const n1 = a / Math.sqrt(1 - eccSquared * Math.sin(phi1Rad) ** 2);
  const t1 = Math.tan(phi1Rad) ** 2;
  const c1 = eccPrimeSquared * Math.cos(phi1Rad) ** 2;
  const r1 = (a * (1 - eccSquared)) / (1 - eccSquared * Math.sin(phi1Rad) ** 2) ** 1.5;
  const d = x / (n1 * k0);
  let lat = phi1Rad - (n1 * Math.tan(phi1Rad) / r1) * (
    d ** 2 / 2
    - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * eccPrimeSquared) * d ** 4 / 24
    + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * eccPrimeSquared - 3 * c1 ** 2) * d ** 6 / 720
  );
  lat = lat * 180 / Math.PI;
  let lon = (
    d
    - (1 + 2 * t1 + c1) * d ** 3 / 6
    + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * eccPrimeSquared + 24 * t1 ** 2) * d ** 5 / 120
  ) / Math.cos(phi1Rad);
  lon = longOrigin + lon * 180 / Math.PI;
  return [lon, lat];
};

const expectedDistance = (slug) => {
  const audit = auditBySlug.get(slug);
  if (Number.isFinite(audit?.distance_m)) return audit.distance_m;
  const master = masterBySlug.get(slug);
  if (Number.isFinite(master?.distance_m)) return master.distance_m;
  return null;
};

const routeShape = (slug) => auditBySlug.get(slug)?.shape ?? (masterBySlug.get(slug)?.circular === true ? 'circular' : masterBySlug.get(slug)?.circular === false ? 'linear' : null);

const fetchXml = async (url) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { accept: 'application/xml,text/xml,*/*' } });
    const text = await response.text();
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 160)}`);
    return text;
  } finally {
    clearTimeout(timer);
  }
};

const results = [];
for (const source of registry.routes ?? []) {
  const url = buildWfsUrl(source.codigoequi);
  const expected = expectedDistance(source.slug);
  const result = {
    slug: source.slug,
    codigoequi: String(source.codigoequi),
    source_crs: registry.srsname,
    expected_distance_m: expected,
    expected_shape: routeShape(source.slug),
    fetch_status: 'pending',
    geometry_validated: false,
  };

  try {
    const xml = await fetchXml(url);
    const lines = parseCoordinatePairs(xml);
    const pointCount = lines.reduce((sum, line) => sum + line.length, 0);
    const calculatedLength = Math.round(lines.reduce((sum, line) => sum + lineLength(line), 0));
    const flat = lines.flat();
    const bounds = flat.length ? {
      min_x: Math.min(...flat.map(([x]) => x)),
      min_y: Math.min(...flat.map(([, y]) => y)),
      max_x: Math.max(...flat.map(([x]) => x)),
      max_y: Math.max(...flat.map(([, y]) => y)),
    } : null;
    const wgs84Bounds = bounds ? {
      southwest: utm30ToWgs84(bounds.min_x, bounds.min_y),
      northeast: utm30ToWgs84(bounds.max_x, bounds.max_y),
    } : null;
    const ratio = expected && calculatedLength ? calculatedLength / expected : null;
    const plausibleLength = ratio === null ? null : ratio >= 0.8 && ratio <= 2.2;

    result.fetch_status = 'success';
    result.line_parts = lines.length;
    result.point_count = pointCount;
    result.calculated_length_m = calculatedLength;
    result.length_ratio_to_sheet = ratio === null ? null : Number(ratio.toFixed(4));
    result.length_plausible_for_review = plausibleLength;
    result.bounds_epsg25830 = bounds;
    result.bounds_wgs84 = wgs84Bounds;
    result.geometry_non_empty = lines.length > 0 && pointCount >= 2 && calculatedLength > 0;
    result.geometry_validated = result.geometry_non_empty === true && plausibleLength !== false;
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
  authority: registry.authority,
  service: registry.service,
  source_crs: registry.srsname,
  methodology: {
    geometry_source: 'official REDIAM WFS filtered by confirmed CODIGOEQUI',
    length: 'planar sum of source geometry segments in EPSG:25830 meters',
    transform: 'WGS84 / UTM zone 30N mathematical conversion for bounds sanity check',
    auto_validation_limit: 'machine checks only; publication still requires evidence review and catalog update',
    plausibility_ratio_range: [0.8, 2.2],
    note: 'The broad ratio intentionally accommodates one-way technical-sheet distance versus complete/circular geometry. Material discrepancies still require manual review.'
  },
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
