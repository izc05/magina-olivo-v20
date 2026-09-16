export type RouteGeometryFormat = 'kml' | 'gml' | 'kmz';
export type SupportedRouteCrs = 'EPSG:4326' | 'EPSG:25830';

type Position2D = [number, number];

type LineStringGeometry = {
  type: 'LineString';
  coordinates: Position2D[];
};

export type NormalizedRouteGeometry = {
  geometry: LineStringGeometry;
  start: Position2D;
  end: Position2D;
  distanceM: number;
  altitudeMinM: number | null;
  altitudeMaxM: number | null;
};

export type NormalizeRouteGeometryInput = {
  format: RouteGeometryFormat;
  content: string;
  sourceCrs?: SupportedRouteCrs;
};

type Position3D = [number, number, number | null];

const EARTH_RADIUS_M = 6_371_008.8;

function radians(value: number) {
  return (value * Math.PI) / 180;
}

function haversineDistanceM(a: Position2D, b: Position2D) {
  const lat1 = radians(a[1]);
  const lat2 = radians(b[1]);
  const dLat = lat2 - lat1;
  const dLon = radians(b[0] - a[0]);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

function parseFinite(value: string, label: string) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error(`invalid_${label}`);
  return parsed;
}

function assertWgs84([longitude, latitude]: Position2D) {
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
    throw new Error('route_geometry_coordinate_out_of_range');
  }
}

function parseKmlCoordinates(content: string): Position3D[] {
  const matches = [...content.matchAll(/<coordinates(?:\s[^>]*)?>([\s\S]*?)<\/coordinates>/gi)];
  const raw = matches.flatMap((match) => match[1].trim().split(/\s+/).filter(Boolean));
  if (raw.length < 2) throw new Error('route_geometry_requires_two_points');

  return raw.map((tuple) => {
    const [longitudeText, latitudeText, altitudeText] = tuple.split(',');
    if (longitudeText === undefined || latitudeText === undefined) {
      throw new Error('invalid_kml_coordinate');
    }
    const longitude = parseFinite(longitudeText, 'longitude');
    const latitude = parseFinite(latitudeText, 'latitude');
    const altitude = altitudeText === undefined || altitudeText === ''
      ? null
      : parseFinite(altitudeText, 'altitude');
    assertWgs84([longitude, latitude]);
    return [longitude, latitude, altitude];
  });
}

function parseGmlCoordinates(content: string, sourceCrs: SupportedRouteCrs): Position3D[] {
  if (sourceCrs !== 'EPSG:4326') {
    throw new Error(`unsupported_route_geometry_crs:${sourceCrs}`);
  }

  const posLists = [...content.matchAll(/<(?:(?:\w+):)?posList([^>]*)>([\s\S]*?)<\/(?:(?:\w+):)?posList>/gi)];
  if (posLists.length === 0) throw new Error('gml_pos_list_required');

  const points: Position3D[] = [];
  for (const match of posLists) {
    const attributes = match[1] ?? '';
    const numbers = (match[2] ?? '').trim().split(/\s+/).filter(Boolean)
      .map((value) => parseFinite(value, 'gml_coordinate'));
    const dimensionMatch = attributes.match(/srsDimension\s*=\s*["'](\d+)["']/i)
      ?? content.match(/srsDimension\s*=\s*["'](\d+)["']/i);
    const dimension = dimensionMatch ? Number(dimensionMatch[1]) : 2;
    if (dimension !== 2 && dimension !== 3) throw new Error('unsupported_gml_dimension');
    if (numbers.length < dimension * 2 || numbers.length % dimension !== 0) {
      throw new Error('invalid_gml_pos_list');
    }

    const axisLabels = (attributes.match(/axisLabels\s*=\s*["']([^"']+)["']/i)
      ?? content.match(/axisLabels\s*=\s*["']([^"']+)["']/i))?.[1]?.toLowerCase() ?? '';
    const longitudeFirst = axisLabels.startsWith('long') || axisLabels.startsWith('lon');

    for (let index = 0; index < numbers.length; index += dimension) {
      const first = numbers[index]!;
      const second = numbers[index + 1]!;
      const longitude = longitudeFirst ? first : second;
      const latitude = longitudeFirst ? second : first;
      const altitude = dimension === 3 ? numbers[index + 2]! : null;
      assertWgs84([longitude, latitude]);
      points.push([longitude, latitude, altitude]);
    }
  }

  if (points.length < 2) throw new Error('route_geometry_requires_two_points');
  return points;
}

function buildResult(points: Position3D[]): NormalizedRouteGeometry {
  const coordinates: Position2D[] = points.map(([longitude, latitude]) => [longitude, latitude]);
  let distanceM = 0;
  for (let index = 1; index < coordinates.length; index += 1) {
    distanceM += haversineDistanceM(coordinates[index - 1]!, coordinates[index]!);
  }

  const altitudes = points
    .map(([, , altitude]) => altitude)
    .filter((altitude): altitude is number => altitude !== null);

  return {
    geometry: { type: 'LineString', coordinates },
    start: coordinates[0]!,
    end: coordinates[coordinates.length - 1]!,
    distanceM,
    altitudeMinM: altitudes.length > 0 ? Math.min(...altitudes) : null,
    altitudeMaxM: altitudes.length > 0 ? Math.max(...altitudes) : null,
  };
}

export function normalizeRouteGeometry(input: NormalizeRouteGeometryInput): NormalizedRouteGeometry {
  const sourceCrs = input.sourceCrs ?? 'EPSG:4326';
  if (input.format === 'kml') {
    if (sourceCrs !== 'EPSG:4326') {
      throw new Error(`unsupported_route_geometry_crs:${sourceCrs}`);
    }
    return buildResult(parseKmlCoordinates(input.content));
  }
  if (input.format === 'gml') {
    return buildResult(parseGmlCoordinates(input.content, sourceCrs));
  }
  throw new Error(`unsupported_route_geometry_format:${input.format}`);
}
