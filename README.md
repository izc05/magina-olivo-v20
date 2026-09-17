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

- [`docs/00-master/RC1-BASELINE.md`](docs/00-master/RC1-BASELINE.md) — decisiones inmutables.
- [`docs/00-master/MASTER-SPEC-RC1.md`](docs/00-master/MASTER-SPEC-RC1.md) — especificación funcional.
- [`docs/00-master/CHANGE-CONTROL.md`](docs/00-master/CHANGE-CONTROL.md) — cómo se puede cambiar la baseline.
- [`docs/02-domain/DATA-MODEL-RC1-FUTURE.md`](docs/02-domain/DATA-MODEL-RC1-FUTURE.md) — modelo de datos RC1 y extensiones futuras.
- [`docs/07-plans/ROADMAP-RC1.md`](docs/07-plans/ROADMAP-RC1.md) — fases y Gates obligatorios.

## Estado de la ruta

```text
✅ 0.1 Master Spec
✅ 0.2 Arquitectura escalable Mi Campo
▶ 0.3 Data Model RC1 + Future
⬜ 0.4 Catastro Contract
⬜ 0.5 Offline/Sync Contract
⬜ 0.6 Screen Map
⬜ 0.7 Design System Spec
⬜ Implementación Android
```

## Regla para agentes

Codex, Antigravity u otros agentes deben leer primero la baseline y el plan de fase. No pueden rediseñar arquitectura, saltar Gates ni introducir nuevas funciones RC1 desde un prompt genérico.

`main` se mantiene estable. Diseño y documentación se trabajan en ramas `docs/*`; implementación en ramas `feat/*`.
