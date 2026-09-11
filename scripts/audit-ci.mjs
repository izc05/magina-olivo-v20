import { readFile, readdir } from 'node:fs/promises';
import { extname, join } from 'node:path';
import process from 'node:process';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const strict = process.argv.includes('--strict');
const expectedMajors = new Map([
  ['actions/checkout', 7],
  ['actions/setup-node', 7],
  ['pnpm/action-setup', 6],
  ['actions/cache', 6],
  ['actions/upload-artifact', 7],
  ['actions/configure-pages', 5],
  ['actions/upload-pages-artifact', 4],
  ['actions/deploy-pages', 4],
]);

const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

const findings = [];
let actionReferences = 0;
let installCommands = 0;
let pullRequestWorkflows = 0;
let writePermissionWorkflows = 0;
let setupNodeWorkflows = 0;

function addFinding(kind, file, detail) {
  findings.push({ kind, file, detail });
}

function topLevelPermissions(source) {
  const lines = source.split(/\r?\n/);
  const start = lines.findIndex((line) => line === 'permissions:');
  if (start < 0) return null;

  const permissions = [];
  for (let index = start + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line && !line.startsWith(' ')) break;
    const match = line.match(/^  ([A-Za-z-]+):\s*(read|write|none)\s*$/);
    if (match) permissions.push({ scope: match[1], level: match[2] });
  }
  return permissions;
}

function allWritePermissions(source) {
  return [...source.matchAll(/^\s+([A-Za-z-]+):\s*write\s*$/gm)].map((match) => match[1]);
}

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const source = await readFile(file, 'utf8');
  const hasPullRequest = /^  pull_request:\s*$/m.test(source);
  const hasPullRequestTarget = /^  pull_request_target:\s*$/m.test(source);
  const permissions = topLevelPermissions(source);
  const writeScopes = allWritePermissions(source);
  const usesSetupNode = /uses:\s*actions\/setup-node@v\d+/.test(source);

  if (hasPullRequest) pullRequestWorkflows += 1;
  if (writeScopes.length) writePermissionWorkflows += 1;
  if (usesSetupNode) setupNodeWorkflows += 1;

  if (hasPullRequestTarget) {
    addFinding('dangerous-trigger', entry.name, 'pull_request_target executes with base-repository privileges');
  }

  if (!permissions) {
    addFinding('implicit-permissions', entry.name, 'workflow does not declare an explicit top-level permissions block');
  }

  if (hasPullRequest && writeScopes.length) {
    addFinding(
      'pull-request-write-permission',
      entry.name,
      `pull_request workflow requests write access: ${[...new Set(writeScopes)].join(', ')}`,
    );
  }

  if (hasPullRequest && /\bsecrets\.[A-Za-z0-9_]+/.test(source)) {
    addFinding('pull-request-secret-reference', entry.name, 'pull_request workflow references repository secrets');
  }

  if (/^\s+node-version:\s*/m.test(source)) {
    addFinding('runtime-version-literal', entry.name, "use node-version-file: '.nvmrc' instead of duplicating a Node version");
  }
  if (usesSetupNode && !/node-version-file:\s*['"]?\.nvmrc['"]?/.test(source)) {
    addFinding('runtime-source-drift', entry.name, "actions/setup-node must read Node from .nvmrc");
  }

  for (const match of source.matchAll(/uses:\s*([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@v(\d+)/g)) {
    actionReferences += 1;
    const action = match[1];
    const major = Number(match[2]);
    const expected = expectedMajors.get(action);
    if (expected && major < expected) {
      addFinding('outdated-action', entry.name, `${action}@v${major} -> expected v${expected}+`);
    }
  }

  for (const line of source.split(/\r?\n/)) {
    if (!line.includes('pnpm install')) continue;
    installCommands += 1;
    if (!line.includes('--frozen-lockfile')) {
      addFinding('mutable-install', entry.name, line.trim());
    }
  }
}

const counts = new Map();
for (const finding of findings) {
  counts.set(finding.kind, (counts.get(finding.kind) ?? 0) + 1);
}

console.log(
  `CI audit: ${entries.length} workflows, ${actionReferences} action references, ${installCommands} pnpm install commands, ${setupNodeWorkflows} setup-node workflows, ${pullRequestWorkflows} pull_request workflows, ${writePermissionWorkflows} workflows with explicit write permissions.`,
);
if (!findings.length) {
  console.log('CI audit OK: no known modernization, runtime-source or permission debt detected.');
  process.exit(0);
}

console.log('CI audit findings:');
for (const finding of findings) {
  console.log(`- [${finding.kind}] ${finding.file}: ${finding.detail}`);
}
console.log(`Summary: ${[...counts.entries()].map(([kind, count]) => `${kind}=${count}`).join(', ')}`);

if (strict) {
  process.exitCode = 1;
} else {
  console.log('Audit is informational. Use --strict to make findings fail CI.');
}
