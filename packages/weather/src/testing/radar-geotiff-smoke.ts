import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';
import * as tar from 'tar-stream';
import {
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

assert.equal(
  parseRadarObservationTimestampFromName('compo_20260910_0430.tif'),
  '2026-09-10T04:30:00.000Z',
);
assert.equal(
  parseRadarObservationTimestampFromName('radar-2026-09-10T04-35-20.tiff'),
  '2026-09-10T04:35:20.000Z',
);
assert.equal(parseRadarObservationTimestampFromName('compo_latest.tif'), null);

const bundle = await createBundle([
  { name: 'README.txt', bytes: Buffer.from('ignored metadata') },
  { name: 'compo_20260910_0430.tif', bytes: fakeTiff('snapshot-A') },
  { name: 'nested/compo_20260910_0435.tiff', bytes: fakeTiff('snapshot-B') },
]);

const parsed = await parseAemetRadarGeoTiffBundle(
  new Uint8Array(bundle),
  new Date('2026-09-10T04:36:00Z'),
  'https://www.aemet.es/es/api-eltiempo/radar/download/compo',
);
assert.equal(parsed.length, 2);
assert.deepEqual(parsed.map((asset) => asset.sourceName), [
  'compo_20260910_0430.tif',
  'nested/compo_20260910_0435.tiff',
]);
assert.deepEqual(parsed.map((asset) => asset.metadata.observed_at), [
  '2026-09-10T04:30:00.000Z',
  '2026-09-10T04:35:00.000Z',
]);
assert.ok(parsed.every((asset) => asset.metadata.asset_format === 'geotiff'));
assert.ok(parsed.every((asset) => asset.metadata.analysis_ready === false));
assert.ok(parsed.every((asset) => asset.metadata.crs === 'EPSG:4326'));
assert.ok(parsed.every((asset) => asset.contentType === 'image/tiff'));

const invalidBundle = await createBundle([
  { name: 'compo_20260910_0440.tif', bytes: Buffer.from('not-a-tiff') },
]);
await assert.rejects(
  () => parseAemetRadarGeoTiffBundle(new Uint8Array(invalidBundle)),
  /INVALID_SIGNATURE/,
);

console.log('RADAR_GEOTIFF_BUNDLE_SMOKE_OK');
