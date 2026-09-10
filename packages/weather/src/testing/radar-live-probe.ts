import {
  fetchAemetNationalRadarGeoTiffs,
  inspectRadarGeoTiff,
} from '../index.js';

function endian(bytes: Uint8Array) {
  if (bytes[0] === 0x49 && bytes[1] === 0x49) return 'little';
  if (bytes[0] === 0x4d && bytes[1] === 0x4d) return 'big';
  return 'unknown';
}

const assets = await fetchAemetNationalRadarGeoTiffs();
const report = [];

for (const asset of assets) {
  const inspection = await inspectRadarGeoTiff(asset.bytes);
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
  });
}

console.log('AEMET_RADAR_LIVE_PROBE');
console.log(JSON.stringify(report, null, 2));
