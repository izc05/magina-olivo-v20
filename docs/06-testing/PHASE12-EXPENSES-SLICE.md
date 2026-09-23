# Phase 12 — Expenses, purchases, organizations + generic OCR

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-23
**Base commit:** `e42754ac` (`main`, Phase 11 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Validated commit:** `f0725b2a` (code `51130e08` + the compiler-exported `6.json`)
**Plan:** `docs/07-plans/PHASE12-EXPENSES-ORGANIZATIONS-OCR.md`

> Every CI, emulator and artifact figure below is copied from a real run.

## Gate 12

| Clause | Proof |
| --- | --- |
| No monetary double counting | `ExpenseLedgerContractTest`: `aPostedExpenseIsCountedOnceWhereverItIsSeen`, `anActivityCostIsOneLinkedExpenseAndNeverASecondNumber`, `anExtraExpenseOnTheSameActivityIsCountedOnceNextToItsCost`, `purchaseLinesDescribeTheExpenseAndNeverAddMoney`, `aDeletedExpenseLeavesTheTotalsAndQueuesOneTombstone`; JVM `MoneyTest.onlyPostedMoneyIsSummed` |
| Organization reuse works across contexts | `anOrganizationWithTwoRolesIsOneRowReusedAcrossContexts` |
| OCR cannot auto-post money or silently confirm values | `aReviewedDocumentBecomesADraftThatIsNeverCountedUntilPosted`, `aConfirmedDocumentCannotCreateASecondExpense`, `aDraftWithoutAnAmountCannotBePosted`, `aFailedReadingKeepsTheDocumentAndCanBeRetried`, `aDiscardedDocumentLeavesNoMoneyAndNoFile` |

Also: `anExpenseOnAFarmWithARunningCampaignCountsInThatCampaign`,
`relationsOutsideTheFarmAreRejectedWithoutWritingMoney`, `theLedgerSurvivesARestart`,
`RoomMigrationTest.migration5To6KeepsExistingExpensesAsPostedMoneyAndAddsTheLedgerTables`.

JVM: `MoneyTest`, `PurchaseDocumentParserTest`, `AttachmentKindTest` were compiled and
run locally with the Kotlin 2.2.20 compiler before any CI run — 13/13 passed.
`ExpenseFormTest` depends on Android classes and runs in CI only.

## Evidence

| Check | Command / workflow | Result | Run |
| --- | --- | --- | --- |
| Lint, unit tests, three debug builds, instrumented compilation | `foundation` | PASS | [Android CI #346](https://github.com/izc05/magina-olivo-v20/actions/runs/35853828444) on `f0725b2a` |
| Full API 35 instrumentation | `gate3-emulator` | PASS — `instrumentation_rc=0` | Android CI #346, job `107157527615` |
| Room 5→6 migration | `RoomMigrationTest` (5 tests) | PASS on `f0725b2a` | Android CI #346 |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | PASS — `offline_room_instrumentation_rc=0` | Android CI #346 |
| Emulator crash buffer | `gate3-emulator-evidence` | EMPTY — `0 evidence/crash.txt` | Android CI #346, artifact `10747120662` |
| Suite size | same code on `51130e08`, before `6.json` existed | 126 instrumented tests, 125 passing — the only failure was the migration test's missing `6.json`; 66/66 in airplane mode, including `ExpenseLedgerContractTest` 14/14 | PR run `35853074416` |
| Hand-written migration vs compiler export | script comparing `MIGRATION_5_6` with `6.json` | all 6 tables and 13 indices identical | `6.json` identityHash `c03985bd413e80dbb09ae77a5537b72b` |

### What the first runs caught

1. Two assertions compared a `Long?` with an `Int` literal; the behaviour was right, the
   test was wrong (`51130e08`).
2. The first-time export of `6.json` was left untracked and blocked the workflow's own
   schema commit in one of the two runs; the other run published it (`f0725b2a`). The
   workflow fix could not be pushed from this environment (no workflow permission) and
   is noted for the owner.
3. The independent emulator run on `f0725b2a` lost a race in `AppNavigationTest`: the
   "Añadir finca" button is composed while the list is still loading, disabled, and the
   test clicked it at once. The helper now waits, bounded, for the control to be enabled.

## Known gaps

- Real OCR quality on printed invoices must be checked on a device with real documents.
- Camera capture of a document is verified by hand only, as in Phase 11.
