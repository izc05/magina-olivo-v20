# Mágina Olivo UX audit implementation plan

> **For agentic workers:** Execute the tasks in order. The user-requested audit is the specification and authorizes native inline execution.

**Goal:** Audit the current Android 0.3.0 farmer workflow on an emulator and make narrowly scoped changes that reduce avoidable choices while preserving all existing data and actions.

**Architecture:** Keep the five approved roots, Compose components, Room/repository contracts and screen routes. Base fixes on current emulator evidence and existing screen tests; do not revive UI from older branches.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Android instrumentation tests, adb.

**Spec:** User's UX audit request in this conversation; normative constraints in `AGENTS.md`, `docs/00-master/RC1.2-PRODUCT-LOCK.md`, CR-007 and the UX-246 gate checklist.

## Global Constraints

- Preserve `Inicio · Mi Campo · Cuaderno · Avisos · Perfil`.
- Keep the Mágina Olivo cream/olive visual language, photographs and existing feature capability.
- Do not remove stored fields; optional inputs may be folded under a clearly labelled control.
- Keep field work usable without network access.
- Do not merge into `main`.

## Review Focus

- Empty, loading and error states remain distinct and actionable.
- Farm form fields survive text entry, scrolling, keyboard dismissal and save.
- Back navigation from sheets/forms returns to the expected root.
- The current Android 0.3.0 flow works after process recreation and offline operation where supported.
- Large touch targets and readable text remain available in portrait and enlarged-font configurations.

---

### Task 1: Capture current baseline and exercise the main flows

**Files:**
- Evidence: `artifacts/ux-audit/`
- Review: existing tests under `app/src/androidTest/java/com/isivoltpro/maginaolivo/`

- [x] Record APK/HEAD/device details and current screens from clean app data.
- [ ] Walk onboarding, roots, empty farm state, farm/parcel/campaign, Cuaderno, activities, expenses/production, Avisos and Perfil.
- [ ] Record available/partial/unavailable auth, offline, map and machinery flows.
- [ ] Compare the prior static audit findings to the current app; keep only reproducible findings.

Progress note: the original 0.3.0 APK was visually inspected through onboarding, Inicio, Mi Campo and the new-farm sheet. The complete interactive walk is blocked because the connected Android device disappeared while the full instrumentation suite was running; see `docs/06-testing/UX-AUDIT-0.3.0-2026-09-27.md`.

### Task 2: Simplify the first-farm path

**Files:**
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/feature/farms/FarmScreens.kt`
- Test: `app/src/androidTest/java/com/isivoltpro/maginaolivo/FarmScreensTest.kt`

- [x] Add an instrumentation assertion that the empty list offers exactly one create-farm action while populated lists retain `Añadir finca`.
- [x] Run the focused test and confirm it fails on the duplicate empty-state action.
- [x] Add an assertion that only the farm name is initially shown and `Más detalles` reveals every existing optional field.
- [x] Run the focused test and confirm the advanced-fields assertion fails before implementation.
- [x] Hide `Añadir finca` and aggregate zero cards only when there are no farms; retain the existing empty-state CTA.
- [x] Fold municipality, province, description and notes behind `Más detalles`, preserving values and save behavior.
- [ ] Re-run the extended Farm screen round-trip test after device recovery. The original 5/5 focused suite passed; the added assertions compile but could not be run.

### Task 3: Re-run the farmer journey and project validation

**Files:**
- Evidence: `artifacts/ux-audit/`
- Report: `docs/08-audits/UX-AUDIT-END-TO-END-2026-09-27.md`

- [ ] Run the farm → parcel → campaign → activity registration → Diario/reopen journey on emulator.
- [ ] Check the available irrigation, treatment, pruning, soil, harvest, weighing, expense, machinery, map, reminder, profile and sign-out paths; label unsupported flows accurately.
- [ ] Re-test keyboard/scroll/Back and cold reopen from a clean app state.
- [x] Run unit tests (208 pass), lint (36 warnings, 1 hint), and DEV build.
- [ ] Complete Android instrumentation suite. It started 276 tests but stopped after 8 when `emulator-5554` went offline.
- [x] Record exact results and remaining blockers; keep the branch unmerged.
