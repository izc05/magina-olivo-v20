# Olive Farm App — Current Work State

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Last reviewed:** 2026-09-24

This file is the quick continuity marker for a new ChatGPT/Codex/Antigravity session. It does not replace the baseline/spec; it tells the worker where to resume.

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

## Current allowed phase

```text
▶ PHASE 19 — CUADERNO DE CAMPAÑA + HISTORICAL ANALYTICS (opened 2026-09-24 after Gate 18 PASS)
  19A — Cuaderno projection/navigation: MERGED (PR #233, 2026-09-24).
  19B — Jornada + multiple Pesadas: MERGED (PR #235, 2026-09-24, owner "puedes seguir"; CI green:
  unit, 201 instrumented tests, Gate 3 evidence; Room v13). Emulator/airplane-mode check by Codex
  pending — Codex review quota was exhausted on #235.
  19C — Rendimientos pendientes: MERGED (PR #236, 2026-09-24, owner "continuamos"; CI green).
  19D — Jornales: MERGED (PR #237, 2026-09-24, owner "puedes continuar"; CI green; Room v14).
  19E — Equipment usage: IN PROGRESS (executor Claude; branch claude/phase19e-equipment; Room v15).
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
8. `docs/04-ui/CUADERNO-CAMPANA-SCREEN-SPEC-RC1.2.md`
9. `docs/design/VISUAL_DESIGN_LOCK.md`
10. `docs/design/DESIGN_SYSTEM.md`
11. `docs/00-master/RC1.1-PRODUCT-LOCK.md`
12. `docs/00-master/RC1.1-CHANGE-REQUEST.md`
13. `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`
14. `docs/00-master/MASTER-SPEC-RC1.md`
15. `docs/00-master/RC1-GATE-REVIEW.md`
16. `docs/07-plans/ROADMAP-RC1.2.md`
17. `docs/07-plans/PHASE3-DESIGN-REFERENCE.md`

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
