# Gate 6 — Campaigns slice evidence

**Slice decision:** PASS
**Gate 6 overall:** PASS — Farms, Parcels and Campaigns all validated
**Reviewed:** 2026-09-22
**Base commit:** `5f1c6f9a` (`feat/android-parcels`, Parcel slice PASS)
**Validated code commit:** `164aaa48b4a9b87a04d7523a5985c5a0cab4d5ca`
**Stacked PR:** [#205](https://github.com/izc05/magina-olivo-v20/pull/205) — open, draft, base `main`, not merged

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
live Catastro lookup and Campaign restore.

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

## Lifecycle — final and canonical

```text
PREPARATION → ACTIVE → HARVEST → CLOSED
                          ▲          │
                          └──────────┘
                        reopen (explicit, confirmed, audited)
```

Strictly linear. `ACTIVE → CLOSED` is rejected as an illegal transition: closing is
legal only from `HARVEST`. The single backwards edge is `CLOSED → HARVEST` via
`reopen`. No `previousStatus` column is persisted; the linear contract makes it
unnecessary.

| Rule | Behavior |
| --- | --- |
| Parcel selection editable | only in `PREPARATION` |
| Activation with zero Parcels | rejected; status, version and outbox unchanged |
| Second current Campaign per Farm | rejected atomically (`active_campaign_exists`), for `ACTIVE` and for `HARVEST` |
| Activation | atomically freezes Farm name, Parcel name, managed area, cadastral reference and geometry |
| Close | legal only from `HARVEST`; `ACTIVE → CLOSED` is an illegal transition |
| End date before start date | rejected (`endDate` / `before_start`) |
| Cross-Farm Parcel selection | rejected; the whole create rolls back |

### Archive semantics

- `archivePreparation` applies only to `PREPARATION`.
- `ACTIVE`, `HARVEST` and `CLOSED` are protected (`protected_campaign`).
- An archived draft is soft-deleted: hidden from active reads, and no mutation may
  resurrect it (`archived_campaign`).
- A repeated archive is idempotent, mirroring the Farm aggregate contract.
- **RC1 ships no Campaign restore.**

### Reopen semantics

- Legal only from `CLOSED`, to `HARVEST`.
- Explicit and confirmed through the confirmation sheet.
- Audited: advances the aggregate version and queues a deterministic outbox intent.
- Clears `endDate`.
- Rejected while another Campaign already holds the Farm's single current slot.
- Frozen activation snapshots survive the reopen untouched.

### Date semantics

- The close action uses the device date; **RC1 adds no date picker**.
- The repository rejects `endDate < startDate`.
- Known limitation: a Campaign whose start date is in the future cannot be closed on
  the device's current date. Accepted for RC1 by owner decision.

## Defect found and fixed during this slice

**Soft-deleted Campaigns remained fully mutable.**

`CampaignDao.findById` returns rows regardless of `deleted_at`, and
`OfflineFirstCampaignRepository.mutate` did not re-check the flag. An archived draft
kept `status = PREPARATION`, so `activate`, `updatePreparation`, `markHarvest`,
`close` and `reopen` all still succeeded on it. An archived draft could therefore be
resurrected and could take the Farm's single current-Campaign slot.

`OfflineFirstFarmRepository` already guarded `metadata.deletedAt` on every mutation;
the Campaign aggregate did not. Fix applied, matching the existing Farm pattern:

- `mutate` rejects soft-deleted aggregates with `AppError.Conflict("archived_campaign")`;
- `archivePreparation` opts out of that guard and returns `Success` when the Campaign
  is already archived.

Regression covered by `archivedPreparationIsRemovedFromTheFarmAndCannotBeResurrected`.

## Tests added

`app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/CampaignLifecycleContractTest.kt`
— **13 instrumented contract tests**. `OfflineFirstCampaignRepositoryTest` is unchanged
and keeps the original end-to-end activation and restart proof.

| Test | Contract clause |
| --- | --- |
| `createEnqueuesExactlyOneDeterministicCreateIntent` | create queues exactly one CREATE intent |
| `repeatedMutationsNeverDuplicateTheCampaignOutboxIntent` | six mutations still collapse to one pending intent; snapshots queue nothing |
| `activationWithoutParcelsFailsWithoutMutatingStatusOrOutbox` | zero-Parcel activation leaves status, version and outbox untouched |
| `secondCurrentCampaignForTheSameFarmIsRejectedAtomically` | one current Campaign per Farm, for ACTIVE and for HARVEST |
| `parcelOutsideTheFarmIsRejectedWithoutPersistingTheCampaign` | cross-Farm Parcel selection rolls the whole create back |
| `illegalTransitionsLeaveThePersistedStateUntouched` | every illegal transition from each of the four states is a no-op |
| `lifecycleIsStrictlyLinearActiveCannotCloseHarvestCanAndClosedCanReopen` | `ACTIVE → CLOSED` rejected, `HARVEST → CLOSED` allowed, `CLOSED → HARVEST` allowed |
| `endDateBeforeStartDateIsRejectedWithoutClosingTheCampaign` | invalid end date does not close or bump the version |
| `closeThenReopenIsExplicitAuditedAndClearsTheEndDate` | full close → reopen → close cycle; version advances, snapshots survive |
| `reopenIsRejectedWhileAnotherCurrentCampaignHoldsTheFarmSlot` | reopen respects the single-current-Campaign rule |
| `archiveIsRejectedForActiveHarvestAndClosedCampaigns` | protected states cannot be archived |
| `archivedPreparationIsRemovedFromTheFarmAndCannotBeResurrected` | regression for the defect above; double archive is idempotent |
| `historicalSnapshotsSurviveLaterFarmAndParcelRenames` | frozen snapshot values outlive later Farm/Parcel edits |

## End-to-end flow

`AppNavigationTest.farmParcelCampaignLifecyclePersistsAcrossRecreation` proves the
complete recorrido on a real emulator:

```text
onboarding → Mi Olivar → create Farm → open Farm → create Parcel
→ create Campaign selecting that Parcel → ACTIVE → HARVEST → CLOSED
→ process recreate() → history → frozen snapshot + truthful empty summaries
→ reopen → HARVEST → CLOSED
```

Stabilisation applied during this slice, test-only:

- every asynchronous transition waits for observable UI state; `UI_TIMEOUT_MS = 15_000L`
  and there is no `Thread.sleep` anywhere in the suite;
- lifecycle actions are viewport-safe: `performScrollTo()` followed by
  `assertIsDisplayed()`, `assertIsEnabled()` and `assertHasClickAction()`, because the
  detail screen is a `verticalScroll` Column where a composed button can sit outside the
  viewport and swallow the click silently;
- the confirmation sheet is awaited in two phases — first the `Confirmar cambio` title,
  then `confirm-campaign-action` — so a sheet that never opened is distinguishable from
  a sheet that opened without its button;
- a timeout fails with a named semantics diagnostic instead of a bare timeout;
- the empty-state assertion is deterministic: the three metric cards `Kg recogidos`,
  `Rendimiento` and `Gastos` are each scrolled to and asserted, and
  `onAllNodesWithText("Sin datos").assertCountEquals(3)` proves exactly three truthful
  empty summaries.

## Automated evidence

All evidence below comes from the CI runs on validated commit `164aaa48`.

| Check | Result | Evidence |
| --- | --- | --- |
| Lint, unit tests, instrumented-test compilation, DEV/STAGING/PRODUCTION debug builds (`foundation`) | PASS | [Android CI run 35686302694](https://github.com/izc05/magina-olivo-v20/actions/runs/35686302694) |
| Full API 35 instrumentation (`gate3-emulator`) | PASS — 61/61 | Android CI #315, run `35686302694` |
| Independent emulator evidence (`gate3-evidence`) | PASS — 61/61 | [Gate 3 Android Emulator Evidence #36, run 35686302700](https://github.com/izc05/magina-olivo-v20/actions/runs/35686302700) |
| `CampaignLifecycleContractTest` | PASS — 13/13 | Both emulator runs |
| Offline Room Farm + Campaign repository tests under airplane mode | PASS — 9/9 | Both emulator runs |
| Emulator crash buffer | PASS — 0 bytes | Both emulator runs |
| Installable DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10676852700`) |

### Airplane-mode proof

The evidence script does not simulate offline behavior. It enables real airplane mode
on the emulator and verifies the system setting before running the repository tests:

```text
adb shell cmd connectivity airplane-mode enable
adb shell settings get global airplane_mode_on   → 1
  → OfflineFirstFarmRepositoryTest
  → OfflineFirstCampaignRepositoryTest
adb shell cmd connectivity airplane-mode disable
```

Result: 9/9 PASS with radios off.

### Artifacts

| Artifact | ID | Size | SHA-256 |
| --- | --- | --- | --- |
| `magina-olivo-dev-debug` (DEV APK) | `10676852700` | 13,146,341 bytes | `118156bdbab98197224aa7a0c3d12fcdad6857ecf13b2cffaf3bbb5f620948ff` |
| `gate3-emulator-evidence` (Android CI) | `10676937872` | not recorded | `7fade11ed541cc19c83e794328b13cb8c66b8a3e5dc7c0cf1651da00952798a4` |
| `magina-olivo-gate3-emulator-evidence` (independent Gate 3) | `10677092622` | not recorded | `eb894726508eaa8b2d56c50d1c58f2371e692c5a9f216cbb5aa82735a4085ebd` |

Digests are of the downloaded artifact ZIPs.

## Acceptance matrix

| Requirement | Status | Evidence |
| --- | --- | --- |
| Create campaign | PASS | `createEnqueuesExactlyOneDeterministicCreateIntent`, E2E |
| Edit campaign (preparation only) | PASS | `preparationSelectionCanChangeButCannotAfterActivation` |
| Select active campaign | PASS | `secondCurrentCampaignForTheSameFarmIsRejectedAtomically` |
| Close campaign (HARVEST only) | PASS | `lifecycleIsStrictlyLinearActiveCannotCloseHarvestCanAndClosedCanReopen` |
| Reopen campaign | PASS | `closeThenReopenIsExplicitAuditedAndClearsTheEndDate`, E2E reopen path |
| History and summaries | PASS | `historicalSnapshotsSurviveLaterFarmAndParcelRenames`, E2E `Sin datos` ×3 |
| Room persistence | PASS | schema v3 + `MIGRATION_2_3` + `RoomMigrationTest` |
| App restart without data loss | PASS | `OfflineFirstCampaignRepositoryTest`, E2E `recreate()` |
| Relationship integrity | PASS | `parcelOutsideTheFarmIsRejectedWithoutPersistingTheCampaign` |
| Outbox, no duplicates | PASS | `repeatedMutationsNeverDuplicateTheCampaignOutboxIntent` |
| Loading / empty / success / error states | PASS | `CampaignViewModelTest`, `CampaignScreensTest` |
| Real navigation Finca → Parcela → Campaña | PASS | `AppNavigationTest`, both emulator runs |
| Unit tests | PASS | `foundation`, run `35686302694` |
| Instrumented tests | PASS — 61/61 | Runs `35686302694` and `35686302700` |
| Airplane-mode repository run | PASS — 9/9 | Both emulator runs |
| E2E Finca → Parcela → Campaña | PASS | Both emulator runs |
| Crash buffer | PASS — 0 bytes | Both emulator runs |
| DEV APK | PASS | Artifact `10676852700` |

## Data not recorded

The following were not supplied by the CI summary and are deliberately left unstated
rather than estimated:

- the separate unit-test count for this run (the Parcel slice baseline was 30);
- the uncompressed byte sizes of the two evidence artifacts.

## Gate 6 result

| Slice | Decision | Validated commit |
| --- | --- | --- |
| Farms | PASS | `a2d2d475` |
| Parcels | PASS | `ee89b9f7` |
| Campaigns | PASS | `164aaa48` |
| Combined Farm → Parcel → Campaign flow | PASS | `164aaa48` |

```text
CAMPAIGNS SLICE = PASS
GATE 6 = PASS
```
