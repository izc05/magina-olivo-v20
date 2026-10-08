import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const observed = JSON.parse(readFileSync(new URL('./wire-fields.observed.json', import.meta.url), 'utf8'));
const fieldAt = row => observed.wireFields.find(f => f.row === row);

// Internal design guards only. Not a complete JSON serializer or remote schema validator.
function exactNumericToken(value, digits) {
  if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value) || value.length > digits) throw new Error('invalid_numeric_id');
  return BigInt(value).toString();
}
function exactlyOne(values) {
  return values.filter(v => v !== null && v !== undefined).length === 1;
}

test('source manifest retains source identity and exact wire field types', () => {
  assert.equal(observed.protocol, '3.11.4');
  assert.equal(observed.descriptorSha256, 'eec4e809732e17cd845343db1e0b7bb51507b8878c8f6c0f4d57039cac752217');
  assert.equal(new Set(observed.wireFields.map(f => f.row)).size, observed.wireFields.length);
  assert.deepEqual([fieldAt(277).field, fieldAt(277).typeRaw], ['IdAjenaTratamFito', 'number(10)']);
  assert.equal(fieldAt(285).typeRaw, 'number(16)');
  assert.equal(fieldAt(323).typeRaw, 'string(10)');
  assert.equal(fieldAt(327).typeRaw, 'number(8,3)');
});

test('16-digit identifier survives numeric-token serialization without Number conversion', () => {
  const id = '9999999999999999';
  assert.equal(Number.isSafeInteger(Number(id)), false);
  const wire = `{"CodigoDGC":${exactNumericToken(id, 16)}}`;
  assert.equal(wire, '{"CodigoDGC":9999999999999999}');
  assert.notEqual(String(JSON.parse(wire).CodigoDGC), id); // Ordinary JSON.parse would lose precision.
  assert.throws(() => exactNumericToken(Number(id), 16), /invalid_numeric_id/);
  assert.throws(() => exactNumericToken('10000000000000000', 16), /invalid_numeric_id/);
  assert.throws(() => exactNumericToken('SYN-UUID', 10), /invalid_numeric_id/);
});

test('wire dose/quantity uses XOR while source variable flags are both mandatory', () => {
  assert.equal(exactlyOne(['0.125', null]), true);
  assert.equal(exactlyOne([null, '0.00']), true); // Zero is present, never mistaken for absence.
  assert.equal(exactlyOne(['0.125', '1.00']), false);
  assert.equal(exactlyOne([null, undefined]), false);
  for (const variableId of [419, 420]) {
    assert.equal(observed.variables.find(v => v.variableId === variableId).obligationRaw, 'Obligatorio');
  }
});

test('official versus external DGC references are alternative identifiers', () => {
  assert.equal(exactlyOne(['1234567890123456', null]), true);
  assert.equal(exactlyOne([null, '9876543210123456']), true);
  assert.equal(exactlyOne(['1234567890123456', '9876543210123456']), false);
  assert.equal(exactlyOne([null, null]), false);
});

test('irrigation and soil evidence are kept separate from treatment descriptor', () => {
  assert.equal(fieldAt(589).field, 'Cantidad');
  assert.equal(fieldAt(590).field, 'UnidadMedida');
  assert.equal(fieldAt(501).field, 'ParametrosSuelo {');
  assert.equal(fieldAt(506).field, 'Ph');
  assert.equal(fieldAt(358).field, 'Eficacia');
});
