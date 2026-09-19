# Phase 5 — Local database foundation

**Decision:** PASS  
**Reviewed:** 2026-09-19  
**Validated code commit:** `ad61d6f4`  
**Integration PR:** [#199](https://github.com/izc05/magina-olivo-v20/pull/199)

## Scope

Gate 5 establishes Room as the durable local source of truth without exposing production Farm UI before Phase 6. The validated implementation provides:

- Room 2.8.5, KSP and committed v1/v2 schema snapshots;
- explicit `MIGRATION_1_2`, with no destructive fallback;
- 13 core tables covering ownership, farms, parcels/memberships, campaigns, activities, harvests, expenses, documents, weather cache, alerts and sync outbox;
- client UUIDs and lossless converters for `Instant`, `LocalDate` and synchronization enums;
- common soft-delete, local-version and sync metadata;
- DAO/Flow boundaries and a `FarmRepository` contract;
- atomic Farm create/archive plus durable outbox operations;
- DEV-only deterministic, idempotent fixtures excluded from production flavors;
- composition-root wiring with no direct Room dependency from Compose/navigation.

## Automated evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Lint, 22 unit tests, instrumented-test compilation and DEV/STAGING/PRODUCTION debug builds | PASS | [Android CI run 35465669062](https://github.com/izc05/magina-olivo-v20/actions/runs/35465669062) |
| Full API 35 instrumentation | PASS — 28/28 | Run 35465669062, artifact `gate3-emulator-evidence` (`10590189323`) |
| Room repository tests with airplane mode enabled and Wi-Fi disabled | PASS — 3/3 | Run 35465669062, `airplane-mode.txt` and `offline-room-instrumentation.txt` |
| Independent emulator reproduction | PASS — 28/28 plus offline 3/3 | [Evidence run 35465670678](https://github.com/izc05/magina-olivo-v20/actions/runs/35465670678), artifact `magina-olivo-gate3-emulator-evidence` (`10591126864`) |
| Crash buffer after cold starts | PASS — 0 bytes | Both emulator evidence artifacts |
| Installable DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10591491019`) |

DEV APK verification:

```text
file: app-dev-debug.apk
bytes: 12752285
sha256: E70E8662DB46B963B87D5A432230EDB34CA76CCFBD2D52C95839AE8CE71D665A
```

Local verification used Gradle 9.4.1 and the Android Studio JBR:

```text
lintDevDebug
testDevDebugUnitTest
assembleDevDebug
assembleStagingDebug
assembleProductionDebug
assembleDevDebugAndroidTest
```

Result: 170 tasks, `BUILD SUCCESSFUL`.

## Persistence behavior covered

- a v1 workspace survives the explicit migration to v2;
- Room validates every v2 core table after migration;
- repository create writes normalized local state and a CREATE outbox row atomically;
- data survives database close/reopen;
- archive hides the Farm from active Flow reads while retaining its tombstone;
- archive increments the local version and writes a DELETE outbox row;
- a foreign-key failure rolls back both domain and outbox effects;
- the DEV fixture is deterministic, idempotent and does not enqueue synchronization;
- the repository proof runs with Android airplane mode enabled and Wi-Fi disabled.

## Defects found and closed

The first API 35 run exposed two valid failures that local compile-only verification could not detect:

1. a test compared Room's persisted `Long` version with an `Int` expectation;
2. Navigation selected `kotlinx-serialization-core` 1.7.3 while Room Migration's schema reader used JSON 1.8.1, producing `AbstractMethodError` at runtime.

The assertion now uses the stored type and a serialization BOM aligns both production and test runtime classpaths at 1.8.1. Two independent emulator runs then passed. The strict instrumentation parser retained from Gate 4 correctly prevented a false green.

## Open non-blocking follow-up

- WorkManager and remote synchronization remain intentionally deferred to their approved gate.
- Attachment binary copying is deferred; Phase 5 stores only the metadata contract.
- Compose test-rule v1 deprecation warnings remain a QA/toolchain maintenance item.
- Real-device install and persistence smoke remains mandatory before RC distribution.

## Gate decision

```text
GATE 5 = PASS
```

Phase 6 Farm implementation may begin. `main` remains unchanged until the owner authorizes the stacked integration chain.
