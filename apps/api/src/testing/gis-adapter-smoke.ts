import assert from 'node:assert/strict';
import {
  buildCatastroBboxUrl,
  buildCatastroReferenceUrl,
  parseCatastroGml,
  validateCadastralReference,
  validateCatastroBbox,
} from '../gis/catastro.js';
import {
  buildSigpacRecintoByIdUrl,
  buildSigpacRecintosUrl,
  normalizeSigpacFeature,
  validateSigpacBbox,
  validateSigpacFeatureId,
} from '../gis/sigpac.js';

const cadastralReference = '23044A00100001';
assert.equal(validateCadastralReference(cadastralReference), true);
assert.equal(validateCadastralReference('../etc/passwd'), false);

const catastroBboxUrl = new URL(buildCatastroBboxUrl({
  minLon: -3.51,
  minLat: 37.70,
  maxLon: -3.50,
  maxLat: 37.71,
}));
assert.equal(catastroBboxUrl.origin, 'https://ovc.catastro.meh.es');
assert.equal(catastroBboxUrl.pathname, '/INSPIRE/wfsCP.aspx');
assert.equal(catastroBboxUrl.searchParams.get('srsName'), 'EPSG::25830');
assert.equal(catastroBboxUrl.searchParams.get('typenames'), 'cp:CadastralParcel');
assert.equal(catastroBboxUrl.searchParams.get('count'), '80');

const catastroReferenceUrl = new URL(buildCatastroReferenceUrl(cadastralReference.toLowerCase()));
assert.equal(catastroReferenceUrl.searchParams.get('STOREDQUERY_ID'), 'GetParcel');
assert.equal(catastroReferenceUrl.searchParams.get('REFCAT'), cadastralReference);
assert.equal(catastroReferenceUrl.searchParams.get('srsName'), 'EPSG::25830');

assert.match(
  validateCatastroBbox({ minLon: -3.50, minLat: 37.70, maxLon: -3.40, maxLat: 37.71 }) ?? '',
  /maximum span/,
);
assert.match(
  validateCatastroBbox({ minLon: -3.40, minLat: 37.70, maxLon: -3.50, maxLat: 37.71 }) ?? '',
  /inverted/,
);

const syntheticCatastroGml = `<?xml version="1.0" encoding="UTF-8"?>
<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0" xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:cp="http://inspire.ec.europa.eu/schemas/cp/4.0">
  <wfs:member>
    <cp:CadastralParcel gml:id="CP.${cadastralReference}">
      <cp:nationalCadastralReference>${cadastralReference}</cp:nationalCadastralReference>
      <cp:label>Parcela de prueba</cp:label>
      <cp:areaValue>12000</cp:areaValue>
      <cp:beginLifespanVersion>2026-01-01T00:00:00Z</cp:beginLifespanVersion>
      <cp:geometry>
        <gml:Polygon srsName="EPSG::25830">
          <gml:exterior><gml:LinearRing>
            <gml:posList>450000 4173000 450100 4173000 450100 4173100 450000 4173100 450000 4173000</gml:posList>
          </gml:LinearRing></gml:exterior>
        </gml:Polygon>
      </cp:geometry>
    </cp:CadastralParcel>
  </wfs:member>
</wfs:FeatureCollection>`;

const parsedCatastro = parseCatastroGml(syntheticCatastroGml);
assert.equal(parsedCatastro.length, 1);
assert.equal(parsedCatastro[0]?.nationalCadastralReference, cadastralReference);
assert.equal(parsedCatastro[0]?.areaM2, 12000);
assert.equal(parsedCatastro[0]?.geometry.type, 'Polygon');
const catastroPoint = parsedCatastro[0]?.geometry.coordinates[0]?.[0];
assert.ok(catastroPoint);
assert.ok(catastroPoint[0]! > -10 && catastroPoint[0]! < 5, `Unexpected longitude ${catastroPoint[0]}`);
assert.ok(catastroPoint[1]! > 35 && catastroPoint[1]! < 44, `Unexpected latitude ${catastroPoint[1]}`);

const sigpacBboxUrl = new URL(buildSigpacRecintosUrl({
  minLon: -3.51,
  minLat: 37.70,
  maxLon: -3.50,
  maxLat: 37.71,
}));
assert.equal(sigpacBboxUrl.origin, 'https://sigpac-hubcloud.es');
assert.equal(sigpacBboxUrl.pathname, '/ogcapi/collections/recintos/items');
assert.equal(sigpacBboxUrl.searchParams.get('f'), 'json');
assert.equal(sigpacBboxUrl.searchParams.get('limit'), '100');

assert.equal(validateSigpacFeatureId('233788127'), true);
assert.equal(validateSigpacFeatureId('../items'), false);
assert.equal(validateSigpacFeatureId('123?f=json'), false);
const sigpacItemUrl = new URL(buildSigpacRecintoByIdUrl('233788127'));
assert.equal(sigpacItemUrl.pathname, '/ogcapi/collections/recintos/items/233788127');

assert.match(
  validateSigpacBbox({ minLon: -3.50, minLat: 37.70, maxLon: -3.40, maxLat: 37.71 }) ?? '',
  /maximum span/,
);

const normalizedSigpac = normalizeSigpacFeature({
  id: 233788127,
  properties: {
    provincia: 23,
    municipio: 44,
    poligono: 12,
    parcela: 345,
    recinto: 2,
    pendiente_media: 18.5,
    altitud: 740,
    dn_surface: 8000,
    uso_sigpac: 'OV',
    unexpected_private_field: 'ignored',
  },
  geometry: {
    type: 'Polygon',
    coordinates: [[
      [-3.5000, 37.7000],
      [-3.4990, 37.7000],
      [-3.4990, 37.7010],
      [-3.5000, 37.7010],
      [-3.5000, 37.7000],
    ]],
  },
});
assert.ok(normalizedSigpac);
assert.equal(normalizedSigpac.id, '233788127');
assert.equal(normalizedSigpac.provincia, 23);
assert.equal(normalizedSigpac.municipio, 44);
assert.equal(normalizedSigpac.poligono, 12);
assert.equal(normalizedSigpac.parcela, 345);
assert.equal(normalizedSigpac.recinto, 2);
assert.equal(normalizedSigpac.surfaceM2, 8000);
assert.equal(normalizedSigpac.usoSigpac, 'OV');
assert.equal('unexpected_private_field' in normalizedSigpac, false);

console.log('GIS_ADAPTER_SMOKE_OK');
