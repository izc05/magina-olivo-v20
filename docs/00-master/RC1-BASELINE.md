# Olive Farm App — RC1.2 Baseline

**Status:** APPROVED BASELINE  
**Baseline ID:** RC1.2-BASELINE-2026-09-18  
**Supersedes:** RC1.2-BASELINE-2026-09-18  
**Approved change:** `docs/00-master/RC1.2-CHANGE-REQUEST.md`  
**Normative product lock:** `docs/00-master/RC1.2-PRODUCT-LOCK.md`

**Rule:** this document is the source of truth for the RC1.1 work line. Structural changes require an explicit Change Request and a new baseline revision. Do not silently change architecture, scope, navigation or phase ordering.

## Product identity

The product is a native Android application for private management of one or more olive-growing holdings. The public brand is pending final naming. Initial implementation targets Spain, while the domain is designed for olive growers in other countries without rebuilding the core.

It is **not** a revival of the old territorial V20 portal. The agricultural core comes first. Home may consume selected contextual information such as weather/radar, reference olive-oil prices and preferred-cooperative notices/news, but those services are secondary and must never block field-management workflows.

The immutable core is:

```text
FINCA
  ↓
PARCELA
  ↓
CAMPAÑA
  ↓
ACTUACIONES
  ↓
GASTOS / COSECHA / ENTREGAS
  ↓
HISTÓRICO
  ↓
INFORMES
```

## RC1.1 core scope

RC1.1 must cover, through the ordered roadmap and Gates:

- account and private workspace;
- onboarding;
- named farms with optional user cover photo;
- agricultural parcels with app-owned identity/geometry plus provider-specific land-registry links; Spain supports Catastro;
- agricultural campaigns and historical campaign review;
- field activities;
- irrigation provider/community + sector where relevant;
- planned work, calendar and local Android reminders;
- machinery as an operational resource;
- expenses, purchases and reusable supplier/cooperative/company references;
- harvests;
- olive deliveries;
- ticket/photo/PDF attachment and generic OCR review with human confirmation for deliveries, invoices, receipts and agricultural documents;
- yield analysis added after delivery without rewriting the delivery;
- photos and documents;
- parcel/campaign timeline/history;
- useful historical charts and comparisons;
- contextual Home: weather/radar, campaign/upcoming work, olive-oil reference market, preferred cooperative notices/news;
- profile preferences including country/region/locality, locale/timezone/currency/units and preferred cooperative;
- deferred synchronization;
- separate private Admin web surface;
- campaign/farm/parcel reporting and PDF;
- Android APK validation on real hardware;
- controlled test-track preparation before Google Play release.

## Explicitly outside RC1.1

The following do not enter RC1.1 unless a later approved Change Request adds them:

- professional/client mode for managing third-party owners' holdings;
- public territorial portal;
- tourism/community portal;
- advertising marketplace;
- active loyalty/gamification rewards;
- general editorial feed unrelated to the selected cooperative/agricultural context;
- AI agronomic assistant;
- advanced sensors/IoT/automation.

Ideas in these areas go to later backlog and do not interrupt the active Gate.

## Immutable architectural decisions

1. **Android first.** RC1.1 delivers a real installable Android APK.
2. **Kotlin + Jetpack Compose** for the Android client.
3. **Offline-first.** Normal field work must not depend on connectivity.
4. **Room is the local operational source of truth.** UI reads local state.
5. **Write locally first.** Saving a field record succeeds locally before remote sync.
6. **WorkManager/outbox model** for durable deferred synchronization.
7. **Supabase is the remote backend**, behind repository/service abstractions.
8. **PostgreSQL/PostGIS** stores synchronized structured/geographic data.
9. **MapLibre-compatible map abstraction** for Android map rendering.
10. **Catastro is an external source, not the application's database.** Imported geometry/identity is normalized into Mágina Olivo.
11. **Agricultural parcel identity is independent from Catastro identity.** Do not assume permanent 1:1 equivalence.
12. **Campaign historical snapshots are mandatory.** Later parcel changes must not rewrite prior campaign history.
13. **Harvest and Delivery are distinct.** Mixed-origin production must not be fabricated.
14. **OCR is assistive.** Extracted ticket values require user review/confirmation.
15. **Yield is later analysis.** Adding it never rewrites the original delivery.
16. **Expense ledger is authoritative.** Activity UI must not duplicate monetary totals.
17. **Soft delete/versioning** for critical historical entities.
18. **External Home feeds are optional/cached.** Failure must not block agricultural operation.
19. **Feature growth is modular.** Optional modules depend on the stable core, never the reverse.
20. **Primary navigation is frozen:** Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil.
21. **Admin is separate from Android.** No hidden mobile admin tab.
22. **No feature is Done** until UI, persistence, validation, offline behavior, tests and synchronization (when applicable) are covered.

## Scalability principle

```text
LEVEL 1 — RC1.1 FIELD CORE
Fincas · Parcelas · Campañas · Actuaciones · Riego · Cosecha · Entregas · Gastos · Documentos

LEVEL 2 — RC1.1 OPERATIONAL SUPPORT
Maquinaria · Calendario · Recordatorios · Proveedores/Compras · Histórico/Gráficas · Inicio contextual · Perfil

LEVEL 3 — RC1.1 PLATFORM SUPPORT
Catastro/Mapas · Sync · Admin · Informes/PDF · QA/Play test tracks

POST-RC1.1
Modo profesional/clientes · Fidelización activa · Publicidad · IA · Sensores/automatización
```

## Priority order

```text
1. DO NOT LOSE DATA
2. WORK OFFLINE
3. BE SIMPLE TO USE
4. IDENTIFY PARCELS CORRECTLY
5. PRESERVE HISTORY
6. SYNCHRONIZE SAFELY
7. BE VISUALLY ATTRACTIVE AND CONSISTENT
```

Visual polish is required, but never overrides data integrity, truthful data or field usability.

## Mandatory development discipline

Each phase follows:

```text
Read baseline + Product Lock + current phase plan
→ work only on the assigned phase
→ isolated branch/worktree
→ tests first where practical
→ implement
→ build
→ verify on relevant device/data
→ review diff
→ commit
→ PR
→ pass Gate
→ integrate
→ mark Gate complete
→ next phase
```

Do not skip phases because a later feature looks attractive.

## RC1.1 success criterion

RC1.1 is accepted when a real user can manage an olive campaign end to end, including periods without mobile coverage, and can:

- identify their farms/parcels correctly;
- keep campaign history;
- record what was done and planned;
- control irrigation reminders and machinery relations;
- know what it cost and where products were purchased;
- record harvest and delivery;
- photograph/read/review delivery tickets;
- add later yield truthfully;
- review historical kg/dates/yields;
- use Catastro/map references;
- consult contextual weather/market/cooperative information without making it a dependency;
- generate useful reports;
- install and use a validated Android build on real hardware.
