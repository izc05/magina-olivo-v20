# V20 — ingeniería transversal, limpieza y estructura

Rama: `chore/v20-cleanup-structure`

Base inicial histórica: `09299449bb07eab24867c658e63a32350ae7d805`

Objetivo: mantener un workstream transversal para estructura del repositorio, tooling, CI, contratos de runtime, staging, seguridad de supply-chain e higiene de integración, sin duplicar los frentes de producto que avanzan en ramas propietarias.

## Estado verificado

Último HEAD técnico completamente verificado antes de esta actualización documental:

- Foundation: `10d61ea01aae9353ba6f344525f74bcacf109468`
- Candidate integrado: `76a086717236057027f2b24a06d7942546d5e52c`
- Sincronización en ese punto: **0 commits behind**

Gates sobre ese HEAD:

- ✅ `V20 environment contract` #55
- ✅ `V20 lockfile guard` #47
- ✅ `V20 foundation check` #38
- ✅ `V20 full candidate check` #2174
- ✅ `V20 beta browser E2E` #483
- ✅ `V20 staging readiness` #102

`main` permanece fuera de este workstream y PR #10 no se fusiona automáticamente.

## Naturaleza del workstream

Foundation no es un módulo funcional. Su función es cerrar deuda compartida y proporcionar garantías que todas las ramas puedan reutilizar:

- repositorio y documentación de entrada coherentes;
- versiones de runtime reproducibles;
- dependencias bloqueadas;
- contratos de entorno;
- CI segura y mantenible;
- migraciones verificables;
- staging reproducible;
- supply-chain de Actions y contenedores inmutable;
- sincronización segura con el candidate.

No cubre diseño de pantallas ni ampliación funcional de GIS, Mi Campo, Registro/Campaña, Profesional, Documentos/OCR, Weather/Mapa, Inicio/Hoy, Perfil, Planificar, Admin o Público/Explorar cuando ya existe workstream propietario.

## Lote 1 — entrada al repositorio y trabajo paralelo

Se corrigió la documentación inicial, que todavía describía V20 como una arquitectura previa a implementación.

Entregables:

- `README.md` actualizado al monorepo real;
- `docs/INDEX.md` como índice técnico;
- `docs/LOCAL_DEVELOPMENT.md` reconciliado con auth, memberships, CORS y preview actuales;
- `AGENTS.md` con reglas de trabajo paralelo;
- `docs/WORKSTREAMS.md` con ownership de ramas;
- plantilla de PR con alcance, archivos compartidos y validación;
- `database/seeds/002_dev_identity.sql` para identidad/membership local reproducible;
- `.gitignore` ampliado para artifacts, caches, logs, Playwright y TypeScript.

Reglas consolidadas:

- cada chat/agente trabaja en su rama;
- `main` no se toca durante desarrollo paralelo;
- no se fusiona automáticamente el candidate;
- datos reales por defecto y preview explícita;
- responsive 360/390/430 cuando cambia UI;
- handoff con SHA, archivos, pruebas y limitaciones.

## Lote 2 — runtime y dependencias reproducibles

Runtime acordado:

- Node 22 mediante `.nvmrc`;
- `engines.node >=22 <23`;
- pnpm `10.15.1` declarado en `packageManager`;
- `scripts/check-runtime-contract.mjs` ejecutado también desde `preinstall`.

Dependencias:

- `pnpm-lock.yaml` v9 versionado;
- CI usa `pnpm install --frozen-lockfile`;
- `pnpm-workspace.yaml` permite únicamente el lifecycle script revisado de `esbuild`;
- `strictDepBuilds: true` evita ejecutar scripts nuevos no revisados silenciosamente.

Comandos raíz relevantes:

```text
pnpm check:runtime
pnpm check:lockfile
pnpm check:env
pnpm check:staging
pnpm check:migrations
pnpm check:containers
pnpm check:fast
pnpm check
```

## Lote 3 — contratos de entorno y seguridad de producción

`scripts/check-env-contract.mjs` mantiene documentadas **39 variables runtime** consumidas por apps/paquetes.

`scripts/check-staging-contract.mjs` controla **41 variables de staging**, de las cuales **5 son deploy-only**.

El contrato de staging exige, entre otras garantías:

- `NODE_ENV=production`;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `AUTH_COOKIE_SECURE=true`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- CORS explícito y HTTPS;
- API y web en orígenes coherentes;
- `GOOGLE_CLIENT_ID` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` iguales;
- bucket claramente aislado de producción;
- OCR Tesseract con límites explícitos;
- worker con `ocr,radar,notifications`.

Claves obsoletas rechazadas en staging:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

La API además falla cerrado si producción intenta arrancar con `ALLOW_DEV_AUTH_HEADERS=true`, y `request-context` ignora esos headers bajo `NODE_ENV=production` incluso si la app se construye directamente en una prueba.

## Lote 4 — CI y supply-chain de GitHub Actions

`scripts/audit-ci.mjs --strict` audita actualmente:

- **23 workflows**;
- **75 referencias a Actions**;
- todas las Actions externas fijadas por SHA completo de 40 caracteres;
- versión humana conservada como comentario (`# v7`, etc.);
- runtime Node tomado de `.nvmrc`;
- instalaciones pnpm congeladas;
- triggers críticos de candidate/E2E;
- permisos mínimos de GitHub Actions;
- Pages con `contents: read` global y write/id-token únicamente en el job de deploy.

Pins centralizados en `scripts/ci-action-pins.mjs` para:

- checkout;
- setup-node;
- pnpm setup;
- cache;
- upload-artifact;
- setup-python;
- configure-pages;
- upload-pages-artifact;
- deploy-pages.

`scripts/modernize-ci.mjs` y `scripts/pin-ci-actions.mjs` generan las referencias aprobadas, mientras el auditor impide volver a tags mutables.

## Lote 5 — migraciones persistentes y repetibles

Además del guard estático `scripts/check-migrations.mjs`, staging usa un runner persistente con `schema_migrations`.

Garantías:

- cada SQL queda registrado;
- checksum SHA-256 por migración;
- estados `applying` / `applied`;
- una migración histórica aplicada no se reejecuta;
- modificar una migración aplicada produce mismatch;
- estado interrumpido/inconsistente falla cerrado;
- una segunda pasada correcta es no-op.

`V20 staging readiness` comprueba que el número de filas `applied` coincide con el número de migraciones SQL.

## Lote 6 — staging reproducible

`deploy/staging/` contiene:

```text
.env.example
Dockerfile.api
Dockerfile.worker
Dockerfile.web
docker-compose.yml
migrate.sh
deploy-host.sh
nginx.conf
```

Stack:

```text
PostGIS 17
   ↓
migrate one-shot
   ↓
API Fastify + worker + web nginx
```

Hardening actual:

- PostgreSQL no publica puerto al host;
- API y web solo publican en loopback;
- API/worker usan filesystem `read_only`;
- `tmpfs` explícito;
- `cap_drop: ALL`;
- `no-new-privileges`;
- nginx ejecuta imagen unprivileged;
- web se construye con preview desactivada;
- worker incluye Tesseract `spa+eng` y Poppler;
- OCR se valida también dentro de la imagen final.

## Lote 7 — imágenes Docker inmutables

El siguiente riesgo de supply-chain tras fijar GitHub Actions eran los tags Docker mutables.

Ahora las bases de staging conservan un tag legible y añaden digest SHA-256 inmutable:

- Node 22 Bookworm slim;
- nginx unprivileged 1.27 Alpine;
- PostGIS 17 / PostgreSQL 3.5;
- PostgreSQL 17 Bookworm.

`scripts/check-container-pins.mjs` audita Dockerfiles, Compose y las imágenes auxiliares usadas por `deploy-host.sh`.

Resultado actual:

> `Container pin contract OK: 9 staging image references use explicit tags plus immutable sha256 digests.`

El gate Foundation y Staging Readiness ejecutan `pnpm check:containers`, por lo que reintroducir una base sin digest o con `latest` rompe CI.

## Lote 8 — despliegue de staging y backup previo

`deploy/staging/deploy-host.sh` se usa como recorrido real también desde Staging Readiness.

Flujo validado:

1. preflight del `.env` privado;
2. build de imágenes;
3. arranque aislado de PostgreSQL;
4. espera de readiness;
5. **backup `pg_dump -Fc` obligatorio antes de cualquier migración**;
6. arranque de migraciones, API, worker y web;
7. health local;
8. worker en ejecución;
9. segunda pasada de migraciones no-op;
10. comprobación del registro de migraciones.

Readiness valida además que el dump existe, no está vacío y puede ser leído por `pg_restore` **PostgreSQL 17**, reutilizando el servicio `migrate` fijado por digest. Esto evita validar un dump de PG17 con el cliente PG16 que Ubuntu 24.04 instala por defecto.

## Lote 9 — deploy remoto exact-SHA

`.github/workflows/staging-deploy.yml` es manual y requiere Environment `staging`.

El deploy:

- exige SHA completo;
- exige confirmación `DEPLOY-STAGING`;
- comprueba que el SHA pertenece al historial actual del candidate;
- exige Full Candidate + Browser E2E + Staging Readiness verdes para ese mismo SHA;
- valida `.env` y URLs;
- valida SSH/known_hosts;
- sube una release inmutable;
- ejecuta `deploy-host.sh`;
- realiza smoke HTTPS externo;
- solo después actualiza `current` / `CURRENT_SHA`.

No existe rollback destructivo automático de base de datos.

El staging externo real **no se considera aprobado** hasta disponer de host, dominios HTTPS, bucket, Google Auth, AEMET/radar, VAPID, observabilidad y una prueba real de backup + restore.

## Lote 10 — QA transversal

Foundation conserva y sincroniza los gates del candidate sin apropiarse del workstream QA:

- recorrido Playwright real de agricultor;
- storage fixture controlado para documento/OCR;
- responsive 360/390/430;
- controles críticos en tablet/escritorio;
- trazas/screenshots ante fallos;
- Full Candidate con smokes de API, economía, documentos y Profesional.

La regla sigue siendo: si una prueba revela una regresión funcional, se corrige el producto en su workstream; Foundation solo corrige infraestructura, contratos o pruebas transversales que sean de su propiedad.

## Integración segura con el candidate

Cuando `feat/v20-visual-prototype` avanza, Foundation no copia ciegamente archivos compartidos.

Procedimiento:

1. inspeccionar commits nuevos;
2. distinguir producto de infraestructura transversal;
3. conservar contratos/hardening más fuertes de Foundation;
4. absorber la intención compatible;
5. crear merge de historial con el candidate como segundo padre;
6. comprobar `behind_by = 0`;
7. validar el HEAD exacto con los seis gates.

Este procedimiento evitó reintroducir durante la sincronización variables obsoletas, tags Docker mutables y versiones de tooling incompatibles.

## Deuda transversal pendiente

No bloquea el lote actual:

- staging externo real y restore comprobado en host;
- observabilidad persistente;
- CSP/HSTS cuando los dominios definitivos existan;
- rate limiting según exposición de endpoints;
- revisar redundancia/coste de CI sin debilitar gates;
- mantener pins de Actions y contenedores cuando se actualicen versiones;
- continuar sincronización segura mientras el candidate siga recibiendo workstreams.

No se considera deuda Foundation el desarrollo de pantallas o módulos ya asignados a otros chats.

## Criterio de cierre de un lote Foundation

Un lote transversal se considera cerrado cuando:

- no introduce cambios accidentales de producto;
- deja un guard preventivo cuando es razonable;
- queda sincronizado con el candidate;
- los archivos compartidos están reconciliados explícitamente;
- los seis gates relevantes están verdes sobre el HEAD técnico;
- la deuda restante tiene propietario y no se presenta como resuelta si depende de staging externo.
