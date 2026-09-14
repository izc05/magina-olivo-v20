import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const root = process.cwd();
const adminDir = resolve(root, 'apps/web/src/app/admin');
const apiRoutesDir = resolve(root, 'apps/api/src/routes');
const modulesPage = readFileSync(resolve(adminDir, 'modulos/page.tsx'), 'utf8');
const modulesDirectoryPath = resolve(adminDir, 'modulos/admin-modules-directory.tsx');
const modulesDirectory = existsSync(modulesDirectoryPath) ? readFileSync(modulesDirectoryPath, 'utf8') : '';
const adminPage = readFileSync(resolve(adminDir, 'page.tsx'), 'utf8');
const adminCssPath = resolve(adminDir, 'admin.css');
const adminCss = existsSync(adminCssPath) ? readFileSync(adminCssPath, 'utf8') : '';
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function adminApiRoutesInFile(filePath) {
  const source = readFileSync(filePath, 'utf8');
  const routePattern = /app\.(get|post|put|patch|delete)\(\s*(['"`])(\/api\/v1\/admin\/[^'"`]+)\2/g;
  const allAppRoutePattern = /app\.(get|post|put|patch|delete)\(\s*(['"`])([^'"`]+)\2/g;
  const adminMatches = [...source.matchAll(routePattern)];
  const allRouteMatches = [...source.matchAll(allAppRoutePattern)];

  return adminMatches.map((match) => {
    const start = match.index ?? 0;
    const nextRoute = allRouteMatches.find((candidate) => (candidate.index ?? 0) > start);
    const end = nextRoute?.index ?? source.length;
    return {
      method: match[1].toUpperCase(),
      path: match[3],
      segment: source.slice(start, end),
    };
  });
}

function accessGuardInRoute(segment) {
  const assignment = segment.match(/const\s+([A-Za-z_$][\w$]*)\s*=\s*await\s+requirePlatformAccess\s*\(/);
  if (!assignment) return { protected: false, reason: 'missing_assignment' };
  const variable = escapeRegExp(assignment[1]);
  const failClosed = new RegExp(`if\\s*\\(\\s*!\\s*${variable}\\s*\\)\\s*(?:return\\b|\\{\\s*return\\b)`);
  if (!failClosed.test(segment)) return { protected: false, reason: 'missing_fail_closed', variable: assignment[1] };
  return { protected: true, variable: assignment[1] };
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
  if (!adminRouteGate.includes('caught instanceof ApiRequestError && caught.status === 401')) {
    failures.push('AdminRouteGate debe distinguir explícitamente una sesión expirada (401).');
  }
  if (!adminRouteGate.includes('await auth.refreshSession()')) {
    failures.push('AdminRouteGate debe resincronizar AuthProvider cuando la sesión administrativa haya expirado.');
  }
  if (!adminRouteGate.includes("'expired'")) {
    failures.push('AdminRouteGate debe mantener un estado específico para sesión administrativa caducada.');
  }
  if (!adminRouteGate.includes('caught instanceof ApiRequestError && caught.status === 403')) {
    failures.push('AdminRouteGate debe tratar explícitamente el rechazo 403.');
  }
  if (!adminRouteGate.includes("state !== 'authorized'")) {
    failures.push('AdminRouteGate no debe renderizar contenido antes de confirmar autorización.');
  }
  if (!adminRouteGate.includes("window.addEventListener('focus'")) {
    failures.push('AdminRouteGate debe revalidar acceso al recuperar foco.');
  }
  if (!adminRouteGate.includes("document.addEventListener('visibilitychange'")) {
    failures.push('AdminRouteGate debe revalidar acceso al volver visible la pestaña.');
  }
  if (!adminRouteGate.includes('validatingRef.current')) {
    failures.push('AdminRouteGate debe impedir validaciones de acceso concurrentes.');
  }
}

if (!modulesPage.includes("import moduleRegistry from './admin-modules.json'")) {
  failures.push('La UI de módulos debe leer el registro canónico admin-modules.json.');
}
if (!modulesPage.includes('<AdminModulesDirectory modules={modules} />')) {
  failures.push('La página de módulos debe delegar exploración y filtros en AdminModulesDirectory.');
}
if (!modulesDirectory) {
  failures.push('Falta el directorio interactivo admin-modules-directory.tsx.');
} else {
  if (!modulesDirectory.includes('type="search"')) failures.push('El directorio Admin debe ofrecer búsqueda accesible.');
  if (!modulesDirectory.includes("useState<'all' | ModuleStatus>")) failures.push('El directorio Admin debe filtrar por estado.');
  if (!modulesDirectory.includes("useState<'all' | ModuleArea>")) failures.push('El directorio Admin debe filtrar por área.');
  if (!modulesDirectory.includes('aria-live="polite"')) failures.push('El contador de resultados del directorio debe anunciar cambios de forma accesible.');
  if (!modulesDirectory.includes('PR fuente')) failures.push('El directorio Admin debe mostrar el PR fuente de los handoffs pendientes.');
}

if (!adminPage.includes("import moduleRegistry from './modulos/admin-modules.json'")) {
  failures.push('La portada Admin debe leer el mismo registro canónico que el directorio.');
}
if (!adminPage.includes("module.status === 'available' && module.href")) {
  failures.push('La portada Admin debe generar sus accesos operativos desde módulos available del registro.');
}
if (!adminPage.includes('href="/admin/modulos"')) {
  failures.push('El centro Admin no enlaza al directorio unificado.');
}
const launcherIndex = adminPage.indexOf('className="admin-shortcuts"');
const controlCenterIndex = adminPage.indexOf('<AdminControlCenter />');
if (launcherIndex < 0 || controlCenterIndex < 0 || launcherIndex > controlCenterIndex) {
  failures.push('El lanzador de módulos debe aparecer antes del AdminControlCenter para ser descubrible al entrar.');
}
if (!adminCss.includes('.admin-shortcuts') || !adminCss.includes('.admin-shortcuts-list')) {
  failures.push('El lanzador de módulos debe tener estilos propios responsive en admin.css.');
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
    if (module.sourceBranch || module.sourcePr || module.targetHref) failures.push(`${label} está disponible y no debe conservar metadatos de integración externa.`);
    if (isAdminHref(module.href) && !existsSync(routeToPagePath(module.href))) {
      failures.push(`La ruta disponible ${module.href} no tiene una page.tsx real.`);
    }
  }

  if (module.status === 'implemented') {
    if (module.href) failures.push(`${label} no debe exponer href antes de su absorción.`);
    if (typeof module.sourceBranch !== 'string' || !module.sourceBranch.trim()) failures.push(`${label} debe declarar sourceBranch.`);
    if (!Number.isInteger(module.sourcePr) || module.sourcePr <= 0) failures.push(`${label} debe declarar sourcePr como número de PR válido.`);
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

const apiRouteFiles = readdirSync(apiRoutesDir, { withFileTypes: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith('.ts'))
  .map((entry) => resolve(apiRoutesDir, entry.name))
  .sort();

let adminApiFiles = 0;
let protectedAdminApiRoutes = 0;
for (const filePath of apiRouteFiles) {
  const source = readFileSync(filePath, 'utf8');
  const routes = adminApiRoutesInFile(filePath);
  if (!routes.length) continue;
  adminApiFiles += 1;

  if (!source.includes("from '../admin/access.js'")) {
    failures.push(`${basename(filePath)} expone rutas Admin pero no importa el control de acceso de plataforma.`);
  }

  for (const route of routes) {
    const guard = accessGuardInRoute(route.segment);
    if (guard.protected) {
      protectedAdminApiRoutes += 1;
      continue;
    }
    if (guard.reason === 'missing_assignment') {
      failures.push(`${basename(filePath)}: ${route.method} ${route.path} no asigna el resultado de requirePlatformAccess en su handler.`);
    } else {
      failures.push(`${basename(filePath)}: ${route.method} ${route.path} no corta ejecución cuando ${guard.variable ?? 'el acceso'} es nulo.`);
    }
  }
}

if (adminApiFiles === 0 || protectedAdminApiRoutes === 0) {
  failures.push('No se detectaron endpoints /api/v1/admin protegidos; revisa el detector del contrato.');
}

if (!modulesPage.includes('Que esté implementado en otra rama demuestra cobertura')) {
  failures.push('Falta la regla explícita que separa cobertura de absorción.');
}

if (failures.length) {
  console.error('Contrato Admin unificado: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Contrato Admin unificado: OK (${modules.length} superficies registradas; ${availableModules.length} disponibles, ${implementedModules.length} implementadas en ramas con PR fuente, ${topLevelAdminRoutes.length} rutas web raíz y ${protectedAdminApiRoutes} endpoints Admin protegidos en ${adminApiFiles} routers; 401/403 fail-closed; directorio filtrable; lanzador visible).`,
);
