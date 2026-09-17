# Mágina Olivo — Catastro Contract RC1 v1

**Status:** Phase 0.4 draft
**Purpose:** define exactly how official cadastral services are used to discover/import parcels without making normal app operation depend on Catastro.

## 1. Source of truth boundaries

Catastro is the authoritative external source at import/query time for cadastral parcel identity and geometry available through its public INSPIRE services.

Mágina Olivo becomes the operational source of truth for the user's saved parcel after import.

```text
CATASTRO
  discovery/import source
        ↓
CadastreAdapter
        ↓
normalized ParcelCandidate
        ↓
user confirms
        ↓
MÁGINA OLIVO PARCEL
own UUID + normalized geometry + source metadata
```

Normal parcel/farm/campaign screens must read the stored Mágina Olivo parcel, not re-fetch Catastro every time.

## 2. Official services used

Official DG Catastro INSPIRE reference:

`https://www.catastro.hacienda.gob.es/webinspire/index.html`

### WMS — visualization

Official endpoint documented by Catastro:

`http://ovc.catastro.meh.es/cartografia/INSPIRE/spadgcwms.aspx?`

Supported operations include:

- GetCapabilities;
- GetMap;
- GetFeatureInfo.

Role in Mágina Olivo:

- optional cadastral overlay for visual parcel discovery;
- user orientation/confirmation;
- never used as the application's offline parcel geometry store.

### WFS — parcel geometry/data

Official endpoint documented by Catastro:

`http://ovc.catastro.meh.es/INSPIRE/wfsCP.aspx?`

Documented standard/format:

- WFS 2.0.0;
- GML 3.2.1;
- `CP:CadastralParcel`;
- `CP:CadastralZoning`.

Supported operations include:

- GetCapabilities;
- DescribeFeatureType;
- ListStoredQueries;
- DescribeStoredQueries;
- GetFeature.

Role in Mágina Olivo:

- obtain exact parcel candidate geometry/identity;
- query a small visible area;
- resolve a known cadastral reference;
- optionally obtain neighbouring parcels during selection.

## 3. Documented WFS query modes relevant to RC1

### BBOX discovery

Use `GetFeature` + `CP:CadastralParcel` for a small area around the user's tap/viewport.

Catastro documentation states cadastral-parcel WFS requests are limited to approximately:

- maximum 1 km² request extent;
- maximum 5,000 parcel elements.

The application must therefore never issue uncontrolled province/municipality-size interactive WFS requests.

### Known cadastral reference

The documented stored query `GetParcel` accepts a 14-position cadastral reference and returns a cadastral parcel feature.

RC1 can use this for the "Buscar por referencia catastral" flow.

### Neighbour parcels

`GetNeighbourParcel` can return a parcel plus adjoining parcels for a supplied reference.

This is optional for RC1 UX but useful for robust selection/highlighting.

### Parcels by cadastral zoning

`GetParcelsByZoning` is documented but is not required for the normal RC1 selection workflow.

## 4. CRS contract

Official WFS documentation lists support for:

- EPSG:4326 — WGS84 geographic;
- EPSG:4258 — ETRS89 geographic;
- EPSG:25829 — ETRS89 / UTM 29N;
- EPSG:25830 — ETRS89 / UTM 30N;
- EPSG:25831 — ETRS89 / UTM 31N;
- EPSG:3857 — Web Mercator;
- legacy Web Mercator code also listed in official documentation.

### Internal Mágina Olivo rule

All persisted/interchanged app parcel geometry is normalized to a single application representation compatible with GeoJSON WGS84 (EPSG:4326).

The Catastro adapter owns all CRS/axis-order conversion.

UI/domain/database code outside the adapter must never need to know whether source data arrived as UTM 30, 31, ETRS89 or another supported CRS.

### Spike requirement

Phase 9 Catastro Spike must verify coordinate/axis handling with known real parcels before production code freezes the parser.

## 5. Normalized candidate contract

The integration layer returns a provider-neutral object conceptually equivalent to:

```text
ParcelCandidate
- provider: CATASTRE
- external_id/reference
- cadastral_reference
- cadastral_polygon?
- cadastral_parcel?
- municipality?
- province?
- geometry_wgs84
- centroid_wgs84
- source_area_m2?
- raw_source_version/metadata?
- fetched_at
```

The UI must not manipulate raw GML directly.

## 6. User confirmation boundary

Fetching a `ParcelCandidate` does not create a parcel automatically.

Mandatory flow:

```text
candidate fetched
→ geometry rendered/highlighted
→ cadastral identity shown
→ user confirms
→ optional user display name
→ target farm selected/known
→ domain transaction creates Parcel + Membership
```

This prevents accidental import of the parcel under the finger when map precision or nearby boundaries are ambiguous.

## 7. Parcel creation transaction

On confirmation, create atomically where possible:

1. `Parcel` UUID generated locally;
2. normalized stored geometry;
3. cadastral identity/source fields;
4. `source = CADASTRE`;
5. `source_imported_at`;
6. optional source fingerprint;
7. `FarmParcelMembership`;
8. sync outbox entries.

The local operation succeeds even if remote synchronization is unavailable after the Catastro fetch has completed.

## 8. Offline behavior

After successful import/confirmation, the following must work without Catastro and without Internet:

- farm map shows the parcel boundary;
- parcel detail shows its boundary;
- parcel can participate in campaigns;
- field work can be recorded against it;
- timeline/history works;
- reports can use the stored/snapshotted geometry as designed.

WMS tiles/images are not the authoritative offline geometry.

## 9. Search flows

### A. Visual map selection

```text
Open Add Parcel
→ choose Catastro map
→ map starts at chosen/search area
→ optional "my location"
→ cadastral overlay visible at suitable zoom
→ tap/select location
→ request small candidate area or feature info
→ resolve exact WFS parcel
→ highlight polygon
→ user confirms
→ save
```

### B. Cadastral reference

```text
Open Add Parcel
→ search by reference
→ validate reference shape
→ WFS StoredQuery GetParcel
→ candidate
→ map preview
→ user confirms
→ save
```

### C. GML import

GML import is a separate adapter. It must produce the same normalized `ParcelCandidate` contract before confirmation.

Thus the rest of the app does not care whether the parcel came from WFS or a user-provided GML file.

## 10. Map-layer responsibilities

```text
Map UI
├── BaseMapLayer
├── CadastreWmsLayer      // discovery only
├── CandidateParcelLayer  // current selection
└── OwnedParcelLayer      // persisted Mágina Olivo data
```

`OwnedParcelLayer` always renders from local domain geometry.

The user must be able to distinguish:

- parcels merely visible in cadastral overlay;
- the currently selected candidate;
- parcels already owned/saved in Mágina Olivo.

## 11. Adapter boundaries

Recommended interfaces conceptually:

```text
CadastreVisualProvider
- capabilities/config
- overlay descriptor

CadastreParcelProvider
- findByReference(ref): ParcelCandidate
- findAround(point/bbox): List<ParcelCandidate>
- optional neighbours(ref): List<ParcelCandidate>

GeometryNormalizer
- parseGml(...)
- transformToAppCrs(...)
- validate(...)
- calculate/fix centroid where required
```

The domain layer consumes `ParcelCandidate`; it does not consume HTTP/XML/GML-specific types.

## 12. Geometry validation

Before user confirmation, the adapter must reject/flag:

- empty geometry;
- unsupported geometry type;
- invalid coordinates;
- self-intersection/invalid polygon where normalization cannot safely repair it;
- absurd/out-of-range coordinates after CRS conversion;
- missing external identity when query mode requires one.

MultiPolygon must be supported where the provider returns a valid multipart cadastral geometry.

Do not simplify the stored master parcel geometry destructively merely for display performance. A derived display geometry may be introduced separately if required.

## 13. Duplicate detection

Before creating a new parcel, detect likely duplicates using:

1. exact normalized cadastral reference when available;
2. provider + external/source identity;
3. optional spatial/geometry similarity warning if identity is absent.

If an active parcel with the same cadastral reference already exists in the workspace, the UI should offer opening/reusing it rather than silently duplicating it.

## 14. Source freshness

Persist:

```text
source = CADASTRE
source_imported_at
source_fingerprint? / source metadata
```

Do not automatically replace the user's stored parcel geometry whenever Catastro changes.

A future explicit action may support:

`Check cadastral update → compare → review diff → user accepts update`.

If accepted, historical campaign geometry remains protected by snapshots.

## 15. Error model

Normalize provider failures into app-level outcomes, for example:

```text
NETWORK_UNAVAILABLE
SERVICE_UNAVAILABLE
RATE_OR_LIMIT_REACHED
NO_PARCEL_FOUND
MULTIPLE_CANDIDATES
INVALID_REFERENCE
INVALID_PROVIDER_GEOMETRY
CRS_TRANSFORM_ERROR
PARSER_ERROR
LICENSE_OR_PROVIDER_ERROR
UNKNOWN
```

Do not expose raw XML/HTTP errors as the normal user message.

UX examples:

- "No hemos podido consultar Catastro ahora. Tus parcelas guardadas siguen disponibles."
- "No encontramos una parcela con esa referencia. Revisa el dato."
- "Hay varias parcelas en ese punto. Acerca el mapa y selecciona una."

## 16. Retry policy

Interactive Catastro actions may retry safely with bounded attempts for transient network/service errors.

Do not create background jobs that continuously scrape/query broad cadastral areas.

Normal synchronization of already-imported Mágina Olivo parcels is independent of Catastro availability.

## 17. Licensing/provenance rule

Official Catastro INSPIRE services are used subject to their published access/use licence.

Engineering requirements:

- retain provider/provenance metadata;
- document the official source in application legal/about information where appropriate;
- do not present Mágina Olivo's stored operational copy as an official current cadastral certificate;
- do not mass-republish the original Catastro dataset as a substitute cadastral service;
- treat transformed/internal parcel representations as application data used for the user's management workflow;
- review licence wording before public/commercial distribution features expand beyond private holding management.

## 18. Privacy boundary

RC1 only needs publicly accessible/non-protected cadastral geometry/identity for parcel selection.

Do not design RC1 around automated access to protected ownership data.

If protected/titular data is ever required later, it becomes a separate authenticated legal/integration project and requires a Change Request.

## 19. Performance rules

- query only a small area needed for user interaction;
- debounce rapid map taps/viewport queries;
- cancel obsolete in-flight discovery requests when the user moves away;
- cache only short-lived discovery responses where useful;
- persist confirmed parcel geometry permanently in the local domain DB;
- never use huge municipality/province WFS queries for interactive browsing.

## 20. Test matrix for Phase 9 spike

Use known real parcels in multiple conditions:

- rural olive parcel in Sierra Mágina;
- narrow/irregular parcel;
- parcel near adjoining boundaries;
- known reference lookup;
- visual tap lookup;
- map zoom changes;
- no-network after import;
- service timeout;
- invalid reference;
- duplicate import attempt;
- geometry with more complex rings if encountered.

Verify:

- cadastral identity;
- polygon location;
- coordinate orientation;
- centroid;
- displayed area sanity;
- MapLibre rendering;
- Room round-trip;
- reopen app offline.

## 21. Phase 0.4 Gate

Phase 0.4 is ready for approval when all statements below are unambiguous:

- [ ] WMS is visualization/discovery, not app storage.
- [ ] WFS/GML is the primary official parcel-geometry integration.
- [ ] reference search and map selection resolve to the same `ParcelCandidate` model.
- [ ] CRS conversion is isolated in the adapter.
- [ ] persisted app geometry uses one normalized CRS/representation.
- [ ] confirmed parcel remains usable offline.
- [ ] duplicate detection is defined.
- [ ] provider failure does not break previously saved parcels.
- [ ] no mass interactive WFS download is required.
- [ ] source provenance and licence boundary are recorded.
- [ ] protected ownership data is outside RC1.
- [ ] production implementation is blocked until a real-parcel Android spike validates geometry/axis behavior.

## 22. Official references reviewed

- DG Catastro INSPIRE services: `https://www.catastro.hacienda.gob.es/webinspire/index.html`
- DG Catastro WFS Cadastral Parcels documentation: linked from the official INSPIRE page.
- DG Catastro WMS INSPIRE documentation: linked from the official INSPIRE page.
- DG Catastro INSPIRE access/use licence: linked from the official INSPIRE page.
