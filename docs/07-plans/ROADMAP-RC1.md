# Mágina Olivo Android — RC1 Roadmap

> **SUPERSEDED BY RC1.1:** `docs/07-plans/ROADMAP-RC1.1.md` is the normative roadmap after approved CR-001 (2026-09-18). This file is retained as historical RC1 context only; agents must not use it to override RC1.1 phase order or scope.

**Baseline:** `RC1-BASELINE-2026-09-17`

This roadmap defines the mandatory sequence of work. A phase cannot be marked complete until its Gate is passed. New ideas do not reorder the active sequence.

## Current state

- ✅ 0.1 Master product definition
- ✅ 0.2 Scalable Mi Campo architecture
- ✅ 0.3 Data Model RC1 + Future — approved with normative addendum
- ✅ 0.4 Catastro Contract
- ✅ 0.5 Offline/Sync Contract — approved with normative addendum
- ✅ 0.6 Screen Map — approved with normative cost interpretation
- ✅ 0.7 Design System Spec
- ▶ Phase 1 — Android project foundation

The formal cross-document review is recorded in `docs/00-master/RC1-GATE-REVIEW.md`. Normative clarifications are in `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`.

---

# BLOCK A — Definition and contracts

## Phase 0.1 — Master Spec

Defines identity, RC1 scope, exclusions, Android/offline principles, farms, parcels, campaigns, activities, harvest, expenses, documents and reports.

**Gate:** no contradiction in product scope.

## Phase 0.2 — Scalable Mi Campo architecture

Defines optional future modules without putting them into RC1: tasks, calendar, machinery, products, personnel, suppliers, inventory, irrigation, analyses, management zones, collaboration and AI.

**Gate:** future modules can reference the core without making the core depend on them.

## Phase 0.3 — Data Model RC1 + Future

Freeze entities, fields, relationships, UUIDs, geometry, indexes, historical snapshots, audit metadata, soft delete, local/remote representation and reserved extension points.

**Gate:** no ambiguous ownership/cardinality/history rule remains.

**Status:** PASSED with `RC1-NORMATIVE-ADDENDUM.md`.

## Phase 0.4 — Catastro Contract

Freeze the sequence:

```text
Locate → view cadastral overlay → select parcel → obtain identity → obtain geometry → validate → transform → save own parcel → assign to farm → use offline
```

Document WMS/WFS/GML/CRS/error/caching rules.

**Gate:** a real parcel can be transformed from cadastral source to Mágina Olivo parcel deterministically at contract level; Phase 9 remains the mandatory Android real-parcel proof before productionizing the integration.

**Status:** PASSED.

## Phase 0.5 — Offline/Sync Contract

Freeze local-first transactions, outbox, WorkManager scheduling, idempotency, retries, token expiry, conflicts, attachments, soft delete and multi-device behavior.

**Gate:** no critical field operation requires active Internet.

**Status:** PASSED with aggregate clarifications in the normative addendum.

## Phase 0.6 — Complete Screen Map

Specify every RC1 screen with purpose, entry, exit, actions, empty/loading/error/offline states and navigation.

**Gate:** no primary flow contains dead ends or undefined transitions.

**Status:** PASSED. Activity form cost fields are convenience inputs for linked Expense rows, per normative addendum.

## Phase 0.7 — Design System Spec

Freeze visual tokens and reusable interaction components.

**Gate:** implementation can build UI without inventing one-off visual patterns.

**Status:** PASSED.

---

# BLOCK B — Android foundation

## Phase 1 — Android project foundation

Branch: `feat/android-foundation`

Kotlin, Jetpack Compose, Gradle, DEV/STAGING/PROD environments, CI and test setup.

**Gate 1:** installable APK starts successfully on a physical Android device.

## Phase 2 — Base architecture

UI → ViewModel → Use Case → Repository → local/remote data sources.

**Gate 2:** architectural boundaries are testable and UI has no direct database/backend coupling.

## Phase 3 — Design system implementation

Buttons, inputs, cards, dialogs, chips, app bars, navigation, status components, typography and spacing.

**Gate 3:** internal component gallery verified on real Android hardware.

## Phase 4 — Navigation shell

Permanent bottom navigation:

`Inicio · Fincas · Campaña · Registrar · Más`

**Gate 4:** full navigation shell works without dead routes/back-stack defects.

---

# BLOCK C — Data foundation

## Phase 5 — Room local database

Implement the approved RC1 schema, DAOs, repositories and migrations.

**Gate 5:** force-closing/restarting the app preserves all local records.

## Phase 6 — Supabase backend

Auth, PostgreSQL/PostGIS, Storage and RLS. Remote schema mirrors the approved contract, not the UI.

**Gate 6:** User A cannot read or modify User B's workspace data.

## Phase 7 — Minimum synchronization proof

Local create → durable outbox → remote sync → recovery on another installation/device.

**Gate 7:** a small vertical slice survives offline creation and remote recovery without duplication.

---

# BLOCK D — Farms and real parcels

## Phase 8 — Farms

Create/list/edit/archive/open farms, local-first.

**Gate 8:** create farm offline, close app, reopen and retain it.

## Phase 9 — Catastro technical spike

Isolated feasibility proof on Android with real Sierra Mágina parcels:

`map → tap parcel → cadastral identity → geometry → highlighted polygon`.

**Gate 9:** selected real parcel boundary is reproduced correctly. Do not build full cadastral UX before this passes.

## Phase 10 — Parcels

Productionize parcel import/search/select/confirm/name/save/assign/edit/archive.

**Gate 10:** select with Internet, enable airplane mode, reopen app and still display the stored parcel boundary.

## Phase 11 — Farm map

Display all owned parcel polygons, fit bounds, tap selection, quick card and parcel detail.

**Gate 11:** farm with at least five parcels remains understandable and each parcel is independently accessible.

---

# BLOCK E — Campaign and field notebook

## Phase 12 — Campaigns

Create/activate/select parcels/snapshot/harvest state/close/history.

**Gate 12:** later parcel edits do not modify a closed campaign's snapshot.

## Phase 13 — Activity engine

Generic activity + activity-parcel relation, attachments/cost/notes; start with `OBSERVATION` and `OTHER`.

Interpret `cost` as convenience creation/editing of linked Expense data. `expenses` remains the sole financial ledger.

**Gate 13:** one offline activity can target several parcels and appears correctly in every parcel timeline.

## Phases 14–20 — Typed agricultural activities

Implement sequentially, each with full create/edit/soft-delete/multi-parcel/linked-expense/photo/timeline/offline/sync coverage:

14. Poda
15. Abonado
16. Tratamiento
17. Suelo/desbroce/laboreo
18. Riego básico
19. Mantenimiento de explotación
20. Incidencias

**Gate per type:** the complete behavior above passes before beginning the next type.

---

# BLOCK F — Economy and production

## Phase 21 — Expenses

Campaign/farm/parcel/activity expenses and derived totals. `expenses` is the only authoritative financial ledger.

**Gate 21:** totals reconcile across entity scopes without double counting.

## Phase 22 — Harvest

Multiple partial harvests, single/multi-parcel, weights and optional distribution.

**Gate 22:** repeated harvest events produce correct parcel/farm/campaign totals and kg/ha.

## Phase 23 — Deliveries

Destination, kg, ticket/albarán and yield. Delivery stays distinct from harvest.

**Gate 23:** harvested and delivered quantities can differ without corrupting either record set.

---

# BLOCK G — Digital dossier

## Phase 24 — Photos

Camera/gallery, local persistence and deferred upload.

**Gate 24:** take photo offline, force-close/restart and retain linkage before synchronization.

## Phase 25 — Documents

PDF/ticket/invoice/plan/GML/analysis/other via Android document picker.

**Gate 25:** document metadata and ownership remain correct through offline/retry flows.

## Phase 26 — Parcel timeline

Unified chronological projection of activities, incidents, harvests, photos and relevant documents.

**Gate 26:** timeline remains chronologically and historically correct after edits/soft deletes/sync.

---

# BLOCK H — Results

## Phase 27 — Home/dashboard

Only now use real data for campaign, farms, surface, activities, expenses, harvest and recent activity.

**Gate 27:** every displayed figure is derived from real persisted records.

## Phase 28 — Reports

Campaign report first, then farm and parcel reports; export/share PDF.

**Gate 28:** generated report reconciles with persisted totals and historical snapshots.

## Phase 29 — History/comparisons

Cross-campaign comparison of production/cost/work where data exists.

**Gate 29:** comparisons never substitute current parcel metadata for historic snapshots.

---

# BLOCK I — Robustness

## Phase 30 — Full offline torture pass

Airplane mode → work → close app → reboot device → continue work.

**Gate 30:** no accepted operation is lost.

## Phase 31 — Full synchronization hardening

Intermittent network, expired session, retries, duplicates, large attachments, two devices and conflicts.

**Gate 31:** remote convergence occurs without silent data loss or duplicate domain records.

## Phase 32 — Security

RLS, sessions, Storage policies, keys, logs and private data boundaries.

**Gate 32:** documented security tests pass.

## Phase 33 — Performance

Representative dataset: ~20 farms, ~100 parcels, multiple campaigns, thousands of activities and hundreds of attachments.

**Gate 33:** primary screens remain operationally fluid and database queries are indexed/observable.

---

# BLOCK J — Real validation and release

## Phase 34 — Physical APK validation

At least two Android devices/resolutions where available.

## Phase 35 — Simulated full campaign

3+ farms, 10+ parcels, 20+ activities, expenses, repeated harvests, deliveries, attachments and final report.

## Phase 36 — RC1 Candidate

Create `release/rc1`. Freeze features. Only bugs, security, data-loss risks and critical UX corrections enter.

## Phase 37 — RC1

Release only after the complete acceptance checklist passes.

---

# Post-RC1 expansion order

Do not reorder RC1 to implement these early.

- **RC1.5:** Tasks, agricultural calendar, work templates.
- **RC2:** Products, equipment, people, suppliers.
- **RC2.5:** Purchases, warehouse, inventory.
- **RC3:** Management zones, advanced irrigation, analyses, farm infrastructure/features.
- **RC4:** Shared holdings, roles, crews, advisor access.
- **RC5:** Automation, sensors, advanced analytics and AI.

---

# Continuity rule

Every new working session must:

1. identify the last passed Gate;
2. open the immediately following phase;
3. work only that phase's accepted scope;
4. validate its Gate;
5. document/commit/PR;
6. mark the Gate passed;
7. only then proceed.

If a new idea appears, capture it in backlog unless it is required to make the current phase correct.
