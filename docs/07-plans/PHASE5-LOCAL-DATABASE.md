# Phase 5 — Local database foundation implementation plan

**Status:** In progress
**Precondition:** Gate 4 PASS
**Branch:** `feat/android-room-core`
**Stacked base:** `feat/android-navigation` / PR #198

## Contract

Introduce Room as the durable local source of truth without starting production feature UI. Accepted offline writes must commit domain state and their outbox operation atomically. IDs are client-generated UUIDs and deletion is soft where agricultural history or later synchronization requires it.

## Scope

- Room 2.8.5 with KSP and exported schemas;
- explicit UTC `Instant`, agricultural `LocalDate`, UUID and enum storage converters;
- ownership root and the minimum RC1.2 tables for profile, farms, parcels/membership, campaigns, activities, harvests, expenses, documents, weather cache, alerts and sync outbox;
- common version, deletion and sync metadata conventions;
- indexed foreign keys and dependency ordering compatible with the approved offline/sync contract;
- focused DAOs with observable `Flow` reads;
- a local Farm repository slice proving atomic local write + outbox behavior without exposing Farm UI early;
- deterministic DEV/test fixture entry point kept outside normal production startup;
- database migration and restart-persistence instrumentation tests.

## Deliberate limits

- no Supabase SDK, remote payload or WorkManager implementation yet;
- no production Farm forms/list state before Phase 6;
- no attachment binary copying before the attachment phase;
- no destructive migration fallback;
- no direct Room access from Compose.

## Implementation sequence

1. Add pinned Room/KSP dependencies and schema export configuration.
2. Add failing converter, metadata and repository-contract tests.
3. Introduce the initial internal schema and export it.
4. Expand to the Phase 5 schema through an explicit migration, retaining both schema snapshots.
5. Add DAOs and an atomic local Farm repository that enqueues stable outbox operations.
6. Wire the database/repository through `AppCompositionRoot`, with no UI consumer yet.
7. Add migration, restart persistence, soft-delete and transactional outbox instrumentation tests.
8. Prove the tests with networking unavailable; Room behavior must not depend on connectivity.
9. Run lint, unit tests, every debug environment build and full API 35 instrumentation.
10. Record Gate 5 evidence and update `CURRENT-STATE.md` only when all blockers are closed.

## Gate 5 acceptance

- exported schemas are committed and an explicit migration validates;
- data written through the repository survives database close/reopen;
- create/update/archive produce observable local state without network access;
- domain mutation and outbox insertion are one Room transaction;
- UUID, timestamp, date and money/unit conventions remain lossless;
- normal reads hide tombstones while recovery queries retain them;
- no destructive migration is configured;
- Compose and ViewModels have no direct DAO/database dependency;
- lint, unit tests, instrumented tests and debug builds are green;
- emulator crash buffer is empty.

Gate 5 remains FAIL until the migration and restart/offline proofs pass on Android instrumentation.
