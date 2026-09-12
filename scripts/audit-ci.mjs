import { readFile, readdir } from 'node:fs/promises';
import { extname, join } from 'node:path';
import process from 'node:process';
import { ACTION_PINS } from './ci-action-pins.mjs';

const workflowsDir = join(process.cwd(), '.github', 'workflows');
const strict = process.argv.includes('--strict');
const criticalPullRequestPaths = new Map([
  ['visual-prototype-check.yml', ['.nvmrc', 'package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml']],
  ['beta-browser-e2e.yml', ['.nvmrc', 'package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml']],
]);
const actionUsesPattern = /uses:\s*([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@([^\s#]+)(?:\s+#\s*([^\s]+))?/g;

const entries = (await readdir(workflowsDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && ['.yml', '.yaml'].includes(extname(entry.name)))
  .sort((a, b) => a.name.localeCompare(b.name));

const findings = [];
let actionReferences = 0;
let installCommands = 0;
let pullRequestWorkflows = 0;
let writePermissionWorkflows = 0;
let setupNodeWorkflows = 0;
let deployPagesWorkflows = 0;

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

function workflowJobs(source) {
  const lines = source.split(/\r?\n/);
  const jobsStart = lines.findIndex((line) => line === 'jobs:');
  if (jobsStart < 0) return [];

  const jobs = [];
  let current = null;
  for (let index = jobsStart + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line && !line.startsWith(' ')) break;

    const jobMatch = line.match(/^  ([A-Za-z0-9_-]+):\s*$/);
    if (jobMatch) {
      if (current) jobs.push({ ...current, source: current.lines.join('\n') });
      current = { name: jobMatch[1], lines: [line] };
      continue;
    }
    if (current) current.lines.push(line);
  }
  if (current) jobs.push({ ...current, source: current.lines.join('\n') });
  return jobs;
}

function jobPermissions(jobSource) {
  const lines = jobSource.split(/\r?\n/);
  const start = lines.findIndex((line) => line === '    permissions:');
  if (start < 0) return [];

  const permissions = [];
  for (let index = start + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (/^    \S/.test(line)) break;
    const match = line.match(/^      ([A-Za-z-]+):\s*(read|write|none)\s*$/);
    if (match) permissions.push({ scope: match[1], level: match[2] });
  }
  return permissions;
}

function pullRequestPaths(source) {
  const lines = source.split(/\r?\n/);
  const triggerStart = lines.findIndex((line) => line === '  pull_request:');
  if (triggerStart < 0) return [];

  let pathsStart = -1;
  for (let index = triggerStart + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (/^  \S/.test(line)) break;
    if (line === '    paths:') {
      pathsStart = index;
      break;
    }
  }
  if (pathsStart < 0) return [];

  const paths = [];
  for (let index = pathsStart + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (/^    \S/.test(line)) break;
    const match = line.match(/^      -\s+['"]?(.+?)['"]?\s*$/);
    if (match) paths.push(match[1]);
  }
  return paths;
}

for (const entry of entries) {
  const file = join(workflowsDir, entry.name);
  const source = await readFile(file, 'utf8');
  const hasPullRequest = /^  pull_request:\s*$/m.test(source);
  const hasPullRequestTarget = /^  pull_request_target:\s*$/m.test(source);
  const permissions = topLevelPermissions(source);
  const writeScopes = allWritePermissions(source);
  const usesSetupNode = /uses:\s*actions\/setup-node@/.test(source);
  const usesDeployPages = /uses:\s*actions\/deploy-pages@/.test(source);

  if (hasPullRequest) pullRequestWorkflows += 1;
  if (writeScopes.length) writePermissionWorkflows += 1;
  if (usesSetupNode) setupNodeWorkflows += 1;
  if (usesDeployPages) deployPagesWorkflows += 1;

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

  const requiredPaths = criticalPullRequestPaths.get(entry.name);
  if (requiredPaths) {
    const paths = new Set(pullRequestPaths(source));
    for (const requiredPath of requiredPaths) {
      if (!paths.has(requiredPath)) {
        addFinding('critical-trigger-gap', entry.name, `pull_request.paths must include ${requiredPath}`);
      }
    }
  }

  if (usesDeployPages) {
    const topLevelWrites = (permissions ?? []).filter((permission) => permission.level === 'write');
    if (topLevelWrites.length) {
      addFinding('pages-top-level-write', entry.name, 'Pages workflow must keep write permissions out of the top-level permissions block');
    }
    if (!(permissions ?? []).some((permission) => permission.scope === 'contents' && permission.level === 'read')) {
      addFinding('pages-missing-contents-read', entry.name, 'Pages workflow must declare top-level contents: read');
    }

    const jobs = workflowJobs(source);
    const deploymentJobs = jobs.filter((job) => /uses:\s*actions\/deploy-pages@/.test(job.source));
    if (deploymentJobs.length !== 1) {
      addFinding('pages-deploy-job-count', entry.name, `expected exactly one deploy-pages job, found ${deploymentJobs.length}`);
    } else {
      const deploymentJob = deploymentJobs[0];
      const deploymentPermissions = jobPermissions(deploymentJob.source);
      const writeSet = new Set(
        deploymentPermissions
          .filter((permission) => permission.level === 'write')
          .map((permission) => permission.scope),
      );
      for (const scope of ['pages', 'id-token']) {
        if (!writeSet.has(scope)) {
          addFinding('pages-deploy-permission', entry.name, `deploy job must declare ${scope}: write`);
        }
      }
      for (const scope of writeSet) {
        if (!['pages', 'id-token'].includes(scope)) {
          addFinding('pages-extra-write-permission', entry.name, `deploy job has unnecessary ${scope}: write`);
        }
      }

      for (const job of jobs) {
        if (job.name === deploymentJob.name) continue;
        const jobWrites = jobPermissions(job.source).filter((permission) => permission.level === 'write');
        if (jobWrites.length) {
          addFinding(
            'pages-nondeploy-write-permission',
            entry.name,
            `${job.name} job requests write access: ${jobWrites.map((permission) => permission.scope).join(', ')}`,
          );
        }
      }
    }
  }

  for (const match of source.matchAll(actionUsesPattern)) {
    actionReferences += 1;
    const action = match[1];
    const ref = match[2];
    const versionComment = match[3] ?? null;
    const pin = ACTION_PINS.get(action);

    if (!pin) {
      addFinding('unapproved-action', entry.name, `${action}@${ref} is not in scripts/ci-action-pins.mjs`);
      continue;
    }
    if (!/^[0-9a-f]{40}$/.test(ref)) {
      addFinding('mutable-action-ref', entry.name, `${action}@${ref} must use a full immutable commit SHA`);
      continue;
    }
    if (ref !== pin.sha) {
      addFinding('unapproved-action-sha', entry.name, `${action}@${ref} -> approved ${pin.sha}`);
    }
    if (versionComment !== pin.version) {
      addFinding('action-version-comment', entry.name, `${action}@${ref} must retain human-readable comment # ${pin.version}`);
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
  `CI audit: ${entries.length} workflows, ${actionReferences} immutable Action references, ${installCommands} pnpm install commands, ${setupNodeWorkflows} setup-node workflows, ${pullRequestWorkflows} pull_request workflows, ${writePermissionWorkflows} workflows with explicit write permissions, ${deployPagesWorkflows} Pages deploy workflows.`,
);
if (!findings.length) {
  console.log('CI audit OK: immutable Action pins, runtime source, critical triggers and least-privilege permissions are all enforced.');
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
