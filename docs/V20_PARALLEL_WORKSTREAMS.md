# V20 Parallel Workstreams

Baseline product commit for parallel screen work: `f69f785c379ff5e3143c4fed2a072213af18787f`.

The coordinator chat remains responsible for `feat/v20-visual-prototype`, CI health, shared CSS/layout, cross-domain contracts and final integration. Parallel chats work only on their own `agent/*` branches.

## Workstream A — Inicio + Hoy

Branch: `agent/home-hoy`

Primary routes/components:
- `apps/web/src/app/page.tsx`
- `apps/web/src/app/mi-campo/hoy/page.tsx`
- `apps/web/src/components/home-daily-center.tsx`
- `apps/web/src/components/home-priority-card.tsx`
- `apps/web/src/components/today-agenda-client.tsx`
- directly related data sources

Goal: make the daily entry point useful, real-data driven, simple and mobile-first. Do not redesign global navigation.

## Workstream B — Mi Campo + Finca

Branch: `agent/mi-campo-fincas`

Primary routes/components:
- `apps/web/src/app/mi-campo/page.tsx`
- finca view routes excluding `fincas/nueva`
- `apps/web/src/components/mi-campo-dashboard.tsx`
- `apps/web/src/components/farm-detail-shell.tsx`
- finca-specific panels/data sources

Goal: turn Finca into the clear visible unit: status, recent activity, campaign/economics/documents shortcuts and understandable real data.

## Workstream C — Registrar + Campaña

Branch: `agent/registro-campana`

Primary routes/components:
- `apps/web/src/app/mi-campo/registrar/**`
- `apps/web/src/app/mi-campo/campana/**`
- `apps/web/src/components/register-hub-client.tsx`
- harvest/result/settlement entry components
- `apps/web/src/components/campaign-summary-client.tsx`
- record/campaign data sources directly required by those screens

Goal: fastest possible field recording flow, with real persistence and a campaign summary that a farmer can understand at a glance.

## Workstream D — Profesional

Branch: `agent/profesional`

Primary routes/components:
- `apps/web/src/app/mi-campo/profesional/**`
- `apps/web/src/components/professional-*`
- corresponding professional data sources

Goal: coherent customer → work → quote → invoice → collection → document/share journey. Keep financial/audit state explicit.

## Workstream E — Documentos + OCR

Branch: `agent/documentos-ocr`

Primary routes/components:
- `apps/web/src/app/mi-campo/documentos/**`
- `apps/web/src/components/farm-document-*`
- `apps/web/src/components/document-review-client.tsx`
- `apps/web/src/components/harvest-ocr-client.tsx`
- document/OCR data sources

Goal: upload and review documents with clear OCR proposal states, explicit confirmation and links to finca/campaign/domain records.

## Workstream F — GIS + Nueva Finca

Branch: `agent/gis-fincas`

Primary routes/components:
- `apps/web/src/app/mi-campo/fincas/nueva/**`
- `apps/web/src/app/mi-campo/mapa/**`
- `apps/web/src/components/new-farm-wizard.tsx`
- `apps/web/src/components/farm-map.tsx`
- GIS-facing web data-source code created for this flow

Goal: real Catastro/SIGPAC selection and preview. Preferred progression: identify/search → preview real geometry → create/save finca → link verified land reference with optional canonical geometry. A provider failure must not fabricate a successful location.

## Workstream G — QA + Mobile E2E

Branch: `agent/qa-mobile-e2e`

Primary files:
- `e2e/**`
- tightly scoped screen fixes only when required by a demonstrated failing test

Goal: continuously test the other streams at 360/390/430 widths, critical farmer journey, documents/OCR and public/professional flows. Do not weaken assertions simply to obtain green CI.

## Workstream H — Public/Explore review

Branch: `agent/public-explore`

Primary routes/components:
- `apps/web/src/app/explorar/**`
- `apps/web/src/app/documento-publico/**`
- `apps/web/src/components/public-commercial-share-client.tsx`
- public-only data sources

Goal: polish public surfaces without introducing private-data dependencies.

## Collision policy

Parallel agents must not independently edit global styles/layout/contracts/migrations/workflows. When several screens need the same global change, the coordinator applies it once after reviewing all requests.

A workstream may introduce a **screen-local stylesheet/component** inside its scope. Prefer this over modifying global CSS.

## Integration order

The coordinator reviews each branch independently. Passing branch-local tests does not mean automatic integration. Integration normally follows:

1. QA evidence and scope review.
2. Conflict check against current `feat/v20-visual-prototype` HEAD.
3. Cherry-pick or reviewed merge into the candidate.
4. Full candidate + browser E2E.
5. Only then mark the workstream integrated.

No parallel agent merges `main` or the V20 candidate.

## Chat starter template

Use this at the beginning of a new coding chat and replace `<WORKSTREAM>` and `<BRANCH>`:

> Trabaja como agente V20 de Mágina Olivo para el workstream `<WORKSTREAM>`. Repositorio: `izc05/magina-olivo-v20`. Rama exclusiva: `<BRANCH>`. Parte del baseline `f69f785c379ff5e3143c4fed2a072213af18787f`. Lee `AGENTS.md` y `docs/V20_PARALLEL_WORKSTREAMS.md` en la rama `chore/v20-parallel-workstreams`, además de los blueprints del dominio. No toques `main`, no fusiones el PR #1 y no edites archivos compartidos reservados al coordinador. Implementa únicamente tu pantalla/módulo con datos reales, estados loading/empty/error/success, responsive 360/390/430 y pruebas relevantes. Haz commits pequeños en tu rama. Al terminar, entrega SHA, archivos cambiados, tests y cualquier cambio compartido que necesites para que el chat coordinador lo integre.
