import assert from 'node:assert/strict';
import test from 'node:test';
import {
  parseAemetCapAlert,
  selectAemetOfficialAlertForCoordinates,
} from '../aemet-warnings.js';

const yellowJaen = `<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <identifier>yellow-jaen</identifier>
  <info>
    <language>es-ES</language>
    <event>Viento</event>
    <headline>Aviso amarillo por viento</headline>
    <description>Rachas fuertes en zonas de Jaén.</description>
    <onset>2026-09-15T16:00:00+02:00</onset>
    <expires>2026-09-15T23:59:00+02:00</expires>
    <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>amarillo</value></parameter>
    <area>
      <areaDesc>Capital y Montes de Jaén</areaDesc>
      <polygon>37.40,-4.10 38.15,-4.10 38.15,-2.90 37.40,-2.90 37.40,-4.10</polygon>
    </area>
  </info>
</alert>`;

const orangeElsewhere = `<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <identifier>orange-cadiz</identifier>
  <info>
    <language>es-ES</language>
    <event>Lluvias</event>
    <headline>Aviso naranja por lluvias</headline>
    <description>Lluvias intensas lejos de Sierra Mágina.</description>
    <onset>2026-09-15T16:00:00+02:00</onset>
    <expires>2026-09-15T23:59:00+02:00</expires>
    <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>naranja</value></parameter>
    <area>
      <areaDesc>Litoral gaditano</areaDesc>
      <polygon>36.00,-6.80 36.80,-6.80 36.80,-5.50 36.00,-5.50 36.00,-6.80</polygon>
    </area>
  </info>
</alert>`;

const greenJaen = yellowJaen
  .replace('yellow-jaen', 'green-jaen')
  .replace('amarillo', 'verde')
  .replace('Aviso amarillo por viento', 'Sin aviso');

test('parses Spanish AEMET CAP metadata and polygon', () => {
  const alert = parseAemetCapAlert(yellowJaen);
  assert.equal(alert?.id, 'yellow-jaen');
  assert.equal(alert?.level, 'yellow');
  assert.equal(alert?.headline, 'Aviso amarillo por viento');
  assert.equal(alert?.areas[0]?.description, 'Capital y Montes de Jaén');
  assert.equal(alert?.areas[0]?.polygons.length, 1);
});

test('selects only non-green warnings whose polygon contains the user coordinates', () => {
  const selected = selectAemetOfficialAlertForCoordinates(
    [yellowJaen, orangeElsewhere, greenJaen],
    37.73,
    -3.41,
  );

  assert.equal(selected?.source, 'AEMET');
  assert.equal(selected?.level, 'yellow');
  assert.equal(selected?.title, 'Aviso amarillo por viento');
});

test('returns null when no official warning covers the coordinates', () => {
  const selected = selectAemetOfficialAlertForCoordinates([orangeElsewhere], 37.73, -3.41);
  assert.equal(selected, null);
});
