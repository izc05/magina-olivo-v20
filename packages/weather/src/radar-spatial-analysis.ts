import { fromArrayBuffer } from 'geotiff';
import { inspectRadarGeoTiff, type RadarGeoTiffInspection } from './radar-geotiff-inspection.js';
import type { RadarPaletteIndexBand } from './aemet-national-reflectivity-palette.js';

export type GeoPoint = { lat: number; lon: number };
export type RadarCoverage = 'covered' | 'no_coverage' | 'unknown' | 'outside_raster';
export type CompassDirection = 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW';

export type RadarEchoBand = {
  minDbz: number;
  maxDbz: number | null;
};

export type RadarSpatialObservation = {
  analysisReady: boolean;
  coverageAtPoint: RadarCoverage;
  precipitationDetected: boolean;
  pointBand: RadarEchoBand | null;
  nearestEchoDistanceKm: number | null;
  nearestEchoBearingDeg: number | null;
  nearestEchoDirection: CompassDirection | null;
  nearestEchoBand: RadarEchoBand | null;
  strongestBandWithinRadius: RadarEchoBand | null;
  searchRadiusKm: number;
  reason: string | null;
};

export type RadarGrid = {
  width: number;
  height: number;
  bbox: [number, number, number, number];
  values: ArrayLike<number>;
  indexBands: RadarPaletteIndexBand[];
  noCoverageIndexes: number[];
  clearIndexes: number[];
};

const EARTH_RADIUS_KM = 6371.0088;
const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

function toRadians(value: number) {
  return value * DEG_TO_RAD;
}

export function haversineKm(a: GeoPoint, b: GeoPoint) {
  const lat1 = toRadians(a.lat);
  const lat2 = toRadians(b.lat);
  const dLat = toRadians(b.lat - a.lat);
  const dLon = toRadians(b.lon - a.lon);
  const sinLat = Math.sin(dLat / 2);
  const sinLon = Math.sin(dLon / 2);
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function initialBearingDeg(a: GeoPoint, b: GeoPoint) {
  const lat1 = toRadians(a.lat);
  const lat2 = toRadians(b.lat);
  const dLon = toRadians(b.lon - a.lon);
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return (Math.atan2(y, x) * RAD_TO_DEG + 360) % 360;
}

export function bearingToCompass(bearing: number): CompassDirection {
  const directions: CompassDirection[] = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  return directions[Math.round(((bearing % 360) + 360) % 360 / 45) % 8]!;
}

function pointInsideBbox(point: GeoPoint, bbox: RadarGrid['bbox']) {
  return point.lon >= bbox[0] && point.lon <= bbox[2] && point.lat >= bbox[1] && point.lat <= bbox[3];
}

function pixelForPoint(point: GeoPoint, grid: Pick<RadarGrid, 'width' | 'height' | 'bbox'>) {
  if (!pointInsideBbox(point, grid.bbox)) return null;
  const [minLon, minLat, maxLon, maxLat] = grid.bbox;
  const x = Math.min(grid.width - 1, Math.max(0, Math.floor(((point.lon - minLon) / (maxLon - minLon)) * grid.width)));
  const y = Math.min(grid.height - 1, Math.max(0, Math.floor(((maxLat - point.lat) / (maxLat - minLat)) * grid.height)));
  return { x, y };
}

function pointForPixel(x: number, y: number, grid: Pick<RadarGrid, 'width' | 'height' | 'bbox'>): GeoPoint {
  const [minLon, minLat, maxLon, maxLat] = grid.bbox;
  return {
    lon: minLon + ((x + 0.5) / grid.width) * (maxLon - minLon),
    lat: maxLat - ((y + 0.5) / grid.height) * (maxLat - minLat),
  };
}

function bandShape(band: RadarPaletteIndexBand | null | undefined): RadarEchoBand | null {
  return band ? { minDbz: band.min, maxDbz: band.max } : null;
}

function stronger(a: RadarPaletteIndexBand | null, b: RadarPaletteIndexBand) {
  if (!a) return b;
  return b.min > a.min ? b : a;
}

export function analyzeRadarGrid(
  grid: RadarGrid,
  point: GeoPoint,
  options: { radiusKm?: number; minDbz?: number } = {},
): RadarSpatialObservation {
  const radiusKm = Math.min(250, Math.max(1, options.radiusKm ?? 80));
  const minDbz = Math.max(12, options.minDbz ?? 12);

  if (!Number.isFinite(point.lat) || !Number.isFinite(point.lon)) {
    return {
      analysisReady: false,
      coverageAtPoint: 'unknown',
      precipitationDetected: false,
      pointBand: null,
      nearestEchoDistanceKm: null,
      nearestEchoBearingDeg: null,
      nearestEchoDirection: null,
      nearestEchoBand: null,
      strongestBandWithinRadius: null,
      searchRadiusKm: radiusKm,
      reason: 'invalid_point',
    };
  }

  if (grid.values.length !== grid.width * grid.height) {
    return {
      analysisReady: false,
      coverageAtPoint: 'unknown',
      precipitationDetected: false,
      pointBand: null,
      nearestEchoDistanceKm: null,
      nearestEchoBearingDeg: null,
      nearestEchoDirection: null,
      nearestEchoBand: null,
      strongestBandWithinRadius: null,
      searchRadiusKm: radiusKm,
      reason: 'raster_size_mismatch',
    };
  }

  const target = pixelForPoint(point, grid);
  if (!target) {
    return {
      analysisReady: true,
      coverageAtPoint: 'outside_raster',
      precipitationDetected: false,
      pointBand: null,
      nearestEchoDistanceKm: null,
      nearestEchoBearingDeg: null,
      nearestEchoDirection: null,
      nearestEchoBand: null,
      strongestBandWithinRadius: null,
      searchRadiusKm: radiusKm,
      reason: 'point_outside_raster',
    };
  }

  const bandByIndex = new Map(grid.indexBands.map((band) => [band.index, band]));
  const noCoverage = new Set(grid.noCoverageIndexes);
  const clear = new Set(grid.clearIndexes);
  const targetValue = Number(grid.values[target.y * grid.width + target.x]);
  const targetBand = bandByIndex.get(targetValue) ?? null;

  let coverageAtPoint: RadarCoverage = 'unknown';
  if (targetBand || clear.has(targetValue)) coverageAtPoint = 'covered';
  else if (noCoverage.has(targetValue)) coverageAtPoint = 'no_coverage';

  let nearest: { distanceKm: number; bearing: number; band: RadarPaletteIndexBand } | null = null;
  let strongest: RadarPaletteIndexBand | null = null;

  // Scan the raster once. The current AEMET national product is ~344k pixels,
  // which is inexpensive in a worker and avoids fragile degree/km window math.
  // A future optimized window reader can preserve this pure contract.
  for (let y = 0; y < grid.height; y += 1) {
    for (let x = 0; x < grid.width; x += 1) {
      const value = Number(grid.values[y * grid.width + x]);
      const band = bandByIndex.get(value);
      if (!band || band.min < minDbz) continue;

      const pixelPoint = pointForPixel(x, y, grid);
      const distanceKm = haversineKm(point, pixelPoint);
      if (distanceKm > radiusKm) continue;

      strongest = stronger(strongest, band);
      if (!nearest || distanceKm < nearest.distanceKm) {
        nearest = {
          distanceKm,
          bearing: initialBearingDeg(point, pixelPoint),
          band,
        };
      }
    }
  }

  return {
    analysisReady: true,
    coverageAtPoint,
    precipitationDetected: nearest !== null,
    pointBand: bandShape(targetBand),
    nearestEchoDistanceKm: nearest ? Math.round(nearest.distanceKm * 10) / 10 : null,
    nearestEchoBearingDeg: nearest ? Math.round(nearest.bearing) : null,
    nearestEchoDirection: nearest ? bearingToCompass(nearest.bearing) : null,
    nearestEchoBand: bandShape(nearest?.band),
    strongestBandWithinRadius: bandShape(strongest),
    searchRadiusKm: radiusKm,
    reason: nearest ? null : coverageAtPoint === 'no_coverage' ? 'no_radar_coverage_at_point' : 'no_echo_within_radius',
  };
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

export async function analyzeAemetNationalRadarGeoTiff(
  bytes: Uint8Array,
  point: GeoPoint,
  options: { radiusKm?: number; minDbz?: number } = {},
): Promise<{ inspection: RadarGeoTiffInspection; observation: RadarSpatialObservation }> {
  const inspection = await inspectRadarGeoTiff(bytes);
  if (!inspection.analysisReady || inspection.scaleSource !== 'aemet-national-reflectivity-palette-v1') {
    return {
      inspection,
      observation: {
        analysisReady: false,
        coverageAtPoint: 'unknown',
        precipitationDetected: false,
        pointBand: null,
        nearestEchoDistanceKm: null,
        nearestEchoBearingDeg: null,
        nearestEchoDirection: null,
        nearestEchoBand: null,
        strongestBandWithinRadius: null,
        searchRadiusKm: Math.min(250, Math.max(1, options.radiusKm ?? 80)),
        reason: 'geotiff_not_analysis_ready',
      },
    };
  }

  const tiff = await fromArrayBuffer(toArrayBuffer(bytes));
  const image = await tiff.getImage();
  const raster = await image.readRasters({ interleave: true });
  const values = raster as unknown as ArrayLike<number>;

  return {
    inspection,
    observation: analyzeRadarGrid({
      width: inspection.width!,
      height: inspection.height!,
      bbox: inspection.bbox!,
      values,
      indexBands: inspection.paletteIndexBands,
      noCoverageIndexes: inspection.noCoverageIndexes,
      clearIndexes: inspection.clearIndexes,
    }, point, options),
  };
}
