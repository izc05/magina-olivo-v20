import { normalizeRouteGeometry } from '../routes/geometry/normalize.js';

const sampleKml = `<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <Placemark>
      <LineString>
        <coordinates>-3.5000,37.7000,900 -3.4900,37.7050,950 -3.4800,37.7100,1000</coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>`;

const result = normalizeRouteGeometry({
  format: 'kml',
  content: sampleKml,
  sourceCrs: 'EPSG:4326',
});

if (result.geometry.type !== 'LineString') {
  throw new Error(`Expected LineString, got ${result.geometry.type}`);
}

if (result.geometry.coordinates.length !== 3) {
  throw new Error(`Expected 3 coordinates, got ${result.geometry.coordinates.length}`);
}

if (result.start[0] !== -3.5 || result.start[1] !== 37.7) {
  throw new Error(`Unexpected start coordinate: ${JSON.stringify(result.start)}`);
}

if (result.end[0] !== -3.48 || result.end[1] !== 37.71) {
  throw new Error(`Unexpected end coordinate: ${JSON.stringify(result.end)}`);
}

if (!(result.distanceM > 2000 && result.distanceM < 2100)) {
  throw new Error(`Unexpected calculated distance: ${result.distanceM}`);
}

if (result.altitudeMinM !== 900 || result.altitudeMaxM !== 1000) {
  throw new Error(`Unexpected altitude range: ${result.altitudeMinM}..${result.altitudeMaxM}`);
}

console.log('route geometry KML normalization smoke test passed');
