# Phase 9 — Activity engine evidence

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-22
**Base commit:** `5ddecdfc` (`main`, Gate 6 composite PASS)
**Branch:** `feat/android-activity-engine`
**Validated commit:** `3caaef94`
**PR:** [#206](https://github.com/izc05/magina-olivo-v20/pull/206) — merged into `main` as `f82be163`

> Every CI, emulator, APK and artifact figure below is copied from a real run.
> Nothing here is estimated.

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
3. **Cost is deliberately absent.** `RC1-NORMATIVE-ADDENDUM` D2 supersedes
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
| E2 — Registrar (+) | `RegisterActivityRoute` / `RegisterActivityViewModel`, replacing the reference screen behind `Registrar actuación` |
| F — tests | `ActivityEngineContractTest.kt`, `AppNavigationTest.kt` |
| G — evidence | this document |

### Navigation

No sixth root tab. The five frozen roots are untouched:
`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`.

Activities are reached in the agricultural context where they belong: an **Actuaciones**
section inside Farm detail, and a nested `activity/{activityId}` route that resolves to
the `Mi Olivar` root, exactly like `farm`, `parcel` and `campaign`.

`Registrar (+) → Registrar actuación` now opens the production editor instead of the old
reference screen. A global entry point has no Farm in context, so it resolves one first:
with a single Farm the question is not asked, with several the Farm is chosen and can be
changed. From there it is the same ViewModel, the same repository and the same aggregate
the Farm detail uses — one record, one home.

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

`AppNavigationTest.registrarPlusCreatesARealActivityOnTheSelectedFarm` — E2E proving the
Registrar (+) entry point writes through the same aggregate: the Activity created there
is the very same row the Farm detail lists, not a second record.

`RoomMigrationTest.migration3To4PreservesActivitiesAndAddsParcelTargets` — the 3→4
migration validated against the compiler-exported schema: the existing Activity survives
untouched, `activity_parcels` appears with its full column set, and the unique
`(activity_id, parcel_id)` index is present.

All waits use the viewport-safe helpers established in Gate 6: `UI_TIMEOUT_MS = 15_000L`
and no `Thread.sleep` anywhere. The multi-parcel E2E initially failed on CI with
`'2 parcelas' is not displayed` — the node was composed but below the fold — which is the
same viewport failure mode already documented in `PHASE6-PARCELS-SLICE.md`; every display
assertion now scrolls its node into view first.

## Known gaps

### Review environment

Nothing in this branch was compiled or executed by the author of these changes: the
review environment has no Android SDK, no Gradle distribution and no emulator. CI is the
only real check, so the workflows were changed to publish what a failure actually says —
Kotlin compiler errors and failing instrumentation output as workflow annotations, which
are readable without a GitHub session — rather than leaving a red run with nothing but
`Process completed with exit code 1`.

### Room v4 schema export — RESOLVED

`app/schemas/com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase/4.json` is now in
the branch, generated by the Room annotation processor during a real Gradle build and
committed verbatim from CI (`b22977be`). It was never written by hand and its
`identityHash` (`459ca541662fd6def079b309cfa1c97f`) is the compiler's own.

`RoomMigrationTest.migration3To4PreservesActivitiesAndAddsParcelTargets` validates the
migration against that exported schema.

### Deferred by design

- Typed agronomic details (Phase 10) — the aggregate has the extension point, nothing more.
- Activity cost as a linked `expenses` row (`RC1-NORMATIVE-ADDENDUM` D3).
- Campaign linkage is modelled (`campaignId`) but not yet offered in the UI.

## Evidence

All figures below come from the runs named in the last column.

| Check | Command / workflow | Result | Run |
| --- | --- | --- | --- |
| Lint | `:app:lintDevDebug` | PASS | [Android CI #326](https://github.com/izc05/magina-olivo-v20/actions/runs/35745611196) |
| Unit tests | `:app:testDevDebugUnitTest` | PASS — 32 tests | Android CI #326 |
| Instrumented compilation | `:app:assembleDevDebugAndroidTest` | PASS | Android CI #326 |
| Debug builds | `assembleDevDebug assembleStagingDebug assembleProductionDebug` | PASS | Android CI #326 |
| Full API 35 instrumentation | `gate3-emulator` | PASS — 75 instrumented tests, `instrumentation_rc=0` | Android CI #326, job `106806329629` |
| Room 3→4 migration test | `RoomMigrationTest` | PASS | Android CI #326 |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | PASS — 20 tests, `offline_room_instrumentation_rc=0` | Android CI #326 |
| Emulator crash buffer | `gate3-emulator-evidence` | EMPTY — `0 evidence/crash.txt` | Android CI #326 |
| Independent emulator run | `Gate 3 Android Emulator Evidence #46` | PASS | [run 35745611268](https://github.com/izc05/magina-olivo-v20/actions/runs/35745611268) |
| Installable DEV APK | `magina-olivo-dev-debug` | 13 258 909 bytes | `sha256:a1f0e131f645d051db18f4188eedbd31e94a5b2af704a08a02701d25eacd7923` |
| Evidence bundle | `gate3-emulator-evidence` | 3 274 884 bytes | `sha256:6d8a4d3ab8a9ce7b729048b963cb70d844beb92d86ee32f17fb14e72288314a3` |

### Emulator and device

```text
serial=emulator-5554
android_release=15
sdk=35
model=Android SDK built for x86_64
abi=x86_64
physical_size=1080x2400
```

Screenshot evidence was captured at 360dp, 393dp, 480dp and 393dp with font scale 1.3.

### Cold start, three consecutive COLD launches

| Run | TotalTime | WaitTime |
| --- | --- | --- |
| 1 | 2301 ms | 2305 ms |
| 2 | 2256 ms | 2262 ms |
| 3 | 2554 ms | 2570 ms |

### Memory after the suite

```text
TOTAL PSS:    76491 kB      TOTAL RSS:   196336 kB     TOTAL SWAP:  0 kB
Java Heap:    13256 kB      Native Heap:  10848 kB     Graphics:    0 kB
Views:            8         Activities:       1        WebViews:    0
```

Activities and views do not accumulate across the suite, so the navigation graph is not
leaking screens. The gfx figures in the same bundle are not a usable performance signal:
only four frames are rendered after the suite, on a software-rendered emulator.

### Test counts

| Suite | Count |
| --- | --- |
| Instrumented (all) | 75 |
| of which Activity contract (`ActivityEngineContractTest`) | 11 |
| of which E2E navigation (`AppNavigationTest`) | 13 |
| Airplane-mode repository tests | 20 |
| JVM unit tests | 32 |

Gate 6 baseline (`164aaa48`) was 61 instrumented tests and 9 airplane-mode repository
tests, with an empty crash buffer. Phase 9 raises both and keeps the crash buffer empty.

```text
PHASE 9 = COMPLETE AND VALIDATED
ROOM V4 SCHEMA EXPORTED BY THE COMPILER AND COMMITTED (459ca541662fd6def079b309cfa1c97f)
```
