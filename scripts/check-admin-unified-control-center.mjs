import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const modulesPage = readFileSync(resolve(root, 'apps/web/src/app/admin/modulos/page.tsx'), 'utf8');
const adminPage = readFileSync(resolve(root, 'apps/web/src/app/admin/page.tsx'), 'utf8');

const requiredModuleIds = [
  'operations',
  'analytics',
  'management',
  'municipalities',
  'businesses',
  'experiences',
  'magina-pass',
  'routes',
  'route-community',
  'route-sponsorships',
  'adventure',
];

const requiredAvailableRoutes = [
  '/admin/operaciones',
  '/admin/analitica',
  '/admin/gestion',
  '/admin/ayuntamientos',
  '/admin/territorio',
  '/admin/media',
  '/admin/web',
];

const failures = [];

for (const id of requiredModuleIds) {
  if (!modulesPage.includes(`id: '${id}'`)) failures.push(`Falta el módulo ${id}.`);
}

for (const href of requiredAvailableRoutes) {
  if (!modulesPage.includes(`href: '${href}'`)) failures.push(`Falta la ruta Admin disponible ${href}.`);
}

if (!adminPage.includes('href="/admin/modulos"')) failures.push('El centro Admin no enlaza al directorio unificado.');
if (!adminPage.includes('href="/admin/ayuntamientos"')) failures.push('El centro Admin no enlaza directamente a Ayuntamientos.');

const integrationModules = ['businesses', 'experiences', 'magina-pass', 'routes', 'route-community', 'route-sponsorships', 'adventure'];
for (const id of integrationModules) {
  const row = modulesPage.split('\n').find((line) => line.includes(`id: '${id}'`));
  if (!row?.includes("status: 'integration'")) failures.push(`${id} debe permanecer en integración hasta que su rama sea absorbida.`);
  if (row?.includes('href:')) failures.push(`${id} no debe exponer href antes de su absorción.`);
}

if (!modulesPage.includes('Los módulos no absorbidos no generan enlaces rotos')) {
  failures.push('Falta la regla explícita contra enlaces rotos de módulos no absorbidos.');
}

if (failures.length) {
  console.error('Contrato Admin unificado: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`Contrato Admin unificado: OK (${requiredModuleIds.length} módulos críticos verificados).`);
