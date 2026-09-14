# Mágina Olivo V20 — Admin unificado

## Objetivo

Mantener una única vista de gobierno para las superficies administrativas de V20 sin copiar lógica desde ramas funcionales ni crear enlaces a rutas que todavía no existen en la base coordinada.

La superficie visible es `/admin/modulos` y su fuente de verdad es `apps/web/src/app/admin/modulos/admin-modules.json`.

## Base coordinada actual

Este hardening se ha reconciliado sobre `integrate/v20-beta-closure` después de la integración de **Empresas**. Por tanto, Empresas ya forma parte del candidate y no debe volver a estado `implemented`.

La reconciliación conserva todos los avances del candidate y aplica únicamente el delta posterior de seguridad, UX y gobernanza del Admin.

## Registro canónico

`admin-modules.json` es el inventario único de módulos administrativos. La UI y el contrato automático leen el mismo registro.

Cada módulo declara:

- `id`, `title`, `description` y `area`;
- `status: available` + `href` cuando su código ya está absorbido;
- o `status: implemented` + `sourceBranch` + `sourcePr` + `targetHref` cuando su Admin existe en una rama funcional pero todavía no forma parte de esta base.

Un módulo `available` no conserva metadatos de handoff externo.

## Cobertura auditada

El inventario actual registra **20/20 módulos críticos con superficie administrativa identificada**:

- **14/20 disponibles** en el candidate, incluida Empresas;
- **6/20 implementados** en ramas funcionales y pendientes de absorción;
- **0/20 sin contrato Admin conocido**.

Tener cobertura 20/20 no significa que los 20 módulos estén ya integrados. Separa deliberadamente “existe Admin para este dominio” de “su código ya está absorbido”.

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

## Pendientes de absorción funcional

| Módulo | Rama fuente | PR fuente | Ruta Admin objetivo |
| --- | --- | ---: | --- |
| Experiencias y reservas | `feat/v20-business-experiences` | #84 | `/admin/empresas/experiencias` |
| Mágina Pass | `feat/v20-business-magina-pass` | #82 | `/admin/empresas/magina-pass` |
| Rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas` |
| Comunidad de rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas/comunidad` |
| Patrocinios de rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas/patrocinios` |
| Mágina Aventura | `feat/v20-routes-adventure` | #87 | `/admin/rutas/aventuras` |

Estas rutas son contratos de handoff, no enlaces activos. Solo pasan a `href` después de absorber su código real y validar el candidate resultante.

## Directorio y descubrimiento

`/admin/modulos` ofrece:

- búsqueda por nombre, descripción, id, rama, PR o ruta objetivo;
- filtro `available / implemented`;
- filtro por área Plataforma / Territorio / Negocio / Experiencia;
- contador accesible mediante `aria-live`;
- estado vacío y limpieza de filtros;
- composición responsive 3 → 2 → 1 columnas.

La portada `/admin` muestra el lanzador de módulos **antes** del centro de control. Los accesos disponibles se generan desde el registro canónico.

## Acceso transversal

Todo `/admin` pasa por `AdminRouteGate` desde `apps/web/src/app/admin/layout.tsx`.

El gate:

- valida `adminApi.session()` en servidor antes de habilitar la UI;
- distingue `401` de sesión caducada y `403` de cuenta sin permiso corporativo;
- resincroniza `AuthProvider` tras un `401`;
- trabaja fail-closed ante errores de validación;
- revalida al recuperar foco o visibilidad;
- impide validaciones concurrentes;
- revalida silenciosamente una sesión ya autorizada para no desmontar formularios ni perder estado local.

El layout aplica también `noindex`, `nofollow` y `nocache` a todo el árbol Admin.

## Protección del propio acceso

Una sesión `super_admin` no puede degradar ni revocar su propio acceso mediante `PUT /api/v1/admin/platform-access/:userId`.

La API devuelve `409 cannot_change_current_admin_access` cuando el usuario objetivo coincide con el administrador actual y se intenta cambiar su rol o revocar el acceso.

La consola aplica la misma regla de experiencia: la cuenta actual se muestra como **acceso protegido**, sin selector de rol ni acción de revocación. Los handlers de UI también bloquean esos intentos antes de llamar a la API.

La frontera real de seguridad sigue siendo el backend.

## Política de settings públicos

`site_settings` admite claves internas flexibles, pero solo estas claves pueden publicarse:

- `alerts.banner`
- `home.hero`
- `home.territory_banner`
- `site.contact`
- `site.identity`
- `site.seo`
- `site.social`

La política se aplica dos veces:

1. `PUT /api/v1/admin/settings/:key` rechaza `is_public=true` fuera de la allowlist con `400 setting_not_publicable`;
2. `/api/v1/public/site-settings` vuelve a filtrar por allowlist, incluso si una fila antigua quedara marcada como pública.

La consola deshabilita **Disponible para la web pública** para claves internas y fuerza el valor a privado al cambiar a una clave no publicable.

## Autorización de API

El contrato recorre los routers TypeScript de `apps/api/src/routes` y detecta handlers `/api/v1/admin/...`.

Cada endpoint detectado debe:

1. asignar el resultado de `requirePlatformAccess(...)`;
2. cortar ejecución cuando el acceso sea nulo;
3. importar el control de acceso corporativo.

Los smokes con PostgreSQL ejercitan `support`, `editor`, `admin` y `super_admin`, incluida la protección de autoacceso y la política de settings públicos.

## Detección de rutas huérfanas

Si aparece una nueva `/admin/<algo>/page.tsx` que no corresponde a un módulo `available`, CI falla. Del mismo modo, un `href` declarado como disponible sin `page.tsx` real también falla.

Esto obliga a que toda nueva superficie administrativa se incorpore al centro unificado.

## Contrato automático

`scripts/check-admin-unified-control-center.mjs` comprueba como mínimo:

- los 20 dominios críticos;
- IDs únicos, estados y áreas válidos;
- semántica `available / implemented`;
- existencia física de cada ruta disponible;
- ausencia de `href` en handoffs no absorbidos;
- `sourceBranch + sourcePr + targetHref` en módulos externos;
- rutas Admin huérfanas y duplicadas;
- consumo real del registro por UI;
- búsqueda/filtros y lanzador del directorio;
- gate `401/403`, revalidación y fail-closed;
- protección del propio acceso en UI y API;
- allowlist de settings públicos en UI y API;
- autorización de servidor en endpoints Admin;
- `noindex/nofollow/nocache` transversal.

El contrato forma parte de `pnpm check`, `pnpm check:fast`, `V20 platform admin check` y del workflow ligero `V20 Admin governance contract`.

## Regla de absorción

Cuando un módulo externo esté listo:

1. actualizar/reconciliar su rama contra el candidate vigente;
2. pasar sus checks específicos y globales;
3. integrar su código real en el candidate coordinado;
4. cambiar su entrada de `implemented` a `available`;
5. eliminar `sourceBranch`, `sourcePr` y `targetHref`;
6. añadir el `href` real;
7. ejecutar de nuevo contrato, typecheck, build, smokes, E2E y staging readiness.

No se debe copiar lógica de dominio al Admin para simular una absorción.

Este documento describe arquitectura y gobierno; el estado definitivo siempre lo determina CI sobre el HEAD exacto que se pretenda integrar.
