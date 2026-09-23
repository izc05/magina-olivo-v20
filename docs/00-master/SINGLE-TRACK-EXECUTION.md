# Mágina Olivo — Single Track Execution Plan

**Status:** CANONICAL EXECUTION PATH  
**Date:** 2026-09-18  
**Repository:** `izc05/magina-olivo-v20`  
**Rule:** GitHub `main` is the only source of truth for the Android product.

This document reconciles work done across multiple ChatGPT/Codex/Antigravity chats and branches into one ordered delivery path.

## 1. Canonical state now

Already merged into `main`:

- RC1.2 product baseline and gate-driven roadmap;
- Phase 1 Android Foundation;
- Phase 2 Base Application Architecture;
- CR-003 Mágina Olivo display brand + canonical visual direction;
- machine-readable Visual Design Lock;
- committed brand/onboarding/core reference boards;
- Phase 3 Compose design tokens/components;
- six-screen onboarding;
- Inicio/Home reference;
- Mi Olivar / Fincas reference;
- Farm Detail reference;
- Map / Catastro reference;
- Harvest reference;
- Expenses/Documents reference;
- Weather/Market reference;
- Campaign reference;
- Register Activity reference;
- Parcel Detail reference;
- Delivery/OCR Review reference;
- complete supporting component catalogue.

Implementation slices merged to main are represented by PRs #181–#192.

The old monolithic Phase 3 PR #180 is superseded and must not be merged.

## 2. Current active work

```text
CURRENT:              Phase 12 — Expenses / purchases / organizations /
                      generic OCR = PASS, merged into `main` through PR #208

ACTIVE PRODUCTION PHASE: Phase 13 — Harvest (NEXT)

BLOCKER:              none
```

Gates 3, 4 and 5 passed. Gate 6 composite passed on 2026-09-22 on commit `164aaa48`;
see `docs/06-testing/PHASE6-CAMPAIGNS-SLICE.md` and `docs/00-master/CURRENT-STATE.md`.

The validated Android stack was integrated into `main` through PRs #197–#201, #205 and
finally #206, whose merge commit is `f82be163`. Phase 9 is complete, validated and merged.
Phase 10 added the typed agronomic details on top of that Activity aggregate and was
merged as `4acc3ab9`. Section 3 below is retained as the historical record of how Gate 3
was closed.

## 3. Gate 3 closure order

Close these items in order:

1. render and review 360 dp compact phone;
2. render and review approximately 393–412 dp common phone;
3. render and review 480 dp large phone;
4. verify 1.0× and enlarged font scale;
5. TalkBack/semantics review;
6. reduced-motion review where relevant;
7. outdoor/bright-screen contrast review;
8. empty/loading/error/offline states;
9. capture representative Android screenshots;
10. compare screenshots with `docs/design/reference/`;
11. add the still-pending canonical screenshots for Parcel Detail and Delivery/OCR Review;
12. final audit for ad-hoc colors/spacing/components;
13. keep the Figma handoff manifest ready for later transfer when MCP access is available.

Figma transfer is **supporting design handoff, not a Gate 3 blocker**. The normative Gate 3 condition is representative Android visual/accessibility validation plus recorded evidence.

**Gate 3 PASS only after the required Android evidence is recorded in the repository.**

## 4. Execution discipline after Gate 3

For every production phase:

```text
main
  ↓
one isolated phase branch
  ↓
implementation
  ↓
tests + CI
  ↓
physical/device evidence when the Gate requires it
  ↓
Gate PASS recorded
  ↓
merge to main
  ↓
close branch/PR
  ↓
start next phase
```

Rules:

- one active production implementation phase at a time;
- at most two auxiliary parallel lines, and only for documentation/research/preparation;
- never implement a later production phase early;
- never work directly on `main`;
- never reuse old V20 code wholesale;
- if an immutable baseline must change, open a Change Request first.

## 5. Ordered path to completion

### Phase 4 — Navigation shell

Deliver:

- real five-root navigation:
  `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`;
- one NavHost;
- predictable back stack;
- Register center action/sheet;
- nested shell routes for Finca, Parcela, Campaña, analytics and OCR review;
- DEV design gallery moved behind a developer-only entry.

**Gate 4:** instrumentation navigation tests + physical Android navigation smoke.

### Phase 5 — Local database foundation

Deliver:

- Room;
- migrations;
- UUID identity;
- soft delete/version conventions;
- repository boundaries;
- attachment metadata;
- test/dev fixtures.

**Gate 5:** restart persistence + migration tests + airplane-mode CRUD proof.

### Gate 6 composite — reconciliation (execution 2026-09)

Phase 6 (Farms), Phase 7 (Parcels) and Phase 8 (Campaigns) were developed as stacked
slices and validated **jointly** under one **Gate 6 composite**, together with the
combined `Farm → Parcel → Campaign` end-to-end flow. Result: PASS.

This is an execution fact, not a renumbering. Phase numbering in this document is
unchanged: Phase 6 remains Farms, Phase 7 remains Parcels, Phase 8 remains Campaigns,
Phase 9 remains the Activity engine, and every later phase keeps its number. Where the
per-phase gates below read **Gate 6**, **Gate 7** and **Gate 8**, they were satisfied
together by the Gate 6 composite.

### Phase 6 — Farms

Deliver real offline Farm CRUD, archive/restore, optional cover and large-list behavior.

**Gate 6:** 1/20/50+ farms work offline and survive restart.

### Phase 7 — Parcels manual core

Deliver real Parcel CRUD under Farm, independent alias/name, agronomic basics and reserved geometry/provider links.

**Gate 7:** multiple parcels per Farm + history-safe relations + offline persistence.

### Phase 8 — Campaigns + history

Deliver campaign create/activate/close, parcel participation and historical snapshots.

**Gate 8:** closed history remains stable after later Parcel edits.

### Phase 9 — Activity engine

Deliver common activity header, multi-parcel targeting, planned/completed/cancelled and drafts.

**Gate 9:** one canonical activity can target multiple parcels without duplication.

### Phase 10 — Typed activities + irrigation

Deliver pruning, fertilization, treatment, soil/desbroce, observation/incident/other and irrigation provider/sector/duration/volume.

**Gate 10:** typed validation + offline CRUD.

### Phase 11 — Attachments

Deliver camera/file picker, image/PDF lifecycle, thumbnails and local references.

**Gate 11:** attachments survive restart and failed future upload.

### Phase 12 — Expenses / purchases / organizations / generic OCR

Deliver authoritative Expense ledger, reusable organizations, purchase items and generic OCR review-to-draft.

**Gate 12:** no monetary double counting and OCR cannot silently confirm/post money.

### Phase 13 — Harvest

Deliver Harvest distinct from Delivery, truthful mixed-origin handling and optional exact parcel allocation.

**Gate 13:** no fabricated per-parcel split.

### Phase 14 — Deliveries + ticket OCR + later yield

Deliver cooperative/mill destination, ticket/photo/PDF, human OCR review, later yield analysis and coverage.

**Gate 14:** original delivery remains immutable and weighted metrics stay truthful.

### Phase 15 — Machinery

Deliver machinery list/detail, activity relation and lightweight usage/hours.

**Gate 15:** machinery adds value without making activity entry heavy.

### Phase 16 — Calendar / agenda / reminders / Android notifications

Deliver planned-work projection, people/provider/duration, reminders and local notification deep-links.

**Gate 16:** reminders fire offline on physical Android.

### Phase 17 — Spain Catastro technical spike

Validate real Spanish lookup/import, GML/geometry parsing, legal/operational constraints and failures.

**Gate 17:** locate/import one real Spanish parcel without scraping or invented geometry.

### Phase 18 — Geometry + map

Deliver provider-neutral import boundary, normalized app-owned geometry, parcel/farm map and offline display after import.

**Gate 18:** imported geometry survives restart and airplane mode.

### Phase 19 — Historical analytics

Deliver kg/date/yield time series, weighted yield coverage and cross-campaign comparison.

**Gate 19:** charts derive from canonical truth and expose unknown/partial data.

### Phase 20 — Home contextual services

Deliver weather/radar, subtle weather visuals, market reference and preferred-cooperative notices with freshness/cache states.

**Gate 20:** every external feed may fail while Mi Olivar remains operational.

### Phase 21 — Profile

Deliver account/regional/unit/notification/cooperative/export/help/privacy preferences.

**Gate 21:** preferences persist offline and sensitive actions are protected.

### Phase 22 — Supabase backend contract

Deliver auth, PostgreSQL/PostGIS, storage, RLS and remote repository/service boundaries.

**Gate 22:** backend represents the local model without server-first shortcuts.

### Phase 23 — Synchronization

Deliver outbox, WorkManager, retries, idempotency, conflicts and attachment synchronization.

**Gate 23:** repeated offline/online cycles converge without data loss.

### Phase 24 — Admin web

Deliver the separate private admin surface for users, organizations, territory/content, sources, notifications and audit/health.

**Gate 24:** no admin privilege appears in ordinary Android UI.

### Phase 25 — Reports / PDF

Deliver farm/parcel/campaign reports from canonical data.

**Gate 25:** generated totals reconcile exactly with source records/history.

### Phase 26 — QA torture pass

Airplane mode, process death, migrations, storage pressure, interrupted OCR/sync, stale feeds, large datasets, accessibility, performance.

**Gate 26:** no open data-loss blocker.

### Phase 27 — Real-device beta

Multiple Android devices/versions, real field usage, notifications, camera/OCR, Catastro and outdoor use.

**Gate 27:** beta release blockers closed.

### Branding Gate

Before store publication:

- final adaptive icon/logo exports;
- brand-conflict review;
- package/application-ID decision;
- store assets;
- migration tests if technical rename occurs.

### Phase 28 — Google Play release candidate

Signing, versioning, privacy/legal assets, internal/closed tracks, crash/ANR review and rollback plan.

**Gate 28:** production release candidate approved.

## 6. Legacy/reusable asset ledger — reference only

Old repositories/branches are **not merge sources**. They are study/reference candidates to reduce research later.

### Phase 17–18 candidates — Catastro / GIS / map

Repository: `izc05/magina-olivo`

Potential reference branches:

- `feat/android-map-catastro-v1`
- `feat/catastro-inspire-v1`
- `feat/catastro-map-selector-v1`
- `feat/catastro-batch-import-v1`
- `feat/catastro-manual-gps-v1`
- `feat/map-first-create-farm-v1`
- `feat/plot-map-v1`
- `feat/sigpac-overlay-v1`
- `feat/pnoa-map-layers-v1`
- `feat/parcel-source-comparison-v1`
- `feat/plot-sigpac-associations-v1`
- `feat/parcel-map-agronomy-convergence-v1`

Allowed use:

- study algorithms/contracts;
- port small compatible tests/helpers selectively;
- compare provider behavior;
- reuse verified public-source knowledge.

Forbidden:

- wholesale merge;
- importing old architecture/navigation/state model;
- making Catastro the Parcel identity;
- bypassing the Phase 17 technical spike.

### Phase 20 candidates — weather/radar

Old weather/radar branches may be inspected only when Phase 20 begins. Provider/source logic must be revalidated against RC1.2 and Android/offline constraints.

### Post-RC candidates — loyalty/public portal/advertising

Mi Olivo, rewards, territorial web/community, tourism and advertising branches remain outside RC1.2 production scope.

They may not enter the Android core before the Post-RC gate.

## 7. Parallel-chat reconciliation rule

When another chat advances this project:

1. it must work from the latest `main`;
2. it must state the current Phase and Gate;
3. it must use an isolated branch/PR;
4. it must not create a second competing roadmap;
5. it must update `CURRENT-STATE.md` only when a Gate/phase transition actually occurs;
6. this Single Track plan + `ROADMAP-RC1.2.md` remain the execution authority.

If two chats create overlapping work, do not merge both. Compare against `main`, keep the cleaner/current implementation and close the superseded branch/PR.

## 8. Immediate next action

**Do not start Phase 4 yet.**

Immediate work is only:

```text
Gate 3 visual/accessibility evidence
→ Gate 3 PASS
→ merge/update continuity docs
→ Phase 4 branch from latest main
```
