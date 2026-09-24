# Phase 17 — Catastro reference lookup and confirmed import (prepared, gate-bound)

**Status:** prepared on `codex/phase17-catastro`, **not mergeable** until Gate 16 is PASS
(physical-device reminder check). The owner decided on 2026-09-23 to finish this line and keep
it waiting, instead of opening Phase 17 early. `CURRENT-STATE.md` still names Phase 16 as the
allowed phase.
**Contract:** `docs/03-maps/CADASTRE-CONTRACT-RC1.md` · spike: `docs/07-plans/PHASE17-CATASTRO-SPIKE.md`
**Gate 17:** a real Spanish parcel can be located/imported without scraping or invented geometry.

## Flow

1. Farm detail → Parcelas → **Catastro** (`import-catastro`) opens the search with that farm already
   chosen. With a single farm the farm is chosen automatically too.
2. The farmer types the 14-character reference. `OfficialCadastreClient` calls the INSPIRE WFS
   `GetParcel` stored query in `EPSG::4326`, reads the XML body (the service answers HTTP 200 even for
   errors) and keeps only the parcel whose reference matches.
3. The candidate card shows reference, cadastral area and the outline drawn from the received
   geometry, with the note that the stored copy is not a current cadastral certificate.
4. **Confirmar e incorporar** stores the parcel locally with `source = CATASTRO`, the reference,
   GeoJSON geometry (longitude, latitude) and the provider area. Nothing is saved before the farmer
   confirms. Back from the new parcel returns to its farm.

## Truth and safety rules kept

- No scraping and no protected data: only public identity, geometry and area.
- The coordinate check covers all Catastro territory, Canary Islands, Ceuta and Melilla included;
  swapped axes are rejected as invalid geometry instead of being stored.
- A reference already active in the workspace is refused (`duplicate_cadastral_reference`).
- Offline or service failure: a clear message; saved parcels keep working. No Room migration.
- XML parsing disables DTDs and external entities; response size is capped at 2 MB.

## Tests

| Test | Kind | What it proves |
|---|---|---|
| `CadastreClientTest` | JVM | Real Huelma GML fixture, wrong-reference rejection, multipart + holes, Canary Islands accepted, swapped axes rejected |
| `CatastroLiveImportTest` | Instrumented, live | Real WFS lookup of `23044A00400021`, confirmed import, duplicate refused, data intact after reopening Room |
| `CadastreImportScreenTest` | Compose, deterministic | No import before confirmation, preselected farm retained, import disabled without a farm |

The parser suite also rejects UTF-16 DTDs before XML parsing and unrecognized CRS identifiers.
The contour preview uses a local latitude correction for longitude distances and even-odd filling
to preserve holes. This is a parcel outline preview, not a georeferenced interactive map.

The live test runs only in `.github/workflows/phase17-catastro.yml`; the ordinary emulator suite
excludes it so everyday CI never depends on the external service.

## CI evidence

| Check | Result | Run |
|---|---|---|
| Hardened XML parser + live lookup/import + Room reopen | PASS on `05d0d937`, `OK (1 test)` | [Live run 35953041380](https://github.com/izc05/magina-olivo-v20/actions/runs/35953041380) |
| Lint, JVM tests, three application variants and instrumented test compilation | PASS, foundation job on `05d0d937` | [Android CI 35953041737](https://github.com/izc05/magina-olivo-v20/actions/runs/35953041737) |
| Full emulator suite, including both Catastro confirmation screen tests | PASS on `05d0d937`: 181 tests; separate airplane-mode persistence run: 105 tests; reference layouts: 12 tests at each of four configurations | [Android CI 35953041737](https://github.com/izc05/magina-olivo-v20/actions/runs/35953041737) |
| Live WFS lookup + confirmed import + Room reopen (`CatastroLiveImportTest`, API 35 emulator) | PASS on `2ab51c89` | [Phase 17 Catastro live import run 35912423582](https://github.com/izc05/magina-olivo-v20/actions/runs/35912423582) |
| Independent emulator evidence (live test excluded) | PASS on `5a95b70d` | [Gate 3 run 35911510100](https://github.com/izc05/magina-olivo-v20/actions/runs/35911510100) |

The first live runs found two real defects before any phone did: the workflow never launched the
test (split command line), and Android's XML parser rejected the Xerces hardening flags, so every
lookup failed on a device. Both are fixed on this branch.

## Pending before Gate 17 PASS

- Gate 16 PASS and merge of the Phase 16 line.
- Real-device check of the import path with coverage and without it.
- Map rendering stays in Phase 18.

## Physical-device acceptance steps

1. In a test workspace with a farm, open its Parcelas section and tap Catastro.
2. With connectivity, search `23044A00400021`. Check the returned reference, provider area and contour.
3. Leave without confirming: the farm must still have the same parcel count.
4. Repeat, name the parcel and confirm. Check the destination farm, CATASTRO source and cadastral area.
5. Search and confirm the same reference again: the app must report that it already exists.
6. Enable airplane mode, close and reopen the app, and open the imported parcel. Its saved data must remain available.
7. Attempt a fresh lookup while offline: show a recoverable error without deleting or replacing the saved parcel.
8. Restore connectivity and repeat lookup. Record device, Android version, commit/APK, date and results.

These steps remain pending on a physical device; emulator evidence must not be recorded as that check.
