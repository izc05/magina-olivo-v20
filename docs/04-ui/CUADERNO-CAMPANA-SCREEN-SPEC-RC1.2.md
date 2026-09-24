# RC1.2 Screen Specification — Cuaderno de campaña

**Status:** APPROVED IMPLEMENTATION REFERENCE (CR-005)  
**Date:** 2026-09-24  
**Scope:** Farm/Campaign nested experience. No new bottom-navigation root.

## 1. Goal

Make Mágina Olivo feel simple in the field while keeping a professional engine underneath.

The farmer should think:

> “Anoto lo que he hecho; Mágina Olivo ordena el cuaderno.”

The screen must never look like a spreadsheet or one giant ERP form.

## 2. Entry points

Cuaderno can be opened from:

- Farm hub → **Cuaderno**;
- Quick Add actions;
- Parcel context;
- Campaign/history context.

When launched from a known Farm/Parcel/Campaign, that context is prefilled and is not asked again unless the user changes it.

## 3. Farm hub

Primary cards:

1. **Parcelas**
2. **Cuaderno**
3. **Campañas**
4. **Documentos**

Secondary compact access may include **Maquinaria**.

Do not add a root tab.

## 4. Cuaderno home

Header:

`Cuaderno · Campaña 2026/27`

Support line:

`Finca El Cortijo · 4 parcelas`

Top primary action:

`+ Registrar`

Then two large visual groups, not one long mixed timeline.

### Group A — Trabajos del año

Compact quick actions:

- Tratamiento
- Abonado
- Riego
- Poda
- Suelo
- Otro

Recent rows show only essential information:

`22 SEP · Tratamiento · Parcela Norte · Completo`

Tap opens canonical Activity detail.

### Group B — Recolección

Summary card:

- kg delivered;
- number of Pesadas;
- yield average + coverage;
- jornales;
- harvest expenses.

Quick actions:

- **Jornada**
- **Pesada**
- **Jornales**
- **Gasto**

Recent recolección activity is grouped by date.

## 5. Pesadas

### List

Title:

`Pesadas`

Top search:

`Buscar nº de pesada o vale`

Filters:

- Pendiente de rendimiento
- Con rendimiento
- Date
- Cooperative

Each row:

`#45872 · 27 NOV`  
`Cooperativa X · 1.840 kg`  
`Rendimiento pendiente`

or

`#45872 · 27 NOV`  
`Cooperativa X · 1.840 kg · 22,8 %`

Never hide the cooperative on the row.

### Create Pesada

Use a short step flow rather than a large form.

#### Step 1 — Dónde

Default:

`Finca El Cortijo`

Choices:

- Toda la finca / reparto no conocido
- Una parcela
- Varias parcelas

Exact kg per Parcel is only asked when the user explicitly says they know the split.

#### Step 2 — Cooperativa o almazara

Choose one saved Organization or type a one-off name if permitted by the existing Delivery contract.

This selection belongs to this Pesada only.

#### Step 3 — Peso

Fields:

- Nº pesada / vale
- kg netos
- date/time defaults to now

Optional advanced values:

- gross;
- tare;
- member/vehicle reference.

#### Step 4 — Documento

- Hacer foto
- Elegir PDF/imagen
- Omitir por ahora

#### Save

Buttons:

- `Guardar`
- `Guardar y añadir otra`

## 6. Add later yield

From a Pesada row/detail:

`Añadir rendimiento`

Short form:

- analysis date;
- fat yield;
- industrial yield when available;
- notes optional.

The original weighing is visibly preserved.

Search by ticket number must make this workflow usable 2–3 days later.

## 7. Jornada de recolección

The Jornada focuses on people/resources/costs, not on forcing duplicate weight entry.

Header:

`Jornada · 24 noviembre`

Context:

`El Cortijo · Toda la finca`

Four icon cards:

- **Jornales**
- **Maquinaria**
- **Gastos**
- **Pesadas**

Summary:

`5 personas · 2 vibradoras · 3 pesadas · 5.430 kg`

If no linked Pesadas exist:

`Kg pendientes de pesada`

Never show fake zero.

## 8. Jornales

Tap:

`Registrar jornales`

Screen:

- reusable people as selectable chips/cards;
- `+ Persona`;
- Jornada completa / Media jornada / Horas;
- inherited Farm/date/Parcel context;
- optional “Repetir cuadrilla anterior”.

Footer:

`5 seleccionadas`

`Guardar 5 jornales`

If cost is requested, route it through Expense rather than duplicating a second monetary source.

## 9. Maquinaria/equipment

Grid/list with icons:

- Tractor
- Vibradora
- Peine eléctrico
- Remolque
- Sopladora
- Otra

After tap:

`Vibradora   −  2  +`

A registered Machine can optionally be chosen for asset history, but the fast quantity workflow does not require that.

## 10. Harvest expenses

Quick categories:

- Jornales/servicio
- Gasoil
- Gasolina
- Aceite/lubricante
- Maquinaria/alquiler
- Transporte
- Otro

Minimum fields:

- category;
- amount;
- optional concept;
- optional ticket/photo.

Farm/Campaign/Jornada are inherited.

## 11. Fitosanitario

From Trabajos del año:

`Tratamiento`

Short path:

1. Parcel(s)
2. Product
3. Dose / quantity
4. Save

Secondary action:

`Factura o compra`

This opens/links the existing generic OCR + Expense/Purchase workflow.

The treatment record and the invoice are linked but remain separate canonical facts.

## 12. Documents

Cuaderno may project relevant documents:

- delivery/weighing tickets;
- phytosanitary invoices;
- fertilizer invoices;
- fuel/harvest receipts;
- generic agricultural documents.

The Farm-level **Documentos** section remains available as the complete archive.

## 13. Campaign summary and charts

Nested from Campaign or Cuaderno:

### Production

- delivered kg;
- Pesadas count;
- kg by date chart;
- yield by date chart;
- weighted average yield;
- yield coverage.

### Recollection cost

- labour;
- machinery/service;
- fuel;
- transport;
- other.

### Comparison

Compare campaigns:

- total delivered kg;
- weighted yield + coverage;
- harvest window;
- total/harvest cost;
- cost/kg where complete;
- recorded jornales.

Parcel comparison only uses truthfully attributable records.

## 14. Visual density rules

- one primary question per sheet/screen;
- no more than 4–6 primary options visible at once where practical;
- icons have text labels;
- use cards/rows rather than stacked bordered fields;
- hide advanced details by default;
- prefer date=today and active Campaign defaults;
- preserve outdoor contrast;
- large touch targets;
- reduced motion support;
- never use color alone for status.

## 15. Empty and partial states

Examples:

### No Pesadas

`Aún no hay pesadas en esta campaña.`  
`Cuando lleves aceituna a la cooperativa, apunta el vale y los kilos.`

### Yield pending

`3 pesadas · 5.430 kg`  
`Rendimiento pendiente`

### No exact Parcel attribution

`Estos kilos pertenecen a la finca, pero no sabemos el reparto exacto por parcela.`

### No harvest expenses

`Todavía no has añadido gastos de recolección.`

## 16. Truth rules

1. Delivery/Pesada cooperative belongs to each Pesada.
2. Later yield never mutates the original Pesada.
3. Farm metrics may include mixed/unallocated deliveries.
4. Parcel metrics never receive guessed kg.
5. Expense is authoritative for money.
6. Missing data is unknown, not zero.
7. User never types the same kg twice as a mandatory workflow.
8. OCR always requires human review.
9. Cuaderno is a projection/orchestration layer; canonical domain entities remain canonical.

## 17. Implementation order after Gate 18

1. route/IA rename: Farm `Trabajos` → `Cuaderno` without changing root navigation;
2. Cuaderno projection screen using existing Activities/Harvest/Deliveries/Expenses/Documents;
3. Pesada vocabulary + search by ticket + pending-yield queue;
4. harvest-day reconciliation design;
5. individual labour records;
6. harvest equipment quantity records;
7. harvest expense quick categories/links;
8. Phase 19 analytics projections and charts;
9. fitosanitario invoice-link polish;
10. real-device field validation.

Every slice requires migration/tests where applicable and must preserve current data.
