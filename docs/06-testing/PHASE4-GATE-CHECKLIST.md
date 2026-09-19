# Phase 4 — Production navigation shell

**Decision:** PASS  
**Reviewed:** 2026-09-19  
**Validated code commit:** `483fc145`  
**Integration PR:** [#198](https://github.com/izc05/magina-olivo-v20/pull/198)

## Scope

Gate 4 converts the approved reference catalogue into one production navigation shell. It intentionally does not add Room, agricultural persistence or remote services.

The validated shell provides:

- one `NavHost` and one shared bottom bar;
- frozen roots `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`;
- root selection derived from the navigation back stack;
- single-top root navigation with saved-state restoration;
- contextual Register sheet before entering a data flow;
- nested routes for farm, parcel, campaign, analytics, Map/Catastro, weather, harvest, expenses/documents and OCR review;
- persisted first-run onboarding completion;
- a DEV-only route to the component catalogue;
- explicit placeholders for Calendario and Perfil without inventing later-phase data.

## Automated evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Lint, 17 unit tests, instrumented-test compilation and DEV/STAGING/PRODUCTION debug builds | PASS | [Android CI run 35449235415](https://github.com/izc05/magina-olivo-v20/actions/runs/35449235415) |
| Full API 35 instrumentation | PASS — 24/24 | Run 35449235415, artifact `gate3-emulator-evidence` (`10586108771`) |
| Independent emulator reproduction | PASS — 24/24 | [Evidence run 35449237218](https://github.com/izc05/magina-olivo-v20/actions/runs/35449237218), artifact `magina-olivo-gate3-emulator-evidence` (`10586228729`) |
| Crash buffer after cold starts | PASS — 0 bytes | Both emulator evidence artifacts |
| Installable DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10585572737`) |

DEV APK verification:

```text
file: app-dev-debug.apk
bytes: 12403759
sha256: 975AACA096832951B1BC3F98929B49877103DFBA764531A8A3832F9D5A532805
```

Local verification used Gradle 9.4.1 and the Android Studio JBR:

```text
testDevDebugUnitTest
assembleDevDebugAndroidTest
lintDevDebug
```

The authoritative CI also builds every debug environment with JDK 17.

## Navigation behavior covered

- onboarding can be skipped and remains completed after Activity recreation;
- every frozen root is reachable and exposes the correct selected state;
- back from a non-start root returns to Inicio;
- the active root survives Activity recreation;
- revisiting a root does not leave a duplicate destination on the back stack;
- Register opens its context sheet, can be cancelled without navigation and can enter a register flow;
- farm detail is reachable from Mi Olivar and back returns correctly;
- the component catalogue is reachable from Perfil only in DEV;
- route classification and invalid nested identifiers are unit-tested.

## Defect found and closed

The first emulator run exposed a false-positive CI condition: Android's `adb am instrument` can return process code 0 even when JUnit reports `FAILURES!!!`. Four navigation tests also inherited a restored `NavController` stack because preferences were cleared after `ActivityScenario` had already launched.

The fix:

1. clears first-run state through an outer JUnit rule before the Activity launches;
2. validates the instrumentation result text for `OK (N tests)` and rejects JUnit failures, runner failures and process crashes;
3. applies the same strict validation to each screenshot test invocation.

The corrected workflow was then reproduced independently with 24/24 tests. A green emulator job can no longer hide this class of test failure.

## Open non-blocking follow-up

- No external deep link is registered yet because no unauthenticated, stable record URL has been approved. Route builders are centralized so later typed links can be added without a second graph.
- Compose test rule v1 still emits its known deprecation warning; migration to v2 remains a QA/toolchain maintenance item.
- Real-device install/back/TalkBack smoke remains mandatory before RC1 distribution.

## Gate decision

```text
GATE 4 = PASS
```

Phase 5 local-database implementation may begin. `main` remains unchanged until the owner authorizes the integration PR chain.
