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

**Acción:** añadidos `docs/INDEX.md` y `docs/WORKSTREAMS.md`. Este último mantiene ahora un mapa explícito de ramas activas y propiedad de frentes.

### E2E heredado dependía de una barra final de URL

El candidate de partida tenía el `V20 full candidate check` verde pero su `V20 beta browser E2E` fallaba al comparar literalmente `/mi-campo/mapa?fieldId=...` con `/mi-campo/mapa/?fieldId=...`.

**Acción:** el test valida ahora semánticamente el `pathname` normalizado y el parámetro `fieldId`, manteniendo la comprobación real de la pantalla de mapa. No se cambió código GIS ni de producto para satisfacer la prueba.

**Resultado del lote 1:** `V20 full candidate check` y `V20 beta browser E2E` verdes.

## Lote 2 — runtime, variables de entorno y CI rápido

### Node no estaba declarado en el repositorio

CI usaba Node 22 pero el repositorio no declaraba versión compatible para desarrollo local.

**Acción:**

- añadido `.nvmrc` con Node 22;
- añadido `engines.node >=22 <23` en `package.json`;
- declarado también el rango de pnpm compatible con el `packageManager` actual.

### Contrato de entorno incompleto y divergente

El `.env.example` raíz no incluía todas las variables realmente consumidas por API, web, worker y paquetes. Además los ejemplos de API, worker, infra y staging habían evolucionado de forma distinta.

Hallazgos relevantes:

- `VAPID_PUBLIC_KEY` era runtime de API pero faltaba en el contrato raíz;
- el worker necesita `WORKER_MODULES`, `OCR_PROCESSOR_MODE`, VAPID y configuración S3/radar según módulo;
- `AEMET_API_KEY` es usada por weather y por las evaluaciones agronómicas del worker;
- `infra/.env.example` documentaba una variable TTL S3 que el runtime actual no consume;
- staging declaraba `GOOGLE_CLIENT_SECRET`, `SESSION_SECRET` y `OCR_PROVIDER=tesseract` aunque el código actual no los usa;
- el worker actual rechaza iniciar OCR en `NODE_ENV=production` porque todavía no existe un procesador OCR productivo configurado.

**Acción:** alineados `.env.example`, `apps/api/.env.example`, `apps/worker/.env.example`, `infra/.env.example` y `deploy/staging/.env.example` con el runtime implementado. Staging deja OCR fuera de `WORKER_MODULES` hasta disponer de un proveedor productivo real.

### Variables ocultas podían reaparecer

**Acción:** añadido `scripts/check-env-contract.mjs` y comando `pnpm check:env`. El script inspecciona `apps/` y `packages/` y falla si encuentra un `process.env.*` no documentado en `.env.example`.

También se añadió el workflow rápido `V20 environment contract`, que no instala dependencias y se dispara cuando cambian runtime, configuración o el propio contrato.

**Resultado actual:** el gate ha pasado detectando 29 variables runtime, todas documentadas.

### GitHub Actions obsoletas detectadas

Los logs actuales muestran que `actions/checkout@v4` y `actions/setup-node@v4` todavía dependen del runtime antiguo de Actions y GitHub ya los fuerza a Node 24. Las versiones publicadas actuales son superiores.

**Acción iniciada:** el nuevo `V20 environment contract` ya usa `actions/checkout@v7` y `actions/setup-node@v7`. La actualización del resto de workflows se hará por lotes para no provocar conflictos artificiales con ramas de producto activas.

## Coherencia comprobada

El código y los gates actuales confirman:

- sesiones y memberships activas por defecto;
- headers de desarrollo desactivados salvo `ALLOW_DEV_AUTH_HEADERS=true`;
- CORS con allowlist;
- headers de seguridad en API privada;
- `NEXT_PUBLIC_PREVIEW_MODE=false` como configuración esperada fuera de demo;
- Playwright con identidad E2E explícita;
- Node 22 como runtime de aplicación acordado;
- contrato automático de variables de entorno;
- workstreams paralelos documentados por rama.

## Deuda transversal pendiente

Se mantiene como backlog de este mismo workstream:

- actualizar progresivamente las actions antiguas de los workflows existentes;
- introducir un lockfile reproducible y, después, pasar CI a instalación congelada;
- revisar duplicación entre workflows y extraer convenciones comunes cuando aporte valor real;
- auditar configuración de staging/producción conforme se activen nuevos módulos;
- mantener el mapa de workstreams actualizado durante la integración.

## Deuda que pertenece a otros frentes

No se intenta resolver aquí porque ya tiene workstream de producto:

- GIS real de alta/edición de finca y Catastro/SIGPAC;
- experiencia Mi Campo y ficha de finca;
- registro/campaña/cosecha/rendimiento;
- profesional y facturación;
- documentos/OCR funcional;
- clima/radar/mapa de producto;
- Inicio/Hoy;
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
