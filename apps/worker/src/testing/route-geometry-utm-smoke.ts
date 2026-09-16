import { normalizeRouteGeometry } from '../routes/geometry/normalize.js';

const sampleGml = `<?xml version="1.0" encoding="UTF-8"?>
<gml:LineString xmlns:gml="http://www.opengis.net/gml/3.2" srsName="urn:ogc:def:crs:EPSG::25830" srsDimension="2">
  <gml:posList>458762 4187614 458900 4187700 459100 4187900</gml:posList>
</gml:LineString>`;

const result = normalizeRouteGeometry({
  format: 'gml',
  content: sampleGml,
  sourceCrs: 'EPSG:25830',
});

const [longitude, latitude] = result.start;
if (Math.abs(longitude - (-3.468646132)) > 0.000001) {
  throw new Error(`Unexpected transformed longitude: ${longitude}`);
}
if (Math.abs(latitude - 37.835021319) > 0.000001) {
  throw new Error(`Unexpected transformed latitude: ${latitude}`);
}
if (!(result.distanceM > 400 && result.distanceM < 500)) {
  throw new Error(`Unexpected transformed distance: ${result.distanceM}`);
}

console.log('route geometry EPSG:25830 normalization smoke test passed');
