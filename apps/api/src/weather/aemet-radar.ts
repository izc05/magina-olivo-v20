import type { RadarSnapshotMetadata } from '@magina/contracts';

const AEMET_BASE_URL = 'https://opendata.aemet.es/opendata';
const AEMET_RADAR_NATIONAL_ENDPOINT = `${AEMET_BASE_URL}/api/red/radar/nacional`;
const RADAR_TIMEOUT_MS = 8_000;
const MAX_RADAR_BYTES = 15 * 1024 * 1024;

export type RadarBinaryAsset = {
  metadata: RadarSnapshotMetadata;
  bytes: Uint8Array;
  contentType: string;
  sourceUrl: string;
};

type AemetEnvelope = {
  estado?: number;
  descripcion?: string;
  datos?: string;
};

function trustedAemetDataUrl(value: unknown): URL {
  if (typeof value !== 'string' || !value.trim()) throw new Error('AEMET_RADAR_DATA_URL_MISSING');
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.hostname !== 'opendata.aemet.es') {
    throw new Error('AEMET_RADAR_DATA_URL_NOT_TRUSTED');
  }
  return url;
}

function assetFormat(contentType: string): RadarSnapshotMetadata['asset_format'] {
  const normalized = contentType.split(';', 1)[0]?.trim().toLowerCase();
  if (normalized === 'image/gif') return 'gif';
  if (normalized === 'image/png') return 'png';
  if (normalized === 'image/jpeg') return 'jpeg';
  if (normalized === 'image/tiff' || normalized === 'image/geotiff') return 'geotiff';
  return 'unknown';
}

export function resolveAemetNationalRadarEnvelope(payload: unknown): URL {
  if (!payload || typeof payload !== 'object') throw new Error('AEMET_RADAR_ENVELOPE_INVALID');
  return trustedAemetDataUrl((payload as AemetEnvelope).datos);
}

export function buildRadarSnapshotMetadata(contentType: string, fetchedAt: Date): RadarSnapshotMetadata {
  const format = assetFormat(contentType);
  return {
    source: 'aemet_national_mosaic',
    product: 'reflectivity',
    crs: 'EPSG:4326',
    observed_at: null,
    fetched_at: fetchedAt.toISOString(),
    asset_format: format,
    analysis_ready: format === 'geotiff',
  };
}

async function fetchWithTimeout(url: string, init?: RequestInit) {
  return fetch(url, { ...init, signal: AbortSignal.timeout(RADAR_TIMEOUT_MS) });
}

export async function fetchAemetNationalRadarAsset(): Promise<RadarBinaryAsset> {
  const apiKey = process.env.AEMET_API_KEY?.trim();
  if (!apiKey) throw new Error('AEMET_API_KEY_NOT_CONFIGURED');

  const envelopeResponse = await fetchWithTimeout(AEMET_RADAR_NATIONAL_ENDPOINT, {
    headers: {
      api_key: apiKey,
      accept: 'application/json',
      'user-agent': 'Magina-Olivo/20 (+radar-observation-adapter)',
    },
  });
  if (!envelopeResponse.ok) throw new Error(`AEMET_RADAR_ENVELOPE_HTTP_${envelopeResponse.status}`);

  const dataUrl = resolveAemetNationalRadarEnvelope(await envelopeResponse.json());
  const assetResponse = await fetchWithTimeout(dataUrl.toString(), {
    headers: { accept: 'image/*,application/octet-stream' },
  });
  if (!assetResponse.ok) throw new Error(`AEMET_RADAR_ASSET_HTTP_${assetResponse.status}`);

  const declaredSize = Number(assetResponse.headers.get('content-length'));
  if (Number.isFinite(declaredSize) && declaredSize > MAX_RADAR_BYTES) throw new Error('AEMET_RADAR_ASSET_TOO_LARGE');

  const bytes = new Uint8Array(await assetResponse.arrayBuffer());
  if (bytes.byteLength > MAX_RADAR_BYTES) throw new Error('AEMET_RADAR_ASSET_TOO_LARGE');

  const contentType = assetResponse.headers.get('content-type') ?? 'application/octet-stream';
  return {
    metadata: buildRadarSnapshotMetadata(contentType, new Date()),
    bytes,
    contentType,
    sourceUrl: dataUrl.toString(),
  };
}
