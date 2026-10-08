import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

// Fixture integrity only. No production adapter, cache or authorization logic.
const read = name => JSON.parse(readFileSync(new URL(`./fixtures/${name}.synthetic.json`, import.meta.url), 'utf8'));
const v1 = read('catalog-v1');
const v2 = read('catalog-v2');
const duplicates = read('duplicates');

test('fixtures preserve observed official structure and explicit synthetic identities', () => {
  for (const catalog of [v1, v2, duplicates]) {
    assert.ok(Array.isArray(catalog.Productos) && catalog.Productos.length > 0);
    for (const row of catalog.Productos) {
      assert.match(row.DATOSPRODUCTO.Num_Registro, /^SYN-\d{5}$/);
      assert.equal(typeof row.DATOSPRODUCTO.IdProducto, 'number');
      for (const key of ['COMPOSICION', 'USOS', 'OTRASDENOMINACIONES', 'OTROSNOMBRES']) assert.ok(Array.isArray(row[key]));
      assert.equal(typeof row.COMPOSICION[0]['Nombre Sustancia'], 'string');
      assert.equal(typeof row.USOS[0].CodigoCultivo, 'string');
      assert.equal(typeof row.USOS[0]['Unidad Medida dosis'], 'string');
    }
  }
});

test('official double-serialized envelope round-trips without changing content checksum', () => {
  const content = JSON.stringify(v1);
  const envelope = { Id: 1, Tipo: 'CEX', Fecha: '2026-10-02T00:00:00', Contenido: content };
  const wire = JSON.stringify(JSON.stringify(envelope));
  const decoded = JSON.parse(JSON.parse(wire));
  assert.deepEqual(JSON.parse(decoded.Contenido), v1);
  const hash = text => createHash('sha256').update(text, 'utf8').digest('hex');
  assert.equal(hash(decoded.Contenido), hash(content));
  assert.notEqual(hash(JSON.stringify(v2)), hash(content));
});

test('copper fixture covers substance search, accents, units and unknown fields', () => {
  const product = v1.Productos[0];
  assert.match(product.COMPOSICION[0]['Nombre Sustancia'], /COBRE/);
  assert.match(product.DATOSPRODUCTO.Nombre, /Á/);
  assert.equal(product.COMPOSICION[0].DescripcionNota, '% (EXPR. EN CU)');
  assert.equal(product.USOS[0]['Plazo Seguridad'], 'NP');
  assert.equal(product.USOS[0].TipoUsuario, '');
  assert.equal(product.USOS[0].Volumen_Min, 0);
});

test('duplicate fixture contains an exact repeat and a conflicting sale deadline', () => {
  assert.equal(new Set(duplicates.Productos.map(p => p.DATOSPRODUCTO.Num_Registro)).size, 1);
  assert.equal(new Set(duplicates.Productos.map(p => p.DATOSPRODUCTO.IdProducto)).size, 2);
  assert.deepEqual(duplicates.Productos[0], duplicates.Productos[2]);
  assert.notEqual(duplicates.Productos[0].DATOSPRODUCTO.Fecha_LimiteVenta, duplicates.Productos[1].DATOSPRODUCTO.Fecha_LimiteVenta);
});

test('successive fixtures express cancellation separately from disappearance', () => {
  assert.equal(v1.Productos[0].DATOSPRODUCTO.Estado, 'Vigente');
  assert.equal(v2.Productos[0].DATOSPRODUCTO.Estado, 'Cancelado');
  const removed = v1.Productos[1].DATOSPRODUCTO.Num_Registro;
  assert.ok(!v2.Productos.some(p => p.DATOSPRODUCTO.Num_Registro === removed));
  assert.equal(v1.Productos[1].DATOSPRODUCTO.Estado, 'Vigente');
});
