# Phase 14 — Deliveries, weight-ticket OCR and later yield

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-23
**Base commit:** `c75cad56` (`main`, Phase 13 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Validated commit:** `a90068e4` (code `02772001` + the compiler-exported `8.json`)
**Plan:** `docs/07-plans/PHASE14-DELIVERIES-TICKET-OCR-YIELD.md`

> Every CI, emulator and artifact figure below is copied from a real run.

## Gate 14

| Clause | Proof |
| --- | --- |
| The original delivery survives OCR/yield updates unchanged | `DeliveryContractTest.aLaterYieldNeverChangesTheOriginalDelivery` (the delivery row is byte-for-byte equal before and after recording, correcting and removing the analysis; its version and single `CREATE` intent unchanged), `confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading` (a second confirmation, a new reading and a discard are all refused and the delivery row stays equal), `yieldCanArriveAfterTheCampaignClosesWhileTheDeliveryStaysHistory` |
| OCR cannot auto-confirm | `readingATicketNeverRecordsADelivery` (an `EXTRACTED` ticket writes no delivery and cannot become an expense), `anIncompleteReadingNeedsReviewAndAnInvalidReviewWritesNothing`, `confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading` (the delivery carries the farmer's corrected net, not the engine's); JVM `DeliveryTicketParserTest.aMissingNetIsNeverComputedFromGrossAndTare` |
| Weighted metrics are correct | `yieldIsWeightedByDeliveredKilosWithItsCoverage` ((1000 × 20 % + 3000 × 24 %) / 4000 = 23 %, coverage 66 %), `aDeletedDeliveryLeavesTheTotalsWithItsYield`; JVM `DeliveryRulesTest.yieldIsWeightedByDeliveredKilosAndShowsItsCoverage`, `withoutAnyAnalysisThereIsNoYieldNotAZeroYield` |

Also: `aDeliveryIsItsOwnRecordAndNeedNotMatchTheHarvest`, `anExactSplitMustReconcileWithTheDeliveredKilos`,
`aSavedCooperativeIsCopiedByNameOntoTheDelivery`, `deliveriesAndYieldSurviveARestart`,
`RoomMigrationTest.migration7To8AddsEmptyDeliveryTablesAndKeepsHarvests`.

JVM: `DeliveryRulesTest`, `DeliveryTicketParserTest`, `DeliveryFormTest` and the Harvest tests
re-run on the shared `ParcelSplit` were compiled and run locally with the Kotlin 2.2.20
compiler before any CI run — 29/29 and 4/4 passed.

## Evidence

| Check | Workflow | Result | Run |
| --- | --- | --- | --- |
| Lint, unit tests, three debug builds, instrumented compilation | `foundation` | PASS | [Android CI #358](https://github.com/izc05/magina-olivo-v20/actions/runs/35861745903) on `a90068e4` (dispatch) |
| Full API 35 instrumentation | `gate3-emulator` | PASS | Android CI #358, job `107183385623`, artifact `10750408489` |
| Independent emulator run | `gate3-evidence` | PASS — `instrumentation_rc=0`, `offline_room_instrumentation_rc=0` | [Gate 3 Android Emulator Evidence #72](https://github.com/izc05/magina-olivo-v20/actions/runs/35861748914), artifact `10750996585` |
| Emulator crash buffer | `gate3-evidence` | EMPTY — `0 evidence/crash.txt` | run `35861748914` |
| Installable DEV APK | `magina-olivo-dev-debug`, artifact `10751205861` | 34 378 876 bytes (zip) | `sha256:89e265250309e6be253939c5b3f5d798a1ed8a42e680a05f97f3e288e11ef602` |
| Suite size | same code on `02772001`, before `8.json` existed | 150 instrumented tests, 149 passing — the only failure was the migration test's missing `8.json`; 88/88 in airplane mode, including `DeliveryContractTest` 11/11 | run `35861149038` |
| Hand-written migration vs compiler export | comparison of `MIGRATION_7_8` with `8.json` | 3 tables and 7 indices identical | `8.json` identityHash `f6a5b7c509fe79db10e965828d4c9097` |

### What the first runs caught

1. `anExactSplitMustReconcileWithTheDeliveredKilos` ended in an expression returning a
   UUID, so JUnit refused the whole class (`initializationError`) on `ffc7c2c5`; it now
   ends in an assertion (`02772001`).
2. The first run published `8.json` itself (`a90068e4`); the next `foundation` run's own
   schema commit then failed because the file already existed. Both workflows were
   dispatched on the branch head.

## Known gaps

- Real OCR quality on printed weight tickets must be checked on a device with real tickets.
- The delivery and ticket-review forms are covered by JVM tests and the repository
  contract; there is no Compose UI test of S81 yet.
