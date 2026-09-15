import { readFile, readdir, writeFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import process from 'node:process';
import { ACTION_PINS } from './ci-action-pins.mjs';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const write = process.argv.includes('--write');
const nodeVersionLiteralPattern = /^(\s*)node-version:\s*['"]?\d+(?:\.\d+){0,2}['"]?\s*$/gm;
const actionUsesPattern = /(uses:\s*)([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@([^\s#]+)(?:\s+#\s*([^\s]+))?/g;

const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

let changedFiles = 0;
let replacementsApplied = 0;

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const original = await readFile(file, 'utf8');
  let next = original;

  const runtimeMatches = next.match(nodeVersionLiteralPattern)?.length ?? 0;
  if (runtimeMatches) {
    replacementsApplied += runtimeMatches;
    next = next.replace(nodeVersionLiteralPattern, "$1node-version-file: '.nvmrc'");
  }

  const mutableMatches = next.match(/pnpm install --no-frozen-lockfile/g)?.length ?? 0;
  if (mutableMatches) {
    replacementsApplied += mutableMatches;
    next = next.replace(/pnpm install --no-frozen-lockfile/g, 'pnpm install --frozen-lockfile');
  }

  next = next.replace(actionUsesPattern, (full, prefix, action) => {
    const pin = ACTION_PINS.get(action);
    if (!pin) {
      throw new Error(`${entry.name}: unapproved external Action ${action}; review it before adding a pin`);
    }
    const replacement = `${prefix}${action}@${pin.sha} # ${pin.version}`;
    if (full !== replacement) replacementsApplied += 1;
    return replacement;
  });

  if (next === original) continue;
  changedFiles += 1;
  console.log(`${write ? 'updated' : 'would update'} ${entry.name}`);
  if (write) await writeFile(file, next);
}

console.log(`CI modernization: ${changedFiles} workflow files, ${replacementsApplied} replacements${write ? ' applied' : ' pending'}.`);
if (!write && changedFiles) {
  console.log('Run with --write to apply the mechanical migration and immutable Action pins.');
}
