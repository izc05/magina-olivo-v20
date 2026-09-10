import { Readable } from 'node:stream';
import { createGunzip } from 'node:zlib';
import * as tar from 'tar-stream';
import type { RadarBinaryAsset } from './radar.js';

const AEMET_RADAR_GEOTIFF_BUNDLE_URL = 'https://www.aemet.es/es/api-eltiempo/radar/download/compo';
const AEMET_RADAR_GEOTIFF_HOST = 'www.aemet.es';
const RADAR_GEOTIFF_TIMEOUT_MS = 15_000;
const MAX_COMPRESSED_BYTES = 50 * 1024 * 1024;
const MAX_UNCOMPRESSED_BYTES = 100 * 1024 * 1024;
const MAX_TIFF_BYTES = 35 * 1024 * 1024;
const MAX_TIFF_ENTRIES = 6;

export function buildAemetNationalRadarGeoTiffBundleUrl() {
  return AEMET_RADAR_GEOTIFF_BUNDLE_URL;
}

export function validateAemetRadarGeoTiffBundleUrl(value: string) {
  const url = new URL(value);
  if (
    url.protocol !== 'https:'
    || url.hostname !== AEMET_RADAR_GEOTIFF_HOST
    || url.pathname !== '/es/api-eltiempo/radar/download/compo'
  ) {
    throw new Error('AEMET_RADAR_GEOTIFF_URL_NOT_TRUSTED');
  }
  return url;
}

function isGeoTiff(bytes: Uint8Array) {
  if (bytes.byteLength < 4) return false;
  const littleEndian = bytes[0] === 0x49 && bytes[1] === 0x49 && bytes[2] === 0x2a && bytes[3] === 0x00;
  const bigEndian = bytes[0] === 0x4d && bytes[1] === 0x4d && bytes[2] === 0x00 && bytes[3] === 0x2a;
  return littleEndian || bigEndian;
}

export function parseRadarObservationTimestampFromName(name: string): string | null {
  const match = name.match(/(20\d{2})[-_]?([01]\d)[-_]?([0-3]\d)[T_\-]?([0-2]\d)[:_\-]?([0-5]\d)(?:[:_\-]?([0-5]\d))?/);
  if (!match) return null;

  const [, yearText, monthText, dayText, hourText, minuteText, secondText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText ?? '0');
  const value = new Date(Date.UTC(year, month - 1, day, hour, minute, second));

  if (
    value.getUTCFullYear() !== year
    || value.getUTCMonth() !== month - 1
    || value.getUTCDate() !== day
    || value.getUTCHours() !== hour
    || value.getUTCMinutes() !== minute
    || value.getUTCSeconds() !== second
  ) return null;

  return value.toISOString();
}

export async function parseAemetRadarGeoTiffBundle(
  compressed: Uint8Array,
  fetchedAt = new Date(),
  sourceUrl = AEMET_RADAR_GEOTIFF_BUNDLE_URL,
): Promise<RadarBinaryAsset[]> {
  if (compressed.byteLength === 0) throw new Error('AEMET_RADAR_GEOTIFF_BUNDLE_EMPTY');
  if (compressed.byteLength > MAX_COMPRESSED_BYTES) throw new Error('AEMET_RADAR_GEOTIFF_BUNDLE_TOO_LARGE');
  validateAemetRadarGeoTiffBundleUrl(sourceUrl);

  return new Promise<RadarBinaryAsset[]>((resolve, reject) => {
    const extractor = tar.extract();
    const gunzip = createGunzip();
    const assets: RadarBinaryAsset[] = [];
    let uncompressedBytes = 0;
    let settled = false;

    const fail = (error: Error) => {
      if (settled) return;
      settled = true;
      gunzip.destroy();
      extractor.destroy();
      reject(error);
    };

    gunzip.on('data', (chunk: Buffer) => {
      uncompressedBytes += chunk.byteLength;
      if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
        fail(new Error('AEMET_RADAR_GEOTIFF_UNCOMPRESSED_TOO_LARGE'));
      }
    });
    gunzip.on('error', (error) => fail(error));
    extractor.on('error', (error) => fail(error));

    extractor.on('entry', (header, stream, next) => {
      const lowerName = header.name.toLowerCase();
      const isTiffEntry = header.type === 'file' && (lowerName.endsWith('.tif') || lowerName.endsWith('.tiff'));
      if (!isTiffEntry) {
        stream.resume();
        stream.on('end', next);
        return;
      }

      if (assets.length >= MAX_TIFF_ENTRIES) {
        stream.resume();
        stream.on('end', () => fail(new Error('AEMET_RADAR_GEOTIFF_TOO_MANY_ENTRIES')));
        return;
      }

      const chunks: Buffer[] = [];
      let entryBytes = 0;
      stream.on('data', (chunk: Buffer) => {
        entryBytes += chunk.byteLength;
        if (entryBytes > MAX_TIFF_BYTES) {
          fail(new Error('AEMET_RADAR_GEOTIFF_ENTRY_TOO_LARGE'));
          return;
        }
        chunks.push(chunk);
      });
      stream.on('error', (error) => fail(error));
      stream.on('end', () => {
        if (settled) return;
        const bytes = new Uint8Array(Buffer.concat(chunks));
        if (!isGeoTiff(bytes)) {
          fail(new Error(`AEMET_RADAR_GEOTIFF_INVALID_SIGNATURE:${header.name}`));
          return;
        }
        assets.push({
          metadata: {
            source: 'aemet_national_mosaic',
            product: 'reflectivity',
            crs: 'EPSG:4326',
            observed_at: parseRadarObservationTimestampFromName(header.name),
            fetched_at: fetchedAt.toISOString(),
            asset_format: 'geotiff',
            analysis_ready: true,
          },
          bytes,
          contentType: 'image/tiff',
          sourceUrl,
          sourceName: header.name,
        });
        next();
      });
    });

    extractor.on('finish', () => {
      if (settled) return;
      if (assets.length === 0) {
        fail(new Error('AEMET_RADAR_GEOTIFF_NO_TIFF_ENTRIES'));
        return;
      }
      settled = true;
      resolve(assets);
    });

    Readable.from([Buffer.from(compressed)]).pipe(gunzip).pipe(extractor);
  });
}

export async function fetchAemetNationalRadarGeoTiffs(): Promise<RadarBinaryAsset[]> {
  const url = validateAemetRadarGeoTiffBundleUrl(buildAemetNationalRadarGeoTiffBundleUrl());
  const response = await fetch(url, {
    headers: {
      accept: 'application/tar+gzip, application/gzip, application/octet-stream',
      'user-agent': 'Magina-Olivo/20 (+AEMET-geotiff-radar-adapter)',
    },
    signal: AbortSignal.timeout(RADAR_GEOTIFF_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`AEMET_RADAR_GEOTIFF_HTTP_${response.status}`);

  const contentType = (response.headers.get('content-type') ?? '').split(';', 1)[0]?.trim().toLowerCase();
  if (!['application/tar+gzip', 'application/gzip', 'application/x-gzip', 'application/octet-stream'].includes(contentType)) {
    throw new Error(`AEMET_RADAR_GEOTIFF_UNEXPECTED_CONTENT_TYPE:${contentType || 'missing'}`);
  }

  const declaredSize = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredSize) && declaredSize > MAX_COMPRESSED_BYTES) {
    throw new Error('AEMET_RADAR_GEOTIFF_BUNDLE_TOO_LARGE');
  }

  const compressed = new Uint8Array(await response.arrayBuffer());
  return parseAemetRadarGeoTiffBundle(compressed, new Date(), url.toString());
}
