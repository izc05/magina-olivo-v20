# Mágina Olivo V20 — Admin unificado

## Objetivo

Mantener una única vista de gobierno para las superficies administrativas de V20 sin copiar lógica desde ramas funcionales ni crear enlaces a rutas que todavía no existen en la base coordinada.

La superficie visible es `/admin/modulos` y su fuente de verdad es `apps/web/src/app/admin/modulos/admin-modules.json`.

## Base coordinada actual

Este hardening está reconciliado sobre `integrate/v20-beta-closure` después de la absorción de Empresas, Experiencias, Mágina Pass y Rutas. La reconciliación conserva los avances del candidate y añade únicamente el delta de seguridad, UX y gobernanza propio del Admin.

## Registro canónico

`admin-modules.json` es el inventario único de módulos administrativos. La UI y el contrato automático leen el mismo registro.

Cada módulo declara:

- `id`, `title`, `description` y `area`;
- `status: available` + `href` cuando su código ya está absorbido;
- o `status: implemented` + `sourceBranch` + `sourcePr` + `targetHref` cuando su Admin existe en una rama funcional pero todavía no forma parte de esta base.

Un módulo `available` no conserva metadatos de handoff externo.

## Cobertura auditada

El inventario actual registra **20/20 módulos críticos con superficie administrativa identificada**:

- **19/20 disponibles** en el candidate;
- **1/20 implementado** en rama funcional y pendiente de absorción;
- **0/20 sin contrato Admin conocido**.

Tener cobertura 20/20 no significa que todos los módulos estén ya integrados. El único handoff externo actual es **Mágina Aventura**.

## Disponibles

- Operaciones — `/admin/operaciones`
- Analítica — `/admin/analitica`
- Gestión — `/admin/gestion`
- Campañas y planes — `/admin/campanas-planes`
- Agenda — `/admin/agenda`
- Trabajos — `/admin/trabajos`
- Documentos / OCR — `/admin/documentos`
- Profesional — `/admin/profesional`
- Fuentes — `/admin/fuentes`
- Territorio — `/admin/territorio`
- Multimedia — `/admin/media`
- Web / CMS — `/admin/web`
- Ayuntamientos — `/admin/ayuntamientos`
- Empresas — `/admin/empresas`
- Experiencias y reservas — `/admin/empresas/experiencias`
- Mágina Pass — `/admin/empresas/magina-pass`
- Rutas — `/admin/rutas`
- Comunidad de rutas — `/admin/rutas/comunidad`
- Patrocinios de rutas — `/admin/rutas/patrocinios`

## Handoff pendiente

| Módulo | Rama fuente | PR fuente | Ruta Admin objetivo |
| --- | --- | ---: | --- |
| Mágina Aventura | `feat/v20-routes-adventure` | #87 | `/admin/rutas/aventuras` |

La ruta objetivo es un contrato de handoff, no un enlace activo. Solo pasa a `href` después de absorber su código real y validar el candidate resultante.

## Directorio y descubrimiento

`/admin/modulos` ofrece búsqueda por nombre, descripción, id, rama, PR o ruta objetivo; filtros por disponibilidad y área; contador accesible mediante `aria-live`; estado vacío y limpieza de filtros; y composición responsive. La portada `/admin` muestra el lanzador de módulos antes del centro de control y genera los accesos disponibles desde el registro canónico.

## Acceso transversal

Todo `/admin` pasa por `AdminRouteGate` desde `apps/web/src/app/admin/layout.tsx`.

El gate valida `adminApi.session()` antes de habilitar la UI, distingue `401` de sesión caducada y `403` de cuenta sin permiso corporativo, resincroniza `AuthProvider`, trabaja fail-closed, revalida al recuperar foco o visibilidad e impide validaciones concurrentes. El layout aplica además `noindex`, `nofollow` y `nocache` a todo el árbol Admin.

## Protección del propio acceso

Una sesión `super_admin` no puede degradar ni revocar su propio acceso mediante `PUT /api/v1/admin/platform-access/:userId`. La API devuelve `409 cannot_change_current_admin_access` cuando el usuario objetivo coincide con el administrador actual y se intenta cambiar su rol o revocar el acceso.

La consola aplica la misma regla de experiencia: la cuenta actual se muestra como acceso protegido y los handlers de UI bloquean esos intentos antes de llamar a la API. La frontera real de seguridad sigue siendo el backend.

## Política de settings públicos

`site_settings` admite claves internas flexibles, pero solo estas claves pueden publicarse:

- `alerts.banner`
- `home.hero`
- `home.territory_banner`
- `site.contact`
- `site.identity`
- `site.seo`
- `site.social`

`PUT /api/v1/admin/settings/:key` rechaza `is_public=true` fuera de la allowlist con `400 setting_not_publicable`, y `/api/v1/public/site-settings` vuelve a filtrar por allowlist. La consola deshabilita la publicación para claves internas.

## Autorización de API

El contrato recorre los routers TypeScript de `apps/api/src/routes` y detecta handlers `/api/v1/admin/...`. Cada endpoint detectado debe asignar el resultado de `requirePlatformAccess(...)`, cortar ejecución cuando el acceso sea nulo e importar el control de acceso corporativo.

Los smokes con PostgreSQL ejercitan `support`, `editor`, `admin` y `super_admin`, incluida la protección de autoacceso y la política de settings públicos.

## Detección de rutas huérfanas

Si aparece una nueva `/admin/<algo>/page.tsx` que no corresponde a un módulo `available`, CI falla. Del mismo modo, un `href` declarado como disponible sin `page.tsx` real también falla. Esto obliga a incorporar toda nueva superficie administrativa al centro unificado.

## Contrato automático

`scripts/check-admin-unified-control-center.mjs` comprueba como mínimo los 20 dominios críticos; IDs, estados y áreas; semántica `available / implemented`; existencia física de rutas disponibles; metadatos de handoff; rutas huérfanas y duplicadas; consumo del registro por UI; búsqueda/filtros y lanzador; gate `401/403`; protección del propio acceso; allowlist de settings públicos; autorización de servidor y política transversal de indexación.

El contrato forma parte de `pnpm check`, `pnpm check:fast`, `V20 platform admin check` y del workflow ligero `V20 Admin governance contract`.

## Regla de absorción

Cuando Mágina Aventura esté lista:

1. reconciliar su rama contra el candidate vigente;
2. pasar sus checks específicos y globales;
3. integrar su código real en el candidate coordinado;
4. cambiar `adventure` de `implemented` a `available`;
5. eliminar `sourceBranch`, `sourcePr` y `targetHref`;
6. añadir el `href` real `/admin/rutas/aventuras`;
7. ejecutar contrato, typecheck, build, smokes, E2E y staging readiness.

No se debe copiar lógica de dominio al Admin para simular una absorción. El estado definitivo lo determina CI sobre el HEAD exacto que se pretenda integrar.
