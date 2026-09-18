# Olive Farm App — Master Spec RC1.2

**Baseline:** `RC1.2-BASELINE-2026-09-18`
**Normative override:** `docs/00-master/RC1.2-PRODUCT-LOCK.md` supersedes contradictory RC1/RC1.1 wording. Public brand is TBD.
**Product sentence:** _Tu olivar, finca por finca, parcela por parcela y campaña por campaña._

## 1. Product goal

The product is a private Android field-management application for one or more olive holdings, initially implemented for Spain and designed for olive-growing regions beyond Spain. It helps the user answer:

1. What farms/parcels do I manage?
2. What have I done in them?
3. What has it cost?
4. What have I harvested/delivered?
5. What happened in each campaign historically?

The app must remain simple enough to use in the field with one hand and intermittent/no connectivity.

## 2. Primary user journey

```text
Install
→ account/session
→ create farm
→ add real parcel from Catastro/import
→ start campaign
→ register field work
→ add expense/photo/document
→ register harvest
→ register delivery
→ review parcel/campaign history
→ generate report
→ close campaign
```

## 3. Primary navigation

Permanent bottom navigation:

```text
Inicio | Mi Olivar | Registrar (+) | Calendario | Perfil
```

This navigation is frozen by RC1.1. Farm, parcel and campaign screens are reached through `Mi Olivar`; deep screens do not add primary tabs.

`Registrar` is the principal operational action and can be context-aware:

- from parcel: parcel preselected;
- from farm: farm preselected;
- from campaign: campaign preselected;
- from Home: user selects target.

Adding future Mi Campo modules must not add a large number of primary tabs.

## 4. Home

Purpose: answer "what matters today?" without turning Mágina Olivo into a portal.

Core/priority content:

- current olive-grove/campaign summary;
- upcoming planned work/reminders;
- prominent `+ Registrar`;
- farms/parcels and current harvest totals when useful.

Contextual secondary blocks:

- selected municipality weather and radar access;
- reference olive-oil market with AOVE/Virgen/Lampante series when the configured source supports them;
- preferred cooperative notices/news.

External Home data is optional/cached and must never block agricultural workflows. Always show source/last update for market data. Do not add unrelated general news, tourism feeds or advertising in RC1.1.

## 5. Farms

A farm is a user-defined logical grouping of parcels.

Required flows:

- list farms;
- create farm quickly (name required, other metadata optional);
- optional user-selected farm cover photo;
- open farm;
- edit;
- archive;
- show map with owned parcel geometry;
- show parcel list;
- show active campaign summary;
- show farm documents;
- context-aware Register action.

Current area/parcel count are derived from active memberships rather than manually maintained totals.

## 6. Parcels

A parcel is the primary geographic unit.

Required fields/behavior:

- stable app UUID;
- user display name;
- cadastral identity when available;
- cadastral polygon/parcel metadata;
- municipality/province;
- stored polygon/multipolygon geometry;
- cadastral and managed surface;
- notes/photos/documents;
- optional agronomic profile;
- current farm membership plus historical membership records.

### Parcel import methods

RC1 target methods:

1. visual selection through cadastral map;
2. reference-based cadastral lookup;
3. GML import.

GeoJSON/manual geometry can be enabled when integration infrastructure is ready.

### Parcel detail

Must prioritize the map and show:

- name;
- area;
- cadastral identity;
- active campaign;
- quick Register action;
- latest work;
- current production when data exists;
- chronological timeline.

## 7. Catastro contract intent

Catastro is a discovery/import source. Mágina Olivo stores its own normalized parcel record and geometry after import.

The normal farm/parcels UI must not require repeated cadastral calls after a parcel has been added.

No mass scraping/downloading of cadastral cartography belongs to the product.

## 8. Campaigns

Campaign states:

```text
PREPARATION → ACTIVE → HARVEST → CLOSED
```

A campaign captures participating parcels with historical snapshots.

Required behavior:

- create;
- choose included parcels;
- start/activate;
- show summary;
- record activities/expenses/harvest/deliveries;
- move to harvest state;
- close through explicit confirmation;
- retain as immutable-ish historical record;
- consult older campaigns.

A closed campaign is protected against accidental edits. Any future reopening mechanism must be explicit and audited.

## 9. Register activity engine

The global Register entry asks what the user wants to record.

RC1 activity families:

- Observación;
- Poda;
- Abonado;
- Tratamiento fitosanitario;
- Suelo/desbroce/laboreo;
- Riego;
- Mantenimiento;
- Incidencia;
- Otro;
- plus separate shortcuts for Cosecha and Gasto.

One activity may apply to multiple parcels.

Do not build one giant form. Each type has a common activity header and typed detail fields.

### Common activity data

- campaign;
- date;
- selected parcels;
- title/description where useful;
- operator text/responsible;
- machinery text;
- direct cost where useful;
- notes;
- photos/documents;
- status.

### Poda

- pruning type;
- worker count;
- hours;
- machinery;
- residue management;
- cost;
- photos/notes.

### Abonado

- product;
- quantity/unit;
- dose/unit;
- application method;
- affected parcels/area;
- cost;
- notes/photos.

### Tratamiento

- product;
- active substance when known;
- quantity;
- dose;
- reason;
- equipment;
- cost;
- notes/photos.

### Suelo

- work type;
- method;
- machinery;
- hours/cost as applicable;
- notes/photos.

### Riego

- parcel(s);
- planned/completed date and time where useful;
- duration;
- volume if known;
- reusable irrigation community/company when configured;
- reusable sector when configured;
- system;
- cost/notes;
- optional local Android reminder for previous day and/or same day.

Advanced hydraulic telemetry/automation remains post-RC1.1.

### Incidencia

- category;
- severity;
- location when useful;
- description;
- photos;
- state: open/monitoring/resolved;
- action taken;
- resolution timestamp.

## 10. Drafts and field resilience

Longer forms should protect unfinished user input. If the app is interrupted while entering a record, the user should be able to resume rather than start over.

Required fields must be minimal. Missing secondary data must not prevent saving a valid field observation/work record.

## 11. Expenses

Expenses can relate to campaign/farm/parcel/activity/harvest/delivery.

Initial categories:

- labor;
- products;
- machinery;
- fuel;
- irrigation;
- external services;
- repairs;
- harvest;
- transport;
- other.

The app is not a general accounting product. Its economic purpose is agricultural cost understanding.

Derived metrics may include:

- total campaign cost;
- cost/farm;
- cost/parcel;
- €/ha;
- €/kg when production exists.

## 12. Harvest

Harvest is distinct from a generic activity.

Required:

- date;
- campaign;
- one/multiple parcels;
- total kg;
- exact per-parcel kg where known;
- explicit unallocated mode where not known;
- collection method;
- workers/machinery text;
- notes/photos/documents.

The system never fabricates a parcel production split.

## 12.5 Generic OCR

OCR is a shared document capability. It may assist with delivery/weight tickets, supplier invoices/receipts, phytosanitary/fertilizer documents, irrigation invoices and generic agricultural documents. Extracted values stay in review/draft state until the user confirms them; OCR must never auto-post financial records.

## 13. Deliveries

Delivery is distinct from harvest.

Required:

- date;
- reusable destination organization (cooperative/mill);
- delivered kg;
- delivery/albarán/ticket numbers when available;
- original ticket/photo/PDF;
- OCR extraction state and extracted values;
- mandatory human review/correction before OCR values become confirmed delivery fields;
- notes.

Yield is later analysis linked to the delivery:

- analysis date;
- fat yield when available;
- industrial yield when available;
- other source values when supported.

Adding or correcting yield must not rewrite the original delivery. Mixed-origin deliveries may reference several parcels; if the exact split is unknown, keep kg unallocated rather than fabricating parcel totals.

Harvested and delivered totals may legitimately differ during the campaign.

## 14. Documents and photos

Attachments can belong to:

- farm;
- parcel;
- campaign;
- activity;
- expense;
- harvest;
- delivery.

Supported operational types include photos, PDF, ticket, invoice, plan, GML and generic document.

Attachments are local-first. Upload failure must not destroy the local attachment reference.

## 15. Parcel timeline

Each parcel has a chronological operational history showing relevant:

- activities;
- incidents;
- harvests;
- selected documents/photos;
- campaign context.

Timeline is a projection, not a duplicate source table.

## 16. Reports

RC1 report priority:

1. Campaign report.
2. Farm report.
3. Parcel report.

Campaign report should contain, where data exists:

- campaign identity/period;
- farms/parcels/surface;
- map;
- work summary;
- pruning/fertilization/treatments;
- harvest;
- deliveries/yields;
- expenses;
- derived production/cost indicators;
- relevant appendices.

Reports can be previewed/exported/shared as PDF.

Historical reports use campaign snapshots, not today's parcel values when they differ.

## 17. Offline-first UX

The user must be able to:

- open locally known farms/parcels/campaigns;
- see owned parcel boundaries;
- create/edit normal field records;
- register expense/harvest/delivery;
- capture photos;
- close/reopen the app;

without active coverage.

Visible sync state is concise and non-intrusive:

```text
✓ Sincronizado
↑ N cambios pendientes
⚠ Problema de sincronización
```

Do not block field work merely because the server is unreachable.

## 18. Permissions

Ask permissions at the moment of use:

- location when user requests own position/map centering;
- camera when taking a photo;
- documents through Android picker.

No permanent location tracking is required for RC1.

## 19. Visual principles

Identity: modern olive agriculture + simple technology.

Use:

- clean light surfaces;
- olive-green identity accents;
- highly legible typography;
- large touch targets;
- map-first parcel/farm screens;
- restrained cards and icons;
- real field photography where helpful.

Avoid:

- rustic wood textures;
- decorative dashboards;
- crowded menus;
- tiny buttons;
- information communicated by color alone.

## 20. RC1.1 operational support and later growth

RC1.1 includes lightweight operational support for:

- calendar/planned work/local reminders;
- machinery relations;
- reusable agricultural organizations/providers;
- purchases/products as agricultural expense context;
- irrigation provider/community and sector;
- historical charts;
- contextual Home services.

Later growth remains optional for:

- personnel;
- warehouse/inventory;
- management zones;
- advanced irrigation telemetry;
- farm geographic features (well, tank, shed, hydrant, gate, road...);
- professional/client workspaces;
- collaboration roles;
- automation/sensors;
- advanced analytics/AI;
- active loyalty/gamification.

Optional modules depend on the stable agricultural core; the core must never depend on them.

## 21. Definition of Done

A feature is not Done because its screen renders. Applicable completion includes:

- approved UX behavior;
- domain model;
- local persistence;
- validation/error states;
- offline behavior;
- synchronization behavior;
- tests;
- migration impact;
- documentation;
- real-device verification when relevant.

## 22. RC1 end-to-end acceptance

A real user must be able to:

```text
install APK
→ create/login account
→ create farm
→ import/select real cadastral parcel
→ retain geometry offline
→ create campaign
→ record multiple agricultural activities
→ record expense
→ attach photo/document
→ record harvest
→ record delivery
→ inspect parcel timeline
→ calculate valid totals/ratios
→ generate campaign report
→ close campaign
→ inspect historical campaign
→ recover synchronized data on another installation/device
```

If this journey is not robust, RC1 is not complete.
