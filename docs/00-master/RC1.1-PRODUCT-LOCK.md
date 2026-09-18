# Mágina Olivo — RC1.1 Product Lock

> **SUPERSEDED BY RC1.2:** use `docs/00-master/RC1.2-PRODUCT-LOCK.md` and `docs/07-plans/ROADMAP-RC1.2.md` for current work. This file is historical context only.

**Status:** APPROVED / NORMATIVE  
**Baseline:** `RC1.1-BASELINE-2026-09-18`  
**Scope:** Android app for private olive-farm management, initially focused on Jaén.

This file is mandatory reading for ChatGPT, Codex, Antigravity and any other implementation agent. Where an older RC1 document contradicts this file, **this RC1.1 Product Lock wins** until that older document is editorially reconciled.

## 1. Product promise

Mágina Olivo is a simple, attractive Android application that lets an olive grower control their own farms and parcels campaign by campaign, even without coverage.

Core:
`Finca → Parcela → Campaña → Actuaciones / Cosecha / Entregas / Gastos / Documentos → Histórico → Informes`.

The product is agricultural first. Weather, market and cooperative information are contextual helpers, not the core.

## 2. Initial user

RC1.1 is for the farmer managing their **own** holdings.

The data model may reserve a workspace/owner relationship, but UI for professional contractors managing farms for named customers is post-RC1.1.

## 3. Farm and parcel

- A Farm has a user-defined name such as “Finca Foralico”.
- One Farm may contain 1, 10, 50+ Parcels.
- Parcel display labels may be “1”, “2”, “A”, “B”, “La Hoya”, etc.
- Agricultural parcel identity is owned by Mágina Olivo.
- Catastro is an import/reference source, never the authoritative app database.
- A parcel may store cadastral polygon/parcel/reference and copied geometry.
- The model must not assume 1 agricultural parcel = exactly 1 cadastral parcel forever.
- Farm can have a user-selected cover photo.
- Deletion of critical historical entities is soft-delete/versioned.

## 4. Campaigns and history

- Parcels persist across campaigns; they are never recreated each year.
- Campaign history must be accessible quickly from Farm and Parcel.
- Historical summaries/charts may show:
  - kg harvested/delivered by campaign;
  - delivery/harvest dates;
  - yield values;
  - campaign-to-campaign comparisons.
- Weighted yield summaries must use kg weighting when appropriate.
- If only part of delivered kg has yield data, UI must show the coverage and pending analyses.
- Closed campaign historical data must not silently change because present-day parcel metadata changes.

## 5. Activities

Activity engine uses a small common header plus typed detail forms. Do not create one giant form.

Initial families include pruning, fertilization, treatment, soil/desbroce, irrigation, observation/incident/other. One activity may target several parcels.

Activities support planned/completed/cancelled status where useful.

## 6. Irrigation

Irrigation may record:

- parcel(s);
- date/time/duration;
- volume when known;
- irrigation community or company;
- sector;
- system;
- notes/cost relation;
- planned reminder.

Irrigation provider/community and sector must be reusable records where practical, not repeatedly typed free text.

## 7. Reminders and calendar

Planned work and irrigation may create local Android reminders:

- previous day;
- same day;
- user-selected time.

Calendar is an RC1.1 operational surface. Notifications must work locally without server connectivity.

## 8. Machinery

Machinery is an RC1.1 resource.

A machine can be related to activities. Initial data entry stays lightweight; advanced maintenance, insurance, ITV, fuel/horometer analytics can grow later without changing the core.

## 9. Harvest, delivery, OCR and yield

Harvest and Delivery are separate domain events.

Delivery flow:

`recolección → entrega → cooperativa/almázara → vale/ticket → OCR review → kg confirmed → yield later → optional settlement later`.

Requirements:

- destination organization;
- date;
- delivered kg;
- ticket/albarán number when present;
- original image/PDF retained;
- OCR never auto-commits extracted values as unquestioned truth;
- OCR result is shown for human correction/confirmation;
- later fat/industrial yield is linked as a separate analysis;
- adding yield must not rewrite the original delivery;
- mixed-origin load may reference multiple parcels;
- if exact per-parcel split is unknown, store it as unallocated/mixed instead of inventing kg.

## 10. Expenses, purchases and providers

Agricultural expenses may relate to campaign/farm/parcel/activity/harvest/delivery.

Purchases may record:

- supplier / cooperative / company;
- product(s);
- quantity/unit;
- amount;
- ticket/invoice/document;
- date;
- optional relation to later activity.

The expense ledger remains the authoritative source for monetary totals; activity UI may link/create an Expense but must not duplicate amounts.

## 11. Agricultural organizations

Use a reusable organization/contact concept for roles such as:

- cooperative;
- mill/almázara;
- supplier/company;
- irrigation community/company;
- workshop/service provider.

One organization can have multiple roles.

The user's preferred cooperative can provide Home notices/news while the same organization can also be selected as a delivery destination or supplier.

## 12. Home

Home is contextual and lightweight. It should answer:

- what do I need to do today/soon?
- how is my current campaign going?
- what weather is coming?
- what is happening at my reference cooperative?
- how is the reference olive-oil market moving?

Target blocks:

1. place/weather + radar access;
2. My Olive Grove / current campaign summary;
3. upcoming planned activity/reminder;
4. olive-oil market;
5. preferred cooperative notices/news.

Home external data is optional/cached. A feed failure must never block Farm/Parcel/Campaign work.

## 13. Oil market

When a reliable configured source exposes categories, Home/Market can show synchronized time-series for:

- AOVE;
- Virgen;
- Lampante.

Requirements:

- same time axis when compared;
- clear unit;
- source;
- last update;
- reference-market wording;
- never imply it is the exact price the user's cooperative will pay.

## 14. Preferred location and cooperative

Profile stores user-selected municipality and preferred cooperative. GPS is not required merely to personalize Home.

## 15. Onboarding

First-run onboarding: 4–6 concise visual screens.

Recommended narrative:

1. manage farms/parcels;
2. record agricultural work;
3. control harvest/delivery + OCR;
4. plan and receive reminders;
5. weather/market/cooperative/history;
6. optional final “Start” if needed.

Onboarding must be skippable/reviewable and must not block offline use after initialization.

## 16. Navigation

Frozen RC1.1 primary navigation:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

Rules:

- center Registrar is the fast operational action;
- deep screens do not create new bottom tabs;
- context preselects farm/parcel/campaign;
- Android back stack remains predictable.

## 17. Profile

Profile must cover:

- account;
- municipality;
- preferred cooperative;
- notification preferences;
- units/preferences;
- synchronization/backups/export where applicable;
- privacy/help/about.

## 18. Admin

Admin is a **separate private web application/surface**, not a hidden Android admin tab.

It may manage:

- users;
- municipalities;
- cooperatives/organizations;
- content/news/notices;
- external source configuration;
- notifications;
- operational moderation;
- later loyalty/advertising controls.

## 19. Loyalty / Mi Olivo

Keep architecture modular and feature-flagged. Do not let gamification delay or distort the agricultural core. Activate only after field-management usability and reliability are proven.

## 20. Visual direction

The app must be visually attractive and recognizably Mágina Olivo:

- modern olive-agriculture identity;
- light readable surfaces;
- olive-green accent;
- strong outdoor legibility;
- real/farm imagery where useful;
- farm cover photos;
- restrained cards;
- clear charts;
- strong empty/offline/error states;
- no rustic wood cliché;
- no decorative dashboard clutter.

Before broad screen coding, implementation agents must follow the frozen design tokens/components and approved reference screens. They may not independently redesign navigation or information architecture.

## 21. Delivery sequence

The gate-driven route remains mandatory:

1. Android foundation.
2. Base architecture.
3. Design tokens/components and shell.
4. Local data layer.
5. Farms.
6. Parcels.
7. Campaigns/history.
8. Activities + irrigation.
9. Expenses/purchases/providers.
10. Harvest/deliveries/OCR/yield.
11. Machinery.
12. Calendar/reminders/notifications.
13. Catastro + map/geometries.
14. Home contextual services.
15. Profile/account/sync.
16. Admin web.
17. Reports/PDF/advanced historical charts.
18. QA offline/online, migrations, performance and real-device beta.
19. Google Play test tracks and release preparation.
20. Promotional website after the Android product is stable.

Exact phase numbers remain governed by the Roadmap; this list is the product ordering constraint.

## 22. Non-negotiable agent rule

Do not:

- merge old V20 portal architecture into the new Android app;
- add public tourism/community features to RC1.1;
- skip gates;
- replace offline-first with server-first;
- invent parcel/campaign data;
- fabricate mixed-delivery allocation;
- auto-accept OCR values without confirmation;
- duplicate expense totals;
- redesign the frozen primary navigation;
- activate loyalty before the technical core is stable.

When a requested implementation conflicts with this Product Lock, stop that feature and raise a Change Request.
