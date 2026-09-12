import assert from 'node:assert/strict';
import {
  analyzeRadarGrid,
  bearingToCompass,
  haversineKm,
} from '../radar-spatial-analysis.js';

assert.equal(bearingToCompass(0), 'N');
assert.equal(bearingToCompass(90), 'E');
assert.equal(bearingToCompass(180), 'S');
assert.equal(bearingToCompass(270), 'W');
assert.ok(Math.abs(haversineKm({ lat: 0, lon: 0 }, { lat: 0, lon: 1 }) - 111.2) < 0.2);

const width = 5;
const height = 5;
const values = new Uint8Array(width * height).fill(1); // covered, no >=12 dBZ echo
const at = (x: number, y: number) => y * width + x;

// Target point (0,0) maps to center pixel (2,2).
// Nearest echo is one pixel east; a stronger echo is farther north-west.
values[at(3, 2)] = 7;
values[at(0, 0)] = 3;

const observation = analyzeRadarGrid({
  width,
  height,
  bbox: [-0.25, -0.25, 0.25, 0.25],
  values,
  indexBands: [
    { index: 7, min: 12, max: 18, rgba: [0, 0, 252, 255] },
    { index: 3, min: 48, max: 54, rgba: [255, 255, 0, 255] },
  ],
  noCoverageIndexes: [0],
  clearIndexes: [1],
}, { lat: 0, lon: 0 }, { radiusKm: 50 });

assert.equal(observation.analysisReady, true);
assert.equal(observation.coverageAtPoint, 'covered');
assert.equal(observation.precipitationDetected, true);
assert.equal(observation.pointBand, null);
assert.equal(observation.nearestEchoDirection, 'E');
assert.ok(observation.nearestEchoDistanceKm !== null && observation.nearestEchoDistanceKm > 10 && observation.nearestEchoDistanceKm < 12);
assert.deepEqual(observation.nearestEchoBand, { minDbz: 12, maxDbz: 18 });
assert.deepEqual(observation.strongestBandWithinRadius, { minDbz: 48, maxDbz: 54 });

const noCoverageValues = new Uint8Array(width * height).fill(1);
noCoverageValues[at(2, 2)] = 0;
const noCoverage = analyzeRadarGrid({
  width,
  height,
  bbox: [-0.25, -0.25, 0.25, 0.25],
  values: noCoverageValues,
  indexBands: [{ index: 7, min: 12, max: 18, rgba: [0, 0, 252, 255] }],
  noCoverageIndexes: [0],
  clearIndexes: [1],
}, { lat: 0, lon: 0 }, { radiusKm: 20 });
assert.equal(noCoverage.coverageAtPoint, 'no_coverage');
assert.equal(noCoverage.precipitationDetected, false);
assert.equal(noCoverage.reason, 'no_radar_coverage_at_point');

const outside = analyzeRadarGrid({
  width,
  height,
  bbox: [-0.25, -0.25, 0.25, 0.25],
  values,
  indexBands: [{ index: 7, min: 12, max: 18, rgba: [0, 0, 252, 255] }],
  noCoverageIndexes: [0],
  clearIndexes: [1],
}, { lat: 50, lon: 50 }, { radiusKm: 20 });
assert.equal(outside.coverageAtPoint, 'outside_raster');
assert.equal(outside.reason, 'point_outside_raster');

console.log('RADAR_SPATIAL_ANALYSIS_SMOKE_OK');
