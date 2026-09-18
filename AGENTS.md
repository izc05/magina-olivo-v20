# AGENTS.md — Olive Farm App RC1.2

This repository is under a frozen, gate-driven product baseline.

## Mandatory reading order

Before changing code or architecture, read:

1. `docs/00-master/RC1-BASELINE.md`
2. `docs/00-master/RC1.2-PRODUCT-LOCK.md`
3. `docs/00-master/CURRENT-STATE.md`
4. `docs/07-plans/ROADMAP-RC1.2.md`
5. the current phase execution plan, when one exists
6. only then the domain/architecture/UI contracts needed for that phase

If an older RC1/RC1.1 document conflicts with the RC1.2 Product Lock, RC1.2 wins.

## Product identity

The product is a **native Android, offline-first application for farmers managing their own olive farms**. Initial implementation targets Spain; architecture is geographic-neutral for olive-growing countries.

Core hierarchy:

`Finca → Parcela → Campaña → Actuaciones / Cosecha / Entregas / Gastos / Documentos → Histórico → Informes`

Do not turn the new Android app back into the old territorial V20 portal. Do not treat “Mágina Olivo” as the final customer-facing brand.

## Frozen primary navigation

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

Do not redesign or add root tabs without an approved Change Request.

## Current phase rule

Read `docs/00-master/CURRENT-STATE.md` and implement **only the currently allowed phase**.

Never skip a Gate because a later feature is easy or visually attractive.

Documentation/research may prepare later phases, but production implementation remains gate-bound.

## Non-negotiable architecture

- Kotlin + Jetpack Compose.
- Android first.
- Offline-first.
- Room becomes the local operational source of truth when its phase begins.
- Write locally first.
- Durable deferred synchronization via outbox/WorkManager when that phase begins.
- Supabase is remote backend, behind repository/service boundaries.
- Land-registry providers are external import/reference sources, not the app database.
- Spain Catastro is one provider, not universal Parcel identity.
- App owns normalized parcel identity and stored geometry.
- Client-generated UUIDs for synchronizable domain entities.
- Soft delete/versioning for critical historical records.
- Historical campaign snapshots must not be rewritten by later parcel changes.
- Feature growth is modular.

## Truth/data rules

Never:

- invent parcel or campaign data;
- fabricate per-parcel kg for a mixed delivery;
- auto-accept OCR values without human confirmation;
- restrict OCR architecture to delivery tickets only;
- rewrite the original delivery when later yield arrives;
- duplicate money between an Activity and the authoritative Expense ledger;
- present stale/external information as fresh;
- make Home external feeds a dependency for field work.

## RC1.2 product requirements to preserve

- named Farms containing one or many agricultural Parcels;
- optional Farm cover photo;
- Parcel alias/name independent from Catastro identity;
- campaigns persist history without recreating Parcels each year;
- quick historical campaign access and truthful kg/date/yield charts;
- typed activity forms, not one giant form;
- irrigation community/company + sector;
- local reminders for planned work/irrigation;
- machinery as operational resource;
- reusable organizations for cooperative/mill/supplier/irrigation provider;
- purchases linked to the Expense ledger;
- Harvest distinct from Delivery;
- delivery ticket/photo/PDF + OCR review;
- later yield analysis as separate linked data;
- Home contextual weather/radar, oil-market reference and preferred cooperative notices/news;
- OCR for delivery tickets, invoices/receipts and agricultural documents;
- irrigation historical pricing snapshots;
- scheduled work with expected people/provider and reminders;
- subtle weather-driven Home effects with reduced-motion/performance safeguards;
- profile municipality + preferred cooperative;
- separate private Admin web surface;
- loyalty/Mi Olivo activation deferred until the agricultural core is proven;
- professional/client mode deferred until post-RC1.2.

## Old V20 isolation

Branches, PRs and code from the old web/territorial V20 exist in this repository history.

Do not copy, merge or resurrect old V20 architecture, pages, navigation, database contracts or public portal modules into the new Android line unless a task explicitly names a specific reusable asset and confirms compatibility with RC1.2.

A broad prompt such as “improve the app” is **not** permission to reuse old V20 code.

## Branch discipline

- `main` stays stable.
- Use isolated `feat/*`, `fix/*`, `docs/*` branches.
- Keep one phase/task scope per branch where practical.
- Do not mix later-phase product features into a foundation PR.
- Review `git diff` for scope creep before opening/merging a PR.

## Definition of Done

A feature is not done because a screen renders.

Applicable DoD includes:

- approved UX behavior;
- domain/data contract;
- local persistence;
- validation;
- empty/loading/error/offline states;
- tests;
- migration impact;
- sync behavior when applicable;
- documentation;
- real-device verification when relevant.

## Change control

If a task conflicts with the frozen baseline or assumes a final public brand before the Naming Gate:

1. stop the conflicting implementation;
2. create a Change Request following `docs/00-master/CHANGE-CONTROL.md`;
3. do not silently “improve” the architecture;
4. resume only after approval.

## Agent handoff format

When finishing work, report:

- phase;
- branch/PR;
- files changed;
- tests/build commands and results;
- Gate evidence status;
- known blockers;
- explicit statement that no later-phase scope was added.
