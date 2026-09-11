# V20 — ingeniería transversal, limpieza y estructura

Rama: `chore/v20-cleanup-structure`

Base inicial: `09299449bb07eab24867c658e63a32350ae7d805`

Objetivo: mantener un workstream transversal de V20 para estructura del repositorio, tooling, CI, entorno reproducible y deuda compartida, sin duplicar los frentes de producto que avanzan en ramas paralelas.

## Naturaleza del workstream

Este frente **no termina después de una única limpieza**. Va cerrando lotes independientes mientras V20 se desarrolla en paralelo. Su función es reducir fricción de integración y detectar deuda que afecta a más de un módulo.

No cubre cambios funcionales de mapa, Catastro/SIGPAC, diseño de pantallas ni ampliación de módulos cuando ya existe una rama propietaria.

## Lote 1 — entrada al repositorio y cierre del candidate

### README obsoleto

El README seguía describiendo V20 como un repositorio de arquitectura/documentación previa a implementación, aunque el proyecto ya contiene web, API, worker, PostGIS, OCR, GIS, meteorología, profesional y E2E.

**Acción:** corregido. El README vuelve a ser una puerta de entrada fiable al repositorio.

### Desarrollo local describía una autenticación ya superada

`LOCAL_DEVELOPMENT.md` afirmaba que Auth/Workspace Membership todavía no estaba implementado. En el código actual sí existen sesiones y memberships, y los headers `x-user-id`/`x-workspace-id` solo funcionan cuando `ALLOW_DEV_AUTH_HEADERS=true`.

**Acción:** corregido. Se documenta autenticación real, headers de desarrollo opt-in, CORS y modo preview.

### Seed demo sin identidad reproducible

El seed de demo creaba workspace/campaña/finca pero no una identidad local asociada.

**Acción:** añadido `database/seeds/002_dev_identity.sql` con usuario local y membership owner sobre el workspace demo.

### Validación raíz fragmentada

Existían `typecheck`, `build` y `e2e:beta`, pero faltaba un comando único para la comprobación normal del monorepo.

**Acción:** añadidos `pnpm check` y `pnpm check:fast`.

### Higiene de artefactos

**Acción:** `.gitignore` ampliado para informes Playwright, resultados de tests, logs, caches, store de pnpm y `tsbuildinfo`.

### Documentación y ramas paralelas

**Acción:** añadidos `docs/INDEX.md`, `docs/WORKSTREAMS.md` y `AGENTS.md`. El repositorio mantiene ahora un mapa explícito de ramas activas, ownership y protocolo de handoff.

### E2E heredado dependía de una barra final de URL

El candidate de partida tenía el `V20 full candidate check` verde pero su `V20 beta browser E2E` fallaba al comparar literalmente `/mi-campo/mapa?fieldId=...` con `/mi-campo/mapa/?fieldId=...`.

**Acción:** el test valida ahora semánticamente el `pathname` normalizado y el parámetro `fieldId`, manteniendo la comprobación real de la pantalla de mapa. No se cambió código GIS ni de producto para satisfacer la prueba.

**Resultado del lote 1:** `V20 full candidate check` y `V20 beta browser E2E` verdes.

## Lote 2 — runtime, variables de entorno y dependencias reproducibles

### Node y pnpm declarados

CI usaba Node 22 pero el repositorio no declaraba versión compatible para desarrollo local.

**Acción:**

- añadido `.nvmrc` con Node 22;
- añadido `engines.node >=22 <23` en `package.json`;
- declarado el rango de pnpm compatible con `packageManager`.

### Contrato de entorno

El `.env.example` raíz no incluía todas las variables consumidas por API, web, worker y paquetes.

**Acción:** reconciliados `.env.example`, API, worker, infra y staging. `scripts/check-env-contract.mjs` inspecciona `apps/` y `packages/` y falla ante variables `process.env.*` no documentadas.

**Resultado:** 29 variables runtime documentadas y gate `V20 environment contract` verde.

### Lockfile reproducible

El repositorio no tenía `pnpm-lock.yaml` y CI instalaba con `--no-frozen-lockfile`.

**Acción:** generado y versionado `pnpm-lock.yaml` (lockfile v9), añadido `check:lockfile` y gate `V20 lockfile guard`.

`pnpm check`, `pnpm check:fast` y el nuevo `V20 foundation check` usan instalación reproducible y validan entorno + typecheck/build.

**Resultado:** foundation verde con Node 22, pnpm 10.15.1, instalación congelada, contrato de entorno, typecheck y build completo.

## Lote 3 — CI compartida y trabajo paralelo

### Inventario de deuda CI

Se añadió `scripts/audit-ci.mjs` para auditar workflows de forma reproducible.

Primera auditoría objetiva:

- 21 workflows;
- 66 referencias a Actions;
- 17 comandos de instalación pnpm;
- 52 referencias a Actions antiguas;
- 15 instalaciones mutables (`--no-frozen-lockfile`).

### Modernización controlada de workflows

Se añadió `scripts/modernize-ci.mjs` y se generó un artefacto validado con `audit-ci --strict` antes de publicarlo.

**Aplicado:** 18 workflows actualizados mecánicamente, 67 sustituciones:

- `actions/checkout@v4` → `actions/checkout@v7`;
- `actions/setup-node@v4` → `actions/setup-node@v7`;
- `pnpm/action-setup@v4` → `pnpm/action-setup@v6`;
- `actions/upload-artifact@v4` → `actions/upload-artifact@v7`;
- `pnpm install --no-frozen-lockfile` → `pnpm install --frozen-lockfile`.

El workflow temporal usado para generar el artefacto se elimina tras publicar el lote. Los archivos grandes (`beta-browser-e2e.yml` y `visual-prototype-check.yml`) se verificaron por SHA contra el artefacto validado antes de ensamblar el commit.

### Protocolo para chats/agentes paralelos

`AGENTS.md` y `docs/WORKSTREAMS.md` consolidan:

- cada chat/agente trabaja en una rama propia y un frente concreto;
- no se toca `main`;
- no se fusiona automáticamente el candidate;
- ownership de archivos compartidos;
- responsive 360/390/430 para pantallas;
- datos reales por defecto y preview explícita;
- formato estándar de handoff con SHA, archivos, tests y limitaciones.

Workstreams registrados: Inicio/Hoy, Mi Campo/Finca, Registro/Campaña, Profesional, Documentos/OCR, GIS/Nueva finca, QA móvil/E2E, Público/Explorar, Perfil/Ajustes, Planificar/Tareas, Admin, Weather/Radar y este frente transversal.

## Coherencia comprobada

El código y los gates actuales confirman:

- sesiones y memberships activas por defecto;
- headers de desarrollo solo con `ALLOW_DEV_AUTH_HEADERS=true`;
- CORS con allowlist;
- headers de seguridad en API privada;
- `NEXT_PUBLIC_PREVIEW_MODE=false` fuera de demo;
- Playwright con identidad E2E explícita;
- Node 22 y pnpm 10.15.1 como runtime/tooling acordados;
- lockfile versionado;
- contrato automático de variables de entorno;
- foundation reproducible;
- workstreams paralelos documentados por rama.

## Deuda transversal pendiente

Backlog de este workstream después de cerrar la modernización CI:

- vigilar que nuevas ramas no reintroduzcan Actions antiguas o instalaciones mutables;
- revisar duplicación entre workflows y extraer componentes reutilizables solo cuando reduzca complejidad real;
- auditar configuración de staging/producción conforme se activen nuevos módulos;
- mantener foundation sincronizada con el candidate activo;
- revisar seguridad y permisos de Actions antes del cierre beta;
- mantener el mapa de workstreams al día durante la integración.

## Deuda que pertenece a otros frentes

No se resuelve aquí porque ya tiene workstream propietario:

- GIS real de alta/edición de finca y Catastro/SIGPAC;
- experiencia Mi Campo y ficha de finca;
- registro/campaña/cosecha/rendimiento;
- profesional y facturación;
- documentos/OCR funcional;
- clima/radar/mapa de producto;
- Inicio/Hoy;
- Perfil/Ajustes;
- Planificar/Tareas;
- Admin;
- público/explorar;
- QA móvil y pulido UX.

## Criterio de cierre de cada lote

Cada lote transversal queda listo cuando:

- no introduce cambios accidentales de producto;
- deja automatización preventiva cuando sea razonable;
- los archivos modificados están revisados;
- los gates afectados están verdes;
- cualquier deuda restante tiene propietario claro.
