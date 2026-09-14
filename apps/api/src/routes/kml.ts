import { createHash } from 'node:crypto';

export type ParsedKmlPoint = {
  latitude: number;
  longitude: number;
  elevationM: number | null;
  distanceM: number;
};

export type ParsedKmlTrack = {
  name: string | null;
  checksum: string;
  geojson: {
    type: 'LineString';
    coordinates: [number, number][];
  };
  points: ParsedKmlPoint[];
  distanceM: number;
  elevationGainM: number | null;
  elevationLossM: number | null;
  minAltitudeM: number | null;
  maxAltitudeM: number | null;
  bbox: [number, number, number, number];
};

const MAX_KML_BYTES = 5 * 1024 * 1024;
const MAX_TRACK_POINTS = 100_000;
const EARTH_RADIUS_M = 6_371_008.8;

function decodeXmlText(value: string) {
  return value
    .replace(/^<!\[CDATA\[([\s\S]*)\]\]>$/i, '$1')
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'");
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

function placemarkName(kml: string) {
  const placemark = kml.match(/<Placemark\b[^>]*>([\s\S]*?)<\/Placemark\s*>/i)?.[1] ?? kml;
  const name = placemark.match(/<name\b[^>]*>([\s\S]*?)<\/name\s*>/i)?.[1]?.trim();
  return name ? decodeXmlText(name) || null : null;
}

export function parseKmlTrack(kml: string): ParsedKmlTrack {
  const byteLength = Buffer.byteLength(kml, 'utf8');
  if (byteLength === 0) throw new Error('kml_empty');
  if (byteLength > MAX_KML_BYTES) throw new Error('kml_too_large');
  if (!/<kml\b/i.test(kml)) throw new Error('kml_root_missing');

  const lineStrings = [...kml.matchAll(/<LineString\b[^>]*>([\s\S]*?)<\/LineString\s*>/gi)];
  if (lineStrings.length === 0) throw new Error('kml_linestring_missing');
  if (lineStrings.length > 1) throw new Error('kml_multiple_linestrings_unsupported');

  const coordinatesText = lineStrings[0][1].match(/<coordinates\b[^>]*>([\s\S]*?)<\/coordinates\s*>/i)?.[1]?.trim();
  if (!coordinatesText) throw new Error('kml_coordinates_missing');

  const tuples = coordinatesText.split(/\s+/).filter(Boolean);
  if (tuples.length > MAX_TRACK_POINTS) throw new Error('kml_too_many_points');
  if (tuples.length < 2) throw new Error('kml_track_too_short');

  const points: ParsedKmlPoint[] = [];
  let cumulativeDistance = 0;

  for (const tuple of tuples) {
    const parts = tuple.split(',');
    if (parts.length < 2) throw new Error('kml_invalid_coordinate');
    const longitude = Number(parts[0]);
    const latitude = Number(parts[1]);
    const altitudeRaw = parts[2]?.trim();
    const altitude = altitudeRaw === undefined || altitudeRaw === '' ? null : Number(altitudeRaw);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) throw new Error('kml_invalid_latitude');
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) throw new Error('kml_invalid_longitude');
    if (altitude !== null && !Number.isFinite(altitude)) throw new Error('kml_invalid_altitude');

    const previous = points.at(-1);
    if (previous) cumulativeDistance += haversineMeters(previous, { latitude, longitude });
    points.push({
      latitude,
      longitude,
      elevationM: altitude,
      distanceM: Math.round(cumulativeDistance),
    });
  }

  let minLongitude = Infinity;
  let minLatitude = Infinity;
  let maxLongitude = -Infinity;
  let maxLatitude = -Infinity;
  let elevationGain = 0;
  let elevationLoss = 0;
  let comparableElevationPairs = 0;
  const elevations = points.flatMap((point) => point.elevationM === null ? [] : [point.elevationM]);

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
    name: placemarkName(kml),
    checksum: createHash('sha256').update(kml, 'utf8').digest('hex'),
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
