import assert from 'node:assert/strict';
import {
  buildRadarSnapshotMetadata,
  resolveAemetNationalRadarEnvelope,
} from '../weather/aemet-radar.js';

const trusted = resolveAemetNationalRadarEnvelope({
  estado: 200,
  datos: 'https://opendata.aemet.es/opendata/sh/example-radar.gif',
});
assert.equal(trusted.hostname, 'opendata.aemet.es');
assert.equal(trusted.protocol, 'https:');

assert.throws(
  () => resolveAemetNationalRadarEnvelope({ datos: 'https://example.com/radar.gif' }),
  /NOT_TRUSTED/,
);
assert.throws(
  () => resolveAemetNationalRadarEnvelope({ estado: 200 }),
  /DATA_URL_MISSING/,
);

const gif = buildRadarSnapshotMetadata('image/gif', new Date('2026-09-10T04:00:00Z'));
assert.equal(gif.source, 'aemet_national_mosaic');
assert.equal(gif.product, 'reflectivity');
assert.equal(gif.crs, 'EPSG:4326');
assert.equal(gif.asset_format, 'gif');
assert.equal(gif.analysis_ready, false);
assert.equal(gif.observed_at, null);

const geotiff = buildRadarSnapshotMetadata('image/tiff', new Date('2026-09-10T04:05:00Z'));
assert.equal(geotiff.asset_format, 'geotiff');
assert.equal(geotiff.analysis_ready, false, 'MIME alone must never mark a GeoTIFF analysis-ready');

console.log('RADAR_ADAPTER_SMOKE_OK');
