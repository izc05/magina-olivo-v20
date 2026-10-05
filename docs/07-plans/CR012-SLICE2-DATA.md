# Task 2A — CR-012 Slice 2 persistence and financial writers

Read AGENTS.md, CURRENT-STATE, docs/02-domain/CR012-LABOUR-COST-CONTRACT.md
and Issue #309 complete (gh issue view) before editing. Android native only.
Implement domain/data + instrumented tests; do not edit feature UI, navigation or
machinery code beyond keeping compilable shared DayCostLedger. No subagents.

## Interface contract for the later UI task

- Extend CrewDraft at end with appliedRate: LabourRateSnapshot? = null and
  initialPayments: List<LabourPayment> = emptyList(). Extend LabourChange at end
  with appliedRate: LabourRateSnapshot? = null; null means preserve existing
  snapshot for matching basis, not silently replace an agreed rate. Defaults
  keep call sites compiling. Explicit UI input records the agreed snapshot.
- Add to LabourRepository default methods so existing fakes compile:
  observePayments(campaignId): Flow<List<LabourPayment>>;
  recordPayment(payment): AppResult<UUID>;
  removePayment(paymentId): AppResult<Unit> (soft-delete to correct mistake).
  Optional method observeSettlement data only if truly necessary; UI can use
  observeForCampaign + expenses + payments and domain allocation.
- Payment state is computed; no mutable paidAmount on Worker or Expense.
- Add shared domain helper for campaign allocations if necessary rather than
  duplicating the same financial calculation in UI and data.

## Persistence/writers

Room v21 migration additive after v20: nullable historical applied price/currency/
price date/basis on harvest_labour, and labour_payments table with UUID, workspace,
worker, campaign, paymentDate, positive amount, currency, optional note,
LocalMetadata and foreign keys/indexes. Add DAO and LABOUR_PAYMENT outbox identity.
Schema exported by compiler only; no handwritten schema JSON. Preserve legacy
anonymous/HALF_DAY/null snapshots without inventing prices or debt.

recordCrew: person mandatory, only FULL_DAY/HOURS for new recollection; validate
workspace and campaign. Capture usual farm rate only for a NEW line if draft has
none; an explicit appliedRate overrides. Missing price remains unknown and should
not silently make a known zero cost. Save snapshots, sync single Expense and
initialPayments in one transaction. All new payments verified against confirmed
reconciled generated balance, with campaign/workspace/currency scoped correctly.
New recordCount is rejected in campaign. Preserve historical counts read/edit
without fabricating identity; HALF_DAY allowed only on existing historical entry.

DayCostLedger labour uses checked sum of LabourPricing.amountMinor individually,
not DayCostCalculator aggregated minutes. Currency comes from snapshots; habitual
currency changes do not rewrite saved values. Cost zero is explicitly known.
If any live day line lacks snapshot, preserve old ledger and make new priced
write to that mixed day fail with actionable validation until the user explicitly
confirms missing prices. Do not replace ledger by partial known subtotal.
Changing preferences never backfills history. Explicit edits can confirm missing
prices on legacy lines, with deterministic complete allocation when all known.

Common debt guard after candidate writes/before commit across whole campaign:
no affected worker/currency has payments greater than new confirmed generated.
Cover update/remove labour, Harvest deletion, direct Expense mutations and manual
cost collisions/linking/preferCalculated. Throw controlled exception for rollback,
catch outside withTransaction (returning Failure inside transaction is not rollback).
Read-only legacy unattributable cost remains campaign expense and never person debt.
Payment soft-delete corrects mistaken payments; no payments deleted with Harvest.

recordPayment allows CLOSED as well as active; never sync Expense or reopen campaign.
Validate latest pending inside same transaction; duplicate UUID exact matching live
content is successful retry, mismatch conflict; do not resurrect removed movement.
Reject deleted campaign and wrong workspace/context. An archived (soft-deleted) worker
may still be paid up to its outstanding pending (#481): archiving blocks new jornales,
not debt already accrued; without pending the payment is rejected. Positive amount only,
no advances or currency conversion. Cost edit below already paid blocked atomically.

## Required tests (real Room, TDD)

- v20→21 migration null snapshots/payments empty, schema and legacy preserved.
- three named persons ×60 =180 in one Expense; fractional-hour per-line rounding
  differs from aggregate and reconciles exactly (e.g. two1min at1EUR/h =>4 cents,
  not3); snapshot agreement/default freezing60→65, current currency changes.
- fourdays60=>240, initial partial100 then80=>180/pending60; exact60 after close
  no Expense/campaign/cost/kg changes. Reopen DB retains all movement UUIDs/saldo.
- concurrent payments, overpay, duplicate retry UUID, mismatchedUUID, invalidcontext.
- negative/zero payment, explicit zero price, unknown price not zero.
- edit/remove labour/Harvest/Expense/manualcollision cannot reduce below paid;
  rollback data and outbox intents; correcting payment soft-delete enables edit.
- anonymous legacy preserved and newcount rejected; no invented prices on migration.
- adapt existing instrumented tests whose old expectations contradict new CR012
  (anonymous creation, future rates recalculation) to current contract, preserving
  their meaningful ledger/rollback coverage. Avoid making tests pass by removing
  assertions; seed legacy entities directly where verifying compatibility.

## Validation and report

JAVA_HOME C:\Program Files\Android\Android Studio\jbr,
SDK C:\Users\ISICIO\AppData\Local\Android\Sdk,
Gradle C:\Users\ISICIO\.gradle\manual\gradle-9.4.1\bin\gradle.bat.
Emulator emulator-5580 dedicated CR012 AVD available after boot; use adb install
and am instrument focused classes for RED/GREEN evidence. Focused JVM tests then
full testDevDebugUnitTest + assembleDevDebugAndroidTest before report. Do not push
or merge. Commit data/domain/test changes only; root owns plan/docs and UI.
Write full report (files, RED/GREEN commands and results, concerns) to path provided
by root; return only status, commitSHAs, brief test summary and unresolved questions.
