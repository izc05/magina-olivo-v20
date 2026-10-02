# CR-012 Task 4 — native surfaces and explicit economic context

Status: **DONE_WITH_CONCERNS** — implementation/local verification complete; root independent review, exact-head CI and owner physical-device acceptance remain external gates. Source commit `d0d24e2dc49169768623ff753f2d29ce40e91952`, branch `feat/cr012-slice4-pantallas`, base `a1573ca49c25b59a813a3519fdc71ad637b810b5`. Single implementer; no push, merge, backend changes, schema migration, root navigation redesign or historical rewrite.

## Result and scope

Day resources are compact navigable labour, machinery and other-expense cards. Their detail sheets reuse the original entries/forms; historical worker count and free-text machinery remain display-only. Method/notes sit behind More details. Day totals follow canonical weighed kg/Pesadas/parcel context. Redundant Pesadas aggregate text was removed while individual delivery rows, origin names and weighted day yield/coverage remain.

Campaign production and recollection costs are separate. The cost cards use mutually exclusive posted Expense buckets (labour / equipment / other), include fuel, transport and repairs in Other, and avoid adding operational draft prices or payments. The 300+180+50 EUR fixture totals 530 EUR over 3200 kg: exact decimal 0.165625 EUR/kg, displayed 0.17 EUR/kg. Named work, unknown attribution and independent paid/pending balances remain reachable in existing details. Payments still work after campaign close.

Economic campaign assignment requires an explicit campaign or linked day. Farm/date alone remains outside the campaign. The projection includes all explicitly campaign-linked categories, with no mutation of existing outside history. The generic Expense form retains original currency, campaign/day association, amount, line total and recorded unit price; document proposals supply their contract currency. EUR remains the compatible default for generic new expenses. Unsupported historical currency disables editing with its original code.

Canonical cost/kg uses Slice 1's `RecollectionCostSummary` and safe decimal display rounding. Zero posted money with weighed kg produces zero; absent money, unsupported currency or absent/zero denominator stays unavailable. Mixed currencies display separately in Day/Campaign totals, comparisons, Costs and campaign-context Expense list, with no conversion.

Root's bounded ruling is applied to new Day Other expenses: unique supported ISO from live posted day Expenses first; otherwise confirmed historical snapshots; usual farm currency only with no history. Ambiguous/unsupported history blocks with original codes and the normal explicit Expense route remains available. Async reads cannot establish a guessed default: the contextual entry waits for relevant resource/rate reads and blocks on read failure. This is an addition to the existing contextual resolver, not a currency selector.

Shared semantic tokens provide terracotta labour, Earth machinery (including comb detail), Money other expenses, Info total/cost-per-kg, sage yield, Success paid, Warning pending and Info partial payment. Relevant native sheets use `MoWarmWhite`; edit sheets skip partial expansion so post-IME Save is physically reachable. Accessible cards merge title/value/support text and expose native button role with minimum touch size.

## Verification environment and exact commands

Windows PowerShell, Java `C:/Program Files/Android/Android Studio/jbr`, SDK `C:/Users/ISICIO/AppData/Local/Android/Sdk`, Gradle `C:/Users/ISICIO/.gradle/manual/gradle-9.4.1/bin/gradle.bat`. Dedicated `emulator-5580`, MaginaOlivo_CR012_API35, synthetic records only. Physical viewport 1080x2340; density 440 = 392dp/font1.0; density 480 = 360dp/font1.3. Animation scales zero.

Final focused APK build:

```powershell
$gradle = 'C:/Users/ISICIO/.gradle/manual/gradle-9.4.1/bin/gradle.bat'
$adb = 'C:/Users/ISICIO/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ISICIO/AppData/Local/Android/Sdk'
$env:ANDROID_SERIAL = 'emulator-5580'
& $gradle :app:assembleDevDebug :app:assembleDevDebugAndroidTest --console=plain
& $adb -s emulator-5580 install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
& $adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
& $adb -s emulator-5580 shell wm density 480
& $adb -s emulator-5580 shell settings put system font_scale 1.3
& $adb -s emulator-5580 shell am instrument -w -e class com.isivoltpro.maginaolivo.Cr012SurfacesTest,com.isivoltpro.maginaolivo.JornadaScreenTest com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner
```

`task4-apk-final.log`: BUILD SUCCESSFUL. `task4-final-360.log`: **17/17**, 13 CR012 +4 Jornada, zero failures. Assertions cover currency context/form emission, mixed/no-conversion comparisons, historical JPY Costs, loading/read errors, detail navigation, partial payment after close, explicit campaign forms and canonical 530/3200 display. Earlier fresh UI compatibility focus: **21/21** (`task4-focused-ui2.log`). Room focus: **26/26** in `task4-native-room.log`, including explicit/outside writer context, JPY/EUR/KWD create/update/line preservation, closed rollback and outbox contracts.

Final full Gradle gate:

```powershell
& $adb -s emulator-5580 shell wm density reset
& $adb -s emulator-5580 shell settings put system font_scale 1.0
& $gradle --no-daemon testDevDebugUnitTest lintDevDebug assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest --console=plain
```

Final ordinary instrumented gate (the exact four live fixture exclusions in `.github/scripts/gate3-emulator-evidence.sh`):

```powershell
& $gradle --no-daemon :app:connectedDevDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.isivoltpro.maginaolivo.CatastroLiveImportTest,com.isivoltpro.maginaolivo.Phase18OnlineImportTest,com.isivoltpro.maginaolivo.Phase18OfflineReopenTest,com.isivoltpro.maginaolivo.Phase18PolygonParcelLookupTest' --console=plain
```

`task4-gradle-final.log`: **BUILD SUCCESSFUL in 3m 7s**, exit 0, 173 tasks (25 executed /148 up-to-date). Final JVM XML: **309 tests, 0 failures/errors/skips**, 70 suites under `jvm/`. Lint XML: **0 errors, 41 warnings, 3 hints** (no claim that all warnings predate this slice). DEV/STAGING/PRODUCTION and AndroidTest APKs built. Only two Android-test assertion/fixture adaptations changed after this production foundation gate; production source and DEV APK are unchanged.

First full ordinary Android run `task4-android-final.log`: **408 tests, 2 failures, 0 errors/skips**, BUILD FAILED in 6m14s. Complete original XML is retained under `android-initial/`. The lifecycle case used descendant assertions incompatible with accessible merged KPI semantics; same tags and original unknown/zero/lifecycle assertions now check merged text. The Room JornadaCost case relied on implicit farm/date campaign assignment; its 3000 standalone cost is now explicitly linked, preserving the original 19550 campaign and 16550 day acceptance. An additional 700 farm/date-only cost must retain null campaign and gives 20250 in the complete ledger, proving economic exclusion without deleting the source expense. Existing edit/delete and persistence checks remain.

After rebuilding AndroidTest (`task4-gate-assertions-build.log`), the same direct native command with `-e class com.isivoltpro.maginaolivo.Cr012SurfacesTest,com.isivoltpro.maginaolivo.AppNavigationTest#farmParcelCampaignLifecyclePersistsAcrossRecreation,com.isivoltpro.maginaolivo.data.local.JornadaCostContractTest` gave **16/16 GREEN** at default392dp/font1.0 (`task4-gate-assertions-green.log`). This rechecks both failures and all 13 CR012 cases. The justified final ordinary repeat with the exact exclusions above gave **408/408 GREEN**, **0 failures/errors/skips**, exit 0, **BUILD SUCCESSFUL in 6m3s**, 77 tasks (1 executed /76 up-to-date): `logs/task4-android-green.log`, original JUnit XML under `android-final/`. This includes Room expense/labour/payment/equipment/day contracts and ordinary native navigation. No unfiltered Android-suite PASS is claimed.

Owner DEV package: `.superpowers/sdd/CR012-EXECUTION-PLAN/cr012-slice4-dev.apk` (also normal `app/build/outputs/apk/dev/debug/app-dev-debug.apk`), **107053208 bytes**, SHA256 `E005115FAFA792E4E1491195AB171A1DAAB26E212F851B82D0B57CE3CDDEB2C6`. Binary kept locally for owner review rather than committing a 107MB APK into source control.

## RED/GREEN and retained failed iterations

All listed logs are preserved under `logs/`; failures below are not successful evidence.

| Log | Observed result / diagnosis |
| --- | --- |
| task4-red / task4-green | JVM form/projection RED 12/4 failures, then 12/12 green. |
| task4-kpi-red / task4-kpi-green | Exposed precise KPI RED 5/1 (high/zero old formula), then 5/5 green. |
| task4-buckets-red | Historical manual HARVEST classification RED 4/1; corrected exclusive buckets verified in later full JVM. |
| task4-ui-red | Native 1/1 failure: missing compact campaign labour card, then later focused green. |
| task4-iteration | Compile failure: missing ExpenseSummary import; corrected. |
| task4-iteration2 | JVM 304/1: old route query literal; updated optional explicit campaign contract. |
| task4-iteration3 | JVM 304/1: route-builder test invokes Android Uri stub on plain JVM; removed that invalid harness test, native emitted context is asserted. |
| task4-iteration4 | Full JVM 304/304 and DEV/AndroidTest builds green. |
| task4-focused-ui | 47/22 failures with a stale APK pair (including NotebookActions NoSuchMethodError); not used as verification and not assumed to explain later fresh failures. |
| task4-focused-ui2 | Fresh APK compatibility focus 21/21 green. |
| task4-native-room | Fresh 32/2: Room 26 passed; 2 native total-card assertions failed because nonclickable KPI descendants were not merged. Corrected semantics. |
| task4-native2 | Fresh 7/1: read errors not visible before opening details; corrected surface visibility. |
| task4-day-currency-red | 1/1: historical JPY Day new-cost label missing against usual EUR; fixed scoped resolver/form. |
| task4-pre-accessibility | Compile failure from invalid assertDoesNotExist import; removed import (member assertion remains). |
| task4-360-font130 | Stale attempt 12/1 after that failed build; not valid current verification. |
| task4-360-font130-current | Fresh 12/2: equipment capture content-wait sampling timed out before native click; old Jornada yield-label assertion failed after deliberate duplicate-summary removal. Dense compositor-content sampling and row/yield assertions fixed individually. |
| task4-360-font130-final | Fresh 12/12 green. |
| task4-line-price-red | JVM 9/1: historical recorded unit price lost during form edit; retained unchanged, later 9/9 green. |
| task4-comparison-red | Native 2/1: comparison omitted KWD mixed currency; mixed Day blocking passed. Separate comparison ledger labels fixed. |
| task4-360-font130-complete | Fresh 14/14 green. |
| task4-context-totals-red | Native 1/1: explicit campaign expense-list total omitted mixed original currency; canonical totals fixed. |
| task4-360-font130-accepted | Fresh 15/15 green. |
| task4-context-read-red | JVM 5/1: pending resource read established guessed EUR; read-state guard added, subsequent full JVM green. |
| task4-jpy-cost-tab-red | Native 1/1: JPY-only posted ledger showed "Sin gastos contabilizados" via legacy EUR count; canonical count fixed. |
| task4-final-360 | Fresh final source 17/17 green. |
| task4-android-final | First full ordinary 408/2, the two exact stale semantics/context test contracts described above; original XML retained. |
| task4-gate-assertions-green | Fresh two-failure closure plus native/default-density captures, 16/16 green. |
| task4-android-green | Final ordinary whole suite 408/408 green, 0 failures/errors/skips; original final JUnit XML retained. |

## Native evidence and physical interaction

`native/` contains actual Android compositor PNGs and native accessibility hierarchy XML, at 392dp/font1.0 and 360dp/font1.3, for Day resources/total/kg, labour/machinery details, Campaign compact costs/total/kg, closed person partial balance, payment after close, equipment post-edit Save and legacy JPY labour post-edit Save. These execute production Compose screens with injected synthetic canonical records; they are **component fixtures, not a persisted whole-app journey**. Separate Room and ordinary navigation/instrumentation contracts establish persisted behaviour. No reference mockups or rendered fake screenshots are used.

The initial 392dp person capture was visually blank despite semantic assertions. It is not accepted evidence; final default-density recapture replaces it. Capture now waits for actual native compositor content rather than trusting file length. Person name/generated/paid/pending display is asserted; accepted 360dp and final392dp captures were visually inspected. Paid 60 is Success green, pending 40 Warning amber, Partial Info and closed-history explanation visible; final392dp also visibly shows the canonical work row and the dated 60EUR payment/correction row. Machinery detail uses cream surface with Earth badges for shaker, comb and trailer. Gradle connected-test cleanup removes application external files, so accepted captures were pulled immediately after the successful direct native focused run, before the final Gradle suite.

For post-edit modal proof, the test enters equipment price 80 EUR / legacy labour 1000 JPY, closes IME, scrolls Save into view, checks enabled/displayed, resolves its native accessibility node, verifies visible physical-window bounds and sends one native pointer down/up at its centre. Actual draft callbacks must emit EUR8000 / JPY1000. **This is physical pointer proof distinct from semantic pricing OnClick tests.** No retry clicks or arbitrary test sleeps were added. Dense screenshot readiness sampling only waits for observable compositor content. Both cases passed at 360dp/font1.3.

## Review concerns and remaining external gates

Code review and exact-head CI are owned by root after this handoff. Owner physical-device acceptance remains pending; no device gate is declared PASS. The supplied APK is for that physical review. Live Catastro/ordered Phase18 fixture suites were intentionally excluded from the ordinary suite, consistent with the repository harness, and are not claimed verified here.

Calendar campaign duration, dates with Pesadas and number of recollection days remain separate production concepts with explicit titles; no repeated money aggregate or long worker list is present on Campaign summary. Annual machinery-history category semantics are retained; only recollection ledger buckets change. Existing canonical records, historical snapshot immutability, closed-campaign rollback, outbox and Expense/payment independence remain subject to Room/full-suite verification.

No quota reset/purchase, other worker, reviewer or user-approval stall was used. Source commit `d0d24e2d` contains 38 bounded source/test files (862 insertions /247 deletions); exact inventory in [source-files.txt](source-files.txt). Public evidence is committed separately; its commit is the evidence-only successor of `d0d24e2d` (avoids a self-referential report hash). Primary changed files are ExpenseLedgerWriter, CampaignNotebook, RecollectionLedger, Expense forms/review/context routes, precise Dashboard/Comparison, Harvest resource cards/read state/sheets, Campaign summary/Costs, payment semantic colours and shared MoKpiMetric accessibility. Tests include ExpenseForm, DayExpenseCurrencyContext, CampaignNotebook/Analytics, native CR012 surfaces and adapted existing Room/UI/navigation contracts. `git diff --check` passed; no unrelated files were staged. Public evidence includes 40 PNG/native-hierarchy files, all 70 final JVM suite XMLs, initial/final full Android JUnit XML, current lint XML, complete failed/successful task logs and this report. Execution-plan report is an identical local copy.


Root handoff recovery: implementer quota expired after source commit and complete GREEN evidence/report, before staging the evidence commit. Root verified final 309 JVM and 408 native result, closed documentation only and committed preserved evidence. No implementation or tests were repeated during this recovery. Independent review and exact-head CI remain required.
