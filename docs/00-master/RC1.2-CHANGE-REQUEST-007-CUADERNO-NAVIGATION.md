# CR-007 — Mi Cuaderno como centro operativo y nueva navegación

**Status:** APPROVED BY OWNER — 2026-09-25 (GitHub Issue #246; UX-A audit approved)  
**Baseline:** `main` at `3dbc58d2420d5899cfc9c9e0d927f96bea25a91a`. Nothing merged is reset or rewritten.

## Motivation

Consultation/analysis and daily registration were mixed: the `Registrar (+)` root only opened a
sheet, the Cuaderno lived deep inside each Farm, and planned work had its own Calendario root.
The owner separates the three questions:

- **Mi Campo** — ¿Cómo está mi explotación?
- **Mi Cuaderno** — ¿Qué he hecho hoy?
- **Campaña** — ¿Cómo está funcionando esta campaña?

All three read the same records; nothing is duplicated.

## Proposed change

1. Bottom navigation: **Inicio · Mi Campo · Cuaderno · Avisos · Perfil**. Cuaderno is the centre.
2. `Registrar (+)` stops being a root. "Registrar hoy" lives inside Cuaderno and reuses the
   existing register choices and forms (no second register system, no second activity model).
3. Calendario stops being a root: its agenda (overdue, today, next days, reminders) is the
   **Avisos** root. Reminder notifications open the Activity over Avisos.
4. "Mi Olivar" is renamed **Mi Campo** (label only; the internal `olivar` route is kept).
5. Internal routes `register` and `calendar` are kept as nested routes (under Cuaderno and
   Avisos) so every existing entry point keeps working.
6. Owner decisions recorded with the UX-A approval: active Farm remembered as a local
   preference (no Room); Jornal outside recolección = LABOR Expense (inside recolección it opens
   the Jornada's jornales); "Maquinaria" quick action = machine use on an Activity; the
   phytosanitary legal model is closed separately.

## Affected baseline sections

- AGENTS.md "Frozen primary navigation"; DESIGN_SYSTEM root destinations; CURRENT-STATE.
- CR-005 keeps its data rules; its Cuaderno becomes a root surface instead of only a Farm section.

## Affected phases

Functional phases (20C onward) are paused while UX-B → UX-G run (Issue #246 §8).

## Data impact

None. No Room schema change, no migration, no new model.

## Offline/sync impact

None. All screens read local Room data.

## UX impact

New root layout; Cuaderno and Avisos roots; old roots reachable as nested routes.

## Risks

Navigation test churn (labels/tags) and deep-link behaviour; covered by updated navigation tests.

## Alternatives considered

Keeping `+` as a centre action button with Cuaderno inside Mi Olivar: rejected by the owner.

## Decision

Approved by the owner (Issue #246, UX-A approval, 2026-09-25).

## New baseline version

RC1.2 + CR-006 + CR-007.
