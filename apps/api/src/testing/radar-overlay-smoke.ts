import assert from 'node:assert/strict';
import type { RadarGrid } from '@magina/weather';
import { renderRadarOverlayPng } from '../weather/radar-overlay.js';

const grid: RadarGrid = {
  width: 2,
  height: 1,
  bbox: [-4, 37, -3, 38],
  values: new Uint8Array([7, 3]),
  indexBands: [{ index: 7, min: 18, max: 24, rgba: [0, 148, 252, 255] }],
  noCoverageIndexes: [],
  clearIndexes: [3],
};

const png = renderRadarOverlayPng(grid);
assert.deepEqual([...png.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
assert.equal(png.readUInt32BE(16), 2);
assert.equal(png.readUInt32BE(20), 1);
assert.equal(png.toString('ascii', 12, 16), 'IHDR');
assert.ok(png.byteLength > 50);

console.log('RADAR_OVERLAY_SMOKE_OK');
