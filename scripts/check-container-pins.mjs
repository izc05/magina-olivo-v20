import { readFileSync } from 'node:fs';

const dockerfiles = [
  'deploy/staging/Dockerfile.api',
  'deploy/staging/Dockerfile.worker',
  'deploy/staging/Dockerfile.web',
];

const composeFiles = ['deploy/staging/docker-compose.yml'];
const shellFiles = ['deploy/staging/deploy-host.sh'];
const digestPattern = /@sha256:[0-9a-f]{64}$/;

function fail(message) {
  throw new Error(`Container pin contract failed: ${message}`);
}

function assertPinnedReference(reference, source) {
  if (!digestPattern.test(reference)) {
    fail(`${source} uses mutable image reference '${reference}'. Keep a readable tag and append @sha256:<64 hex>.`);
  }

  const imageAndTag = reference.slice(0, reference.indexOf('@sha256:'));
  const lastSlash = imageAndTag.lastIndexOf('/');
  const lastColon = imageAndTag.lastIndexOf(':');
  if (lastColon <= lastSlash || lastColon === imageAndTag.length - 1) {
    fail(`${source} must keep an explicit tag before the digest: '${reference}'.`);
  }

  const tag = imageAndTag.slice(lastColon + 1);
  if (tag === 'latest') {
    fail(`${source} must not use the floating 'latest' tag even when a digest is present.`);
  }
}

let checked = 0;

for (const file of dockerfiles) {
  const source = readFileSync(file, 'utf8');
  const aliases = new Set();

  for (const [index, raw] of source.split(/\r?\n/).entries()) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^FROM(?:\s+--platform=\S+)?\s+(\S+)(?:\s+AS\s+([A-Za-z0-9._-]+))?$/i);
    if (!match) continue;

    const [, reference, alias] = match;
    if (!aliases.has(reference)) {
      assertPinnedReference(reference, `${file}:${index + 1}`);
      checked += 1;
    }
    if (alias) aliases.add(alias);
  }
}

for (const file of composeFiles) {
  const source = readFileSync(file, 'utf8');
  for (const [index, raw] of source.split(/\r?\n/).entries()) {
    const match = raw.match(/^\s*image:\s*["']?([^"'#\s]+)["']?\s*(?:#.*)?$/);
    if (!match) continue;
    assertPinnedReference(match[1], `${file}:${index + 1}`);
    checked += 1;
  }
}

for (const file of shellFiles) {
  const source = readFileSync(file, 'utf8');
  const assignments = [...source.matchAll(/^([A-Z0-9_]+_IMAGE)="([^"]+)"$/gm)];
  if (assignments.length === 0) {
    fail(`${file} must declare external docker-run images through *_IMAGE variables so they can be audited.`);
  }
  for (const match of assignments) {
    assertPinnedReference(match[2], `${file}:${match[1]}`);
    checked += 1;
  }

  const rawDockerRuns = source.match(/docker\s+run[\s\S]*?\n\s+([A-Za-z0-9./_-]+:[^\s\\]+)/g) ?? [];
  for (const invocation of rawDockerRuns) {
    if (!invocation.includes('$') && !digestPattern.test(invocation)) {
      fail(`${file} contains a docker run image that is not routed through an audited pinned variable.`);
    }
  }
}

if (checked < 1) fail('no external container references were found to audit');
console.log(`Container pin contract OK: ${checked} staging image references use explicit tags plus immutable sha256 digests.`);
