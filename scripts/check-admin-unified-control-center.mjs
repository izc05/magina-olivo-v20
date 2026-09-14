import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const adminDir = resolve(root, 'apps/web/src/app/admin');
const modulesPage = readFileSync(resolve(adminDir, 'modulos/page.tsx'), 'utf8');
const adminPage = readFileSync(resolve(adminDir, 'page.tsx'), 'utf8');
const adminLayoutPath = resolve(adminDir, 'layout.tsx');
const adminRouteGatePath = resolve(root, 'apps/web/src/components/admin-route-gate.tsx');
const adminLayout = existsSync(adminLayoutPath) ? readFileSync(adminLayoutPath, 'utf8') : '';
const adminRouteGate = existsSync(adminRouteGatePath) ? readFileSync(adminRouteGatePath, 'utf8') : '';

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
const lines = modulesPage.split('\n');

function countOccurrences(text, needle) {
  return text.split(needle).length - 1;
}

function routeToPagePath(href) {
  const segments = href.split('/').filter(Boolean);
  return resolve(root, 'apps/web/src/app', ...segments, 'page.tsx');
}

if (!adminLayout) {
  failures.push('Falta apps/web/src/app/admin/layout.tsx para aplicar políticas comunes a todo Admin.');
} else {
  if (!adminLayout.includes('<AdminRouteGate>{children}</AdminRouteGate>')) {
    failures.push('El layout de Admin debe envolver todas las rutas con AdminRouteGate.');
  }
  if (!adminLayout.includes('index: false') || !adminLayout.includes('follow: false')) {
    failures.push('El layout de Admin debe aplicar robots noindex/nofollow de forma centralizada.');
  }
  if (!adminLayout.includes('nocache: true')) {
    failures.push('El layout de Admin debe desactivar cacheado de indexación con nocache.');
  }
}

if (!adminRouteGate) {
  failures.push('Falta el gate corporativo compartido apps/web/src/components/admin-route-gate.tsx.');
} else {
  if (!adminRouteGate.includes('await adminApi.session()')) {
    failures.push('AdminRouteGate debe validar la sesión administrativa contra el servidor.');
  }
  if (!adminRouteGate.includes('caught instanceof ApiRequestError && caught.status === 403')) {
    failures.push('AdminRouteGate debe tratar explícitamente el rechazo 403.');
  }
  if (!adminRouteGate.includes("state !== 'authorized'")) {
    failures.push('AdminRouteGate no debe renderizar contenido antes de confirmar autorización.');
  }
}

for (const id of requiredModuleIds) {
  const occurrences = countOccurrences(modulesPage, `id: '${id}'`);
  if (occurrences !== 1) failures.push(`El módulo ${id} debe aparecer exactamente una vez; aparece ${occurrences}.`);
}

const moduleRows = requiredModuleIds.map((id) => ({
  id,
  row: lines.find((line) => line.includes(`id: '${id}'`)) ?? '',
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

  const pagePath = routeToPagePath(href);
  if (!existsSync(pagePath)) failures.push(`La ruta disponible ${href} no tiene una page.tsx real (${pagePath}).`);
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

const availableRouteRoots = new Set(
  requiredAvailableRoutes.map((href) => href.split('/').filter(Boolean)[1]),
);
availableRouteRoots.add('modulos');

const topLevelAdminRoutes = readdirSync(adminDir, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && existsSync(resolve(adminDir, entry.name, 'page.tsx')))
  .map((entry) => entry.name)
  .sort();

for (const routeRoot of topLevelAdminRoutes) {
  if (!availableRouteRoots.has(routeRoot)) {
    failures.push(`Ruta Admin huérfana: /admin/${routeRoot} tiene page.tsx pero no está registrada en el centro unificado.`);
  }
}

for (const routeRoot of availableRouteRoots) {
  if (routeRoot === 'modulos') continue;
  if (!topLevelAdminRoutes.includes(routeRoot)) {
    failures.push(`Módulo disponible sin superficie Admin raíz: falta /admin/${routeRoot}/page.tsx.`);
  }
}

const targetHrefs = implementedModules.map(({ targetHref }) => targetHref);
if (new Set(targetHrefs).size !== targetHrefs.length) {
  failures.push('Las rutas objetivo de módulos implementados deben ser únicas.');
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

console.log(
  `Contrato Admin unificado: OK (${requiredModuleIds.length}/${requiredModuleIds.length} superficies Admin registradas; 13 disponibles, 7 implementadas en ramas, ${topLevelAdminRoutes.length} rutas raíz verificadas y gate corporativo común activo).`,
);
