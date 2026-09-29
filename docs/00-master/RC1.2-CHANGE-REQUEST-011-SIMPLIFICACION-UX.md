# RC1.2 Change Request 011 — Simplificación y pulido UX

Status: **APPROVED BY OWNER, 2026-09-29** (plan «MÁGINA OLIVO — PLAN DE SIMPLIFICACIÓN Y PULIDO UX»,
sent by the owner in the executor session).

Executor: Claude. Reviewer: Codex.

## Motivation

The agricultural core is implemented (CR-010 merged). What remains before a near-final version
and Play Store is not new function but fewer decisions: two Cuadernos, a «Registrar hoy» that
repeats the quick actions, a manual «Abrir jornada de hoy», «Jornada» next to «Jornal», and the
same action reachable by several paths.

Principle: **«Menos opciones visibles, misma potencia.»** Hide complexity, never remove capacity.

## Rules for this CR

- Work from `main`, one branch per block, CI green before merging.
- No Room schema or migration change unless strictly necessary; no data removed; offline-first
  repositories and kilo/yield/expense/campaign calculations unchanged.
- No new functions, no reopened phases, no full redesign. The cream + olive identity stays.
- Test tags, accessibility and current screen sizes are kept; every important change is tested.
- Out of scope: community, advertising, EPI, new cooperatives, admin, new APIs, large new
  charts, extra AI, new OCR systems, new sync, social functions.

## Target structure (unchanged bottom bar)

`Inicio · Mi Campo · Cuaderno · Avisos · Perfil`

- **Inicio** — what is happening today (campaign, weather, notices, market); not a second Cuaderno.
- **Mi Campo** — consultation: farms, parcels, campaigns, map, Catastro.
- **Cuaderno** — the one place where everything done is recorded.
- **Avisos** — planning and reminders.
- **Perfil** — personal settings and own resources («Mis máquinas»).

## Closed list of changes

### Block A — navigation and duplicates

1. **One Cuaderno.** «Mi Campo → Finca → Cuaderno» opens the bottom-bar Cuaderno on that Farm;
   the per-Farm Cuaderno (Trabajos · Recolección · Resumen) is removed. Nothing is lost: its
   works are in Diario (plus «Ver todos los trabajos de la finca»), its recolección figures and
   «+ Nueva pesada» in Campaña.
2. **`QuickAddSheet` removed** (no longer in any real flow; its screenshot test now captures the
   Cuaderno).
3. **No «Registrar hoy».** The Cuaderno shows the actions directly; no second menu repeats them.
4. **Context propagated.** From a Farm, a Parcel or Inicio the Cuaderno opens on that Farm (and
   Parcel, shown and removable with «Toda la finca»); Pesada, Jornal and Gasto keep the Farm.
5. **«Abrir jornada de hoy» removed** from the main flow: a Pesada groups into its day
   automatically (CR-010); «Jornal» opens today's recolección day by itself.
6. **«Jornada» → «Día de recolección»** in the interface (internal names unchanged); «Jornal»
   is only for people.
7. **Registrar vs planificar.** Cuaderno says «Registrar trabajo»; Avisos says «Planificar trabajo».

### Block B — actions

Six first-level actions: **Trabajo · Riego · Tratamiento · Pesada · Jornal · Gasto**.
«Documento» lives in its record (ticket → Gasto/Pesada; farm papers → Documentos de la finca).
Machinery use appears inside a work or recolección day («Uso de maquinaria»); Perfil shows
**«Mis máquinas»**.

### Block C — visual cleaning

White/near-white cards on the cream background; semantic colour per section (Trabajo olive,
Riego soft blue, Tratamiento technical green, Pesada ochre, Jornal terracotta, Gasto gold,
Campaña deep olive, Avisos amber); icons on a tinted circle; one primary action per block;
more air, less noise. `MoIcons` reused.

### Block D — texts

Terminology unified (Jornada/Jornal/Cosecha/Recolección, «Registrar o planificar»); «Pronto»,
«Próximamente» and promises of functions that do not exist are hidden or rewritten (Perfil,
onboarding map text).

### Block E — verification

The 16 flows of the plan (Mi Campo → Finca → Cuaderno; each action; Pesada + OCR; automatic
day; second Pesada same day; Jornal; machinery; Gasto manual and ticket; restart; airplane
mode; close campaign; late yield) on the APK of the last block.

## Success criteria

- No usual action has two main paths.
- No screen asks for a datum it already knows from context.
- A new farmer finds where to consult farms, record work, a Pesada, jornales and expenses,
  consult the campaign and plan the future, without external help.

## Delivery

One PR per sub-block (A1 one Cuaderno + actions + context; A2 Jornal/Pesada/Gasto context and
«Abrir jornada» removal; A3 texts; then C, D), each with tests and CI green, reported with
changes, removals, what was kept, tests, CI and screenshots when possible. No new phase is
started after Block E: the owner reviews the result visually first.

## Relation to Gate CR-010

The owner's physical-device run of Gate CR-010 is done on the APK that includes this CR, so the
flows are tested once, on the final screens.
