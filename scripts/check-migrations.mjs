import { readdir } from 'node:fs/promises';
import { join } from 'node:path';
import process from 'node:process';

const migrationsDir = join(process.cwd(), 'database', 'migrations');
const entries = (await readdir(migrationsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && entry.name.endsWith('.sql'))
  .map((entry) => entry.name)
  .sort((a, b) => a.localeCompare(b));

if (!entries.length) {
  console.error('Migration contract failed: database/migrations contains no SQL migrations.');
  process.exit(1);
}

const filenamePattern = /^(\d{4})_([a-z0-9]+(?:_[a-z0-9]+)*)\.sql$/;
const bySequence = new Map();
const invalid = [];
const parsed = [];

for (const filename of entries) {
  const match = filename.match(filenamePattern);
  if (!match) {
    invalid.push(filename);
    continue;
  }

  const sequence = Number(match[1]);
  if (sequence < 1) {
    invalid.push(filename);
    continue;
  }

  const existing = bySequence.get(sequence) ?? [];
  existing.push(filename);
  bySequence.set(sequence, existing);
  parsed.push({ filename, sequence });
}

const duplicates = [...bySequence.entries()]
  .filter(([, filenames]) => filenames.length > 1)
  .sort(([left], [right]) => left - right);

const findings = [];
for (const filename of invalid) {
  findings.push(`invalid filename: ${filename} (expected NNNN_snake_case.sql)`);
}
for (const [sequence, filenames] of duplicates) {
  findings.push(`duplicate sequence ${String(sequence).padStart(4, '0')}: ${filenames.join(', ')}`);
}

if (findings.length) {
  console.error('Migration contract failed:');
  for (const finding of findings) console.error(`- ${finding}`);
  process.exit(1);
}

const numericOrder = [...parsed].sort((a, b) => a.sequence - b.sequence || a.filename.localeCompare(b.filename));
const lexicographicOrder = parsed.map(({ filename }) => filename);
const expectedOrder = numericOrder.map(({ filename }) => filename);
if (JSON.stringify(lexicographicOrder) !== JSON.stringify(expectedOrder)) {
  console.error('Migration contract failed: lexicographic filename order differs from numeric sequence order.');
  process.exit(1);
}

const first = numericOrder[0].sequence;
const last = numericOrder[numericOrder.length - 1].sequence;
const next = String(last + 1).padStart(4, '0');
console.log(
  `Migration contract OK: ${numericOrder.length} migrations, range ${String(first).padStart(4, '0')}..${String(last).padStart(4, '0')}; next suggested prefix ${next}.`,
);
console.log('Sequence gaps are allowed; duplicate numeric prefixes are not.');
