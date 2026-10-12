# PR #745 — continuation QA, 2026-10-12

Base: Claude `9465de1ce450da5d6af98216914da1144967b4c1`. All four implementation blocks remain intact. No production Kotlin, calculations, database, navigation or visual design changed.

## Failure and correction

CI had one stale surface test: `dashboard-cost-per-kg` was intentionally replaced by the campaign summary. The regression now checks the cost descendant and preserves `530 EUR / 3,200 kg = 0.166 EUR/kg`; the partial-cost case checks «Coste contabilizado/kg» and «Incompleto» next to the same booked figure.

Additional production-surface tests check both themes, the folded default, opening/closing all four charts, textual totals and actual text line bounds/ellipsis. The test host respects system bars. `TextLayoutResult.hasVisualOverflow` is unsuitable here because the paragraph retains its available width even when Text measures to its intrinsic width; assertions use each actual line instead.

## Executed local verification

- Gradle 9.4.1, Android Studio JBR 21, SDK 37, API 35 `MaginaOlivo_Claude_API35`, serial emulator-5584. Existing app data retained; no `pm clear`. Device density/font restored by the runner.
- `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`: BUILD SUCCESSFUL; 584 JVM tests, zero failures. Existing compiler warnings remain.
- `verify-campaign-panel.ps1`: 16/16 instrumented executions, four methods for each configuration below. Each includes explicit LIGHT and DARK production-screen tests. Attached text logs are the actual instrumentation output.

| Width | Density on 1080px | Font | Result |
|---|---:|---:|---|
| 360dp | 480 | 1.0 | 4/4 |
| 360dp | 480 | 1.3 | 4/4 |
| 390dp (390.07 actual) | 443 | 1.0 | 4/4 |
| 390dp (390.07 actual) | 443 | 1.3 | 4/4 |

Run after installing the DEV and test builds:

```powershell
.github/scripts/verify-campaign-panel.ps1 -Adb C:/Users/ISICIO/AppData/Local/Android/Sdk/platform-tools/adb.exe -Serial emulator-5584
```

Captures reviewed: summary, incomplete-cost warning, parcel and cost donut legends, kg bars, yield labels in both themes at large font. Costs may wrap at 360dp/font1.3; they remain complete. These are synthetic canonical records, not physical owner QA.

Separately opened the existing DEMO in MainActivity/Cuaderno: 6,680 kg / 4 pesadas / 3 days / 20.57% / 0.228 EUR/kg / 1,522.50 EUR. `demo-summary-411dp.png` preserves that observation at the AVD original density; it is separate from the narrow-screen fixture matrix.

## Emulator infrastructure and CI

Old runs differ: 38269332258 completes all four reference capture variants and 196 offline tests before the stale-test failure; 38269426163 loses the hierarchy after successful onboarding at font1.3. Connection refused occurs during emulator teardown. No evidence establishes an agricultural app crash as its cause; no gate was skipped or relaxed.

Gate script now saves device/app/activity/memory/crash state if hierarchy dumping fails and pulls production panel captures into the CI evidence artifact. Bash syntax and all six fake-adb safety guard tests pass. GitHub foundation + both emulator gates must still pass on the new HEAD before merge approval. This local QA does not certify the full 753-test instrumented suite or CI.

No candidate APK, merge, Supabase production deployment or later-phase scope. Next: CI on this PR, then security G0 #743 in its separate worktree.
