import { readFile, readdir, writeFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import process from 'node:process';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const write = process.argv.includes('--write');
const lockfileWorkflow = 'lockfile-generation.yml';
const replacements = [
  [/actions\/checkout@v4/g, 'actions/checkout@v7'],
  [/actions\/setup-node@v4/g, 'actions/setup-node@v7'],
  [/pnpm\/action-setup@v4/g, 'pnpm/action-setup@v6'],
  [/actions\/upload-artifact@v4/g, 'actions/upload-artifact@v7'],
];

const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

let changedFiles = 0;
let replacementsApplied = 0;

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const original = await readFile(file, 'utf8');
  let next = original;

  for (const [pattern, replacement] of replacements) {
    const matches = next.match(pattern)?.length ?? 0;
    if (matches) {
      replacementsApplied += matches;
      next = next.replace(pattern, replacement);
    }
  }

  if (entry.name !== lockfileWorkflow) {
    const mutableMatches = next.match(/pnpm install --no-frozen-lockfile/g)?.length ?? 0;
    if (mutableMatches) {
      replacementsApplied += mutableMatches;
      next = next.replace(/pnpm install --no-frozen-lockfile/g, 'pnpm install --frozen-lockfile');
    }
  }

  if (next === original) continue;
  changedFiles += 1;
  console.log(`${write ? 'updated' : 'would update'} ${entry.name}`);
  if (write) await writeFile(file, next);
}

console.log(`CI modernization: ${changedFiles} workflow files, ${replacementsApplied} replacements${write ? ' applied' : ' pending'}.`);
if (!write && changedFiles) {
  console.log('Run with --write to apply the mechanical migration.');
}
