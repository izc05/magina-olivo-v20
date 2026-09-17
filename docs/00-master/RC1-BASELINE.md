# Mágina Olivo Android — RC1 Baseline

**Status:** APPROVED BASELINE
**Baseline ID:** RC1-BASELINE-2026-09-17
**Rule:** this document is the source of truth for the RC1 work line. Structural changes require an explicit Change Request and a new baseline revision (RC1.1, RC1.2, ...). Do not silently rewrite this file to change architecture or scope.

## Product identity

Mágina Olivo is an Android application for private management of one or more olive-growing holdings. It is not a territorial portal and it is not an information/news application.

The immutable core is:

```text
FINCA
  ↓
PARCELA
  ↓
CAMPAÑA
  ↓
ACTUACIONES
  ↓
GASTOS / COSECHA / ENTREGAS
  ↓
HISTÓRICO
  ↓
INFORMES
```

## RC1 core scope

RC1 must cover:

- account and private workspace;
- farms;
- cadastral parcels and own stored geometries;
- agricultural campaigns;
- field activities;
- expenses;
- harvests;
- olive deliveries;
- photos and documents;
- parcel timeline/history;
- campaign/farm/parcel reporting;
- offline operation;
- deferred synchronization;
- Android APK validation on real hardware.

## Explicitly outside RC1

The following do not enter RC1 unless a new baseline revision explicitly adds them:

- weather and rain radar;
- weather alerts;
- news;
- oil prices;
- public cooperative directory;
- tourism or town information;
- advertising;
- gamification;
- marketplace;
- general editorial content;
- AI assistants;
- advanced sensors/IoT.

Ideas in these areas go to post-RC1 backlog, not to active implementation.

## Immutable architectural decisions

1. **Android first.** RC1 delivers a real installable Android APK.
2. **Kotlin + Jetpack Compose** for the Android client.
3. **Offline-first.** Normal field work must not depend on connectivity.
4. **Room is the local operational source of truth.** UI reads local state.
5. **Write locally first.** Saving a field record succeeds locally before remote sync.
6. **WorkManager/outbox model** for durable deferred synchronization.
7. **Supabase is the remote backend**, behind repository/service abstractions.
8. **PostgreSQL/PostGIS** stores synchronized structured/geographic data.
9. **MapLibre-compatible map abstraction** for Android map rendering.
10. **Catastro is an external source, not the application's database.** Selected parcel geometry is copied into Mágina Olivo's own model.
11. **Campaign historical snapshots are mandatory.** Later parcel changes must not rewrite prior campaign history.
12. **Soft delete/versioning** for critical historical entities.
13. **Feature growth is modular.** New Mi Campo modules depend on the core; the core must not depend on optional modules.
14. **Bottom navigation remains simple:** Inicio · Fincas · Campaña · Registrar · Más.
15. **No feature is Done** until UI, persistence, validation, offline behavior, tests and synchronization (when applicable) are covered.

## Scalability principle — Mi Campo

The future application can grow into a full private farm-management system, but growth is layered:

```text
LEVEL 1 — RC1 CORE
Fincas · Parcelas · Campañas · Actuaciones · Cosecha · Gastos · Documentos

LEVEL 2 — FARM OPERATIONS
Tareas · Calendario · Personal · Maquinaria · Productos · Almacén · Compras · Proveedores · Riego · Análisis

LEVEL 3 — ADVANCED MANAGEMENT
Zonas · Colaboración · Automatización · Sensores · Analítica avanzada · IA
```

RC1 implements Level 1 while keeping the data model extensible for Levels 2 and 3.

## Priority order

```text
1. DO NOT LOSE DATA
2. WORK OFFLINE
3. BE SIMPLE TO USE
4. IDENTIFY PARCELS CORRECTLY
5. PRESERVE HISTORY
6. SYNCHRONIZE SAFELY
7. LOOK EXCELLENT
```

Visual polish is important but never overrides data integrity or field usability.

## Mandatory development discipline

Each phase follows:

```text
Read baseline/spec
→ work only on the assigned phase
→ isolated branch/worktree
→ tests first where practical
→ implement
→ build
→ verify on relevant device/data
→ review diff
→ commit
→ PR
→ pass Gate
→ integrate
→ mark Gate complete
→ next phase
```

Do not skip phases because a later feature looks attractive.

## RC1 success criterion

RC1 is accepted when a user can manage a real olive campaign from end to end using the app for:

- what land they manage;
- what was done on it;
- what it cost;
- what was harvested/delivered;
- the preserved historical result;

including periods without mobile coverage.
