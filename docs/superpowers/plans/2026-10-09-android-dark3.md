# Android DARK-3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Finish #707's reactive device appearance across agricultural screens and expose Perfil → Ajustes → Apariencia.
**Architecture:** Reuse AppearanceStore and MoColors from DARK-1/2. Resolve colors in Compose; keep Room, navigation and source image pixels intact. One productive PR, from main `6bd2879d`.
**Tech Stack:** Kotlin, Compose, Android API35, Gradle9.4.1/JDK17.
**Spec:** `docs/00-master/RC1.2-PRODUCT-LOCK.md` §17; owner issue #707, sequenced by #711.

## Global Constraints

- `SYSTEM(default)/LIGHT/DARK`; device preference, no Room/workspace/outbox changes.
- `Inicio | Mi Campo | [+] | Cuaderno | Perfil`; four roots, existing forms and records.
- Normal text >=4.5:1; source photography, brand artwork and PNOA pixels never inverted.
- Preserve every saved file and retained data. No Web V3, OCR activation or Gate22+.
- Exact final-SHA independent review plus foundation/gate3-emulator/gate3-evidence SUCCESS before merge.
- Physical Gate21 remains pending owner acceptance. Candidate APK only from integrated green main after B3/B4.

## Review Focus

- Photographic foregrounds must keep their readable fixed photo color; test photo identity and inspect overlays.
- Noncomposable chart/data functions cannot read CompositionLocals; test chart data and actual themed rendering.
- Preference write failure retains the previous selection and keeps agricultural actions available; test failure UI.
- System bars need legible icons in gestures and three-button navigation; test real-window flags and inspect screenshots.
- Large-font sheets and keyboard must leave saving/cancellation reachable; test 360dp/font1.3 and inspect captures.

### Task 1: Remaining screen surfaces

**Files:** Modify remaining `feature/{activities,agenda,attachments,campaigns,catastro,deliveries,expenses,farms,harvests,machinery,maps,notebook,parcels,phytosanitary}/*.kt` and UI reference screen shells; exact legacy usages in `artifacts/696-e2e/707-dark3-legacy-token-inventory.txt`. Create `app/src/androidTest/java/com/isivoltpro/maginaolivo/DarkAgriculturalSurfacesTest.kt`.
**Interfaces:** Consumes `MaginaOlivoTheme(mode: AppearanceMode)`, `MoColors.current: MoPalette`, `MoSurfaceTokens`; existing real screen functions and UiPolishFixtures. Produces the same screen APIs with reactive colors; no data changes.

- [x] Write real-composable DARK fixtures for farm list/detail/editor, campaign, notebook, agenda and parcel editor. Measure normal text's strongest rendered foreground against the actual bitmap background, requiring contrast >=4.5:1. Use unmerged text nodes, not parent cards/icons. Capture screenshots and assert controls reachable. Photo overlays keep source pixels; chart legends remain visible. Avoid system-bar pixels.
- [x] Build test APK then run that class on emulator-5580. Expected: foreground contrast assertions FAIL on legacy olive/ink text over dark surfaces, without compile errors. Most app backgrounds already follow MoSurfaceTokens from #706; do not claim they were still cream.
- [x] Migrate colors by role; resolve palette outside Canvas/remember callbacks, pass colors to pure renderers. Keep photography/artwork reference colors fixed. Expected: same data/navigation APIs.
- [x] Rebuild, run class and JVM suite. Expected: all PASS. Commit the screen migration and RED/GREEN evidence.

### Task 2: Profile selector and production activation

**Files:** Create `feature/profile/AppearanceSettings.kt`; modify `ProfileScreen.kt`, `navigation/AppNavigation.kt`, `app/AppRoot.kt`, `app/AppearanceStore.kt`, `ui/theme/Theme.kt`. Tests: `AppearanceContractTest.kt`, new `AppearanceSettingsTest.kt`, `app/AppearanceStoreTest.kt`.
**Interfaces:** Consumes Task1 surfaces and `AppearanceStore.mode: StateFlow<AppearanceMode>`, `suspend setMode(AppearanceMode): Boolean`. Produces `@Composable AppearanceSettings(store: AppearanceStore)`, optional Profile screen slot and ProfileRoute store parameter. Production passes `compositionRoot.appearanceStore`.

- [x] Write UI tests: production Perfil exposes Apariencia, three labelled radio options, default Seguir sistema, selecting Oscuro updates actual window/background and summary; Claro restores light; cancel leaves stored mode. Failed write preserves prior mode and shows error. Test re-creation/reopening with persistent isolated preferences and SYSTEM night changes. Expected: absent selector / guarded LIGHT causes FAIL.
- [x] Implement accessible >=48dp options in existing sheet style, immediate confirmed persistence and visible failure. Collect Flow with lifecycle. Remove the temporary LIGHT guard and its obsolete guard assertion only after Task1 passes; retain persistence/system tests. Update system-bar icon contrast reactively, preserving host-window lifecycle.
- [x] Run targeted UI plus JVM suite. Expected: PASS, including failure and cancellation without agricultural writes. Commit activation and evidence.

### Task 3: Complete QA and integrate

**Files:** DARK-3 QA dossier/evidence, minimal CURRENT-STATE transition, tests/scripts from Tasks1/2.
**Interfaces:** Consumes both completed tasks. Produces one independently reviewed green PR and merged main; #707 physical QA remains explicit.

- [x] Run lintDevDebug/testDevDebugUnitTest/assembleDevDebug/assembleStagingDebug/assembleProductionDebug/assembleDevDebugAndroidTest with bounded local heap/workers. Expected: all PASS.
- [x] Run representative agricultural/appearance/navigation UI in LIGHT/DARK; capture 360/390/430dp/font1.3, modal/keyboard, + and bell, offline and map controls, gestures/three buttons. Verify source photos/tiles remain unfiltered. Expected: readable, reachable controls; no residual light surfaces outside source imagery/artwork.
- [ ] Record actual counts, hashes, limitations; commit and open one PR for DARK-3. Obtain exact-final-SHA independent review and all three remote checks. Expected: no blocking findings and three SUCCESS on the same HEAD.
- [ ] Merge only after those results. Verify metadata, update continuity evidence, then continue B3 maps/GPS. No physical Gate PASS claim.
