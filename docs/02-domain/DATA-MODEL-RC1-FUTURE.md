# Mágina Olivo — Data Model RC1 + Future v1

**Status:** Phase 0.3 draft for baseline review
**Depends on:** `docs/00-master/RC1-BASELINE.md`

The model is designed for Android offline-first operation with Room locally and PostgreSQL/PostGIS remotely. Domain IDs are UUIDs generated on the client so records can be created without connectivity.

## 1. Global conventions

### Identifiers

All synchronizable domain entities use:

```text
id UUID
workspace_id UUID
created_at Instant
updated_at Instant
deleted_at Instant?       // soft delete where applicable
version Long              // optimistic concurrency/domain version
device_id UUID?           // last originating device where useful
```

The client creates the UUID before first persistence. Remote insertion must never replace the domain ID.

### Time

- Persist machine timestamps as UTC instants.
- Persist user agricultural dates separately as local dates where time-of-day is not semantically relevant.
- Do not reconstruct campaign dates from `created_at`.

### Money

- Persist monetary values as integer minor units (`amount_cents`) plus ISO currency (`EUR` for the initial Spanish product).
- Never persist financial values as binary floating point.

### Areas and quantities

- Canonical area: square metres (`area_m2`, Double/Decimal depending layer).
- UI may display hectares.
- Canonical harvest weight: kilograms.
- Product/rate units must be explicit, never inferred from a numeric field.

### Geometry

Remote: PostGIS geometry with SRID explicitly declared.
Local: a normalized geometry representation that Room can persist and MapLibre can render without re-querying Catastro.

Application interchange format: GeoJSON-compatible WGS84 (EPSG:4326) unless the Catastro adapter temporarily works in another CRS. CRS transformation happens at the integration boundary.

---

# 2. Ownership root

## `workspaces`

Purpose: ownership/security boundary and future shared holding boundary.

Fields:

```text
id UUID PK
name String
owner_user_id UUID
created_at Instant
updated_at Instant
version Long
```

RC1 behavior: one personal workspace per account by default.

Future: workspace members/roles without migrating every agricultural table.

## `workspace_members` — reserved/optional RC1 infrastructure

```text
id UUID PK
workspace_id UUID FK
user_id UUID
role OWNER | ADMIN | WORKER | ADVISOR | VIEWER
status ACTIVE | INVITED | DISABLED
created_at
updated_at
```

Not exposed in RC1 UI, but schema may exist early if useful for RLS.

---

# 3. Farms

## `farms`

Purpose: user-defined logical holding grouping one or more parcels.

```text
id UUID PK
workspace_id UUID FK
name String NOT NULL
description String?
municipality String?
province String?
cover_attachment_id UUID?
notes String?
status ACTIVE | ARCHIVED
created_at
updated_at
deleted_at?
version
```

Derived, not authoritative stored totals:

- current parcel count;
- current managed area;
- active campaign metrics.

These are queried/projected from source records to avoid stale duplicated totals.

Indexes:

```text
(workspace_id, status)
(workspace_id, name)
```

---

# 4. Parcels

## `parcels`

Purpose: stable Mágina Olivo representation of a managed land parcel.

```text
id UUID PK
workspace_id UUID FK
display_name String NOT NULL

cadastral_reference String?
cadastral_polygon String?
cadastral_parcel String?
municipality String?
province String?

source CADASTRE | GML | GEOJSON | MANUAL
source_imported_at Instant?
source_fingerprint String?

geometry Polygon/MultiPolygon NOT NULL
centroid Point?

cadastral_area_m2 Decimal?
managed_area_m2 Decimal?

notes String?
status ACTIVE | ARCHIVED

created_at
updated_at
deleted_at?
version
```

Rules:

1. `display_name` is the user's operational name and may differ from Catastro.
2. Cadastral identity is metadata; it is not the internal primary key.
3. Stored geometry is owned by Mágina Olivo after import and must remain available offline.
4. `managed_area_m2` may differ from cadastral area.
5. Changing current parcel metadata never rewrites campaign snapshots.

Remote spatial index:

```text
GIST (geometry)
```

Other indexes:

```text
(workspace_id, status)
(workspace_id, cadastral_reference)
(workspace_id, display_name)
```

---

# 5. Farm ↔ Parcel membership

## `farm_parcel_memberships`

Purpose: avoid destructive `parcel.farm_id` history.

```text
id UUID PK
workspace_id UUID FK
farm_id UUID FK
parcel_id UUID FK
valid_from LocalDate
valid_until LocalDate?
created_at
updated_at
version
```

Cardinality:

```text
Farm 1 ──< Membership >── 1 Parcel
```

Operational rule for RC1: a parcel should normally have at most one active farm membership at a time.

Database/application invariant:

```text
no overlapping active membership periods for the same parcel
```

Historical membership must not be physically overwritten.

---

# 6. Agronomic parcel profile

## `parcel_profiles`

Purpose: optional agronomic data without bloating cadastral identity.

```text
id UUID PK
workspace_id UUID FK
parcel_id UUID UNIQUE FK
crop_type String?                  // initial expected value: OLIVE
primary_variety String?            // e.g. Picual
secondary_variety String?
approx_tree_count Int?
plantation_year Int?
plantation_age_years Int?
planting_frame String?
density_trees_ha Decimal?
farming_system TRADITIONAL | INTENSIVE | SUPER_INTENSIVE | OTHER | UNKNOWN?
water_regime RAINFED | IRRIGATED | MIXED | UNKNOWN?
slope_class String?
soil_notes String?
notes String?
created_at
updated_at
version
```

Most fields are optional. RC1 may expose only a subset while keeping the entity available.

---

# 7. Campaigns

## `campaigns`

```text
id UUID PK
workspace_id UUID FK
name String NOT NULL
season_start_year Int
season_end_year Int
start_date LocalDate?
end_date LocalDate?
status PREPARATION | ACTIVE | HARVEST | CLOSED
notes String?
closed_at Instant?
created_at
updated_at
version
```

Rules:

- `season_end_year >= season_start_year`.
- closed campaign data is protected from accidental editing;
- reopening, if ever allowed, must be an explicit audited action.

Indexes:

```text
(workspace_id, status)
(workspace_id, season_start_year, season_end_year)
```

## `campaign_parcels`

Historical snapshot of parcel participation.

```text
id UUID PK
workspace_id UUID FK
campaign_id UUID FK
parcel_id UUID FK
farm_id_at_start UUID?
parcel_name_at_start String
managed_area_m2_at_start Decimal?
cadastral_reference_at_start String?
geometry_snapshot Polygon/MultiPolygon?
created_at
updated_at
version
```

Unique:

```text
(campaign_id, parcel_id)
```

This snapshot is the authoritative historical context for campaign reporting.

---

# 8. Activities

## `activities`

Common activity header.

```text
id UUID PK
workspace_id UUID FK
campaign_id UUID FK
activity_type ActivityType
activity_date LocalDate NOT NULL
started_at Instant?
ended_at Instant?
title String?
description String?
operator_text String?            // RC1 fallback before people module
machinery_text String?           // RC1 fallback before equipment module
notes String?
status DRAFT | COMPLETED | CANCELLED
cost_cents Long?
currency String = EUR
created_at
updated_at
deleted_at?
version
```

`ActivityType` RC1:

```text
OBSERVATION
OTHER
PRUNING
SOIL_WORK
FERTILIZATION
PHYTOSANITARY
IRRIGATION
MAINTENANCE
INCIDENT
```

Harvest is intentionally not an activity subtype because it has separate quantity/distribution/delivery semantics.

## `activity_parcels`

```text
id UUID PK
workspace_id UUID FK
activity_id UUID FK
parcel_id UUID FK
campaign_parcel_id UUID?
area_affected_m2 Decimal?
notes String?
created_at
updated_at
version
```

Unique:

```text
(activity_id, parcel_id)
```

This permits one activity applied to multiple parcels without duplicating the header.

---

# 9. Typed activity details

Use typed one-to-one detail tables instead of an untyped JSON blob for critical agronomic fields.

## `pruning_details`

```text
activity_id UUID PK/FK
pruning_type String?
worker_count Int?
hours Decimal?
residue_management String?
```

## `fertilization_details`

```text
activity_id UUID PK/FK
product_name String?
product_id UUID?                 // future products module
total_quantity Decimal?
unit String?
dose_value Decimal?
dose_unit String?
application_method String?
```

## `phytosanitary_details`

```text
activity_id UUID PK/FK
product_name String?
product_id UUID?
active_substance String?
total_quantity Decimal?
unit String?
dose_value Decimal?
dose_unit String?
reason String?
equipment_text String?
```

## `soil_work_details`

```text
activity_id UUID PK/FK
work_type String?
method String?
```

## `irrigation_details`

```text
activity_id UUID PK/FK
duration_minutes Int?
volume_m3 Decimal?
sector_text String?
system_text String?
```

## `maintenance_details`

```text
activity_id UUID PK/FK
maintenance_type String?
asset_text String?
```

## `incident_details`

```text
activity_id UUID PK/FK
category String?
severity LOW | MEDIUM | HIGH | CRITICAL?
incident_status OPEN | MONITORING | RESOLVED
location_geometry Point?
action_taken String?
resolved_at Instant?
```

Rule: exactly one matching detail row for activity types that require structured details.

---

# 10. Expenses

## `expenses`

```text
id UUID PK
workspace_id UUID FK
campaign_id UUID FK
expense_date LocalDate NOT NULL
category LABOR | PRODUCTS | MACHINERY | FUEL | IRRIGATION | EXTERNAL_SERVICE | REPAIR | HARVEST | TRANSPORT | OTHER
description String
amount_cents Long NOT NULL
currency String = EUR
supplier_text String?

farm_id UUID?
parcel_id UUID?
activity_id UUID?
harvest_id UUID?
delivery_id UUID?

created_at
updated_at
deleted_at?
version
```

Rule: one expense record is counted once. Reports aggregate by its relations; they do not duplicate expenses into multiple tables.

Indexes:

```text
(workspace_id, campaign_id, expense_date)
(activity_id)
(parcel_id)
(farm_id)
```

Future supplier module can add `contact_id` while keeping `supplier_text` for historical compatibility.

---

# 11. Harvest

## `harvests`

```text
id UUID PK
workspace_id UUID FK
campaign_id UUID FK
harvest_date LocalDate NOT NULL
total_weight_kg Decimal NOT NULL
collection_method String?
worker_count Int?
machinery_text String?
notes String?
created_at
updated_at
deleted_at?
version
```

## `harvest_parcels`

```text
id UUID PK
workspace_id UUID FK
harvest_id UUID FK
parcel_id UUID FK
campaign_parcel_id UUID?
weight_kg Decimal?
allocation_mode EXACT | UNALLOCATED
created_at
updated_at
version
```

Rules:

- one harvest may include multiple parcels;
- if all parcel weights are `EXACT`, their sum must reconcile with `total_weight_kg` within configured rounding tolerance;
- if exact parcel distribution is unknown, retain `UNALLOCATED`; never invent per-parcel production.

---

# 12. Deliveries

## `deliveries`

```text
id UUID PK
workspace_id UUID FK
campaign_id UUID FK
delivery_date LocalDate NOT NULL
destination_name String NOT NULL
weight_kg Decimal NOT NULL
delivery_number String?
ticket_number String?
fat_yield_percent Decimal?
industrial_yield_percent Decimal?
notes String?
created_at
updated_at
deleted_at?
version
```

Harvest and delivery remain independent. The system must not force total delivered kg to equal harvested kg at every moment.

Future: `contact_id` can reference an almazara/supplier contact.

---

# 13. Attachments

## `attachments`

```text
id UUID PK
workspace_id UUID FK
owner_type FARM | PARCEL | CAMPAIGN | ACTIVITY | EXPENSE | HARVEST | DELIVERY | ANALYSIS | OTHER
owner_id UUID
attachment_type PHOTO | PDF | TICKET | INVOICE | PLAN | GML | DOCUMENT | OTHER
mime_type String
filename String
file_size_bytes Long?
sha256 String?
local_uri String?
remote_path String?
upload_status LOCAL_ONLY | PENDING | UPLOADING | SYNCED | FAILED
captured_at Instant?
created_at
updated_at
deleted_at?
version
```

The local record is created before upload. Remote Storage failure must not remove the local ownership link.

Index:

```text
(workspace_id, owner_type, owner_id)
```

---

# 14. Synchronization outbox

## `sync_outbox` — local only

```text
id UUID PK
entity_type String
entity_id UUID
operation CREATE | UPDATE | DELETE | UPLOAD_ATTACHMENT
payload_version Int
attempt_count Int
next_attempt_at Instant?
last_error_code String?
last_error_message String?
created_at Instant
updated_at Instant
```

The domain transaction and outbox insertion must be atomic in Room when possible.

Do not use remote state as the only record that an operation needs synchronization.

## Local sync metadata

Either embedded in local entities or normalized as appropriate:

```text
sync_status LOCAL_ONLY | PENDING | SYNCING | SYNCED | FAILED | CONFLICT
remote_version Long?
last_synced_at Instant?
```

---

# 15. Conflict records

## `sync_conflicts` — local operational table

```text
id UUID PK
entity_type String
entity_id UUID
local_version Long
remote_version Long
local_payload Json
remote_payload Json
created_at Instant
resolved_at Instant?
resolution KEEP_LOCAL | KEEP_REMOTE | MERGED?
```

Critical records (activities, harvests, deliveries, expenses) must never be silently overwritten when the conflict policy cannot prove one version safely supersedes the other.

---

# 16. Reserved future modules

These entities are architecturally reserved but not required for RC1 screens.

## `management_zones`

Subdivision of a managed parcel.

```text
id
workspace_id
parcel_id
name
geometry
area_m2
valid_from
valid_until?
```

## `farm_features`

Generic geographic features.

```text
id
workspace_id
farm_id?
parcel_id?
feature_type WELL | TANK | SHED | HYDRANT | METER | GATE | ROAD | OTHER
name
geometry POINT | LINESTRING | POLYGON
notes
```

## `tasks`

```text
id
workspace_id
campaign_id?
farm_id?
due_date?
priority
activity_type?
title
status PLANNED | PENDING | IN_PROGRESS | COMPLETED | CANCELLED
converted_activity_id?
```

## `people`

```text
id
workspace_id
display_name
role_text
contact_id?
active
```

## `equipment`

```text
id
workspace_id
name
type
brand?
model?
registration?
year?
active
```

## `products`

```text
id
workspace_id
name
category
unit
active
```

## `contacts`

```text
id
workspace_id
type MILL | SUPPLIER | WORKSHOP | ADVISOR | COMPANY | OTHER
name
phone?
email?
address?
```

## `inventory_items` / `inventory_movements`

Reserved for post-RC2 stock management. Activity/product integration must not be required by RC1.

## `analyses`

```text
id
workspace_id
analysis_type SOIL | LEAF | WATER | OLIVE | OTHER
sample_date
laboratory?
parcel_id?
result_summary?
```

Documents attach through generic `attachments`.

---

# 17. Cardinality summary

```text
User 1 ──< WorkspaceMember >── 1 Workspace
Workspace 1 ──< Farm
Workspace 1 ──< Parcel
Farm 1 ──< FarmParcelMembership >── 1 Parcel
Workspace 1 ──< Campaign
Campaign 1 ──< CampaignParcel >── 1 Parcel
Campaign 1 ──< Activity
Activity 1 ──< ActivityParcel >── 1 Parcel
Campaign 1 ──< Expense
Campaign 1 ──< Harvest
Harvest 1 ──< HarvestParcel >── 1 Parcel
Campaign 1 ──< Delivery
Any supported owner 1 ──< Attachment
```

---

# 18. Historical invariants

These invariants are non-negotiable for RC1:

1. Parcel current name/area/farm changes do not mutate campaign snapshots.
2. A closed campaign remains reportable even if its parcel is later archived.
3. Soft-deleted activities/expenses/harvests retain enough audit history for safe synchronization and recovery.
4. Current farm membership does not define historical farm membership.
5. Reports for historical campaigns use campaign snapshot values where semantics require historical context.
6. Unknown harvest distribution remains unknown; it is not estimated silently.
7. Delivery history is not reconstructed from current contact/almázara metadata.

---

# 19. RLS ownership contract

Every remote domain row must be reachable to an authorized user through `workspace_id` membership.

Baseline policy intent:

```text
READ/INSERT/UPDATE/DELETE allowed only when authenticated user is an active member of row.workspace_id with sufficient role.
```

RC1 personal workspace may expose only owner permissions in UI, but RLS must be compatible with future roles.

No client APK receives a service-role credential.

---

# 20. Room vs remote responsibility

## Room stores

- all RC1 domain records needed for field operation;
- parcel geometries required to render owned land;
- local attachment references;
- outbox;
- sync/conflict metadata;
- user session/cache metadata as appropriate.

## Supabase stores

- synchronized domain records;
- PostGIS geometries;
- account/workspace membership;
- attachment binaries in Storage;
- server-side authorization state.

## UI rule

Normal RC1 UI reads observable local repositories. It does not wait for a network response to show a locally known farm, parcel, campaign or activity.

---

# 21. Derived metrics — never primary editable truth

Calculate/project rather than manually store as authoritative fields:

- farm total current area;
- farm parcel count;
- campaign activity count;
- campaign total expenses;
- campaign/farm/parcel harvest totals;
- kg/ha;
- €/ha;
- €/kg;
- recent activity.

Caches/materialized projections may be introduced later for performance but must be reproducible from source records.

---

# 22. Phase 0.3 Gate checklist

Before declaring Phase 0.3 complete, verify:

- [ ] every RC1 entity has a clear owner/workspace boundary;
- [ ] every many-to-many relation has an explicit join entity;
- [ ] parcel ↔ farm history cannot be destroyed by a simple reassignment;
- [ ] campaign history is protected by snapshots;
- [ ] offline-created IDs do not depend on server insertion;
- [ ] money and measurement units are explicit;
- [ ] parcel geometry survives without Catastro connectivity;
- [ ] activity types can grow without a single giant untyped form/table;
- [ ] harvest and delivery are distinct;
- [ ] unknown harvest allocation remains representable;
- [ ] attachments are local-first;
- [ ] sync conflicts are representable without silent overwrite;
- [ ] future tasks/equipment/products/zones can reference the core without changing core identity;
- [ ] RLS can authorize every remote row through workspace membership;
- [ ] historical reports can be reproduced after later farm/parcel edits.

If all items pass, Phase 0.3 can be marked **BASELINE APPROVED** and work proceeds to Phase 0.4 — Catastro Contract.
