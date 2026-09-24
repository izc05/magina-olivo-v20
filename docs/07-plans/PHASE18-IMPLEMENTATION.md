# Phase 18 implementation plan

**Goal:** real parcel discovery/import and local maps after an airplane-mode restart.
**Architecture:** neutral domain candidate; bounded WFS and GML adapters; Room provenance;
MapLibre using local GeoJSON and an embedded style. Kotlin/Compose, Room, JTS, Proj4J.
**Spec:** `PHASE18-MAP-DESIGN.md`, approved by the owner on 2026-09-24.
**Execution:** inline in this worktree, preserving phase 17 and the pending physical gates.

## Constraints and review focus

- Keep UUIDs, transactional membership/outbox, and campaign snapshots.
- Never save a discovery result without confirmation or treat imagery as stored geometry.
- Reject unsupported CRS, malformed/oversized XML, invalid topology and stale query results.
- Handle files with multiple parcels, holes, multipart geometries and Canary Islands CRS.
- Local maps must load without remote style assets and survive activity lifecycle changes.
- Missing geometry/managed area must remain visibly unknown.

## Tasks

1. [ ] Domain/adapters: move candidate to `domain/registry/ParcelCandidate.kt`; preserve old
   alias for callers. Add `ParcelGeometry.kt` (JTS validity), `GmlParcelReader.kt` (secure DOM,
   Proj4J conversion), bounded `findNear(latitude, longitude)` and file import. Tests compare
   UTM/WGS84 coordinates, preserve holes, reject crossed rings and oversized/DTD input.
2. [ ] Persistence: add nullable import provenance columns to ParcelEntity, migration 10→11,
   and defaulted domain fields. Preserve existing rows and snapshots. Add migration tests
   and reopening assertions to the live import test; duplicate returns an existing UUID to UI.
3. [ ] Local renderer: `ParcelMap.kt`, embedded style with optional IGN imagery and cadastral
   WMS. Bind lifecycle, GeoJSON updates, hit selection and camera fitting. Test the actual
   map with saved geometry while networking is disabled, including a screenshot.
4. [ ] Screens: farm map and parcel map, selection sheet, reference/visual/file import paths
   feeding one confirmation form. Bounded explicit taps; no background broad queries.
   Compose checks for no auto-import, chosen farm, duplicate open and recoverable errors.
5. [ ] Validation: run Android lint/unit/build/emulator CI and dedicated live→restart→offline
   gate. Inspect failures and evidence, review branch, update docs, deliver APK and stacked PR.

## Verification commands

`gradle --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest`

Instrumented suite uses the existing Gate 3 workflow. Dedicated phase 18 workflow imports
official data online, forces application stop, enables airplane mode, then launches a separate
test invocation to reopen Room and render/select the saved polygon. Success requires both
test invocations and the captured map evidence, not merely an APK compilation.

## Execution ledger

- Started from phase 17 commit dcb63c11, design commit 3f339d67.
- Ruling: execute the approved scope in this session; user has already authorized continuing
  through phase 18. Physical verification stays pending until actual device evidence exists.
