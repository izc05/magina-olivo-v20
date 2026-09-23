# Phase 15 — Machinery

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-23
**Base commit:** `a701d2b9` (`main`, Phase 14 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Validated commit:** `ce46234b` (code `1c30daa0` + the compiler-exported `9.json`)
**Plan:** `docs/07-plans/PHASE15-MACHINERY.md`

> Every CI, emulator and artifact figure below is copied from a real run.

## Gate 15

Machinery adds value without making activities mandatory or complex.

| Clause | Proof |
| --- | --- |
| Activities are never made mandatory | `MachineryContractTest.anActivityNeedsNoMachineAndNoHours` (an Activity without machines saves as before; a machine can be named without hours) |
| Nothing becomes complex | `aMachineNeedsOnlyAName` (a name is enough; duplicates refused), `machinesAreChildrenOfTheActivityWithOneIntent` (one Activity intent and version; naming a machine never changes the machine), `inconsistentHoursAreRefusedWithoutWritingTheActivity` |
| Machinery adds value | `aMachineShowsTheActivitiesThatUsedItAndOnlyRecordedHours` (uses listed; recorded hours summed, uses without hours counted apart), `aRetiredMachineStaysOnPastActivitiesButCannotBeChosenAgain`, `machinesAndTheirUseSurviveARestart` |

Also `RoomMigrationTest.migration8To9AddsEmptyMachineryTables`. JVM `MachineRulesTest` was
compiled and run locally with the Kotlin 2.2.20 compiler before any CI run — 4/4 passed.

## Evidence

| Check | Workflow | Result | Run |
| --- | --- | --- | --- |
| Lint, unit tests, three debug builds, instrumented compilation | `foundation` | PASS | [Android CI run 35864581017](https://github.com/izc05/magina-olivo-v20/actions/runs/35864581017) on `ce46234b` (dispatch) |
| Full API 35 instrumentation | `gate3-emulator` | PASS | same run, job `107192829884`, artifact `10752371902` |
| Independent emulator run | `gate3-evidence` | PASS — `instrumentation_rc=0`, `offline_room_instrumentation_rc=0` | [Gate 3 Android Emulator Evidence run 35864584360](https://github.com/izc05/magina-olivo-v20/actions/runs/35864584360), artifact `10751961974` |
| Emulator crash buffer | `gate3-evidence` | EMPTY — `0 evidence/crash.txt` | run `35864584360` |
| Installable DEV APK | `magina-olivo-dev-debug`, artifact `10752556533` | 34 490 571 bytes (zip) | `sha256:fe7185fc2c4e24bc1e29100395417412e9d499728f5117194e950dd0f2f724f7` |
| Suite size | same code on `1c30daa0`, before `9.json` existed | 158 instrumented tests, 157 passing — the only failure was the migration test's missing `9.json`; 95/95 in airplane mode, including `MachineryContractTest` 7/7 | run `35863676873` |
| Hand-written migration vs compiler export | comparison of `MIGRATION_8_9` with `9.json` | 2 tables and 2 indices identical | `9.json` identityHash `92afe124dff360278e2e7434eda9d52e` |

## Known gaps

- The machinery screens and the optional Activity section are covered by the repository
  contract and JVM rules; there is no Compose UI test of them yet.
- Real-device check of the machinery flow is still to be recorded.
