# Phase 9 — Activity engine evidence

**Phase decision:** PENDING VALIDATION — implementation complete, CI not yet green
**Reviewed:** 2026-09-22
**Base commit:** `5ddecdfc` (`main`, Gate 6 composite PASS)
**Branch:** `feat/android-activity-engine`
**PR:** [#206](https://github.com/izc05/magina-olivo-v20/pull/206) — open, draft, base `main`

> Every CI, emulator, APK and artifact field below stays `PENDING` until a real run
> produces it. Nothing here is estimated.

## Scope

The **common engine** for agricultural activities. Phase 9 builds the canonical Activity
aggregate and its multi-parcel targeting; **Phase 10 adds typed details** (pruning,
fertilisation, treatment, irrigation, clearing, machinery). Harvest, expenses and OCR
are separate phases and are not implemented here.

In scope:

- Activity as the canonical aggregate with a common header;
- relation to Farm, optional relation to Campaign;
- M:N targeting of Parcels through `activity_parcels`;
- one Activity for many Parcels — never one Activity per Parcel;
- states `DRAFT`, `PLANNED`, `COMPLETED`, `CANCELLED` from the existing enum;
- date, description, notes;
- resumable drafts;
- offline create/edit, Room persistence, restart persistence;
- deterministic Activity outbox intent;
- real list/detail/form UI and real navigation;
- extension point for Phase 10 typed details.

Explicitly out of scope: typed agronomic forms, machinery, harvest, expenses, OCR,
and any remote/Supabase work.

## Normative reconciliation

Three findings resolved before writing code; none required a product decision.

1. **`activities` already existed.** Phase 5 created the table and `ActivityEntity` in
   schema v2. Phase 9 therefore **extends** rather than creates: the missing piece was
   `activity_parcels`.
2. **The status enum was already reconciled.** `ActivityStatus` ships
   `DRAFT, PLANNED, COMPLETED, CANCELLED`, which spans both the
   `DATA-MODEL-RC1-FUTURE` draft (`DRAFT|COMPLETED|CANCELLED`) and the RC1.2 Product
   Lock §9 notion of first-class planned work. No new state was invented.
3. **Cost is deliberately absent.** `RC1-NORMATIVE-ADDENDUM` D3 supersedes
   `activities.cost_cents` / `activities.currency`. The columns exist from v2; Phase 9
   never reads or writes them. Activity cost will arrive as a linked `expenses` row in
   its own phase.

`RC1-NORMATIVE-ADDENDUM` D5 defines `activity_parcels` and typed details as aggregate
children of `activities`. The implementation follows that literally.

## Architecture

Unchanged from the Farm/Parcel/Campaign slices. No second architecture.

```text
Compose  →  ViewModel (StateFlow)  →  ActivityRepository  →  Room  →  Outbox
```

- `activities` is the aggregate root; `activity_parcels` are aggregate children.
- Room schema v4 with `MIGRATION_3_4`; the migration only adds `activity_parcels`
  and its indices. **No destructive change, no data rewrite.**
- Compose never touches Room directly; ViewModels depend only on `ActivityRepository`.
- Every mutation commits locally first, bumps the aggregate version, marks the row
  `PENDING` and collapses into a single Activity outbox intent.
- Parcel targets never synchronise independently of their Activity.

### Multi-parcel targeting

```text
Activity (1)  ──<  activity_parcels (N)  >──  Parcel (N)
UNIQUE (activity_id, parcel_id)
```

Selecting three Parcels produces **one** `activities` row and **three**
`activity_parcels` rows. The same canonical Activity is reachable from any of its
Parcels through `observeForParcel`, with the same identity.

## Lifecycle

```text
DRAFT ──plan──> PLANNED ──complete──> COMPLETED
  │                │                      │
  └──cancel────────┴──cancel──> CANCELLED │
                   ▲                 │    │
                   └────reopen───────┴────┘
```

| Rule | Behavior |
| --- | --- |
| Create | `PLANNED` by default; `DRAFT` when saved as a resumable draft |
| Planned activity | requires at least one Parcel |
| Draft | may be saved with no Parcel and resumed later |
| `plan()` | `DRAFT → PLANNED`, requires at least one Parcel |
| `complete()` | `PLANNED → COMPLETED` only |
| `cancel()` | from `DRAFT` or `PLANNED` |
| `reopen()` | from `COMPLETED` or `CANCELLED` back to `PLANNED`, explicit and confirmed |
| Editing a `COMPLETED` activity | rejected (`protected_activity`) until an explicit reopen |
| Archive | only `DRAFT` or `CANCELLED`; `PLANNED` and `COMPLETED` are protected |
| Archived activity | soft-deleted, hidden from active reads, cannot be mutated or resurrected (`archived_activity`); a repeated archive is idempotent |
| Cross-Farm Parcel | rejected; the whole create rolls back |

These are Activity's own rules, derived from the Product Lock and the roadmap. They are
deliberately **not** a copy of the Campaign lifecycle: an Activity has no "one current
per Farm" constraint, has no frozen historical snapshot, and its reopen returns to
`PLANNED` rather than to a harvest state.

## Implementation

| Slice | Files |
| --- | --- |
| A — contract | `domain/activity/ActivityRepository.kt` |
| B — Room v4 | `entity/CoreEntities.kt` (`ActivityParcelTargetEntity`), `model/ActivityRows.kt`, `dao/ActivityDao.kt`, `DatabaseMigrations.kt`, `MaginaOlivoDatabase.kt` |
| C — repository | `data/repository/OfflineFirstActivityRepository.kt`, wired in `LocalPersistence.kt` and `AppCompositionRoot.kt` |
| D — presentation | `feature/activities/ActivityViewModels.kt` |
| E — UI + navigation | `feature/activities/ActivityScreens.kt`, `navigation/AppDestination.kt`, `navigation/AppNavigation.kt`, `feature/farms/FarmScreens.kt` |
| F — tests | `ActivityEngineContractTest.kt`, `AppNavigationTest.kt` |
| G — evidence | this document |

### Navigation

No sixth root tab. The five frozen roots are untouched:
`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`.

Activities are reached in the agricultural context where they belong: an **Actuaciones**
section inside Farm detail, and a nested `activity/{activityId}` route that resolves to
the `Mi Olivar` root, exactly like `farm`, `parcel` and `campaign`.

### UI

Real production screens built from the canonical component set — `MoSectionHeader`,
`MoStatusChip`, `MoPrimaryButton`, `MoSecondaryButton`, `MoTextField`, `MoEmptyState`,
`MoErrorState`, `MoMetricCard`, `MoCream`/`MoWarmWhite`/`MoOutline`. No new visual
language, no ad-hoc colors or spacing. Loading, empty, success and recoverable error
states are all covered, and lifecycle changes go through the same confirmation sheet
pattern used by Campaigns.

## Tests added

`ActivityEngineContractTest` — **11 instrumented contract tests**:

| Test | Contract clause |
| --- | --- |
| `oneActivityTargetsManyParcelsWithoutDuplicatingTheHeader` | one canonical Activity, N targets, reachable from either Parcel |
| `repeatedTargetingNeverDuplicatesAParcelRow` | re-selecting the same Parcel never duplicates a row |
| `parcelOutsideTheFarmIsRejectedWithoutPersistingTheActivity` | cross-Farm selection rolls the create back |
| `createEnqueuesExactlyOneDeterministicCreateIntent` | one CREATE intent |
| `repeatedMutationsNeverDuplicateTheActivityOutboxIntent` | many mutations collapse to one pending intent; targets queue nothing |
| `plannedActivityRequiresAtLeastOneParcel` | validation |
| `aDraftMayBeSavedEmptyAndResumedIntoPlanned` | resumable draft |
| `illegalTransitionsLeaveThePersistedStateUntouched` | every illegal transition is a no-op |
| `completedActivityIsProtectedFromEditsUntilItIsReopened` | no silent edit of a completed record |
| `archiveIsProtectedForPlannedAndCompletedAndCannotResurrect` | archive rules + idempotence |
| `activityAndTargetsSurviveAProcessRestart` | restart persistence |

`AppNavigationTest.oneActivityTargetsTwoParcelsAsASingleCanonicalRecord` — E2E on a real
emulator: create Farm → two Parcels → one Activity targeting **both** → assert exactly
one `activity-row` and two `activity-target` rows → complete → protected → process
restart → still one canonical Activity with two targets.

All waits use the viewport-safe helpers established in Gate 6: `UI_TIMEOUT_MS = 15_000L`
and no `Thread.sleep` anywhere.

## Known gaps

### ROOM V4 SCHEMA EXPORT PENDING RETRIEVAL

`app/schemas/com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase/4.json` is **not
in this branch**. It must be produced by the Room annotation processor during a Gradle
build; its `identityHash` cannot be written by hand and **has not been fabricated**.

Consequence: `RoomMigrationTest` cannot validate the 3→4 migration until the file is
committed. Retrieval options, to be decided:

1. run `gradlew :app:assembleDevDebug` locally and commit the generated file;
2. temporarily publish `app/schemas/**` as a CI artifact and commit it from there.

### Review environment

Nothing in this branch has been compiled or executed by the author of these changes:
the review environment has no Android SDK, no Gradle distribution and no emulator.
Everything is verified by reading. CI is the first real check.

## Evidence gaps pending CI

| Check | Command / workflow | Result | Run / artifact |
| --- | --- | --- | --- |
| Lint | `:app:lintDevDebug` | PENDING | PENDING |
| Unit tests | `:app:testDevDebugUnitTest` | PENDING | PENDING |
| Instrumented compilation | `:app:assembleDevDebugAndroidTest` | PENDING | PENDING |
| Debug builds | `assembleDevDebug assembleStagingDebug assembleProductionDebug` | PENDING | PENDING |
| Full API 35 instrumentation | `gate3-emulator` | PENDING | PENDING |
| Room 3→4 migration test | `RoomMigrationTest` | BLOCKED | schema export pending |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | PENDING | PENDING |
| Emulator crash buffer | `gate3-emulator-evidence` | PENDING | PENDING |
| Installable DEV APK | `magina-olivo-dev-debug` | PENDING | PENDING |

Baseline to beat, from Gate 6 (`164aaa48`): 61 instrumented tests, 9 airplane-mode
repository tests, empty crash buffer.

```text
PHASE 9 = IMPLEMENTATION COMPLETE / VALIDATION PENDING
ROOM V4 SCHEMA EXPORT PENDING RETRIEVAL
```
