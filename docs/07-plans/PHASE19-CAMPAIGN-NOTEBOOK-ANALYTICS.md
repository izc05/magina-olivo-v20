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
  Pesada count. A ticket read by OCR chooses its Jornada in the same review (Confirmar entrega).
- **Tests.** `JornadaPesadasContractTest` (Gate 19B: three Pesadas on one date to two
  cooperatives survive a database reopen as one Jornada of 5.430 kg; corrections/removals keep
  the total equal; refused links write nothing; removing a Jornada releases its Pesadas),
  `RoomMigrationTest` 12→13, `DeliveryContractTest` (OCR ticket joins a Jornada), `JornadaScreenTest`, `JornadaTest`, `DeliveryFormTest`,
  `DeliveryRulesTest`. Airplane mode: all paths are local Room writes with no network call;
  the owner/Codex emulator check is the device evidence.

### 19C implementation notes (Claude, branch `claude/phase19c-rendimientos`; design prepared during 19B review)

Prepared by Claude while 19B was in review. Executor Claude, reviewer Codex (handoff table).
No schema change is expected: 19C reads existing `deliveries` + `delivery_yield_analyses`.

- **Domain (pure, JVM-tested).** `PesadaQuery(text, status, cooperative, farmId, from, to)`
  and `PesadaSearch.filter(deliveries, query)`:
  - `text` matches ticket nº or albarán nº, case/space-insensitive, digits-only also match
    (`45872` finds `#45872`, `V-45872`);
  - `status` = `PENDING` (no live analysis) / `WITH_YIELD`;
  - cooperative by organization id, else by copied `destinationName`;
  - newest first; nothing is hidden silently (the empty result says which filter emptied it).
- **Parcel yield (`ParcelYield.of(deliveries)`).** A Pesada contributes to a Parcel's weighted
  yield only when it has one origin Parcel, or an EXACT share for that Parcel (its kilos are
  the weight). Unallocated/mixed kilos count in the Farm/Campaign average (existing
  `DeliverySummary`) and in coverage, never in a Parcel. Missing yield is unknown, not zero.
- **UI.** Cuaderno → Recolección gains `Pesadas` (list with search field
  `Buscar nº de pesada o vale`, chips `Pendiente de rendimiento · Con rendimiento`,
  cooperative and date filters). Row: `#45872 · 27 NOV` / `Cooperativa X · 1.840 kg` /
  `Rendimiento pendiente` or `· 22,8 %`. One tap `Añadir rendimiento` opens the existing
  Phase 14 yield editor (separate record; the weighing is shown unchanged above it).
  Home/Cuaderno badge: `N pesadas sin rendimiento` when N > 0.
- **Tests planned.** `PesadaSearchTest` (ticket digits, status, cooperative, dates),
  `ParcelYieldTest` (single/exact vs mixed never leaks), contract test: yield added 3 days
  later changes only `delivery_yield_analyses` (Delivery row, version and outbox unchanged)
  and the derived metrics; Compose test for the pending list + add-yield path.
- **Gate 19C evidence:** as in the slice definition above.
- **Implemented as designed**, with these concrete choices: `domain/delivery/PesadaSearch.kt`
  (`PesadaQuery`, `YieldStatus`, `PesadaSearch.filter/statusOf/cooperativeKey`, `ParcelYield.of`).
  Search is in **Entregas → Pesadas** (the screen the Cuaderno's *Pesada* action opens); the
  Cuaderno → Recolección shows `N pesadas sin rendimiento`, which opens the same list filtered to
  pending (`deliveries/pending`). *Añadir rendimiento* on a pending row opens the Pesada directly
  on its yield form (`delivery/{id}/yield`). Cuaderno → Resumen gains *Rendimiento por parcela*
  with coverage; mixed-load kilos are stated as counting only in the campaign total.
- **Tests:** `PesadaSearchTest` (ticket digits, status, cooperative, dates, parcel yield never
  leaks a mixed load), `JornadaPesadasContractTest.aYieldAddedDaysLater…` (Delivery and Jornada
  rows unchanged, one outbox intent for the yield only), `PesadaSearchScreenTest`.

### 19D implementation notes (Claude, branch `claude/phase19d-jornales`)

- **Room v14** (`MIGRATION_13_14`): `workers` (reusable person/alias; not payroll/HR) and
  `harvest_labour` (Jornada child: `worker_id` + name snapshot for a named person, or a quick
  count with no person; `quantity`, `unit` FULL_DAY/HALF_DAY/HOURS, `minutes` per person for
  hours). **No money column**: a labour cost is an Expense (recolección), the only ledger.
- **Rules** (`domain/labour`): one line per named person per Jornada (`already_recorded`), a
  named line always counts one person, hours only with HOURS and ≤ 24 h; a closed Campaign's
  Jornada takes no labour; removing a Jornada tombstones its labour. Each line and each person
  has its own outbox intent (`HARVEST_LABOUR`, `WORKER`).
- **Deterministic totals** (`LabourSummary`): people, whole days, half days and hours are kept
  apart — never converted into one another — and do not depend on line order.
- **UI:** Jornada detail → *Jornales* (summary `7 personas · 5 jornadas · 2 medias`, one row
  per line with *Quitar*) and *Registrar jornales*: *Por personas* (chips, *+ Persona*,
  *Repetir cuadrilla anterior (N)* from the Farm's previous Jornada with a named crew,
  `5 seleccionadas`, `Guardar 5 jornales`) or *Solo número*; *Jornada completa / Media
  jornada / Horas* for everyone saved together. Cuaderno: Recolección rows add `N jornales`;
  Resumen adds *Jornales*.
- **Tests:** `LabourTest` (summary, rules, hours parsing), `LabourContractTest` (Gate 19D: five
  people in one save; totals after edits/removal/reopen; no expenses written; no duplicate
  person; repeat previous crew; closed campaign; Jornada removal), `RoomMigrationTest` 13→14,
  `LabourScreenTest` (repeat crew + one → `Guardar 5 jornales`).

### 19E implementation notes (Claude, branch `claude/phase19e-equipment`)

- **Room v15** (`MIGRATION_14_15`): `harvest_equipment` (Jornada child): `type`
  TRACTOR/SHAKER/COMB/TRAILER/BLOWER/OTHER, `label` (the farmer's word for OTHER, or the
  Machine's name), `quantity`, optional `machine_id` (then the line is that one machine).
- **No fake assets:** type + quantity lines never create a Machine; a registered Machine is
  only referenced (it must be live and in the workspace). Rules: 1–50 per line, one line per
  kind / named other / machine, OTHER needs a name.
- **One save:** the sheet replaces the Jornada's lines in one transaction (add / update /
  tombstone), so saving the same sheet twice changes nothing; each line has its own outbox
  intent (`HARVEST_EQUIPMENT`). Removing a Jornada removes its equipment.
- **UI:** Jornada → *Maquinaria* (`1 tractor · 2 vibradoras · 1 peine eléctrico`) and
  *Anotar maquinaria*: `Tractor − 1 +`, `Vibradora − 2 +`, `Peine eléctrico`, `Remolque`,
  `Sopladora`, *Otra (nombre)*, and registered machines as optional chips. Cuaderno → Resumen:
  *Maquinaria (días de uso)*.
- **Tests:** `EquipmentTest`, `EquipmentContractTest` (Gate 19E: the example with 0 machines
  created; deterministic replace; registered machine referenced; closed campaign; Jornada
  removal), `RoomMigrationTest` 14→15, `EquipmentScreenTest`.
- **CI note:** as with every Room version bump, the first run lacks `15.json` until the CI bot
  publishes the exported schema; the migration test is re-run on the next commit.

### 19F implementation notes (Claude, branch `claude/phase19f-gastos`)

- **No new ledger, no schema change.** The Phase 12 `expenses.harvest_id` column (present since
  v6, unused until now) links an ordinary Expense to its Jornada. `Expense.harvestId`,
  `ExpenseDraft.harvestId`; `ExpenseLedgerWriter` checks the Jornada is live and in the same
  workspace/Farm, and takes the Farm and Campaign from it. The Expense form carries the link, so
  editing a Jornada cost never unlinks it.
- **Quick kinds** (`JornadaExpenseKind`) are shortcuts to existing categories: Jornales/servicio
  (LABOR), Gasoil and Gasolina (FUEL), Aceite/lubricante and Maquinaria/alquiler (MACHINERY),
  Transporte (TRANSPORT), Otro (HARVEST). The concept defaults to the kind's name.
- **Jornada cost** (`JornadaCost`) = `ExpenseSummary` of the Expenses linked to it: posted only,
  drafts named "sin contar". It is read from the ledger, never stored, so it equals the posted
  rows exactly and each Expense is counted once in the Campaign.
- **Documents:** *Guardar y añadir foto del tique* saves the Expense and opens it, where the
  existing attachments (photo/PDF) and document OCR already work.
- **Removing a Jornada** keeps its costs in Gastos (real money) and only unlinks them.
- **UI:** Jornada → *Gastos de la jornada* (`Coste 165,50 € · 1 borrador sin contar`, rows open
  the Expense) and *Añadir gasto* (kind chips, importe, concepto opcional). Cuaderno → Recolección
  lists Jornada costs and each Jornada row shows its cost.
- **Tests:** `JornadaCostTest`, `ExpenseFormTest` (edit keeps the Jornada), `JornadaCostContractTest`
  (Gate 19F: cost = SQL sum of posted rows, counted once in the Campaign, edits/deletes follow,
  other Farm refused, removed Jornada keeps the money), `JornadaCostScreenTest`.

