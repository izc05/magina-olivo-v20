# Mágina Olivo V20

Nuevo repositorio limpio para el rediseño completo de Mágina Olivo.

## Estado

Proyecto en fase de especificación previa a implementación Android.

**Baseline vigente:** `RC1-BASELINE-2026-09-17`

La arquitectura, el alcance RC1 y la secuencia de fases se consideran congelados. Cualquier cambio estructural requiere un Change Request y una nueva revisión de baseline.

## Principio

**Sencillo por fuera, estructurado por dentro.**

Mágina Olivo será una aplicación Android privada y offline-first para gestionar el olivar:

`Finca → Parcela → Campaña → Actuaciones → Gastos/Cosecha/Entregas → Histórico → Informes`

## Documentación fuente de verdad

### Gobierno y producto

- [`docs/00-master/RC1-BASELINE.md`](docs/00-master/RC1-BASELINE.md) — decisiones inmutables.
- [`docs/00-master/MASTER-SPEC-RC1.md`](docs/00-master/MASTER-SPEC-RC1.md) — especificación funcional.
- [`docs/00-master/CHANGE-CONTROL.md`](docs/00-master/CHANGE-CONTROL.md) — cómo se puede cambiar la baseline.

### Arquitectura y dominio

- [`docs/02-domain/DATA-MODEL-RC1-FUTURE.md`](docs/02-domain/DATA-MODEL-RC1-FUTURE.md) — modelo de datos RC1 y extensiones futuras.
- [`docs/03-maps/CADASTRE-CONTRACT-RC1.md`](docs/03-maps/CADASTRE-CONTRACT-RC1.md) — integración oficial de Catastro, geometría y funcionamiento offline posterior a importación.
- [`docs/01-architecture/OFFLINE-SYNC-CONTRACT-RC1.md`](docs/01-architecture/OFFLINE-SYNC-CONTRACT-RC1.md) — Room, outbox, WorkManager, idempotencia, conflictos y recuperación.

### UX/UI

- [`docs/04-ui/SCREEN-MAP-RC1.md`](docs/04-ui/SCREEN-MAP-RC1.md) — todas las pantallas y flujos RC1.
- [`docs/04-ui/DESIGN-SYSTEM-RC1.md`](docs/04-ui/DESIGN-SYSTEM-RC1.md) — principios visuales y componentes reutilizables.

### Plan

- [`docs/07-plans/ROADMAP-RC1.md`](docs/07-plans/ROADMAP-RC1.md) — fases y Gates obligatorios.

## Estado de la ruta

```text
✅ 0.1 Master Spec / identidad de producto
✅ 0.2 Arquitectura escalable Mi Campo
🟡 0.3 Data Model RC1 + Future — documentado, pendiente de aprobación de Gate
🟡 0.4 Catastro Contract — documentado, pendiente de aprobación de Gate
🟡 0.5 Offline/Sync Contract — documentado, pendiente de aprobación de Gate
🟡 0.6 Screen Map — documentado, pendiente de aprobación de Gate
🟡 0.7 Design System Spec — documentado, pendiente de aprobación de Gate
⬜ Implementación Android
```

No se inicia código productivo hasta revisar/aprobar los Gates documentales 0.3–0.7 o registrar explícitamente las excepciones mediante Change Request.

## Regla para agentes

Codex, Antigravity u otros agentes deben leer primero la baseline y el plan de fase. No pueden rediseñar arquitectura, saltar Gates ni introducir nuevas funciones RC1 desde un prompt genérico.

`main` se mantiene estable. Diseño y documentación se trabajan en ramas `docs/*`; implementación en ramas `feat/*`.
