import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const adminDir = resolve(root, 'apps/web/src/app/admin');
const modulesPage = readFileSync(resolve(adminDir, 'modulos/page.tsx'), 'utf8');
const adminPage = readFileSync(resolve(adminDir, 'page.tsx'), 'utf8');
const registryPath = resolve(adminDir, 'modulos/admin-modules.json');
const adminLayoutPath = resolve(adminDir, 'layout.tsx');
const adminRouteGatePath = resolve(root, 'apps/web/src/components/admin-route-gate.tsx');
const adminLayout = existsSync(adminLayoutPath) ? readFileSync(adminLayoutPath, 'utf8') : '';
const adminRouteGate = existsSync(adminRouteGatePath) ? readFileSync(adminRouteGatePath, 'utf8') : '';
const modules = JSON.parse(readFileSync(registryPath, 'utf8'));

const baselineModuleIds = [
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

const allowedStatuses = new Set(['available', 'implemented']);
const allowedAreas = new Set(['Plataforma', 'Territorio', 'Negocio', 'Experiencia']);
const failures = [];

function routeToPagePath(href) {
  const segments = href.split('/').filter(Boolean);
  return resolve(root, 'apps/web/src/app', ...segments, 'page.tsx');
}

function isAdminHref(value) {
  return typeof value === 'string' && value.startsWith('/admin/');
}

if (!Array.isArray(modules)) {
  failures.push('admin-modules.json debe contener un array de módulos.');
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

if (!modulesPage.includes("import moduleRegistry from './admin-modules.json'")) {
  failures.push('La UI de módulos debe leer el registro canónico admin-modules.json.');
}

const ids = modules.map((module) => module?.id);
for (const id of baselineModuleIds) {
  const occurrences = ids.filter((candidate) => candidate === id).length;
  if (occurrences !== 1) failures.push(`El módulo crítico ${id} debe aparecer exactamente una vez; aparece ${occurrences}.`);
}

if (new Set(ids).size !== ids.length) {
  failures.push('El registro Admin contiene IDs de módulo duplicados.');
}

if (modules.length < baselineModuleIds.length) {
  failures.push(`El registro no puede reducirse por debajo de los ${baselineModuleIds.length} módulos críticos auditados.`);
}

for (const module of modules) {
  if (!module || typeof module !== 'object') {
    failures.push('Todos los registros Admin deben ser objetos.');
    continue;
  }

  const label = module.id || '(sin id)';
  if (typeof module.id !== 'string' || !module.id.trim()) failures.push('Hay un módulo sin id válido.');
  if (typeof module.title !== 'string' || !module.title.trim()) failures.push(`${label} no tiene title válido.`);
  if (typeof module.description !== 'string' || !module.description.trim()) failures.push(`${label} no tiene description válida.`);
  if (!allowedStatuses.has(module.status)) failures.push(`${label} usa un status no permitido: ${module.status}.`);
  if (!allowedAreas.has(module.area)) failures.push(`${label} usa un área no permitida: ${module.area}.`);

  if (module.status === 'available') {
    if (!isAdminHref(module.href)) failures.push(`${label} está disponible pero no declara href bajo /admin/.`);
    if (module.sourceBranch || module.targetHref) failures.push(`${label} está disponible y no debe conservar metadatos de integración externa.`);
    if (isAdminHref(module.href) && !existsSync(routeToPagePath(module.href))) {
      failures.push(`La ruta disponible ${module.href} no tiene una page.tsx real.`);
    }
  }

  if (module.status === 'implemented') {
    if (module.href) failures.push(`${label} no debe exponer href antes de su absorción.`);
    if (typeof module.sourceBranch !== 'string' || !module.sourceBranch.trim()) failures.push(`${label} debe declarar sourceBranch.`);
    if (!isAdminHref(module.targetHref)) failures.push(`${label} debe declarar targetHref bajo /admin/.`);
  }
}

const availableModules = modules.filter((module) => module?.status === 'available');
const implementedModules = modules.filter((module) => module?.status === 'implemented');
const availableHrefs = availableModules.map((module) => module.href).filter(Boolean);
const targetHrefs = implementedModules.map((module) => module.targetHref).filter(Boolean);

if (new Set(availableHrefs).size !== availableHrefs.length) {
  failures.push('Dos módulos disponibles no pueden compartir el mismo href.');
}

if (new Set(targetHrefs).size !== targetHrefs.length) {
  failures.push('Dos módulos implementados no pueden compartir la misma ruta objetivo.');
}

const availableRouteRoots = new Set(
  availableHrefs.map((href) => href.split('/').filter(Boolean)[1]).filter(Boolean),
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
  `Contrato Admin unificado: OK (${modules.length} superficies registradas; ${availableModules.length} disponibles, ${implementedModules.length} implementadas en ramas, ${topLevelAdminRoutes.length} rutas raíz verificadas y gate corporativo común activo).`,
);
