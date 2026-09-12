import proj4 from 'proj4';

const CATASTRO_WFS_URL = 'https://ovc.catastro.meh.es/INSPIRE/wfsCP.aspx';
const CATASTRO_TIMEOUT_MS = 8_000;
const CATASTRO_MAX_FEATURES = 80;
const MAX_XML_BYTES = 2_000_000;

const WGS84 = 'EPSG:4326';
const ETRS89_UTM30 = '+proj=utm +zone=30 +ellps=GRS80 +units=m +no_defs +type=crs';

export type CatastroBbox = {
  minLon: number;
  minLat: number;
  maxLon: number;
  maxLat: number;
};

export type PolygonGeometry = {
  type: 'Polygon';
  coordinates: number[][][];
};

export type MultiPolygonGeometry = {
  type: 'MultiPolygon';
  coordinates: number[][][][];
};

export type LandGeometry = PolygonGeometry | MultiPolygonGeometry;

export type CatastroParcel = {
  id: string;
  nationalCadastralReference: string;
  label: string | null;
  areaM2: number | null;
  beginLifespanVersion: string | null;
  geometry: LandGeometry;
};

function xmlText(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&')
    .trim();
}

function tagValue(xml: string, localName: string): string | null {
  const expression = new RegExp(`<(?:(?:[A-Za-z0-9_-]+):)?${localName}\\b[^>]*>([\\s\\S]*?)<\\/(?:(?:[A-Za-z0-9_-]+):)?${localName}>`, 'i');
  const match = expression.exec(xml);
  if (!match?.[1]) return null;
  return xmlText(match[1].replace(/<[^>]+>/g, ''));
}

function finiteNumber(value: string | null): number | null {
  if (value == null || value === '') return null;
  const number = Number(value.replace(',', '.'));
  return Number.isFinite(number) ? number : null;
}

export function validateCatastroBbox(bbox: CatastroBbox): string | null {
  const values = [bbox.minLon, bbox.minLat, bbox.maxLon, bbox.maxLat];
  if (!values.every(Number.isFinite)) return 'Catastro bbox must contain finite coordinates';
  if (bbox.minLon < -10 || bbox.maxLon > 5 || bbox.minLat < 35 || bbox.maxLat > 44) {
    return 'Catastro bbox is outside the supported Spain/Mágina scope';
  }
  if (bbox.minLon >= bbox.maxLon || bbox.minLat >= bbox.maxLat) return 'Catastro bbox is inverted or empty';
  if (bbox.maxLon - bbox.minLon > 0.05 || bbox.maxLat - bbox.minLat > 0.05) {
    return 'Catastro bbox exceeds the V20 maximum span';
  }
  return null;
}

export function validateCadastralReference(reference: string): boolean {
  return /^[A-Z0-9]{14}$/.test(reference.trim().toUpperCase());
}

function toUtm30(longitude: number, latitude: number): [number, number] {
  return proj4(WGS84, ETRS89_UTM30, [longitude, latitude]) as [number, number];
}

function fromUtm30(x: number, y: number): [number, number] {
  return proj4(ETRS89_UTM30, WGS84, [x, y]) as [number, number];
}

export function buildCatastroBboxUrl(bbox: CatastroBbox): string {
  const validation = validateCatastroBbox(bbox);
  if (validation) throw new Error(validation);
  const [minX, minY] = toUtm30(bbox.minLon, bbox.minLat);
  const [maxX, maxY] = toUtm30(bbox.maxLon, bbox.maxLat);
  const url = new URL(CATASTRO_WFS_URL);
  url.searchParams.set('service', 'WFS');
  url.searchParams.set('version', '2.0.0');
  url.searchParams.set('request', 'GetFeature');
  url.searchParams.set('typenames', 'cp:CadastralParcel');
  url.searchParams.set('srsName', 'EPSG::25830');
  url.searchParams.set('bbox', `${minX},${minY},${maxX},${maxY}`);
  url.searchParams.set('count', String(CATASTRO_MAX_FEATURES));
  return url.toString();
}

export function buildCatastroReferenceUrl(reference: string): string {
  const normalized = reference.trim().toUpperCase();
  if (!validateCadastralReference(normalized)) throw new Error('Invalid cadastral reference');
  const url = new URL(CATASTRO_WFS_URL);
  url.searchParams.set('service', 'WFS');
  url.searchParams.set('version', '2.0.0');
  url.searchParams.set('request', 'GetFeature');
  url.searchParams.set('STOREDQUERY_ID', 'GetParcel');
  url.searchParams.set('REFCAT', normalized);
  url.searchParams.set('srsName', 'EPSG::25830');
  return url.toString();
}

function parsePosList(text: string): number[][] | null {
  const values = text.trim().split(/\s+/).map(Number);
  if (values.length < 8 || values.length % 2 !== 0 || !values.every(Number.isFinite)) return null;
  const ring: number[][] = [];
  for (let index = 0; index < values.length; index += 2) {
    const point = fromUtm30(values[index]!, values[index + 1]!);
    if (!point.every(Number.isFinite)) return null;
    ring.push(point);
    if (ring.length > 10_000) return null;
  }
  return ring;
}

function extractRings(memberXml: string): number[][][] {
  const rings: number[][][] = [];
  const expression = /<(?:(?:[A-Za-z0-9_-]+):)?posList\b[^>]*>([\s\S]*?)<\/(?:(?:[A-Za-z0-9_-]+):)?posList>/gi;
  let match: RegExpExecArray | null;
  while ((match = expression.exec(memberXml)) !== null) {
    if (!match[1]) continue;
    const ring = parsePosList(xmlText(match[1]));
    if (ring) rings.push(ring);
    if (rings.length > 64) break;
  }
  return rings;
}

export function parseCatastroGml(xml: string): CatastroParcel[] {
  if (!xml || Buffer.byteLength(xml, 'utf8') > MAX_XML_BYTES) throw new Error('Catastro response exceeds safe XML size');
  if (/<(?:ows:)?ExceptionReport\b/i.test(xml)) throw new Error('Catastro WFS returned an exception');

  const memberExpression = /<(?:(?:[A-Za-z0-9_-]+):)?member\b[^>]*>([\s\S]*?)<\/(?:(?:[A-Za-z0-9_-]+):)?member>/gi;
  const items: CatastroParcel[] = [];
  let memberMatch: RegExpExecArray | null;
  while ((memberMatch = memberExpression.exec(xml)) !== null && items.length < CATASTRO_MAX_FEATURES) {
    const member = memberMatch[1] ?? '';
    if (!/(?:CadastralParcel)/i.test(member)) continue;
    const reference = tagValue(member, 'nationalCadastralReference')?.toUpperCase() ?? null;
    if (!reference || !validateCadastralReference(reference)) continue;
    const rings = extractRings(member);
    if (!rings.length) continue;
    const geometry: LandGeometry = rings.length === 1
      ? { type: 'Polygon', coordinates: [rings[0]!] }
      : { type: 'MultiPolygon', coordinates: rings.map((ring) => [ring]) };
    items.push({
      id: reference,
      nationalCadastralReference: reference,
      label: tagValue(member, 'label'),
      areaM2: finiteNumber(tagValue(member, 'areaValue')),
      beginLifespanVersion: tagValue(member, 'beginLifespanVersion'),
      geometry,
    });
  }
  return items;
}

async function fetchCatastroXml(url: string): Promise<string> {
  const response = await fetch(url, {
    headers: {
      accept: 'application/gml+xml, application/xml, text/xml',
      'user-agent': 'Magina-Olivo-V20/1.0 Catastro-INSPIRE-adapter',
    },
    signal: AbortSignal.timeout(CATASTRO_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`Catastro upstream HTTP ${response.status}`);
  const contentLength = Number(response.headers.get('content-length') ?? 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_XML_BYTES) throw new Error('Catastro response exceeds safe XML size');
  return response.text();
}

export async function fetchCatastroParcels(bbox: CatastroBbox): Promise<CatastroParcel[]> {
  return parseCatastroGml(await fetchCatastroXml(buildCatastroBboxUrl(bbox)));
}

export async function fetchCatastroParcelByReference(reference: string): Promise<CatastroParcel> {
  const normalized = reference.trim().toUpperCase();
  const items = parseCatastroGml(await fetchCatastroXml(buildCatastroReferenceUrl(normalized)));
  const exact = items.find((item) => item.nationalCadastralReference === normalized);
  if (!exact) throw new Error('Catastro parcel not found');
  return exact;
}
