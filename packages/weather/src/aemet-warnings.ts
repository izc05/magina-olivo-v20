import { Readable } from 'node:stream';
import { createGunzip } from 'node:zlib';
import * as tar from 'tar-stream';
import { validateAemetDataUrl } from './aemet.js';
import type { WeatherOfficialAlert } from './weather-state.js';

const AEMET_BASE_URL = 'https://opendata.aemet.es/opendata';
const AEMET_WARNING_TIMEOUT_MS = 10_000;
const MAX_ARCHIVE_BYTES = 12 * 1024 * 1024;
const MAX_XML_BYTES = 2 * 1024 * 1024;
const MAX_ARCHIVE_ENTRIES = 256;

export type AemetWarningLevel = 'green' | 'yellow' | 'orange' | 'red' | 'unknown';
export type AemetWarningPolygon = Array<{ latitude: number; longitude: number }>;
export type AemetCapArea = {
  description: string | null;
  polygons: AemetWarningPolygon[];
};
export type AemetCapAlert = {
  id: string | null;
  level: AemetWarningLevel;
  event: string | null;
  headline: string;
  description: string | null;
  onset: string | null;
  expires: string | null;
  areas: AemetCapArea[];
};

type AemetEnvelope = {
  estado?: number;
  descripcion?: string;
  datos?: string;
};

function decodeXml(value: string) {
  return value
    .replace(/^<!\[CDATA\[|\]\]>$/g, '')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&')
    .trim();
}

function tagText(xml: string, tag: string) {
  const match = xml.match(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return match?.[1] ? decodeXml(match[1].replace(/<[^>]+>/g, '')) : null;
}

function blocks(xml: string, tag: string) {
  return Array.from(xml.matchAll(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, 'gi')))
    .map((match) => match[1] ?? '');
}

function spanishInfo(xml: string) {
  const infos = blocks(xml, 'info');
  if (!infos.length) return null;
  return infos.find((info) => /^es(?:-|$)/i.test(tagText(info, 'language') ?? '')) ?? infos[0] ?? null;
}

function parameterValue(info: string, name: string) {
  for (const parameter of blocks(info, 'parameter')) {
    if ((tagText(parameter, 'valueName') ?? '').trim().toLowerCase() === name.toLowerCase()) {
      return tagText(parameter, 'value');
    }
  }
  return null;
}

function normalizeLevel(value: string | null): AemetWarningLevel {
  const level = (value ?? '').trim().toLowerCase();
  if (level.includes('verde')) return 'green';
  if (level.includes('amarillo')) return 'yellow';
  if (level.includes('naranja')) return 'orange';
  if (level.includes('rojo')) return 'red';
  return 'unknown';
}

function parsePolygon(value: string): AemetWarningPolygon | null {
  const points = value.trim().split(/\s+/).map((pair) => {
    const [latitudeText, longitudeText] = pair.split(',');
    const latitude = Number(latitudeText);
    const longitude = Number(longitudeText);
    return { latitude, longitude };
  }).filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
  return points.length >= 3 ? points : null;
}

function parseAreas(info: string): AemetCapArea[] {
  return blocks(info, 'area').map((area) => ({
    description: tagText(area, 'areaDesc'),
    polygons: blocks(area, 'polygon').map((polygon) => parsePolygon(decodeXml(polygon))).filter((polygon): polygon is AemetWarningPolygon => polygon !== null),
  }));
}

export function parseAemetCapAlert(xml: string): AemetCapAlert | null {
  const info = spanishInfo(xml);
  if (!info) return null;
  const headline = tagText(info, 'headline') ?? tagText(info, 'event');
  if (!headline) return null;
  return {
    id: tagText(xml, 'identifier'),
    level: normalizeLevel(parameterValue(info, 'AEMET-Meteoalerta nivel')),
    event: tagText(info, 'event'),
    headline,
    description: tagText(info, 'description'),
    onset: tagText(info, 'onset'),
    expires: tagText(info, 'expires'),
    areas: parseAreas(info),
  };
}

function pointInPolygon(latitude: number, longitude: number, polygon: AemetWarningPolygon) {
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const current = polygon[i]!;
    const previous = polygon[j]!;
    const intersects = ((current.latitude > latitude) !== (previous.latitude > latitude))
      && (longitude < ((previous.longitude - current.longitude) * (latitude - current.latitude)) / ((previous.latitude - current.latitude) || Number.EPSILON) + current.longitude);
    if (intersects) inside = !inside;
  }
  return inside;
}

function alertCoversCoordinates(alert: AemetCapAlert, latitude: number, longitude: number) {
  return alert.areas.some((area) => area.polygons.some((polygon) => pointInPolygon(latitude, longitude, polygon)));
}

function officialLevel(level: AemetWarningLevel): NonNullable<WeatherOfficialAlert>['level'] | null {
  if (level === 'yellow') return 'yellow';
  if (level === 'orange') return 'orange';
  if (level === 'red') return 'red';
  if (level === 'unknown') return 'unknown';
  return null;
}

function priority(level: NonNullable<WeatherOfficialAlert>['level']) {
  return level === 'red' ? 4 : level === 'orange' ? 3 : level === 'yellow' ? 2 : 1;
}

export function selectAemetOfficialAlertForCoordinates(
  documents: string[],
  latitude: number,
  longitude: number,
): WeatherOfficialAlert {
  const candidates = documents
    .map(parseAemetCapAlert)
    .filter((alert): alert is AemetCapAlert => alert !== null)
    .filter((alert) => alert.level !== 'green' && alertCoversCoordinates(alert, latitude, longitude))
    .map((alert) => {
      const level = officialLevel(alert.level);
      if (!level) return null;
      return {
        source: 'AEMET' as const,
        level,
        title: alert.headline,
        description: alert.description,
        startsAt: alert.onset,
        endsAt: alert.expires,
      };
    })
    .filter((alert): alert is NonNullable<WeatherOfficialAlert> => alert !== null)
    .sort((left, right) => priority(right.level) - priority(left.level));
  return candidates[0] ?? null;
}

export function buildAemetLatestWarningsUrl(area = '61') {
  if (!/^(?:esp|6[1-9]|7[0-9])$/.test(area)) throw new Error('INVALID_AEMET_WARNING_AREA');
  return `${AEMET_BASE_URL}/api/avisos_cap/ultimoelaborado/area/${encodeURIComponent(area)}`;
}

async function archiveXmlDocuments(bytes: Uint8Array) {
  if (!bytes.byteLength) throw new Error('AEMET_WARNING_ARCHIVE_EMPTY');
  if (bytes.byteLength > MAX_ARCHIVE_BYTES) throw new Error('AEMET_WARNING_ARCHIVE_TOO_LARGE');
  const gzipped = bytes[0] === 0x1f && bytes[1] === 0x8b;

  return new Promise<string[]>((resolve, reject) => {
    const extractor = tar.extract();
    const documents: string[] = [];
    let entries = 0;
    let settled = false;

    const fail = (error: Error) => {
      if (settled) return;
      settled = true;
      extractor.destroy();
      reject(error);
    };

    extractor.on('error', (error) => fail(error));
    extractor.on('entry', (header, stream, next) => {
      entries += 1;
      if (entries > MAX_ARCHIVE_ENTRIES) {
        stream.resume();
        stream.on('end', () => fail(new Error('AEMET_WARNING_ARCHIVE_TOO_MANY_ENTRIES')));
        return;
      }
      if (header.type !== 'file' || !/\.xml$/i.test(header.name)) {
        stream.resume();
        stream.on('end', next);
        return;
      }
      const chunks: Buffer[] = [];
      let size = 0;
      stream.on('data', (chunk: Buffer) => {
        size += chunk.byteLength;
        if (size > MAX_XML_BYTES) {
          fail(new Error('AEMET_WARNING_XML_TOO_LARGE'));
          return;
        }
        chunks.push(chunk);
      });
      stream.on('error', (error) => fail(error));
      stream.on('end', () => {
        if (settled) return;
        documents.push(Buffer.concat(chunks).toString('utf8'));
        next();
      });
    });
    extractor.on('finish', () => {
      if (settled) return;
      settled = true;
      resolve(documents);
    });

    const source = Readable.from(Buffer.from(bytes));
    if (gzipped) {
      const gunzip = createGunzip();
      gunzip.on('error', (error) => fail(error));
      source.pipe(gunzip).pipe(extractor);
    } else {
      source.pipe(extractor);
    }
  });
}

async function fetchJson(url: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(AEMET_WARNING_TIMEOUT_MS) });
  if (!response.ok) throw new Error(`AEMET_WARNING_REQUEST_FAILED:${response.status}`);
  return response.json() as Promise<unknown>;
}

export async function fetchAemetOfficialAlertForCoordinates(
  latitude: number,
  longitude: number,
  area = '61',
): Promise<WeatherOfficialAlert> {
  const apiKey = process.env.AEMET_API_KEY?.trim();
  if (!apiKey) return null;

  const envelope = await fetchJson(buildAemetLatestWarningsUrl(area), {
    headers: {
      api_key: apiKey,
      accept: 'application/json',
      'user-agent': 'Magina-Olivo-V20/1.0 (+AEMET-warning-adapter)',
    },
  }) as AemetEnvelope;
  if (!envelope.datos || typeof envelope.datos !== 'string') return null;

  const dataUrl = validateAemetDataUrl(envelope.datos);
  const response = await fetch(dataUrl, {
    headers: { accept: 'application/octet-stream' },
    signal: AbortSignal.timeout(AEMET_WARNING_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`AEMET_WARNING_DATA_FAILED:${response.status}`);
  const bytes = new Uint8Array(await response.arrayBuffer());
  const documents = await archiveXmlDocuments(bytes);
  return selectAemetOfficialAlertForCoordinates(documents, latitude, longitude);
}
