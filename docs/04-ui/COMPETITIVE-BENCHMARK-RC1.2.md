# RC1.2 Competitive Benchmark — Olive Farm Management UI

**Status:** REFERENCE / NON-NORMATIVE VISUAL BENCHMARK  
**Date:** 2026-09-18  
**Purpose:** capture visible strengths from a competitor reference and turn them into explicit “match or exceed” criteria without copying its branding, layout or visual identity.

## 1. What is visibly useful in the competitor reference

The provided screens visibly organize one farm/campaign into three high-value analytical areas:

- Producción
- Costes
- Rentabilidad

Visible metrics include:

### Production
- total olive kg;
- average yield;
- total oil liters;
- kg per olive tree;
- liters per olive tree;
- oil-type distribution;
- historical weighings/deliveries with date, kg and yield.

### Costs
- total annual costs;
- cost per kg;
- cost per olive tree;
- breakdown by activity;
- visible categories such as harvesting, soil maintenance, pruning, pruning-residue removal, desvareto and other costs.

### Profitability
- income with VAT;
- annual costs;
- net profit;
- profit margin;
- income per kg;
- cost per kg;
- income per olive tree;
- cost per olive tree.

## 2. What our product must preserve conceptually

RC1.2 must make the following answers available quickly for each campaign:

- How many kg have I harvested/delivered?
- What yield did I obtain?
- How much oil does that represent where calculable?
- How much have I spent?
- Where did I spend it?
- What did I earn?
- What is my net result?
- What does this mean per kg, hectare, olive tree and parcel where data quality allows it?
- How does the current campaign compare with previous campaigns?

## 3. Where RC1.2 must go beyond the reference

The product must not stop at result dashboards. It must connect the whole operational chain:

```text
Plan → Work → Purchase/Input → Cost → Harvest → Delivery → OCR → Yield → Income/Settlement → Profitability → History
```

Differentiators to preserve:

- offline-first field operation;
- farm → parcel → campaign structure;
- multi-parcel activities;
- irrigation provider/community + sector + tariff snapshot;
- machinery relations;
- agenda/reminders/people/crew;
- reusable cooperatives/mills/suppliers;
- generic OCR for delivery tickets, invoices and receipts;
- later yield analysis without rewriting delivery truth;
- original documents retained;
- Catastro/registry-provider abstraction + app-owned geometry;
- weather/radar;
- reference olive-oil market;
- preferred cooperative information;
- historical campaign comparison;
- separate private Admin web surface.

## 4. Campaign analytical IA

Within a Farm/Campaign analytical area, RC1.2 should support these logical destinations:

1. Resumen
2. Producción
3. Costes
4. Rentabilidad
5. Histórico
6. Documentos

These are **logical sections**, not necessarily permanent top-level app tabs. Primary app navigation remains:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

## 5. Resumen — match-or-exceed criteria

A campaign summary should be able to show, when data exists:

- total olive kg;
- analyzed-yield coverage;
- weighted average yield;
- estimated/recorded oil quantity;
- total actual expenses;
- total recorded income;
- net result;
- margin;
- number of parcels;
- managed area;
- number of olive trees;
- upcoming planned work;
- recent activity.

Rules:

- unknown values are never shown as fake zero;
- partial yield coverage is disclosed;
- estimates are labelled as estimates;
- money comes from the authoritative Expense/Income records.

## 6. Producción — match-or-exceed criteria

Required projections:

- kg by campaign;
- kg by date;
- kg by parcel when allocation is truthful;
- yield by delivery/date;
- weighted campaign yield;
- yield coverage;
- historical deliveries/weighings;
- oil-type/quality distribution only when source data supports it.

Delivery rows should be richer than the reference and may show:

- date;
- cooperative/mill;
- confirmed kg;
- ticket number;
- OCR review status;
- yield status;
- linked document;
- mixed-origin status.

## 7. Costes — match-or-exceed criteria

Required views:

- total actual costs;
- cost/kg;
- cost/ha;
- cost/olive tree where tree count is known;
- cost by activity type;
- cost by parcel where allocation is known;
- cost by month/date;
- purchase/supplier drill-down;
- linked invoice/ticket;
- OCR review state.

Cost categories are derived from actual canonical expense/activity data. Avoid hard-coding only the categories visible in the competitor reference.

## 8. Rentabilidad — match-or-exceed criteria

Possible metrics when inputs are reliable:

- income before/after applicable taxes when explicitly recorded/configured;
- actual costs;
- net result;
- margin;
- income/kg;
- cost/kg;
- result/kg;
- income/ha;
- cost/ha;
- result/ha;
- income/olive tree;
- cost/olive tree;
- result/olive tree.

Do not infer tax treatment from locale alone. Tax fields/labels must be driven by recorded data/configuration and can vary by country/user context.

## 9. Historical comparison

Campaign history should let the user compare at least:

- kg;
- yield;
- costs;
- income;
- net result;
- cost/kg;
- kg/olive tree where comparable;
- dates/harvest window.

Comparison UI must highlight missing/partial data rather than pretending campaigns have identical completeness.

## 10. Visual benchmark

The competitor reference is functionally clear but RC1.2 should feel more current and more operational.

Our approved direction:

- cleaner hierarchy;
- stronger typography;
- fewer decorative cards;
- more contextual drill-down;
- real farm imagery only where useful;
- charts with real analytical purpose;
- source/freshness/coverage states;
- modern olive palette;
- strong outdoor readability;
- OCR/document states;
- upcoming-work context;
- weather-responsive Home separated from analytical screens.

## 11. Do-not-copy rule

Do not copy:

- competitor logo;
- exact visual layout;
- exact icon set;
- exact color palette;
- exact card geometry;
- exact wording;
- exact chart styling.

Use the reference only to validate user needs and expected analytical depth.

## 12. Phase 3 acceptance benchmark

The Phase 3 reference screens for Campaign/Production/Costs/Profitability must make it possible to answer the core analytical questions at least as quickly as the competitor reference while also exposing RC1.2 differentiators without clutter.

A design fails this benchmark if:

- production/cost/profitability require deep menu hunting;
- OCR/document status is invisible;
- partial yield data looks complete;
- cost totals can be double-counted;
- unknown metrics render as misleading zero;
- visual density makes outdoor use difficult;
- operational next actions disappear behind analytics.
