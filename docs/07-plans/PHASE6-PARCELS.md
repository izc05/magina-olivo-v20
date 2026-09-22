# Gate 6 — Parcels implementation plan

**Status:** PASS — validated on commit `ee89b9f7`
**Precondition:** Farm slice PASS
**Branch:** `feat/android-parcels`
**Stacked base:** `feat/android-farms` / PR #200

## Contract

Implement the production offline Parcel lifecycle without pulling Map/Catastro scope forward. A Parcel has a stable app-owned identity and belongs to a Farm through time-bounded membership history; cadastral fields remain optional provenance, never the primary key and never fabricated.

## Scope

- domain model, DAO projections and repository contract for active/archived Parcel reads;
- atomic create + initial Farm membership + outbox intent;
- edit, archive and restore with non-destructive membership history;
- alias/display name, cadastral reference, polygon/parcel identifiers, municipality/province, known area, notes and optional GeoJSON polygon;
- explicit `MANUAL` versus `CATASTRO` provenance;
- Farm detail Parcel list with loading/empty/error states and manual-create action;
- production Parcel detail/edit/archive route;
- truthful unknown states (`Sin registrar`) and no mock agronomic values;
- restart, rollback, outbox, ViewModel, Compose and navigation tests.

## Data rules

- a Parcel ID is an app UUID, not a cadastral reference;
- create requires a non-blank alias and an existing active Farm;
- a Parcel normally has exactly one current membership;
- moving/restoring closes the previous membership and creates a new interval; history is never overwritten;
- area is optional and must be positive when present;
- manual entry is marked `MANUAL`, including user-typed cadastral-looking fields;
- only a later verified Catastro adapter may create `CATASTRO` provenance;
- a polygon is retained as GeoJSON; a center point alone is not accepted as Parcel geometry;
- Parcel mutations save locally first and enqueue a deterministic Parcel aggregate intent.

## Implementation sequence

1. Write failing repository tests for create/membership/outbox, edit, archive/restore, restart and FK rollback.
2. Add Parcel domain models, DAO projections and offline repository.
3. Wire the repository through `LocalPersistence`.
4. Add ViewModels and unit tests.
5. Replace production Farm/Parcel reference content while preserving DEV reference screens.
6. Add Compose/navigation tests and accessibility assertions.
7. Run lint, unit tests, all debug builds and API 35 instrumentation, including offline Room proof.
8. Record the Parcel-slice result; keep Gate 6 FAIL until Campaigns also pass.

## Acceptance

- manual create/edit/archive/restore works without network and survives restart;
- Farm membership history remains queryable and has no overlapping current membership;
- list/detail use only persisted data;
- cadastral provenance is not falsely asserted;
- invalid names/areas fail without partial rows or outbox entries;
- back navigation and process recreation remain stable;
- CI, API 35 instrumentation and crash-buffer checks pass.

```text
PARCELS SLICE = PASS
GATE 6 = FAIL (IN PROGRESS)
```
