import { fetchOfficialRouteAsset } from '../routes/geometry/source-fetch.js';

let attempts = 0;
const result = await fetchOfficialRouteAsset({
  url: 'https://example.invalid/official-route.kml',
  fetchImpl: async () => {
    attempts += 1;
    return {
      status: 403,
      ok: false,
      headers: new Headers(),
      arrayBuffer: async () => new ArrayBuffer(0),
    };
  },
});

if (result.status !== 'blocked') {
  throw new Error(`Expected blocked status, got ${result.status}`);
}
if (result.httpStatus !== 403) {
  throw new Error(`Expected HTTP 403, got ${result.httpStatus}`);
}
if (attempts !== 1) {
  throw new Error(`403 must not be retried; attempts=${attempts}`);
}

console.log('route source blocked fetch smoke test passed');
