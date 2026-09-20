# Gate 6 — Farms slice evidence

**Slice decision:** PASS  
**Gate 6 overall:** FAIL — Parcel and Campaign slices remain  
**Reviewed:** 2026-09-20  
**Validated code commit:** `a2d2d475`  
**Stacked PR:** [#200](https://github.com/izc05/magina-olivo-v20/pull/200)

## Delivered behavior

- first-run local workspace bootstrap;
- offline Farm create, edit, archive and restore;
- Room-backed active/archived list and detail Flows;
- atomic domain mutation plus outbox intent;
- truthful parcel-count, area and active-campaign projections;
- loading, empty, success and recoverable error UI states;
- 1, 20 and 50-Farm navigation/list coverage;
- durable cover-photo selection with persisted Android URI permission;
- local document metadata and document/Farm outbox operations in one Room transaction;
- canonical production Mi Olivar/Farm routes, with the old visual references retained only for DEV/reference tests.

## Automated evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Lint, 27 unit tests, instrumented-test compilation and DEV/STAGING/PRODUCTION debug builds | PASS | [Android CI run 35480574641](https://github.com/izc05/magina-olivo-v20/actions/runs/35480574641) |
| Full API 35 instrumentation | PASS — 37/37 | Run 35480574641, artifact `gate3-emulator-evidence` (`10595279045`) |
| Room repository tests with airplane mode and Wi-Fi disabled | PASS — 6/6 | Run 35480574641, `offline-room-instrumentation.txt` |
| Emulator crash buffer | PASS — 0 bytes | Artifact `gate3-emulator-evidence` (`10595279045`) |
| Installable DEV APK | PASS | Artifact `magina-olivo-dev-debug` (`10595827125`) |

DEV APK verification:

```text
file: app-dev-debug.apk
bytes: 13441822
sha256: 0AD4F97559B9B108027C389C54E31FAA4874CE0357E5A786F039A8D4C05AE01C
```

## Defects found and closed

The first emulator run exposed four test defects: JUnit lifecycle methods returning `Boolean`, an invalid lazy-list scroll strategy, a double `setContent`, and unsynchronized Back handling. The second run left one real UX issue: the Farm editor could close while retaining IME focus, so Back dismissed the keyboard instead of returning from detail. Production now clears focus and hides the keyboard after a successful save; the final run passed all tests.

## Remaining Gate 6 work

- production Parcel lifecycle and Farm membership history;
- production Campaign lifecycle, active selection, close/reopen and historical summaries;
- combined Farm → Parcel → Campaign navigation and persistence proof.

```text
FARMS SLICE = PASS
GATE 6 = FAIL (IN PROGRESS)
```
