# RC1.2 Screen Specification — Campaign Analytics

**Status:** IMPLEMENTATION REFERENCE  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Mandatory references:** Brand Direction + Competitive Benchmark + Visual Tokens.

## 1. Purpose

Define the exact information architecture and UI behavior for the campaign analytical experience that must equal or exceed the supplied competitor reference.

This specification covers:

- Campaña / Resumen
- Producción
- Costes
- Rentabilidad
- Histórico
- Documentos

These are nested analytical sections inside Farm/Campaign context, not primary bottom-navigation roots.

## 2. Shared campaign header

Every analytical screen begins with context:

```text
←
Finca Foralico
Campaña 2026/27 ▾
```

Optional support line:

```text
12 parcelas · 24,38 ha
```

Header actions:

- back;
- campaign selector;
- overflow menu.

Overflow may expose:

- edit campaign;
- close/reopen campaign where allowed;
- report/PDF later;
- campaign settings/history actions.

## 3. Section navigation

Preferred compact horizontal segment/tab row:

```text
Resumen | Producción | Costes | Rentabilidad
```

Historical/document access can appear as secondary actions from Resumen or overflow rather than forcing 6 permanent tabs on narrow phones.

If all 6 sections are shown, use horizontally scrollable tabs with strong selected state.

## 4. Screen A — Campaña / Resumen

### Top metrics

Prioritize four:

- kg entregados/cosechados;
- rendimiento medio;
- costes;
- beneficio/resultado.

Example fixture:

```text
5.300 kg
22,43 %
1.102,94 €
3.501,15 €
```

Fixture data is design-only.

### Data integrity badges

Examples:

```text
Rendimiento: 78 % de kg analizados
2 entregas pendientes
```

Never show a weighted yield without its coverage if incomplete.

### Farm/campaign facts

Compact row/cards:

- parcels;
- surface;
- olive-tree count.

### Upcoming work

Show next 1–3 planned items:

- irrigation;
- harvest;
- pruning crew;
- treatment.

### Recent activity

Timeline rows:

- delivery;
- treatment;
- irrigation;
- purchase;
- pruning.

Each row supports drill-down.

## 5. Screen B — Producción

### Hero metrics

- total olive kg;
- weighted average yield;
- oil quantity when calculable/reliably recorded.

Supporting metrics:

- kg/olive tree;
- kg/ha;
- liters/olive tree where meaningful;
- number of deliveries.

### Evolution chart

Primary chart:

```text
x-axis: harvest/delivery date or month
bar: kg
line: yield %
```

Toggle options:

- Kilos
- Rendimiento
- Ambos

Rules:

- only plot yield for deliveries with analysis;
- missing analysis creates a gap/unknown, not 0%.

### Distribution

Possible distribution:

- AOVE;
- Virgen;
- Lampante;
- other/unknown.

Only show if source records actually classify production/delivery/oil.

### Delivery history

Row fields:

```text
27/12/2026
3.800 kg
Cooperativa X
Vale #45872
Rendimiento 23,0 %  OR  Pendiente
OCR Confirmado / Por revisar
```

Actions:

- view delivery;
- open ticket;
- add yield;
- correct reviewed data with audit/history.

Primary CTA:

`+ Añadir entrega`

## 6. Screen C — Costes

### Headline metrics

- total actual cost;
- cost/kg;
- cost/ha;
- cost/olive tree if known.

### Cost breakdown

Horizontal bars ordered descending.

Dynamic categories derived from actual activity/expense types:

- recolección;
- suelo/desbroce;
- poda;
- tratamientos;
- abonado;
- riego;
- maquinaria;
- proveedores/servicios;
- other.

Each row:

- category;
- amount;
- percent;
- optional count.

Tap → filtered expense list.

### Time evolution

Optional chart:

- monthly costs;
- cumulative cost.

### Purchases/documents

Recent rows:

- supplier;
- date;
- amount;
- invoice/ticket status;
- OCR status.

Primary actions:

- add expense;
- scan invoice/ticket.

## 7. Screen D — Rentabilidad

### Headline result

Large card:

```text
Beneficio / Resultado neto
3.501,15 €
```

Supporting:

- recorded income;
- actual costs;
- margin %.

### Ratios

When data quality supports it:

- income/kg;
- cost/kg;
- result/kg;
- income/ha;
- cost/ha;
- result/ha;
- income/olive tree;
- cost/olive tree;
- result/olive tree.

Do not show ratios based on missing denominator data.

### Comparison

Simple campaign comparison:

```text
2026/27 vs 2025/26
kg          +8 %
cost/kg     -4 %
yield       +1,2 pp
result      +12 %
```

Use percentages only when mathematically meaningful and prior campaign data exists.

### Taxes

Never hard-code IVA behavior into the generic profitability card.

If the user has explicit income/tax data:

- show recorded gross/net values with labels;
- country/profile rules can guide forms later;
- no inferred tax accounting.

## 8. Screen E — Histórico

### Timeline mode

Chronological:

- work;
- purchases;
- harvest;
- deliveries;
- yield;
- costs;
- documents.

Filters:

- all;
- work;
- harvest;
- deliveries;
- expenses;
- documents.

### Campaign compare mode

Select 2–4 campaigns.

Compare:

- kg;
- yield;
- costs;
- income;
- result;
- kg/ha;
- cost/kg;
- harvest window.

Partial-data markers mandatory.

## 9. Screen F — Documentos

Group by type:

- delivery tickets;
- invoices;
- receipts;
- phytosanitary/fertilizer docs;
- irrigation documents;
- generic.

Status chips:

```text
Pendiente OCR
Por revisar
Confirmado
Error OCR
Solo archivo
```

Document detail:

- original file/image;
- extracted values;
- confidence support;
- confirmed canonical links;
- audit timestamp.

## 10. Loading states

Use skeletons for projections.

Never replace unknown finance/production values with fake zeros during loading.

## 11. Empty states

Examples:

### No production
```text
Aún no hay entregas en esta campaña.
Añade la primera cuando lleves aceituna a la cooperativa o almazara.
[ Añadir entrega ]
```

### No expenses
```text
Todavía no has registrado gastos.
Puedes añadirlos manualmente o escanear una factura.
[ Añadir gasto ] [ Escanear ]
```

### Yield pending
```text
5.300 kg entregados
Rendimiento pendiente para 2 entregas.
[ Añadir rendimiento ]
```

## 12. Error/offline states

Core local analytics remain available offline.

External unavailable data must never remove campaign analytics.

Attachment/OCR cases:

- image saved locally, OCR waiting for connection;
- OCR failed, manual data entry available;
- upload pending, document remains visible locally.

## 13. Visual benchmark against competitor

Must improve:

- density;
- hierarchy;
- drill-down;
- historical comparison;
- document visibility;
- truthful partial data;
- next actions.

Do not create a screen with only decorative metric cards.

## 14. Phase 3 Figma deliverables

Create at common phone width:

1. Campaign Summary
2. Production
3. Costs
4. Profitability

Then create:

5. History
6. Documents
7. Delivery/OCR review
8. Invoice/OCR review

For the first four screens also create:

- one compact-width proof;
- loading state;
- empty/partial state for at least one key metric.

## 15. Phase implementation handoff

Compose implementation later must map all visible metrics to named projection/use-case outputs, not direct SQL/UI calculations.

Examples:

```text
GetCampaignProductionSummary
GetCampaignCostSummary
GetCampaignProfitabilitySummary
GetCampaignYieldCoverage
GetCampaignDeliveryHistory
GetCampaignHistoricalComparison
```

Exact use-case names may evolve in Phase 2/feature phases, but UI must not embed business accounting formulas.
