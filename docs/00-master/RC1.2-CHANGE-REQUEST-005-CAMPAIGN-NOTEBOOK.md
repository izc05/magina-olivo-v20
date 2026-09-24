# CR-005 — Cuaderno de campaña simple + recolección por jornadas, pesadas y rendimiento

**Decision owner:** 2026-09-24  
**Status:** APPROVED PRODUCT DIRECTION — implementation remains gate-bound  
**Scope:** Android RC1.2, without changing the five frozen root destinations.

## Why this change

The app already has the correct canonical pieces — Campaigns, Activities, Expenses,
Harvest, Deliveries, later Yield, Machinery and Documents — but exposing them as separate
technical modules would make the farmer understand the data model before recording work.

The product direction is therefore:

> **Simple in front, powerful underneath.**

The farmer records what happened. Mágina Olivo projects those canonical records into one
clear **Cuaderno de campaña**. The Cuaderno is a view/workflow over existing sources of
truth; it is **not** a second ledger and must not duplicate kilos, money or activities.

## Navigation / information architecture

The frozen roots stay unchanged:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

Inside one Farm, keep the short CR-004 hub and expose four clear areas:

1. **Parcelas**
2. **Campañas**
3. **Cuaderno**
4. **Documentos**

The current user-facing **Trabajos** entry becomes **Cuaderno**. Existing route/deep-link
compatibility may be preserved internally during migration, but the visible concept is the
Campaign notebook.

### What belongs where

- **Parcelas:** land, grove description, Catastro/map and parcel-specific data.
- **Campañas:** create/activate/close Campaigns and browse prior Campaigns.
- **Cuaderno:** everything that happened during the selected Campaign.
- **Documentos:** farm-level/archive view (deeds, invoices, tickets, photos, reports), with
  links back to the Campaign/Activity/Harvest/Delivery that owns each document.

## Cuaderno structure

The Cuaderno opens on the Farm's running Campaign and uses three simple sections:

### 1. Trabajos del año

A chronological projection of canonical Activities:

- tratamiento fitosanitario;
- abonado;
- riego;
- poda;
- suelo/desbroce;
- observación/incidencia;
- other agricultural work.

The UI remains step-based and compact. Technical/optional fields live behind
**Más datos**, never in one giant vertical form.

For phytosanitary work, the Activity can link its product/purchase/expense and invoice or
receipt. The invoice is stored once in Documents/Attachments; the Cuaderno only references it.

### 2. Recolección

Recollection is visually separated from the rest of the year's work because its workflow is
different and is concentrated into days.

The primary object shown to the farmer is a **Jornada de recolección**:

- date;
- Farm;
- optional Parcel / multiple Parcels;
- pesadas/entregas;
- jornales;
- machinery/equipment used;
- associated expenses;
- documents.

Default targeting is the **whole Farm** because that matches common field practice. The
farmer can optionally target one Parcel or several Parcels when that information is useful.

### 3. Resumen

A visual campaign summary fed only from canonical records:

- total kg;
- number of harvesting days;
- number of weighings;
- analysed-yield coverage;
- weighted average yield;
- expenses and cost/kg when available;
- jornales;
- production by date;
- comparison with earlier Campaigns.

Unknown or partially allocated data must remain visible as unknown/partial; the app must
never invent parcel attribution.

## Jornada de recolección — interaction contract

A Jornada must not be a long form. It opens as a short card/screen with large actions:

`⚖️ Registrar pesada · 👷 Jornales · 🚜 Maquinaria · 💶 Gastos`

A day can be left in progress and completed later. Entering data in one action must not force
the user through the other actions.

### Pesadas / entregas

One Jornada can contain **zero, one or many** weighings.

Each weighing is its own canonical Delivery and has its own:

- kilograms (net weight);
- cooperative/mill destination;
- ticket/albarán number;
- date and optional time;
- photo/PDF of ticket, albarán or supporting document;
- origin Farm and optional Parcel allocation;
- later yield analysis.

**The cooperative is selected per weighing, never globally for the Jornada.** This is
essential because the same farmer can make several trips on the same day to different
cooperatives/mills.

Farmer-facing wording should prefer **Pesada**. Internally it continues to use the Delivery
aggregate, because Delivery already owns destination, kilos, ticket number, OCR and Yield.

### Fast later yield

Yield commonly arrives two or three days after the weighing. The farmer must not have to
reopen the whole Jornada.

Provide **Rendimientos pendientes** with fast search/filter by:

- ticket/albarán number;
- cooperative;
- date;
- Farm.

Selecting a weighing opens a very short action:

`Rendimiento · Fecha de análisis · Foto/documento opcional · Guardar`

The existing separate Delivery Yield record remains authoritative: adding or correcting
yield must not rewrite the original weighing.

### Yield averages

Campaign/Farm weighted yield:

`Σ (analysed kg × yield) / Σ analysed kg`

Always show analysed-kilo coverage when not every weighing has yield.

Parcel yield is only shown when origin attribution supports it:

- one Parcel selected: the weighing can contribute to that Parcel;
- exact multi-Parcel split: use exact kilos;
- mixed/unknown split: do **not** invent a Parcel yield. Keep those kilos in Farm/Campaign
  totals and mark Parcel coverage as partial.

This truth rule is mandatory for historical comparisons.

## Jornales — end-of-day workflow

Jornales are usually recorded at the end of the day.

The first screen is intentionally small:

- people / quantity;
- full day, half day or hours;
- optional cost/rate;
- Farm inherited from the Jornada;
- Parcel optional.

Support two entry modes without turning this into an HR system:

1. **Quick count:** e.g. `5 jornales`.
2. **People/crew:** select saved local names/aliases and register them together.

Useful accelerators:

- **Repetir cuadrilla de ayer**;
- select several people and save once;
- default date = Jornada date.

No payroll, contracts or sensitive HR dossier is introduced by this CR.

## Machinery / equipment — end-of-day workflow

Show icon tiles and quantity controls, for example:

- Tractor × 1
- Vibradora × 2
- Peine eléctrico × 1
- Remolque × 1
- Sopladora × 1
- Other

A line may optionally point to an already registered Machine when the farmer tracks an
individual asset, but quick quantity entry must work without creating every handheld tool as
a full Machine.

## Expenses during recolección

Use the existing authoritative Expense ledger. A Jornada only provides context and a fast
entry point.

Quick categories include:

- gasóleo;
- gasolina;
- aceite/lubricantes;
- alquiler de maquinaria;
- transporte;
- repairs;
- other.

Each Expense can have amount, optional quantity/litres, notes and ticket/invoice photo.
Expense relations to `harvest_id` / `delivery_id` already exist and must be used instead of
creating a second harvest-cost ledger.

## Data-model direction

### No duplicate Cuaderno tables

Cuaderno is a projection of:

`Campaign + Activities + Harvest + Deliveries + Yield + Expenses + Machinery + Documents`

No `notebook_entries` table that copies canonical events.

### Harvest evolves into the Jornada container

The existing Harvest aggregate is the natural base for the Jornada because it already owns
date, Campaign, Farm and parcel origin.

However, current Harvest has one required `weight_grams` value while the new farmer
workflow gets kilograms from one or more Deliveries. Implementation must avoid two editable
sources of truth.

Preferred migration rule:

- add an optional Delivery → Harvest/Jornada relation;
- legacy Harvest rows retain their recorded manual weight unchanged;
- new Jornada flow derives displayed delivered kg from its linked Deliveries;
- if a manual no-ticket weight is supported, it is explicitly labelled manual/unverified;
- the user must never edit a Jornada total independently when linked weighings are the source.

The exact schema migration is an implementation detail, but **two independently editable kg
totals are forbidden**.

### Detailed jornales and equipment

Replace/augment the coarse existing Harvest `worker_count` and `machinery_text` summary
with child records that can express:

- multiple labour entries;
- equipment type + quantity;
- optional link to a registered Machine;
- optional cost relation through the Expense ledger.

Legacy summary fields must remain readable through migration/history.

## Visual rules

- No long multi-section form.
- One task per screen/sheet.
- Large recognisable icons.
- Date/Farm/Campaign inherited whenever already known.
- Optional technical data behind **Más datos**.
- Timeline cards show only the information needed to recognise the event.
- Every save gives a compact confirmation and returns to the Jornada/Cuaderno.
- Outdoor readability takes priority over decorative density.

Example Recolección card:

```text
24 NOV · El Cortijo

5.430 kg · 3 pesadas
5 jornales · 2 vibradoras · 1 tractor
246 € gastos
Rendimiento: 21,4 % · cobertura 68 %

[ Abrir jornada ]
```

## Historical analytics

Phase 19 must use these same canonical records to answer practical questions:

- kg by Campaign and Farm;
- kg by harvesting date;
- cumulative harvest curve;
- yield by date;
- yield by cooperative;
- weighted Farm/Campaign yield and coverage;
- Parcel yield only with truthful allocation;
- jornales and harvesting cost;
- cost/kg;
- expense breakdown;
- year-over-year Campaign comparison.

The intended farmer questions are:

- ¿Cuándo empecé y terminé de coger el año pasado?
- ¿Qué fechas dieron mejor rendimiento?
- ¿Cuántos kilos dio esta finca?
- ¿Qué parcela rindió mejor cuando el reparto es conocido?
- ¿Cuánto me costó recoger?
- ¿Cuántos jornales necesité?
- ¿Cómo cambia este año respecto al anterior?

## Implementation ordering

This CR does **not** bypass the current roadmap gate. Phase 18 remains next. Implementation
should be split after the current gate into small reviewable slices:

1. Cuaderno information architecture and read-only projection.
2. Jornada UX over Harvest + Delivery linking.
3. Fast multi-weighing flow with per-weighing cooperative/document.
4. Pending-yield search and quick entry.
5. Jornales + equipment child records.
6. Recollection expense shortcuts using the existing Expense ledger.
7. Historical/visual analytics.

Every slice must keep offline-first behavior, migration tests and truth-preserving totals.

## Acceptance rules

This change is acceptable only when:

- a farmer can record three weighings in one day, each to a different cooperative, without
  duplicating the Jornada;
- a yield received days later can be found quickly by ticket number and attached to exactly
  that weighing;
- weighted yield remains correct with partial coverage;
- Parcel averages never include unallocated mixed-origin kilos;
- five jornales + two vibradoras + one peine + fuel expense can be entered without one long
  scrolling form;
- the same data appears in Cuaderno, expenses, documents and analytics without being copied
  into competing ledgers;
- historical Campaign data survives later edits to Farms/Parcels;
- the five frozen root destinations remain unchanged.

## Decision

APPROVED as product direction on 2026-09-24. Production implementation remains subject to
the single-track gate and the migration/test requirements above.
