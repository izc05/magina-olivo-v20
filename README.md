# Mágina Olivo — Android offline-first

Repositorio de ingeniería del reinicio Android de la aplicación de gestión del olivar. La marca visible aprobada por CR-003 es **Mágina Olivo**.

## Estado

**Resumen del 23/10/2026:** núcleo Android offline-first y cuenta ya desarrollados; **sincronización G2 aún NO integrada ni desplegada**, Gate 21 de aceptación física pendiente y sin APK 1.0 final. El snapshot comprobado de `main` es `3224ac97` (PR #764). Verificar SHA actualizado antes de actuar.

**Trabajos DRAFT pendientes:**
- [#765 — G2.1](https://github.com/izc05/magina-olivo-v20/pull/765): contratos remotos de sincronización; no desplegados.
- [#766 — G2.3](https://github.com/izc05/magina-olivo-v20/pull/766): cola Room persistente; versión 27 propuesta, mientras `main` permanece en Room 26.
- [#768 — G2.4](https://github.com/izc05/magina-olivo-v20/pull/768): ACK, captura local y recuperación; sin cliente HTTP de sincronización.
- [#769 — cierre 1.0](https://github.com/izc05/magina-olivo-v20/pull/769): planes documentales para riego, perfil y Google Play; **no implementados**.

**Orden aprobado de trabajo:** cerrar G2 con pruebas y autorización para integrar → riego → perfil → ayuda/privacidad/Google Play → Gate 21 físico y APK candidata. Web V3 y ampliaciones quedan fuera de la línea productiva. No hacer merges, despliegues ni APK por iniciativa propia.

**Fuente de continuidad vigente:** [`docs/00-master/CURRENT-STATE.md`](docs/00-master/CURRENT-STATE.md), sección inicial «Cola efectiva de trabajo — cierre Android 1.0». Las referencias a CR-011 y fases anteriores se mantienen solo como historial.

**Baseline contractual:** `RC1.2-BASELINE-2026-09-18`. Cualquier cambio estructural requiere control de cambios; esta actualización documental no altera la baseline.

**Visibilidad:** este repositorio GitHub está público actualmente, aunque los datos de la app son privados. La decisión de cambiar visibilidad o adoptar licencia queda pendiente del propietario. No almacenar secretos ni datos agrícolas reales en el repositorio.

## Principio

**Sencillo por fuera, estructurado por dentro.**

La aplicación será Android nativa, privada y offline-first, especializada en la gestión del olivar:

`Finca → Parcela → Campaña → Actuaciones → Gastos/Cosecha/Entregas → Histórico → Informes`

## Lectura obligatoria para agentes

1. [`docs/00-master/RC1-BASELINE.md`](docs/00-master/RC1-BASELINE.md)
2. [`docs/00-master/RC1.2-PRODUCT-LOCK.md`](docs/00-master/RC1.2-PRODUCT-LOCK.md)
3. [`docs/00-master/SINGLE-TRACK-EXECUTION.md`](docs/00-master/SINGLE-TRACK-EXECUTION.md)
4. [`docs/00-master/RC1.2-CHANGE-REQUEST.md`](docs/00-master/RC1.2-CHANGE-REQUEST.md)
5. [`docs/00-master/RC1-NORMATIVE-ADDENDUM.md`](docs/00-master/RC1-NORMATIVE-ADDENDUM.md)
6. [`docs/00-master/MASTER-SPEC-RC1.md`](docs/00-master/MASTER-SPEC-RC1.md)
7. [`docs/00-master/RC1-GATE-REVIEW.md`](docs/00-master/RC1-GATE-REVIEW.md)
8. [`docs/00-master/CURRENT-STATE.md`](docs/00-master/CURRENT-STATE.md)
9. [`docs/07-plans/ROADMAP-RC1.2.md`](docs/07-plans/ROADMAP-RC1.2.md)

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

- [`docs/07-plans/ROADMAP-RC1.2.md`](docs/07-plans/ROADMAP-RC1.2.md) — fases y Gates obligatorios.

## Estado de la ruta

```text
✅ 0.1 Master Spec / identidad de producto
✅ 0.2 Arquitectura escalable Mi Campo
✅ 0.3 Data Model RC1 + Future
✅ 0.4 Catastro Contract
✅ 0.5 Offline/Sync Contract
✅ 0.6 Screen Map
✅ 0.7 Design System Spec
✅ 0.8 RC1.1 Product reconciliation
✅ 0.9 RC1.2 geographic-neutral scope + generic OCR
✅ 0.9 RC1.2 global olive scope + generic OCR
✅ FASE 1 — Android Project Foundation
✅ FASE 2 — Base application architecture
✅ FASE 3 implementación — sistema visual + pantallas de referencia
✅ FASES 4–20 — núcleo agrícola offline-first, Cuaderno de campaña, recolección y servicios de Inicio (Gate 20 PASS 2026-09-28)
✅ CR-010 — Campaña simple (fusionada; Gate CR-010 cerrado según CURRENT-STATE)
✅ CR-011 — Simplificación y pulido UX (cerrada según CURRENT-STATE)
▶ G2 — Sincronización propia Android ↔ Supabase (PRs #765/#766/#768 en DRAFT)
```

**Trabajo activo:** el que indique la sección inicial vigente de [`docs/00-master/CURRENT-STATE.md`](docs/00-master/CURRENT-STATE.md). Los hitos históricos anteriores no autorizan reanudar fases antiguas.

## Regla para agentes

Codex, Claude, Antigravity u otros agentes deben leer primero la sección vigente de `CURRENT-STATE.md`, la baseline, el addendum y el plan de fase. No pueden rediseñar arquitectura, saltar Gates ni introducir nuevas funciones RC1 desde un prompt genérico.

`main` se mantiene estable. Diseño y documentación se trabajan en ramas `docs/*`; implementación en ramas `feat/*`.
