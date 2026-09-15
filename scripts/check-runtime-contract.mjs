import { readFile } from 'node:fs/promises';
import process from 'node:process';

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
const nvmrc = (await readFile(new URL('../.nvmrc', import.meta.url), 'utf8')).trim().replace(/^v/, '');

const expectedNodeMajor = Number(nvmrc.split('.')[0]);
const actualNodeMajor = Number(process.versions.node.split('.')[0]);
const declaredNodeEngine = packageJson.engines?.node;
const declaredPnpmEngine = packageJson.engines?.pnpm;
const packageManager = packageJson.packageManager;

const failures = [];

if (!Number.isInteger(expectedNodeMajor) || expectedNodeMajor <= 0) {
  failures.push(`.nvmrc no contiene una versión Node válida: ${nvmrc || '(vacío)'}`);
} else {
  if (actualNodeMajor !== expectedNodeMajor) {
    failures.push(`Node ${process.versions.node} no cumple .nvmrc (${nvmrc}). Usa Node ${expectedNodeMajor}.x.`);
  }

  const lowerBound = `>=${expectedNodeMajor}`;
  const upperBound = `<${expectedNodeMajor + 1}`;
  if (typeof declaredNodeEngine !== 'string' || !declaredNodeEngine.includes(lowerBound) || !declaredNodeEngine.includes(upperBound)) {
    failures.push(`engines.node (${declaredNodeEngine ?? 'sin declarar'}) no está alineado con .nvmrc (${nvmrc}).`);
  }
}

const packageManagerMatch = typeof packageManager === 'string' ? packageManager.match(/^pnpm@(\d+)\.(\d+)\.(\d+)$/) : null;
if (!packageManagerMatch) {
  failures.push(`packageManager debe fijar una versión exacta de pnpm; valor actual: ${packageManager ?? 'sin declarar'}.`);
} else {
  const [, major, minor, patch] = packageManagerMatch;
  const pinnedVersion = `${major}.${minor}.${patch}`;
  if (typeof declaredPnpmEngine !== 'string' || !declaredPnpmEngine.includes(`>=${pinnedVersion}`) || !declaredPnpmEngine.includes(`<${Number(major) + 1}`)) {
    failures.push(`engines.pnpm (${declaredPnpmEngine ?? 'sin declarar'}) no está alineado con packageManager (${packageManager}).`);
  }

  const userAgent = process.env.npm_config_user_agent ?? '';
  const runningPnpm = userAgent.match(/(?:^|\s)pnpm\/(\d+)\.(\d+)\.(\d+)/)?.slice(1);
  if (runningPnpm) {
    const [runningMajor, runningMinor, runningPatch] = runningPnpm;
    const runningVersion = `${runningMajor}.${runningMinor}.${runningPatch}`;
    if (runningVersion !== pinnedVersion) {
      failures.push(`pnpm ${runningVersion} no coincide con packageManager (${packageManager}). Usa pnpm ${pinnedVersion}.`);
    }
  }
}

if (failures.length) {
  console.error('Contrato de runtime V20 inválido:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`Runtime V20 válido: Node ${process.versions.node}; .nvmrc=${nvmrc}; ${packageManager}; engines.node=${declaredNodeEngine}; engines.pnpm=${declaredPnpmEngine}.`);
