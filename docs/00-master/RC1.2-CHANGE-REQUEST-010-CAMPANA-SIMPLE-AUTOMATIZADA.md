# CR-010 — Campaña simple, automatizada y centrada en Pesadas

**Status:** APPROVED BY OWNER — 2026-09-28  
**Baseline:** RC1.2 + CR-005 + CR-007.  
**Priority:** NEXT PRODUCT UPDATE. This work must be completed before resuming unrelated functional expansion.  
**Supersedes/refines:** the user-facing recolección flow in CR-005, Issue #246 §4B and Issue #254 wherever they require a manually opened/selected Jornada or defer weight-ticket OCR.

## Motivation

The current implementation is technically rich but exposes too many internal concepts to the farmer:
Campaña, iniciar recolección, Jornada, Harvest, Pesada, Entrega, kg recogidos, kg entregados and
multiple entry screens. This makes the normal campaign workflow harder to understand than the
real agricultural task.

The owner has fixed a simpler mental model:

> Activate the Farm's Campaign, record each cooperative/mill weighing, and let Mágina Olivo
> automatically organize the day, people, machinery, expenses and statistics.

The application must do more work so the farmer performs fewer taps.

## Canonical mental model

```text
Finca
  └─ Campaña
      └─ Día de recolección (automatic grouping by date)
          ├─ Pesada 1
          ├─ Pesada 2
          ├─ Pesada N
          ├─ Jornales / personas
          ├─ Maquinaria
          └─ Gastos adicionales
```

### Product vocabulary

User-facing terms:

- **Campaña**
- **Día de recolección** / date
- **Pesada**
- **Jornales**
- **Maquinaria**
- **Gastos**
- **Rendimiento**

The internal `Harvest/Jornada` aggregate may remain for compatibility and persistence, but it
must not be a required concept the farmer has to create or select before recording a Pesada.

## 1. Campaign lifecycle

User-facing lifecycle is deliberately simple:

`Borrador → Activa → Cerrada`

Rules:

1. A Campaign belongs to one Farm.
2. A Campaign may include one or several Parcels of that Farm.
3. Once activated, the farmer can immediately record Pesadas.
4. Do not require a separate **“Iniciar recolección”** action.
5. Closing preserves the complete history.
6. Later yield analysis remains editable/addable after Campaign close.
7. Existing internal `HARVEST` status may be adapted for backward compatibility but should not
   create another mandatory visible step.

## 2. Productive truth: Pesadas are the canonical kg source

**Pesada = one real weighing/delivery to a cooperative or mill.**

Campaign total kilograms:

`SUM(all confirmed Pesada.netGrams in the Campaign)`

Day total kilograms:

`SUM(all confirmed Pesada.netGrams on that Farm/Campaign/date)`

Never ask the farmer to type the same kilograms a second time into a Jornada/Harvest form.

Do not present “kg recogidos” and “kg entregados” as competing principal totals for this workflow.

## 3. Parcels and mixed origin

A Pesada belongs to one Farm/Campaign and may originate from:

- one Parcel;
- several Parcels;
- the Farm when exact Parcel allocation is unknown.

Truth rule:

- one Parcel + known load: the whole load may be attributed to that Parcel;
- several Parcels: do not invent a split;
- exact Parcel kg are stored only when the farmer explicitly knows them.

Farm/Campaign totals always remain valid. Parcel analytics must clearly distinguish:

- **kg assigned exactly**, and
- **kg from mixed Pesadas in which this Parcel participated, without exact split**.

No proportional or equal allocation may be fabricated.

## 4. Automatic day grouping

The farmer does **not** manually open a Jornada as part of the normal flow.

When a Pesada is saved:

1. determine Farm + active Campaign + delivery date;
2. find the internal day/Jornada shell for that Farm/Campaign/date;
3. if it does not exist, create it automatically;
4. link the Pesada;
5. return to the Day/Cuaderno context.

The UI shows **days of recolección**, not the technical requirement to manage Harvest records.

There may be many Pesadas on one day.

## 5. Fast Pesada form

Primary goal: a frequent field operation completed with minimal typing.

### Entry points

- Cuaderno → Registrar hoy → **Pesada**
- Cuaderno → Campaña activa → **+ Nueva pesada**
- Campaign detail → **+ Nueva pesada**
- Day detail → **+ Nueva pesada**

Known Farm/Campaign context is prefilled.

### Main fields

- date/time: default **now**, editable;
- Farm/Campaign: prefilled when known;
- origin Parcels: tap selection;
- cooperative/mill: saved Organization or one-off supported name;
- net kg;
- ticket / weighing / albarán number;
- origin: **Árbol/Vuelo** or **Suelo**.

### Secondary / advanced fields

Keep collapsed unless needed:

- gross weight;
- tare;
- exact kg split by Parcel;
- notes;
- other existing Delivery metadata.

### Repeated trips

Provide **Guardar y añadir otra**.

Keep safe context:

- Farm/Campaign;
- date;
- cooperative (changeable);
- selected origin Parcels where useful.

Reset the new Pesada's own kg, ticket number and unique fields.

## 6. Ticket photo/PDF + OCR is in scope now

Every Pesada can carry its original ticket/vale as:

- camera photo;
- gallery/image;
- PDF where supported by the existing attachment contract.

### Integrated OCR experience

The Pesada form must offer an obvious action such as:

**Añadir vale y leer datos**

Flow:

```text
Nueva pesada
→ Añadir foto/PDF
→ OCR local/service profile DELIVERY_TICKET
→ proposal
→ highlight extracted fields
→ farmer reviews/corrects
→ confirm
→ save Pesada + attachment
```

OCR should propose, when present:

- net kg;
- gross kg;
- tare;
- ticket/weighing/albarán number;
- date;
- time;
- cooperative/mill text;
- any additional reliable ticket identifiers.

### OCR safety invariant

OCR **never auto-confirms or silently writes** the Pesada.

The farmer sees the extracted proposal, corrects it when needed and confirms the values.

If OCR cannot read a field, manual entry remains available immediately.

The original image/PDF remains attached to the Pesada.

## 7. Cuaderno chronology

Cuaderno is the fastest operational entry point.

When a Campaign is active, **Registrar hoy** exposes compact one-tap actions including:

- Pesada
- Jornales
- Maquinaria
- Gasto
- the normal annual-work actions already defined elsewhere.

The Diario/Cuaderno chronology groups recolección by date.

Example:

```text
10 DIC · Los Llanos
8.750 kg · 3 pesadas
5 jornales · 1 tractor · 2 vibradoras
Rendimiento 20,8 % parcial
Coste del día 585 €
```

Opening that date shows the canonical records; it does not create copies.

## 8. Jornales

Jornales belong to the Day/Jornada.

Default workflow is by reusable named people:

- stable Worker ID;
- visible name and surname/full name;
- quick multi-select;
- repeat previous crew;
- full day / half day / hours.

Quick anonymous count may remain only as an optional fallback, not the preferred path.

### Optional labour pricing

Campaign/Farm preferences may provide:

- usual full-day price;
- optional hourly price.

The Day can calculate:

- full day × usual price;
- half day × 0.5 usual price;
- hours × hourly price.

Allow a per-person override only when necessary.

Money must still reconcile with the authoritative Expense ledger. Implementation must choose one
authoritative financial posting path so attendance-derived labour cost is never also counted as a
separate manually duplicated labour Expense.

## 9. Machinery / equipment

Machinery belongs to the Day/Jornada, not each Pesada.

Fast entry supports type + quantity:

- tractor;
- vibradora;
- peine eléctrico;
- remolque;
- sopladora;
- other.

Optional registered `Machine` reference remains available for asset history.

### Optional machinery pricing

Allow a usual per-day/use cost by equipment type or registered Machine.

Examples:

- tractor 60 €/day;
- vibradora 35 €/day.

The Day and Campaign may calculate usage counts and cost, but must not duplicate a manually posted
machinery Expense.

Statistics include usage-days/quantities even when no price is configured.

## 10. Additional expenses

Additional recolección expenses remain contextual entries through the existing Expense ledger:

- fuel;
- lubricant;
- transport;
- repair;
- rental;
- other.

Calculated labour/machinery cost and additional Expense records must have an explicit
non-duplicating financial contract.

Target Day summary:

```text
Labour calculated       350 €
Machinery calculated    130 €
Additional expenses     105 €
--------------------------------
Day cost                585 €
```

Target Campaign analytics include total cost and cost/kg when the denominator and financial data
are sufficient.

## 11. Yield

Yield remains separate from the Pesada.

A Pesada without analysis:

- counts in kg;
- displays **Rendimiento pendiente**;
- never contributes a zero yield.

Later analysis can add/correct:

- fat yield;
- industrial yield;
- analysis date;
- notes/document where supported.

Campaign averages remain kg-weighted and expose analysis coverage.

## 12. Campaign dashboard / statistics

The active Campaign screen must answer in seconds:

- total kg weighed;
- number of Pesadas;
- calendar days since Campaign activation;
- days with Pesadas;
- days with jornales;
- number/total of jornales;
- machinery usage summary;
- weighted yield + analysed-kg coverage;
- costs and cost/kg when available.

Historical dates:

- first day with Pesada;
- last day with Pesada;
- Campaign close date.

Charts:

1. **kg per day** with cumulative kg;
2. **yield by date** with unknown values as gaps;
3. optional cooperative distribution;
4. campaign-to-campaign comparison;
5. labour and machinery totals/cost where meaningful.

Parcel charts may only use exact attribution; mixed participation must be labelled separately.

## 13. Visual redesign for Campaign/Recolección

Keep the Mágina Olivo identity but strengthen hierarchy. The current UI is perceived as too flat
and pastel-heavy for the operational campaign area.

Required direction:

- keep warm cream as page background, not as the dominant component colour;
- stronger deep olive headers/primary states;
- richer earth/gold accents for yield/value;
- distinct visual accents for Pesadas, Jornales, Machinery and Costs;
- larger KPI typography;
- stronger card hierarchy and section separation;
- bigger meaningful icons, not decorative micro-icons;
- charts must feel like first-class product components;
- selected/current state must be immediately obvious;
- do not rely on colour alone for meaning;
- maintain accessibility contrast and touch targets.

This CR explicitly authorizes a targeted visual refinement of the Campaign/Recolección/Cuaderno
surfaces while preserving the broader brand system.

## 14. What disappears from the normal user flow

Do not require:

`Activate Campaign → Start Harvest → Open Jornada → Select Jornada → New Pesada`

Target:

`Activate Campaign → New Pesada`

The internal model can preserve compatibility, but the farmer should not have to manage it.

Remove or demote:

- duplicate Harvest/Cosecha entry points that merely repeat Pesada totals;
- “Entrega” when it is the same user action as Pesada;
- manually editable day kg totals;
- repeated Farm/Campaign selectors when context is already known.

## 15. Compatibility / data migration

Preserve existing Room data and historical records.

Preferred strategy:

- reuse Campaign, Delivery, DeliveryYieldAnalysis, attachments, Labour, Equipment, Expense;
- keep Harvest/Jornada internally as compatibility/day aggregate where useful;
- migrate/add only what is required for automatic day creation, pricing preferences and
  non-duplicating cost linkage;
- no destructive migration;
- no fabricated values during migration.

Existing unlinked historical records remain readable.

## 16. Offline-first

All primary field operations work without network:

- save Pesada;
- attach local ticket;
- create/find Day/Jornada;
- save jornales;
- save machinery;
- save expenses.

OCR must degrade safely: if its engine/provider is unavailable, the ticket stays attached and the
farmer can fill the form manually.

## 17. Acceptance scenario

A physical-device E2E must prove:

```text
Create/choose Farm
→ create Campaign with several Parcels
→ activate Campaign
→ Cuaderno
→ Pesada
→ select 2 Parcels
→ photograph ticket
→ OCR proposes kg + ticket number (+ other readable fields)
→ farmer confirms/corrects
→ save
→ Guardar y añadir otra
→ save second Pesada same day
→ Day is created/grouped automatically
→ add 5 named workers
→ repeat previous crew on another day
→ add 1 tractor + 2 vibradoras
→ add fuel expense
→ later add yield to each Pesada
→ Campaign totals and charts update
→ close Campaign
→ add a late yield after close
→ historical comparison remains correct
```

Verify additionally:

- two mixed Parcels never receive fabricated kg;
- different Pesadas on same day may use different cooperatives;
- OCR never writes without confirmation;
- no money is counted twice;
- cold start/restart preserves the flow;
- airplane mode preserves core recording;
- old Campaign/Harvest/Delivery records remain readable.

## 18. Explicitly deferred from this CR

Do not mix into this implementation:

- EPI worker-signature compliance workflow;
- redesign of planned-work reminders/forms;
- unrelated Home/weather/market expansion;
- professional payroll/HR;
- advanced machinery maintenance.

Those may be specified separately after this Campaign update is stable.

## Affected baseline sections

- CR-005 — Cuaderno de campaña and recolección user flow;
- CR-007 — Cuaderno as the operational root;
- Phase 13 Harvest user-facing role;
- Phase 14 Delivery/Pesada + ticket OCR;
- Phase 19B–19G;
- Campaign/Recolección visual implementation.

## Data impact

Potential additive changes:

- automatic day/Jornada uniqueness or lookup contract by Farm/Campaign/date;
- pricing preferences / snapshots for labour and machinery if required;
- authoritative linkage for calculated costs to avoid double counting;
- indexes useful for ticket/date/campaign search.

No data may be destroyed or backfilled by guess.

## Risks

- double counting Harvest vs Delivery kg;
- duplicate money from calculated vs manual expenses;
- wrong automatic day linkage;
- OCR misread accepted without review;
- visual redesign becoming a second design system;
- parcel analytics fabricating mixed-load attribution.

Every risk above is explicitly prohibited by this CR.

## Decision

**APPROVED BY OWNER — 2026-09-28.**

This is the next priority product update. Production implementation should be executed as one
coherent Campaign/Cuaderno UX correction, split into reviewable commits/PR slices if needed, before
resuming unrelated feature expansion.

---

## Amendment 1 — implementation contract (APPROVED BY OWNER, 2026-09-28)

Added after a code review of `main` (`cb98c700`) against this CR. It closes four ambiguities that
would otherwise reintroduce exactly the risks this CR prohibits, and records six implementation
notes. It narrows *how*, never *what*: every rule above still applies.

### A1. An automatic day never keeps kilos it no longer has (fabricated kg)

Today `JornadaLedger.reconcile` leaves a Jornada's kilos untouched when its last linked Pesada is
deleted or moved to another date ("keeps its kilos as its own figure"). That was designed for
hand-typed historical Jornadas; for an automatic day it would keep kilos no Pesada supports.

Rule:

- a day/Jornada **created automatically** by a Pesada (mark it, e.g. `origin = AUTO_DAY`) derives
  its kilos only from its live Pesadas;
- when it has no live Pesadas left it returns to **«Kg pendientes de pesada»** (never a kept or
  typed figure) and, if it has no jornales, machinery or expenses either, it is soft-deleted;
- the current "keep own figure" behaviour stays **only** for legacy hand-typed Jornadas;
- tests: delete the only Pesada, move a Pesada to another date, move it back.

### A2. Kilogram totals with legacy data (no loss, no double count)

Principal Campaign/Day total = `SUM(live Pesada.netGrams)`.

Legacy Harvest/Jornada records with hand-typed kilos and **no** linked Pesada are neither dropped
nor added into that total. They are shown separately as **«kg registrados sin pesada
(histórico)»**, in the Campaign summary, comparisons and charts' notes. A Jornada with linked
Pesadas contributes only through its Pesadas. «kg recogidos» vs «kg entregados» stop being
competing principal totals (§2).

### A3. One authoritative money path for calculated labour/machinery cost

Today jornales and equipment write no money; costs live only in the Expense ledger
(`ExpenseOrigin = MANUAL | ACTIVITY_COST | DOCUMENT_OCR`).

Rule:

- a calculated day cost is **posted to the Expense ledger** as one linked entry per Day and kind:
  new origins `DAY_LABOUR` and `DAY_EQUIPMENT`, linked to the Day/Jornada id;
- it is recalculated (updated in place, same id, new version) whenever attendance, quantities or
  prices change; removed when the Day's attendance/usage is removed;
- if a **manual** LABOR/MACHINERY expense already exists for that Farm/date, the app warns and asks
  which one stands; it never counts both;
- Day/Campaign cost and cost/kg read **only** the ledger, so nothing can be summed twice;
- price changes never rewrite closed Campaigns: the price used is snapshotted on the posted entry.

### A4. Room version order

This CR's schema additions (day origin mark, day lookup index, pricing preferences/snapshots,
Expense origins/links) take **Room v17**. Phase 21A `profile_settings` moves to **Room v18**.
Migrations are additive and non-destructive; `RoomMigrationTest` covers 16→17.

### Implementation notes (existing code to change)

1. **Lifecycle.** `close()` accepts ACTIVE as well as HARVEST; `reopen()` returns to ACTIVE;
   the «Iniciar recolección» button disappears; every UI check that means "recolección running"
   (Notebook quick actions, Registrar hoy, labels) treats ACTIVE and HARVEST alike. Existing HARVEST
   rows keep working unmigrated and read as «Activa».
2. **Automatic day.** The Pesada form stops defaulting to «Sin jornada»; saving finds or creates the
   day by Farm + Campaign + date. The day's Parcels are the **union** of its Pesadas' origins; editing
   a Pesada's date moves it to the right day (A1 applies to the day it leaves). A legacy day with an
   exact split among several Parcels cannot hold Pesadas: create a new automatic day beside it
   and show both in that date.
3. **Several legacy Jornadas on one date.** Lookup is deterministic (oldest live `AUTO_DAY` first,
   else oldest live Jornada that accepts Pesadas); the date view groups all of them, hiding none.
4. **OCR inside Nueva Pesada.** `confirmDeliveryTicket` must save through the same writer as the
   form (so it also gets the automatic day), and the ticket parser gains **time** and
   **cooperative/mill text**. The no-auto-accept rule already holds and stays tested.
5. **Late yield.** `recordYield` already accepts closed Campaigns; add a test that fixes it.
6. **Gate.** Implementation starts after Gate 20 PASS. **Gate CR-010** = the §17 scenario on a
   physical device plus A1–A3 tests green in CI.

### Delivery slices (one PR each, in order)

1. Lifecycle Borrador → Activa → Cerrada + kg totals/legacy rule (A2).
2. Automatic day on save + A1 + notes 2–3.
3. OCR inside the Pesada form + parser time/cooperative (note 4).
4. Pricing preferences + calculated costs through the ledger (A3, Room v17).
5. Campaign dashboard and charts (§12).
6. Targeted visual refinement of Campaign/Recolección/Cuaderno (§13).
