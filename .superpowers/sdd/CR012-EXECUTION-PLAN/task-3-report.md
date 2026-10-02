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
