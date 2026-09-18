# Olive Farm App — RC1.2 Data Model Addendum

**Status:** NORMATIVE  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Extends:** `docs/02-domain/DATA-MODEL-RC1.1-ADDENDUM.md`

## 1. Workspace geographic/runtime preferences

Add explicit context instead of hard-coding Jaén/Spain assumptions:

```text
workspace_preferences
  workspace_id UUID PK
  country_code String          // ISO 3166-1 alpha-2, e.g. ES
  region String?
  locality String?
  timezone String              // IANA, e.g. Europe/Madrid
  locale String                // e.g. es-ES
  currency String              // ISO 4217, e.g. EUR
  area_unit HECTARE | ACRE
  volume_unit LITER | M3 | OTHER
  mass_unit KG | TONNE
  updated_at Instant
```

Spain defaults are allowed in onboarding, but persistence stays explicit.

## 2. Provider-neutral land registry links

Replace Catastro-specific coupling with a generic external-link model:

```text
parcel_registry_links
  id UUID
  parcel_id UUID FK
  provider SPAIN_CATASTRO | MANUAL | IMPORTED_GEOMETRY | OTHER
  external_reference String?
  country_code String
  provider_metadata Json?
  imported_geometry Geometry?
  imported_area_m2 Decimal?
  imported_at Instant?
  active Boolean
  created_at / updated_at / deleted_at / version
```

Spain Catastro metadata may still be normalized into provider metadata or a typed Spain extension.

## 3. Generic OCR extraction

Create one OCR service model for all document types:

```text
document_ocr_extractions
  id UUID
  workspace_id UUID
  attachment_id UUID
  document_type DELIVERY_TICKET
              | PURCHASE_INVOICE
              | PURCHASE_RECEIPT
              | PHYTOSANITARY_INVOICE
              | FERTILIZER_INVOICE
              | IRRIGATION_INVOICE
              | GENERIC_AGRICULTURAL_DOCUMENT
  owner_type DELIVERY | PURCHASE | EXPENSE | ACTIVITY | IRRIGATION | DOCUMENT | OTHER
  owner_id UUID?
  engine String
  engine_version String?
  raw_text String?
  extracted_json Json?
  confidence_json Json?
  status PENDING | EXTRACTED | NEEDS_REVIEW | CONFIRMED | FAILED
  reviewed_at Instant?
  confirmed_at Instant?
  created_at / updated_at / version
```

Rules:

- attachment is the immutable source artifact;
- extraction is assistive data;
- confirmed domain values live in their canonical entities;
- a confirmed extraction can create/update a **draft** domain record only through an explicit reviewed command;
- financial posting still requires the Expense/Purchase workflow.

## 4. Typed extraction schemas

### Delivery ticket proposal

Possible extracted fields:

```text
organization_name?
ticket_number?
delivery_date?
delivery_time?
gross_kg?
tare_kg?
net_kg?
member_reference?
vehicle_reference?
other_fields?
```

### Purchase invoice/receipt proposal

Possible extracted fields:

```text
supplier_name?
supplier_tax_id?
invoice_number?
invoice_date?
subtotal?
tax_amount?
total_amount?
currency?
items[] {
  description
  quantity?
  unit?
  unit_price?
  line_total?
}
```

No field is trusted until reviewed.

## 5. Irrigation tariff snapshot

```text
irrigation_price_snapshots
  id UUID
  activity_id UUID UNIQUE FK
  pricing_basis PER_M3 | PER_HOUR | PER_EVENT | PER_HECTARE | INVOICE_TOTAL | OTHER
  unit_price_minor Long?
  quantity Decimal?
  estimated_amount_minor Long?
  currency String
  price_date LocalDate
  linked_expense_id UUID?
  notes String?
  created_at / updated_at / deleted_at / version
```

The linked Expense remains the authoritative actual cost.

## 6. Planned-work details

Extend planned activities or add a thin planning detail:

```text
activity_planning_details
  activity_id UUID PK
  planned_start Instant?
  planned_end Instant?
  expected_people_count Int?
  provider_organization_id UUID?
  crew_text String?
  expected_duration_minutes Int?
```

A planned activity should normally become the completed activity; do not duplicate it merely because work happened.

## 7. Weather visual presentation model

Weather visual effects are presentation state, not agricultural records.

```text
WeatherVisualState
  condition CLEAR | CLOUDY | RAIN | STORM | FOG | WIND | OTHER
  precipitationIntensity?
  cloudCover?
  windSpeed?
  isDay
  freshness
  reducedMotion
  performanceTier
```

Do not persist this as canonical farm history unless a later weather-history feature is explicitly approved.

## 8. Country-aware external market data

Extend market observation:

```text
oil_market_observations
  country_code String
  market_scope String?
  category AOVE | VIRGEN | LAMPANTE | OTHER
  observed_at Instant
  price Decimal
  currency String
  unit String
  source_id String
  fetched_at Instant
```

Categories are provider-dependent. Unsupported categories remain absent, never inferred.

## 9. Branding metadata

Brand/display strings must not be embedded in domain tables.

The final public name/logo/icon is an application/configuration concern and may change without migrating farm data.
