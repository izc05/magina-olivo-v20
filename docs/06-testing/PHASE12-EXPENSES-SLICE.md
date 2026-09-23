# Phase 12 — Expenses, purchases, organizations + generic OCR

**Phase decision:** PENDING — implementation complete, CI evidence not yet collected
**Reviewed:** 2026-09-23
**Base commit:** `e42754ac` (`main`, Phase 11 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Plan:** `docs/07-plans/PHASE12-EXPENSES-ORGANIZATIONS-OCR.md`

> No CI, emulator or artifact figure appears below until a real run produces it.

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
| Lint, unit tests, builds | `foundation` | pending | — |
| Full API 35 instrumentation | `gate3-emulator` | pending | — |
| Room 5→6 migration | `RoomMigrationTest` | pending (needs the exported `6.json`) | — |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | pending | — |
| Independent emulator run | `Gate 3 Android Emulator Evidence` | pending | — |

## Known gaps

- Real OCR quality on printed invoices must be checked on a device with real documents.
- Camera capture of a document is verified by hand only, as in Phase 11.
