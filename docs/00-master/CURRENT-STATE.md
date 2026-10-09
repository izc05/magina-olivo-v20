# Olive Farm App — Current Work State

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Last reviewed:** 2026-10-09

This file is the quick continuity marker for a new ChatGPT/Codex/Antigravity session. It does not replace the baseline/spec; it tells the worker where to resume.

## Current owner execution queue — #711 / #696

The owner's 9 October continuation in [#711](https://github.com/izc05/magina-olivo-v20/issues/711)
and priorities in [#696](https://github.com/izc05/magina-olivo-v20/issues/696) supersede the older
resume instructions below. Android only; Web V3 paused. One productive PR at a time, independent
review and foundation / gate3-emulator / gate3-evidence SUCCESS on its final SHA before merge.

- B0 expense context: #712 merged as `45e21fe05624ba68a6020c43258ef75e1351c700`; reviewed HEAD
  `92fff335241376ecca13dd4bcab6bcfa1a3034d6`, all three checks SUCCESS. #684/#692/#709/#710 also merged.
- B1 agricultural evidence: 284 current offline instrumented tests PASS, retained C1/C2 records,
  costs/payments, snapshots and photo verified. Upgrade DEV1606→1609 kept identical backups.
  See [B0/B1 evidence](../qa/ANDROID-711-B1-AGRICULTURAL-EVIDENCE-2026-10-09.md).
- B2 contrast #706: #713 merged as `324af37a8ff83efdb12c71482fa5b9c1caba432d`; reviewed HEAD
  `330e21f943773aac772e1c01e0a523d212a805b8`, all three checks SUCCESS, 526 JVM / 89 directed UI
  and 63 evidence PNG. See [contrast evidence](../qa/ANDROID-706-SURFACE-CONTRAST-2026-10-09.md).
- B2 navigation #705: #716 merged as `db072b672e40c3bc60e0a7b2fe2f5a6532de58c7`; reviewed HEAD
  `783184558b5d0d1e16a8e19543e795a7daced235`, all three checks SUCCESS, main CI37926969086 SUCCESS.
  Local526JVM/62UI/two repeated offline cases; CI704UI/196offline/4x14captures;36localPNG.
  See [navigation evidence](../qa/ANDROID-705-CR014-NAVIGATION-2026-10-09.md).
- Current productive slice: #707 DARK-1, branch `codex/707-dark1-theme` from integrated main.
  Device appearance preference and semantic tokens first; DARK-2 components then DARK-3 full
  screens/Perfil selector in separate sequential PRs. Maps/GPS B3, weather B4, candidate B5 follow.
- Expense classification follows the later #411 owner decision: generic Cuaderno asks explicitly;
  creation from a concrete Campaign keeps its Farm/Campaign context. The 3 October preselection
  description below is historical and superseded. #522 relation cleanup in general editors remains.
- Gate21 physical acceptance remains OPEN. Final candidate must come from integrated main with
  green CI. No physical PASS, Gate22+, Auth/Sync/PDF expansion or Web work is authorized by CI alone.

## Passed Gates

```text
✅ 0.1 Master product definition
✅ 0.2 Scalable Mi Campo architecture
✅ 0.3 Data Model RC1 + Future
✅ 0.4 Catastro Contract
✅ 0.5 Offline/Sync Contract
✅ 0.6 Complete Screen Map
✅ 0.7 Design System Spec
✅ 0.8 RC1.1 Product reconciliation/spec lock
✅ 0.9 RC1.2 geographic-neutral scope + generic OCR
✅ Gate 1 — Android Project Foundation
✅ Gate 2 — Base application architecture
✅ CR-003 — Mágina Olivo brand + canonical visual system approved
✅ Gate 3 — Visual / accessibility / emulator validation
✅ Gate 4 — Production navigation shell
✅ Gate 5 — Local database foundation
✅ Gate 6 — Farms + Parcels + Campaigns (composite)
✅ Gate 9 — Activity engine
✅ Gate 10 — Typed activities + irrigation
✅ Gate 11 — Attachments
✅ Gate 12 — Expenses, purchases, organizations + generic OCR
✅ Gate 13 — Harvest
✅ Gate 14 — Deliveries + weight-ticket OCR + later yield
✅ Gate 15 — Machinery
✅ Gate 16 — Calendar, agenda, reminders (physical check confirmed by the owner 2026-09-23)
✅ CR-005 — Cuaderno de campaña / recolección IA approved
✅ Gate 17 — Spain Catastro lookup + confirmed import (owner device PASS 2026-09-24: "17 y 18 OK")
✅ Gate 18 — Land registry geometries + offline map (owner device PASS 2026-09-24 on the main APK
   of 3516ad45; CI: live import → airplane mode → offline render, live polygon/parcel lookup)
```

## Owner-priority product correction — CR-010

**APPROVED 2026-09-28.** The next product update after the currently in-flight Gate 20 validation is
**CR-010 — Campaña simple, automatizada y centrada en Pesadas**. It takes priority over starting
Phase 21 or unrelated feature expansion.

Canonical flow:

`Activar Campaña → Nueva Pesada → día/Jornada automático → jornales + maquinaria + gastos → rendimiento posterior`.

Issue #254 is the implementation tracker. CR-010 explicitly brings ticket OCR into the compact
Pesada form, removes manual Jornada selection from the normal flow, adds optional labour/machinery
pricing without duplicate money, strengthens Campaign analytics and authorizes the targeted
Campaign/Recolección visual refinement.

Do not mix the deferred EPI-signature workflow or planning/reminder form redesign into CR-010.

**Amendment 1 (approved 2026-09-28)** fixes the implementation contract: automatic days never keep
kilos without Pesadas (A1), legacy hand-typed kg shown apart, never dropped or double counted (A2),
calculated labour/machinery cost posted once through the Expense ledger (A3), Room v17 (slice 2)
and v18 (slice 4) for CR-010 and v19 for Phase 21A (A4, revised by the owner 2026-09-28), six delivery slices and **Gate CR-010** (§17 on a physical device +
A1–A3 tests). Gate 20 PASSED 2026-09-28; all six slices merged 2026-09-28; Gate CR-010 open.

## Current allowed phase

**Update 2026-10-03 (owner):**

- **CR-012 Slice 4 MERGED** (#341, merge `43a2d28d`): explicit economic context, recolección
  cost cards, cost/kg from POSTED Expense ÷ canonical Pesadas. Codex handed execution to Claude
  (no tokens); Claude fixed the review P1 (OCR review keeps Farm/Campaign) and the owner decision
  «Cuaderno → Gasto preselects "Gasto de recogida · Campaña …", changeable to "Gasto general de
  finca/parcela"». CR-012 is technically complete; its owner-device acceptance travels with the
  candidate APK.
- **CR-013 APPROVED and IMPLEMENTED** (#342/#339 → PR #343, merge `f92a626f`):
  `RC1.2-CHANGE-REQUEST-013-PESADA-MANUAL-OCR-APLAZADO.md`. A Pesada is the kilos the farmer
  types; the receipt photo/file is an optional attachment added after saving and never changes
  kilos, campaign, farm or costs. «Leer vale», «Añadir vale y leer datos» and «Ticket o factura»
  left the normal path. OCR code and stored documents stay dormant. OCR is not a requirement for
  1.0, Gate 21, the APK, Backend/Auth, Sync, PDF, Web or release.
- **#345 MERGED** (PR #348, merge `908b5023`): rain probability on Inicio (null never 0 %), weather
  fresh for 1 h with refresh on entering/resuming Inicio, «Actualizar», water-blue rain radar.
- **Gate 21 checkpoint prepared:** `docs/06-testing/PHASE21-GATE-CHECKLIST.md`; candidate DEV APK =
  Android CI run #761 on `main` `908b5023`. Waiting for the owner's device run.
- **Owner-approved polish before Cuenta/Sync (2026-10-03, #351):** after testing APK 761 the owner
  authorized three small changes, outside Phase 22 and with no schema/Auth/Sync change: (3) municipality
  and province from Catastro when a parcel is added or edited with its reference (functional),
  (1) Cuaderno header hierarchy, (2) parcel detail in blocks. One PR each (#352, #353, #354); Gate 21
  stays open until the owner's device run.
- **APK 761 correction tracker (2026-10-03, #356):** the owner listed the remaining device findings
  in #350 and #357–#366 (plus #355/#359 as 1.0 improvements) and asked for no new candidate APK until
  they are all done. They run as small PRs from `main`, one block each, before Phase 22 and with no
  schema/Auth/Sync change; Gate 21 stays open until the owner's device run of the resulting APK.
- **Web:** WEB-0 (#346) may advance only in design, storyboard, public structure, staging and visual
  preparation. It must not touch Auth, schema, RLS or Sync before Phases 22/23.
- **Perfil** is the single source of municipality, cooperative and territorial preferences. Weather,
  town council/news, cooperative and local businesses consume it and never duplicate the choice.

**Owner priority 2026-10-01 — CR-012 (Issue #309), executor Codex:** complete the
recollection economic flow in sequence: Slice 1 contracts → Slice 2 identified
labour/historical rates/payment movements → Slice 3 machinery/use costs → Slice 4
day/campaign cards and cost/kg. Owner authorized continuous execution and PR
integration after review and validation. Expense POSTED remains the sole campaign
cost source; payments settle debt and never create costs. Plan:
`docs/07-plans/CR012-EXECUTION-PLAN.md`. No unrelated backend/product expansion.
CR-012 Slices 1 (#310), 2 (#313), and 3 (#314) are merged. Slice 3 exact head
`c292d154` passed all six CI checks; merge `5fd86744`. Current production
delivery: Slice 4 surfaces, explicit economic context, and cost/kg.
Evidence: `docs/06-testing/CR012-SLICE2.md` and `docs/06-testing/CR012-SLICE3.md`.
Post-edit modal physical Save reachability is carried to Slice 4 accessibility validation.
The pending physical-device Gate 21 remains pending; this priority does not claim
it passed. Record per-slice PRs and evidence before declaring CR-012 complete.

```text
▶ PHASE 21 — PROFILE: ALLOWED (2026-09-29), executor Claude. Owner «Todo ok» on the build with
  main 841e9804 closes CR-011 and Gate CR-010 (checklists updated); OCR on an authentic ticket /
  invoice is carried to the Phase 27 beta. Plan: docs/07-plans/PHASE21-PROFILE.md (P1 Room v19,
  P2 reminders 1 day before at 08:00, P3 export waits for Phase 25). Slices 21A → 21B → 21C.
  Room v19 holds only 21A `profile_settings`; the Campaign activation date stays a proposal.
  21A MERGED (#307, Room v19): Perfil «Tu municipio» + «Tu cooperativa» (live Organization
  reference); Inicio/weather week fall back to the profile municipality.
  21B MERGED (#308, Room v20): Perfil → Avisos — master switch + day-before hour (08:00 default,
  P2); existing day-before reminders follow the chosen hour; switched off = no alarm, nothing deleted.
  21C MERGED (#311): Ayuda y privacidad — «Qué hay de nuevo» (CHANGELOG bundled per variant),
  «Privacidad y datos» (names every outside provider: AEMET/MET Norway, RainViewer, IGN, Catastro,
  Junta de Andalucía, AOVE.net), «Usar la app sin cobertura». Export waits for Phase 25 (P3).
  ▶ GATE 21 OPEN: owner device check of Perfil (municipio, cooperativa, avisos on/off + hour,
    ayuda). Next phase after Gate 21: 22 — Supabase backend contract (needs owner project access).
✔ CR-011 — SIMPLIFICACIÓN Y PULIDO UX: CLOSED 2026-09-29 (owner device «Todo ok»; build-683
  fixes PR #305: Jornal stuck on «cargando» root cause, Cuaderno at 360 dp / large text).
✔ GATE CR-010: PASS 2026-09-29 (same device run).
▷ History — CR-011 (owner-approved 2026-09-29), executor Claude.
  docs/00-master/RC1.2-CHANGE-REQUEST-011-SIMPLIFICACION-UX.md. No new functions, no schema
  change. Blocks A (navigation/duplicates) → B (actions) → C (visual) → D (texts) → E (flows).
  MERGED: A1 #298 (one Cuaderno, six direct actions, no «Registrar hoy», QuickAddSheet removed,
  Farm/Parcel context, Inicio campaign → Cuaderno · Campaña); A2/A3/D #299 (no «Abrir jornada
  de hoy», «Día de recolección», «Mis máquinas»/«Uso de maquinaria», Cuaderno «Registrar
  trabajo» vs Avisos «Planificar trabajo», Perfil without «Pronto», onboarding map text).
  MERGED: C #300 (near-white cards, semantic action colours set per action, one primary per block).
  E: docs/06-testing/CR011-FLOWS-CHECKLIST.md (16 flows → automated evidence + device run).
  Duplicates removed: second Cuaderno per Farm, «Registrar hoy» sheet, QuickAddSheet,
  «Documento» as a first-level action, «Abrir jornada de hoy», «Registrar o planificar».
  The Gate CR-010 device run happens on the APK that includes CR-011.
▶ CR-010 — CAMPAÑA SIMPLE (Issue #254), executor Claude, after Gate 20 PASS.
  All six slices MERGED (2026-09-28): 1a #288 · 1b #289 · 2 #290 (Room v17) · 3 #291 ·
  4 #293 (Room v18) · 5 #294 · 6 #295. A3 follow-up (unlinked same-date costs) #296 MERGED.
  ▶ GATE CR-010 OPEN = §17 on a physical device + A1–A3 tests green in CI.
    Checklist: docs/06-testing/CR010-GATE-CHECKLIST.md. Phase 21 starts only after this Gate.
  Proposed for Phase 21A (Room v19, owner to confirm): store the Campaign activation date so
  «Días de campaña» can count from activation (today it counts from the start date and says so).
✔ GATE 20 PASS — owner decision 2026-09-28: APK 0.5.0 build 606 tested on the emulator, no
  defects; the owner chose to close Gate 20 on it. Physical-phone re-check in the Phase 27 beta.
  Checklist: docs/06-testing/PHASE20-GATE-CHECKLIST.md.
  UX-246 device checks D1–D12 remain open in docs/06-testing/UX-246-GATE-CHECKLIST.md.
  UX owner-feedback review (Codex, branch codex/android-ux-review-0.3.0 @71a5e7f, reviewed and
  merged by Claude): Mi Campo empty state, farm/parcel forms folded, single "Añadir" for parcels,
  "Registrar hoy" type-first + Completed. Report: docs/06-testing/UX-OWNER-FEEDBACK-2026-09-27.md.
  GLOBAL UX AUDIT NOT COMPLETE: Inicio next (hierarchy/density with the owner; "campaña en
  Preparación" wording), then Producción/Gastos, Perfil/Ajustes, Avisos, Maquinaria, Mapa/Catastro,
  offline and every activity type — on the emulator's existing data set (no data wipe).
  App version on main: 0.5.0 (docs/CHANGELOG-APP.md; Perfil shows "Versión · compilación").

  Issue #246 / CR-007 UX reorganisation: UX-A…UX-F MERGED (#248–#252); UX-G QA done in CI.
  Closes with the owner's device checks D1–D12 (docs/06-testing/UX-246-GATE-CHECKLIST.md).
  Follow-ups merged 2026-09-27: Cuaderno day-by-day chronology + visual hierarchy (#265), short
  work form (#266), visible version/build (#267).

  Issue #254 historical implementation (#261/#262/#263/#264/#268/#269) remains merged and is the
  compatibility base, but **#254 is RE-SCOPED and ACTIVE again under CR-010 (2026-09-28)**.
  The previous manual Jornada-oriented UX is not the final target. CR-010 now requires automatic
  day/Jornada grouping, OCR integrated in Nueva Pesada, optional labour/machinery pricing,
  stronger Campaign analytics and the targeted visual correction. Do not mark #254 complete until
  the CR-010 physical-device E2E passes.

✔ PHASE 20 — HOME CONTEXTUAL SERVICES + WEATHER VISUALS: slices 20A–20D merged; Gate 20 PASS 2026-09-28 (owner, emulator).
  20A (#243), 20B + deploy (#247), 20B-fix (#257), 20B-radar (#259), 20C (#260): MERGED.
  20D oil market: SOURCE APPROVED 2026-09-27 (Junta de Andalucía Observatorio, weekly prices at
  almazara/bodega; MAPA and EU DG AGRI as comparison sources; POOLred not approved) — next slice,
  executor Claude, own PR after documenting access/reuse + fixtures. Free MVP (Issue #271):
  AOVE.net widget as "Pulso diario" (publisher-hosted, nothing copied) + Junta weekly trend.
  20D-1 MERGED (PR #273, 0.4.0): domain trend, normalized JSON parser, cached feed, Home card;
  the app switch OIL_MARKET_FUNCTION_DEPLOYED is off, so the official trend says "Sin fuente
  configurada" until the function is live.
  20D-2 MERGED (PR #274, #276, #278): `oil-market` Edge Function (Junta adapter; the live
  header "Semana 38: (14/9/26 - 20/9/26)" fixed in #278). DEPLOYED 2026-09-28, deploy run #3:
  HTTP 200 from eu-west-3, 401 without key.
  20D-3 MERGED (PR #280, 0.5.0): official source on + "Mercado del aceite" screen, 12-week chart.
  20E cooperative notices:
  DEFERRED (D4). Checklist: docs/06-testing/PHASE20-GATE-CHECKLIST.md (G1–G7).
  Still owed by the owner: deploy run #6 summary and the AEMET error line.

  Weather week (7 days) + simplified Home: inside Gate 20 via CR-009 / PR #284 (Codex, draft;
  owner-confirmed scope). Gate 20 re-validates with B4/D5 on the APK that includes it and after
  an authorized redeploy of `weather-forecast`.

⏭ PHASE 21 — PROFILE: PREPARED, not started (docs/07-plans/PHASE21-PROFILE.md; 21A locality +
  preferred cooperative, 21B preferences, 21C help/privacy). Production starts after Gate 20 PASS.
  «Seguir mi ubicación» is proposed for 21A by CR-008 (PROPOSED, not approved).
```

✔ PHASE 19 — CUADERNO DE CAMPAÑA + HISTORICAL ANALYTICS: CLOSED (Gate 19 PASS 2026-09-25)
  19A — Cuaderno projection/navigation: MERGED (PR #233, 2026-09-24).
  19B — Jornada + multiple Pesadas: MERGED (PR #235, 2026-09-24, owner "puedes seguir"; CI green:
  unit, 201 instrumented tests, Gate 3 evidence; Room v13). Emulator/airplane-mode check by Codex
  pending — Codex review quota was exhausted on #235.
  19C — Rendimientos pendientes: MERGED (PR #236, 2026-09-24, owner "continuamos"; CI green).
  19D — Jornales: MERGED (PR #237, 2026-09-24, owner "puedes continuar"; CI green; Room v14).
  19E — Equipment usage: MERGED (PR #238, 2026-09-24, owner "continúa"; CI green; Room v15).
  19F — Recollection expenses/documents: MERGED (PR #239, 2026-09-24, owner "ya puedes fusionar";
  CI green; no schema change — reuses expenses.harvest_id).
  19G — Visual historical analytics: MERGED (PR #240, 2026-09-24, owner "si ok"; CI green;
  no schema change — pure projection of Room rows).
  GATE 19: PASS (2026-09-25, owner "TODO OK" after device checks; device model/Android
  version not reported) — docs/06-testing/PHASE19-GATE-CHECKLIST.md.
  per docs/07-plans/AGENT-HANDOFF-PHASE18-19.md and docs/07-plans/PHASE19-CAMPAIGN-NOTEBOOK-ANALYTICS.md.
  Device model/Android version of the Gate 17/18 check: not reported by the owner.
```

Gate 5 is recorded as PASS in `docs/06-testing/PHASE5-GATE-CHECKLIST.md`. Gate 6 is closed as a composite PASS across its Farm, Parcel and Campaign slices. Phase 9 is closed as PASS and merged into `main`. Phase 10 builds the typed agronomic details on the Activity aggregate Phase 9 delivered.

## Mandatory reading order for any agent

1. `docs/00-master/RC1-BASELINE.md`
2. `docs/00-master/RC1.2-PRODUCT-LOCK.md`
3. `docs/00-master/SINGLE-TRACK-EXECUTION.md`
4. `docs/00-master/RC1.2-CHANGE-REQUEST.md`
5. `docs/00-master/RC1.2-CHANGE-REQUEST-003-BRAND-VISUAL.md`
6. `docs/00-master/RC1.2-CHANGE-REQUEST-004-DESIGN-V3.md`
7. `docs/00-master/RC1.2-CHANGE-REQUEST-005-CUADERNO-CAMPANA.md`
8. `docs/00-master/RC1.2-CHANGE-REQUEST-006-WEATHER-EDGE-FUNCTIONS.md`
9. `docs/00-master/RC1.2-CHANGE-REQUEST-007-CUADERNO-NAVIGATION.md`
10. `docs/00-master/RC1.2-CHANGE-REQUEST-008-WEATHER-WEEK-LOCATION.md`
11. `docs/00-master/RC1.2-CHANGE-REQUEST-009-HOME-WEATHER-MARKET.md`
12. `docs/00-master/RC1.2-CHANGE-REQUEST-010-CAMPANA-SIMPLE-AUTOMATIZADA.md`
13. `docs/04-ui/CUADERNO-CAMPANA-SCREEN-SPEC-RC1.2.md`
10. `docs/design/VISUAL_DESIGN_LOCK.md`
11. `docs/design/DESIGN_SYSTEM.md`
12. `docs/00-master/RC1.1-PRODUCT-LOCK.md`
13. `docs/00-master/RC1.1-CHANGE-REQUEST.md`
14. `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`
15. `docs/00-master/MASTER-SPEC-RC1.md`
16. `docs/00-master/RC1-GATE-REVIEW.md`
17. `docs/07-plans/ROADMAP-RC1.2.md`
18. `docs/07-plans/PHASE3-DESIGN-REFERENCE.md`

Then read only the domain/architecture/UI contracts needed by the current phase.

## Approved preparation after current Gate

CR-005 is approved as the next product-workflow refinement. It reorganizes the Farm experience around a **Cuaderno** with two visual areas — **Trabajos del año** and **Recolección** — while keeping the frozen root navigation and canonical domain boundaries. User-facing **Pesada** maps to Delivery; every Pesada owns its cooperative/mill selection and later yield remains a separate analysis. Individual harvest labour, equipment quantities and harvest-expense links are specified for later implementation.

**Do not implement CR-005 production code while Phase 18 is active.** Documentation/specification work is allowed; the first production slice starts with Phase 19 after Gate 18 closes.

### Codex / Claude coordination

Canonical handoff: `docs/07-plans/AGENT-HANDOFF-PHASE18-19.md`.

- **Codex** owns Phase 18 production from a fresh branch based on latest `main`.
- The old `codex/phase18-parcel-map` branch is stale/diverged and is reference-only.
- **Claude** is review + Phase 19 preparation until Gate 18 passes.
- After Gate 18 merges, Phase 19 executes sequentially as 19A–19G with alternating executor/reviewer roles.
- No two agents may implement the same production slice in parallel.

## Hard stop rule

If implementation requires changing an immutable baseline decision, stop feature work and open a Change Request. Do not silently adapt architecture because a library, agent or generated template prefers another approach.

RC1.2 Product Lock is normative and overrides contradictory RC1-era wording until all older documents are editorially reconciled.

## RC1.2 reconciliation

CR-003 resolves the display brand as **Mágina Olivo** and freezes the visual references under `docs/design/`. Geographic-neutral domain/data architecture remains unchanged.

Gate 2 passed on 2026-09-18. Phase 3 implementation is merged into `main`: canonical Compose tokens/components, six-screen onboarding and all required reference screens are implemented.

Gate 3 passed on integration commit `6f37b736` on 2026-09-19. Lint, 12 unit tests, 15 Android instrumentation tests, all debug environment builds, four rendering configurations, accessibility semantics, cold starts and crash-buffer checks passed. The installable DEV APK and emulator evidence are attached to GitHub Actions runs `35435717081` and `35435717079`.

PR #197 integrates and supersedes the Android validation intent of draft PRs #195 and #196 without closing or deleting their historical record. `main` remains unchanged pending owner authorization.

Gate 4 passed on code commit `483fc145` on 2026-09-19. The app now has one production `NavHost`, the five frozen roots, deterministic back behavior, contextual Register entry, persisted onboarding completion and DEV-only catalogue access. CI passed 17 unit tests and 24 Android instrumentation tests; emulator evidence and the installable DEV APK are attached to runs `35449235415` and `35449237218`.

Validation also closed a CI false positive: the evidence script now rejects JUnit `FAILURES!!!` even when `adb am instrument` returns zero. PR #198 contains the Phase 4 stack and remains separate from `main`.

Gate 5 passed on code commit `ad61d6f4` on 2026-09-19. Room is now the wired local persistence foundation with committed v1/v2 schemas, an explicit migration, 13 core tables, client UUIDs, soft-delete/version/sync metadata, repository/DAO boundaries and transactional Farm + outbox proof. A deterministic fixture exists only in the DEV flavor.

CI passed 22 unit tests and 28 Android instrumentation tests. The three repository tests were also executed separately with Android airplane mode enabled and Wi-Fi disabled; both the primary and independent API 35 emulator runs passed with empty crash buffers. Evidence and the installable DEV APK are attached to runs `35465669062` and `35465670678`. PR #199 contains the stacked Phase 5 implementation; `main` remains unchanged.

The Gate 6 Farm slice passed on code commit `a2d2d475` on 2026-09-20. Production Mi Olivar now uses Room-backed Farm list/detail routes with create, edit, archive, restore, truthful derived summaries and durable cover-photo metadata. Every mutation is local-first and queues its synchronization intent. CI passed 27 unit tests, 37 API 35 instrumentation tests and 6 repository tests under airplane mode; the crash buffer was empty. Evidence and the verified DEV APK are attached to run `35480574641`. PR #200 contains this stacked slice.

The Gate 6 Parcel slice passed on code commit `ee89b9f7` on 2026-09-20. Production Farm detail now lists persisted Parcels and supports manual create, detail, edit, archive and restore. Parcel identity is app-owned, Farm membership history is non-destructive, optional GeoJSON geometry is retained, and manual data is never presented as Catastro-verified. Mutations are local-first and enqueue deterministic outbox intents. CI passed 30 unit tests, 42 API 35 instrumentation tests and 7 repository tests under airplane mode; the crash buffer was empty. Evidence and the verified DEV APK are attached to run `35506482946`. PR #201 contains this stacked slice.

The Gate 6 Campaign slice passed on code commit `164aaa48` on 2026-09-22. Production Farm detail now owns the full Campaign lifecycle: create, edit while in preparation, activate, move to harvest, close, explicit audited reopen and archive of a draft. Room schema v3 and `MIGRATION_2_3` back a Campaign aggregate whose `campaign_parcels` children are materialised atomically at activation, freezing Farm name, Parcel name, managed area, cadastral reference and geometry so closed history survives any later Farm or Parcel rename. Mutations are local-first and collapse into a single deterministic Campaign outbox intent.

The canonical lifecycle is strictly linear `PREPARATION → ACTIVE → HARVEST → CLOSED`; `ACTIVE → CLOSED` is an illegal transition and `CLOSED → HARVEST` is the only backwards edge. At most one ACTIVE or HARVEST Campaign may exist per Farm. `ACTIVE`, `HARVEST` and `CLOSED` are protected from normal deletion; an archived `PREPARATION` draft is soft-deleted, cannot be mutated or resurrected, and a repeated archive is idempotent. RC1 ships no Campaign restore, and closing uses the device date with no date picker, the repository rejecting an end date before the start date.

Review added `CampaignLifecycleContractTest` (13 instrumented contract tests) and hardened the combined E2E. It also found and fixed one real defect: soft-deleted Campaigns stayed mutable, so an archived draft could be resurrected and could take the Farm's single current-Campaign slot; `mutate` now rejects archived aggregates with `archived_campaign`, matching the Farm repository pattern.

Both emulator workflows passed on `164aaa48`: Android CI #315 (run `35686302694`) with `foundation` SUCCESS and `gate3-emulator` SUCCESS, and the independent Gate 3 Android Emulator Evidence #36 (run `35686302700`) SUCCESS. The full instrumented suite passed 61/61, including 13/13 Campaign contract tests, and the Farm and Campaign repository tests passed 9/9 with real airplane mode enabled via `adb shell cmd connectivity airplane-mode enable` and verified through `settings get global airplane_mode_on = 1`. The crash buffer was 0 bytes. The verified DEV APK is artifact `magina-olivo-dev-debug` (`10676852700`, 13,146,341 bytes, sha256 `118156bd…0948ff`); emulator evidence is artifacts `10676937872` and `10677092622`. Full detail is in `docs/06-testing/PHASE6-CAMPAIGNS-SLICE.md`. PR #205 contains this stacked slice and remains open, draft and unmerged.

```text
GATE 6 = PASS (Farms + Parcels + Campaigns + combined flow)
```

## Phase 9 — Activity engine

The Activity engine is complete, validated and **merged into `main`**: PR #206 was merged
as commit `f82be163`, on top of `main` at `5ddecdfc`. Code commit `3caaef94` is the one
whose CI evidence is quoted below.

Phase 9 delivers the common Activity aggregate, not typed agronomic forms. `activities`
already existed from schema v2, so the phase extends rather than creates: Room schema v4
and `MIGRATION_3_4` add only `activity_parcels` and its indices, with no data rewrite.
Selecting several Parcels produces **one** `activities` row and one `activity_parcels`
row per Parcel — never one Activity per Parcel — and the same canonical Activity is
reachable from any of its Parcels. Targets never synchronise independently: every
mutation collapses into a single deterministic Activity outbox intent.

The lifecycle is Activity's own, not a copy of Campaign's: `DRAFT → PLANNED → COMPLETED`,
`cancel` from `DRAFT` or `PLANNED`, and an explicit confirmed `reopen` back to `PLANNED`.
A planned Activity requires at least one Parcel; a draft may be saved empty and resumed.
A completed Activity is protected from edits until it is reopened, archive is allowed only
for `DRAFT` or `CANCELLED`, archived rows cannot be mutated or resurrected, and a Parcel
from another Farm rolls the whole create back.

No sixth root tab: the five frozen roots are untouched. Activities live in an
**Actuaciones** section inside Farm detail plus a nested `activity/{activityId}` route
that resolves to `Mi Olivar`, and `Registrar (+) → Registrar actuación` now opens the
production editor instead of the old reference screen, writing through the same aggregate.

Both emulator workflows passed on `3caaef94`: Android CI #326 (run `35745611196`) with
`foundation` SUCCESS and `gate3-emulator` SUCCESS, and the independent Gate 3 Android
Emulator Evidence #46 (run `35745611268`) SUCCESS. The full instrumented suite passed
75/75, including 11/11 Activity contract tests, and 20 repository tests passed with real
airplane mode enabled. The crash buffer was 0 bytes. The verified DEV APK is artifact
`magina-olivo-dev-debug` (13,258,909 bytes, sha256
`a1f0e131f645d051db18f4188eedbd31e94a5b2af704a08a02701d25eacd7923`).

`app/schemas/.../4.json` was generated by the Room annotation processor in CI and
committed verbatim (`b22977be`); its `identityHash` was never written by hand.
`RoomMigrationTest` validates the 3→4 migration against it. Full detail is in
`docs/06-testing/PHASE9-ACTIVITY-ENGINE-SLICE.md`.

Two CI workflow changes were needed to get there and are part of the branch: a red run
used to show only `Process completed with exit code 1`, so Gradle compiler errors, failing
instrumentation output and the emulator evidence summary are now published as workflow
annotations, which are readable without a GitHub session.

```text
PHASE 9 = COMPLETE AND VALIDATED / MERGED TO MAIN (f82be163)
```

### Post-merge correction

Android CI #328, the first run of `main` after the merge, failed on the navigation E2E
with the same tree that had passed three times on the branch: the suite was losing races
on a cold emulator, not regressing. `hotfix/android-e2e-stability` fixes the suite only —
editors now prove they opened before the test types into them, clicks are guarded, and
the test no longer expects the Farm list when Mi Olivar restores the Farm detail it was
left on. It also lets both workflows run on pushes to `feat/**` and `hotfix/**`, so a
branch can reach a green emulator run before a pull request exists. Android CI #329-#331
show the intermediate diagnoses; **Android CI #332 is green**.

## Phase 10 — Typed agricultural activities + irrigation

Complete, validated and **merged into `main`** as commit `4acc3ab9`, a normal merge
commit whose parents are `f82be163` and `0a10fe67`. The branch carried
`hotfix/android-e2e-stability`, so this one merge also restored `main` to green.

Phase 10 adds what kind of work an Activity was, as structured fields, without a second
Activity and without a giant form. Room v5 and the additive `MIGRATION_4_5` create the
seven typed detail tables the contract defines — pruning, fertilisation, phytosanitary,
soil work, irrigation, maintenance and incident — plus the irrigation tariff snapshot from
`RC1.2-PRODUCT-LOCK` §8. Each is one-to-one with its Activity, so the database itself
enforces that an Activity carries only one, and `OBSERVATION` and `OTHER` carry none
because the contract gives them no structured fields.

A detail is an aggregate child, never an aggregate of its own: it is validated against the
Activity's type before anything is written, then written in the same transaction as the
header and the Parcel targets, it moves the Activity's own version, and it queues no
synchronization intent of its own (`RC1-NORMATIVE-ADDENDUM` D5 and D10). Retyping an
Activity replaces its detail in that same transaction. No fertilisation, irrigation or
pruning outbox exists.

The editor keeps its common header and shows exactly one typed block, the one belonging to
the chosen type; switching type removes the previous block rather than hiding it. The
irrigation block can record a historical tariff snapshot, which is an estimate for the
farmer's own reading: `expenses` remains the only authoritative financial source, and
`activities.cost_cents` / `activities.currency` are still neither read nor written
(`RC1-NORMATIVE-ADDENDUM` D2). `product_id` is reserved, nullable and carries no foreign
key, so a fertilisation or a treatment is recordable with no Products module (D8).

Both workflows passed on `526e1605`: Android CI #336 (run `35778004863`) with `foundation`
SUCCESS and `gate3-emulator` SUCCESS, and the independent Gate 3 Android Emulator Evidence
#55 (run `35778004995`) SUCCESS. The full instrumented suite passed 94/94, including 17/17
typed detail contract tests, 42 JVM unit tests passed, and 37 repository tests passed with
real airplane mode. The crash buffer was 0 bytes. The verified DEV APK is artifact
`magina-olivo-dev-debug` (13,338,868 bytes, sha256
`435beb9316af0e47dd7ddb4605df04feed1b6597b44f2f9e35d03e5cf1a2969a`).

`app/schemas/.../5.json` was generated by the Room annotation processor in CI and committed
verbatim (`d7ea2914`), identityHash `a1fcd78acb39c2497f0f20efb5602598`; the hand-written
migration was then verified against it column by column before the migration test ran. Full
detail is in `docs/06-testing/PHASE10-TYPED-ACTIVITIES-SLICE.md`.

Post-merge verification: **Android CI #338** (run `35782182234`) on `4acc3ab9` passed with
`foundation` SUCCESS and `gate3-emulator` SUCCESS. The DEV APK from that run is 13,338,872
bytes, sha256 `d446b494f9b36d0a7c796d00a4ded6f3a8b65a365716834309f584f76e90045c`.

```text
PHASE 10 = COMPLETE AND VALIDATED / MERGED TO MAIN (4acc3ab9)
MAIN = GREEN
```

## Phase 11 — Attachments

Implemented on branch `claude/dreamy-dijkstra-tdui2c`; plan and decisions in
`docs/07-plans/PHASE11-ATTACHMENTS.md`, evidence in
`docs/06-testing/PHASE11-ATTACHMENTS-SLICE.md`. **Gate 11 passed** on commit `803d69b6`:
Android CI #340 (run `35840622582`) with `foundation` and `gate3-emulator` SUCCESS —
111/111 instrumented tests, 52/52 repository tests in airplane mode including 14/14
attachment contract tests, empty crash buffer — and the independent Gate 3 Android
Emulator Evidence #57 (run `35842241545`) SUCCESS. **Merged into `main`** through PR #207 as
merge commit `e42754ac`, after the PR's own CI run was green.

- Camera capture (through the app's own `FileProvider`) and document picker for images
  and PDF, from a "Documentos" section in Farm, Parcel and Activity detail. No new root.
- Every attachment is copied into `filesDir/attachments/` with its SHA-256 and size
  before its `documents` row and single `UPLOAD_ATTACHMENT` intent are written in one
  transaction; a failed transaction releases the copy.
- Derived thumbnails for photos (EXIF-rotated) and the first page of PDFs.
- `recordUploadFailure` records a failed upload without touching the local URI, the
  file or the version — the Gate 11 guarantee.
- The Farm cover is now a copied Farm photo; covers saved before Phase 11 keep their
  content URI.
- No Room schema change: `documents` (schema v2) already carries the attachment
  contract, so the database stays at v5.

## Phase 12 — Expenses, purchases, organizations + generic OCR

Implemented on branch `claude/dreamy-dijkstra-tdui2c`; plan and decisions in
`docs/07-plans/PHASE12-EXPENSES-ORGANIZATIONS-OCR.md`, evidence in
`docs/06-testing/PHASE12-EXPENSES-SLICE.md`. **Gate 12 passed** on commit `f0725b2a`:
Android CI #346 (run `35853828444`) with `foundation` and `gate3-emulator` SUCCESS, the
5→6 migration verified against the compiler-exported `6.json`, empty crash buffer. The
PR #208 head `d500b00` (a test-helper race fix plus evidence) was green again on
`foundation`, `gate3-emulator` and `gate3-evidence` before merging into `main`.

- Room v6: `expenses` gains `status` (DRAFT | POSTED), `origin` and its Activity /
  Harvest / Delivery / supplier links; new `agricultural_organizations`,
  `organization_roles`, `purchases`, `purchase_items`, `document_ocr_extractions`.
- Every total is computed from POSTED expenses only. The Activity form's "Coste" edits
  its one linked ACTIVITY_COST Expense in the same transaction (D2).
- Organizations carry several roles and are chosen, not duplicated.
- "Subir documento" keeps the file, reads it on the device (ML Kit, bundled model), and
  shows a review form; confirming creates a DRAFT expense that counts only after a person
  posts it.

**Merged into `main`** through PR #208 as merge commit `2fbeb935`.

## Phase 13 — Harvest

Implemented on branch `claude/dreamy-dijkstra-tdui2c`; plan and decisions in
`docs/07-plans/PHASE13-HARVEST.md`, evidence in `docs/06-testing/PHASE13-HARVEST-SLICE.md`.
**Gate 13 passed** on commit `b8fccb38`: Android CI #352 (run `35858239514`) with
`foundation` and `gate3-emulator` SUCCESS, empty crash buffer, and the independent Gate 3
Android Emulator Evidence #67 (run `35858242303`) SUCCESS.

- Room v7: `harvests` gains collection method, worker count and machinery text;
  new `harvest_parcels`, a child of the Harvest aggregate (D6).
- A Harvest belongs to its Farm's running Campaign and its origin Parcels are that
  Campaign's. Several Parcels default to "No conozco el reparto exacto"; exact kilos
  must add up to the total to the gram; a partial split leaves the rest unattributed.
- S70 shows campaign totals as known-per-Parcel kilos plus "sin repartir" kilos.

**Merged into `main`** through PR #209 as merge commit `c75cad56`.

## Phase 14 — Deliveries + weight-ticket OCR + later yield

Implemented on branch `claude/dreamy-dijkstra-tdui2c`; plan and decisions in
`docs/07-plans/PHASE14-DELIVERIES-TICKET-OCR-YIELD.md`, evidence in
`docs/06-testing/PHASE14-DELIVERIES-SLICE.md`. **Gate 14 passed** on commit `a90068e4`:
Android CI #358 (run `35861745903`) with `foundation` and `gate3-emulator` SUCCESS, and
Gate 3 Android Emulator Evidence #72 (run `35861748914`) SUCCESS with an empty crash buffer.

- Room v8: `deliveries`, `delivery_parcels` (same split rule as Harvest) and
  `delivery_yield_analyses`, a separate record so a later yield never changes the delivery.
- A weight ticket is read on the device by the generic OCR service (`DELIVERY_TICKET`);
  the Delivery is created only by the explicit reviewed command, from the values the farmer
  confirmed, once.
- S80 shows delivered kilos and fat/industrial yield weighted by kilos with its coverage.

**Merged into `main`** through PR #210 as merge commit `a701d2b9`.

## Phase 15 — Machinery

Implemented on branch `claude/dreamy-dijkstra-tdui2c`; plan and decisions in
`docs/07-plans/PHASE15-MACHINERY.md`, evidence in `docs/06-testing/PHASE15-MACHINERY-SLICE.md`.
**Gate 15 passed** on commit `ce46234b`: Android CI run `35864581017` with `foundation` and
`gate3-emulator` SUCCESS, and Gate 3 Android Emulator Evidence run `35864584360` SUCCESS
with an empty crash buffer.

- Room v9: `machines` (its own aggregate, archived rather than deleted) and
  `activity_machines` (children of the Activity aggregate).
- "Maquinaria" is reached from the Mi Olivar header; the Activity editor gains an optional
  machinery section with optional hours. Nothing about an Activity becomes mandatory.

## Phase 16 — Calendar, agenda, reminders and Android notifications

Implemented on branch `claude/dreamy-dijkstra-tdui2c` (PR #212); plan and decisions in
`docs/07-plans/PHASE16-AGENDA-REMINDERS.md`, evidence in
`docs/06-testing/PHASE16-AGENDA-REMINDERS-SLICE.md`. Emulator evidence passed on commit
`5566c8ec`: Android CI run `35874808659` (`foundation`, `gate3-emulator` SUCCESS) and Gate 3
Android Emulator Evidence run `35874811956` SUCCESS, with `AgendaReminderContractTest` 10/10
in airplane mode.

- Room v10: `activity_planning_details` and `reminders` (children of the Activity aggregate).
- The Calendario root shows planned work; reminders are local `AlarmManager` alarms
  rebuilt from the stored rows; notifications open the Activity.

**Gate 16 PASS** (owner physical-device check, 2026-09-23). Merged into `main` through PR #212.

## Merged on 2026-09-24 (owner request)

- #212 Phase 16 · #213 UI polish v2 · #214 Inicio with real data + identity pass (#215)
- #218 Design v3 / CR-004 (Room v11 grove description, big photo headers, Farm hub + sub-screens)
- #217 Phase 17 — Catastro lookup and confirmed import. CI evidence PASS, including the live WFS
  import (`docs/06-testing/PHASE17-CATASTRO-IMPORT-SLICE.md`). **Gate 17 physical-device
  acceptance is pending** on the owner phone with the `main` APK.

## Next deliverable

Owner phone test of the `main` APK (Catastro import with and without coverage) to close Gate 17,
then **Phase 18 — Land registry geometries + map**.

## Parallel-chat reconciliation

All work from separate chats is reconciled through `docs/00-master/SINGLE-TRACK-EXECUTION.md`.

Rules:
- `main` is the source of truth;
- merged Phase 3 slices #181–#192 supersede the old monolithic PR #180;
- old web/V20 branches are reference-only unless a later phase explicitly approves selective reuse;
- no second roadmap may override the single-track plan.
