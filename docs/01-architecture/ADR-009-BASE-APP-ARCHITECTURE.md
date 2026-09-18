# ADR-009 — Base Android Application Architecture

**Status:** Accepted for RC1.2 Phase 2  
**Date:** 2026-09-18  
**Baseline:** `RC1.2-BASELINE-2026-09-18`

## Context

The Android foundation is now validated. Before adding persistence or agricultural features, the application needs stable boundaries that prevent UI code from depending directly on databases, network SDKs or external providers.

The product must remain:

- Android-native;
- offline-first;
- testable;
- region-neutral in its domain foundations;
- simple enough to maintain without premature framework complexity.

## Decision

Keep a **single Android app Gradle module** during Phase 2 and establish architectural boundaries by package and constructor-injected contracts.

Dependency direction:

```text
UI / Compose
   ↓
Application / use cases (later)
   ↓
Repository contracts (later)
   ↓
Infrastructure implementations (later)

Core primitives are independent of feature UI and remote SDKs.
```

Phase 2 introduces:

- `AppResult` and `AppError`;
- `AppClock`;
- `IdGenerator`;
- `AppDispatchers`;
- `AppLogger`;
- `RegionalContext`;
- `UnitPreferences`;
- `AppEnvironment`;
- `AppCompositionRoot`;
- `AppRoot`.

## Dependency injection

Use constructor injection and one explicit `AppCompositionRoot`.

Do not add Hilt, Koin or another dependency-injection framework in Phase 2.

A DI framework may be reconsidered only when at least one of these becomes true:

- object graph complexity creates repeated manual wiring;
- multiple scopes/lifetimes are needed;
- feature ownership/module boundaries justify it;
- testing becomes materially harder without assisted injection.

The mere existence of many classes is not sufficient justification.

## Gradle modularization

Keep one `:app` module for now.

Consider Gradle multi-module extraction only when evidence exists, such as:

- build times become a real problem;
- independent feature ownership appears;
- strict API boundaries need compiler enforcement;
- reusable modules genuinely exist.

Do not split modules for appearance or “enterprise” style.

## Errors/results

Infrastructure-specific exceptions must not leak into presentation contracts.

Use project-owned `AppError` categories. User-facing translated messages belong to the presentation layer, not the domain error type.

## Time

Use `AppClock`.

Rules:

- machine/audit timestamps use `Instant`;
- agricultural calendar dates use local date with explicit zone context;
- campaign dates are domain data, never inferred from `createdAt`.

## Identity

Use client-generated UUIDs through `IdGenerator`.

The same UUID must survive future Room persistence and Supabase synchronization.

## Concurrency

Use `AppDispatchers` instead of hard-coding coroutine dispatchers inside repositories/use cases.

Phase 2 adds the abstraction; real background workflows arrive later.

## Logging

Use `AppLogger`.

Do not routinely log:

- credentials/tokens;
- raw OCR content;
- precise parcel geometries;
- sensitive personal/account data.

## Regional neutrality

`RegionalContext` explicitly carries:

- country;
- locale;
- timezone;
- currency.

`UnitPreferences` carries measurement choices.

Spain is the first default implementation, not the identity of the domain.

No core entity may depend on Jaén, Spain Catastro or EUR as an invariant.

## UI root

`MainActivity` builds the composition root and passes it to `AppRoot`.

Compose screens do not construct infrastructure services themselves.

Phase 2 intentionally keeps the visible Foundation screen unchanged.

## Deferred

Not allowed in Phase 2:

- Room;
- Supabase;
- MapLibre;
- WorkManager;
- agricultural entities;
- navigation shell;
- OCR;
- weather;
- notifications;
- feature repositories.

## Consequences

Positive:

- later features have deterministic time/ID seams;
- SDKs remain behind future infrastructure boundaries;
- tests can use fakes without global mutable state;
- regional expansion does not require rewriting the core.

Trade-offs:

- explicit wiring is slightly verbose;
- the single app module relies on tests/review for package discipline until modularization is justified.

## Validation

Gate 2 requires:

- all environment builds green;
- unit tests green;
- instrumentation smoke compile/green;
- architecture boundary test green;
- no forbidden future infrastructure in Phase 2 source;
- physical launcher still opens after the refactor.
