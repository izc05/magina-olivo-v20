# Índice de documentación — Mágina Olivo V20

Este archivo es la puerta de entrada a la documentación del repositorio. La intención es evitar que decisiones vigentes, blueprints históricos y auditorías operativas queden mezclados sin contexto.

## Empezar aquí

- [`V20_MASTER_ARCHITECTURE.md`](V20_MASTER_ARCHITECTURE.md) — arquitectura técnica general.
- [`V20_MASTER_PRODUCT_ARCHITECTURE.md`](V20_MASTER_PRODUCT_ARCHITECTURE.md) — arquitectura de producto y módulos.
- [`V20_BETA_CLOSURE_AUDIT.md`](V20_BETA_CLOSURE_AUDIT.md) — checklist vivo del candidate/beta.
- [`LOCAL_DEVELOPMENT.md`](LOCAL_DEVELOPMENT.md) — levantar y validar el proyecto en local.
- [`WORKSTREAMS.md`](WORKSTREAMS.md) — reglas para trabajar en paralelo sin pisar ramas.

## Mi Campo

- [`MI_CAMPO_STRUCTURAL_BLUEPRINT.md`](MI_CAMPO_STRUCTURAL_BLUEPRINT.md) — estructura funcional de Mi Campo.
- [`MI_CAMPO_NAVIGATION_BLUEPRINT.md`](MI_CAMPO_NAVIGATION_BLUEPRINT.md) — navegación y jerarquía visible.
- [`V20_MI_CAMPO_DATA_CONTRACT.md`](V20_MI_CAMPO_DATA_CONTRACT.md) — contrato de datos del núcleo privado.
- [`MI_CAMPO_ECONOMICS_MODEL.md`](MI_CAMPO_ECONOMICS_MODEL.md) — costes, ingresos y economía por finca/campaña.
- [`MI_CAMPO_DOCUMENTS_MODEL.md`](MI_CAMPO_DOCUMENTS_MODEL.md) — documentos ligados a finca y actividad.

## GIS, territorio y meteorología

- [`MI_CAMPO_GIS_MODEL.md`](MI_CAMPO_GIS_MODEL.md) — modelo GIS de Mi Campo.
- [`V20_GIS_FARM_LAND_REFERENCES.md`](V20_GIS_FARM_LAND_REFERENCES.md) — referencias de tierra, Catastro/SIGPAC y geometría.
- [`V20_TERRITORY_WEATHER.md`](V20_TERRITORY_WEATHER.md) — territorio y meteorología.
- [`V20_RADAR_OBSERVATION_ARCHITECTURE.md`](V20_RADAR_OBSERVATION_ARCHITECTURE.md) — radar y observación.
- [`WEATHER_RADAR_AGRONOMIC_BLUEPRINT.md`](WEATHER_RADAR_AGRONOMIC_BLUEPRINT.md) — experiencia agronómica sobre clima/radar.

## Identidad, notificaciones y trabajos asíncronos

- [`V20_AUTH_IDENTITY_MEMBERSHIPS.md`](V20_AUTH_IDENTITY_MEMBERSHIPS.md) — sesiones, usuarios, workspaces y memberships.
- [`PUSH_NOTIFICATIONS.md`](PUSH_NOTIFICATIONS.md) — push y notificaciones.
- [`V20_JOBS_OCR_RUNTIME.md`](V20_JOBS_OCR_RUNTIME.md) — jobs y runtime de OCR.
- [`V20_OCR_BENCHMARK_DECISION.md`](V20_OCR_BENCHMARK_DECISION.md) — decisión y benchmark OCR.

## Producto público, administración y expansión

- [`PUBLIC_PRODUCT_BLUEPRINT.md`](PUBLIC_PRODUCT_BLUEPRINT.md) — producto público y exploración.
- [`ADMIN_BLUEPRINT.md`](ADMIN_BLUEPRINT.md) — panel de administración/CMS.
- [`MI_OLIVO_BLUEPRINT.md`](MI_OLIVO_BLUEPRINT.md) — línea futura Mi Olivo.

## UX, despliegue y operación

- [`V20_VISUAL_DIRECTION.md`](V20_VISUAL_DIRECTION.md) — dirección visual.
- [`V20_STAGING_RUNBOOK.md`](V20_STAGING_RUNBOOK.md) — puesta en staging y comprobaciones.

## Higiene documental

Una documentación nueva debe indicar claramente una de estas categorías en su encabezado o propósito:

- **Vigente / operativo:** describe cómo funciona hoy el candidate.
- **Blueprint:** define intención o diseño todavía no completado.
- **Auditoría:** registra estado, deuda, riesgos y criterio de salida.
- **Runbook:** pasos reproducibles de operación/despliegue.

Cuando un documento deje de representar el estado actual, no debe seguir presentándose como operativo. Se actualiza, se marca como histórico o se sustituye por una referencia al documento vigente.
