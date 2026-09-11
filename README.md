# Mágina Olivo V20

Mágina Olivo V20 es la reconstrucción de la plataforma agrícola de Mágina con una arquitectura API-first, móvil y preparada para trabajar con datos reales de finca, campaña, GIS, meteorología, documentos y actividad profesional.

> **Principio de producto:** sencillo por fuera, estructurado por dentro.

## Estado actual

V20 está en fase de **cierre de beta/candidate**. Ya no es un repositorio previo a implementación: existen web, API, worker, PostgreSQL/PostGIS, contratos compartidos, recorridos E2E y checks especializados de CI.

El núcleo privado cubre, entre otros:

- fincas y campañas;
- trabajos, riegos, cosecha, rendimiento y economía;
- agenda y planificación;
- Catastro/SIGPAC y contexto GIS;
- AEMET, radar y alertas;
- documentos, OCR y revisión;
- clientes, presupuestos, trabajos, facturas y cobros profesionales;
- autenticación, sesiones y memberships por workspace.

## Arquitectura

```text
apps/
  web/       Next.js + React
  api/       Fastify + Kysely
  worker/    trabajos asíncronos

packages/
  contracts/ contratos y esquemas compartidos
  jobs/      contratos de jobs
  weather/   lógica meteorológica compartida

database/
  migrations/
  seeds/

services/
  ocr-benchmark/

infra/       entorno local y servicios auxiliares
deploy/      recursos de despliegue
e2e/         recorridos Playwright
docs/        decisiones, modelos y auditorías
```

## Desarrollo local

Requisitos principales: Node.js 22, pnpm 10.15.1, Docker/Compose y `psql`.

```bash
pnpm install
docker compose -f infra/docker-compose.dev.yml up -d
```

La preparación completa de base de datos, identidad local, API, web y modo preview está documentada en [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md).

## Validación

```bash
pnpm typecheck
pnpm build
pnpm e2e:beta
```

También puede ejecutarse el chequeo agregado:

```bash
pnpm check
```

GitHub Actions añade validaciones con PostGIS real, migraciones, API, GIS, meteorología/radar, documentos/OCR, flujo profesional, navegador y auditoría móvil.

## Reglas importantes

- `main` se mantiene estable; el trabajo entra mediante ramas y PR revisables.
- El modo demo solo es válido con `NEXT_PUBLIC_PREVIEW_MODE=true`.
- Cuando existe API configurada, un error real no debe convertirse silenciosamente en datos demo.
- Las modificaciones de esquema se realizan mediante nuevas migraciones; no se reescriben migraciones ya compartidas.
- No se mezclan frentes grandes en una misma rama. GIS/mapa, limpieza estructural, UX y módulos de producto deben poder revisarse de forma independiente.

## Documentación

El índice mantenido de documentación está en [`docs/INDEX.md`](docs/INDEX.md). El checklist vivo del candidate está en [`docs/V20_BETA_CLOSURE_AUDIT.md`](docs/V20_BETA_CLOSURE_AUDIT.md).
