# Mágina Olivo — RC1.1 Data Model Addendum

**Status:** NORMATIVE  
**Baseline:** `RC1.1-BASELINE-2026-09-18`  
**Extends:** `docs/02-domain/DATA-MODEL-RC1-FUTURE.md`

This addendum adds the entities required by RC1.1. Existing global conventions remain: client-generated UUIDs, UTC machine timestamps, local agricultural dates, soft delete/versioning where history matters, local-first persistence and later synchronized remote replicas.

## 1. Farm cover

Do not store a raw image blob on `farms`.

```text
farms
  cover_attachment_id UUID? FK -> attachments.id
```

The original attachment remains available; UI may derive/cache thumbnails.

## 2. Parcel ↔ Catastro identity

Agricultural parcel identity is app-owned. Do not model Catastro as the parcel primary key.

```text
parcel_cadastre_links
  id UUID
  parcel_id UUID FK
  cadastral_reference String?
  province_code String?
  municipality_code String?
  polygon_code String?
  parcel_code String?
  source String
  imported_geometry Geometry?
  imported_area_m2 Decimal?
  imported_at Instant
  active Boolean
  created_at / updated_at / deleted_at / version
```

One agricultural parcel may eventually have more than one cadastral link. The current UI can start with the common one-link case.

## 3. Agricultural organizations

```text
agricultural_organizations
  id UUID
  workspace_id UUID
  name String
  municipality String?
  province String?
  address String?
  phone String?
  website String?
  notes String?
  created_at / updated_at / deleted_at / version
```

Roles are separate so one organization can be both mill and supplier:

```text
organization_roles
  organization_id UUID
  role COOPERATIVE | MILL | SUPPLIER | IRRIGATION_PROVIDER | WORKSHOP | SERVICE_PROVIDER | OTHER
```

Never duplicate an organization merely because it is used in two flows.

## 4. User preferences

```text
user_preferences
  workspace_id UUID PK
  preferred_municipality String?
  preferred_cooperative_id UUID?
  notify_previous_day Boolean
  notify_same_day Boolean
  default_notification_time LocalTime?
  unit_preferences Json?
  updated_at Instant
```

GPS is not required for municipality/cooperative personalization.

## 5. Machinery

```text
machines
  id UUID
  workspace_id UUID
  name String
  category TRACTOR | ATOMIZER | MOWER | PRUNER | HARVEST | TRAILER | TOOL | OTHER
  make String?
  model String?
  registration_or_serial String?
  current_hours Decimal?
  notes String?
  created_at / updated_at / deleted_at / version
```

```text
activity_machines
  activity_id UUID
  machine_id UUID
  start_hours Decimal?
  end_hours Decimal?
  usage_hours Decimal?
```

RC1.1 does not require a full maintenance/insurance subsystem.

## 6. Irrigation provider and sector

Prefer reuse of `agricultural_organizations` with role `IRRIGATION_PROVIDER`.

```text
irrigation_sectors
  id UUID
  workspace_id UUID
  provider_organization_id UUID?
  name String
  notes String?
  created_at / updated_at / deleted_at / version
```

Typed irrigation detail:

```text
irrigation_details
  activity_id UUID PK
  provider_organization_id UUID?
  sector_id UUID?
  duration_minutes Int?
  volume_m3 Decimal?
  irrigation_system String?
```

## 7. Reminders

```text
reminders
  id UUID
  workspace_id UUID
  owner_type ACTIVITY | IRRIGATION | OTHER
  owner_id UUID
  trigger_at Instant
  kind PREVIOUS_DAY | SAME_DAY | CUSTOM
  enabled Boolean
  local_notification_id String?
  fired_at Instant?
  created_at / updated_at / deleted_at / version
```

Android notification scheduling is local-first. Remote push is not required to satisfy field reminders.

## 8. Purchases and expense linkage

Money remains authoritative in `expenses`.

Optional purchase detail can normalize what was bought without double-counting money:

```text
purchases
  id UUID
  workspace_id UUID
  expense_id UUID UNIQUE FK
  supplier_organization_id UUID?
  purchase_date LocalDate
  invoice_or_ticket_number String?
  notes String?
  created_at / updated_at / deleted_at / version
```

```text
purchase_items
  id UUID
  purchase_id UUID
  product_name String
  quantity Decimal?
  unit String?
  unit_price_cents Long?
```

The sum of purchase items is descriptive/validated against the linked Expense; it does not create a second financial ledger.

## 9. Delivery parcel allocation

A delivery may have one or multiple parcel origins.

```text
delivery_parcels
  delivery_id UUID
  parcel_id UUID
  allocated_kg Decimal?
  allocation_state EXACT | UNALLOCATED_MIXED
```

Rules:

- exact allocations may sum to delivered kg;
- mixed/unallocated mode is valid;
- never estimate/fabricate a split simply to fill parcel metrics.

## 10. Delivery OCR

Keep the original attachment and OCR review lifecycle separate from confirmed delivery truth.

```text
delivery_ocr_extractions
  id UUID
  delivery_id UUID
  attachment_id UUID
  engine String
  engine_version String?
  raw_text String?
  extracted_json Json?
  status PENDING | EXTRACTED | NEEDS_REVIEW | CONFIRMED | FAILED
  confidence_json Json?
  reviewed_at Instant?
  created_at / updated_at / version
```

Confirmation writes reviewed values to the normal Delivery fields through an audited application command. Never treat raw OCR output as confirmed truth.

## 11. Yield analysis

```text
delivery_yield_analyses
  id UUID
  delivery_id UUID FK
  analysis_date LocalDate?
  fat_yield_percent Decimal?
  industrial_yield_percent Decimal?
  source_document_attachment_id UUID?
  notes String?
  created_at / updated_at / deleted_at / version
```

A delivery can exist indefinitely without analysis. Adding analysis later must not mutate ticket date, confirmed delivered kg or original attachment.

Weighted summary example:

```text
weighted_yield =
  SUM(delivery.confirmed_kg * analysis.yield_percent)
  / SUM(delivery.confirmed_kg with valid analysis)
```

UI must also expose analysis coverage (kg analysed / total delivered kg).

## 12. External Home cache

External/context data is not the agricultural source of truth.

```text
oil_market_observations
  id UUID
  category AOVE | VIRGEN | LAMPANTE
  observed_at Instant
  price Decimal
  unit String
  source_id String
  fetched_at Instant
```

```text
cooperative_content_cache
  id UUID
  organization_id UUID
  source_url String
  title String
  published_at Instant?
  fetched_at Instant
  canonical_url String?
```

Weather/radar can use an infrastructure cache rather than durable domain tables unless offline requirements later justify persistence.

Rules for all external cache:

- source + freshness visible;
- stale/failed refresh does not block agricultural UI;
- never rewrite campaign/farm/parcel truth.

## 13. Derived history/chart projections

Charts are projections from canonical entities, not duplicate editable tables.

Required projections include:

- kg by campaign;
- kg by harvest/delivery date;
- yield by delivery/date;
- weighted yield by campaign/parcel where allocation is exact;
- cost by campaign/farm/parcel;
- reminder/upcoming-work projection.

If a metric cannot be calculated truthfully because allocations or analyses are missing, return partial/unknown state rather than inventing data.

## 14. Post-RC1.1 reservation

Professional mode can later add:

```text
clients / workspace_parties / managed_holdings
```

without changing `farm.id`, `parcel.id`, campaign history or the personal RC1.1 workflow.
