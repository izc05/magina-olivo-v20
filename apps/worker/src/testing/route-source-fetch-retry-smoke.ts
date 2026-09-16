import { fetchOfficialRouteAsset } from '../routes/geometry/source-fetch.js';

let attempts = 0;
const result = await fetchOfficialRouteAsset({
  url: 'https://example.invalid/official-route.kml',
  fetchImpl: async () => {
    attempts += 1;
    if (attempts < 3) {
      return {
        status: 503,
        ok: false,
        headers: new Headers(),
        arrayBuffer: async () => new ArrayBuffer(0),
      };
    }
    return {
      status: 200,
      ok: true,
      headers: new Headers({ 'content-type': 'application/vnd.google-earth.kml+xml' }),
      arrayBuffer: async () => new TextEncoder().encode('<kml/>').buffer,
    };
  },
  maxAttempts: 3,
  retryDelayMs: 0,
});

if (result.status !== 'fetched') {
  throw new Error(`Expected fetched status, got ${result.status}`);
}
if (attempts !== 3 || result.attempts !== 3) {
  throw new Error(`Expected 3 attempts, got fetch=${attempts} result=${result.attempts}`);
}
if (new TextDecoder().decode(result.content) !== '<kml/>') {
  throw new Error('Fetched body was not preserved');
}

console.log('route source retry smoke test passed');
