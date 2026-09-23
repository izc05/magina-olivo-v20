# Phase 13 — Harvest

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-23
**Base commit:** `2fbeb935` (`main`, Phase 12 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Validated commit:** `b8fccb38` (code `d703f8fe` + the compiler-exported `7.json`)
**Plan:** `docs/07-plans/PHASE13-HARVEST.md`

> Every CI, emulator and artifact figure below is copied from a real run.

## Gate 13

| Clause | Proof |
| --- | --- |
| Totals remain truthful | `HarvestContractTest.campaignTotalsStayTruthfulAcrossHarvests` (`total = Σ exact + unallocated` over three harvests with exact, unknown and partial splits), `deletingAHarvestRemovesItsKilosAndQueuesOneTombstone`, `aHarvestSurvivesARestart`; JVM `HarvestRulesTest.theSummaryNeverAttributesUnallocatedKilosToAParcel` |
| No parcel split is fabricated | `anUnknownSplitStoresOnlyTheTotalAndNoParcelKilos`, `anExactSplitMustReconcileWithTheTotalToTheGram`, `aPartialSplitKeepsTheRestUnallocatedAndNeverAssignsIt`; JVM `HarvestFormTest.theDefaultForSeveralParcelsIsTheUnknownSplit` |

Also: `aHarvestAndItsParcelsAreOneAggregateWithOneIntent` (D6), `originParcelsMustBelongToTheCampaign`,
`onlyFarmsWithARunningCampaignAreOfferedAndCanRecordHarvest`,
`aFutureDateOrADateBeforeTheCampaignIsRefused`, `aClosedCampaignsHarvestIsHistoryAndCannotChange`,
`RoomMigrationTest.migration6To7KeepsExistingHarvestsAndAddsTheirOriginParcels`.

JVM: `WeightTest`, `HarvestRulesTest`, `HarvestFormTest` were compiled and run locally with
the Kotlin 2.2.20 compiler before any CI run — 15/15 passed.

## Evidence

| Check | Workflow | Result | Run |
| --- | --- | --- | --- |
| Lint, unit tests, three debug builds, instrumented compilation | `foundation` | PASS | [Android CI #352](https://github.com/izc05/magina-olivo-v20/actions/runs/35858239514) on `b8fccb38` (dispatch) |
| Full API 35 instrumentation | `gate3-emulator` | PASS | Android CI #352, job `107171828079` |
| Emulator crash buffer | `gate3-emulator` | EMPTY — `0 evidence/crash.txt` | Android CI #352, artifact `10748771064` |
| Independent emulator run | `gate3-evidence` | PASS | [Gate 3 Android Emulator Evidence #67](https://github.com/izc05/magina-olivo-v20/actions/runs/35858242303) on `b8fccb38` |
| Suite size | same code on `d703f8fe`, before `7.json` existed | 138 instrumented tests, 137 passing — the only failure was the migration test's missing `7.json`; `HarvestContractTest` 11/11; 77/77 in airplane mode, including `HarvestContractTest` 11/11 | PR runs `35857492551`, `35857492560` |
| Hand-written migration vs compiler export | comparison of `MIGRATION_6_7` with `7.json` | `harvest_parcels` table and its 3 indices identical; the 3 new `harvests` columns nullable as written | `7.json` identityHash `346a62532c2651d88b7e0813eba77b4d` |

The first `foundation` run published `7.json` itself this time (`b8fccb38`); the runs that
commit triggered stayed in `action_required`, so both workflows were dispatched on the branch.

## Known gaps

- The harvest form is verified by JVM tests and the repository contract; there is no
  Compose UI test of S71 yet.
- Real-device check of the harvest flow is still to be recorded.
