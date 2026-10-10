# #711 B3 — Native maps, GPS and SIGPAC reference

Status: implementation/verification in progress; no merge, final candidate APK or physical Gate21 PASS yet.
Base: integrated DARK-3 `0a528d5fe1ea6b48e1d7c3acd3f03d4d6b6aa196`.
Plan: [B3 implementation plan](../superpowers/plans/2026-10-10-android-b3-maps-gps.md).

## Behavior and scope

Native MapLibre remote tile errors now produce a source-specific notice. The explicit **Solo parcelas** action selects NONE while saved geometry remains selectable. Disabled-source late callbacks are ignored; native listeners follow the view lifetime. One failed tile does not imply a global outage.

SIGPAC recintos are an explicit, default-off reference beneath saved parcels, with FEGA/MAPA attribution. The official `AU.Sigpac:recinto` WMS uses 512px transparent EPSG:3857 tiles. NONE excludes every remote raster, including radar. IGN/PNOA remain 256px with their original pixels and paints. Catastro lookup/import, Room geometry, parcel identity and GPS quality policy are unchanged.

IME padding shrinks the map to the actual keyboard area. Measured header/bottom panel insets position zoom/frame controls within the usable map area. Search shows a compact Cerrar label with its full accessibility description. Concrete search/error assistance suppresses the introductory guide.

Independent review of `6f47a7069b4ca0677110203737399391adaba8f6` found one Important/P2: a requested point still used the whole map centre beneath the search header with the IME open. The correction applies absolute measured camera padding after valid dimensions; only a new focus token sets target/zoom. Viewport changes preserve the user's later pan, zoom or parcel framing.

## Reproduced failures and verification

- Task1: real native TileOperation.Error for source base before the missing-notice RED. Final LIGHT/DARK native tests passed 2/2 in9.125s, with native saved-feature hit tests, NONE absence of remote sources and four inspected screenshots.
- Task2 style RED:3 tests/2 failures (missing SIGPAC source and radar retained in NONE). Initial GREEN:3 tests/0 failures.
- Task2 production Scaffold UI RED:3/3 failures in11.546s. Denial panel covered the frame control; search wrapped the map button; SIGPAC toggle was absent. Initial GREEN:3/3 in14.193s.
- Live native integration exposed a genuine WMS protocol error:1.1.1/SRS returned HTTP200 **XML**, not an image. The preserved XML reports MissingParameterValue. A targeted version contract RED failed1/3; correction to **1.3.0/CRS** passed the build and targeted JVM checks in47s.
- Actual MainActivity/MapLibre SIGPAC rendering PASS at2026-10-10T06:44:53Z:22,974 magenta reference pixels, FEGA/MAPA visible, platform test GPS37.636,-3.480/hAcc10m. Screenshot SHA256 `9276296c257997edba727815a41145ba9653ec012be578b59b80f522a1c6fe39`. IGN base had partial failures and the new notice/fallback was visible. This proves reference rendering; precise point/camera evidence is separately retained below.
- Three widths360/390/430dp,font1.3:15 directed native/viewport/reference executions passed,18 PNG retained. Inspection found a two-line NONE button at360dp. A new assertion reproduced192px versus118px row height; visible Parcelas/full accessible Solo parcelas corrected it. The corrected15 executions/18 inspected PNG passed again.

- Real keyboard inspection found Alejar bottom1579px covered by IMEtop1507px. The native window/UI bounds comparator reproduced RED; root IME padding compiledPASS1m40. Actual MainActivity keyboard QA passed360/390/430dp at07:06:41Z, six inspected PNG: controls remain above the keyboard or hide when space is insufficient, and frame control returns on dismissal. First QA retry failed because display recreation left stale bounds; that raw failure is retained, and the driver now waits for the observed current width before tapping. No production assertion was relaxed.

## Contextual GPS and data preservation

The new empty-farm native regression reproduced the review finding at360dp/font1.3: projected target y816.625 versus search header bottom920, failing in24.197s. Its log and keyboard screenshot are retained. Earlier attempts failed before this assertion (SystemUI boot ANR, then premature rendered-feature assertions) and are excluded from the functional RED. The owned emulator's SystemUI dialog was dismissed with Wait; native event logs show startup SystemUI/Phone/Gboard ANRs, not an app ANR.

The first padding correction passed the bounds assertions but timed out querying the actual location feature. A native diagnostic found zero source features after the fast IGN→NONE style transition; explicitly refeeding the identical GeoJSON made the point render at its projected position in23.818s. That deliberate diagnostic failure is retained and its reinjection removed from the final test. The production fix keys both location and saved geometry effects to the newly loaded Style object, avoiding a coalesced true→false→true readiness flag. The final regression also requires the actual point to survive a local DARK→LIGHT style transition without changing location.

Corrected build passed in1m44s; the directed point/style/pan/frame case passed in29.828s. Final360/390/430dp,font1.3 matrix passed18 executions (six cases per configuration), with24 inspected native PNG. Actual point is visible above the IME, later pan/zoom and frame are retained, saved geometry stays rendered/selectable in LIGHT/DARK and the source error/fallback and denied-GPS/search controls pass. Screenshots are taken only after native rendered-feature confirmation for the focused point. Final bounded foundation and exact-SHA integration checks remain pending.

Capture limitation noted by the provisional independent review: the430dp bare-component DARK/NONE fallback PNG retains the previous dark outline during transition, unlike the settled360dp image. The native test proves local-source absence and saved-feature hit testing; these immediate raster/fallback captures do not assert final palette settling. Keep this image as transient functional evidence, not a final-color PASS. Existing unaffected DARK-3 palette evidence remains separate; the final APK's necessary native QA can inspect settling again.

The actual production route requested Android permission only after **Mi ubicación**. Denial left typed coordinates usable; the explicit permission action reopened the OS dialog. A temporary shell-owned test GPS provider delivered37.636,-3.480/hAcc10m; the platform dump and native blue point agreed in the preflight. `emu geo fix` returned OK but did not deliver these coordinates and is explicitly excluded from GPS PASS evidence. Original190 app files were restored by hash; the test provider was removed and shell mock-location app-op reset. App permissions were revoked back to their prior granted/denied state; this does not claim byte-identical Android permission metadata.

All QA ran on owned API35 emulator5580 with a read-only AVD. No physical device, Room records or original checkout was cleared. Raw failed runs, backups, the original crash logs and previous successful DARK evidence are retained. Bare component native-error screenshots draw beneath the status bar; production Scaffold and actual MainActivity evidence are separate.

The restarted read-only overlay used for the final isolated component tests contained15 files, preserved in a separate binary streamed archive (SHA256 `53deef8976b08b15d03a707c3ebdb5dda6302d06bf682609860b30ff2cc88884`). The original190 files were then restored with zero hash differences before shutting down only5580 for foundation verification. This does not claim the restarted component fixture contained the agricultural database during those tests; those tests deliberately use state objects without Room writes.

## Source availability and provenance

Primary [MAPA SIGPAC directory](https://www.mapa.gob.es/gl/cartografia-y-sig/ide/directorio_datos_servicios/agricultura/servicios-wms-sigpac/wms_sigpac) publishes `https://sigpac-hubcloud.es/wms`. Capabilities and actual1.3.0 GetMap/GetFeatureInfo returned200. At the probe coordinate the reported use is **PS-PASTIZAL**, not olives; no current-year declaration is inferred. A recinto is not Catastro/app parcel identity.

IGN/PNOA capabilities returned200, with numeric GoogleMapsCompatible matrix identifiers0..20 matching the existing URL template. Host detailed GetTile requests returned504 twice; native base tiles also timed out on this connection. Overview imagery rendered in retained tests. These observations do not establish a global provider outage.

## Retained evidence

Workspace evidence root: `artifacts/696-e2e/` (preserved, not indiscriminately staged).

- `b3-official-services-preflight-20261010/`: raw capabilities, valid SIGPAC PNG/GetFeatureInfo, failed1.1.1 XML, IGN/PNOA retries and request metadata.
- `b3-gps-native-preflight-20261010/`: OS dialog, denial/manual/retry, fresh mock GPS/native point and platform dump.
- `711-b3-native-final-captures/`: final Task1 LIGHT/DARK error/fallback captures; previous RED captures remain separate.
- `711-b3-viewport-red-captures/`, `711-b3-viewport-sigpac-{red,green}.txt`: production Scaffold regressions.
- `711-b3-sigpac-version-red.{txt,xml}`, `711-b3-sigpac-v130-green-build.txt`: protocol regression and corrected build.
- `711-b3-live-native-sigpac-20261010/`: failed initial native integration, retained.
- `711-b3-live-native-sigpac-v130-20261010/`: real successful native reference, result.json, UI XML, screenshot and platform location.
- `711-b3-map-matrix-final/`: first15 executions/18PNG, retained before the NONE row correction.
- `711-b3-map-matrix-corrected/`: corrected15 executions/18 inspected PNG, before the additional IME-padding fix; closed-keyboard behavior is unchanged by that final padding.
- `711-b3-native-keyboard-controls-green-retry-20261010/`: final actual MainActivity keyboard bounds, six inspected PNG and three verified configurations after IME padding. Original keyboard captures/driver failure remain separate.
- `711-b3-focused-map-final/`: final corrected18 executions/24 native PNG and inspection contacts, including actual native point with IME and subsequent parcel framing.
- `711-b3-focus-empty-farm-red.{txt,png}`, `711-b3-focus-source-diagnostic.txt`: genuine viewport RED and separately confirmed style/source defect. Earlier infrastructure/premature-query failures are retained separately.

## Remaining integration gates

Final bounded foundation, exact-SHA independent review and foundation/gate3-emulator/gate3-evidence SUCCESS remain required. The owner physical script remains pending: online layers/reference, contextual denied/enabled GPS, manual/offline parcel use and controls on the actual device. B4 live weather and B5 integrated green-main APK follow B3 merge.
