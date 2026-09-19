# Olive Farm App — Current Work State

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Last reviewed:** 2026-09-19

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
```

## Current allowed phase

```text
▶ PHASE 6 — FARMS RC1.2
```

Gate 5 is recorded as PASS in `docs/06-testing/PHASE5-GATE-CHECKLIST.md`. Phase 6 may now implement the complete offline Farm lifecycle and production UI; Parcel implementation remains blocked until the Phase 6 gate passes.

## Mandatory reading order for any agent

1. `docs/00-master/RC1-BASELINE.md`
2. `docs/00-master/RC1.2-PRODUCT-LOCK.md`
3. `docs/00-master/SINGLE-TRACK-EXECUTION.md`
4. `docs/00-master/RC1.2-CHANGE-REQUEST.md`
5. `docs/00-master/RC1.2-CHANGE-REQUEST-003-BRAND-VISUAL.md`
6. `docs/design/VISUAL_DESIGN_LOCK.md`
7. `docs/design/DESIGN_SYSTEM.md`
8. `docs/00-master/RC1.1-PRODUCT-LOCK.md`
9. `docs/00-master/RC1.1-CHANGE-REQUEST.md`
10. `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`
11. `docs/00-master/MASTER-SPEC-RC1.md`
12. `docs/00-master/RC1-GATE-REVIEW.md`
13. `docs/07-plans/ROADMAP-RC1.2.md`
14. `docs/07-plans/PHASE3-DESIGN-REFERENCE.md`

Then read only the domain/architecture/UI contracts needed by the current phase.

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

## Next deliverable

Implement Phase 6 Farms RC1.2:

- create, edit, archive and restore Farm operations;
- local list/card/detail UI with loading, empty, success and error states;
- optional cover-photo metadata reference and notes;
- surface, parcel count, active campaign and productive-summary placeholders backed only by truthful local data;
- 1/20/50+ farm behavior and complete offline operation.

Gate 6 requires the complete Farm lifecycle to survive restart and work offline. Do not begin production Parcel implementation before Gate 6 passes.

## Parallel-chat reconciliation

All work from separate chats is reconciled through `docs/00-master/SINGLE-TRACK-EXECUTION.md`.

Rules:
- `main` is the source of truth;
- merged Phase 3 slices #181–#192 supersede the old monolithic PR #180;
- old web/V20 branches are reference-only unless a later phase explicitly approves selective reuse;
- no second roadmap may override the single-track plan.
