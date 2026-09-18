# Olive Farm App — Current Work State

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Last reviewed:** 2026-09-18

This file is the quick continuity marker for a new ChatGPT/Codex/Antigravity session. It does not replace the baseline/spec; it tells the worker where to resume.

## Passed Gates

```text
✅ 0.1 Master product definition
✅ 0.2 Scalable Mi Campo architecture
✅ 0.3 Data Model RC1 + Future (with normative addendum)
✅ 0.4 Catastro Contract
✅ 0.5 Offline/Sync Contract (with normative addendum)
✅ 0.6 Complete Screen Map (with normative cost interpretation)
✅ 0.7 Design System Spec
✅ 0.8 RC1.1 Product reconciliation/spec lock
✅ 0.9 RC1.2 geographic-neutral scope + generic OCR + naming gate
```

## Current allowed phase

```text
▶ PHASE 1 — ANDROID PROJECT FOUNDATION
```

Do not start Phase 2 or any agricultural feature until Gate 1 has passed.

## Mandatory reading order for any agent

1. `docs/00-master/RC1-BASELINE.md`
2. `docs/00-master/RC1.2-PRODUCT-LOCK.md`
3. `docs/00-master/RC1.2-CHANGE-REQUEST.md`
4. `docs/00-master/RC1.1-PRODUCT-LOCK.md`
5. `docs/00-master/RC1.1-CHANGE-REQUEST.md`
6. `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`
7. `docs/00-master/MASTER-SPEC-RC1.md`
8. `docs/00-master/RC1-GATE-REVIEW.md`
9. `docs/07-plans/ROADMAP-RC1.2.md`
10. the current phase implementation plan when one exists

Then read only the domain/architecture/UI contracts needed by the current phase.

## Hard stop rule

If implementation requires changing an immutable baseline decision, stop feature work and open a Change Request. Do not silently adapt architecture because a library, agent or generated template prefers another approach.

RC1.2 Product Lock is normative and overrides contradictory RC1-era wording until all older documents are editorially reconciled.

## RC1.2 reconciliation

Phase 0.9 documentation is defined in `docs/07-plans/ROADMAP-RC1.2.md`. Public brand remains TBD; do not deepen legacy customer-facing naming. Implementation remains locked to Phase 1 until Gate 1 passes.

## Next deliverable

Execute Phase 2 according to `docs/07-plans/PHASE2-BASE-ARCHITECTURE.md`.

Gate 2 requires CI, architecture tests, scope review and a physical launcher smoke after the refactor. Do not start Phase 3 implementation before Gate 2 PASS.
