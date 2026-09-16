import { inflateRawSync } from 'node:zlib';

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
  format: Exclude<RouteGeometryFormat, 'kmz'>;
  content: string;
  sourceCrs?: SupportedRouteCrs;
};

export type NormalizeRouteAssetInput = {
  format: RouteGeometryFormat;
  content: string | Uint8Array;
  sourceCrs?: SupportedRouteCrs;
};

type Position3D = [number, number, number | null];

const EARTH_RADIUS_M = 6_371_008.8;
const ZIP_EOCD_SIGNATURE = 0x06054b50;
const ZIP_CENTRAL_SIGNATURE = 0x02014b50;
const ZIP_LOCAL_SIGNATURE = 0x04034b50;
const MAX_KMZ_ENTRIES = 256;
const MAX_KML_BYTES = 20 * 1024 * 1024;

const GRS80_A = 6_378_137;
const GRS80_F = 1 / 298.257222101;
const UTM_K0 = 0.9996;
const UTM_ZONE_30_CENTRAL_MERIDIAN_RAD = (-3 * Math.PI) / 180;

function radians(value: number) {
  return (value * Math.PI) / 180;
}

function degrees(value: number) {
  return (value * 180) / Math.PI;
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

function etrs89Utm30ToWgs84(easting: number, northing: number): Position2D {
  if (easting < 100_000 || easting > 900_000 || northing < 0 || northing > 10_000_000) {
    throw new Error('route_geometry_utm_coordinate_out_of_range');
  }

  const eccentricitySquared = GRS80_F * (2 - GRS80_F);
  const secondEccentricitySquared = eccentricitySquared / (1 - eccentricitySquared);
  const x = easting - 500_000;
  const meridionalArc = northing / UTM_K0;
  const mu = meridionalArc / (GRS80_A * (
    1
    - eccentricitySquared / 4
    - (3 * eccentricitySquared ** 2) / 64
    - (5 * eccentricitySquared ** 3) / 256
  ));

  const e1 = (1 - Math.sqrt(1 - eccentricitySquared)) / (1 + Math.sqrt(1 - eccentricitySquared));
  const footprintLatitude = mu
    + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * Math.sin(2 * mu)
    + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * Math.sin(4 * mu)
    + (151 * e1 ** 3 / 96) * Math.sin(6 * mu)
    + (1097 * e1 ** 4 / 512) * Math.sin(8 * mu);

  const sinFootprint = Math.sin(footprintLatitude);
  const cosFootprint = Math.cos(footprintLatitude);
  const tanFootprint = Math.tan(footprintLatitude);
  const n1 = GRS80_A / Math.sqrt(1 - eccentricitySquared * sinFootprint ** 2);
  const r1 = GRS80_A * (1 - eccentricitySquared)
    / (1 - eccentricitySquared * sinFootprint ** 2) ** 1.5;
  const t1 = tanFootprint ** 2;
  const c1 = secondEccentricitySquared * cosFootprint ** 2;
  const d = x / (n1 * UTM_K0);

  const latitude = footprintLatitude - (n1 * tanFootprint / r1) * (
    d ** 2 / 2
    - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * secondEccentricitySquared) * d ** 4 / 24
    + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * secondEccentricitySquared - 3 * c1 ** 2)
      * d ** 6 / 720
  );
  const longitude = UTM_ZONE_30_CENTRAL_MERIDIAN_RAD + (
    d
    - (1 + 2 * t1 + c1) * d ** 3 / 6
    + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * secondEccentricitySquared + 24 * t1 ** 2)
      * d ** 5 / 120
  ) / cosFootprint;

  const result: Position2D = [degrees(longitude), degrees(latitude)];
  assertWgs84(result);
  return result;
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
      const altitude = dimension === 3 ? numbers[index + 2]! : null;
      let longitude: number;
      let latitude: number;

      if (sourceCrs === 'EPSG:25830') {
        [longitude, latitude] = etrs89Utm30ToWgs84(first, second);
      } else {
        longitude = longitudeFirst ? first : second;
        latitude = longitudeFirst ? second : first;
        assertWgs84([longitude, latitude]);
      }
      points.push([longitude, latitude, altitude]);
    }
  }

  if (points.length < 2) throw new Error('route_geometry_requires_two_points');
  return points;
}

function findEndOfCentralDirectory(bytes: Uint8Array) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const minimumOffset = Math.max(0, bytes.byteLength - 65_557);
  for (let offset = bytes.byteLength - 22; offset >= minimumOffset; offset -= 1) {
    if (view.getUint32(offset, true) === ZIP_EOCD_SIGNATURE) return offset;
  }
  throw new Error('invalid_kmz_end_of_central_directory');
}

function extractKmlFromKmz(content: Uint8Array) {
  if (content.byteLength < 22) throw new Error('invalid_kmz_archive');
  const view = new DataView(content.buffer, content.byteOffset, content.byteLength);
  const eocdOffset = findEndOfCentralDirectory(content);
  const entryCount = view.getUint16(eocdOffset + 10, true);
  const centralOffset = view.getUint32(eocdOffset + 16, true);
  if (entryCount < 1 || entryCount > MAX_KMZ_ENTRIES) throw new Error('invalid_kmz_entry_count');

  let cursor = centralOffset;
  const decoder = new TextDecoder('utf-8');
  for (let entryIndex = 0; entryIndex < entryCount; entryIndex += 1) {
    if (cursor + 46 > content.byteLength || view.getUint32(cursor, true) !== ZIP_CENTRAL_SIGNATURE) {
      throw new Error('invalid_kmz_central_directory');
    }
    const compressionMethod = view.getUint16(cursor + 10, true);
    const compressedSize = view.getUint32(cursor + 20, true);
    const uncompressedSize = view.getUint32(cursor + 24, true);
    const fileNameLength = view.getUint16(cursor + 28, true);
    const extraLength = view.getUint16(cursor + 30, true);
    const commentLength = view.getUint16(cursor + 32, true);
    const localHeaderOffset = view.getUint32(cursor + 42, true);
    const fileNameStart = cursor + 46;
    const fileNameEnd = fileNameStart + fileNameLength;
    if (fileNameEnd > content.byteLength) throw new Error('invalid_kmz_filename');
    const fileName = decoder.decode(content.subarray(fileNameStart, fileNameEnd));

    if (fileName.toLowerCase().endsWith('.kml')) {
      if (uncompressedSize > MAX_KML_BYTES) throw new Error('kmz_kml_too_large');
      if (localHeaderOffset + 30 > content.byteLength
        || view.getUint32(localHeaderOffset, true) !== ZIP_LOCAL_SIGNATURE) {
        throw new Error('invalid_kmz_local_header');
      }
      const localNameLength = view.getUint16(localHeaderOffset + 26, true);
      const localExtraLength = view.getUint16(localHeaderOffset + 28, true);
      const dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength;
      const dataEnd = dataStart + compressedSize;
      if (dataEnd > content.byteLength) throw new Error('invalid_kmz_entry_data');
      const compressed = content.subarray(dataStart, dataEnd);

      let kmlBytes: Uint8Array;
      if (compressionMethod === 0) {
        kmlBytes = compressed;
      } else if (compressionMethod === 8) {
        kmlBytes = inflateRawSync(compressed);
      } else {
        throw new Error(`unsupported_kmz_compression:${compressionMethod}`);
      }
      if (kmlBytes.byteLength > MAX_KML_BYTES) throw new Error('kmz_kml_too_large');
      return decoder.decode(kmlBytes);
    }

    cursor = fileNameEnd + extraLength + commentLength;
  }

  throw new Error('kmz_kml_entry_required');
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
  return buildResult(parseGmlCoordinates(input.content, sourceCrs));
}

export function normalizeRouteAsset(input: NormalizeRouteAssetInput): NormalizedRouteGeometry {
  if (input.format === 'kmz') {
    if (typeof input.content === 'string') throw new Error('kmz_binary_content_required');
    return normalizeRouteGeometry({
      format: 'kml',
      content: extractKmlFromKmz(input.content),
      sourceCrs: input.sourceCrs,
    });
  }
  if (typeof input.content !== 'string') throw new Error(`${input.format}_text_content_required`);
  return normalizeRouteGeometry({
    format: input.format,
    content: input.content,
    sourceCrs: input.sourceCrs,
  });
}
