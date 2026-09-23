# Phase 17 — Spain Catastro technical spike (preparation)

**Status:** research line prepared while Gate 16 waits for its physical-device check.
No production code: nothing here is compiled into the app.
**Branch:** `docs/phase17-catastro-spike` (auxiliary research line, `SINGLE-TRACK-EXECUTION`)
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

## Decisions the spike must close before Phase 17 production work

1. **App CRS path:** request `EPSG::4326` directly (no projection code on the phone) or request
   `EPSG::25830/25829/25831` and convert in the adapter — depends on the measured deviation and
   the axis order the service really returns.
2. **Parser:** GML 3.2 subset actually used by Catastro (polygon/multipolygon/holes) → the
   normalized `ParcelCandidate` of the contract (§5).
3. **Area shown to the farmer:** provider `areaValue` vs computed; which one is stored as
   `cadastralAreaM2`.
4. **Error mapping:** real service responses → the contract's error model (§15).
5. **Timeouts/retries** from measured latencies.

## Out of scope here

Map rendering (Phase 18), Room changes, UI, WMS overlay, protected ownership data (§18),
bulk downloads. Gate 17 is not claimed from this preparation: it needs the Android import
path on a real parcel, which starts only after Gate 16 PASS.
