import { readFile, readdir } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import process from 'node:process';

const root = process.cwd();
const sourceRoots = ['apps', 'packages'];
const sourceExtensions = new Set(['.js', '.cjs', '.mjs', '.ts', '.tsx']);
const ignoredDirectories = new Set(['node_modules', 'dist', '.next', 'out', 'coverage', 'test-results', 'playwright-report']);
const envPattern = /process\.env(?:\.([A-Z][A-Z0-9_]*)|\[['"]([A-Z][A-Z0-9_]*)['"]\])/g;

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (ignoredDirectories.has(entry.name)) continue;
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await sourceFiles(path));
    else if (entry.isFile() && sourceExtensions.has(extname(entry.name))) files.push(path);
  }
  return files;
}

function documentedEnvironmentVariables(example) {
  const variables = new Set();
  for (const line of example.split(/\r?\n/)) {
    const match = line.match(/^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=/);
    if (match?.[1]) variables.add(match[1]);
  }
  return variables;
}

const examplePath = join(root, '.env.example');
const example = await readFile(examplePath, 'utf8');
const documented = documentedEnvironmentVariables(example);
const used = new Map();

for (const sourceRoot of sourceRoots) {
  const directory = join(root, sourceRoot);
  for (const file of await sourceFiles(directory)) {
    const source = await readFile(file, 'utf8');
    for (const match of source.matchAll(envPattern)) {
      const name = match[1] ?? match[2];
      if (!name) continue;
      const locations = used.get(name) ?? [];
      locations.push(relative(root, file));
      used.set(name, locations);
    }
  }
}

const missing = [...used.keys()].filter((name) => !documented.has(name)).sort();
if (missing.length) {
  console.error('Environment contract failed. Add these runtime variables to .env.example:');
  for (const name of missing) {
    const locations = [...new Set(used.get(name) ?? [])].sort();
    console.error(`- ${name}: ${locations.join(', ')}`);
  }
  process.exitCode = 1;
} else {
  console.log(`Environment contract OK: ${used.size} runtime variables are documented in .env.example.`);
}
