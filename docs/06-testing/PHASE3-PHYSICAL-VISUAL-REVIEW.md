# Phase 3 — Physical Visual Review Guide

**Target APK SHA-256:** `93095387244cb4a91c7455d5786b5129b3f6e0b2db2c96aa26cd233d3dd004c9`

Use this guide on a real Android phone before Gate 3 closes.

## 1. General

Check:

- app opens directly into DEV design gallery;
- no crash while switching tabs;
- no obvious clipping;
- no text cut off;
- cards remain readable outdoors/bright screen;
- bottom navigation labels remain legible;
- scrolling feels natural;
- no screen looks visually denser than the competitor benchmark.

## 2. Onboarding

Approve if:

- first message is understood in <5 seconds;
- CTA is obvious;
- illustration area does not dominate;
- text is not too long.

## 3. Inicio

Approve if the first glance reveals:

1. current weather;
2. current farm/campaign context;
3. next task;
4. where to register something.

Reject if market/cooperative content visually dominates the user's own farm.

## 4. Mi Olivar

Approve if:

- farm cards are clearly separated;
- 2+ farms remain easy to scan;
- surface/parcel metrics are readable;
- user can understand that one farm contains multiple parcels.

## 5. Finca

Approve if:

- farm identity is obvious;
- parcel list is the main operational focus;
- campaign and recent activity are secondary but visible.

## 6. Parcela

Approve if:

- area/tree count/variety are understandable;
- irrigation context is visible;
- map placeholder does not imply Catastro is already implemented.

## 7. Campaña

Approve if:

- kg, yield, costs and result can be found immediately;
- partial yield coverage is obvious;
- no metric looks falsely complete.

## 8. Producción

Approve if:

- kg and yield are the primary metrics;
- chart is understandable;
- delivery list is easy to scan;
- pending yield is visually different from confirmed yield.

## 9. Costes

Approve if:

- total, €/kg and breakdown are easy to find;
- activity bars are readable;
- scan-invoice action is visible but not dominant.

## 10. Rentabilidad

Approve if:

- net result is the visual focus;
- income/cost relationship is obvious;
- ratios are readable without feeling like a spreadsheet.

## 11. Registrar

Approve if:

- selecting what to register is obvious;
- form hierarchy is simple;
- planned vs completed intent is understandable;
- screen does not look overloaded.

## 12. Calendario

Approve if:

- upcoming work is easy to scan by date;
- people/provider details fit naturally;
- reminders are distinguishable from work items.

## 13. Entrega OCR

Approve if:

- original document context is visible;
- extracted values look reviewable, not automatically trusted;
- confidence/review state is understandable;
- yield pending is clearly separate.

## 14. Factura OCR

Approve if:

- supplier/date/number/total are easy to check;
- line items are readable;
- the user understands this creates a draft, not an automatic final expense.

## 15. Perfil

Approve if:

- country/location/currency/units/cooperative are understandable;
- it does not look like an admin panel;
- advanced settings remain secondary.

## 16. Estados

Approve if:

- loading, empty, partial, offline and error are visually distinct;
- offline state feels safe rather than alarming;
- unknown/partial values never look like zero.

## 17. Bottom navigation

Check all:

- Inicio;
- Mi Olivar;
- Registrar;
- Calendario;
- Perfil.

Approve if:

- center Register action is obvious;
- selected state is clear;
- labels remain visible;
- no accidental taps due to cramped targets.

## 18. Fast response format

The product owner can return any of:

```text
Gate 3 visual OK
```

or:

```text
Inicio: OK
Mi Olivar: OK
Campaña: make metrics larger
Costes: too dense
OCR: OK
Bottom nav: OK
```

Screenshots with comments are preferred for visual corrections.

## 19. Gate rule

Physical visual approval alone does not close Gate 3 while Figma transfer remains explicitly required by the current checklist.

If the project owner decides Figma is optional rather than blocking, that must be a deliberate baseline change documented separately; do not silently drop the requirement.
