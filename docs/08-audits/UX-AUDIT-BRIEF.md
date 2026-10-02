# UX audit end-to-end — Mágina Olivo

**Owner request:** 2026-09-24  
**Executor:** Codex  
**Mode:** REVIEW FIRST. No broad redesign.  
**Production rule:** Claude may continue the active phase/fix. Codex must not modify the same
production files while those PRs are active.

## Objective

Review the Android app from first launch to the deepest production flows and make it feel
coherent, understandable and alive without increasing visual noise.

The audit must answer:

- Does the farmer always know **where they are**?
- Does the farmer always know **what can be tapped**?
- Does each action give immediate feedback?
- Are loading, success, warning, validation and error states visually distinct?
- Are transitions calm and intentional rather than abrupt?
- Does Inicio explain the state of the farm/campaign in seconds?
- Do colors help recognition without becoming decorative noise?
- Can the app be understood outdoors and with one hand?
- Are forms short and progressive?
- Is the same visual language used from onboarding to reports?

## Hard constraints

Keep the frozen roots:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

Do not invent a second design language. Follow:

- `docs/design/VISUAL_DESIGN_LOCK.md`
- `docs/design/DESIGN_SYSTEM.md`
- CR-004 + icon-family color addendum
- CR-005 Cuaderno contract

Do not implement Phase 19 production while Gate 18 remains open.

## Audit order

### A — First launch / onboarding

Review all six onboarding pages as one narrative, not six isolated screens.

Check:

- progress is obvious;
- Skip / Next / Begin states;
- photo/text hierarchy;
- page-to-page transition;
- final CTA;
- first transition into Inicio;
- accessibility/reduced-motion path.

Desired feel: calm editorial progression, short fade/slide, no flashy carousel.

### B — Inicio

Current Inicio is functional but must become a real operational landing screen.

Review:

- hero/photo treatment;
- greeting/date/location;
- campaign summary hierarchy;
- stats readability;
- next work;
- quick actions;
- empty farm / no campaign / no upcoming work;
- overdue state;
- future weather/market/cooperative placeholder;
- whether the page feels like one coherent screen rather than stacked components.

Audit micro-interactions:

- press feedback;
- selected/active quick action;
- subtle card elevation/scale on press;
- content appearance when data loads;
- transition after tapping campaign/work;
- success return after a registration;
- error/retry state;
- reduced-motion fallback.

Do not add decorative animation that delays field work.

### C — Global navigation

Review all five root destinations.

The selected destination must be unmistakable through more than one cue:

- icon treatment;
- label;
- container/background/tint;
- optional subtle indicator.

Check:

- root switching;
- repeated tap on current root;
- back behavior;
- deep-screen return;
- contextual Registrar;
- transition consistency between roots and deep screens.

PR #228 is the current navigation-root fix. Do not duplicate or conflict with it.

### D — Mi Olivar end-to-end

Walk:

`Mi Olivar → Finca → Parcelas → Parcela → Campaña → Documentos → back`

Review:

- list/card hierarchy;
- selected/current Farm context;
- photo headers;
- section entry affordance;
- map entry;
- active campaign state;
- empty states;
- archived/history states;
- whether user always knows Farm vs Parcel vs Campaign context.

### E — Registrar / typed work

Walk every available type:

- Observación
- Poda
- Abonado
- Tratamiento
- Suelo
- Riego
- Incidencia/Mantenimiento/Other where present
- Cosecha
- Gasto
- Entrega/Pesada

Check each interaction state:

`idle → pressed → input focus → validation → saving → saved → failure → retry`

Rules:

- no giant forms;
- advanced options behind secondary reveal;
- inherited Farm/Parcel/Campaign context stays visible;
- validation message appears next to the relevant decision;
- saving state cannot look like frozen UI;
- successful save gets a short confirmation and sensible return.

### F — Calendario / planned work

Check:

- today;
- tomorrow;
- overdue;
- completed;
- cancelled;
- reminder present/absent;
- empty calendar;
- opening from notification;
- return to calendar.

Colors may differentiate state, but never be the only cue.

### G — Harvest / Pesadas / yield / expenses / documents

Audit the existing Phase 13–15 surfaces now, and note what CR-005 Phase 19 will replace or
orchestrate later.

Do not prematurely implement the Cuaderno.

Check especially:

- Cosecha vs Entrega/Pesada vocabulary;
- kg truth;
- cooperative visibility;
- ticket/photo visibility;
- yield pending vs added;
- OCR review;
- expense/document attachment;
- machinery access.

### H — Map / Catastro

Once Phase 18 is merged, repeat the review on the canonical main branch.

Check:

- loading provider;
- offline owned geometry;
- selected parcel;
- multiple candidates;
- no result;
- malformed reference;
- duplicate parcel;
- import confirmation;
- managed vs cadastral area;
- map controls;
- transition from map → parcel detail and back.

### I — Global state language

Create a single audit table for all app states:

| State | Visual rule |
| --- | --- |
| Loading local | skeleton/progress only where necessary |
| Saving | visible inline progress; controls protected |
| Saved | short positive confirmation |
| Validation | field/context message; no generic failure |
| Warning | amber family + icon + clear action |
| Error recoverable | message + Retry |
| Offline | informative, non-blocking when local data works |
| Sync pending | quiet status, not alarm state |
| Empty | explanation + one useful next action |
| Disabled | visually clear but still readable |
| Selected | color + shape/indicator + label |
| Destructive | secondary placement + confirmation |

## Motion / interaction principles

Use motion to explain change, not decorate it.

Candidate patterns to evaluate:

- 120–180 ms press/selection feedback;
- 180–260 ms content fade/slide between related states;
- small animated state change for check/success;
- modal/bottom-sheet motion from Material defaults unless there is a real reason to override;
- no looping decorative animation in operational screens;
- respect Android reduced-motion/accessibility preferences.

The audit may recommend exact tokens after checking the existing theme, but must not scatter
hard-coded animation durations through screens.

## Color / orientation principles

The farmer must know what family they are in:

- olivar/work → olive family;
- land/map/machinery → earth family;
- irrigation/calendar/documents → blue-grey family;
- harvest/value/yield/money → soft-gold family;
- incidents/warnings → amber family.

Use these as recognition cues, not as full-screen theme swaps.

For every deep screen, verify that context is visible through at least one of:

- title;
- breadcrumb/context line;
- photo/header;
- status chip;
- section indicator.

## Required output before production changes

Codex must first produce:

`docs/08-audits/UX-AUDIT-END-TO-END-2026-09.md`

with:

1. flow-by-flow findings;
2. severity: Blocker / High / Medium / Polish;
3. screenshot/test evidence when available;
4. exact screen/component affected;
5. proposed change;
6. risk of touching current phase code;
7. whether it should wait for Phase 18/19;
8. proposed PR grouping.

No broad code changes in the audit commit.

## Implementation after audit

After Claude's active fixes/Phase 18 are settled and merged, improvements are implemented as
small PRs, for example:

- `fix/ux-nav-feedback`
- `feat/ux-home-states`
- `feat/ux-form-feedback`
- `feat/ux-motion-tokens`
- `fix/ux-error-empty-states`

Each PR:

- starts from latest main;
- changes one coherent interaction family;
- adds tests where feasible;
- shows before/after evidence;
- does not mix Phase 19 business logic;
- stops if it collides with an active Claude branch.

## Definition of success

A farmer can go from first launch to recording and reviewing work without needing to infer:

- where they are;
- whether a control is interactive;
- whether an action succeeded;
- why an action failed;
- whether data is local/offline/pending;
- what screen they will return to.

The result should feel more polished and responsive, **not busier**.
