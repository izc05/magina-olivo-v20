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
```

## Current allowed phase

```text
▶ PHASE 5 — LOCAL DATABASE FOUNDATION
```

Gate 4 is recorded as PASS in `docs/06-testing/PHASE4-GATE-CHECKLIST.md`. Phase 5 may now implement the Room schema, migrations, local repositories and test fixtures; farm feature UI remains blocked until Gate 5 passes.

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

## Next deliverable

Implement Phase 5 local database foundation:

- Room schema with client UUIDs and explicit converters;
- migration/exported-schema coverage;
- soft-delete, version and synchronization metadata conventions;
- local repository boundaries and observable reads;
- attachment metadata and durable outbox tables;
- deterministic DEV/test fixtures.

Gate 5 requires migration tests plus restart persistence and airplane-mode CRUD proof on test entities. Do not begin the production Farm feature UI before Gate 5 passes.

## Parallel-chat reconciliation

All work from separate chats is reconciled through `docs/00-master/SINGLE-TRACK-EXECUTION.md`.

Rules:
- `main` is the source of truth;
- merged Phase 3 slices #181–#192 supersede the old monolithic PR #180;
- old web/V20 branches are reference-only unless a later phase explicitly approves selective reuse;
- no second roadmap may override the single-track plan.
