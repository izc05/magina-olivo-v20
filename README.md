# Mágina Olivo V20

Nuevo repositorio limpio para el rediseño completo de Mágina Olivo.

## Estado

Proyecto con baseline RC1 congelada y bloque documental previo a implementación aprobado.

**Baseline vigente:** `RC1.1-BASELINE-2026-09-18`

La arquitectura, el alcance RC1 y la secuencia de fases se consideran congelados. Cualquier cambio estructural requiere un Change Request y una nueva revisión de baseline.

## Principio

**Sencillo por fuera, estructurado por dentro.**

Mágina Olivo será una aplicación Android privada y offline-first para gestionar el olivar:

`Finca → Parcela → Campaña → Actuaciones → Gastos/Cosecha/Entregas → Histórico → Informes`

## Lectura obligatoria para agentes

1. [`docs/00-master/RC1-BASELINE.md`](docs/00-master/RC1-BASELINE.md)
2. [`docs/00-master/RC1.1-PRODUCT-LOCK.md`](docs/00-master/RC1.1-PRODUCT-LOCK.md)
3. [`docs/00-master/RC1.1-CHANGE-REQUEST.md`](docs/00-master/RC1.1-CHANGE-REQUEST.md)
4. [`docs/00-master/RC1-NORMATIVE-ADDENDUM.md`](docs/00-master/RC1-NORMATIVE-ADDENDUM.md)
5. [`docs/00-master/MASTER-SPEC-RC1.md`](docs/00-master/MASTER-SPEC-RC1.md)
6. [`docs/00-master/RC1-GATE-REVIEW.md`](docs/00-master/RC1-GATE-REVIEW.md)
7. [`docs/00-master/CURRENT-STATE.md`](docs/00-master/CURRENT-STATE.md)
8. [`docs/07-plans/ROADMAP-RC1.md`](docs/07-plans/ROADMAP-RC1.md)

## Documentación fuente de verdad

### Gobierno y producto

- [`docs/00-master/RC1-BASELINE.md`](docs/00-master/RC1-BASELINE.md) — decisiones inmutables.
- [`docs/00-master/RC1-NORMATIVE-ADDENDUM.md`](docs/00-master/RC1-NORMATIVE-ADDENDUM.md) — resoluciones normativas de la revisión final; prevalece ante ambigüedades de borradores de componente.
- [`docs/00-master/MASTER-SPEC-RC1.md`](docs/00-master/MASTER-SPEC-RC1.md) — especificación funcional.
- [`docs/00-master/RC1-GATE-REVIEW.md`](docs/00-master/RC1-GATE-REVIEW.md) — revisión cruzada y aprobación de Gates 0.3–0.7.
- [`docs/00-master/CURRENT-STATE.md`](docs/00-master/CURRENT-STATE.md) — punto exacto desde el que debe continuar cualquier nueva sesión.
- [`docs/00-master/CHANGE-CONTROL.md`](docs/00-master/CHANGE-CONTROL.md) — cómo se puede cambiar la baseline.

### Arquitectura y dominio

- [`docs/02-domain/DATA-MODEL-RC1-FUTURE.md`](docs/02-domain/DATA-MODEL-RC1-FUTURE.md) — modelo de datos RC1 y extensiones futuras, sujeto al addendum normativo.
- [`docs/03-maps/CADASTRE-CONTRACT-RC1.md`](docs/03-maps/CADASTRE-CONTRACT-RC1.md) — integración oficial de Catastro, geometría y funcionamiento offline posterior a importación.
- [`docs/01-architecture/OFFLINE-SYNC-CONTRACT-RC1.md`](docs/01-architecture/OFFLINE-SYNC-CONTRACT-RC1.md) — Room, outbox, WorkManager, idempotencia, conflictos y recuperación.

### UX/UI

- [`docs/04-ui/SCREEN-MAP-RC1.md`](docs/04-ui/SCREEN-MAP-RC1.md) — todas las pantallas y flujos RC1; los costes de formularios de actividad se interpretan según el addendum como Expenses enlazados.
- [`docs/04-ui/DESIGN-SYSTEM-RC1.md`](docs/04-ui/DESIGN-SYSTEM-RC1.md) — principios visuales y componentes reutilizables.

### Plan

- [`docs/07-plans/ROADMAP-RC1.md`](docs/07-plans/ROADMAP-RC1.md) — fases y Gates obligatorios.

## Estado de la ruta

```text
✅ 0.1 Master Spec / identidad de producto
✅ 0.2 Arquitectura escalable Mi Campo
✅ 0.3 Data Model RC1 + Future
✅ 0.4 Catastro Contract
✅ 0.5 Offline/Sync Contract
✅ 0.6 Screen Map
✅ 0.7 Design System Spec
▶ FASE 1 — Android Project Foundation
```

**Fase activa:** únicamente Fase 1. No se permite empezar Fase 2 ni funcionalidades agrícolas hasta superar Gate 1.

## Regla para agentes

Codex, Antigravity u otros agentes deben leer primero la baseline, el addendum, `CURRENT-STATE.md` y el plan de fase. No pueden rediseñar arquitectura, saltar Gates ni introducir nuevas funciones RC1 desde un prompt genérico.

`main` se mantiene estable. Diseño y documentación se trabajan en ramas `docs/*`; implementación en ramas `feat/*`.
