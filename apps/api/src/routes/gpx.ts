import { createHash } from 'node:crypto';

export type ParsedGpxPoint = {
  latitude: number;
  longitude: number;
  elevationM: number | null;
  distanceM: number;
};

export type ParsedGpxTrack = {
  name: string | null;
  checksum: string;
  geojson: {
    type: 'LineString';
    coordinates: [number, number][];
  };
  points: ParsedGpxPoint[];
  distanceM: number;
  elevationGainM: number | null;
  elevationLossM: number | null;
  minAltitudeM: number | null;
  maxAltitudeM: number | null;
  bbox: [number, number, number, number];
};

const MAX_GPX_BYTES = 5 * 1024 * 1024;
const MAX_TRACK_POINTS = 100_000;
const EARTH_RADIUS_M = 6_371_008.8;

function decodeXmlText(value: string) {
  return value
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'");
}

function attribute(attributes: string, name: string) {
  const match = attributes.match(new RegExp(`\\b${name}\\s*=\\s*["']([^"']+)["']`, 'i'));
  return match?.[1] ?? null;
}

function haversineMeters(a: { latitude: number; longitude: number }, b: { latitude: number; longitude: number }) {
  const toRadians = (degrees: number) => degrees * Math.PI / 180;
  const lat1 = toRadians(a.latitude);
  const lat2 = toRadians(b.latitude);
  const dLat = lat2 - lat1;
  const dLon = toRadians(b.longitude - a.longitude);
  const sinLat = Math.sin(dLat / 2);
  const sinLon = Math.sin(dLon / 2);
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function parseGpxTrack(gpx: string): ParsedGpxTrack {
  const byteLength = Buffer.byteLength(gpx, 'utf8');
  if (byteLength === 0) throw new Error('gpx_empty');
  if (byteLength > MAX_GPX_BYTES) throw new Error('gpx_too_large');
  if (!/<gpx\b/i.test(gpx)) throw new Error('gpx_root_missing');

  const nameMatch = gpx.match(/<trk\b[^>]*>[\s\S]*?<name\b[^>]*>([\s\S]*?)<\/name>/i);
  const name = nameMatch ? decodeXmlText(nameMatch[1].trim()) || null : null;
  const pointRegex = /<trkpt\b([^>]*)>([\s\S]*?)<\/trkpt\s*>/gi;
  const points: ParsedGpxPoint[] = [];
  let match: RegExpExecArray | null;
  let cumulativeDistance = 0;

  while ((match = pointRegex.exec(gpx)) !== null) {
    if (points.length >= MAX_TRACK_POINTS) throw new Error('gpx_too_many_points');
    const latitude = Number(attribute(match[1], 'lat'));
    const longitude = Number(attribute(match[1], 'lon'));
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) throw new Error('gpx_invalid_latitude');
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) throw new Error('gpx_invalid_longitude');

    const elevationMatch = match[2].match(/<ele\b[^>]*>\s*([-+]?\d+(?:\.\d+)?)\s*<\/ele>/i);
    const elevation = elevationMatch ? Number(elevationMatch[1]) : null;
    const elevationM = elevation !== null && Number.isFinite(elevation) ? elevation : null;
    const previous = points.at(-1);
    if (previous) cumulativeDistance += haversineMeters(previous, { latitude, longitude });

    points.push({
      latitude,
      longitude,
      elevationM,
      distanceM: Math.round(cumulativeDistance),
    });
  }

  if (points.length < 2) throw new Error('gpx_track_too_short');

  let minLongitude = Infinity;
  let minLatitude = Infinity;
  let maxLongitude = -Infinity;
  let maxLatitude = -Infinity;
  const elevations = points.flatMap((point) => point.elevationM === null ? [] : [point.elevationM]);
  let elevationGain = 0;
  let elevationLoss = 0;
  let comparableElevationPairs = 0;

  for (let index = 0; index < points.length; index += 1) {
    const point = points[index];
    minLongitude = Math.min(minLongitude, point.longitude);
    maxLongitude = Math.max(maxLongitude, point.longitude);
    minLatitude = Math.min(minLatitude, point.latitude);
    maxLatitude = Math.max(maxLatitude, point.latitude);
    if (index === 0) continue;
    const previous = points[index - 1];
    if (previous.elevationM === null || point.elevationM === null) continue;
    comparableElevationPairs += 1;
    const delta = point.elevationM - previous.elevationM;
    if (delta > 0) elevationGain += delta;
    if (delta < 0) elevationLoss += Math.abs(delta);
  }

  return {
    name,
    checksum: createHash('sha256').update(gpx, 'utf8').digest('hex'),
    geojson: {
      type: 'LineString',
      coordinates: points.map((point) => [point.longitude, point.latitude]),
    },
    points,
    distanceM: Math.round(cumulativeDistance),
    elevationGainM: comparableElevationPairs > 0 ? Math.round(elevationGain) : null,
    elevationLossM: comparableElevationPairs > 0 ? Math.round(elevationLoss) : null,
    minAltitudeM: elevations.length ? Math.round(Math.min(...elevations)) : null,
    maxAltitudeM: elevations.length ? Math.round(Math.max(...elevations)) : null,
    bbox: [minLongitude, minLatitude, maxLongitude, maxLatitude],
  };
}
