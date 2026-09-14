import assert from 'node:assert/strict';
import test from 'node:test';
import { parseKmlTrack } from './kml.js';

const sample = `<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <name>Fuenmayor prueba</name>
      <LineString>
        <coordinates>
          -3.500000,37.700000,980
          -3.499000,37.701000,1005
          -3.498000,37.702000,995
        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>`;

test('parseKmlTrack extracts one official LineString with deterministic metrics', () => {
  const parsed = parseKmlTrack(sample);
  assert.equal(parsed.name, 'Fuenmayor prueba');
  assert.equal(parsed.points.length, 3);
  assert.equal(parsed.geojson.type, 'LineString');
  assert.equal(parsed.geojson.coordinates.length, 3);
  assert.ok(parsed.distanceM > 0);
  assert.equal(parsed.elevationGainM, 25);
  assert.equal(parsed.elevationLossM, 10);
  assert.equal(parsed.minAltitudeM, 980);
  assert.equal(parsed.maxAltitudeM, 1005);
  assert.equal(parsed.checksum.length, 64);
  assert.deepEqual(parsed.bbox, [-3.5, 37.7, -3.498, 37.702]);
});

test('parseKmlTrack preserves missing elevation instead of inventing it', () => {
  const parsed = parseKmlTrack(`
    <kml><Placemark><LineString><coordinates>
      -3.50,37.70 -3.49,37.71
    </coordinates></LineString></Placemark></kml>
  `);
  assert.equal(parsed.elevationGainM, null);
  assert.equal(parsed.elevationLossM, null);
  assert.equal(parsed.minAltitudeM, null);
  assert.equal(parsed.maxAltitudeM, null);
});

test('parseKmlTrack rejects ambiguous or malformed geometry', () => {
  assert.throws(() => parseKmlTrack('<xml></xml>'), /kml_root_missing/);
  assert.throws(
    () => parseKmlTrack('<kml><Placemark><LineString><coordinates>-3,37</coordinates></LineString></Placemark></kml>'),
    /kml_track_too_short/,
  );
  assert.throws(
    () => parseKmlTrack('<kml><LineString><coordinates>-3,37 -3.1,37.1</coordinates></LineString><LineString><coordinates>-3.2,37.2 -3.3,37.3</coordinates></LineString></kml>'),
    /kml_multiple_linestrings_unsupported/,
  );
  assert.throws(
    () => parseKmlTrack('<kml><LineString><coordinates>-3,100 -3.1,37.1</coordinates></LineString></kml>'),
    /kml_invalid_latitude/,
  );
});
