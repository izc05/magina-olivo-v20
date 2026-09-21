# Gate 6 — Campaigns slice evidence

**Slice decision:** PENDING — implementation complete, validation not yet executed
**Gate 6 overall:** FAIL — Campaign slice not validated
**Reviewed:** 2026-09-21
**Base commit:** `5f1c6f9a` (`feat/android-parcels`, Parcel slice PASS)
**Implementation commit under review:** `7002ce3d` plus uncommitted contract-hardening work
**Stacked PR:** not opened yet

> No CI run, emulator run, APK hash or test count in this document may be filled in
> from estimation. Every `PENDING` field stays `PENDING` until a real run produces it.

## Scope

Production, offline-first Campaign lifecycle for a Farm, with immutable historical
Parcel snapshots and real `Finca → Parcela → Campaña` navigation.

In scope:

- create, edit while in preparation, activate, move to harvest, close, reopen and archive a draft;
- exactly one current (ACTIVE or HARVEST) Campaign per Farm;
- Parcel selection frozen atomically at activation;
- Room-backed active list and history for a Farm;
- deterministic Campaign outbox intents;
- loading, empty, success and recoverable error states;
- truthful summaries: absent kg, delivery, yield and expense data renders `Sin datos`.

Explicitly out of scope for this slice: activity/harvest/expense capture, MapLibre,
live Catastro lookup and any Gate 7 work.

## Architecture

Unchanged from the Product Lock and from the Farm and Parcel slices.

```text
Compose  →  ViewModel (StateFlow)  →  CampaignRepository  →  Room  →  Outbox
```

- `campaigns` is the aggregate root; `campaign_parcels` are aggregate children.
- Room schema v3, `MIGRATION_2_3`, schema exported to
  `app/schemas/com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase/3.json`.
- Compose never touches Room directly; ViewModels depend only on `CampaignRepository`.
- Every mutation commits locally first, bumps the aggregate version, marks the row
  `PENDING` and collapses into a single Campaign outbox intent.
- Snapshot children never synchronize independently of their Campaign.
- Closed history renders frozen snapshot values, never current Farm or Parcel data.
- No new third-party dependency was introduced.

## Lifecycle

```text
PREPARATION → ACTIVE → HARVEST → CLOSED
                          ▲          │
                          └──────────┘
                        reopen (explicit, confirmed, audited)
```

The lifecycle is strictly linear. `ACTIVE` cannot be closed directly: it must pass
through `HARVEST`. The only backwards edge is `CLOSED → HARVEST` via `reopen`, which
clears the end date, advances the aggregate version and queues an outbox intent. No
`previousStatus` column is stored; the linear contract makes it unnecessary.

| Rule | Behavior |
| --- | --- |
| Parcel selection editable | only in `PREPARATION` |
| Activation with zero Parcels | rejected; status, version and outbox unchanged |
| Second current Campaign per Farm | rejected atomically (`active_campaign_exists`) |
| Activation | atomically freezes Farm name, Parcel name, managed area, cadastral reference and geometry |
| Close | legal only from `HARVEST`; `ACTIVE` → `CLOSED` is an illegal transition |
| Close date | the confirm action uses the device date; no date picker in RC1 |
| End date before start date | rejected (`endDate` / `before_start`) |
| Archive | allowed only in `PREPARATION`; `ACTIVE`, `HARVEST` and `CLOSED` are protected |
| Reopen | only from `CLOSED`, explicit and confirmed; clears the end date and advances the version |
| Reopen while another Campaign is current | rejected (`active_campaign_exists`) |
| Archived Campaign | no mutation may resurrect it (`archived_campaign`) |

## Defect found and fixed during this review

**Soft-deleted Campaigns remained fully mutable.**

`CampaignDao.findById` intentionally returns rows regardless of `deleted_at`, and
`OfflineFirstCampaignRepository.mutate` did not re-check the flag. An archived draft
kept `status = PREPARATION`, so `activate`, `updatePreparation`, `markHarvest`,
`close` and `reopen` all still succeeded on it. An archived draft could therefore be
resurrected and could take the Farm's single current-Campaign slot, silently
contradicting the archive contract.

`OfflineFirstFarmRepository` already guards `metadata.deletedAt` on every mutation;
the Campaign aggregate did not. Fix applied, matching the existing Farm pattern:

- `mutate` rejects soft-deleted aggregates with `AppError.Conflict("archived_campaign")`;
- `archivePreparation` opts out of that guard and returns `Success` when the Campaign
  is already archived, mirroring the idempotent Farm archive.

No architecture, schema, Product Lock decision or UI was changed.

## Tests added

New file `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/CampaignLifecycleContractTest.kt`
(11 instrumented tests). `OfflineFirstCampaignRepositoryTest` is unchanged and keeps
the original end-to-end activation and restart proof.

| Test | Contract clause |
| --- | --- |
| `createEnqueuesExactlyOneDeterministicCreateIntent` | create queues exactly one CREATE intent |
| `repeatedMutationsNeverDuplicateTheCampaignOutboxIntent` | six mutations still collapse to one pending intent; snapshots queue nothing |
| `activationWithoutParcelsFailsWithoutMutatingStatusOrOutbox` | zero-Parcel activation leaves status, version and outbox untouched |
| `secondCurrentCampaignForTheSameFarmIsRejectedAtomically` | one current Campaign per Farm, for ACTIVE and for HARVEST |
| `parcelOutsideTheFarmIsRejectedWithoutPersistingTheCampaign` | cross-Farm Parcel selection rolls the whole create back |
| `illegalTransitionsLeaveThePersistedStateUntouched` | every illegal transition from each of the four states is a no-op |
| `endDateBeforeStartDateIsRejectedWithoutClosingTheCampaign` | invalid end date does not close or bump the version |
| `closeThenReopenIsExplicitAuditedAndClearsTheEndDate` | full close → reopen → close cycle; version advances, snapshots survive |
| `reopenIsRejectedWhileAnotherCurrentCampaignHoldsTheFarmSlot` | reopen respects the single-current-Campaign rule |
| `archiveIsRejectedForActiveHarvestAndClosedCampaigns` | protected states cannot be archived |
| `archivedPreparationIsRemovedFromTheFarmAndCannotBeResurrected` | regression test for the defect above; double archive is idempotent |

`AppNavigationTest.farmParcelCampaignLifecyclePersistsAcrossRecreation` hardened:

- the E2E Campaign now starts on a past date, so the legal close date no longer
  depends on the day the suite runs (see open risk below);
- after the process restart the test asserts the protected history, reopens the
  Campaign through the confirmation dialog, closes it again and re-asserts the frozen
  snapshot — proving the audited reopen path end to end, which no test covered before.

## Acceptance matrix

| Requirement | Status | Evidence |
| --- | --- | --- |
| Create campaign | IMPLEMENTED | `CampaignScreens.kt`, `createEnqueuesExactlyOneDeterministicCreateIntent` |
| Edit campaign (preparation only) | IMPLEMENTED | `preparationSelectionCanChangeButCannotAfterActivation` |
| Select active campaign | IMPLEMENTED | `secondCurrentCampaignForTheSameFarmIsRejectedAtomically` |
| Close campaign (HARVEST only) | IMPLEMENTED | `closeThenReopenIsExplicitAuditedAndClearsTheEndDate` |
| Reopen campaign | IMPLEMENTED | `closeThenReopenIsExplicitAuditedAndClearsTheEndDate`, E2E reopen path |
| History and summaries | IMPLEMENTED | `CampaignScreens.kt`, `historicalSnapshotsSurviveLaterFarmAndParcelRenames` |
| Room persistence | IMPLEMENTED | schema v3 + `MIGRATION_2_3` + `RoomMigrationTest` |
| App restart without data loss | IMPLEMENTED | `OfflineFirstCampaignRepositoryTest`, E2E `recreate()` |
| Relationship integrity | IMPLEMENTED | `parcelOutsideTheFarmIsRejectedWithoutPersistingTheCampaign` |
| Outbox, no duplicates | IMPLEMENTED | `repeatedMutationsNeverDuplicateTheCampaignOutboxIntent` |
| Loading / empty / success / error states | IMPLEMENTED | `CampaignViewModels.kt`, `CampaignScreensTest` |
| Real navigation Finca → Parcela → Campaña | IMPLEMENTED | `AppNavigation.kt` `CampaignDetailRoute` |
| Unit tests | PENDING RUN | count: PENDING |
| Instrumented tests | PENDING RUN | count: PENDING |
| Airplane-mode repository run | PENDING RUN | count: PENDING |
| E2E Finca → Parcela → Campaña | PENDING RUN | PENDING |
| Crash buffer | PENDING RUN | PENDING |
| DEV APK | PENDING RUN | PENDING |

## Evidence gaps still pending CI

Nothing below has been executed. The review environment has no Android SDK, no Gradle
distribution and no emulator, so none of it could be produced locally.

| Check | Command / workflow | Result | Run / artifact |
| --- | --- | --- | --- |
| Lint | `:app:lintDevDebug` | PENDING | PENDING |
| Unit tests | `:app:testDevDebugUnitTest` | PENDING | PENDING |
| Instrumented compilation | `:app:assembleDevDebugAndroidTest` | PENDING | PENDING |
| Debug builds | `assembleDevDebug assembleStagingDebug assembleProductionDebug` | PENDING | PENDING |
| Full API 35 instrumentation | `gate3-emulator-evidence.yml` | PENDING | PENDING |
| Repository tests in airplane mode, Wi-Fi off | `offline-room-instrumentation.txt` | PENDING | PENDING |
| Emulator crash buffer | `gate3-emulator-evidence` artifact | PENDING | PENDING |
| Installable DEV APK | `magina-olivo-dev-debug` artifact | PENDING | PENDING |
| DEV APK bytes / sha256 | manual verification | PENDING | PENDING |

Baseline to beat, from the Parcel slice (run `35506482946`): 30 unit tests,
42 instrumented tests, 7 airplane-mode repository tests, empty crash buffer.

## Owner decisions applied (2026-09-21)

**A — Close date.** RC1 adds no date picker. The close action uses the device date and
the repository keeps the `endDate >= startDate` validation. The combined E2E therefore
uses a Campaign whose start date already allows closing on the day the suite runs.

**B — Lifecycle.** Strictly linear `PREPARATION → ACTIVE → HARVEST → CLOSED`.
`close()` was narrowed to accept `HARVEST` only; `ACTIVE → CLOSED` is now rejected as
an illegal transition. `reopen` stays `CLOSED → HARVEST`, explicit, confirmed and
audited, clearing the end date. The single current-Campaign rule per Farm still holds.
No `previousStatus` is persisted.

**C — Archived Campaigns.** RC1 ships no restore. `archivePreparation` applies only to
`PREPARATION`; `ACTIVE`, `HARVEST` and `CLOSED` stay protected. An archived draft stays
soft-deleted, disappears from active reads, can never be mutated again, and a second
archive is idempotent. The `archived_campaign` guard is approved.

## Open risks still pending

- No CI, emulator, airplane-mode or APK evidence exists yet (see the table above).
- Nothing in this slice has been compiled or executed; the review environment has no
  Android SDK, no Gradle distribution and no emulator.

```text
CAMPAIGNS SLICE = PENDING VALIDATION
GATE 6 = FAIL (IN PROGRESS)
```
