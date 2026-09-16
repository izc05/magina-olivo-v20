import { normalizeRouteAsset } from '../routes/geometry/normalize.js';

const kmz = Buffer.from(
  'UEsDBBQAAAAIAGNkMF0p5y2OqgAAAPUAAAAHAAAAZG9jLmttbE2OQQ6CMBRE956CdC1tAQliStkYVy5M1AOQ+oMN0JK2isf3EzWymEzyMpkZUb+GPnqC89qaiiSUkwiMsjdt2opcL4d4S2q5Eh2mMGl8Re4hjDvGpmmidgTTak8NBIYJltKUSLG36jGACVKc+kbB0LhOiqM2cA4Oa6VQ1jocaAJ4GWc055yvs4IWs5ecR8g25Y/lyPIP235Zgo7igi2bBFtusMU2+z+ab8o3UEsBAhQDFAAAAAgAY2QwXSnnLY6qAAAA9QAAAAcAAAAAAAAAAAAAAIABAAAAAGRvYy5rbWxQSwUGAAAAAAEAAQA1AAAAzwAAAAAA',
  'base64',
);

const result = normalizeRouteAsset({
  format: 'kmz',
  content: kmz,
  sourceCrs: 'EPSG:4326',
});

if (result.geometry.coordinates.length !== 3) {
  throw new Error(`Expected 3 KMZ coordinates, got ${result.geometry.coordinates.length}`);
}

if (result.start[0] !== -3.5 || result.start[1] !== 37.7) {
  throw new Error(`Unexpected KMZ start coordinate: ${JSON.stringify(result.start)}`);
}

if (!(result.distanceM > 2000 && result.distanceM < 2100)) {
  throw new Error(`Unexpected KMZ calculated distance: ${result.distanceM}`);
}

console.log('route geometry KMZ normalization smoke test passed');
