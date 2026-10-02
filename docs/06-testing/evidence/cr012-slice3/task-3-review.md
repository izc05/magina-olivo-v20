# CR-012 Task 3 independent review

- **Spec verdict: FAIL**
- **Quality verdict: NEEDS_FIXES**
- **Blocking findings: 3** (1 P1, 2 P2).
- Reviewed base `fde9c6632175e2ac0a48d07e25876ebe6de19c85` → head `17f8d76b234db166aa462c0c45f9bd64d73ce912`, branch `feat/cr012-slice3-maquinaria`.
- Review date: 2026-10-02. No production edits, tests rerun, push, merge, or subagents. This report is the sole review output.

## Findings, in priority order

### F1 — P1: unresolved legacy equipment bypasses same-kind manual collision handling

**Changed location:** `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/DayCostLedger.kt:68–80`, especially the `equipment.all { it.appliedPrice != null }` condition at line 69.

A migrated/open day can legitimately have unknown equipment snapshots and a historical POSTED DAY_EQUIPMENT Expense. While any snapshot remains unknown, equipment synchronization skips both recalculation **and** collision handling. Posting/linking a replacing machinery rental succeeds and leaves both expenses POSTED. For example, the preserved 9,000 JPY historical machinery ledger plus a newly posted 4,000 JPY rental produces 13,000 JPY of campaign/day cost for the same machinery use. This directly violates the binding manual-collision/no-double-money contract.

Concrete checked entry points:

- `OfflineFirstExpenseRepository.kt:64–69`: create writes a POSTED MANUAL expense then calls `costs.sync` inside its Room transaction.
- `OfflineFirstExpenseRepository.kt:84–92`: post changes a draft manual expense to POSTED then calls the same sync.
- `OfflineFirstExpenseRepository.kt:73–81`: update can move/reclassify a posted rental onto this day and calls sync.
- `OfflineFirstDayCostRepository.kt:88–120`: linkToDay assigns the day/campaign to an unlinked posted rental then calls sync.
- `DayCostLedger.kt:142–148`: `manual(..., EQUIPMENT)` already distinguishes a replacing rental from additive oil/lubricants, but is never consulted in the incomplete equipment branch.
- `DayCostLedger.kt:60–66`: the analogous existing incomplete-labour branch correctly rejects this collision.

**Required bounded fix:** preserve the historical equipment ledger unchanged and reject the colliding create/post/update/link transaction with actionable `appliedPrice:confirm_missing_prices` until prices are complete. Throw inside the transaction so the manual status/link/row and outbox mutations roll back. Do not silently remove/draft/reprice the unknown historical ledger. Retain the valid additive lubricant path.

**Focused regressions:** unknown equipment + historical POSTED ledger, then replacing manual create, draft→post, and unlinked→link (also update if not covered by a shared guarded path); verify failure, unchanged ledger/id/amount/status, unchanged manual row/status/link where applicable, and unchanged outbox. Include complete-equipment collision/preferCalculated and additive lubricant controls.

The existing machinery collision test at `DayCostContractTest.kt:272–287` covers fully priced equipment only; it does not detect this migrated/partially confirmed path.

### F2 — P2: clearing a new usual-price prefill still posts the hidden usual price

**Changed locations:** `app/src/main/java/com/isivoltpro/maginaolivo/feature/harvests/EquipmentSection.kt:139–143,153–164,226–233` and `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/OfflineFirstEquipmentRepository.kt:93–98`.

Concrete path: open an empty day with usual shaker price EUR 70, select one shaker, and clear the prefilled unit-price field. The sheet emits `EquipmentDraftLine(..., appliedPrice = null)`; the empty field is neither malformed nor a cleared *existing* confirmed snapshot, so Save stays enabled. Its preview says pending and its help text says the cost remains pending. On save, the repository interprets null on a new line as a request for the usual price and silently captures EUR 70, posting a 7,000-minor-unit DAY_EQUIPMENT Expense. The displayed preview/confirmation and persisted cost disagree.

The same mismatch can occur for any blank new line whose repository has a compatible usual price. The existing clearing test at `EquipmentScreenTest.kt:175–190` covers an already confirmed existing line, so it misses a cleared new prefill.

**Required bounded fix:** explicitly distinguish preserving an existing snapshot / accepting a usual price / leaving a price unknown, or reject deliberately clearing a new prefill with an actionable message and disabled Save. A blank/pending preview must not authorize a hidden usual-priced expense. Preserve backward-compatible usual-prefill behavior for new lines and explicit zero.

**Focused regressions:** clear a new usual prefill, assert the chosen contract at the UI and repository integration seam; ensure no hidden posted cost, while an unchanged visible prefill and explicit zero still persist correctly.

### F3 — P2: partial legacy confirmation accepts a Long-overflowing known subtotal

**Changed location:** `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/OfflineFirstEquipmentRepository.kt:108–110`, coupled with the incomplete-price bypass in `DayCostLedger.kt:69–80`.

The repository validates each resolved quantity×unit price but does not checked-add the resolved amounts before writing. The checked addition in `DayCostCalculator.equipment` only runs when **every** equipment price is known. Consequently, with three existing unknown legacy lines, confirming two quantity-one lines at 6,000,000,000,000,000,000 minor units each while leaving the third unknown succeeds: both products fit Long, but their known subtotal does not. Rows/outbox commit instead of the required overflow validation rollback. A historical expense remains unchanged today, so this is not an immediate fabricated ledger total; it nevertheless persists financially unrepresentable confirmed data and bypasses the binding writer/overflow contract.

The sheet performs checked subtotal addition at `EquipmentSection.kt:133–138`, so this invalid input is blocked in that UI. The canonical repository must also reject it for every caller. Current Room sum-overflow coverage at `DayCostContractTest.kt:244–250` uses a completely priced set and therefore reaches the calculator; it does not cover this bypass.

**Required bounded fix:** checked-add all resolved known line totals inside the existing repository transaction before row/outbox writes, without posting that subtotal while unknown lines remain. Return `appliedPrice:overflow` on failure.

**Focused regression:** three existing legacy lines, two oversized partial confirmations, third null; verify Validation overflow and unchanged rows, historical ledger, and outbox.

## Requirements and integration checked

| Binding requirement | Review result |
| --- | --- |
| Slice bounded to machinery simple cost/tests/UI; no payroll/navigation rewrite | PASS: production diff is bounded; labour implementation/payment writers and root navigation are unchanged. |
| Optional immutable unit price/currency/date; draft override at end | PASS: domain models have appended nullable snapshots; snapshot constructors validate nonnegative amounts and ISO minor-unit currencies. |
| Quantity multiplication once, historical unit price retained on quantity-only edit | PASS for valid complete sets: canonical keys resolve existing snapshots; complete-day calculator uses checked multiplication/addition; unchanged sheet values emit no override. Partial-set overflow is F3. |
| Missing != zero; legacy null; no fabricated migration backfill | PASS at migration/entity/mappers and existing legacy replacement. Cleared new blank UI is F2. |
| Existing historical denomination before today's usual currency; mixed/ambiguous context rejected | PASS: repository resolves posted equipment history and saved currencies; UI resolver checks matching campaign/day and confirmed currencies. JPY/EUR and partial confirmation source tests are present. |
| Keep historical ledger while unknown; reject priced append to retained unresolved legacy | PASS for canonical equipment replace: unknown snapshots do not receive new defaults, partial existing confirmations preserve history, priced append is rejected before writes. |
| Unique DAY_EQUIPMENT expense; known zero stays posted | PASS on normal complete sets: update-in-place uses current expense id; zero is retained; replay produces no row/outbox writes. Historical manual collision is F1. |
| preferCalculated rejects missing equipment prices before demoting manual costs | PASS: new preflight guard at DayCostLedger lines 95–97 precedes manual status/outbox mutation. |
| Slice 2 paid-labour invariant still protects collateral mutations | PASS in inspected seams: equipment replace wraps all writes + sync in one Room transaction; sync still ends in LabourFinance.verifyCampaign; preferCalculated/removeFor retain the guard; exception translation occurs after transaction rollback. No payment-created cost path was introduced. |
| Closed campaign keeps history and rejects edits | PASS by source: equipment replace requires ACTIVE/HARVEST at line 78; sync exits before writes for closed campaigns; preferCalculated/link reject closed campaigns; ExpenseLedgerWriter checks the current and target campaign before manual mutations. Sheet edit action uses harvest.editable. |
| Campaign/day financial totals only from POSTED Expense | PASS in changed seam: Harvest detail still supplies state.costs to JornadaCosts; equipment UI adds operational per-line previews, not a second total source. |
| Room additive v21→22, reopen and snapshot mapping | PASS by source/schema: both entity→EquipmentLine mappers preserve all three fields/null; migration adds nullable columns only; generated schema comparison found only harvest_equipment changed. Migration test preserves a v21 legacy row/quantity/version/nulls; Room test reopens and checks persisted unit price. |
| UI errors and missing prices visible; disabled Save for overflow/currency conflicts | PASS for existing test paths; F2 is the blank-new-line mismatch. |

Concrete unchanged-code checks were limited to named integration risks: EquipmentDao/ExpenseDao live-row filtering, ExpenseLedgerWriter campaign/edit/collision inputs, OfflineFirstExpenseRepository create/update/post/delete sync call sites and transactions, LabourFinance.verifyCampaign implementation, Money currency minor-unit parsing/formatting, existing machinery collision/closure tests, and the CI evidence-script exclusion/Phase18 ordering contract. No broader repository audit was performed.

## Evidence inspection and limits

Read the brief, full referenced machinery plan, handoff report, and supplied review package. The package was read once; the large generated schema/current-state output exceeded tool display capacity, so bounded production/test source diffs and a structured v21/v22 schema comparison were used to inspect the actual seams. Verified local HEAD is the requested exact head and `git diff --check fde9c663 17f8d76b` is clean.

Applied the evidence-before-claims principle from the verification skill; tests were deliberately not rerun per the review instruction. These are inspection results, not newly executed test claims:

- Committed `task-3-gradle.log` ends BUILD SUCCESSFUL, 173 tasks, and shows unit-test/lint/DEV/STAGING/PRODUCTION/AndroidTest build tasks. The numerical full JVM count of 297 and initial Room 31/31 are reported by the handoff; the currently retained unit XML was overwritten by the final scoped run and contains 7 calculator + 2 currency-context tests, zero failures/errors. I did not independently reconstruct the earlier aggregate counts.
- `task-3-compose-adb.log` records OK (7 tests). The earlier retained capture-interleaved log is explicitly red (1 failure among 6); the handoff explains the capture isolation and the final seven-test source is present.
- `task-3-androidtest.log` records 392 tests with one failure, Phase18OfflineReopenTest: “Online setup did not persist the official parcel,” and BUILD FAILED. It must remain described as 391/392, not a full native PASS.
- Separate setup/offline logs each record OK (1 test). The ordinary CI script `.github/scripts/gate3-emulator-evidence.sh:341` excludes the four live/ordered classes; `.github/workflows/phase18-map.yml:52,60` runs online setup before offline reopen. The test-order explanation is consistent with this contract. Exact-head CI execution/status belongs to the root and was not independently verified here.
- Root has already visually verified the two emulator PNGs. This review did not repeat the visual check or claim a persisted end-to-end/physical-device journey.
- A real-device gate remains pending and is honestly disclosed in the handoff. That is an acceptance limit, not a production-code finding invented by this review.

## Minor / follow-up coverage notes

The seven EquipmentScreenTest cases do not directly exercise registered-machine price selection, existing quantity-only snapshot editing, historical JPY form confirmation, or a closed-equipment form. Repository/source checks establish their intended behavior, but the full UI acceptance claim is broader than these seven focused cases. Add meaningful seam coverage when addressing the blocking paths; do not rerun broad suites merely to compensate for missing assertions.

Review verdict is tied to the exact head above. Fix all three findings in one bounded wave, update focused evidence, obtain review of the new head, and require the root's exact-head green CI before considering merge.


## Fix round 1 scoped re-review — 2026-10-02

**Superseding verdict at head `d81f6b777cff4d946e707a97bc8906978413a960`: Spec PASS; Quality APPROVED.**

All three original findings are **ADDRESSED**. Remaining blocking findings: **0**. New blocking breakage in this fix diff: **0**. The original findings and evidence above are retained as the audit trail; their initial FAIL/NEEDS_FIXES verdict applies to the previous head only.

Scope: `17f8d76b234db166aa462c0c45f9bd64d73ce912` → `d81f6b777cff4d946e707a97bc8906978413a960`; F1/F2/F3 and new breakage within those fixes only. Read the previous review, task brief/full referenced machinery plan, updated handoff, and the supplied fix review package once. Its combined evidence output exceeded display capacity, so inspected the bounded production/JVM diff and final evidence files directly. No broad review, production changes, new agents, test reruns, push, or merge.

### Per-finding disposition

| Finding | Status | Checked resolution |
| --- | --- | --- |
| F1 — incomplete legacy manual collision | **ADDRESSED** | `DayCostLedger.kt:80–86` adds the incomplete-equipment branch. A historical POSTED DAY_EQUIPMENT plus replacing POSTED manual machinery now throws `appliedPrice:confirm_missing_prices` within the caller's transaction. It leaves the historical ledger untouched. The shared sync path covers create, draft→post, update, and link; the new Room regression exercises all four and checks rejected row/status/link, outbox counts, historical expense equality, and the posted JPY total. Additive lubricant remains accepted. Existing complete-price collision behavior remains in the unchanged complete branch. Expense/day view-model changes expose actionable confirmation instructions. |
| F2 — blank new field silently repriced | **ADDRESSED** | `Equipment.kt:47–50` appends the explicit `captureUsualPriceWhenMissing` flag with backward-compatible true default. `EquipmentSection.kt:139–144` sends false for a blank displayed field; `OfflineFirstEquipmentRepository.kt:93–98` only falls back to usual rates when the flag is true. Explicit snapshot and existing saved snapshot still take precedence. Under the root's stated ruling a new blank field may be saved as unknown; no hidden cost is posted. Compose covers clearing new EUR 70 prefill and the emitted null/false draft; Room covers that draft staying unknown/no expense despite a usual rate. Unchanged visible prefill, old default callers, explicit zero, and existing confirmed-price clearing controls remain covered. |
| F3 — partial known subtotal overflow | **ADDRESSED** | `OfflineFirstEquipmentRepository.kt:108–113` checked-folds all resolved known line products before any equipment row/outbox write, even when another line is unknown. ArithmeticException retains the established `appliedPrice:overflow` translation outside the transaction. The new Room regression uses two 6e18 quantity-one confirmations plus a third null line and verifies validation failure with unchanged rows, ledger, and outbox count. This validation never posts a partial subtotal. |

### New-breakage assessment

No new blocking issue found in the bounded source/test diff. The explicit-unknown flag never clears a saved snapshot or backfills retained legacy rows. The priced-append/historical-currency checks are preserved. The new manual guard uses the existing replacing-versus-additive classification, and the Slice 2 `LabourFinance.verifyCampaign` call still follows successful sync. The fix changes no schema/migration, payment writer, navigation, or later-phase scope. New user messages are limited to the machinery confirmation failure.

The updated historical JPY fixture explicitly sets POSTED, making the guard regression exercise the actual authoritative-history condition. Assertions now sum POSTED JPY expenses rather than implicitly comparing an EUR-default summary, avoiding a currency-fixture false result.

### Verified evidence and remaining gates

Inspected `docs/06-testing/evidence/cr012-slice3/task-3-fix-final-green.log`: 24 instrumented tests, zero failures, BUILD SUCCESSFUL in 1m 2s, 83 tasks; DEV and AndroidTest assembly tasks succeeded/up-to-date. Parsed the committed final XMLs:

- `task-3-fix-jvm-calculator.xml`: 7 tests; currency XML: 2; machinery-error XML: 1 — **10/10**, zero failures/errors/skips.
- `task-3-fix-instrumented.xml`: root 24 tests, zero failures/errors/skips; DayCostContractTest **15/15** and EquipmentScreenTest **9/9**, including all new regressions and retained focused controls.

These are reviewed fresh implementation-run artifacts, not tests newly executed by the reviewer. `git rev-parse HEAD` matched the requested head; `git diff --check 17f8d76b d81f6b77` was clean; tracked working tree was clean before appending this report. Earlier logs named “green”/“focused” remain red and are accurately disclosed by the updated handoff; they are not the approval evidence.

The original full-suite/physical-device/coverage limits remain as stated above. Exact-head CI is the root's separate mandatory merge gate and was not queried or approved here. This scoped review approves the implementation at the stated head; it does not declare a full native suite or physical-device gate PASS.


## CI-test follow-up scoped review — 2026-10-02

**Verdict at head `1c62d272f25a0648024339163c28bf34dfacbb7b`: Spec PASS; Quality APPROVED.** New blocking findings: **0**.

Reviewed only `d81f6b777cff4d946e707a97bc8906978413a960` → this head: changed EquipmentScreenTest fixture/interaction, supporting evidence, report accuracy, and possible breakage from those test changes. The task brief remains unchanged and the d81 production approval remains applicable; `git diff --name-only` confirms no production source change. Read the supplied package once and inspected changed test source and bounded evidence directly where its combined output was truncated. No tests rerun, subagents, production edits, push, merge, or later-phase review.

### Fixture correction and coverage assessment

- `EquipmentScreenTest.kt:221–250` now exposes a mutable screen state, projects saved lines into EquipmentLine, and increments `equipmentSaved` in the Save callback. This models the existing success feedback. The named unchanged production check at `HarvestScreens.kt:662–663` confirms that equipmentSaved changes close the modal. The fixture does not set modal visibility directly; its closure assertion must still pass through the production effect.
- The first Save remains a physical tap, requires the exact EUR 7,000 snapshot/date and true capture flag, and requires the sheet to disappear. Reopening checks retained quantity one and unit price 70 rather than tapping a plus behind a still-open modal. This fixes the demonstrable original fixture mistake and strengthens the intended quantity/snapshot contract.
- Blank and zero branches now invoke the enabled Save semantic action (`EquipmentScreenTest.kt:210–217,256–269`). Before invocation they assert selected quantity, exact EditableText, correct pending/zero preview, and enabled Save. Afterward they require a non-null callback and exact type/quantity/snapshot/capture-default flag; zero also requires modal closure. They still fail if parsing/validation/draft generation/callback behavior is wrong. They do not inject a saved draft or call the fixture callback directly.
- Named unchanged button check: `MoButtons.kt:30–39` forwards the supplied onClick/enabled to the actual Compose Button. The semantic invocation exercises that button's accessible action; assertIsEnabled prevents the action from hiding a validation-disabled Save.
- Physical interaction controls remain: the corrected initial usual-price Save at line 244; edited unit price EUR 70→80 and EUR 140→160 preview with physical Save at lines 107–128; and the existing stepper Save. Existing confirmed-price clearing and overflow Save-disabled tests are untouched. The repository explicit-unknown/zero regressions approved at d81 are unchanged.

**Behavior is not masked at the declared callback/draft seam.** The blank/zero cases no longer prove their coordinate-based post-text-entry Save routing. This is a deliberate, accurately disclosed limit: the cause of CI's lost pointer callback remains unproven. Approval of this test correction is not a claim that a product touch defect was diagnosed or fixed. No retry, arbitrary sleep, softened monetary assertion, or production bypass was introduced.

### Evidence and report accuracy

Inspected the committed failure excerpts: both prior CI failures show saved-draft null at the two affected assertions, each with 394 tests and one failure. The root's separately reported passing alternate jobs do not cancel those failures.

Parsed `task-3-ci-fixture-red.xml`: one test, one failure. Its log fails assertDoesNotExist because the equipment-sheet still exists after the first successful save. This proves the missing-acknowledgement fixture defect; it does not establish the precise cause of both CI pointer failures. The handoff makes that distinction correctly.

Parsed `task-3-ci-focused-green.xml`: **9 tests, 0 failures/errors/skips**, including both changed methods and retained controls. The corresponding log ends BUILD SUCCESSFUL in 1m 4s, 77 tasks, and shows AndroidTest recompilation/assembly. `task-3-ci-noanimations.log` records **OK (2 tests)**, 7.141s; the original focused log records OK (2 tests), 10.428s. The notinstalled log records instrumentation-not-found setup failure and is retained rather than represented as a test PASS. The short noanimations output proves the two tests passed; device animation/density settings are supplied by the implementation report, not encoded in that output.

The updated report is accurate about what was changed, the independent RED fixture proof, final focused results, unproven pointer-cause limitation, and pending exact-head CI/physical acceptance. No report claim substitutes focused local success for full CI. Local HEAD matches the requested head and `git diff --check d81f6b77 1c62d272` is clean.

F1/F2/F3 remain ADDRESSED with unchanged production code. The root still owns new exact-head CI before merge; this review does not declare that CI or the physical-device gate PASS.


## Edited-price action scoped follow-up review — 2026-10-02

**Verdict at head `d1d7587e1d9e37fda8f3ba92e07d80380925200d`: Spec PASS; Quality APPROVED for the bounded test/action change. New blocking diff findings: 0. Physical coverage limitation carried forward: 1.**

Scope was `1c62d272f25a0648024339163c28bf34dfacbb7b` → this head, only the changed EquipmentScreenTest override interaction, evidence/report accuracy, and resulting new breakage. Read the supplied package once, updated report, and unchanged brief/full machinery requirements; inspected the direct ten-line executable test diff and relevant diagnostic/final evidence where combined output was truncated. No broad review, production edits, subagents, tests rerun, push, or merge.

### Assertion adequacy and new breakage

At `EquipmentScreenTest.kt:123–135`, the test preserves its original usual price 70, quantity two, pre-edit preview 140, override 80, post-edit preview 160, and exact `EquipmentPriceSnapshot(8_000, "EUR", harvest.harvestDate)`. It adds the selected-quantity precondition, exact EditableText “80”, enabled Save, non-null emitted draft, and emitted quantity two. There is no monetary assertion relaxation.

The enabled button's semantic OnClick is invoked once; it still exercises the same production callback/draft generation checked in the preceding review. The test does not inject an expected draft or call onSave directly. Incorrect parsing, quantity multiplication, override snapshot/date, validation disabling Save, or missing callback still fails. Existing initial usual-price and stepper physical Save controls are unchanged. All temporary keyboard/logging/drag experiments were removed from executable source.

No new blocking problem found in this diff. Production F1/F2/F3 remains unchanged and ADDRESSED. The prior financial/spec review remains applicable. This approval evaluates the pricing/action seam; it is not physical reachability approval.

### Established measurement and unresolved coverage

Directly inspected native diagnostic output:

- With IME visible, Save after scroll was y=1375..1507 in a 2340px window with 833px IME inset, and the original physical callback fired in that sample.
- With IME hidden, modal content was y=1303..3283 and Save y=2887..3019, below the 2340px window. The internal scroll value/max was 0/0 before and after performScrollTo.
- The hidden-keyboard class XML records 9 tests/1 failure; its log and the focused viewport diagnostic fail assertIsDisplayed. The first drag failed root selection before injection (two roots); the qualified drag experiment failed Compose idle with pending recompositions. Neither experiment proves that a human cannot expand the sheet, or that an expansion gesture fixes the problem.

The out-of-window measurement is established, not merely speculation about keyboard timing. The original CI lost-callback timing, human post-edit Save reachability, and the cause of the experimental drag timeout remain unresolved. Switching to semantic Save removes physical post-edit routing from this test's claim; it does not fix or disprove a potential accessibility issue. The report states this accurately and retains all RED experiments.

**Carried coverage observation:** physical/final review must check Save after price editing with keyboard open/dismissed and partial/expanded modal, including whether normal scrolling/expansion makes Save reachable. The root's stated carry-forward into the Slice 4 accessibility brief is recorded; this scoped review does not implement that phase or declare its follow-up complete. A semantic/CI PASS must not be used to erase this observation or claim physical acceptance.

### Evidence and report accuracy

The CI excerpt records the prior edited-price null-draft failure and 394 tests/one failure. The updated report does not let sibling passing full suites cancel it.

Parsed `task-3-ci-override-final-green.xml`: **9 tests, zero failures/errors/skips**, including the amended override test and retained controls. Its log ends BUILD SUCCESSFUL in 53s, 77 tasks, and shows AndroidTest rebuild. Diagnostic/hidden/drag outputs agree with their reported outcomes. New executable source is limited to EquipmentScreenTest; other changed files are logs/XML/report. Exact HEAD matches the requested head; `git diff --check 1c62d272 d1d7587e` is clean.

Fresh focused evidence was inspected, not rerun. Report limitations are appropriate. The root still owns new exact-head CI, physical-device acceptance, and the carried accessibility observation.


## Legacy labour confirmation scoped follow-up — 2026-10-02

**Verdict at head `c292d154d00ec422ace018e2cb00aeed3843746d`: Spec PASS; Quality APPROVED for this bounded test/action change. New blocking diff findings: 0.**

Reviewed `d1d7587e1d9e37fda8f3ba92e07d80380925200d` → this head only: the changed legacy JPY confirmation method, related evidence/report accuracy, and new breakage. Read supplied package once, updated report, and unchanged brief; earlier full machinery requirements and financial review remain applicable. No broad audit, production edit, subagents, tests rerun, push, or merge.

At `LabourCurrencyAndOverflowUiTest.kt:31–50`, the test retains the JPY historical ledger versus EUR usual-rate fixture and exact original JPY / 1,000-minor-unit outcome. It adds exact blank initial EditableText and disabled Save, exact entered text “1000” and enabled Save, non-null emitted LabourChange, agreement date, and DAY rate basis. The semantic action is invoked once through the actual enabled form button. No draft is injected, no assertion accepts EUR or null, and no financial assertion is relaxed. The other four test bodies remain unchanged.

Concrete named source checks: `LabourForms.kt:134` constructs a LabourRateSnapshot inside the enabled Save callback; `HarvestScreens.kt:800–802` forwards that change to LabourActions.onUpdate. In this fixture a null captured change therefore means no draft reached its callback, rather than proof that persisted JPY was silently converted to EUR. This source interpretation matches the report. Unchanged physical Save controls in LabourPaymentsUiTest remain at lines 58/71/74/84 and legacy-edit Save controls at 154/171; machinery stepper/initial-price controls are also unchanged. No production labour/payroll/payment writer or currency resolution was changed.

**Coverage limit:** the amended legacy test proves the enabled historical-currency/draft action, not post-input coordinate reachability. Its original physical method passed locally once, so the exact CI touch failure was not reproduced. Earlier measured equipment modal geometry must not be assumed to prove the labour modal's cause. The report correctly carries a separate physical legacy-confirmation check with keyboard open/closed and modal partial/expanded states into device/final review and the root's Slice 4 accessibility follow-up. No product interaction fix or physical acceptance is established by this test amendment.

Evidence inspected: the published CI excerpt records expected JPY versus null and 394 tests/one failure. Original-method log records one focused test and BUILD SUCCESSFUL in 40s; this is not evidence that the CI failure vanished. Parsed final `task-3-ci-labour-legacy-green.xml`: **5 tests, zero failures/errors/skips** with all five expected cases. Its log shows AndroidTest rebuild and BUILD SUCCESSFUL in 44s, 77 tasks. The updated report distinguishes those focused results from pending full exact-head CI and preserves the physical limitation.

No new blocking issue found. `git diff --name-only` confirms only this test plus evidence/report changed; exact HEAD matches the requested head and `git diff --check d1d7587e c292d154` is clean. F1/F2/F3 remains ADDRESSED with unchanged production code. The root's new exact-head six-check CI and physical follow-ups remain separate gates.
