import type { LandGeometry } from './catastro.js';

const SIGPAC_RECINTOS_URL = 'https://sigpac-hubcloud.es/ogcapi/collections/recintos/items';
const SIGPAC_TIMEOUT_MS = 8_000;
const SIGPAC_LIMIT = 100;
const MAX_GEOMETRY_POSITIONS = 20_000;

export type SigpacBbox = {
  minLon: number;
  minLat: number;
  maxLon: number;
  maxLat: number;
};

export type SigpacRecinto = {
  id: string;
  provincia: number | null;
  municipio: number | null;
  agregado: number | null;
  zona: number | null;
  poligono: number | null;
  parcela: number | null;
  recinto: number | null;
  pendienteMedia: number | null;
  altitud: number | null;
  surfaceM2: number | null;
  usoSigpac: string | null;
  geometry: LandGeometry;
};

type ExternalFeature = {
  id?: unknown;
  properties?: Record<string, unknown>;
  geometry?: { type?: unknown; coordinates?: unknown } | null;
};

type ExternalFeatureCollection = { features?: unknown };

function finiteNumber(value: unknown): number | null {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function optionalString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function property(properties: Record<string, unknown>, ...names: string[]): unknown {
  for (const name of names) if (Object.hasOwn(properties, name)) return properties[name];
  return undefined;
}

function countAndValidatePositions(value: unknown, depth = 0): number | null {
  if (!Array.isArray(value) || depth > 5) return null;
  if (value.length === 2 && value.every((item) => typeof item === 'number' && Number.isFinite(item))) {
    const longitude = value[0] as number;
    const latitude = value[1] as number;
    return longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90 ? 1 : null;
  }

  let count = 0;
  for (const child of value) {
    const childCount = countAndValidatePositions(child, depth + 1);
    if (childCount == null) return null;
    count += childCount;
    if (count > MAX_GEOMETRY_POSITIONS) return null;
  }
  return count;
}

function normalizeGeometry(geometry: ExternalFeature['geometry']): LandGeometry | null {
  if (!geometry || (geometry.type !== 'Polygon' && geometry.type !== 'MultiPolygon')) return null;
  const positionCount = countAndValidatePositions(geometry.coordinates);
  if (positionCount == null || positionCount < 4) return null;
  return { type: geometry.type, coordinates: geometry.coordinates } as LandGeometry;
}

export function validateSigpacBbox(bbox: SigpacBbox): string | null {
  const values = [bbox.minLon, bbox.minLat, bbox.maxLon, bbox.maxLat];
  if (!values.every(Number.isFinite)) return 'SIGPAC bbox must contain finite coordinates';
  if (bbox.minLon < -180 || bbox.maxLon > 180 || bbox.minLat < -90 || bbox.maxLat > 90) return 'SIGPAC bbox is outside WGS84 ranges';
  if (bbox.minLon >= bbox.maxLon || bbox.minLat >= bbox.maxLat) return 'SIGPAC bbox is inverted or empty';
  if (bbox.maxLon - bbox.minLon > 0.05 || bbox.maxLat - bbox.minLat > 0.05) return 'SIGPAC bbox exceeds the V20 maximum span';
  return null;
}

export function validateSigpacFeatureId(featureId: string): boolean {
  return /^\d{1,20}$/.test(featureId);
}

export function buildSigpacRecintosUrl(bbox: SigpacBbox): string {
  const validation = validateSigpacBbox(bbox);
  if (validation) throw new Error(validation);
  const url = new URL(SIGPAC_RECINTOS_URL);
  url.searchParams.set('f', 'json');
  url.searchParams.set('bbox', `${bbox.minLon},${bbox.minLat},${bbox.maxLon},${bbox.maxLat}`);
  url.searchParams.set('limit', String(SIGPAC_LIMIT));
  return url.toString();
}

export function buildSigpacRecintoByIdUrl(featureId: string): string {
  if (!validateSigpacFeatureId(featureId)) throw new Error('Invalid SIGPAC feature id');
  const url = new URL(`${SIGPAC_RECINTOS_URL}/${encodeURIComponent(featureId)}`);
  url.searchParams.set('f', 'json');
  return url.toString();
}

export function normalizeSigpacFeature(feature: ExternalFeature): SigpacRecinto | null {
  const geometry = normalizeGeometry(feature.geometry);
  if (!geometry) return null;
  const properties = feature.properties ?? {};
  const idValue = feature.id ?? property(properties, 'dn_pk', 'DN_PK', 'id');
  if (idValue == null) return null;

  return {
    id: String(idValue),
    provincia: finiteNumber(property(properties, 'provincia', 'PROVINCIA')),
    municipio: finiteNumber(property(properties, 'municipio', 'MUNICIPIO')),
    agregado: finiteNumber(property(properties, 'agregado', 'AGREGADO')),
    zona: finiteNumber(property(properties, 'zona', 'ZONA')),
    poligono: finiteNumber(property(properties, 'poligono', 'POLIGONO')),
    parcela: finiteNumber(property(properties, 'parcela', 'PARCELA')),
    recinto: finiteNumber(property(properties, 'recinto', 'RECINTO')),
    pendienteMedia: finiteNumber(property(properties, 'pendiente_media', 'PENDIENTE_MEDIA')),
    altitud: finiteNumber(property(properties, 'altitud', 'ALTITUD')),
    surfaceM2: finiteNumber(property(properties, 'dn_surface', 'DN_SURFACE', 'surface')),
    usoSigpac: optionalString(property(properties, 'uso_sigpac', 'USO_SIGPAC')),
    geometry,
  };
}

async function upstreamRequest(url: string): Promise<Response> {
  return fetch(url, {
    headers: {
      accept: 'application/geo+json, application/json',
      'user-agent': 'Magina-Olivo-V20/1.0 SIGPAC-adapter',
    },
    signal: AbortSignal.timeout(SIGPAC_TIMEOUT_MS),
  });
}

export async function fetchSigpacRecintos(bbox: SigpacBbox): Promise<SigpacRecinto[]> {
  const response = await upstreamRequest(buildSigpacRecintosUrl(bbox));
  if (!response.ok) throw new Error(`SIGPAC upstream HTTP ${response.status}`);
  const payload = await response.json() as ExternalFeatureCollection;
  if (!Array.isArray(payload.features)) throw new Error('SIGPAC upstream returned an invalid feature collection');
  return payload.features.slice(0, SIGPAC_LIMIT).flatMap((feature) => {
    if (typeof feature !== 'object' || feature === null) return [];
    const normalized = normalizeSigpacFeature(feature as ExternalFeature);
    return normalized ? [normalized] : [];
  });
}

export async function fetchSigpacRecintoById(featureId: string): Promise<SigpacRecinto> {
  const response = await upstreamRequest(buildSigpacRecintoByIdUrl(featureId));
  if (!response.ok) throw new Error(`SIGPAC upstream HTTP ${response.status}`);
  const normalized = normalizeSigpacFeature(await response.json() as ExternalFeature);
  if (!normalized || normalized.id !== featureId) throw new Error('SIGPAC upstream returned an invalid or mismatched feature');
  return normalized;
}
