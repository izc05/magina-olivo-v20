# Gate 6 — Parcels slice evidence

**Slice decision:** PASS  
**Gate 6 overall:** FAIL — Campaign slice remains  
**Reviewed:** 2026-09-20  
**Validated code commit:** `ee89b9f7`  
**Stacked PR:** [#201](https://github.com/izc05/magina-olivo-v20/pull/201)

## Delivered behavior

- offline Parcel create, edit, archive and restore;
- stable app UUIDs, optional cadastral fields and explicit `MANUAL`/`CATASTRO` provenance;
- non-destructive, time-bounded Farm membership history;
- optional positive area and retained GeoJSON polygon geometry;
- Room-backed active/archived lists and detail Flows;
- atomic local mutations plus deterministic Parcel outbox intents;
- production Farm detail Parcel list, manual editor and Parcel detail route;
- loading, empty, success and recoverable error states;
- truthful unknown values without fabricated cadastral or agronomic data;
- restart, rollback, validation, ViewModel, Compose and navigation coverage.

MapLibre and live Catastro lookup are intentionally excluded from this slice and remain Gate 7 work.

## Automated evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Lint, 30 unit tests, instrumented-test compilation and DEV/STAGING/PRODUCTION debug builds | PASS | [Android CI run 35506482946](https://github.com/izc05/magina-olivo-v20/actions/runs/35506482946) |
| Full API 35 instrumentation | PASS — 42/42 | Run 35506482946, artifact `gate3-emulator-evidence` (`10603694929`) |
| Room repository tests with airplane mode and Wi-Fi disabled | PASS — 7/7 | Run 35506482946, `offline-room-instrumentation.txt` |
| Emulator crash buffer | PASS — 0 bytes | Artifact `gate3-emulator-evidence` (`10603694929`) |
| Installable DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10604750368`) |

DEV APK verification:

```text
file: app-dev-debug.apk
bytes: 13523742
sha256: EE3748DAF7B420F320223A89C7CD72CAD1153BFB044D95CC03543E3587D2EB6D
```

## Defect found and closed

The end-to-end Parcel flow initially timed out after creation. A semantic-tree capture proved that the persisted Parcel row existed but was outside the Farm detail viewport (`bounds=[0,0,0,0]`), so the coordinate click never reached it. The test now scrolls the real lazy/scroll container to the row before clicking. No navigation test was disabled and the final complete emulator run passed all 42 tests.

## Remaining Gate 6 work

- production Campaign lifecycle, active selection, close/reopen and historical summaries;
- combined Farm → Parcel → Campaign navigation and persistence proof.

```text
PARCELS SLICE = PASS
GATE 6 = FAIL (IN PROGRESS)
```
