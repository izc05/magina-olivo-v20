import assert from 'node:assert/strict';
import {
  AEMET_NATIONAL_REFLECTIVITY_BANDS_V1,
  resolveAemetNationalReflectivityPalette,
} from '../aemet-national-reflectivity-palette.js';

function tiffColorMap(entries: Array<[number, number, number]>) {
  const size = entries.length;
  const values = new Uint16Array(size * 3);
  for (let index = 0; index < size; index += 1) {
    const rgb = entries[index]!;
    values[index] = rgb[0] * 257;
    values[index + size] = rgb[1] * 257;
    values[index + size * 2] = rgb[2] * 257;
  }
  return values;
}

const entries = Array.from({ length: 256 }, () => [0, 0, 0] as [number, number, number]);
entries[0] = [255, 255, 255];
entries[1] = [239, 242, 249];

// Deliberately use non-monotonic, non-contiguous indexes. This reproduces the
// live AEMET behaviour where ColorMap indexes can move between snapshots.
const shuffledIndexes = [8, 3, 12, 5, 2, 11, 7, 4, 10, 6, 9];
for (let offset = 0; offset < AEMET_NATIONAL_REFLECTIVITY_BANDS_V1.length; offset += 1) {
  const band = AEMET_NATIONAL_REFLECTIVITY_BANDS_V1[offset]!;
  entries[shuffledIndexes[offset]!] = [...band.rgb];
}

const resolved = resolveAemetNationalReflectivityPalette(tiffColorMap(entries));
assert.equal(resolved.valid, true);
assert.equal(resolved.source, 'aemet-national-reflectivity-palette-v1');
assert.equal(resolved.indexBands.length, 11);
assert.deepEqual(resolved.noCoverageIndexes, [0]);
assert.deepEqual(resolved.clearIndexes, [1]);

for (const expected of AEMET_NATIONAL_REFLECTIVITY_BANDS_V1) {
  const actual = resolved.indexBands.find((item) => (
    item.rgba[0] === expected.rgb[0]
    && item.rgba[1] === expected.rgb[1]
    && item.rgba[2] === expected.rgb[2]
  ));
  assert.ok(actual, `missing resolved color ${expected.rgb.join(',')}`);
  assert.equal(actual.min, expected.min);
  assert.equal(actual.max, expected.max);
}

const drifted = entries.map((rgb) => [...rgb] as [number, number, number]);
const cyanIndex = shuffledIndexes[2]!;
drifted[cyanIndex] = [1, 252, 252];
const rejected = resolveAemetNationalReflectivityPalette(tiffColorMap(drifted));
assert.equal(rejected.valid, false);
assert.equal(rejected.error, 'palette_echo_color_missing');

console.log('RADAR_PALETTE_SMOKE_OK');
