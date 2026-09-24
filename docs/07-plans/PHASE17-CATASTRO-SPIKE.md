# Phase 17 — Spain Catastro technical spike and Android import

**Status:** WFS probe and Android live import passed in CI. The owner requested the complete
Phase 17 implementation on an isolated branch; merging remains held for Gate 16's physical-device check.
**Branch:** `codex/phase17-catastro`, PR #217. The original probe was prepared on `docs/phase17-catastro-spike`.
**Android evidence and remaining checks:** `docs/06-testing/PHASE17-CATASTRO-IMPORT-SLICE.md`.
**Contract:** `docs/03-maps/CADASTRE-CONTRACT-RC1.md` (§20 test matrix, §21 gate)

**Gate 17:** a real Spanish parcel can be located/imported without scraping or invented geometry.

## Why a probe in CI

The development sandbox cannot reach `ovc.catastro.meh.es` (network policy), while GitHub
Actions runners can. `spikes/catastro/probe.py` runs in `.github/workflows/catastro-spike.yml`
on this branch only, talks exclusively to the official INSPIRE WFS, prints a JSON summary in
the job log and keeps the raw GML as an artifact.

## What the probe establishes

| Question | How |
|---|---|
| Can real parcels be found without typing any reference? | Small BBOX `GetFeature` (240 m square, far below the documented 1 km²) around olive-growing points in Sierra Mágina (Huelma, Bedmar, Jimena). |
| Does lookup by reference return that exact parcel? | `STOREDQUERIE_ID=GetParcel&refcat=<14 chars from the BBOX result>`. |
| What geometry arrives? | GML 3.2 `CP:CadastralParcel`: polygons, rings (holes), multipart, vertex count. |
| Axis order / CRS | Same parcel requested in `EPSG::4326` and `EPSG::25830`; the UTM copy is transformed to WGS84 and the maximum vertex deviation reported; the 4326 axis order (lat,lon vs lon,lat) is detected. |
| Area sanity | Shoelace area in UTM vs the provider's `areaValue`. |
| Error modes | Malformed reference, unknown reference, oversize BBOX (9 km²): status, message, time. |

Timings (ms) are recorded for every call, to size timeouts and retry policy (§15–16).

## Decisions evaluated by the spike

1. **App CRS path:** request `EPSG::4326` directly (no projection code on the phone) or request
   `EPSG::25830/25829/25831` and convert in the adapter — depends on the measured deviation and
   the axis order the service really returns.
2. **Parser:** GML 3.2 subset actually used by Catastro (polygon/multipolygon/holes) → the
   normalized `ParcelCandidate` of the contract (§5).
3. **Area shown to the farmer:** provider `areaValue` vs computed; which one is stored as
   `cadastralAreaM2`.
4. **Error mapping:** real service responses → the contract's error model (§15).
5. **Timeouts/retries** from measured latencies.

## Measured result (GitHub Actions run 35905672255)

- Small BBOX queries returned 3 parcels near Huelma, 30 near Bedmar and 6 near Jimena.
- Six parcels were resolved again by their own 14-character reference. Every returned reference matched.
- The service returned `gml:MultiSurface` with `gml:Surface/gml:PolygonPatch`, not `gml:Polygon`; the probe now parses exterior and interior rings explicitly.
- `EPSG::4326` coordinates arrived in **latitude, longitude** order. Comparison with `EPSG::25830` converted to WGS84 differed by at most 0.198 m across the six samples. The Android adapter can request 4326 directly and normalize coordinates to GeoJSON longitude, latitude order.
- The provider's `areaValue` differed from the UTM shoelace calculation by at most 0.5 m² in these samples. Store the provider value as cadastral area when present; preserve the geometry independently.
- Reference lookups took 732–797 ms; BBOX lookups took 976–2338 ms in this run. Use a bounded timeout and retry only transient transport errors, then verify with more runs before freezing limits.
- Malformed, unknown and oversized BBOX requests all returned HTTP 200 with an exception message and zero parcels. The adapter must inspect the XML body, not use HTTP status alone.

This result validates the service shape for the sampled Sierra Mágina locations. It does not yet prove polygons with holes or multipart parcels in live responses; those cases need fixtures and an additional real sample before the production parser is final. Gate 17 remains open until the Android confirmation and offline save flow works with a real parcel.

## Out of scope here

Interactive map rendering (Phase 18), WMS overlay, protected ownership data (§18), and
bulk downloads remain out of scope. Android now provides reference lookup, a contour preview,
explicit confirmation, farm assignment, duplicate protection, and local persistence through the
existing repository (no schema migration). The live emulator test imports an official parcel and
checks persistence after database reopening. This evidence does not close the physical-device checks
or authorize merging before Gate 16 PASS.
