# CR-005 — Cuaderno de campaña y flujo de recolección

**Status:** APPROVED BY OWNER — 2026-09-24  
**Baseline:** RC1.2 remains in force. This CR refines information architecture and extends later data/UI slices without changing the five frozen roots.  
**Implementation timing:** documentation/preparation may land now; production implementation waits until the active Phase 18 Gate is closed.

## Motivation

The agricultural core is already implemented as separate truthful domains: Activities, Harvest, Deliveries, Expenses, Documents and Machinery. The farmer, however, should not have to understand those technical boundaries.

The owner wants a simpler mental model:

- everything that belongs to the agricultural campaign is visible through one **Cuaderno**;
- normal work carried out during the year is visually separated from the **recolección** period;
- the entry workflow is short, icon-led and contextual rather than one long form;
- the internal engine remains strict and powerful: canonical records, offline-first persistence, historical truth, no duplicated money and no fabricated parcel allocation.

The existing Phase 14 Delivery/Yield model already matches an important real-world detail: one day may contain several independent weighings/deliveries, potentially to different cooperatives, and yield is received later.

## Proposed change

### 1. Frozen primary navigation is unchanged

Keep exactly:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

No sixth root is introduced.

### 2. Farm hub information architecture

The Farm detail remains the short visual hub approved by CR-004, but its four main entries become:

`Parcelas · Cuaderno · Campañas · Documentos`

- **Cuaderno** supersedes the user-facing label **Trabajos**.
- The canonical Activity engine remains unchanged underneath.
- **Maquinaria** remains a shared resource reachable from Mi Olivar / contextual secondary action; it is not made Farm-owned.
- Quick Add remains available from the center `+` action.

### 3. Cuaderno is an orchestration/projection surface, not a duplicate database

Cuaderno groups existing and future canonical records for the selected Farm + Campaign.

It has two visually distinct areas:

#### A. Trabajos del año

- tratamiento fitosanitario;
- abonado;
- riego;
- poda;
- suelo/desbroce;
- mantenimiento/incidencia;
- planificación;
- linked expenses and documents.

#### B. Recolección

- jornadas de recolección;
- pesadas;
- jornales;
- maquinaria/equipment used;
- fuel, lubricant, transport and other harvest-related expenses;
- later yield;
- campaign harvest summary.

The same canonical entities continue to power history, reports and synchronization. Cuaderno never copies an Activity, Delivery or Expense merely to display it.

### 4. User-facing term: “Pesada”

For the farmer-facing UI, **Pesada** is the preferred label for the canonical `Delivery` domain record.

One Pesada represents one actual weighing/delivery ticket and therefore keeps its own:

- Farm and current Campaign;
- date/time;
- cooperative or mill **selected on that Pesada**;
- ticket / weighing / vale number when available;
- delivered net kilograms;
- ticket photo/PDF;
- optional Parcel origin selection/allocation;
- later yield analysis.

The cooperative is never inherited from the day or from another Pesada. A farmer can therefore record several Pesadas on the same day and send each to a different cooperative.

The engineering/domain type may remain `Delivery`; this is a UX vocabulary change.

### 5. Fast Pesada workflow

Default flow:

`Dónde → Cooperativa → Nº de pesada/vale + kg → Foto opcional → Guardar`

Behavior:

- Farm/Campaign context is prefilled when launched from a Farm, Parcel or Cuaderno.
- Origin may be:
  - entire Farm / no exact Parcel allocation;
  - one Parcel;
  - several Parcels.
- No Parcel kilos are invented.
- “Guardar y añadir otra” is available for repeated trips.
- The most recently used cooperative may be offered as a convenience, but it must remain explicitly changeable before save.
- Ticket/document OCR remains assistive and requires review.

### 6. Later yield workflow

Yield normally arrives days after the Pesada. It remains a separate `DeliveryYieldAnalysis`, preserving the Phase 14 invariant that later analysis never rewrites the original weighing.

Cuaderno → Recolección → Pesadas must offer:

- search by ticket/weighing number;
- search/filter by date;
- filter by cooperative;
- status `Rendimiento pendiente` / `Rendimiento añadido`;
- one-tap “Añadir rendimiento”.

Yield metrics:

- Farm/Campaign weighted average uses analysed delivered kg and always shows coverage;
- Parcel weighted average includes only deliveries whose Parcel allocation is truthful enough to support that Parcel metric;
- mixed/unallocated deliveries contribute to Farm/Campaign totals but are never spread across Parcels by guess;
- missing yield is unknown, never zero.

### 7. Jornada de recolección

A Jornada represents how the work was carried out on a date and Farm, with optional Parcel scope.

User entry must be short:

`Finca → Parcela(s) opcional → Jornales → Maquinaria → Gastos → Guardar`

Important rule: **the farmer must never be required to type the same kilograms twice**.

The implementation plan must preserve the existing Harvest-vs-Delivery distinction and define an explicit reconciliation between a harvest work session and one or more Pesadas. If Pesadas are the kg source, the daily UI total is derived from linked canonical deliveries rather than copied by hand.

### 8. Jornales

Recollection labour is recorded per person at the end of the day.

Fast path:

- select one or several reusable people;
- whole day / half day / hours;
- optional Parcel scope inherited from the Jornada;
- “repetir cuadrilla anterior” convenience;
- save all selected people in one action.

If a monetary cost is entered, the **Expense ledger remains authoritative**. Labour attendance/quantity and money must not create two competing financial totals.

### 9. Harvest machinery and simple equipment quantities

The farmer must be able to record usage such as:

- 1 tractor;
- 2 vibradoras;
- 1 peine eléctrico;
- 1 remolque;
- 1 sopladora;
- other.

The model should support both:

- a reference to a registered `Machine` when the farmer wants asset-level history;
- a lightweight equipment type + quantity when individual asset registration would add unnecessary friction.

This extends harvest usage without changing the Phase 15 rule that Machinery itself is a shared resource.

### 10. Recollection expenses and consumables

Quick categories include:

- jornales/services;
- gasoil;
- gasolina;
- aceite/lubricante;
- alquiler/maquinaria;
- transporte;
- other.

Optional quantity/unit may be kept where useful, but actual money is always recorded through the authoritative Expense ledger and may carry a ticket/invoice.

### 11. Fitosanitario + invoice/document relationship

The normal-year Treatment flow stays short:

`Parcela(s) → Producto → Dosis/cantidad → Guardar`

Advanced/optional details live behind “Más datos”.

From the treatment or related purchase the user can attach or associate:

- phytosanitary invoice;
- receipt;
- product purchase;
- supplier;
- expense.

OCR may propose values but never confirms the treatment, product purchase or money silently.

### 12. Visual rules

- no giant vertically stacked form;
- prefer one short screen/sheet per decision;
- large icon-led actions;
- defaults from current context;
- advanced fields under “Más datos”;
- clear save confirmation;
- offline save first;
- avoid duplicated labels and repeated context selectors;
- CR-003/CR-004 visual system remains canonical.

## Affected baseline sections

This CR refines, but does not replace:

- `RC1-BASELINE.md` core hierarchy;
- `RC1.2-PRODUCT-LOCK.md` §§4–9;
- CR-004 Farm hub composition;
- Phase 12 Expenses/OCR;
- Phase 13 Harvest;
- Phase 14 Delivery/Yield;
- Phase 15 Machinery;
- Phase 19 Historical analytics;
- Phase 25 Reports/PDF.

Primary navigation, offline-first architecture, sync model, app-owned Parcel identity and historical snapshot rules are unchanged.

## Data impact

Likely additive implementation work after Phase 18:

- harvest/jornada ↔ delivery linking or equivalent truthful reconciliation;
- individual labour entries;
- harvest equipment usage with quantity and optional Machine reference;
- Expense links/categories sufficient to filter harvest costs;
- searchable ticket/weighing identifier index if not already indexed.

Migration design must preserve every existing Harvest, Delivery, Expense and Yield record.

No migration may fabricate:

- Parcel kg;
- worker identities;
- equipment quantities;
- costs;
- yields.

## Offline/sync impact

All new records are local-first.

- Save succeeds locally before network synchronization.
- Child rows follow their aggregate/outbox contract.
- Attachments keep the existing durable local-file lifecycle.
- Yield remains independently versioned from Delivery.
- Expense remains the only authoritative money ledger.

## UX impact

The user sees a simpler structure while the domain engine becomes richer.

Farm hub:

`Parcelas · Cuaderno · Campañas · Documentos`

Cuaderno:

`Trabajos del año` and `Recolección`

Registrar (+) continues to provide direct shortcuts so Cuaderno is never a mandatory detour.

## Analytics impact

Phase 19 must expose truthful visual comparison by Farm/Campaign and, where attribution supports it, Parcel:

- delivered kg by date;
- weighted yield + coverage;
- recorded harvest labour;
- harvest cost breakdown;
- total campaign expenses;
- cost/kg where denominators are complete;
- cross-campaign comparison;
- yield-by-date history;
- “highest recorded yield” may be highlighted as a historical fact, but the UI must not claim that a date is agronomically “best” without a defined evidence model.

## Risks

- accidental double-counting between Harvest and Delivery;
- false Parcel metrics from mixed deliveries;
- duplicate money between labour/equipment UI and Expense;
- turning Cuaderno into another long ERP form;
- making a cooperative look fixed for the whole day;
- silently linking a Pesada to the wrong Jornada when several work sessions exist.

All are explicitly prohibited.

## Alternatives considered

1. Keep separate feature screens only — rejected because it exposes engineering structure instead of the farmer's workflow.
2. Merge Harvest and Delivery into one record — rejected because field collection and cooperative weighing are different facts.
3. Store all recolección details as free text — rejected because it prevents useful historical comparison.
4. Force every machine to be registered individually — rejected because simple quantity-based equipment logging is needed.

## Decision

**APPROVED.**

RC1.2 remains the baseline. This CR is an approved refinement/extension and does not change the five root destinations.

Production implementation is intentionally deferred until the current Phase 18 work is closed. The first implementation slice should be planned together with Phase 19 so the new Cuaderno projections and the historical analytics are built from the same canonical truth.
