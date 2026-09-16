import { createHash } from 'node:crypto';
import {
  normalizeRouteAsset,
  type NormalizeRouteAssetInput,
  type NormalizedRouteGeometry,
} from './normalize.js';

export type RouteGeometryIngestionRecommendation = 'ready_for_review' | 'distance_mismatch';

export type PrepareRouteGeometryIngestionInput = NormalizeRouteAssetInput & {
  publishedDistanceM?: number | null;
  maxDistanceDeltaPercent?: number;
};

export type PreparedRouteGeometryIngestion = {
  normalized: NormalizedRouteGeometry;
  sourceSha256: string;
  sourceSizeBytes: number;
  bbox: [number, number, number, number];
  distanceDeltaPercent: number | null;
  recommendation: RouteGeometryIngestionRecommendation;
};

function asBytes(content: string | Uint8Array) {
  return typeof content === 'string' ? new TextEncoder().encode(content) : content;
}

function calculateBbox(geometry: NormalizedRouteGeometry): [number, number, number, number] {
  let minLongitude = Number.POSITIVE_INFINITY;
  let minLatitude = Number.POSITIVE_INFINITY;
  let maxLongitude = Number.NEGATIVE_INFINITY;
  let maxLatitude = Number.NEGATIVE_INFINITY;

  for (const [longitude, latitude] of geometry.geometry.coordinates) {
    minLongitude = Math.min(minLongitude, longitude);
    minLatitude = Math.min(minLatitude, latitude);
    maxLongitude = Math.max(maxLongitude, longitude);
    maxLatitude = Math.max(maxLatitude, latitude);
  }

  if (!Number.isFinite(minLongitude) || !Number.isFinite(minLatitude)
    || !Number.isFinite(maxLongitude) || !Number.isFinite(maxLatitude)) {
    throw new Error('route_geometry_bbox_unavailable');
  }
  return [minLongitude, minLatitude, maxLongitude, maxLatitude];
}

export function prepareRouteGeometryIngestion(
  input: PrepareRouteGeometryIngestionInput,
): PreparedRouteGeometryIngestion {
  const bytes = asBytes(input.content);
  const normalized = normalizeRouteAsset(input);
  const sourceSha256 = createHash('sha256').update(bytes).digest('hex');
  const publishedDistanceM = input.publishedDistanceM ?? null;
  const maxDistanceDeltaPercent = input.maxDistanceDeltaPercent ?? 15;

  if (!Number.isFinite(maxDistanceDeltaPercent) || maxDistanceDeltaPercent < 0) {
    throw new Error('invalid_route_distance_delta_threshold');
  }
  if (publishedDistanceM !== null && (!Number.isFinite(publishedDistanceM) || publishedDistanceM <= 0)) {
    throw new Error('invalid_published_route_distance');
  }

  const distanceDeltaPercent = publishedDistanceM === null
    ? null
    : Math.abs(normalized.distanceM - publishedDistanceM) / publishedDistanceM * 100;

  return {
    normalized,
    sourceSha256,
    sourceSizeBytes: bytes.byteLength,
    bbox: calculateBbox(normalized),
    distanceDeltaPercent,
    recommendation: distanceDeltaPercent !== null && distanceDeltaPercent > maxDistanceDeltaPercent
      ? 'distance_mismatch'
      : 'ready_for_review',
  };
}
