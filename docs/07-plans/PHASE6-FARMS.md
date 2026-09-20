# Phase 6 — Farms RC1.2 implementation plan

**Status:** Farm slice PASS; Gate 6 overall remains in progress
**Precondition:** Gate 5 PASS  
**Branch:** `feat/android-farms`  
**Stacked base:** `feat/android-room-core` / PR #199

## Contract

Replace the production Mi Olivar/Farm mock routes with a real, offline Farm lifecycle while preserving the approved Phase 3 visual reference. Farm data is read from Room through ViewModels and repositories; accepted saves never wait for a network service.

## Scope

- create the first local workspace safely when the app has no authenticated bootstrap yet;
- observe active and archived Farms with truthful parcel-count, managed/cadastral-area and active-campaign summaries derived from Room;
- create, edit, archive and restore Farms through atomic local row + outbox transactions;
- production list, empty, loading, error and large-list UI states;
- production Farm detail with only locally known values and explicit unknown/empty treatments;
- add/edit form with required name and optional municipality, province, description and notes;
- optional cover document reference selected through a durable Android document URI, without copying attachment binaries before the Documents phase;
- process/restart and airplane-mode lifecycle tests, plus ViewModel and Compose behavior tests.

## Data rules

- Farm name is required after trimming;
- area and parcel count are derived from current, non-deleted parcel memberships, never typed into Farm;
- unknown area/campaign values render as `Sin registrar` / `Sin campaña`, never fake zero data;
- archive is a tombstone and remains recoverable;
- restore increments version and enqueues an UPDATE intent;
- cover selection must retain URI permission and create local document metadata before linking the Farm;
- no Parcel, Campaign or Documents feature UI is implemented early.

## Implementation sequence

1. Extend DAO projections and repository contracts with detail, update, archive/restore and archived reads.
2. Add a local-workspace bootstrap repository so first-run Farm creation satisfies ownership/FK rules and queues its durable intent.
3. Prove repository lifecycle, summaries, rollback and restart behavior with Android tests.
4. Add ViewModels with immutable UI state and validation/error handling.
5. Replace only production Mi Olivar and Farm-detail routes; retain Phase 3 references in the DEV catalogue/tests.
6. Add create/edit/archive/restore Compose tests, including empty state and 50-Farm scrolling behavior.
7. Add durable cover-document selection and its failure states.
8. Run lint, unit tests, all debug environment builds and full API 35 instrumentation under offline evidence.
9. Record Gate 6 evidence and update `CURRENT-STATE.md` only when all blockers are closed.

## Farm-slice acceptance

- first-run workspace + Farm creation works with no network;
- create/edit/archive/restore survives database close/reopen;
- every synchronizable mutation has the expected durable outbox intent;
- list/detail never display mock agricultural values;
- 1, 20 and 50+ Farms remain usable;
- loading, empty, success and recoverable error states are tested;
- back navigation and saved editor state behave correctly;
- accessibility semantics and target sizes remain compliant;
- lint, unit tests, instrumented tests and all debug builds are green;
- emulator crash buffer is empty.

The Farm slice is complete after API 35 validation. Per the current RC mission, Gate 6 remains FAIL/in progress until the stacked Parcel and Campaign slices also pass.
