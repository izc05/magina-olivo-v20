# Phase 19 — Cuaderno de campaña + historical analytics

**Status:** PLANNED by CR-005; production start remains after Gate 18  
**Normative product decision:** `docs/00-master/RC1.2-CHANGE-REQUEST-005-CAMPAIGN-NOTEBOOK.md`

## Goal

Turn the existing canonical agricultural records into one farmer-friendly Cuaderno without
creating a second source of truth. Recollection must support real daily practice: several
weighings, possibly to different cooperatives, yield received days later, end-of-day
jornales, equipment and expenses.

## Product surface

Inside `Mi Olivar → Finca`:

`Parcelas · Campañas · Cuaderno · Documentos`

Cuaderno has three visible sections:

`Trabajos · Recolección · Resumen`

No new root tab.

## Slice 19A — Cuaderno projection and navigation

- Rename the visible Farm `Trabajos` entry to `Cuaderno`.
- Keep route/deep-link compatibility while introducing the new user-facing structure.
- Build one Campaign-scoped timeline that projects Activities, Harvest days, Deliveries,
  Expenses and related Documents.
- Split the screen visually into `Trabajos`, `Recolección`, `Resumen`.
- Do not duplicate canonical rows.

**Gate 19A:** one Campaign can be browsed as a coherent notebook offline; totals reconcile
with their source repositories.

## Slice 19B — Jornada + multiple pesadas

- Evolve Harvest into the Jornada container.
- Add optional Delivery → Jornada relation.
- One Jornada may link N Deliveries.
- Destination cooperative/mill is chosen **on each Delivery/Pesada**.
- Each Pesada supports kg, ticket/albarán number, date/time and attachment/OCR.
- Whole Farm is the default origin context; Parcel / multiple Parcels are optional.
- No independent editable total when linked Deliveries are the kg source.

**Gate 19B:** three weighings on one date, including different cooperatives, survive restart
and airplane mode and produce one truthful Jornada summary.

## Slice 19C — Rendimiento pendiente

- Add `Rendimientos pendientes`.
- Search/filter by ticket number, cooperative, date and Farm.
- Use existing `delivery_yield_analyses`; do not mutate the Delivery.
- Show weighted yield + analysed-kg coverage.
- Parcel yield only from single-Parcel or exact allocated kilos.

**Gate 19C:** adding yield days later changes only the Yield record and derived metrics;
mixed-origin kilos never leak into a fabricated Parcel average.

## Slice 19D — Jornales

Add lightweight Harvest/Jornada labour children.

Minimum data:

- Jornada;
- optional local person/alias;
- quantity when using quick-count mode;
- full / half / hours;
- optional units/hours;
- optional cost/rate metadata.

UX:

- register several people in one save;
- quick `N jornales`;
- `Repetir cuadrilla de ayer`;
- no payroll/HR dossier.

**Gate 19D:** five workers can be recorded in a few taps and the daily/campaign total remains
deterministic after edits.

## Slice 19E — Equipment usage

Add Jornada equipment lines:

- equipment type/label;
- quantity;
- optional registered `machine_id`;
- optional notes/usage.

Provide icon presets for Tractor, Vibradora, Peine eléctrico, Remolque and Sopladora.

**Gate 19E:** `2 vibradoras + 1 peine + 1 tractor` is representable without creating fake
individual Machine assets.

## Slice 19F — Recollection expenses/documents

Reuse the Phase 12 Expense ledger and existing `harvest_id` / `delivery_id` relations.

Fast categories:

- gasóleo;
- gasolina;
- lubricants;
- rental;
- transport;
- repairs;
- other.

Support camera/document attachment. No new monetary ledger.

**Gate 19F:** Jornada cost equals the authoritative posted Expense rows exactly; no double
counting.

## Slice 19G — Visual historical analytics

Charts/cards derive from canonical truth:

- kg by Campaign;
- kg by date;
- cumulative kg;
- weighted yield by date and Campaign;
- yield by cooperative;
- analysed-yield coverage;
- expenses by category;
- jornales;
- recollection cost/kg;
- year-over-year comparison.

Parcel analytics must display coverage/unknown state whenever origin allocation is partial.

**Gate 19G / Phase 19:** charts reconcile with raw records and expose partial/unknown data
instead of inventing values.

## Migration discipline

All schema changes are additive where practical and must include exported Room schemas,
forward migration tests and restart/airplane-mode contract coverage. Legacy Harvest
`weight_grams`, `worker_count` and `machinery_text` remain readable. Migration must not
rewrite historical meaning merely to fit the new UI.

## Non-goals

- payroll/contracts;
- full workforce management;
- inventory/warehouse;
- automatic unreviewed OCR posting;
- SIEX/RETO integration;
- settlement/payment accounting;
- changing the five root destinations.
