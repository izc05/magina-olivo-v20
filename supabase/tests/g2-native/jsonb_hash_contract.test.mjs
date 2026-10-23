// G2.4 diagnostic only: compare PostgreSQL JSONB hashing against raw JSON bytes.
// Run: node --test supabase/tests/g2-native/jsonb_hash_contract.test.mjs
// Requires G2_NATIVE_WORK and the disposable Docker container from run.sh.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { validateTarget } from './safety.mjs';

const work = process.env.G2_NATIVE_WORK;
assert.ok(work, 'Use the disposable G2 native run.sh runner');
const status = JSON.parse(readFileSync(join(work, 'status.json'), 'utf8'));
const container = validateTarget(
  process.env.G2_NATIVE_PROJECT,
  status.API_URL,
  readFileSync(join(work, 'supabase/config.toml'), 'utf8')
);
const args = ['exec', '-i', container, 'psql', '-X', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'];
const quote = value => "'" + value.replaceAll("'", "''") + "'";
const rawHash = value => createHash('sha256').update(value, 'utf8').digest('hex');
const serverHash = value => execFileSync('docker', args, {
  input: `SELECT encode(sha256(convert_to((${quote(value)})::jsonb::text, 'UTF8')), 'hex');\n`,
  encoding: 'utf8',
  timeout: 15000,
  stdio: ['pipe', 'pipe', 'pipe']
}).trim();

test('PostgreSQL JSONB hashes are not assumed equal to raw JSON UTF-8', () => {
  const vectors = [
    '{"a":1,"b":2}',
    '{"b":2,"a":1}',
    '{ "a" : 1, "b" : 2 }',
    '{"unicode":"olivar 🌿","null":null}',
    '{"escape":"\\u00f1","number":1.0}'
  ];
  let differences = 0;
  for (const payload of vectors) {
    const actual = serverHash(payload);
    assert.match(actual, /^[0-9a-f]{64}$/);
    if (actual !== rawHash(payload)) differences++;
  }
  assert.ok(differences > 0, 'Expected at least one JSONB normalization mismatch');
});

test('JSONB canonicalizes key order and insignificant whitespace before hashing', () => {
  const compact = '{"a":1,"b":2}';
  const reversed = '{"b":2,"a":1}';
  const spaced = '{ "a" : 1, "b" : 2 }';
  assert.notEqual(rawHash(compact), rawHash(reversed));
  assert.notEqual(rawHash(compact), rawHash(spaced));
  assert.equal(serverHash(compact), serverHash(reversed));
  assert.equal(serverHash(compact), serverHash(spaced));
});

test('distinct semantic JSON values are not accepted as identical hashes', () => {
  assert.notEqual(serverHash('{"netGrams":1000}'), serverHash('{"netGrams":1001}'));
  assert.notEqual(serverHash('{"deleted":false}'), serverHash('{"deleted":true}'));
});


test('shared Android delivery vectors match real PostgreSQL text and hash exactly', () => {
  const vectors = JSON.parse(readFileSync(new URL(
    '../../../app/src/test/resources/g2-delivery-payload-hash-vectors.json', import.meta.url
  ), 'utf8'));
  assert.equal(vectors.length, 9);
  for (const vector of vectors) {
    const normalized = execFileSync('docker', args, {
      input: 'SELECT json_build_object(\'text\', (' + quote(vector.payload) + ')::jsonb::text)::text;',
      encoding: 'utf8', timeout: 15000, stdio: ['pipe', 'pipe', 'pipe']
    }).trim();
    assert.equal(JSON.parse(normalized).text, vector.postgresText, vector.name);
    assert.equal(rawHash(vector.postgresText), vector.sha256, vector.name);
    assert.equal(serverHash(vector.payload), vector.sha256, vector.name);
  }
});
