# Gate CR-010 Checklist — Campaña simple, automatizada y centrada en Pesadas

Status: **OPEN.** Every production slice and the A3 follow-up are merged, with their tests green in
CI; the physical-device run (§17) is pending.

**Gate CR-010** (Amendment 1, note 6): the CR-010 §17 acceptance scenario on a **physical device**,
plus the A1–A3 tests green in CI.

## Slices

| Slice | PR | Room | Status |
|---|---|---|---|
| 1a — lifecycle Borrador → Activa → Cerrada | #288 | — | merged |
| 1b — kg totals from Pesadas; hand-typed history shown apart (A2) | #289 | — | merged |
| 2 — automatic day on save, A1, notes 2–3 | #290 | v17 `harvests.day_origin` | merged |
| 3 — ticket photo/PDF + OCR inside Nueva pesada; hour and cooperative (note 4) | #291 | — | merged |
| 4 — recollection prices + calculated costs through the ledger (A3) | #293 | v18 `recollection_rates` | merged |
| 5 — campaign at a glance (§12) | #294 | — | merged |
| 6 — KPI hierarchy on Campaign/Recolección/Cuaderno (§13) | #295 | — | merged |
| A3 follow-up — unlinked same-date costs shown, linked only by the farmer | #296 | — | merged (CI green on 960a58c7) |

## Automated evidence (CI)

| Criterion | Test evidence |
|---|---|
| Lifecycle Borrador → Activa → Cerrada; legacy HARVEST still closes; reopen is explicit | `CampaignLifecycleContractTest.lifecycleIsBorradorActivaCerradaAndLegacyHarvestStillCloses`, `closeThenReopenIsExplicitAuditedAndClearsTheEndDate`, `CampaignRunningTest` |
| **A1** — an automatic day never keeps kilos it no longer has (delete, move, move back) | `JornadaPesadasContractTest.anAutomaticDayNeverKeepsKilosItNoLongerHas`, `aDayWithWhatTheFarmerTypedOnItStaysAndItsOriginGoesBackToTheWholeFarm` |
| **A2** — totals are the sum of live Pesadas; hand-typed history shown apart, never added | `NotebookViewsTest` (legacy kilos, comparison, chart note), `aHandRecordedJornadaIsNeverReusedAndKeepsItsOwnKilos` |
| **A3** — one ledger entry per day and kind, updated in place; read-only in Gastos | `DayCostContractTest.attendanceAndPricesPostOneCalculatedEntryUpdatedInPlace` |
| A3 — same-day collision: never both; «Usar el cálculo» keeps the hand-typed cost as a draft | `aHandTypedCostOfTheSameKindStandsUntilTheFarmerPicksTheCalculation` |
| A3 — oil adds to the machinery day, a rental replaces it | `oilForTheMachinesAddsToTheirCalculatedDayAndNeverReplacesIt`, `DayCostCalculatorTest.onlyAReplacingHandTypedCostStandsForTheCalculation` |
| A3 — a missing price is unknown, never 0 | `machineryWithoutAPriceCountsNoMoneyAndSaysSo`, `DayCostCalculatorTest` |
| A3 — closed Campaigns are never recalculated nor swapped | `aClosedCampaignKeepsItsCostsAndARemovedDayTakesItsCalculatedOnes`, `aClosedCampaignRefusesToSwapWhichCostCounts` |
| A3 — unlinked same-date cost is ambiguous; only farmer-owned costs, never oil; only the farmer links it (#296) | `anUnlinkedHandTypedCostOfTheSameDateIsShownAndOnlyTheFarmerLinksIt`, `DayCostCalculatorTest.unlinkedSameDateCostsAreAmbiguousOnlyWhenTheyCouldStandForTheCalculation` |
| Mixed Parcels never receive fabricated kg | `DeliveryContractTest.anExactSplitMustReconcileWithTheDeliveredKilos`, `aDaysOriginIsTheUnionOfItsPesadasParcelsWithoutASplit` |
| Pesadas of one day may go to different cooperatives | `threePesadasOfOneDayToTwoCooperativesSurviveRestartAsOneTruthfulJornada` |
| OCR never writes without confirmation; confirmed ticket goes to its automatic day | `readingATicketNeverRecordsADelivery`, `aConfirmedTicketGoesToItsAutomaticDayAndNeverToAHandRecordedJornada`, `anIncompleteReadingNeedsReviewAndAnInvalidReviewWritesNothing` |
| Ticket hour/cooperative proposed only when clear; typed form kept when reading from Nueva pesada | `DeliveryTicketParserTest`, `DeliveryFormTest` |
| Late yield after close; the original Pesada never changes | `yieldCanArriveAfterTheCampaignClosesWhileTheDeliveryStaysHistory`, `aLaterYieldNeverChangesTheOriginalDelivery` |
| Restart keeps the flow | `deliveriesAndYieldSurviveARestart`, `threePesadasOfOneDayToTwoCooperativesSurviveRestartAsOneTruthfulJornada` |
| Old records readable; migrations additive | `RoomMigrationTest.migration16To17MarksOnlyUnweighedJornadasAsAutomaticDaysAndKeepsTypedKilos`, `migration17To18AddsAnEmptyPriceTableAndTouchesNoMoney` |
| Dashboard: days, Pesada/jornal days, cost once, cost/kg, unknown as «—» | `NotebookViewsTest.theCampaignAtAGlanceCountsDaysAndReadsMoneyOnlyFromTheLedger` |

## Before starting

- Install the APK from the `main` build that includes #295 and #296 (Actions → Android CI → the
  run for that merge → artifact `magina-olivo-dev-debug`).
- ⚠️ As in Gate 20, CI signs with an unstable debug key: installing over an earlier APK usually
  requires uninstalling first and **all local data is lost**. Use a fresh install and create the
  test data during the run.
- Have two printed or on-screen weighing tickets to photograph, and a PDF ticket if possible.

## Owner device checks — §17 scenario

Device model / Android version: ________ · APK build: ________ · Date: ________

| # | Step | Expected | Result |
|---|---|---|---|
| S1 | Create a Farm with ≥ 3 Parcels | Farm and Parcels saved | ☐ |
| S2 | Create a Campaign with several Parcels, **activate** it | Status «Activa»; no «Iniciar recolección» step | ☐ |
| S3 | Cuaderno → «+ Nueva pesada» | Compact form, the day is not asked | ☐ |
| S4 | Select 2 Parcels, type the cooperative, then «Añadir vale y leer datos» → photograph ticket 1 | What was typed is kept; kg, ticket number, date, hour (if printed) and cooperative (if it matches one saved) proposed for review | ☐ |
| S5 | Correct one value, confirm | Pesada saved with the corrected value; nothing saved before confirming | ☐ |
| S6 | «Guardar y añadir otra» → second Pesada, same day, **another cooperative** | Both Pesadas on one automatic day; each keeps its own cooperative | ☐ |
| S7 | Open the day | Kilos = sum of both Pesadas; origin = union of Parcels; **no kg per Parcel invented** | ☐ |
| S8 | Add 5 named workers | Jornales listed; calculated cost «Calculado» once prices are set | ☐ |
| S9 | Another day: «Repetir cuadrilla anterior» | Same 5 workers proposed | ☐ |
| S10 | Add 1 tractor + 2 vibradoras; set prices in «Precios de recolección» | One «Maquinaria (calculada)» entry; unpriced items named, never 0 € | ☐ |
| S11 | Add a fuel expense (Gasoil) and an Aceite/lubricante expense | Both count **on top** of the calculated costs | ☐ |
| S12 | Add a hand-typed «Jornales/servicio» cost on the same day | Collision warning; only one counts; «Usar el cálculo» swaps it | ☐ |
| S13 | Gastos → a hand-typed labour cost of the same date with no day, and an «Aceite hidráulico» machinery one | The day asks «¿Es el mismo coste…?» for the labour cost only; «Es el mismo coste: enlazar» makes only one count; the oil keeps adding | ☐ |
| S14 | Later: add yield to each Pesada | Weighted yield + analysed-kg coverage update | ☐ |
| S15 | Cuaderno → Resumen | Days, Pesada/jornal days, first/last Pesada, cost, cost/kg; «—» for unknowns; colours by data kind | ☐ |
| S16 | Close the Campaign | Status «Cerrada»; costs no longer recalculated | ☐ |
| S17 | Add a late yield after close | Accepted; the Pesada's kilos unchanged | ☐ |
| S18 | Historical comparison | Totals and chart notes correct; hand-typed history shown apart | ☐ |

### Additional verifications (§17)

| # | Check | Expected | Result |
|---|---|---|---|
| V1 | Mixed Parcels | Never fabricated kg per Parcel | ☐ |
| V2 | Same day, different cooperatives | Allowed and kept | ☐ |
| V3 | OCR | Never writes without confirmation | ☐ |
| V4 | Money | Never counted twice (Gastos total = Cuaderno total) | ☐ |
| V5 | Force-stop and reopen mid-flow | Everything recorded is there | ☐ |
| V6 | Airplane mode | Pesadas, jornales, costs and OCR still recordable | ☐ |
| V7 | Records made before CR-010 | Still readable; hand-typed kilos kept | ☐ |

## Known limits recorded for this Gate

- «Días de campaña» counts from the Campaign's start date and says so («Desde el …»). Storing
  the real activation date needs a new column. Proposed: Room v19 together with Phase 21A (owner
  to confirm).
- A per-person labour price is not included (§8 "only when necessary").
