# Phase 19 — Historical analytics (design preparation)

**Status:** design preparation only (auxiliary documentation line). No production code:
Phase 19 implementation stays blocked until Gates 16, 17 and 18 pass.
**Roadmap scope:** kg by campaign · kg by date · delivery dates · yield series · weighted
campaign yield + coverage · cross-campaign comparison.
**Gate 19:** charts derive from canonical truth and expose partial/unknown data correctly.

## 1. Canonical sources (already in Room, nothing new to store)

| Figure | Source of truth | Unit | Notes |
|---|---|---|---|
| Harvested kilos | `harvests.total_grams` | integer grams | Per parcel only where `harvest_parcels.allocation_mode = EXACT`; the rest is *sin repartir*. |
| Delivered kilos | `deliveries.net_grams` | integer grams | The original delivery is immutable; a later yield never rewrites it. |
| Fat / industrial yield | `delivery_yield_analyses` | hundredths of % | Separate record; may arrive weeks later or never. |
| Money | `expenses` (POSTED only) | minor units | The only money ledger (D2). Never re-summed from activities. |
| Campaign window | `campaigns.start_date` / `end_date`, status | dates | Closed campaigns are history: read-only. |
| Parcel set | `campaign_parcel_snapshots` | — | Snapshots, never the live parcel (historical truth). |

The existing pure summaries (`HarvestSummary`, `DeliverySummary` with `coveragePercent`,
`ExpenseSummary`) already encode the aggregation rules; Phase 19 extends them into series,
it does not re-derive totals elsewhere.

## 2. Metric definitions

- **Kg por campaña** = Σ harvest grams of the campaign. Separate bar for delivered kilos;
  never merged, never "corrected" one by the other.
- **Kg por fecha** = harvest grams grouped by `harvest_date` (day; week when > 60 days shown).
- **Fechas de entrega** = each delivery as a point on the campaign timeline with its net kilos.
- **Serie de rendimiento** = per delivery with analysis: (date, yield). Deliveries without
  analysis are drawn as "sin análisis", never as 0 %.
- **Rendimiento ponderado de campaña** = Σ(kg × yield) / Σ(kg with analysis) — exactly
  `DeliverySummary.fatYield`, always shown with **cobertura** = analysed kg / delivered kg.
- **Comparativa entre campañas** = same metric side by side for 2–5 campaigns of the same
  farm; per-hectare figures only when every compared campaign has a known area, otherwise
  hidden with a sentence (no invented denominators).

## 3. Partial and unknown data (the core of Gate 19)

| Situation | Display rule |
|---|---|
| No harvest yet | "Aún no has registrado cosecha" — no empty axis, no zero bar. |
| Harvest without exact split | Parcel breakdown shows *sin repartir* as its own segment. |
| Delivery without analysis | Counted in kilos, excluded from yield, shown as a hollow marker. |
| Coverage < 100 % | Weighted yield labelled "sobre el X % de los kilos". |
| Coverage = 0 | Yield card: "Llegará con los análisis de entrega". |
| Missing area | No kg/ha; sentence "Añade superficie para calcular rendimientos". |
| Campaign still open | Series marked "en curso"; comparison ends at today, never extrapolated. |
| Different number of days | Comparisons by campaign totals, or by day-of-campaign, never by calendar date. |

Every chart has a text alternative (summary sentence + table) and never encodes meaning
by colour alone (DESIGN_SYSTEM §2).

## 4. Proposed pure domain (JVM-testable, no Android)

```text
domain/analytics/
  CampaignSeries.of(harvests, deliveries, expenses, campaign) -> CampaignSeries
    harvestByDay: List<DayGrams>
    deliveries: List<DeliveryPoint(date, grams, fat?, industrial?)>
    harvestTotal, deliveredTotal, fatYield: WeightedYield?, coverage: Int
  CampaignComparison.of(List<CampaignSeries>, areas) -> rows + hidden-metric reasons
```

Golden-dataset JVM tests (planned): mixed exact/unallocated harvests; deliveries with and
without analysis; a campaign with zero coverage; open vs closed campaign; missing area.
Each golden total must equal the existing summaries (`HarvestSummary`, `DeliverySummary`).

## 5. UI (reuses the polished components)

- Campaign detail: the summary grid stays; a "Histórico" section adds *Kg por fecha* and
  *Entregas y rendimiento* inside `MoChartContainer` (title + description + text table).
- Farm detail → "Campañas": comparison table first, chart second.
- Charts drawn with Compose `Canvas` (no new dependency), 48 dp touch targets on points,
  values readable at 130 % font.

## 6. Open questions for the owner

1. Per-hectare figures: from campaign parcel snapshots (managed area) or cadastral area once
   Phase 18 imports geometry?
2. Which yield leads the UI — fat (graso) or industrial — or both side by side?
3. Comparison horizon: last 3 campaigns by default, or all?
