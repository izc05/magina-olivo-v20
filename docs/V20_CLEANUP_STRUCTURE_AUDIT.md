# V20 — auditoría de limpieza y estructura

Rama: `chore/v20-cleanup-structure`

Base inicial: `09299449bb07eab24867c658e63a32350ae7d805`

Objetivo: limpiar y estructurar el repositorio sin mezclar este trabajo con el frente GIS/mapa ni con nuevas funciones de producto.

## Alcance cerrado de esta rama

Esta rama cubre:

1. documentación de entrada al repositorio;
2. desarrollo local reproducible;
3. identidad de desarrollo controlada;
4. comandos canónicos de validación;
5. higiene de archivos generados;
6. índice documental;
7. reglas de trabajo paralelo;
8. comprobación de coherencia entre documentación y estado real del candidate;
9. endurecimiento de tests de infraestructura cuando bloqueen el cierre por diferencias de representación y no por comportamiento de producto.

No cubre cambios funcionales de mapa, Catastro/SIGPAC, diseño de pantallas ni ampliación de módulos.

## Hallazgos de partida

### 1. README obsoleto

El README seguía describiendo V20 como un repositorio de arquitectura/documentación previa a implementación, aunque el proyecto ya contiene web, API, worker, PostGIS, OCR, GIS, meteorología, profesional y E2E.

**Acción:** corregido. El README vuelve a ser una puerta de entrada fiable al repositorio.

### 2. Desarrollo local describía una autenticación ya superada

`LOCAL_DEVELOPMENT.md` afirmaba que Auth/Workspace Membership todavía no estaba implementado. En el código actual sí existen sesiones y memberships, y los headers `x-user-id`/`x-workspace-id` solo funcionan cuando `ALLOW_DEV_AUTH_HEADERS=true`.

**Acción:** corregido. Se documenta autenticación real, headers de desarrollo opt-in, CORS y modo preview.

### 3. Seed demo sin identidad reproducible

El seed de demo creaba workspace/campaña/finca pero no una identidad local asociada, lo que hacía menos reproducibles las pruebas manuales que necesitan `created_by` o membership.

**Acción:** añadido `database/seeds/002_dev_identity.sql` con usuario local y membership owner sobre el workspace demo.

### 4. Validación raíz fragmentada

Existían `typecheck`, `build` y `e2e:beta`, pero faltaba un comando único para la comprobación estructural normal del monorepo.

**Acción:** añadido `pnpm check` (`typecheck` + `build`) y `pnpm check:fast`.

### 5. Artefactos de Playwright/tooling no estaban cubiertos expresamente

**Acción:** `.gitignore` ampliado para informes Playwright, resultados de tests, logs, cache, store de pnpm y `tsbuildinfo`.

### 6. Documentación extensa sin índice

`docs/` ya contiene arquitectura, producto, GIS, clima, radar, OCR, auth, administración, staging y varios blueprints, pero no tenía una entrada mantenida que distinguiera documentación operativa de diseños futuros.

**Acción:** añadido `docs/INDEX.md` con agrupación por dominio y criterio de higiene documental.

### 7. Ramas paralelas sin contrato de trabajo explícito

El repositorio tiene varios workstreams y agentes. Sin reglas simples, un refactor lateral puede provocar conflictos innecesarios entre mapa, UX, profesional, OCR y limpieza.

**Acción:** añadido `docs/WORKSTREAMS.md` con alcance, tipos de rama y criterio de cierre.

### 8. E2E heredado dependía de una barra final de URL

El candidate de partida tenía el `V20 full candidate check` verde pero su `V20 beta browser E2E` fallaba al comparar de forma literal `/mi-campo/mapa?fieldId=...` con `/mi-campo/mapa/?fieldId=...`. Ambas URLs representan la misma navegación en la configuración actual y la aplicación sí entregaba el `fieldId` correcto.

**Acción:** el test valida ahora semánticamente el `pathname` normalizado y el parámetro `fieldId`, manteniendo después la comprobación real de la pantalla de mapa. No se ha cambiado código GIS ni de producto para satisfacer la prueba.

## Coherencia comprobada

El código actual confirma:

- sesiones y memberships activas por defecto;
- headers de desarrollo desactivados salvo `ALLOW_DEV_AUTH_HEADERS=true`;
- CORS con allowlist;
- headers de seguridad en API privada;
- `NEXT_PUBLIC_PREVIEW_MODE=false` como configuración esperada fuera de demo;
- Playwright con identidad E2E explícita;
- candidate con checks separados para navegador y validación completa.

## Deuda fuera de alcance

No se intenta resolver aquí porque pertenece a otros frentes:

- selector GIS real de alta/edición de finca;
- overlay final de radar;
- ampliación E2E visual de OCR con storage controlado;
- Admin/CMS completo;
- producto público dinámico;
- performance/CSP/HSTS/rate limiting de staging;
- pulido de UX de pantallas funcionales.

Esos puntos continúan en `V20_BETA_CLOSURE_AUDIT.md` o en su workstream específico.

## Criterio de finalización

La rama queda lista para integrar cuando:

- los archivos modificados estén revisados;
- el PR no incluya cambios accidentales de producto;
- `V20 full candidate check` esté verde para esta rama/PR;
- `V20 beta browser E2E` esté verde cuando se dispare por los archivos de esta rama;
- no existan conflictos no resueltos con el HEAD candidato al momento de integrar.
