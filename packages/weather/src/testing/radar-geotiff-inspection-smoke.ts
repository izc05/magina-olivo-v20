import assert from 'node:assert/strict';
import { writeArrayBuffer } from 'geotiff';
import {
  evaluateRadarGeoTiffFacts,
  inspectRadarGeoTiff,
} from '../radar-geotiff-inspection.js';

const ready = evaluateRadarGeoTiffFacts({
  width: 1200,
  height: 900,
  samplesPerPixel: 4,
  crs: 'EPSG:4326',
  geographicTypeGeoKey: 4326,
  bbox: [-10, 35, 5, 44],
  resolution: [0.01, -0.01, 0],
  noData: null,
  scaleRaw: 'RGBA -> dBZ intervals',
});
assert.equal(ready.parsed, true);
assert.equal(ready.analysisReady, true);
assert.deepEqual(ready.validationErrors, []);

const wrongCrs = evaluateRadarGeoTiffFacts({
  ...ready,
  crs: null,
  geographicTypeGeoKey: 3857,
});
assert.equal(wrongCrs.analysisReady, false);
assert.ok(wrongCrs.validationErrors.includes('crs_not_epsg_4326'));

const withoutScale = evaluateRadarGeoTiffFacts({
  ...ready,
  scaleRaw: null,
});
assert.equal(withoutScale.analysisReady, false);
assert.ok(withoutScale.validationErrors.includes('scale_missing'));

const generated = await writeArrayBuffer(new Uint8Array([1, 2, 3, 4]), {
  width: 2,
  height: 2,
  GeographicTypeGeoKey: 4326,
  GTModelTypeGeoKey: 2,
  GTRasterTypeGeoKey: 1,
  ModelPixelScale: [0.1, 0.1, 0],
  ModelTiepoint: [0, 0, 0, -4.0, 38.0, 0],
  GDAL_NODATA: '0',
});

const inspected = await inspectRadarGeoTiff(new Uint8Array(generated));
assert.equal(inspected.parsed, true);
assert.equal(inspected.geographicTypeGeoKey, 4326);
assert.equal(inspected.crs, 'EPSG:4326');
assert.equal(inspected.width, 2);
assert.equal(inspected.height, 2);
assert.equal(inspected.noData, 0);
assert.ok(inspected.bbox);
assert.ok(inspected.resolution);
assert.equal(inspected.analysisReady, false, 'a valid GeoTIFF without AEMET ESCALA must not be analysis-ready');
assert.ok(inspected.validationErrors.includes('scale_missing'));

const malformed = await inspectRadarGeoTiff(new Uint8Array([0x49, 0x49, 0x2a, 0x00, 0x01]));
assert.equal(malformed.parsed, false);
assert.equal(malformed.analysisReady, false);
assert.deepEqual(malformed.validationErrors, ['geotiff_parse_failed']);

console.log('RADAR_GEOTIFF_INSPECTION_SMOKE_OK');
