import { fromArrayBuffer } from 'geotiff';

export type RadarGeoTiffInspection = {
  parsed: boolean;
  analysisReady: boolean;
  width: number | null;
  height: number | null;
  samplesPerPixel: number | null;
  crs: 'EPSG:4326' | null;
  geographicTypeGeoKey: number | null;
  bbox: [number, number, number, number] | null;
  resolution: [number, number, number] | null;
  noData: number | null;
  scaleRaw: string | null;
  validationErrors: string[];
};

export type RadarGeoTiffFacts = Omit<RadarGeoTiffInspection, 'parsed' | 'analysisReady' | 'validationErrors'>;

const MAX_DIMENSION = 20_000;
const MAX_PIXELS = 80_000_000;
const MAX_SCALE_TEXT = 32_768;

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

function finiteTuple4(value: unknown): [number, number, number, number] | null {
  if (!Array.isArray(value) || value.length < 4) return null;
  const tuple = value.slice(0, 4).map(Number);
  if (!tuple.every(Number.isFinite)) return null;
  return tuple as [number, number, number, number];
}

function finiteTuple3(value: unknown): [number, number, number] | null {
  if (!Array.isArray(value) || value.length < 2) return null;
  const tuple = [Number(value[0]), Number(value[1]), Number(value[2] ?? 0)];
  if (!tuple.every(Number.isFinite)) return null;
  return tuple as [number, number, number];
}

function findScaleValue(value: unknown, depth = 0): string | null {
  if (depth > 4 || value == null) return null;
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findScaleValue(item, depth + 1);
      if (found) return found;
    }
    return null;
  }
  if (typeof value !== 'object') return null;

  for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
    if (key.trim().toUpperCase() === 'ESCALA') {
      if (typeof nested === 'string') return nested.slice(0, MAX_SCALE_TEXT);
      if (nested != null) return JSON.stringify(nested).slice(0, MAX_SCALE_TEXT);
    }
  }
  for (const nested of Object.values(value as Record<string, unknown>)) {
    const found = findScaleValue(nested, depth + 1);
    if (found) return found;
  }
  return null;
}

export function evaluateRadarGeoTiffFacts(facts: RadarGeoTiffFacts): RadarGeoTiffInspection {
  const validationErrors: string[] = [];
  const width = facts.width;
  const height = facts.height;

  if (
    width == null
    || height == null
    || !Number.isInteger(width)
    || !Number.isInteger(height)
    || width <= 0
    || height <= 0
    || width > MAX_DIMENSION
    || height > MAX_DIMENSION
    || width * height > MAX_PIXELS
  ) {
    validationErrors.push('invalid_dimensions');
  }

  if (facts.geographicTypeGeoKey !== 4326 || facts.crs !== 'EPSG:4326') {
    validationErrors.push('crs_not_epsg_4326');
  }

  const bbox = facts.bbox;
  if (
    !bbox
    || bbox[0] < -180
    || bbox[2] > 180
    || bbox[1] < -90
    || bbox[3] > 90
    || bbox[0] >= bbox[2]
    || bbox[1] >= bbox[3]
  ) {
    validationErrors.push('invalid_bbox');
  }

  const resolution = facts.resolution;
  if (
    !resolution
    || !Number.isFinite(resolution[0])
    || !Number.isFinite(resolution[1])
    || resolution[0] === 0
    || resolution[1] === 0
  ) {
    validationErrors.push('invalid_resolution');
  }

  if (!facts.scaleRaw?.trim()) validationErrors.push('scale_missing');

  return {
    ...facts,
    parsed: true,
    analysisReady: validationErrors.length === 0,
    validationErrors,
  };
}

export async function inspectRadarGeoTiff(bytes: Uint8Array): Promise<RadarGeoTiffInspection> {
  try {
    const tiff = await fromArrayBuffer(toArrayBuffer(bytes));
    const image = await tiff.getImage();
    const geoKeys = image.getGeoKeys() ?? {};
    const geographicTypeGeoKeyValue = Number((geoKeys as Record<string, unknown>).GeographicTypeGeoKey);
    const geographicTypeGeoKey = Number.isFinite(geographicTypeGeoKeyValue) ? geographicTypeGeoKeyValue : null;

    let bbox: [number, number, number, number] | null = null;
    let resolution: [number, number, number] | null = null;
    try { bbox = finiteTuple4(image.getBoundingBox()); } catch { bbox = null; }
    try { resolution = finiteTuple3(image.getResolution()); } catch { resolution = null; }

    let gdalMetadata: Record<string, unknown> | null = null;
    try { gdalMetadata = await image.getGDALMetadata(); } catch { gdalMetadata = null; }

    let noData: number | null = null;
    try {
      const value = image.getGDALNoData();
      noData = value == null || !Number.isFinite(value) ? null : value;
    } catch {
      noData = null;
    }

    return evaluateRadarGeoTiffFacts({
      width: image.getWidth(),
      height: image.getHeight(),
      samplesPerPixel: image.getSamplesPerPixel(),
      crs: geographicTypeGeoKey === 4326 ? 'EPSG:4326' : null,
      geographicTypeGeoKey,
      bbox,
      resolution,
      noData,
      scaleRaw: findScaleValue(gdalMetadata),
    });
  } catch {
    return {
      parsed: false,
      analysisReady: false,
      width: null,
      height: null,
      samplesPerPixel: null,
      crs: null,
      geographicTypeGeoKey: null,
      bbox: null,
      resolution: null,
      noData: null,
      scaleRaw: null,
      validationErrors: ['geotiff_parse_failed'],
    };
  }
}
