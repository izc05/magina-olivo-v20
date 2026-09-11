import { readFile, readdir } from 'node:fs/promises';
import { basename, extname, join } from 'node:path';
import process from 'node:process';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const strict = process.argv.includes('--strict');
const expectedMajors = new Map([
  ['actions/checkout', 7],
  ['actions/setup-node', 7],
  ['pnpm/action-setup', 6],
  ['actions/upload-artifact', 7],
]);
const mutableInstallAllowlist = new Set(['lockfile-generation.yml']);

const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

const findings = [];
let actionReferences = 0;
let installCommands = 0;

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const source = await readFile(file, 'utf8');

  for (const match of source.matchAll(/uses:\s*([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@v(\d+)/g)) {
    actionReferences += 1;
    const action = match[1];
    const major = Number(match[2]);
    const expected = expectedMajors.get(action);
    if (expected && major < expected) {
      findings.push({
        kind: 'outdated-action',
        file: entry.name,
        detail: `${action}@v${major} -> expected v${expected}+`,
      });
    }
  }

  for (const line of source.split(/\r?\n/)) {
    if (!line.includes('pnpm install')) continue;
    installCommands += 1;
    if (mutableInstallAllowlist.has(basename(file))) continue;
    if (!line.includes('--frozen-lockfile')) {
      findings.push({
        kind: 'mutable-install',
        file: entry.name,
        detail: line.trim(),
      });
    }
  }
}

const counts = new Map();
for (const finding of findings) {
  counts.set(finding.kind, (counts.get(finding.kind) ?? 0) + 1);
}

console.log(`CI audit: ${entries.length} workflows, ${actionReferences} action references, ${installCommands} pnpm install commands.`);
if (!findings.length) {
  console.log('CI audit OK: no known modernization debt detected.');
  process.exit(0);
}

console.log('CI modernization debt:');
for (const finding of findings) {
  console.log(`- [${finding.kind}] ${finding.file}: ${finding.detail}`);
}
console.log(`Summary: ${[...counts.entries()].map(([kind, count]) => `${kind}=${count}`).join(', ')}`);

if (strict) {
  process.exitCode = 1;
} else {
  console.log('Audit is informational for now. Use --strict once legacy workflows have been migrated.');
}
