# Phase 19 Gate Checklist — Cuaderno de campaña + historical analytics

Status: **PENDING — automated evidence complete; device evidence pending** (2026-09-24).

Phase 19 production slices 19A–19G are all merged into `main`. The Gate is **not** PASS
until the owner/Codex device checks below are reported. Nothing here is claimed as verified on
a device.

## Slices merged

| Slice | PR | Room | CI on merged head |
|---|---|---|---|
| 19A — Cuaderno projection/navigation | #233 | — | green |
| 19B — Jornada + multiple Pesadas | #235 | v13 | green |
| 19C — Rendimientos pendientes | #236 | — | green |
| 19D — Jornales | #237 | v14 | green |
| 19E — Equipment usage | #238 | v15 | green |
| 19F — Recollection expenses/documents | #239 | — | green |
| 19G — Visual historical analytics | #240 | — | green (foundation, gate3-emulator, gate3-evidence) |

## Automated evidence (CI: unit + instrumented on emulator)

| Gate | Criterion | Test evidence |
|---|---|---|
| 19A | Campaign browsable as a coherent notebook; totals reconcile | Cuaderno projection tests (19A PR) |
| 19B | Three Pesadas on one date, different cooperatives, survive restart; jornada kg = sum | `JornadaPesadasContractTest`, `JornadaScreenTest`, `JornadaTest` |
| 19C | Later yield changes only the Yield record; mixed loads never give invented Parcel kg | `PesadaSearchTest`, `PesadaSearchScreenTest` |
| 19D | Five workers in a few taps; totals consistent; no money in labour | `LabourContractTest`, `LabourScreenTest`, `LabourTest` |
| 19E | `2 vibradoras + 1 peine + 1 tractor` without fake machines | `EquipmentContractTest`, `EquipmentScreenTest` |
| 19F | Jornada cost = posted Expense rows exactly; counted once | `JornadaCostScreenTest` + 19F contract test |
| 19G | Charts reconcile with raw records; unknown shown as unknown | `CampaignAnalyticsTest`, `CampaignChartsScreenTest` |
| — | Migrations 12→13, 13→14, 14→15 | Room migration tests with exported schemas |

## 19G coverage against the slice list

| Required view | Where |
|---|---|
| kg by date, cumulative kg | Gráficas (bars + line) |
| weighted yield by date + coverage | Gráficas (dots + "n de m días con análisis") |
| yield by cooperative | "Por cooperativa" rows with coverage % |
| kg / yield / cost-per-kg by Campaign, year over year | "Comparar campañas" |
| expenses by category | Resumen text rows (19A/19F), not a drawn chart |
| jornales | Resumen labour card (19D), not a drawn chart |
| Parcel analytics with partial allocation | `ParcelYields` (19C): only single-origin/exact kg, coverage shown |

## Pending device evidence (owner / Codex)

Report device model + Android version with each item.

- [ ] Install `magina-olivo-dev-debug` APK from the latest `main` CI run.
- [ ] Airplane mode: create a Jornada with three Pesadas (two cooperatives), kill the app,
      reopen: all three present, jornada kg = sum.
- [ ] Add a yield analysis to one Pesada days later: only that Pesada and derived yields change.
- [ ] Register five jornales and an equipment line (2 vibradoras + 1 peine + 1 tractor).
- [ ] Add two quick costs to the Jornada; Cuaderno cost equals them, no duplicate.
- [ ] Cuaderno → Resumen: Gráficas summary kg equals the Recolección total; a day without
      analysis shows no yield dot; with a second Campaign, "Comparar campañas" shows
      "sin datos" where cost or kg is missing.
- [ ] Reduced motion / large font: charts and summary lines remain legible.

## Decision

- [ ] Gate 19 PASS — owner sign-off after the device evidence above.
