import assert from 'node:assert/strict';
import test from 'node:test';
import { parseGpxTrack } from './gpx.js';

const sample = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="magina-test">
  <trk>
    <name>Ruta de prueba</name>
    <trkseg>
      <trkpt lat="37.700000" lon="-3.900000"><ele>700</ele></trkpt>
      <trkpt lat="37.701000" lon="-3.899000"><ele>725</ele></trkpt>
      <trkpt lat="37.702000" lon="-3.898000"><ele>710</ele></trkpt>
    </trkseg>
  </trk>
</gpx>`;

test('parseGpxTrack extracts geometry and deterministic metrics', () => {
  const parsed = parseGpxTrack(sample);
  assert.equal(parsed.name, 'Ruta de prueba');
  assert.equal(parsed.points.length, 3);
  assert.equal(parsed.geojson.type, 'LineString');
  assert.equal(parsed.geojson.coordinates.length, 3);
  assert.ok(parsed.distanceM > 0);
  assert.equal(parsed.elevationGainM, 25);
  assert.equal(parsed.elevationLossM, 15);
  assert.equal(parsed.minAltitudeM, 700);
  assert.equal(parsed.maxAltitudeM, 725);
  assert.equal(parsed.checksum.length, 64);
  assert.deepEqual(parsed.bbox, [-3.9, 37.7, -3.898, 37.702]);
});

test('parseGpxTrack preserves unknown elevation instead of inventing it', () => {
  const parsed = parseGpxTrack(`
    <gpx version="1.1"><trk><trkseg>
      <trkpt lat="37.70" lon="-3.90"></trkpt>
      <trkpt lat="37.71" lon="-3.89"></trkpt>
    </trkseg></trk></gpx>
  `);
  assert.equal(parsed.elevationGainM, null);
  assert.equal(parsed.elevationLossM, null);
  assert.equal(parsed.minAltitudeM, null);
  assert.equal(parsed.maxAltitudeM, null);
});

test('parseGpxTrack rejects malformed or too-short tracks', () => {
  assert.throws(() => parseGpxTrack('<xml></xml>'), /gpx_root_missing/);
  assert.throws(
    () => parseGpxTrack('<gpx><trk><trkseg><trkpt lat="37" lon="-3"></trkpt></trkseg></trk></gpx>'),
    /gpx_track_too_short/,
  );
  assert.throws(
    () => parseGpxTrack('<gpx><trk><trkseg><trkpt lat="100" lon="-3"></trkpt><trkpt lat="37" lon="-3"></trkpt></trkseg></trk></gpx>'),
    /gpx_invalid_latitude/,
  );
});
