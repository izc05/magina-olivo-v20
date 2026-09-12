import { readFile, readdir, writeFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import process from 'node:process';
import { ACTION_PINS } from './ci-action-pins.mjs';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

const usesPattern = /(uses:\s*)([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@([^\s#]+)(?:\s+#\s*([^\s]+))?/g;
let references = 0;
let replacements = 0;
const seen = new Map();

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const source = await readFile(file, 'utf8');
  const next = source.replace(usesPattern, (full, prefix, action, ref) => {
    references += 1;
    const pin = ACTION_PINS.get(action);
    if (!pin) {
      throw new Error(`${entry.name}: unapproved external Action ${action}@${ref}`);
    }
    seen.set(action, (seen.get(action) ?? 0) + 1);
    const replacement = `${prefix}${action}@${pin.sha} # ${pin.version}`;
    if (full !== replacement) replacements += 1;
    return replacement;
  });

  if (next !== source) await writeFile(file, next);
}

const missingPins = [...ACTION_PINS.keys()].filter((action) => !seen.has(action));
if (missingPins.length) {
  console.log(`Approved pins not currently used: ${missingPins.join(', ')}`);
}

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const source = await readFile(file, 'utf8');
  for (const match of source.matchAll(usesPattern)) {
    const [, , action, ref, versionComment] = match;
    const pin = ACTION_PINS.get(action);
    if (!pin) throw new Error(`${entry.name}: unapproved external Action ${action}@${ref}`);
    if (ref !== pin.sha) throw new Error(`${entry.name}: ${action} is not pinned to approved SHA ${pin.sha}`);
    if (versionComment !== pin.version) {
      throw new Error(`${entry.name}: ${action}@${ref} must keep version comment # ${pin.version}`);
    }
  }
}

console.log(`Pinned CI Actions: ${references} references across ${entries.length} workflows; ${replacements} replacements.`);
console.log(`Approved Actions in use: ${[...seen.entries()].map(([action, count]) => `${action}=${count}`).join(', ')}`);
