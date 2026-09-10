import assert from 'node:assert/strict';
import { writeArrayBuffer } from 'geotiff';
import { parseAemetRadarEscala } from '../radar-escala.js';
import {
  evaluateRadarGeoTiffFacts,
  inspectRadarGeoTiff,
} from '../radar-geotiff-inspection.js';

const syntheticScale = `{
  'Producto': 'RAD',
  'Lista RGBA': [
    {'Valores': [-12, -6], 'RGBA': ['10', '20', '30', '0']},
    {'Valores': [-6, 0], 'RGBA': ['40', '50', '60', '255']},
    {'Valores': [60, ''], 'RGBA': ['230', '240', '250', '255']}
  ]
}`;

const parsedScale = parseAemetRadarEscala(syntheticScale);
assert.equal(parsedScale.valid, true);
assert.equal(parsedScale.error, null);
assert.deepEqual(parsedScale.bands, [
  { min: -12, max: -6, rgba: [10, 20, 30, 0] },
  { min: -6, max: 0, rgba: [40, 50, 60, 255] },
  { min: 60, max: null, rgba: [230, 240, 250, 255] },
]);

assert.equal(parseAemetRadarEscala('RGBA -> dBZ intervals').valid, false);
assert.equal(parseAemetRadarEscala(null).error, 'scale_missing');
assert.equal(
  parseAemetRadarEscala(`{'Lista RGBA':[{'Valores':[0,1],'RGBA':[300,0,0,255]},{'Valores':[1,2],'RGBA':[1,2,3,255]}]}`).error,
  'scale_rgba_invalid',
);

const ready = evaluateRadarGeoTiffFacts({
  width: 1200,
  height: 900,
  samplesPerPixel: 4,
  crs: 'EPSG:4326',
  geographicTypeGeoKey: 4326,
  bbox: [-10, 35, 5, 44],
  resolution: [0.01, -0.01, 0],
  noData: null,
  photometricInterpretation: null,
  bitsPerSample: [],
  colorMap: null,
  scaleRaw: syntheticScale,
});
assert.equal(ready.parsed, true);
assert.equal(ready.analysisReady, true);
assert.equal(ready.scaleSource, 'escala');
assert.deepEqual(ready.validationErrors, []);
assert.equal(ready.scaleBands.length, 3);

const wrongCrs = evaluateRadarGeoTiffFacts({
  width: ready.width,
  height: ready.height,
  samplesPerPixel: ready.samplesPerPixel,
  crs: null,
  geographicTypeGeoKey: 3857,
  bbox: ready.bbox,
  resolution: ready.resolution,
  noData: ready.noData,
  photometricInterpretation: ready.photometricInterpretation,
  bitsPerSample: ready.bitsPerSample,
  colorMap: null,
  scaleRaw: ready.scaleRaw,
});
assert.equal(wrongCrs.analysisReady, false);
assert.ok(wrongCrs.validationErrors.includes('crs_not_epsg_4326'));

const withoutScale = evaluateRadarGeoTiffFacts({
  width: ready.width,
  height: ready.height,
  samplesPerPixel: ready.samplesPerPixel,
  crs: ready.crs,
  geographicTypeGeoKey: ready.geographicTypeGeoKey,
  bbox: ready.bbox,
  resolution: ready.resolution,
  noData: ready.noData,
  photometricInterpretation: null,
  bitsPerSample: [],
  colorMap: null,
  scaleRaw: null,
});
assert.equal(withoutScale.analysisReady, false);
assert.ok(withoutScale.validationErrors.includes('scale_missing'));
assert.ok(withoutScale.validationErrors.includes('palette_raster_shape_unrecognized'));

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
assert.equal(inspected.analysisReady, false, 'a generic GeoTIFF without ESCALA/AEMET palette must not be analysis-ready');
assert.ok(inspected.validationErrors.includes('scale_missing'));
assert.deepEqual(inspected.scaleBands, []);

const malformed = await inspectRadarGeoTiff(new Uint8Array([0x49, 0x49, 0x2a, 0x00, 0x01]));
assert.equal(malformed.parsed, false);
assert.equal(malformed.analysisReady, false);
assert.deepEqual(malformed.validationErrors, ['geotiff_parse_failed']);
assert.deepEqual(malformed.scaleBands, []);

console.log('RADAR_GEOTIFF_INSPECTION_SMOKE_OK');
