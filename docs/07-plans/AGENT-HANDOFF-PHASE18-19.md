# Agent handoff — Phase 18 → Phase 19

**Status:** ACTIVE COORDINATION  
**Owner decision:** 2026-09-24  
**Rule:** one production slice at a time. Codex and Claude may work in parallel only when one
line is production and the other is review/documentation/preparation.

## Canonical source order

Before any implementation:

1. `AGENTS.md`
2. `docs/00-master/CURRENT-STATE.md`
3. `docs/07-plans/ROADMAP-RC1.2.md`
4. current phase execution plan
5. `docs/00-master/RC1.2-CHANGE-REQUEST-005-CUADERNO-CAMPANA.md`
6. `docs/04-ui/CUADERNO-CAMPANA-SCREEN-SPEC-RC1.2.md`
7. `docs/07-plans/PHASE19-CAMPAIGN-NOTEBOOK-ANALYTICS.md` when Phase 19 opens

Older prompts or chat instructions never override these files.

> **Update 2026-09-24:** Gate 18 PASS (owner). Phase 18 was finished by Claude at the
> owner's request (Codex out of tokens) on `codex/phase18-map-v2`, merged as PR #227.
> **Phase 19 is open; slice 19A (Claude executes, Codex reviews) starts now.**
>
> **Update 2026-09-24 (later):** 19A merged to `main` as PR #233 (owner: "fusiona y continúa";
> CI green: unit, E2E through the Cuaderno, Gate 3 evidence). Codex may still post review
> comments on #233; fixes go to a `claude/phase19a-*` follow-up branch.
> **Next slice: 19B — Jornada + multiple Pesadas (executor Codex, reviewer Claude)** on
> `codex/phase19b-jornada-pesadas` from the latest `main`.
>
> **Update 2026-09-24 (owner):** roles for 19B swapped — "Claude sigue programando, Codex
> está probando la app en el emulador y revisando". **19B executor Claude, reviewer and
> emulator tester Codex**, branch `claude/phase19b-jornada-pesadas`. Codex must not open a
> competing 19B implementation; findings go to the 19B PR as review comments.
>
> **Update 2026-09-24 (later):** 19B merged (PR #235). **19C — Rendimientos pendientes**
> (executor Claude, reviewer Codex) on `claude/phase19c-rendimientos`.
>
> **Update 2026-09-24 (evening):** 19C merged (PR #236). The owner keeps Claude programming
> ("continuamos") while Codex tests on the emulator; Codex's GitHub review quota is exhausted.
> **19D — Jornales: executor Claude** (table said Codex) on `claude/phase19d-jornales`; Codex
> reviews/tests when available.
>
> **Update 2026-09-24 (night):** 19D merged (PR #237). **19E — Equipment usage** (executor
> Claude, as in the table) on `claude/phase19e-equipment`.
>
> **Update 2026-09-24 (night, later):** 19E merged (PR #238). **19F — Recollection
> expenses/documents: executor Claude** (table said Codex; same owner decision as 19D) on
> `claude/phase19f-gastos`.
>
> **Update 2026-09-24 (night, last):** 19F merged (PR #239). **19G — Visual historical
> analytics** (executor Claude, as in the table; reviewer Codex) on `claude/phase19g-analytics`.
> After 19G merges, Phase 19 gate closure needs the owner/Codex device + airplane-mode evidence
> still pending since 19B.
>
> **Update 2026-09-24 (close):** 19G merged (PR #240). All Phase 19 slices are on `main`.
> Gate 19 is PENDING on device evidence — see `docs/06-testing/PHASE19-GATE-CHECKLIST.md`.
> No production work on a later phase until the owner records Gate 19 PASS.

## Production owner during Phase 18 (closed) — Codex, finished by Claude

Phase 18 is the only allowed production phase until Gate 18 passes.

### Important stale branch warning

`codex/phase18-parcel-map` is a **reference branch only**. It diverged from the current
`main` and is many commits behind. Do not merge it wholesale and do not continue production
directly on it.

Use a fresh branch from the latest `main`:

`codex/phase18-map-v2`

Codex may inspect/cherry-pick/reimplement useful pieces from the stale branch, but every change
must be reconciled against the current Room schema, CR-004 UI, Catastro Phase 17 work and the
latest tests.

### Codex Phase 18 scope

Only:

- normalized parcel geometry;
- Farm/Parcel map;
- imported geometry stored locally;
- offline display after restart;
- managed vs cadastral area truth;
- migrations/tests needed by that scope;
- Gate 18 evidence.

No Cuaderno/Jornada/Jornales/Phase 19 production code.

### Codex stop condition

Open/update a Draft PR, run all applicable checks, record Gate 18 evidence and stop. Do not
start Phase 19 from the same branch.

## Claude while Phase 18 is active

Claude is the **review/preparation line**, not a second production implementer.

Allowed:

- review Codex Phase 18 diff and identify regressions;
- inspect migration/test coverage;
- prepare Phase 19 test matrices, data-contract notes and UX acceptance cases;
- improve documentation on an isolated `claude/phase19-cuaderno-prep` branch if needed.

Not allowed before Gate 18 PASS:

- Room migrations for Phase 19;
- Cuaderno production screens;
- Jornada/Delivery linking code;
- labour/equipment production entities;
- production analytics.

## Phase 19 ownership after Gate 18

When Gate 18 is explicitly PASS and merged to `main`, Phase 19 opens.

Production remains sequential. Executor/reviewer alternate so both agents contribute without
editing the same slice in parallel:

| Slice | Executor | Reviewer | Suggested branch |
| --- | --- | --- | --- |
| 19A — Cuaderno projection/navigation | Claude | Codex | `claude/phase19a-cuaderno-projection` |
| 19B — Jornada + multiple Pesadas | Claude (owner swap) | Codex (review + emulator) | `claude/phase19b-jornada-pesadas` |
| 19C — Rendimientos pendientes | Claude | Codex | `claude/phase19c-rendimientos` |
| 19D — Jornales | Codex | Claude | `codex/phase19d-jornales` |
| 19E — Equipment quantities | Claude | Codex | `claude/phase19e-equipment` |
| 19F — Recollection expenses/docs | Codex | Claude | `codex/phase19f-gastos` |
| 19G — Historical analytics | Claude | Codex | `claude/phase19g-analytics` |

A reviewer may comment, propose a patch or prepare tests, but must not create a competing
implementation branch for the same slice.

## Phase 19 invariants

Every agent must preserve:

- five frozen roots: `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`;
- Farm hub: `Parcelas · Cuaderno · Campañas · Documentos`;
- Cuaderno: `Trabajos del año · Recolección · Resumen`;
- user-facing **Pesada** = canonical Delivery;
- cooperative/mill chosen per Pesada;
- later Yield stored separately from Delivery;
- weighted yield by analysed kg + visible coverage;
- no fabricated Parcel kg/yield from mixed origin;
- no duplicate editable kg total between Jornada and linked Pesadas;
- Expense remains the sole authoritative money ledger;
- no giant form;
- offline-first + restart persistence;
- legacy history remains readable.

## Slice workflow

For every production slice:

1. start from latest `main`;
2. read the canonical docs above;
3. implement only the named slice;
4. add/update unit + instrumentation + migration tests as applicable;
5. run `git diff --check` and applicable Android checks;
6. open a Draft PR;
7. reviewer checks truth rules, migration safety, UI scope and test evidence;
8. fix until Gate for the slice is satisfied;
9. merge only after explicit acceptance;
10. next slice starts from the new `main`.

## Required handoff report

Every Codex/Claude handoff must state:

- phase/slice;
- branch and PR;
- base `main` SHA;
- final HEAD SHA;
- files changed;
- migrations added/changed;
- tests and exact results;
- emulator/physical evidence status;
- blockers;
- whether any later-phase scope was added (**must be no**);
- explicit next allowed action.

## Ready-to-use prompt — Codex now

> Read AGENTS.md, CURRENT-STATE, ROADMAP-RC1.2 and PHASE18-IMPLEMENTATION. Work only on
> Phase 18. Do not continue or merge `codex/phase18-parcel-map` directly: it is stale and
> diverged. Start `codex/phase18-map-v2` from latest main, inspect the old branch only as a
> reference, and reconcile useful changes against the current schema/UI. Deliver normalized
> parcel geometry + Farm/Parcel map + offline restart persistence + managed/cadastral area
> truth. Add migration and instrumentation coverage, open a Draft PR, record Gate 18
> evidence, then stop. Do not implement CR-005/Phase 19.

## Ready-to-use prompt — Claude now

> Read AGENTS.md, CURRENT-STATE, ROADMAP-RC1.2, CR-005 Cuaderno, the Cuaderno screen spec and
> the Phase 19 execution plan. Phase 18 is still the only production phase, so do not write
> Phase 19 production code. Review the active Codex Phase 18 implementation for migration,
> truth, offline/restart and regression risks. In parallel, you may prepare Phase 19 test
> matrices/data-contract notes on `claude/phase19-cuaderno-prep`. Do not change Room or
> production UI for Phase 19 until Gate 18 is explicitly PASS and merged.
