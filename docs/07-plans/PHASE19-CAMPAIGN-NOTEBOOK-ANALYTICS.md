# Phase 19 — Cuaderno de campaña + historical analytics

**Status:** PLANNED by CR-005; production start remains after Gate 18  
**Normative product decision:** `docs/00-master/RC1.2-CHANGE-REQUEST-005-CUADERNO-CAMPANA.md`

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

## 19A — implementation notes (2026-09-24, executor Claude, reviewer Codex)

- Farm hub: `Parcelas · Cuaderno · Campañas · Documentos`. The `farm-activities` route is kept
  (deep links, tests) and opened from Cuaderno → Trabajos → "Registrar o planificar trabajo".
- `domain/notebook/CampaignNotebook` is a pure projection: it stores and copies nothing.
  Membership: records linked to the Campaign, plus the Farm's records saved without a
  Campaign whose date falls inside the Campaign's dates. Totals are the same
  `HarvestSummary`, `DeliverySummary` and `ExpenseSummary` the Cosecha/Entregas/Gastos screens
  use (drafts listed, never summed), so the Cuaderno reconciles by construction.
- Cuaderno tabs: **Trabajos** (by month, status chip, tap → Activity), **Recolección** (summary
  strip: recogido, entregado, rendimiento with coverage; Cosecha/Pesada/Gasto actions; rows by
  day: harvests, Pesadas with cooperative and yield-or-pending, harvest days, harvest/transport
  expenses) and **Resumen** (works done/planned, kg, pesadas, weighted yield + coverage,
  expenses by category). Jornada, Jornales and equipment arrive in 19B/19D/19E.
- Campaign in view: harvest first, then active, else the most recent; chips when several.
- Tests: `CampaignNotebookTest` (membership, reconciliation, coverage, draft exclusion) and the
  E2E flows now reach the work list through the Cuaderno.

### 19B implementation notes (Claude, branch `claude/phase19b-jornada-pesadas`)

- **Model.** A Jornada is the existing Harvest (no new table). Room v13 (`MIGRATION_12_13`)
  adds `deliveries.harvest_id` (nullable, indexed, app-enforced) and `deliveries.delivery_time`
  ("HH:mm", nullable). Existing Pesadas stay unlinked and without an hour.
- **Linking is explicit.** The Pesada form has `Jornada de recolección`: *Sin jornada* (default),
  *Nueva jornada de este día*, or one of the Farm's running-Campaign Jornadas. Nothing is
  linked silently. Opening from a Jornada ("Añadir pesada") preselects that Jornada.
- **Rules (`JornadaLedger`, inside the write transaction).** A Pesada may join a live Jornada of
  the same Farm and Campaign, not dated after the Pesada, whose kilos are not split exactly
  among several Parcels. "Nueva jornada" opens a Harvest on the Pesada's day with its origin
  Parcels (or the whole Farm) all *UNALLOCATED* — no per-Parcel kilos are invented.
- **One truthful total.** While a Jornada has live Pesadas, its kilos are their exact sum; every
  create/edit/delete/move of a Pesada re-reconciles it (version bump + outbox intent). The
  Jornada editor shows the kilos read-only ("Suma de sus N pesadas") and rejects an exact
  split. If its last Pesada leaves, the Jornada keeps its kilos as its own editable figure
  (never zeroed). Removing a Jornada releases its Pesadas intact (unlinked).
- **Each Pesada keeps its own** cooperative/mill, net kg, ticket/albarán, hour, attachments/OCR
  and later yield; the Jornada only lists them. "Guardar y añadir otra" keeps Farm, day,
  Jornada, cooperative and Parcels and clears the weighing (kg, ticket, hour).
- **UI.** Jornada detail: "Jornada del …", *Pesadas de la jornada* (`3 pesadas · 5.430 kg ·
  Coop A, Coop B`, one row per Pesada) and *Añadir pesada*. Pesada detail shows its hour and
  that it belongs to a Jornada. Cuaderno → Recolección rows read `Jornada · kg` with the
  Pesada count. A ticket read by OCR is linked afterwards from the Pesada's edit form.
- **Tests.** `JornadaPesadasContractTest` (Gate 19B: three Pesadas on one date to two
  cooperatives survive a database reopen as one Jornada of 5.430 kg; corrections/removals keep
  the total equal; refused links write nothing; removing a Jornada releases its Pesadas),
  `RoomMigrationTest` 12→13, `JornadaScreenTest`, `JornadaTest`, `DeliveryFormTest`,
  `DeliveryRulesTest`. Airplane mode: all paths are local Room writes with no network call;
  the owner/Codex emulator check is the device evidence.

