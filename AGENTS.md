# Mágina Olivo V20 — Parallel Agent Protocol

This repository is being developed with several parallel ChatGPT/Codex workstreams. The goal is to accelerate screen delivery without losing the V20 product model or destabilising the candidate.

## Product rule

**Sencillo por fuera, estructurado por dentro.**

The farmer should see familiar concepts (`Finca`, `Hoy`, `Registrar`, `Campaña`, `Cliente`) while Catastro, SIGPAC, OCR, accounting state and other technical detail remain behind the interface unless they are needed.

## Source of truth

Before changing UI or behaviour, read the relevant material under `docs/`, especially:

- `docs/V20_MASTER_PRODUCT_ARCHITECTURE.md`
- `docs/MI_CAMPO_STRUCTURAL_BLUEPRINT.md`
- `docs/MI_CAMPO_NAVIGATION_BLUEPRINT.md`
- `docs/V20_MI_CAMPO_DATA_CONTRACT.md`
- `docs/V20_VISUAL_DIRECTION.md`
- `docs/V20_BETA_CLOSURE_AUDIT.md`
- domain-specific documents such as GIS, OCR, weather/radar and professional workflows.

## Branching and integration

- Never work directly on `main`.
- Never merge the V20 candidate automatically.
- Each workstream uses its own `agent/*` branch.
- The coordination/integration chat owns `feat/v20-visual-prototype`.
- Agents must not rebase, force-push or rewrite another workstream.
- Deliver small reviewable commits.
- At handoff, report commit SHA, changed files, tests run, unresolved risks and any shared-file changes requested.

## File ownership

An agent may edit only files inside its assigned scope unless the task explicitly authorises more.

The following are integration-owned by default because they can affect every screen:

- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/app/premium.css`
- `apps/web/src/app/mobile-hardening.css`
- `apps/web/src/components/bottom-nav.tsx`
- `apps/web/src/components/topbar.tsx`
- `packages/contracts/**`
- `database/migrations/**`
- shared API/auth/runtime configuration
- `.github/workflows/**`

If a screen needs a shared change, the agent should document the exact required change instead of silently broadening its scope.

## Definition of done for a screen

A screen is not complete because it looks finished. It must:

1. Use real API data whenever `NEXT_PUBLIC_API_URL` is configured; demo/local data must remain explicit preview behaviour only.
2. Preserve current authentication/workspace context.
3. Work at 360, 390 and 430 px widths without horizontal overflow.
4. Keep interactive targets usable on mobile.
5. Handle loading, empty, error and success states.
6. Avoid fake actions: a button that looks functional must perform a real supported action or state clearly that it is pending.
7. Keep Spanish copy concise and understandable to a farmer.
8. Pass relevant typecheck/build/tests and add focused regression coverage when behaviour changes.

## Agent roles

### Coordinator / Integrator
Owns candidate health, shared files, architecture decisions, cross-workstream conflicts, CI, cherry-picks/integration and final release readiness.

### Screen Builder
Owns one isolated user-facing route or closely related route group. Focuses on UI, API connection, responsive states, accessibility and screen-level tests.

### GIS Agent
Owns `Nueva finca`, real Catastro/SIGPAC selection, map linking and verified geometry UX. Must never present a fake GIS selection as real.

### Professional Agent
Owns customer, work, quote, invoice, collection and public commercial flows, preserving auditability and existing professional contracts.

### Documents/OCR Agent
Owns upload, review, extraction and document linking screens. OCR suggestions remain reviewable; extracted data must not silently become authoritative.

### QA / Mobile Agent
Does not redesign product. Finds regressions, strengthens Playwright/smoke coverage, captures reproducible failures and makes only tightly scoped test/hardening fixes.

### Visual Consistency Reviewer
Reviews completed screens against V20 visual direction and mobile ergonomics. It should propose integration-owned CSS changes rather than editing global styles from multiple branches.

## Handoff format

Every agent finishes with:

- **Branch**
- **Commit SHA(s)**
- **Implemented**
- **Files changed**
- **Tests passed**
- **Screens checked at 360/390/430**
- **Shared change requested** (or `none`)
- **Known limitations**
- **Ready for coordinator review:** yes/no
