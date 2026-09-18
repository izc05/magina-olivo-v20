# RC1.2 Figma Build Manifest

**Status:** EXECUTION MANIFEST  
**Target file:** `Olive Farm App — RC1.2 Design System & Mobile`  
**Figma file key:** `4bJUPpc0027FZkM8Qg7OUj`  
**Public brand:** pending Naming Gate  
**Working mark:** OleaTrack

This manifest defines the exact order and scope for Figma work once MCP access is available again.

## 1. File pages — exact order

```text
00 Cover
01 Getting Started
02 Foundations
03 Components
04 Onboarding
05 Inicio
06 Mi Olivar
07 Finca
08 Parcela
09 Campaña
10 Producción
11 Costes
12 Rentabilidad
13 Registrar
14 Calendario
15 Entrega OCR
16 Factura OCR
17 Documentos
18 Perfil
19 States & QA
20 Marketing Assets
```

Do not create extra pages without a documented need.

## 2. Foundations

Create and document:

### Color
- primitive palette;
- semantic app colors;
- success/warning/error/info;
- data-series colors;
- weather-state overlays.

### Typography
- display/lg;
- headline/lg;
- headline/md;
- title/lg;
- title/md;
- body/lg;
- body/md;
- label/lg;
- label/md;
- metric/xl;
- metric/lg.

### Spacing
- 4 / 8 / 12 / 16 / 24 / 32 / 48.

### Radius
- 10 / 14 / 18 / 24 / full.

### Elevation
- 0 / 1 / 2 / 3.

### Icon sizes
- 20 / 24 / 28 / 32.

## 3. Component creation order

Build atoms before composed cards.

### C01 Button
Variants:
- Primary;
- Secondary;
- Tertiary;
- Destructive.

States:
- Default;
- Pressed;
- Disabled;
- Loading.

Sizes:
- Standard only for v1 unless a real need appears.

### C02 IconButton
- Default;
- Selected;
- Disabled.

### C03 TextField
- Text;
- Numeric;
- Money;
- Quantity.

States:
- Default;
- Focus;
- Filled;
- Error;
- Disabled.

### C04 SelectField
- Default;
- Selected;
- Error;
- Disabled.

### C05 DateTimeField
- Date;
- Time;
- DateTime.

### C06 StatusChip
Semantic states:
- Active;
- Planned;
- Closed;
- Pending;
- Confirmed;
- Error;
- Offline;
- Estimate.

### C07 SectionHeader
Properties:
- title;
- optional action text;
- optional action icon.

### C08 MetricCard
Properties:
- label;
- value;
- unit;
- support text;
- icon;
- status.

States:
- Normal;
- Partial;
- Estimate;
- Unavailable.

### C09 FarmCard
Properties:
- cover;
- farm name;
- parcel count;
- area;
- active campaign;
- optional recent metric.

States:
- With image;
- Without image;
- Offline image pending.

### C10 ParcelRow
Properties:
- name;
- area;
- tree count;
- variety;
- irrigation state;
- navigation chevron.

### C11 ActivityRow
Properties:
- type;
- date;
- parcel/farm;
- status;
- optional detail.

### C12 DeliveryRow
Properties:
- date;
- kg;
- destination;
- ticket;
- yield state;
- OCR state.

### C13 ExpenseRow
Properties:
- supplier/category;
- date;
- amount;
- attachment state;
- OCR state.

### C14 ReminderRow
Properties:
- date/time;
- activity type;
- farm/parcel;
- reminder state.

### C15 WeatherCard
Properties:
- location;
- condition;
- temperature;
- precipitation;
- wind;
- freshness;
- radar CTA.

Visual states:
- Clear;
- Cloudy;
- Rain;
- Fog;
- Wind;
- Storm;
- Stale/offline.

### C16 OilMarketCard
Properties:
- AOVE;
- Virgen;
- Lampante;
- source;
- freshness;
- time-range selector.

### C17 CooperativeCard
Properties:
- organization;
- notice;
- news;
- freshness.

### C18 ChartContainer
Variants:
- Line;
- Bar;
- Combo;
- Donut;
- Empty;
- Partial.

Donut is only used when composition genuinely benefits from it.

### C19 EmptyState
Properties:
- icon/illustration;
- title;
- body;
- primary CTA;
- optional secondary CTA.

### C20 ErrorState
- Recoverable;
- Offline;
- Permission;
- External feed unavailable.

### C21 OfflineBanner
- Offline;
- Sync pending;
- Stale external data.

### C22 SyncStatus
- Local;
- Pending;
- Synced;
- Error.

### C23 AttachmentTile
- Image;
- PDF;
- Generic doc;
- Upload pending;
- OCR pending.

### C24 OCRFieldReview
Properties:
- field label;
- extracted value;
- confidence state;
- editable confirmed value.

### C25 OCRReviewPanel
States:
- Processing;
- Needs review;
- Confirmed;
- Failed.

### C26 BottomNavigation
Frozen destinations:
- Inicio;
- Mi Olivar;
- Registrar;
- Calendario;
- Perfil.

### C27 TopAppBar
Variants:
- Root;
- Back;
- Farm;
- Parcel;
- Campaign.

### C28 BottomActionSheet
Used for:
- Register chooser;
- context actions;
- destructive confirmation.

## 4. Reference screens — build order

Do not build every screen at once.

### R1 Onboarding
Build five frames:
- O1 Tu olivar
- O2 Registra trabajos
- O3 Controla tu cosecha
- O4 Organiza lo que viene
- O5 Todo tu olivar contigo

### R2 Inicio
Must demonstrate:
- weather hero;
- campaign/farm summary;
- upcoming work;
- market;
- cooperative;
- bottom nav.

### R3 Mi Olivar
- empty state;
- populated state;
- multiple farm cards.

### R4 Finca
- cover image;
- farm summary;
- parcels;
- current campaign;
- activity.

### R5 Parcela
- agronomic details;
- campaign;
- work;
- history;
- location placeholder.

### R6 Campaña / Resumen
- key campaign metrics;
- data coverage;
- recent activity;
- upcoming work.

### R7 Producción
- hero metrics;
- kg/yield evolution;
- deliveries;
- partial-yield state.

### R8 Costes
- total/cost ratios;
- dynamic activity breakdown;
- purchases;
- OCR invoice status.

### R9 Rentabilidad
- net result;
- margin;
- income/cost/result ratios;
- campaign comparison.

### R10 Registrar
- action chooser;
- typed form example;
- planned/completed state.

### R11 Calendario
- month/agenda;
- planned work;
- people/provider;
- reminders.

### R12 Entrega OCR
- add document;
- extracted draft;
- human confirmation;
- yield pending.

### R13 Factura OCR
- supplier;
- invoice number/date;
- line items;
- tax/total;
- human review;
- create Purchase/Expense draft.

### R14 Perfil
- account;
- region/location;
- preferred cooperative;
- notifications;
- units/currency;
- data/export/help.

## 5. Mandatory state frames

At minimum create:

- loading;
- empty;
- offline;
- stale external feed;
- validation error;
- destructive confirmation;
- OCR failed;
- OCR needs review;
- yield pending;
- sync pending;
- no Catastro/geometry;
- no preferred cooperative;
- no market data.

## 6. Phone widths

Mandatory reference widths:

- 360dp compact;
- 393dp primary;
- 412dp common large;
- 480dp large phone.

Primary design source is 393dp. Validate critical screens at 360dp and 412dp before approval.

## 7. Prototype flows

Build clickable prototype paths for:

### F1 First use
Onboarding → Inicio empty → Add Farm.

### F2 Add agricultural structure
Mi Olivar → Add Farm → Farm → Add Parcel.

### F3 Register field work
+ Register → Activity Type → Parcel(s) → Details → Save.

### F4 Irrigation planning
Calendar/Register → Irrigation → Community/Sector → Date/time → Reminder → Save.

### F5 Delivery OCR
+ Register → Delivery → Camera/file → OCR → Review → Confirm → Yield pending.

### F6 Invoice OCR
+ Register → Expense/Purchase → Camera/file → OCR → Review items → Confirm draft.

### F7 Campaign analytics
Farm → Campaign → Summary → Production → Costs → Profitability → History.

## 8. Design QA checklist

Before Figma Gate 3:

- every reference screen uses variables/tokens;
- no ad-hoc olive shades;
- no component duplicates with slightly different geometry;
- touch targets ≥48dp;
- content survives Android font scaling assumptions;
- charts have textual labels/summary;
- OCR states are visibly distinct;
- partial data never looks complete;
- stale data never looks live;
- offline core remains understandable;
- no final brand assets exported before Naming Gate.

## 9. Code handoff

Every production component should map to a Compose counterpart with the same semantic name where practical:

```text
MoPrimaryButton
MoMetricCard
MoFarmCard
MoParcelRow
MoDeliveryRow
MoExpenseRow
MoWeatherCard
MoOilMarketCard
MoOfflineBanner
MoOcrReviewPanel
MoBottomNav
MoTopAppBar
```

Code Connect is considered after component APIs stabilize.

## 10. Figma access blocker

Current Starter/View MCP limits prevented completing Figma discovery and variable/component creation on 2026-09-18.

Until access resumes:

- repository specs are source of truth;
- do not claim Figma components/tokens exist;
- do not duplicate speculative Figma work elsewhere;
- resume at Phase 0 inspection, then Phase 1 foundations.
