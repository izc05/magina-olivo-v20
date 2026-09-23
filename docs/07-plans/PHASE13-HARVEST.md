# Phase 13 — Harvest implementation plan

**Status:** IMPLEMENTED — awaiting CI evidence for Gate 13
**Precondition:** Gate 12 PASS, merged into `main` through PR #208 as `2fbeb935`
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 13:

- date / campaign / origin parcels;
- kg;
- exact per-parcel allocation when known;
- explicit mixed / unallocated mode;
- collection method / machinery relation.

**Gate 13:** totals remain truthful and no parcel split is fabricated.

Normative sources: `MASTER-SPEC-RC1` §12 (Harvest is distinct from an Activity and from a
Delivery; the system never fabricates a parcel production split), `DATA-MODEL-RC1-FUTURE`
§11 (`harvests`, `harvest_parcels`, reconciliation rule), `RC1-NORMATIVE-ADDENDUM` D6
(`harvest_parcels` are children of the Harvest aggregate; unknown allocation stays
`UNALLOCATED`) and D10, `RC1.1-PRODUCT-LOCK` §9, `SCREEN-MAP-RC1` S70–S72,
`DESIGN_SYSTEM` "Harvest" (parcel/campaign breakdown without fabricated allocation).

## Normative reconciliation

1. **Weight stays in grams.** Schema v2 shipped `harvests.weight_grams` (integer); the data
   model writes `total_weight_kg Decimal`. Integer grams add up exactly, which is what the
   reconciliation rule needs, so the column is kept and the UI speaks kilos.
2. **Reconciliation tolerance is zero.** The data model allows "configured rounding
   tolerance". In integer grams no rounding happens, and any tolerance would be a small
   fabricated split, so exact per-parcel kilos must equal the total to the gram.
3. **Allocation is per row, the mode is derived.** `allocation_mode` lives on
   `harvest_parcels` as in the data model. A Harvest is `EXACT` when every row is exact,
   `UNALLOCATED` when none is, and `PARTIAL` otherwise — the farmer knows some parcels'
   kilos and the rest of the total is shared, unattributed, by the others.
4. **`harvests.destination` is not used.** It shipped in v2; where olives go is a Delivery
   (Phase 14). It is left in place, never written.
5. **Machinery is text.** `machinery_text` as in the data model; the relation to machinery
   records arrives with Phase 15.

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| Which Campaign does a Harvest belong to? | The Farm's running Campaign (`ACTIVE` or `HARVEST`). A Farm without one cannot record a Harvest, and the form says why. |
| Which Parcels can be origins? | Only the Parcels of that Campaign (`campaign_parcels`), under the name they carry today, kept as `parcel_name_at_harvest`. |
| Default split with several Parcels | "No conozco el reparto exacto": only the total. Kilos per Parcel appear only after the person chooses "Conozco los kilos de cada parcela" and types them; a Parcel left blank stays unknown. |
| One origin Parcel | It carries the whole total, stored `EXACT`: that is a fact, not a split. |
| Known kilos already equal to the total while other Parcels are "unknown" | Refused: it would say those Parcels gave nothing, an exact split in disguise. |
| Dates | Not after today and not before the Campaign start. |
| Closed Campaign | Its Harvests are history: read-only, neither edited nor deleted. |
| Delete | Soft delete, one `DELETE` intent; the kilos leave every total. |
| Attachments | Harvest is a new attachment owner (photos, weighing notes). |

## Room v7

`MIGRATION_6_7` adds `collection_method`, `worker_count` and `machinery_text` to `harvests`
as nullable columns — every existing row is kept unchanged, with unknown collection data —
and creates `harvest_parcels` empty. No origin Parcel is guessed for a pre-existing
Harvest. `7.json` is exported by the Room compiler in CI.

## Implementation

| Slice | Files |
| --- | --- |
| A — rules | `domain/harvest/Weight.kt`, `domain/harvest/HarvestRules.kt` (validation, allocation mode, `HarvestSummary`), `domain/harvest/HarvestRepository.kt` |
| B — Room v7 | `entity/HarvestEntities.kt`, `entity/CoreEntities.kt`, `dao/HarvestDao.kt`, `model/HarvestRows.kt`, `DatabaseMigrations.kt` |
| C — repository | `OfflineFirstHarvestRepository` (one transaction, one `HARVEST` intent per aggregate) |
| D — UI | `feature/harvests/*`: S70 list with truthful campaign totals, S71 form, S72 detail; the "Registrar cosecha" entry and Home shortcut now open it. No new root. |
| E — tests | `HarvestContractTest`, `RoomMigrationTest` 6→7, `WeightTest`, `HarvestRulesTest`, `HarvestFormTest` |

Out of scope: Deliveries, delivery tickets and yield (Phase 14), machinery records (Phase
15), harvest charts and campaign analytics (later phases), the Expense form's Harvest
relation, remote sync.
