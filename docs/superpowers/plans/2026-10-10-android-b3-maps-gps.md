# Android B3 Maps/GPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify and finish #711 B3: honest online cartography, contextual GPS, valid SIGPAC reference and safe local parcel work.
**Architecture:** Retain Room geometry as authoritative. Use the existing MapLibre raster/GeoJSON map and contextual one-shot LocationManager route. SIGPAC is an optional official reference overlay; it neither replaces app parcel identity nor silently imports agricultural declarations.
**Tech Stack:** Kotlin/Compose, MapLibre13.6.1, Android API35, Gradle9.4.1/JDK17.
**Spec:** Owner issue #711 B3, docs/00-master/CURRENT-STATE.md, RC1.2 Product Lock and existing Gate18 contract. DARK-3 must merge before this plan's production work starts.

## Global Constraints

- One productive branch/PR, based on integrated DARK-3 main. Independent exact-SHA review and all three checks before merge.
- Preserve all saved work, Room/photos/preferences and original AVD; no physical-device clear or uninstall.
- No invented official attributes, geometry or current-year declarations. A SIGPAC recinto is not Catastro/app parcel identity.
- Keep IGN/PNOA pixels unchanged,256px tiles; retain native overlay order, camera padding and offline NONE behavior.
- Reference overlays are explicit/off by default and excluded from NONE. Existing Catastro selection/import remains its own provider.
- B3 evidence is emulator/live-service evidence; field GPS and physical Gate21 remain owner checks.
- No Web, OCR activation, schema/Auth/sync, Gate22+ or unrelated statistics work.

## Review Focus

- A partial remote raster error must be visible without erasing the saved polygon or blocking manual work.
- Denied/off/no-fix GPS must leave manual search and parcel registration usable; never turn stale/coarse data into a precise search.
- Native location point must reflect the simulated coordinates with GeoJSON longitude first and camera movement inside the usable viewport.
- SIGPAC attribution/provenance must stay readable and honest about source/date; service metadata does not establish Android integration.
- At360/390/430dp and font1.3, header, sheet, keyboard, map controls and selected geometry must remain reachable.

## Read-only preflight already retained

`artifacts/696-e2e/b3-official-services-preflight-20261010/`: official IGN/PNOA/Catastro capabilities200. MAPA directory publishes `https://sigpac-hubcloud.es/wms`; its capabilities list queryable `AU.Sigpac:recinto` and EPSG:3857. Actual GetMap512px and GetFeatureInfo at37.636,-3.480 returned200 and recinto4, polygon4/parcel21,Huelma. Reported use is PS-PASTIZAL; do not relabel it as olives. Layer has no explicit current-year guarantee. IGN/PNOA GetTile17 returned504 twice on the host request path; do not infer a global provider outage.

Actual preflight on owned API35 emulator5580, DARK/font1.3/393dp: production route requested permission only after Mi ubicación; denial kept typed coordinates usable and the explicit permission action reopened Android's dialog. A shell-owned temporary GPS test provider delivered37.636,-3.480/hAcc10m; platform dump and native blue point agree. Console geo fix returnedOK but did not deliver the intended coordinates, so that console result is not a GPS PASS. All190 preflight app files restored by hash; mock provider removed, shell app-op reset to default and app location permissions revoked back to their prior state. No primary agricultural records changed. Evidence: `artifacts/696-e2e/b3-gps-native-preflight-20261010/`.

Two genuine B3 observations need regression coverage: the denial panel obscures Encuadrar mis parcelas, and opening search widens Cerrar búsqueda enough to wrap the Mapa control. IGN's native base renders at overview zoom but detailed tile requests time out without any user-visible failure notice. Preserve screenshots/logs as baseline evidence; do not claim these are fixed or a physical Gate21 PASS.

MapLibre's installed13.6.1 API and official documentation expose `MapView.OnTileActionListener.onTileAction(TileOperation, x,y,z,wrap,overscaledZ,sourceID)`, including `TileOperation.Error`. Use source-specific events, rather than assuming an empty-looking photograph means failure. Primary API: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/-on-tile-action-listener/index.html

### Task 1: Native source errors and safe offline path

**Files:** Modify feature/maps/ParcelMap.kt and FarmMapScreen.kt. Create a focused native error UI test; extend FarmMapScreenTest only for meaningful fallback behavior. Keep diagnostic/probe scripts in artifacts.
**Interfaces:** Retain `ParcelMap(...)` existing callers; append optional `onTileError: (String)->Unit = {}`. Native view lifetime owns the tile listener, current callback uses rememberUpdatedState, and UI updates dispatch safely to main. Farm screen offers the existing MapBase.NONE from an explicit failure message; no automatic geometry mutation.

- [ ] Reproduce real failed raster request in native MapLibre under isolated QA. Determine event sourceID and preserve failed output before implementation. Use a bounded unavailable test endpoint/resource transform only in tests; restore process-global test transforms afterward.
- [ ] Write a failing native assertion that the raster failure is reported while the saved local polygon remains rendered/selectable. Write fallback action assertion: NONE chosen, no remote raster source, geometry unchanged.
- [ ] Add the listener and a short source-appropriate message with explicit Solo parcelas fallback. Do not turn one tile error into a claim that all imagery is unavailable. Clear stale error state on deliberate layer change/retry; remove listener on disposal.
- [ ] Run focused JVM/map/native UI checks. Inspect actual fallback/saved-polygon PNG in LIGHT/DARK. Keep existing passing geometry/label/camera coverage unless new changes invalidate it.

### Task 2: Contextual GPS and valid SIGPAC reference

**Files:** ParcelMap.kt, FarmMapScreen.kt, map JVM tests; new focused contextual-permission and native GPS UI tests. CurrentLocation.kt only if an actual regression is reproduced. No Room/domain identity changes.
**Interfaces:** Append `sigpacLines: Boolean=false` to map/style APIs without changing existing positional callers. Optional512px WMS source `AU.Sigpac:recinto`, EPSG:3857 `{bbox-epsg-3857}`, lies beneath authoritative saved parcels; include FEGA/MAPA attribution only when enabled. Farm layer menu toggles the reference explicitly and disables online overlays in NONE.

- [ ] Add RED style/UI tests for explicit SIGPAC toggle, correct official source/credit, unchanged saved features and absence of all online overlays under NONE.
- [ ] Add the optional reference raster and concise honest label. Keep Catastro lookup/import separate; no recinto attributes imported into saved parcel records.
- [ ] Validate live native GetMap rendering or record a concrete provider blocker. Preserve raw request/UTC/hash/capabilities and native screenshot; GetCapabilities alone is insufficient.
- [ ] On owned isolated QA, run real contextual permission denial and fresh simulated GPS. Assert no prompt until Mi ubicación, point/camera correspondence and useful denial/manual path. Existing quality policy covers stale/poor/coarse fixes; add only missing meaningful regressions. Never use physical-device permission changes.
- [ ] Inspect actual map/header/panel/keyboard at360/390/430dp,font1.3 and framing controls. If a real failure appears, reproduce RED and make a minimal fix before proceeding.
- [ ] Reproduce the observed denied-GPS panel/control overlap and search-row wrapping. Position native map controls within the measured usable viewport; suppress the introductory guide while concrete search/error assistance is active if needed. Keep the full search action description while its visible close label fits the row.

### Task 3: Review/integration and B4 handoff

**Files:** B3 QA dossier/evidence and CURRENT-STATE continuity. Update this plan's checked steps only from actual results.
**Interfaces:** One reviewed green B3 PR; owner physical map/GPS script remains explicit. B4 starts after B3 integration, then B5 versioned main APK.

- [ ] Run bounded lint/JVM/all three APK/test APK foundation once after final code. Reuse unaffected DARK-3 evidence; run changed map/permission/native cases and offline preservation checks.
- [ ] Record actual counts, source availability, provenance, screenshot inspection and physical checklist. Separate service response from Android integration and physical acceptance.
- [ ] Commit/push, request independent exact-SHA review, resolve reproduced findings and obtain foundation/gate3-emulator/gate3-evidence SUCCESS on the final SHA.
- [ ] Merge, verify main metadata/tree and carry owner queue forward to B4. Do not claim final candidate APK or physical Gate21 complete here.
