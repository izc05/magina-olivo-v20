# Mágina Olivo V20 — Admin unificado

## Objetivo

Mantener una única vista de gobierno para las superficies administrativas de V20 sin copiar lógica desde ramas funcionales ni crear enlaces a rutas que todavía no existen en la base coordinada.

La superficie visible es `/admin/modulos` y su fuente de verdad es `apps/web/src/app/admin/modulos/admin-modules.json`.

## Registro canónico

`admin-modules.json` es el inventario único de módulos administrativos. La UI y el contrato automático leen el mismo registro, por lo que no deben mantenerse listas paralelas de estados, rutas, ramas o PR de handoff.

Cada módulo declara:

- `id`, `title`, `description` y `area`;
- `status: available` + `href` cuando su código ya está absorbido;
- o `status: implemented` + `sourceBranch` + `sourcePr` + `targetHref` cuando su Admin existe en una rama funcional pero todavía no forma parte de esta base.

La lista mínima de los 20 dominios críticos auditados permanece protegida en CI para impedir borrados accidentales, pero el contrato admite módulos adicionales sin tener que reprogramar conteos fijos.

## Regla de cierre

Un módulo nuevo que requiera gestión administrativa no se considera cerrado para V20 hasta que:

1. declare su superficie Admin en el registro canónico;
2. aparezca automáticamente en el directorio unificado;
3. tenga una ruta física real antes de marcarse como `available`;
4. declare rama fuente, PR fuente y ruta objetivo cuando su Admin ya exista pero todavía no haya sido absorbido;
5. permanezca sin `href` mientras su rama funcional no haya sido absorbida;
6. mantenga su información técnica/comercial en su dominio original, evitando duplicar modelos o backends.

## Estados del directorio

### `available` — Disponible aquí

La superficie Admin existe dentro de la base de esta rama y puede enlazarse directamente. Debe declarar `href`; CI confirma además que la `page.tsx` correspondiente existe realmente. No conserva `sourceBranch`, `sourcePr` ni `targetHref`, porque ya no es un handoff externo.

### `implemented` — Implementado en rama

La superficie Admin real ya ha sido verificada en una rama funcional, pero ese código todavía no forma parte de esta base. Debe declarar `sourceBranch`, `sourcePr` y `targetHref`, pero no debe exponer `href` para evitar enlaces rotos.

## Cobertura auditada

El inventario actual registra **20 módulos de 20 con superficie administrativa identificada**:

- **13/20 disponibles** en esta rama;
- **7/20 implementados** en ramas funcionales y pendientes de absorción;
- **0/20 sin contrato Admin conocido**.

Tener cobertura 20/20 no significa que los 20 módulos estén ya integrados en esta rama. Separa deliberadamente dos preguntas: “¿existe Admin para este dominio?” y “¿está ya absorbido en la base coordinada?”.

## Directorio y descubrimiento

`/admin/modulos` ya no es una lista estática. Mantiene la vista por áreas y añade una capa ligera de exploración para que el inventario siga siendo utilizable al crecer:

- búsqueda por nombre, descripción, identificador, rama fuente, PR fuente o ruta prevista;
- filtro por estado `available / implemented`;
- filtro por área Plataforma / Territorio / Negocio / Experiencia;
- contador de resultados anunciado mediante `aria-live`;
- estado vacío y limpieza de filtros;
- controles con objetivo táctil mínimo y composición 3 → 2 → 1 columnas.

La portada `/admin` mantiene un lanzador visible **antes** del centro de control. El primer acceso abre el directorio completo y el resto de accesos disponibles se generan desde el registro canónico, evitando otra lista manual susceptible de quedarse desactualizada.

## Disponibles en esta rama

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

## Implementados en ramas funcionales

| Módulo | Rama fuente verificada | PR fuente | Ruta Admin objetivo |
| --- | --- | ---: | --- |
| Empresas | `feat/v20-business-directory` | #80 | `/admin/empresas` |
| Experiencias y reservas | `feat/v20-business-experiences` | #84 | `/admin/empresas/experiencias` |
| Mágina Pass | `feat/v20-business-magina-pass` | #82 | `/admin/empresas/magina-pass` |
| Rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas` |
| Comunidad de rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas/comunidad` |
| Patrocinios de rutas | `feat/v20-routes-explore` | #81 | `/admin/rutas/patrocinios` |
| Mágina Aventura | `feat/v20-routes-adventure` | #87 | `/admin/rutas/aventuras` |

Estas rutas objetivo son información de gobierno y no enlaces activos desde esta rama. Solo pasarán a `href` cuando el código funcional correspondiente haya sido absorbido y validado en la base común.

## Acceso transversal de `/admin`

Todo el segmento `/admin` pasa por `AdminRouteGate` desde `apps/web/src/app/admin/layout.tsx`.

El gate:

- espera una sesión de producto autenticada;
- valida además `adminApi.session()` contra el servidor;
- no habilita la superficie solicitada en la UI antes de confirmar autorización;
- distingue `401` de sesión caducada y resincroniza `AuthProvider`;
- trata `403` como cuenta autenticada sin permiso corporativo Admin;
- ante error de validación mantiene la UI administrativa cerrada y ofrece reintento;
- vuelve a validar al recuperar foco o visibilidad de la pestaña, evitando mantener una sesión Admin visualmente abierta después de caducar;
- bloquea validaciones concurrentes para que `focus` y `visibilitychange` no generen dos comprobaciones que compitan entre sí;
- revalida en segundo plano sin desmontar una pantalla autorizada solo por volver a la pestaña, evitando perder estado local de formularios.

Este gate es una **capa común de experiencia y defensa adicional**, no la frontera de seguridad de los datos. La autorización real sigue siendo responsabilidad obligatoria de cada endpoint `/api/v1/admin/...`, que recibe la sesión mediante `credentials: include` y debe rechazar en servidor a quien no tenga permiso de plataforma. No deben almacenarse secretos en páginas estáticas ni en `admin-modules.json`.

El layout aplica también de forma centralizada `robots: noindex`, `nofollow` y `nocache` a todo el árbol Admin. Las páginas pueden conservar metadata específica de título/descripción sin depender de recordar individualmente la política de indexación.

## Protección del propio acceso administrativo

Una sesión `super_admin` no puede degradar ni revocar su propio acceso desde `PUT /api/v1/admin/platform-access/:userId`.

La API responde `409 cannot_change_current_admin_access` cuando el `userId` objetivo coincide con el administrador actual. Esto evita que un superadministrador se convierta accidentalmente en `admin`, `editor`, `support` o se revoque a sí mismo y pierda capacidad de recuperación.

La consola refleja la misma regla: la fila de la cuenta actual se muestra como **acceso protegido**, sin selector de rol ni acción de revocación. Los handlers de UI también rechazan esos intentos antes de llamar a la API. La seguridad no depende de esa UI: el backend mantiene siempre el bloqueo definitivo.

## Política de settings públicos

`site_settings` sigue admitiendo claves internas flexibles, pero **ser privada o pública no es una decisión arbitraria del formulario**.

Solo estas claves pueden publicarse mediante `/api/v1/public/site-settings`:

- `alerts.banner`
- `home.hero`
- `home.territory_banner`
- `site.contact`
- `site.identity`
- `site.seo`
- `site.social`

La API aplica la allowlist en dos puntos:

1. `PUT /api/v1/admin/settings/:key` rechaza `is_public=true` para cualquier otra clave con `400 setting_not_publicable`;
2. `/api/v1/public/site-settings` vuelve a filtrar por la misma política para no exponer accidentalmente una fila antigua que hubiera quedado marcada pública.

La consola mantiene el checkbox **Disponible para la web pública** deshabilitado cuando la clave no pertenece a esta allowlist y fuerza el estado a privado al cambiar a una clave interna. De este modo UI y servidor comunican la misma política, sin confiar exclusivamente en el cliente.

## Autorización de API

El contrato de gobierno recorre todos los ficheros TypeScript de `apps/api/src/routes`, independientemente de su nombre, y detecta cualquier handler que declare una ruta `/api/v1/admin/...`.

Cada endpoint detectado debe:

1. asignar el resultado de `requirePlatformAccess(...)` dentro de su handler;
2. cortar la ejecución de forma fail-closed cuando ese acceso sea nulo;
3. importar el control de acceso corporativo del servidor.

Esto permite que futuros módulos como Empresas o Rutas queden cubiertos aunque sus routers no se llamen `admin-*.ts`. El check imprime en cada ejecución el número de endpoints Admin protegidos y de routers auditados.

La jerarquía actual queda además ejercitada por smoke tests reales con PostgreSQL para `support`, `editor`, `admin` y `super_admin`, verificando que lectura y mutaciones respetan los mínimos ya declarados por el backend. El smoke de seguridad verifica también que la propia cuenta superadmin no puede degradarse, que una clave pública reconocida sí puede publicarse y que una clave interna no puede cruzar a la API pública.

## Detección de rutas huérfanas

El contrato recorre los directorios de primer nivel de `apps/web/src/app/admin` que tengan una `page.tsx`.

Si aparece una nueva ruta `/admin/<algo>` y no corresponde a un módulo `available` del registro, CI falla con **Ruta Admin huérfana**. Así una nueva adquisición no puede aterrizar silenciosamente fuera del centro unificado.

Del mismo modo, cada módulo `available` debe resolver a una `page.tsx` real; un enlace declarado sin página también hace fallar el contrato.

## Ayuntamientos

La base de esta rama es `feat/v20-municipalities-admin-navigation`, por lo que Ayuntamientos ya se considera disponible dentro de este frente. El directorio enlaza al centro municipal real y conserva la separación de sus editores especializados.

## Empresas, Pass y Experiencias

La auditoría confirma que estos tres dominios ya tienen superficies Admin reales en sus respectivas ramas. No deben reconstruirse dentro del Admin unificado. La integración correcta consiste en absorber primero su código funcional y después cambiar su estado a `available` con el `href` ya existente.

## Rutas, comunidad, patrocinios y Aventura

La auditoría confirma superficies Admin reales para Rutas, moderación comunitaria, patrocinios y Mágina Aventura. El centro unificado registra sus contratos pero no sustituye sus dominios ni adelanta enlaces antes de la absorción.

La actividad GPS privada de Rutas no añade una superficie administrativa separada: sigue siendo una función opt-in del usuario y no se crea un módulo Admin artificial para inflar cobertura.

## Contrato automático

`scripts/check-admin-unified-control-center.mjs` comprueba como mínimo:

- existencia del registro canónico y de los 20 dominios críticos de referencia;
- IDs únicos y campos obligatorios;
- estados y áreas válidos;
- semántica `available` / `implemented`;
- `page.tsx` física para cada `href` disponible;
- ausencia de enlaces activos en módulos aún no absorbidos;
- rama fuente, PR fuente y ruta objetivo de módulos externos;
- rutas Admin huérfanas y duplicados de rutas;
- uso del registro canónico por la UI;
- existencia del directorio filtrable y sus controles básicos accesibles;
- gate corporativo común, tratamiento explícito de `401/403` y fail-closed;
- protección de la propia cuenta tanto en API como en consola;
- allowlist de settings públicos tanto en API como en consola;
- autorización de servidor en todos los endpoints `/api/v1/admin/...` detectados;
- política transversal `noindex/nofollow/nocache`;
- acceso al directorio desde `/admin`;
- regla explícita que separa cobertura administrativa de absorción técnica.

El contrato forma parte de `pnpm check` y `pnpm check:fast`, se ejecuta dentro de `V20 platform admin check` y dispone además del workflow ligero `V20 Admin governance contract`, que no necesita levantar PostgreSQL para detectar regresiones estructurales.

## Validación reciente

Antes del ajuste visual final de la consola, el HEAD `1da57869c3f91e5c25a1213909d0535fa55daf4a` superó `V20 platform admin check`, `V20 full candidate check`, `V20 foundation check`, `V20 environment contract`, `V20 Admin governance contract` y `V20 lockfile guard`. Los cambios posteriores mantienen el mismo contrato y vuelven a ejecutar todos los gates; este documento no debe interpretarse como sustituto del estado de Actions del HEAD actual.

## Próxima absorción

Cuando una rama funcional esté lista para entrar en el Admin unificado:

1. integrar primero su código real en la base coordinada;
2. confirmar que su ruta Admin existe y compila;
3. cambiar en `admin-modules.json` su `status` de `implemented` a `available`;
4. retirar `sourceBranch`, `sourcePr` y `targetHref` y añadir el `href` real;
5. ejecutar el contrato: la ruta física, la navegación y sus endpoints Admin se validarán automáticamente;
6. ejecutar typecheck, build, smokes de rol y checks de dominio antes del handoff.

Para un módulo completamente nuevo, basta con añadir su entrada al registro canónico y su superficie real; el contrato dinámico lo incorporará sin modificar conteos fijos.

Este documento describe cobertura y contrato de integración; no sustituye el estado de CI. La rama solo debe considerarse validada cuando los checks reales del commit correspondiente hayan terminado correctamente.
