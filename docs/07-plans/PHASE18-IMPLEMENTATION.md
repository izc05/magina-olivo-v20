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
2. [ ] Persistence: add nullable import provenance columns to ParcelEntity, migration 11→12 (v11 is the CR-004 grove description on `main`),
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

### Reconciliation onto `main` (2026-09-24, owner decision: Claude continues Codex's slice)

- `codex/phase18-parcel-map` stayed a reference branch (it forked before design v3 and
  defined its own `MIGRATION_10_11`, which collides with the grove migration already on
  `main`). Its work was carried onto `codex/phase18-map-v2`, created from the latest `main`.
- Room: provenance columns now arrive with **`MIGRATION_11_12`** (database version 12). The
  backfill marks existing Catastro rows as `ES_CATASTRO` with their creation time, adds no
  version bump and leaves manual rows NULL; covered by
  `RoomMigrationTest.migration11To12BackfillsCatastroProvenanceAndKeepsManualRowsEmpty`.
- UI placed in the design-v3 structure: the **Mapa** action lives in the Parcelas sub-screen
  (`parcels-map`), the Farm map screen uses the Mo components and shows areas in hectares,
  and the Parcel detail shows its saved boundary in the **Datos** tab with the import date.
- Codex's CI evidence on the reference branch (run 35982709499: live import → force stop →
  airplane mode → offline reopen with the boundary rendered) must be repeated on this branch
  before Gate 18 can be proposed.

### CI evidence on `codex/phase18-map-v2`

| Check | Commit | Result |
|---|---|---|
| Phase 18 offline parcel map (live import → force stop → airplane mode → offline render) | `3b0a86b7` | PASS — run 35986351160 |
| Android CI `foundation` (lint, unit tests, all environments, APK) | `3b0a86b7` | PASS — run 35986482377 |
| Android CI `gate3-emulator` | `3b0a86b7` | 1 failure: `migration11To12…` could not find `12.json`, which CI exports during that same run and publishes as `29cd5fbc`; re-run pending with the schema present |

Gate 18 still needs the full suite green on the final commit and the owner's device check.

### Owner device feedback → farm map as the parcel hub (2026-09-24, approved "plan completo")

Feedback: only the aerial photo is useful to find land; the owner wants to search without
being at the farm, add several parcels at once per farm, and give hand-made parcels a
location. Decision (no root navigation change; entry stays Farm → Parcelas → Mapa):

- **Farm map modes:** *Mis parcelas* (view/select saved parcels) and *Añadir de Catastro*
  (tap the map, Catastro returns the parcels around the point, mark several, review names,
  incorporate all into this farm in one go). References already in the olivar are never
  offered again. Aerial photo is on by default; without connection only the saved
  boundaries are drawn.
- **Search without being there:** coordinates (Google Maps decimal, Spanish decimal commas,
  degrees/minutes/seconds with N/S/E/W/O), *Polígono y parcela* (province + municipality +
  polygon + parcel via Catastro `Consulta_DNPPP`, prefilled with the farm's place) and
  *Mi ubicación* (one position on demand; coarse/fine location permission requested only
  then; nothing tracked or stored).
- **Ubicar en el mapa** on a parcel without boundary: tap its Catastro parcel and
  `ParcelRepository.linkToRegistry` attaches reference, boundary, cadastral area and
  provenance, keeping the parcel's own name, managed area, grove data and history (version
  +1, outbox UPDATE, a reference owned by another parcel is refused). No schema change.
- The single-reference search remains reachable ("Tengo la referencia catastral").
- Tests: `MapSearchTest`, `CadastreLocatorTest` (query, Ñ, list/error answers, DOCTYPE),
  `FarmMapViewModelTest` (multi-import, taken references, link), `FarmMapScreenTest`,
  `OfflineFirstFarmRepositoryTest.handMadeParcelLinkedToCatastro…` and the live
  `Phase18PolygonParcelLookupTest` in the Phase 18 workflow.
