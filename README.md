# Mágina Olivo V20

Aplicación web de Mágina Olivo para gestión del olivar, actividad profesional y contenidos públicos de Sierra Mágina. V20 se desarrolla como un monorepo y ya dispone de un candidate Beta funcional con validación continua.

> La referencia funcional actual es `integrate/v20-beta-closure` (PR #58). `main` todavía no representa el candidate integrado de V20.

## Estructura

- `apps/web`: aplicación Next.js, experiencia pública, Mi Campo, Profesional y administración.
- `apps/api`: API y contratos de acceso a datos.
- `apps/worker`: procesos de fondo para módulos operativos como OCR, radar y notificaciones.
- `packages`: contratos y librerías compartidas.
- `database`: esquema y migraciones controladas.
- `e2e`: pruebas Browser E2E con Playwright.
- `deploy/staging`: contenedores, proxy y operaciones del entorno de staging.
- `docs`: arquitectura, decisiones y runbooks operativos.

## Requisitos

- Node.js 22 (`.nvmrc`; rango `>=22 <23`).
- pnpm 10.15.1 (`packageManager` y `pnpm-lock.yaml`).

El `preinstall` comprueba estas versiones para reducir diferencias entre desarrollo, CI y staging.

## Puesta en marcha

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Servicios independientes:

```bash
pnpm dev:api
pnpm dev:worker
```

Copia únicamente las variables necesarias desde `.env.example`. No confirmes secretos ni credenciales en el repositorio.

## Validación

```bash
pnpm check:fast
pnpm build
pnpm e2e:beta
```

`pnpm check` ejecuta el contrato completo: runtime, lockfile, entorno, staging, migraciones, contenedores, tipos y build.

## Staging

Consulta [docs/V20_STAGING_RUNBOOK.md](docs/V20_STAGING_RUNBOOK.md) y [docs/V20_STAGING_FIRST_DEPLOY_CHECKLIST.md](docs/V20_STAGING_FIRST_DEPLOY_CHECKLIST.md). Los archivos operativos viven en `deploy/staging`.

La configuración real de staging, sus secretos y las operaciones sobre el host se gestionan fuera del flujo normal de desarrollo y no deben improvisarse desde una rama de interfaz.

## Flujo de ramas

- `integrate/v20-beta-closure`: candidate coordinado de la Beta.
- `main`: rama estable histórica; no usar todavía como referencia funcional de V20.
- Las mejoras se desarrollan en ramas independientes y se proponen mediante PR contra el candidate.

Antes de iniciar trabajo nuevo, actualiza referencias remotas y confirma el HEAD real de `origin/integrate/v20-beta-closure`; no dependas de un SHA copiado en la descripción de un PR.
