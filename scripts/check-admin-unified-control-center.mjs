import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const modulesPage = readFileSync(resolve(root, 'apps/web/src/app/admin/modulos/page.tsx'), 'utf8');
const adminPage = readFileSync(resolve(root, 'apps/web/src/app/admin/page.tsx'), 'utf8');

const requiredModuleIds = [
  'operations',
  'analytics',
  'management',
  'campaigns',
  'agenda',
  'jobs',
  'documents',
  'professional',
  'sources',
  'territory',
  'media',
  'web',
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
  '/admin/campanas-planes',
  '/admin/agenda',
  '/admin/trabajos',
  '/admin/documentos',
  '/admin/profesional',
  '/admin/fuentes',
  '/admin/territorio',
  '/admin/media',
  '/admin/web',
  '/admin/ayuntamientos',
];

const implementedModules = [
  { id: 'businesses', sourceBranch: 'feat/v20-business-directory', targetHref: '/admin/empresas' },
  { id: 'experiences', sourceBranch: 'feat/v20-business-experiences', targetHref: '/admin/empresas/experiencias' },
  { id: 'magina-pass', sourceBranch: 'feat/v20-business-magina-pass', targetHref: '/admin/empresas/magina-pass' },
  { id: 'routes', sourceBranch: 'feat/v20-routes-explore', targetHref: '/admin/rutas' },
  { id: 'route-community', sourceBranch: 'feat/v20-routes-explore', targetHref: '/admin/rutas/comunidad' },
  { id: 'route-sponsorships', sourceBranch: 'feat/v20-routes-explore', targetHref: '/admin/rutas/patrocinios' },
  { id: 'adventure', sourceBranch: 'feat/v20-routes-adventure', targetHref: '/admin/rutas/aventuras' },
];

const failures = [];

for (const id of requiredModuleIds) {
  if (!modulesPage.includes(`id: '${id}'`)) failures.push(`Falta el módulo ${id}.`);
}

const moduleRows = requiredModuleIds.map((id) => ({
  id,
  row: modulesPage.split('\n').find((line) => line.includes(`id: '${id}'`)) ?? '',
}));

if (moduleRows.filter(({ row }) => row.includes("status: 'available'")).length !== 13) {
  failures.push('Deben existir exactamente 13 módulos disponibles en la base actual.');
}

if (moduleRows.filter(({ row }) => row.includes("status: 'implemented'")).length !== 7) {
  failures.push('Deben existir exactamente 7 módulos implementados en ramas funcionales.');
}

for (const href of requiredAvailableRoutes) {
  const row = moduleRows.find(({ row }) => row.includes(`href: '${href}'`))?.row;
  if (!row?.includes("status: 'available'")) failures.push(`La ruta ${href} debe pertenecer a un módulo disponible.`);
}

for (const { id, sourceBranch, targetHref } of implementedModules) {
  const row = moduleRows.find((module) => module.id === id)?.row ?? '';
  if (!row.includes("status: 'implemented'")) failures.push(`${id} debe figurar como implementado en rama.`);
  if (row.includes(' href:')) failures.push(`${id} no debe exponer href antes de su absorción.`);
  if (!row.includes(`sourceBranch: '${sourceBranch}'`)) failures.push(`${id} debe declarar su rama fuente ${sourceBranch}.`);
  if (!row.includes(`targetHref: '${targetHref}'`)) failures.push(`${id} debe declarar su ruta objetivo ${targetHref}.`);
  if (!targetHref.startsWith('/admin/')) failures.push(`${id} debe declarar una ruta objetivo bajo /admin/.`);
}

for (const { id, row } of moduleRows) {
  if (row.includes("status: 'available'") && !row.includes('href:')) failures.push(`${id} está disponible pero no tiene href.`);
}

if (!adminPage.includes('href="/admin/modulos"')) failures.push('El centro Admin no enlaza al directorio unificado.');
if (!adminPage.includes('href="/admin/ayuntamientos"')) failures.push('El centro Admin no enlaza directamente a Ayuntamientos.');

if (!modulesPage.includes('Que esté implementado en otra rama demuestra cobertura')) {
  failures.push('Falta la regla explícita que separa cobertura de absorción.');
}

if (failures.length) {
  console.error('Contrato Admin unificado: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`Contrato Admin unificado: OK (${requiredModuleIds.length}/${requiredModuleIds.length} superficies Admin registradas; 13 disponibles y 7 implementadas en ramas).`);
