# Phase 2 — Base Application Architecture Execution Plan

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Precondition:** Gate 1 PASS.  
**Rule:** this plan may be read/prepared now, but **no Phase 2 implementation starts before Gate 1 is recorded as PASS**.

## Goal

Establish the smallest scalable Android architecture needed before agricultural features. Phase 2 must create stable boundaries, not product UI.

The result should let later phases add Room, farms, parcels, campaigns, maps and sync without coupling Compose screens directly to persistence or network code.

## Non-goals

Do not add in Phase 2:

- Room;
- Supabase;
- MapLibre;
- WorkManager;
- farms/parcels/campaign entities;
- bottom-navigation feature screens;
- weather/news/oil-market clients;
- OCR;
- notifications;
- feature-specific repositories.

## Architectural direction

Keep a single Android app module during Phase 2. Prefer package boundaries and dependency rules over premature Gradle multi-module fragmentation.

Target package shape:

```text
com.isivoltpro.maginaolivo
├── app
│   ├── App.kt
│   ├── AppRoot.kt
│   └── AppCompositionRoot.kt
├── core
│   ├── common
│   │   ├── AppResult.kt
│   │   ├── AppError.kt
│   │   └── Preconditions.kt
│   ├── time
│   │   ├── AppClock.kt
│   │   └── SystemAppClock.kt
│   ├── id
│   │   ├── IdGenerator.kt
│   │   └── UuidGenerator.kt
│   ├── dispatchers
│   │   └── AppDispatchers.kt
│   └── logging
│       └── AppLogger.kt
└── foundation
    └── existing Phase 1 launcher shell
```

Phase 2 may later be extracted into modules if real build-time or ownership pressure appears. Do not split merely to look “enterprise”.

## Dependency rule

```text
Compose/UI
   ↓
application/use-case layer
   ↓
repository contracts
   ↓
infrastructure implementations

core/common has no dependency on Android UI or remote services.
```

For Phase 2 itself, only the core abstractions and app composition root are implemented.

## Task 1 — Preserve Phase 1 contracts

Before changing code:

- read RC1.1 baseline/Product Lock;
- verify production application id remains `com.isivoltpro.maginaolivo`;
- verify DEV/STAGING/PRODUCTION variants still build;
- keep the Phase 1 smoke test alive;
- do not rename package/application IDs.

Acceptance:

```text
lintDevDebug PASS
testDevDebugUnitTest PASS
assembleDevDebug PASS
assembleStagingDebug PASS
assembleProductionDebug PASS
```

## Task 2 — Introduce application composition root

Create a single explicit place where infrastructure objects are wired.

Target:

```text
AppCompositionRoot
  ├── AppClock
  ├── IdGenerator
  ├── AppDispatchers
  └── AppLogger
```

Do not hide object creation in random Composables or singleton objects.

Initial DI strategy:

- constructor injection;
- explicit composition root;
- no service locator;
- no global mutable container;
- do not add Hilt/Koin until there is enough real graph complexity to justify it.

This keeps Phase 2 lightweight and testable.

## Task 3 — Define AppResult/AppError contract

Use a project-owned error/result vocabulary so later repositories do not leak Retrofit/SQL/SDK exception types into UI.

Example conceptual states:

```text
AppResult<T>
  Success<T>
  Failure(AppError)

AppError
  Validation
  NotFound
  Conflict
  Permission
  Offline
  Storage
  Remote
  Unknown
```

Rules:

- do not use exceptions as normal UI state;
- preserve original throwable internally for logging where useful;
- user-facing messages are resolved in UI/presentation, not stored as English/Spanish strings in domain errors.

## Task 4 — Clock abstraction

Agricultural records depend heavily on local dates while sync uses instants.

Create:

```text
AppClock
  nowInstant()
  today(zoneId)
```

Production implementation delegates to the system clock.

Tests can provide deterministic time.

Do not derive campaign dates from `createdAt`.

## Task 5 — UUID abstraction

Create `IdGenerator` with a production UUID implementation.

Rules:

- IDs are generated before persistence;
- later Room/Supabase must preserve the same domain UUID;
- tests can inject deterministic IDs.

## Task 6 — Dispatcher abstraction

Define dispatchers centrally:

```text
AppDispatchers
  default
  io
  main
```

Do not hard-code `Dispatchers.IO` throughout later repositories/use cases.

## Task 7 — Logging contract

Create a tiny logging interface suitable for later:

- debug;
- info;
- warning;
- error.

Requirements:

- no secrets/tokens/passwords;
- no OCR ticket raw content in ordinary logs;
- no precise parcel geometry in analytics logs by default;
- production logging implementation can evolve later.

## Task 8 — App root

Move Phase 1 launcher composition behind an `AppRoot`/composition root without changing visible behavior.

The launcher still only proves the app opens. No RC1.1 navigation shell yet.

## Task 9 — Architecture tests

Add tests that protect:

1. production application ID remains stable;
2. deterministic clock injection works;
3. deterministic ID generator works;
4. AppResult maps known failures predictably;
5. app composition root can be constructed in DEV test configuration;
6. no Phase 2 package references Room/Supabase/MapLibre/WorkManager classes.

The sixth check may be a source/dependency assertion rather than a complex architecture-test library.

## Task 10 — Documentation

Create/update:

- `docs/01-architecture/ADR-009-BASE-APP-ARCHITECTURE.md`;
- package/dependency diagram;
- rationale for explicit composition root;
- rule for when a future DI framework or Gradle modularization is justified.

## Gate 2 evidence

Automated:

- lint green;
- unit tests green;
- all three debug environment builds green;
- instrumentation smoke still green;
- architecture contract tests green.

Review:

- no agricultural feature implementation;
- no Room/Supabase/MapLibre/WorkManager;
- no global service locator;
- no Compose screen directly constructing future infrastructure;
- package boundaries match this plan.

Physical device:

- existing launcher still installs/opens after architectural refactor.

## Gate 2 PASS condition

Only after all evidence is recorded may Phase 3 begin.

## Handoff to Phase 3

Phase 3 receives:

- stable Compose app root;
- deterministic core services;
- result/error contract;
- explicit dependency wiring;
- unchanged environment/application identities.

Phase 3 then owns visual tokens/components and reference screens.

## RC1.2 regional-neutral architecture rule

Phase 2 must not hard-code Jaén, Spain, EUR, Europe/Madrid, hectare-only display logic or Spanish Catastro identifiers into domain primitives.

Introduce only the minimal abstractions needed to keep later code honest:

```text
RegionalContext
  countryCode
  locale
  timezone
  currency

UnitPreferences
  area
  volume
  mass
```

Persistence/UI editing of these values belongs to later phases. Phase 2 only establishes safe boundaries/default-provider injection.
