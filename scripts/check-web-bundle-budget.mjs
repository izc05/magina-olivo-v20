import { readdir, stat } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../apps/web/out/', import.meta.url));
const maxSingleBytes = 2.5 * 1024 * 1024;
const maxTotalBytes = 10 * 1024 * 1024;

async function filesUnder(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesUnder(path) : [path];
  }));
  return nested.flat();
}

function mib(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(2)} MiB`;
}

let files;
try {
  files = await filesUnder(root);
} catch (error) {
  console.error('Web export output is missing. Run @magina/web build before the bundle budget check.');
  throw error;
}

const jsFiles = files.filter((path) => path.endsWith('.js'));
if (!jsFiles.length) throw new Error('No JavaScript files found in apps/web/out');

const sizes = await Promise.all(jsFiles.map(async (path) => ({
  path,
  bytes: (await stat(path)).size,
})));
sizes.sort((a, b) => b.bytes - a.bytes);

const total = sizes.reduce((sum, item) => sum + item.bytes, 0);
const largest = sizes[0];

console.log(`Exported JS files: ${sizes.length}`);
console.log(`Total exported JS: ${mib(total)} / budget ${mib(maxTotalBytes)}`);
console.log(`Largest JS chunk: ${mib(largest.bytes)} / budget ${mib(maxSingleBytes)} (${relative(root, largest.path)})`);
console.log('Largest 8 JS files:');
for (const item of sizes.slice(0, 8)) console.log(`  ${mib(item.bytes)}  ${relative(root, item.path)}`);

if (largest.bytes > maxSingleBytes) {
  throw new Error(`Largest JS file exceeds budget: ${relative(root, largest.path)} is ${mib(largest.bytes)}`);
}
if (total > maxTotalBytes) {
  throw new Error(`Total exported JS exceeds budget: ${mib(total)}`);
}
