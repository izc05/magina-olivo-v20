import { normalizeRouteGeometry } from '../routes/geometry/normalize.js';

const sampleGml = `<?xml version="1.0" encoding="UTF-8"?>
<gml:LineString xmlns:gml="http://www.opengis.net/gml/3.2" srsName="urn:ogc:def:crs:EPSG::4326" srsDimension="2">
  <gml:posList>37.7000 -3.5000 37.7050 -3.4900 37.7100 -3.4800</gml:posList>
</gml:LineString>`;

const result = normalizeRouteGeometry({
  format: 'gml',
  content: sampleGml,
  sourceCrs: 'EPSG:4326',
});

if (result.geometry.coordinates.length !== 3) {
  throw new Error(`Expected 3 GML coordinates, got ${result.geometry.coordinates.length}`);
}

if (result.start[0] !== -3.5 || result.start[1] !== 37.7) {
  throw new Error(`Unexpected GML start coordinate: ${JSON.stringify(result.start)}`);
}

if (!(result.distanceM > 2000 && result.distanceM < 2100)) {
  throw new Error(`Unexpected GML calculated distance: ${result.distanceM}`);
}

console.log('route geometry GML normalization smoke test passed');
