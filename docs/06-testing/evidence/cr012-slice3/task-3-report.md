# CR-012 Task 3 handoff — machinery and use cost

## Status and scope

- **Status:** Slice 3 implementation and local verification complete; exact-head CI/review and physical-device gate remain with the root task.
- **Branch:** `feat/cr012-slice3-maquinaria` in `C:\Users\ISICIO\.codex\worktrees\cr012-slice1\MAGINA V20`.
- **Base:** `fde9c6632175e2ac0a48d07e25876ebe6de19c85` (Slice 2 already merged on `main` as `07a25014`).
- **Implementation/evidence commit:** `1b9bbabc` (`feat(cr012): freeze equipment use prices and post day costs once`). This report is a second bounded handoff commit.
- **Phase:** CR-012 Slice 3 only. No Slice 4 cards/navigation, backend, payroll, payment movement, or unrelated product scope was added. No push or merge was performed.

## Delivered behavior

- `EquipmentLine` and `EquipmentDraftLine` now carry a nullable immutable unit-price snapshot: minor units, ISO currency, and agreement date. Existing rows retain `null` as unknown. A new line captures the Farm's usual type price when its currency is compatible; the sheet permits an explicit override, including zero.
- Replacement keeps the canonical machine/type/named-other line identity and the saved snapshot when only quantity changes or the sheet re-saves an unchanged price. An actual price edit updates that line. Registered-machine quantity remains one.
- Room v21→22 adds three nullable columns to `harvest_equipment` without a fabricated backfill. KSP exported `app/schemas/com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase/22.json`; migration validation preserves a v21 legacy row, quantity, version, and null price. Both entity→domain mappers propagate the nullable snapshot.
- `DayCostLedger` calculates from per-line saved snapshots with checked multiplication/addition and updates the unique `DAY_EQUIPMENT` Expense. A known zero remains a POSTED zero Expense. The Expense ledger remains the source of campaign/day monetary totals; equipment lines provide operational detail. Usual-rate edits no longer reprice saved equipment, including an open campaign.
- Incomplete legacy equipment keeps its historical calculated Expense unchanged. Partial confirmation is allowed, but adding a priced line while an older kept line remains unknown is rejected atomically. Manual same-kind cost collision continues to draft the calculated entry; switching to calculated cost requires complete prices. Historical POSTED ledger currency and compatible snapshots take precedence over today's usual currency; ambiguous/mixed denomination is rejected. `LabourFinance.verifyCampaign` still runs after cost sync.
- The equipment sheet shows type/machine, quantity, editable unit cost per day/use, and per-line total. It preloads compatible usual prices, displays missing prices, and disables Save with an actionable message for malformed prices, multiplication/sum overflow, legacy priced append, historical currency conflict, or clearing a confirmed price. Closed campaigns still hide the edit action.

## Changed files

- Domain/calculation: `app/src/main/java/com/isivoltpro/maginaolivo/domain/equipment/Equipment.kt`, `domain/expense/DayCost.kt`.
- Local data: `data/local/entity/EquipmentEntities.kt`, `data/local/MaginaOlivoDatabase.kt`, `data/local/DatabaseMigrations.kt`, generated Room schema `22.json`.
- Repository/ledger: `data/repository/OfflineFirstEquipmentRepository.kt`, `DayCostLedger.kt`, `OfflineFirstDayCostRepository.kt`.
- UI: `feature/harvests/EquipmentSection.kt`, `EquipmentCurrencyContext.kt`, `HarvestScreens.kt`, `HarvestViewModels.kt`.
- Tests: `domain/expense/DayCostCalculatorTest.kt`, `feature/harvests/EquipmentCurrencyContextTest.kt`, `data/local/DayCostContractTest.kt`, `data/local/RoomMigrationTest.kt`, `EquipmentScreenTest.kt`.
- Evidence: `docs/06-testing/evidence/cr012-slice3/equipment-sheet.png`, `day-resources.png`, and the six `task-3-*.log` files beside this report.

## Validation and exact commands

All Gradle commands used `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, `ANDROID_HOME=C:\Users\ISICIO\AppData\Local\Android\Sdk`, and `C:\Users\ISICIO\.gradle\manual\gradle-9.4.1\bin\gradle.bat`. Device was the dedicated headless `MaginaOlivo_CR012_API35` (`emulator-5580`), containing only synthetic test data.

1. **TDD RED:** `:app:testDevDebugUnitTest --tests '*DayCostCalculatorTest'` failed on the absent `EquipmentPriceSnapshot` and snapshot-only calculator API as expected. After implementation, the focused domain test passed.
2. **Focused Room/Compose:** `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.data.local.DayCostContractTest,com.isivoltpro.maginaolivo.data.local.RoomMigrationTest` passed **31/31** initially; the later DayCost + EquipmentScreen focused run passed after adding the additional cases. Final `adb shell am instrument -w -e class com.isivoltpro.maginaolivo.EquipmentScreenTest com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner` passed **7/7**; output is `task-3-compose-adb.log`. A first capture-interleaved test run failed an onSave assertion; moving capture to a dedicated test resolved it. The failure is retained in `task-3-compose-capture.log`.
3. **Full Gradle build gate:** `gradle.bat --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest --console=plain` finished **BUILD SUCCESSFUL**, 173 tasks (39 executed, 134 up-to-date). Log: `task-3-gradle.log`; lint HTML: `app/build/reports/lint-results-devDebug.html`.
4. **Full unfiltered Android test run:** `gradle.bat :app:connectedDevDebugAndroidTest --console=plain` ran **392 tests: 391 passed, 1 failed**. The sole failure was `Phase18OfflineReopenTest.reopensAndRendersImportedGeometryWithoutNetwork`, assertion `Online setup did not persist the official parcel`. Log: `task-3-androidtest.log`. The existing harness executed that offline test before `Phase18OnlineImportTest`, which alone seeds `phase18-offline-map.db`; the latter passed later in the same run. This is a test-order prerequisite, not a Slice 3 failure. After installing the built APKs, running `Phase18OnlineImportTest` then `Phase18OfflineReopenTest` separately yielded **OK (1 test)** for each (`task-3-phase18-setup.log`, `task-3-phase18-offline.log`). No unrelated Phase 18 test or production code was changed. The deterministic ordinary CI suite and exact-head CI status still need root verification.
5. **Final scoped check after small UI/test cleanup:** `:app:testDevDebugUnitTest --tests '*DayCostCalculatorTest' --tests '*EquipmentCurrencyContextTest' :app:assembleDevDebugAndroidTest --console=plain --quiet` passed; rebuilt the DEV APK and Android test APK, then ran the final 7 Compose tests as above. `git diff --cached --check` passed before implementation commit.

## Evidence and review

- `docs/06-testing/evidence/cr012-slice3/equipment-sheet.png`: visually inspected on 2026-10-02; shows 2 vibradoras, habitual editable €70 unit use price, and €140 line preview.
- `docs/06-testing/evidence/cr012-slice3/day-resources.png`: visually inspected on 2026-10-02; shows vibradora €70, peine €20, remolque €30 with distinct unit and line totals. These are emulator Compose fixtures, not an end-to-end persisted user journey or physical-device Gate PASS.
- Transaction tests cover 1×70+1×20+1×30=120, 2×70=140 once, saved snapshot after habitual-rate change and reopen, explicit override, unique Expense/outbox on unchanged re-save, null/zero distinction, JPY historical ledger versus EUR usual rate, partial legacy confirmation, priced-append rejection, mixed-currency rejection, and overflow rollback of rows/outbox. Existing manual-collision and closed-campaign tests passed.
- Self-review traced every `HarvestEquipmentEntity`→`EquipmentLine` mapper (equipment repository and day ledger), confirmed campaign/day totals still read only POSTED Expense, checked the generated schema against migration columns, and reviewed the staged diff for scope and whitespace. No payment movement writer was changed; the Slice 2 labour finance guard remains invoked.

## Remaining concerns / next action

- Root should run exact-head CI with its ordinary Android test exclusion list, obtain independent review, and handle PR/integration. The unfiltered local Android task remains red only for the ordered Phase 18 fixture described above; it must not be described as a full native PASS.
- Physical-device acceptance remains pending. The semantic-color/card cleanup and broader day/campaign context belong to Slice 4; this slice keeps existing navigation and dashboard layout.

## Review fix round 1 — resumed and verified 2026-10-02

The independent review of `17f8d76b234db166aa462c0c45f9bd64d73ce912` raised F1 (P1), F2 (P2), and F3 (P2). This bounded wave preserves the interrupted worker's existing changes; no new implementation or unrelated scope was started. The implementation baseline for this wave is `17f8d76b`.

### Findings addressed

- **F1:** `DayCostLedger.sync` now rejects a replacing POSTED manual machinery expense while unknown equipment snapshots and a POSTED historical DAY_EQUIPMENT ledger coexist. It throws `appliedPrice:confirm_missing_prices` within the existing caller transaction, preserving the historical expense and rolling back create/post/update/link and outbox mutations. Additive lubricant remains accepted. Expense form/detail and day-cost entry points map the validation to actionable Spanish machinery-price instructions.
- **F2:** `EquipmentDraftLine` appends `captureUsualPriceWhenMissing = true` for backward compatibility. Existing saved snapshots still prevail. The sheet sends false for an empty displayed price; a new line explicitly left blank remains unknown even when the farm has a compatible usual price. The owner/root ruling permits saving that unknown price. A visible unchanged prefill captures its displayed amount; explicit zero remains known zero. No selector or navigation was added.
- **F3:** The canonical equipment repository checked-adds every known quantity × unit-price amount before any row/outbox write, including incomplete legacy sets. Overflow returns `appliedPrice:overflow` and rolls back; the known partial subtotal is never posted as the day total.

### Changed source and test files in this wave

1. `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/DayCostLedger.kt`
2. `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/OfflineFirstEquipmentRepository.kt`
3. `app/src/main/java/com/isivoltpro/maginaolivo/domain/equipment/Equipment.kt`
4. `app/src/main/java/com/isivoltpro/maginaolivo/feature/expenses/ExpenseViewModels.kt`
5. `app/src/main/java/com/isivoltpro/maginaolivo/feature/harvests/EquipmentSection.kt`
6. `app/src/main/java/com/isivoltpro/maginaolivo/feature/harvests/HarvestViewModels.kt`
7. `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/DayCostContractTest.kt`
8. `app/src/androidTest/java/com/isivoltpro/maginaolivo/EquipmentScreenTest.kt`
9. `app/src/test/java/com/isivoltpro/maginaolivo/feature/harvests/MachineryFinancialErrorTest.kt`

The F1 Room case covers create, draft→post, update, and unlinked→link; it checks unchanged historical expense, rejected manual row/status/link, unchanged outbox counts, 9,000 JPY posted total, and the accepted additive lubricant control. The fixture explicitly marks historical machinery as POSTED. The F3 case confirms two 6e18 unit prices while a third legacy line stays unknown, then checks Validation overflow and unchanged rows/ledger/outbox count. F2 Room + Compose cover the empty form→draft→repository contract, unchanged usual prefill and explicit zero. Existing focused collision/preferCalculated, missing-price, historical currency, frozen-price, closed-campaign, and overflow controls were retained.

### Fresh final GREEN evidence

Environment: same JAVA_HOME/ANDROID_HOME/Gradle path documented above; `ANDROID_SERIAL=emulator-5580`. The exact final command was:

```text
gradle.bat --no-daemon :app:testDevDebugUnitTest --tests '*MachineryFinancialErrorTest' --tests '*DayCostCalculatorTest' --tests '*EquipmentCurrencyContextTest' :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.data.local.DayCostContractTest,com.isivoltpro.maginaolivo.EquipmentScreenTest --console=plain
```

- Exit **0**; `BUILD SUCCESSFUL in 1m 2s`; `83 actionable tasks: 2 executed, 81 up-to-date`.
- **JVM 10/10:** calculator 7, currency context 2, machinery error message 1. XML: `task-3-fix-jvm-calculator.xml`, `task-3-fix-jvm-currency.xml`, `task-3-fix-jvm-errors.xml`; all zero failures/errors/skips.
- **Instrumented 24/24:** DayCostContractTest 15 and EquipmentScreenTest 9. XML root has tests=24, failures=0, errors=0, skipped=0; native report timestamp `2026-10-02T08:58:26`. XML: `task-3-fix-instrumented.xml`.
- DEV and AndroidTest APK assembly succeeded/up-to-date against the current source. Final output: `task-3-fix-final-green.log`.
- `git diff --check` passed before report/commit. No unchanged full suite was rerun; the earlier baseline full JVM/lint/three-build and unfiltered native evidence remains historical, not new-head proof.

### Retained failed attempts — filenames do not imply success

All five interrupted-worker logs were read and retained unchanged:

- `task-3-fix-red.log`: FAILED in 12s; missing F1 collision guard and F3 subtotal validation returned Success instead of Failure.
- `task-3-fix-f2-red.log`: FAILED in 3s at compile; the new explicit-unknown API was absent. This is compile RED evidence, not an executed UI symptom assertion.
- `task-3-fix-f1-message-red.log`: FAILED in 3s at compile; machinery error helper absent.
- **`task-3-fix-f1f3-green.log` is FAILED in 13s**, despite its name: a test expected JPY 9,000 through an EUR-default summary and obtained zero. The final assertion instead sums POSTED JPY expenses directly; the historical fixture status is explicit.
- **`task-3-fix-focused.log` is FAILED in 53s**, despite the prior verbal GREEN handoff: the existing no-price stepper test expected the old default draft flag (true) while the sheet correctly emitted explicit unknown (false). Its expected drafts now match that contract. The fresh final log/XML above is the confirmed GREEN result.

### Self-review and remaining concerns

Reviewed the complete current source/test diff against the full Slice 3 brief, original findings, Issue #309, and Slice 2 financial guard. All guarded Expense and day-link mutations call sync inside Room transactions and translate the thrown validation outside the transaction. Unknown historical machinery is preserved; only complete equipment pricing reaches the ordinary manual-collision demotion path. The overflow check precedes equipment persistence/outbox writes. The appended flag defaults true for old callers and never clears an existing snapshot. Totals remain Expense POSTED only, with no changes to payroll/payments, migration/schema, root navigation, Slice 4, or backend.

No further source changes were made during the resumed verification. The existing emulator PNGs were already inspected by the root; they are unchanged and not regenerated for this wave. They remain synthetic Compose evidence, not persisted end-to-end or physical-device acceptance.

Independent scoped re-review and **new exact-head six-check CI are mandatory with the root before integration**. Physical-device acceptance remains pending. The earlier unfiltered 391/392 native run and its ordered Phase18 fixture limitation remain accurately reported; this fresh 24/24 scoped run does not claim a full native suite PASS. No push, merge, subagents, or later-phase scope occurred in this wave.

## CI diagnosis and Compose fixture correction — 2026-10-02

Baseline `d81f6b777cff4d946e707a97bc8906978413a960`. The root supplied two new exact-head CI failures: [Gate3 evidence run 36987569607](https://github.com/izc05/magina-olivo-v20/actions/runs/36987569607), `unchangedUsualPrefillAndExplicitZeroStayConfirmed` line 227, and [Gate3 emulator run 36987565423](https://github.com/izc05/magina-olivo-v20/actions/runs/36987565423), `clearingNewUsualPrefillSavesExplicitUnknownInsteadOfHiddenPrice` line 203. Each had 394 tests and one failure, a null captured draft after the physical Save action. The alternate emulator/evidence jobs passed 394 tests; those passes do not cancel the failures. Full failed logs remain in `.superpowers/sdd/CR012-EXECUTION-PLAN/ci-d81-{evidence,emulator}-failed.log`; exact failure excerpts are published as `task-3-ci-failure-excerpts.log`.

### Investigation and bounded proof

Read the systematic-debugging skill and traced the exact path: the price field updates `prices`; selected lines and checked validation derive from that state; `MoPrimaryButton` invokes `onSave(lines)` synchronously. In the real ViewModel, repository success increments `equipmentSaved`; `HarvestDetailScreen` closes its modal in `LaunchedEffect(state.equipmentSaved)`.

Both original failing methods passed once locally after installing the d81 application/test APKs: `adb -s emulator-5580 shell am instrument -w -e class com.isivoltpro.maginaolivo.EquipmentScreenTest#unchangedUsualPrefillAndExplicitZeroStayConfirmed,com.isivoltpro.maginaolivo.EquipmentScreenTest#clearingNewUsualPrefillSavesExplicitUnknownInsteadOfHiddenPrice com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner`, **OK (2 tests)**, 10.428s; `task-3-ci-original-focused.log`. This did not reproduce CI's lost callback and was not treated as a fix or a reason to retry CI.

A definite separate fixture defect was proven: the original unchanged-default/zero method fed a constant `equipmentSaved=0` state and never acknowledged its first save. A diagnostic `assertDoesNotExist` after that first successful 7,000-minor-unit assertion failed because `equipment-sheet` still existed. The next attempted reopen was therefore a physical tap aimed at the underlying day screen while the modal remained, and the second plus could alter the existing draft rather than start the intended new form. Diagnostic command:

```text
gradle.bat --no-daemon :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.EquipmentScreenTest#unchangedUsualPrefillAndExplicitZeroStayConfirmed --console=plain
```

Exit **1**, **1 test failed**, `BUILD FAILED in 40s`; `task-3-ci-fixture-red.log` and `task-3-ci-fixture-red.xml`. The final test retains this closure assertion and passes after correcting the fixture.

### Change and assertions

Only `app/src/androidTest/java/com/isivoltpro/maginaolivo/EquipmentScreenTest.kt` changed. The default/zero fixture now exposes its saved equipment lines through Compose state and increments `equipmentSaved`, matching real success feedback. It physically taps Save for the visible usual price, verifies the exact 7,000 EUR snapshot/date and true default flag, requires modal closure, then reopens and checks retained quantity one and price 70. Editing that confirmed line to zero verifies exact editable text, zero preview, enabled Save, exact zero EUR snapshot/date, type/quantity/flag, and a second closure.

The cleared-price method verifies selected quantity one, exact empty editable text, pending preview, enabled Save, and the emitted type/quantity/null snapshot/false capture-default flag. The empty and zero draft-seam checks invoke the enabled accessible OnClick action instead of mixing their domain assertions with coordinate-based pointer routing after text input. They still fail if Save is disabled or emits no/wrong draft. Existing physical-tap controls remain, including usual-price save, edited unit-price save, and stepper save. No retries, arbitrary sleeps, relaxed snapshot assertions, or production changes were introduced.

The precise cause of the two CI pointer failures was **not reproduced or proven locally**; keyboard/window/layout timing is a possible explanation, not an established result. The semantic-action choice verifies the intended callback/draft seam and does not claim to fix a product touch bug. The missing saved-state acknowledgement is separately proven and corrected, not asserted as the sole explanation for both failures.

### Fresh validation and evidence

Same JAVA_HOME/SDK/Gradle paths and emulator-5580 as above. Full focused class command:

```text
gradle.bat --no-daemon :app:assembleDevDebugAndroidTest :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.EquipmentScreenTest --console=plain
```

Exit **0**, **9/9**, zero failures/errors/skips; `BUILD SUCCESSFUL in 1m 4s`, `77 actionable tasks: 6 executed, 71 up-to-date`. Log `task-3-ci-focused-green.log`; copied XML `task-3-ci-focused-green.xml`. AndroidTest APK rebuilt; DEV application unchanged/up-to-date.

A second bounded check used the two affected methods with Android window/transition/animator scales explicitly set to zero, matching CI's disabled animations; same device width 1080 / density 440 (approximately 393dp), font scale 1.0. After APK install, the above two-method `adb am instrument` command passed **OK (2 tests)** in 7.141s; `task-3-ci-noanimations.log`. The first attempt after the connected Gradle task could not find instrumentation because that task had uninstalled the APKs; the failed setup output is retained as `task-3-ci-noanimations-notinstalled.log`. Reinstalling the built app/test APKs resolved setup; no failing test was retried or hidden.

Self-review confirmed only test/evidence/report files changed; F1/F2/F3 production remains exactly as d81. New logs have trailing whitespace normalized without changing messages/results. No Room/JVM/full-suite repetition was needed for a test-only fixture correction. No screenshots were changed, no push/merge/subagents/later-phase work was performed. Independent scoped review and the root's **new exact-head CI** remain mandatory; this local 9/9 plus 2/2 is not a claim that full CI now passes. Physical-device acceptance remains pending.

## Remaining edited-price CI interaction — 2026-10-02

Baseline `1c62d272f25a0648024339163c28bf34dfacbb7b`. The root observed five of six checks successful (including three full 394-test suites), but [push emulator run 36990263550](https://github.com/izc05/magina-olivo-v20/actions/runs/36990263550) failed its fourth full suite: **394 tests, one failure**, `usualUnitPriceIsEditableAndPreviewMultipliesQuantityOnce`, null captured draft at line 127 after physical Save. The full log remains `.superpowers/sdd/CR012-EXECUTION-PLAN/ci-1c62-emulator-failed.log`; published exact excerpts: `task-3-ci-override-ci-red-excerpts.log`. Passing sibling checks were not used to dismiss this failure.

### Measured diagnosis and failed experiments

Read the exact failure and inspected the native keyboard and modal geometry with temporary diagnostic logging. Pricing assertions were already satisfied before the missing callback. The actual original fixture values are usual price 70, quantity two, preview 140, edited price **80**, preview **160**, and exact **8,000 EUR** unit snapshot/date; these are preserved verbatim in the final change.

- A one-method diagnostic run with native IME/Save-bound logs passed (**1/1**, exit 0, BUILD SUCCESSFUL in 38s): `task-3-ci-override-diagnostic.log` / `task-3-ci-override-touch.log`. After text input, native IME was visible (833px bottom inset) and Save had zero clipped bounds. After internal scroll, Save bounds were y=1375..1507 in a 2340px window, ending exactly at the keyboard's upper edge (2340−833=1507). The physical callback fired in that settled local sample. This does not reproduce the exact CI race or prove that post-edit access is reliable.
- Experiment: `Espresso.closeSoftKeyboard`, Compose idle, an explicit IME-hidden assertion, then internal scroll + displayed/enabled assertions + one physical tap. **8/9** class run, exit 1, BUILD FAILED in 53s; the edited-price method failed `assertIsDisplayed` before the tap. Retained log/XML: `task-3-ci-override-hidden-red.log/.xml`. Closing the keyboard alone did not prove reachability.
- A focused geometry diagnostic reproduced that displayed precondition failure (**1/1 failed**, exit 1, BUILD FAILED in 38s): `task-3-ci-override-viewport-diagnostic.log` / `task-3-ci-override-viewport.log`. IME was hidden, native window height 2340, but the modal content remained partially expanded at y=1303..3283; Save was y=2887..3019 below the window. Internal scroll was value=0/max=0. `performScrollTo` left those values unchanged; it cannot expand the modal's separate anchor.
- A proposed single physical upward drag first failed before injection because an unqualified Compose root selector matched the Activity and modal roots (two roots): `task-3-ci-override-drag-root-red.log` / `task-3-ci-override-drag-root.log`. After qualifying the modal root by its equipment-sheet descendant, a single drag triggered a Compose idling timeout with pending recompositions, before post-drag geometry could be read: exit 1, BUILD FAILED in 1m 5s; `task-3-ci-override-drag-idle-red.log` / `task-3-ci-override-drag.log`. That result neither proves a working user drag nor establishes a production defect. No gesture/click was retried.

All temporary logger, keyboard/drag changes and imports were removed from the final source. The root explicitly stopped further gesture experiments and ruled that this test's purpose is pricing preview plus emitted draft; edited-price Save should use its accessible action while retaining the existing physical Save controls. The observed modal/IME ambiguity must remain a physical-device follow-up and a minor final-review coverage limitation.

### Final bounded change

Only `app/src/androidTest/java/com/isivoltpro/maginaolivo/EquipmentScreenTest.kt` changes executable test source. The edited-price case checks selected quantity two and exact editable text 80, preserves the 140/160 previews, verifies Save enabled, invokes its semantic OnClick once, asserts a draft was emitted, and checks quantity two plus the unchanged exact 8,000 EUR snapshot/date. Existing initial habitual-price Save and stepper Save still use physical taps. No production code, retry, fixed sleep, weakening of numerical assertions, or change to F1/F2/F3 was introduced.

This amendment verifies the pricing/action seam; **it does not claim post-edit physical touch reachability was fixed**. The local out-of-window/partial-modal measurement is established. The cause of the original CI timing and the physical drag idling failure is not fully established, and no generic app or library bug is asserted.

### Fresh final verification and handoff

Same dedicated emulator-5580, JAVA_HOME/SDK/Gradle paths; Android animation scales remained zero as in CI. The final command was:

```text
gradle.bat --no-daemon :app:assembleDevDebugAndroidTest :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.EquipmentScreenTest --console=plain
```

Exit **0**, **9/9**, zero failures/errors/skips; `BUILD SUCCESSFUL in 53s`, `77 actionable tasks: 6 executed, 71 up-to-date`. Log/XML: `task-3-ci-override-final-green.log/.xml`. AndroidTest rebuilt, DEV unchanged/up-to-date. Only trailing log whitespace and a stray blank source line were normalized after execution. Source diff and copied XML were reviewed; staged/committed whitespace checks passed. Unchanged JVM/Room/full-suite checks were not repeated.

Reports are appended here and copied to the tracked evidence report; all diagnostics, including RED attempts, are published with accurate names/results. Existing PNGs unchanged. No push, merge, subagents, Slice 4, payroll, backend or unrelated work occurred. Root must obtain scoped review and new exact-head CI. Physical-device acceptance remains pending and must specifically check edited-price Save with keyboard open, keyboard dismissed, and the partially expanded/expanded modal; this is recorded coverage follow-up, not a hidden PASS or a claimed production fix.
