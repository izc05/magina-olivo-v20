import { prepareRouteGeometryIngestion } from '../routes/geometry/ingest.js';

const kml = '<kml><LineString><coordinates>-3.5000,37.7000,900 -3.4900,37.7050,950 -3.4800,37.7100,1000</coordinates></LineString></kml>';

const prepared = prepareRouteGeometryIngestion({
  format: 'kml',
  content: kml,
  sourceCrs: 'EPSG:4326',
  publishedDistanceM: 2050,
});

if (prepared.sourceSha256 !== 'ab1a0cbe7561320a563ec26d4597b97780f6f157836296233fe35d845f9950ab') {
  throw new Error(`Unexpected SHA-256: ${prepared.sourceSha256}`);
}
if (prepared.bbox.join(',') !== '-3.5,37.7,-3.48,37.71') {
  throw new Error(`Unexpected bbox: ${prepared.bbox.join(',')}`);
}
if (!(prepared.distanceDeltaPercent > 1 && prepared.distanceDeltaPercent < 2)) {
  throw new Error(`Unexpected distance delta: ${prepared.distanceDeltaPercent}`);
}
if (prepared.recommendation !== 'ready_for_review') {
  throw new Error(`Expected ready_for_review, got ${prepared.recommendation}`);
}

const suspicious = prepareRouteGeometryIngestion({
  format: 'kml',
  content: kml,
  sourceCrs: 'EPSG:4326',
  publishedDistanceM: 1000,
});
if (suspicious.recommendation !== 'distance_mismatch') {
  throw new Error(`Expected distance_mismatch, got ${suspicious.recommendation}`);
}

console.log('route geometry ingestion metadata smoke test passed');
