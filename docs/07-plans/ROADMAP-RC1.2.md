# Olive Farm App — Roadmap RC1.2

**Status:** NORMATIVE  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Rule:** phases are ordered. A later phase cannot start until the current Gate passes. Parallel documentation/research may prepare later phases, but production implementation stays gate-bound.

## Current state

```text
✅ Phase 0.1–0.7 — original core contracts
✅ Phase 0.8 — RC1.2 agricultural product reconciliation
✅ Phase 0.9 — RC1.2 geographic-neutral scope + generic OCR + naming gate
▶ Phase 1 — Android Project Foundation
```

The only currently allowed implementation phase is **Phase 1**. The public brand is pending and must not be hard-coded deeper into new domain/UI work.

---

## Phase 0.8 — RC1.1 product reconciliation

Deliverables:

- approved CR-001;
- RC1.1 Baseline;
- RC1.1 Product Lock;
- reconciled Master Spec/Screen Map/Design System;
- Data Model Addendum;
- this roadmap.

**Gate 0.8:** no contradiction remains about Home, navigation, OCR/yield, machinery, irrigation reminders, providers/purchases, onboarding, history or deferred professional/loyalty scope.

## Phase 0.9 — RC1.2 product globalization / OCR generalization

Deliverables:

- approved CR-002;
- RC1.2 Product Lock;
- provider-neutral parcel-registry contract;
- generic OCR contract for delivery/purchase/irrigation/agricultural documents;
- irrigation pricing snapshot;
- scheduled-work people/provider details;
- weather visual-effect rules;
- naming gate.

**Gate 0.9:** no new implementation assumes Jaén-only identity, final “Mágina Olivo” branding, delivery-only OCR or Catastro-as-parcel-primary-key.

## Phase 1 — Android Project Foundation

Prove the engineering delivery path only:

- Gradle/Kotlin/Compose;
- DEV/STAGING/PRODUCTION identities;
- lint/unit/instrumented smoke foundations;
- CI;
- installable DEV APK;
- physical Android validation.

No farm/parcel feature work.

**Gate 1:** automated checks green + DEV APK installs/launches on a physical device + smoke test evidence.

## Phase 2 — Base application architecture

**Execution plan:** `docs/07-plans/PHASE2-BASE-ARCHITECTURE.md`

- module/package boundaries;
- dependency injection;
- repository/use-case boundaries;
- navigation contracts;
- result/error model;
- clock/UUID abstractions;
- logging policy.

**Gate 2:** architecture dependency tests + no Android UI coupled directly to remote services.

## Phase 3 — Design system implementation + reference screens

**Execution plan:** `docs/07-plans/PHASE3-DESIGN-REFERENCE.md`

Implement tokens/components from Design System:

- typography;
- spacing;
- buttons/inputs/chips/cards;
- app bars;
- status/offline components;
- chart primitives;
- image/cover treatment;
- accessibility/touch targets.

Create/freeze reference screens for:

1. Onboarding;
2. Inicio;
3. Mi Olivar;
4. Finca;
5. Parcela;
6. Registrar;
7. Campaign history;
8. Delivery/OCR review.

**Gate 3:** reference screens approved on representative phone sizes and component reuse is proven.

## Phase 4 — Navigation shell

Frozen roots:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

- stable bottom navigation;
- predictable back stack;
- context-aware Register entry;
- placeholder roots only.

**Gate 4:** no dead routes/back-stack defects in instrumentation tests.

## Phase 5 — Local database foundation

- Room schema;
- client UUIDs;
- migrations;
- soft-delete/version conventions;
- local repositories;
- attachment metadata;
- seed/dev fixtures.

**Gate 5:** migration tests + process restart persistence + airplane-mode CRUD proof on test entities.

## Phase 6 — Farms RC1.1

- create/edit/archive/restore;
- optional cover photo reference;
- list/card/detail;
- 1/20/50+ farm behavior;
- local-only operation.

**Gate 6:** farms survive restart and work fully offline.

## Phase 7 — Parcels manual core

- create/edit/archive/restore;
- farm membership;
- alias/name independent of Catastro;
- agronomic basics;
- multiple varieties prepared;
- geometry/cadastre link reserved but not required.

**Gate 7:** multiple parcels per farm, history-safe relations and offline persistence pass.

## Phase 8 — Campaigns + history

- create/activate/close;
- parcel participation without parcel duplication;
- historical snapshots;
- quick past-campaign access;
- protected historical edits.

**Gate 8:** closed campaign remains historically stable after current parcel edits.

## Phase 9 — Activity engine

- common activity header;
- multi-parcel targeting;
- planned/completed/cancelled;
- drafts/resume;
- typed detail extension points.

**Gate 9:** one activity can safely target multiple parcels without duplicate canonical events.

## Phase 10 — Typed agricultural activities + irrigation

- pruning;
- fertilization;
- treatment;
- soil/desbroce;
- observation/incident/other;
- irrigation with provider/community, sector, duration/volume.

**Gate 10:** typed validation + offline CRUD + no giant-form regression.

## Phase 11 — Attachments

- camera/document picker;
- local attachment lifecycle;
- thumbnails;
- farm cover;
- activity/doc attachments;
- PDF/image handling.

**Gate 11:** attachment survives app restart and failed future upload cannot destroy local reference.

## Phase 12 — Expenses, purchases and organizations

- authoritative Expense ledger;
- agricultural organizations with reusable roles;
- supplier/cooperative/company selection;
- purchase items;
- invoice/ticket attachment;
- activity/farm/parcel/campaign relations.

**Gate 12:** no monetary double counting; organization reuse works across contexts.

## Phase 13 — Harvest

- date/campaign/origin parcels;
- kg;
- exact per-parcel allocation when known;
- explicit mixed/unallocated mode;
- collection method/machinery relation.

**Gate 13:** totals remain truthful and no parcel split is fabricated.

## Phase 14 — Deliveries + OCR + later yield

- destination cooperative/mill;
- kg/ticket/albarán;
- image/PDF;
- OCR extraction;
- user correction/confirmation;
- mixed-origin delivery;
- later yield analysis;
- yield coverage;
- settlement extension point.

**Gate 14:** original delivery survives OCR/yield updates unchanged; OCR cannot auto-confirm; weighted metrics are correct.

## Phase 15 — Machinery

- machinery list/detail;
- activity relation;
- optional hours/usage;
- lightweight field workflow.

**Gate 15:** machinery adds value without making activities mandatory/complex.

## Phase 16 — Calendar, reminders and Android notifications

- planned activity projection;
- previous-day/same-day/custom reminder;
- local notification scheduling;
- notification deep-link;
- mark planned work completed/cancelled.

**Gate 16:** reminders fire offline on physical Android under supported OS restrictions.

## Phase 17 — Spain Catastro technical spike

Validate Spain official lookup/import paths, geometry parsing, GML, error modes and legal/operational constraints.

**Gate 17:** a real Jaén parcel can be located/imported without scraping or invented geometry.

## Phase 18 — Land registry/geometries + map

- Spain Catastro visual/reference/GML import routes approved by spike;
- provider-neutral registry/import boundary for other countries;
- app-owned normalized geometry;
- farm/parcel map;
- offline display after import;
- managed vs cadastral area distinction.

**Gate 18:** imported parcel geometry remains usable in airplane mode after restart.

## Phase 19 — Historical analytics

- kg by campaign;
- kg by date;
- delivery dates;
- yield series;
- weighted campaign yield + coverage;
- cross-campaign comparison.

**Gate 19:** charts derive from canonical truth and expose partial/unknown data correctly.

## Phase 20 — Home contextual services + weather visuals

- current campaign/olive-grove summary;
- upcoming work;
- weather/radar;
- subtle live weather visual state (rain/sun/cloud/wind/fog) with reduced-motion/performance safeguards;
- oil market AOVE/Virgen/Lampante when source supports them;
- preferred-cooperative notices/news;
- caching/freshness/source states.

**Gate 20:** failure of every external feed still leaves Mi Olivar fully operational.

## Phase 21 — Profile

- account;
- municipality;
- preferred cooperative;
- notification preferences;
- units;
- sync/export/help/privacy/about.

**Gate 21:** preferences persist offline and account-sensitive operations are protected.

## Phase 22 — Supabase backend contract

- auth;
- PostgreSQL/PostGIS schema aligned to local model;
- storage buckets;
- RLS;
- service/repository boundaries.

**Gate 22:** backend can represent RC1.1 without requiring UI/server-first shortcuts.

## Phase 23 — Synchronization proof and hardening

- outbox;
- WorkManager;
- retries;
- idempotency;
- conflicts;
- attachments;
- offline→online recovery.

**Gate 23:** multi-cycle offline edits converge without data loss or silent overwrite.

## Phase 24 — Admin web

Separate private web surface:

- users;
- organizations/cooperatives;
- municipalities;
- content/notices/news;
- external-source configuration;
- notification operations;
- audit/health basics.

**Gate 24:** no Admin privilege is exposed through normal Android UI and authorization is enforced server-side.

## Phase 25 — Reports/PDF

- campaign report;
- farm report;
- parcel report;
- maps/activities/harvest/delivery/yield/expenses where available;
- historical snapshots;
- preview/export/share.

**Gate 25:** PDF reconciles against canonical totals and historical campaign truth.

## Phase 26 — Full QA torture pass

- airplane mode;
- process death/restart;
- migrations;
- storage pressure;
- interrupted OCR/attachment/sync;
- stale external feeds;
- large representative dataset;
- accessibility;
- performance.

**Gate 26:** no data-loss defect remains open.

## Phase 27 — Real-device beta

- multiple Android devices/versions;
- actual field use;
- Catastro parcel verification;
- notification behavior;
- camera/OCR ticket variability;
- sunlight/outdoor usability;
- battery/network transitions.

**Gate 27:** field beta issues are triaged and release blockers closed.

## ## Branding Gate — before public beta/store assets

- shortlist and approve final product name;
- check obvious product/brand conflicts;
- confirm domain/social naming when practical;
- freeze display name/logo/icon;
- perform one controlled Android technical rename only if approved;
- re-run package/application-ID and migration tests.

**Gate B:** public brand is frozen before store listing assets and production release naming.

## Phase 28 — Google Play test tracks / release candidate

- signing;
- application ID/versioning;
- privacy/legal store assets;
- internal/closed testing;
- crash/ANR review;
- rollback/release checklist.

**Gate 28:** release candidate approved for production publication.

## Post-RC1.1

Only after the agricultural Android product is stable:

- promotional/public website;
- Mi Olivo loyalty activation;
- advertising/partner campaigns;
- professional/client mode;
- personnel/inventory;
- advanced machinery maintenance;
- advanced irrigation/IoT;
- AI/automation.

These do not retroactively change the RC1.1 core.
