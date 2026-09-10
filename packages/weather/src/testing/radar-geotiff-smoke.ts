import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';
import * as tar from 'tar-stream';
import {
  isAemetNationalReflectivityGeoTiffName,
  parseAemetRadarGeoTiffBundle,
  parseRadarObservationTimestampFromName,
  validateAemetRadarGeoTiffBundleUrl,
} from '../aemet-radar-geotiff.js';

function fakeTiff(label: string) {
  return Buffer.concat([
    Buffer.from([0x49, 0x49, 0x2a, 0x00]),
    Buffer.from(label),
  ]);
}

async function createBundle(entries: Array<{ name: string; bytes: Buffer }>) {
  const pack = tar.pack();
  const chunks: Buffer[] = [];
  const finished = new Promise<Buffer>((resolve, reject) => {
    pack.on('data', (chunk: Buffer) => chunks.push(chunk));
    pack.on('end', () => resolve(Buffer.concat(chunks)));
    pack.on('error', reject);
  });

  for (const entry of entries) {
    pack.entry({ name: entry.name, size: entry.bytes.byteLength, type: 'file' }, entry.bytes);
  }
  pack.finalize();
  return gzipSync(await finished);
}

assert.equal(
  validateAemetRadarGeoTiffBundleUrl('https://www.aemet.es/es/api-eltiempo/radar/download/compo').hostname,
  'www.aemet.es',
);
assert.throws(
  () => validateAemetRadarGeoTiffBundleUrl('https://example.com/es/api-eltiempo/radar/download/compo'),
  /NOT_TRUSTED/,
);
assert.throws(
  () => validateAemetRadarGeoTiffBundleUrl('https://www.aemet.es/es/api-eltiempo/radar/download/other'),
  /NOT_TRUSTED/,
);

assert.equal(isAemetNationalReflectivityGeoTiffName('down_radw202609100550_4326.tif'), true);
assert.equal(isAemetNationalReflectivityGeoTiffName('nested/down_radw202609100550_4326.tiff'), true);
assert.equal(isAemetNationalReflectivityGeoTiffName('other_product_202609100550_4326.tif'), false);
assert.equal(
  parseRadarObservationTimestampFromName('down_radw202609100550_4326.tif'),
  '2026-09-10T05:50:00.000Z',
);
assert.equal(
  parseRadarObservationTimestampFromName('radar-2026-09-10T04-35-20.tiff'),
  '2026-09-10T04:35:20.000Z',
);
assert.equal(parseRadarObservationTimestampFromName('down_radw_latest_4326.tif'), null);

const bundle = await createBundle([
  { name: 'README.txt', bytes: Buffer.from('ignored metadata') },
  { name: 'down_radw202609100510_4326.tif', bytes: fakeTiff('snapshot-0510') },
  { name: 'down_radw202609100520_4326.tif', bytes: fakeTiff('snapshot-0520') },
  { name: 'down_radw202609100530_4326.tif', bytes: fakeTiff('snapshot-0530') },
  { name: 'down_radw202609100540_4326.tif', bytes: fakeTiff('snapshot-0540') },
  { name: 'down_radw202609100550_4326.tif', bytes: fakeTiff('snapshot-0550') },
  { name: 'unrelated_202609100600_4326.tif', bytes: Buffer.from('not-a-reflectivity-tiff') },
]);

const parsed = await parseAemetRadarGeoTiffBundle(
  new Uint8Array(bundle),
  new Date('2026-09-10T05:51:00Z'),
  'https://www.aemet.es/es/api-eltiempo/radar/download/compo',
);
assert.equal(parsed.length, 3);
assert.deepEqual(parsed.map((asset) => asset.sourceName), [
  'down_radw202609100530_4326.tif',
  'down_radw202609100540_4326.tif',
  'down_radw202609100550_4326.tif',
]);
assert.deepEqual(parsed.map((asset) => asset.metadata.observed_at), [
  '2026-09-10T05:30:00.000Z',
  '2026-09-10T05:40:00.000Z',
  '2026-09-10T05:50:00.000Z',
]);
assert.ok(parsed.every((asset) => asset.metadata.asset_format === 'geotiff'));
assert.ok(parsed.every((asset) => asset.metadata.analysis_ready === false));
assert.ok(parsed.every((asset) => asset.metadata.crs === 'EPSG:4326'));
assert.ok(parsed.every((asset) => asset.contentType === 'image/tiff'));

const invalidBundle = await createBundle([
  { name: 'down_radw202609100600_4326.tif', bytes: Buffer.from('not-a-tiff') },
]);
await assert.rejects(
  () => parseAemetRadarGeoTiffBundle(new Uint8Array(invalidBundle)),
  /INVALID_SIGNATURE/,
);

const noReflectivity = await createBundle([
  { name: 'something_else_202609100600_4326.tif', bytes: fakeTiff('ignored') },
]);
await assert.rejects(
  () => parseAemetRadarGeoTiffBundle(new Uint8Array(noReflectivity)),
  /NO_REFLECTIVITY_ENTRIES/,
);

console.log('RADAR_GEOTIFF_BUNDLE_SMOKE_OK');
