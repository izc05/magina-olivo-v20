# Phase 3 — Gate 3 visual and accessibility validation

**Decision:** PASS  
**Reviewed:** 2026-09-19  
**Validated commit:** `6f37b736`  
**Integration PR:** [#197](https://github.com/izc05/magina-olivo-v20/pull/197)

## Scope

Gate 3 validates the approved Compose reference implementation. It does not certify production navigation, persistence or live agricultural data; those begin in later phases.

The reviewed package covers:

- canonical Mágina Olivo colors, type, spacing and reusable components;
- six-page onboarding and Home;
- Mi Olivar, farm, parcel, campaign, register, harvest, expenses/documents, Map/Catastro, weather/market and OCR review references;
- compact, common and large phone widths;
- 130% font scaling;
- Android semantics and representative non-happy states;
- repeatable emulator capture and an installable DEV APK.

## Automated evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Android foundation: lint, unit tests, instrumented-test compilation, DEV/STAGING/PRODUCTION debug builds | PASS | [Android CI run 35435717081](https://github.com/izc05/magina-olivo-v20/actions/runs/35435717081) |
| Full Android instrumentation on API 35 | PASS — 15/15 | Android CI run 35435717081, artifact `gate3-emulator-evidence` (`10582204319`) |
| Independent Gate 3 reproduction | PASS — 15/15 | [Gate 3 run 35435717079](https://github.com/izc05/magina-olivo-v20/actions/runs/35435717079), artifact `magina-olivo-gate3-emulator-evidence` (`10582315343`) |
| Crash buffer after cold starts | PASS — 0 bytes | Both emulator evidence artifacts |
| DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10581739628`) |

DEV APK verification:

```text
file: app-dev-debug.apk
bytes: 12024928
sha256: 2BC0C85EA58657A52A2A10273DED4148B8111285061C844B55DB17269C968B4A
```

Local verification also passed with Gradle 9.4.1 and JDK 21:

```text
lintDevDebug
testDevDebugUnitTest
assembleDevDebug
assembleStagingDebug
assembleProductionDebug
assembleDevDebugAndroidTest
```

The authoritative CI environment uses JDK 17.

## Rendering matrix

| Variant | Onboarding | Home | Parcel detail | OCR review | Result |
| --- | --- | --- | --- | --- | --- |
| 360 dp / font 100% | Captured | Captured | Captured | Captured | PASS |
| 393 dp / font 100% | Captured | Captured | Captured | Captured | PASS |
| 480 dp / font 100% | Captured | Captured | Captured | Captured | PASS |
| 393 dp / font 130% | Captured | Captured | Captured | Captured | PASS |

Each Home capture is taken only after its visible header/navigation is present in the accessibility tree. OCR evidence includes a geometry assertion that the extracted amount remains within the ticket preview at every tested configuration.

## Defects found and closed

1. The original PR #195 emulator job used a multiline inline workflow command that `/usr/bin/sh` truncated after successful instrumentation. The validated script from PR #196 is now versioned and called by both workflows.
2. Android CI did not build the app and test APKs before invoking the evidence script. The Gate 3 candidate is now built explicitly.
3. Home could be captured during the onboarding-to-Home recomposition. Capture now waits for visible Home semantics.
4. The OCR ticket used a fixed 210 dp height and clipped `2.850 kg` at 130% font scale. It now uses an adaptive minimum height and has a regression assertion.
5. The API 35 headless emulator can display a `Quickstep isn't responding` launcher overlay during cold boot. The script dismisses only that launcher-specific overlay; an ANR from Mágina Olivo is not ignored.

## Accessibility review

- Visible onboarding and Home controls are exposed through Android semantics.
- Compact width and 130% font evidence was reviewed without blocking text clipping after the OCR fix.
- The approved contrast corrections from PR #195 are included.
- Critical controls continue to use reusable components with minimum touch heights.
- Animations are disabled in emulator validation, so evidence is not timing-dependent.

## Open non-blocking follow-up

- Compose test rule v1 reports a deprecation warning; migration to the v2 test API can be handled in a later QA/toolchain maintenance slice.
- Emulator cold-start `gfxinfo` contains one initialization frame and is not a performance benchmark. Production performance work remains in its dedicated phase.
- Installation and exploratory accessibility review on a real mid-range handset remains mandatory before RC1 distribution.

No open item above blocks the Phase 3 visual/accessibility contract.

## Gate decision

```text
GATE 3 = PASS
```

Phase 4 navigation-shell implementation may begin. `main` is unchanged until the owner authorizes merging PR #197.
