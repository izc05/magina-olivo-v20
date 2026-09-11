# V20 Parallel Workstreams

Current synchronized baseline for new `agent/*` screen work: `09299449bb07eab24867c658e63a32350ae7d805`.

The coordinator chat remains responsible for `feat/v20-visual-prototype`, CI health, shared CSS/layout, cross-domain contracts and final integration. Parallel chats work only on their assigned branches. Before coding, every agent must verify whether the candidate has advanced and report if it needs a coordinator sync.

## Workstream A — Inicio + Hoy

Branch: `agent/home-hoy`  
Issue: #7

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
Issue: #3

Primary routes/components:
- `apps/web/src/app/mi-campo/page.tsx`
- finca view routes excluding `fincas/nueva`
- `apps/web/src/components/mi-campo-dashboard.tsx`
- `apps/web/src/components/farm-detail-shell.tsx`
- finca-specific panels/data sources

Goal: turn Finca into the clear visible unit: status, recent activity, campaign/economics/documents shortcuts and understandable real data.

## Workstream C — Registrar + Campaña

Branch: `agent/registro-campana`  
Issue: #4

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
Issue: #5

Primary routes/components:
- `apps/web/src/app/mi-campo/profesional/**`
- `apps/web/src/components/professional-*`
- corresponding professional data sources

Goal: coherent customer → work → quote → invoice → collection → document/share journey. Keep financial/audit state explicit.

## Workstream E — Documentos + OCR

Branch: `agent/documentos-ocr`  
Issue: #6

Primary routes/components:
- `apps/web/src/app/mi-campo/documentos/**`
- `apps/web/src/components/farm-document-*`
- `apps/web/src/components/document-review-client.tsx`
- `apps/web/src/components/harvest-ocr-client.tsx`
- document/OCR data sources

Goal: upload and review documents with clear OCR proposal states, explicit confirmation and links to finca/campaign/domain records.

## Workstream F — GIS + Nueva Finca

Branch: `agent/gis-fincas`  
Issue: #2

Primary routes/components:
- `apps/web/src/app/mi-campo/fincas/nueva/**`
- `apps/web/src/app/mi-campo/mapa/**`
- `apps/web/src/components/new-farm-wizard.tsx`
- `apps/web/src/components/farm-map.tsx`
- GIS-facing web data-source code created for this flow

Goal: real Catastro/SIGPAC selection and preview. Preferred progression: identify/search → preview real geometry → create/save finca → link verified land reference with optional canonical geometry. A provider failure must not fabricate a successful location.

## Workstream G — QA + Mobile E2E

Branch: `agent/qa-mobile-e2e`  
Issue: #8

Primary files:
- `e2e/**`
- tightly scoped screen fixes only when required by a demonstrated failing test

Goal: continuously test the other streams at 360/390/430 widths, critical farmer journey, documents/OCR and public/professional flows. Do not weaken assertions simply to obtain green CI.

## Workstream H — Public / Explore

Branch: `agent/public-explore`  
Issue: #9

Primary routes/components:
- `apps/web/src/app/explorar/**`
- `apps/web/src/app/documento-publico/**`
- `apps/web/src/components/public-commercial-share-client.tsx`
- public-only data sources

Goal: polish public surfaces without introducing private-data dependencies.

## Workstream I — Perfil + Ajustes

Branch: `agent/perfil-ajustes`  
Issue: #11

Primary routes/components:
- `apps/web/src/app/perfil/**`
- `apps/web/src/components/permission-center.tsx`
- profile/preference settings rendered from Perfil
- direct profile/settings data sources

Goal: clear identity, preferences, permissions and workspace/account settings without turning Perfil into Admin.

## Workstream J — Planificar + Tareas

Branch: `agent/planificar-tareas`  
Issue: #12

Primary routes/components:
- `apps/web/src/app/mi-campo/planificar/**`
- `plan-task-client.tsx`
- `planned-task-actions.tsx`
- `farm-plan-shortcut.tsx`
- directly related agenda/planned-task data sources

Goal: create future tasks simply, relate them to a finca, surface them in agenda/calendar and link execution to the real work record.

## Pre-existing workstream K — Admin

Branch: `feat/v20-admin-progress`  
Issue: #13

This branch pre-dates the formal `agent/*` protocol. It currently shares history with other candidate work; from now on it must specialise only in Admin/CMS work according to `docs/ADMIN_BLUEPRINT.md`.

Goal: users/workspaces, territory/content, cooperatives/almazaras, advertising/configuration and supported administrative operations. It must not redesign farmer-facing screens.

## Pre-existing workstream L — Clima + Radar

Branch: `feat/v20-weather-map`  
Issue: #14

This branch also pre-dates the formal protocol and currently shares history with other candidate work. From now on it must specialise only in weather/radar UX and related map overlays.

Goal: clearly distinguish forecast, observed radar and alerts; expose freshness/source and safe stale/error states; do not duplicate GIS parcel selection.

## Maintenance workstream — Cleanup / Structure

Branch: `chore/v20-cleanup-structure`  
PR: #10

This workstream owns repository/documentation/developer-environment cleanup. It is not a screen builder and must not absorb product work.

## Collision policy

Parallel agents must not independently edit global styles/layout/contracts/migrations/workflows. When several screens need the same global change, the coordinator applies it once after reviewing all requests.

Integration-owned by default:
- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/app/premium.css`
- `apps/web/src/app/mobile-hardening.css`
- `apps/web/src/components/bottom-nav.tsx`
- `apps/web/src/components/topbar.tsx`
- `packages/contracts/**`
- `database/migrations/**`
- `.github/workflows/**`
- shared auth/API/runtime configuration

A workstream may introduce a screen-local stylesheet/component inside its own scope. Prefer this over modifying global CSS.

## Integration order

The coordinator reviews each branch independently. Passing branch-local tests does not mean automatic integration:

1. Agent handoff with SHA, files, tests, viewports and known limitations.
2. Scope/collision review against the current `feat/v20-visual-prototype` HEAD.
3. QA evidence and conflict check.
4. Cherry-pick or reviewed merge into the candidate.
5. Full candidate + browser E2E on the resulting candidate HEAD.
6. Only then mark the workstream integrated.

No parallel agent merges `main` or the V20 candidate.

## Chat starter template

> Trabaja como agente V20 de Mágina Olivo para el Issue `<ISSUE>`. Repositorio: `izc05/magina-olivo-v20`. Usa exclusivamente la rama indicada en ese Issue. Lee primero el Issue, `AGENTS.md` y `docs/V20_PARALLEL_WORKSTREAMS.md` desde `chore/v20-parallel-workstreams`, además de los blueprints del dominio. Antes de modificar nada comprueba el HEAD actual de tu rama y del candidate; si tu rama necesita sincronización, no reescribas historia: informa al coordinador. No toques `main`, no fusiones el PR #1 y no edites archivos compartidos reservados al coordinador. Implementa únicamente tu pantalla/módulo con datos reales, estados loading/empty/error/success, responsive 360/390/430 y pruebas relevantes. Haz commits pequeños en tu rama. Al terminar, entrega SHA, archivos cambiados, tests, viewports, limitaciones y cualquier cambio compartido que necesites para integración.
