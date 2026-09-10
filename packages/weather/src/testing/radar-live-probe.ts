import { createHash } from 'node:crypto';
import { fromArrayBuffer } from 'geotiff';
import {
  fetchAemetNationalRadarGeoTiffs,
  inspectRadarGeoTiff,
} from '../index.js';

function endian(bytes: Uint8Array) {
  if (bytes[0] === 0x49 && bytes[1] === 0x49) return 'little';
  if (bytes[0] === 0x4d && bytes[1] === 0x4d) return 'big';
  return 'unknown';
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

type FileDirectoryProbe = {
  hasTag: (tag: string) => boolean;
  getValue: (tag: string) => unknown;
  loadValue: (tag: string) => Promise<unknown>;
};

function summarizeArrayLike(value: unknown, maxItems = 24) {
  if (value == null || typeof value === 'string') return value;
  if (!ArrayBuffer.isView(value) && !Array.isArray(value)) return value;
  const array = Array.from(value as ArrayLike<number>);
  return {
    length: array.length,
    preview: array.slice(0, maxItems),
  };
}

async function loadTag(directory: FileDirectoryProbe, tag: string) {
  try {
    if (!directory.hasTag(tag)) return null;
    return summarizeArrayLike(await directory.loadValue(tag));
  } catch {
    try {
      return summarizeArrayLike(directory.getValue(tag));
    } catch {
      return 'unreadable';
    }
  }
}

async function paletteSummary(directory: FileDirectoryProbe) {
  if (!directory.hasTag('ColorMap')) return null;
  const raw = await directory.loadValue('ColorMap');
  if (!ArrayBuffer.isView(raw) && !Array.isArray(raw)) return null;

  const values = Array.from(raw as ArrayLike<number>, Number);
  if (values.length === 0 || values.length % 3 !== 0) return null;
  const entries = values.length / 3;

  const normalize = (value: number) => Math.max(0, Math.min(255, Math.round(value / 257)));
  const palette = Array.from({ length: entries }, (_, index) => ({
    index,
    rgb: [
      normalize(values[index] ?? 0),
      normalize(values[index + entries] ?? 0),
      normalize(values[index + entries * 2] ?? 0),
    ],
  }));

  const fingerprintInput = palette.map((entry) => `${entry.index}:${entry.rgb.join(',')}`).join('|');
  return {
    entries,
    sha256: createHash('sha256').update(fingerprintInput).digest('hex'),
    first16: palette.slice(0, 16),
  };
}

function rasterStats(values: ArrayLike<number>) {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  let zeroCount = 0;
  const counts = new Map<number, number>();

  for (let index = 0; index < values.length; index += 1) {
    const value = Number(values[index]);
    if (!Number.isFinite(value)) continue;
    if (value < min) min = value;
    if (value > max) max = value;
    if (value === 0) zeroCount += 1;
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }

  const mostFrequent = [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 20)
    .map(([value, count]) => ({ value, count }));

  return {
    min: Number.isFinite(min) ? min : null,
    max: Number.isFinite(max) ? max : null,
    uniqueCount: counts.size,
    zeroCount,
    mostFrequent,
  };
}

const assets = await fetchAemetNationalRadarGeoTiffs();
const report = [];

for (const asset of assets) {
  const inspection = await inspectRadarGeoTiff(asset.bytes);
  const tiff = await fromArrayBuffer(toArrayBuffer(asset.bytes));
  const image = await tiff.getImage();
  const directory = image.fileDirectory as unknown as FileDirectoryProbe;
  const interleaved = await image.readRasters({ interleave: true });
  const pixels = interleaved as unknown as ArrayLike<number>;

  report.push({
    sourceName: asset.sourceName ?? null,
    byteSize: asset.bytes.byteLength,
    endian: endian(asset.bytes),
    observedAt: asset.metadata.observed_at,
    parsed: inspection.parsed,
    analysisReady: inspection.analysisReady,
    width: inspection.width,
    height: inspection.height,
    samplesPerPixel: inspection.samplesPerPixel,
    crs: inspection.crs,
    geographicTypeGeoKey: inspection.geographicTypeGeoKey,
    bbox: inspection.bbox,
    resolution: inspection.resolution,
    noData: inspection.noData,
    validationErrors: inspection.validationErrors,
    scalePreview: inspection.scaleRaw?.slice(0, 1200) ?? null,
    palette: await paletteSummary(directory),
    tiffTags: {
      photometricInterpretation: await loadTag(directory, 'PhotometricInterpretation'),
      bitsPerSample: await loadTag(directory, 'BitsPerSample'),
      sampleFormat: await loadTag(directory, 'SampleFormat'),
      imageDescription: await loadTag(directory, 'ImageDescription'),
      gdalMetadata: await loadTag(directory, 'GDAL_METADATA'),
      gdalNoData: await loadTag(directory, 'GDAL_NODATA'),
    },
    rasterStats: rasterStats(pixels),
  });
}

console.log('AEMET_RADAR_LIVE_PROBE');
console.log(JSON.stringify(report, null, 2));
