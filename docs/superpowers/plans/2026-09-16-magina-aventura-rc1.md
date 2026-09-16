# Mágina Aventura V20 RC1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidar Mágina Aventura V20 en una única RC1 verificable, con rutas reales, GPS, clima, geometrías oficiales, progresión, comunidad y UI premium sobre un solo SHA verde, sin tocar `main`.

**Architecture:** `release/v20-magina-aventura-rc1` es la única rama de integración de release. Se absorben deltas especialistas en orden, empezando por la base premium ya verde (#142), corrigiendo cada rama roja antes de incorporarla y portando selectivamente los frentes con ancestros antiguos (#152, #153, #93, #138). Seguridad, procedencia de datos, geometría validada y una sola fuente GPS mandan sobre gamificación y efectos visuales.

**Tech Stack:** Next.js 16, React 19, TypeScript 6, Fastify 5, PostgreSQL/PostGIS, MapLibre GL, Playwright 1.55, Node 22, pnpm 10.15.1, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-magina-aventura-rc1-design.md`

## Global Constraints

- Rama de release única: `release/v20-magina-aventura-rc1`.
- Base de release: `integrate/v20-beta-closure` en `d24f28781e47c2bf4371b8f109fe20806b61d9e7`.
- No modificar ni fusionar directamente a `main`.
- No introducir rutas, tracks, usuarios, clima, cierres, actividad ni estadísticas ficticias en producción.
- Un track validado no implica que una ruta esté abierta o sea segura.
- Una sola fuente GPS foreground por actividad; no crear un segundo `watchPosition`.
- Weather Visuals renderiza el estado meteorológico normalizado; no inventa meteorología.
- Las operaciones de finalización/progreso deben seguir siendo idempotentes.
- Ningún frente entra en RC1 con un gate requerido rojo.
- Node `>=22 <23`; pnpm `>=10.15.1 <11`.
- Comandos base de release: `pnpm check:fast`, `pnpm build`, `pnpm e2e:beta`.

---

### Task 1: Congelar la línea base RC1 y absorber la base premium verde #142

**Files:**
- Reference: `docs/superpowers/specs/2026-09-16-magina-aventura-rc1-design.md`
- Reference: `docs/V20_MAGINA_AVENTURA_VISUAL_TARGET.md`
- Integrate from PR #142 files under `apps/web/src/app/aventura/**`, `apps/web/src/app/rutas/detalle/**`, `apps/api/src/routes/weather.ts`, `packages/weather/**`, `e2e/magina-adventure*.spec.ts`.

**Interfaces:**
- Consumes: current candidate `d24f287…`.
- Produces: RC branch containing the exact #142 Adventure premium + Weather Engine baseline.

- [ ] **Step 1: Verify ancestry and no hidden RC changes**

```bash
git fetch origin
git checkout release/v20-magina-aventura-rc1
git merge-base --is-ancestor d24f28781e47c2bf4371b8f109fe20806b61d9e7 HEAD
git diff --name-status d24f28781e47c2bf4371b8f109fe20806b61d9e7...HEAD
```

Expected: only RC1 design/plan documentation before code integration.

- [ ] **Step 2: Merge #142 without flattening its tested history**

```bash
git merge --no-ff 92c5932fb981e3567d5cc794cdb2a363d2f68d27 -m "merge: absorb Mágina Aventura premium baseline into RC1"
```

- [ ] **Step 3: Run the exact baseline gates that were green on #142**

```bash
pnpm install --frozen-lockfile
pnpm check:fast
pnpm --filter @magina/weather typecheck
pnpm --filter @magina/weather build
pnpm --filter @magina/api typecheck
pnpm --filter @magina/web typecheck
pnpm exec playwright test e2e/magina-adventure.spec.ts e2e/magina-adventure-weather.spec.ts
pnpm build
pnpm e2e:beta
```

Expected: PASS. If RC-only docs trigger a regression, stop before Task 2.

- [ ] **Step 4: Push the RC baseline and require the GitHub matrix**

```bash
git push origin release/v20-magina-aventura-rc1
```

Required remote checks on the RC SHA: Full Candidate, Browser E2E, Routes Adventure, Route Activity, Routes Closure, Weather Engine, Foundation, Environment, Platform Admin/Admin Governance, Radar, Lockfile and Staging Readiness.

---

### Task 2: Repair the Bedmar vertical #147 before integration

**Files:**
- Modify: `apps/web/src/lib/route-live-telemetry.ts`
- Modify: `apps/web/src/app/rutas/detalle/route-map.tsx`
- Modify: `e2e/magina-adventure.spec.ts`
- Verify unchanged logic in: `apps/web/src/app/rutas/detalle/route-activity-recorder.tsx`
- Verify unchanged logic in: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`

**Interfaces:**
- Consumes: `RouteLiveTelemetry`, `ROUTE_LIVE_TELEMETRY_EVENT`, `emitRouteLiveTelemetry`.
- Produces: replayable latest GPS telemetry for late-mounted map consumers, while keeping a single recorder watcher.

The current #147 failure has two concrete causes observed in Playwright artifacts:
1. one public-hub assertion still expects obsolete copy (`Ver aventuras` / `Ver rutas`) after the premium copy changed to `Comenzar aventura` / `Ver todas las rutas`;
2. the HUD receives GPS telemetry, but the map can miss the first event when its MapLibre listener mounts after the recorder has emitted it, leaving no `Tu posición · precisión ±7 m` marker.

- [ ] **Step 1: Keep the current failures as the red tests**

```bash
pnpm exec playwright test e2e/magina-adventure.spec.ts
```

Expected before fixes: FAIL in the public hub copy assertion and/or the live map position assertion reproduced by CI.

- [ ] **Step 2: Make telemetry sticky without adding another geolocation watcher**

Add a module-local latest snapshot in `route-live-telemetry.ts`:

```ts
let latestRouteLiveTelemetry: RouteLiveTelemetry | null = null;

export function getLatestRouteLiveTelemetry() {
  return latestRouteLiveTelemetry;
}

export function emitRouteLiveTelemetry(detail: RouteLiveTelemetry) {
  latestRouteLiveTelemetry = detail;
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent<RouteLiveTelemetry>(ROUTE_LIVE_TELEMETRY_EVENT, { detail }));
}
```

Do not add `navigator.geolocation.watchPosition` outside `RouteActivityRecorder`.

- [ ] **Step 3: Replay the latest telemetry when RouteMap mounts**

Import `getLatestRouteLiveTelemetry` in `route-map.tsx` and, after MapLibre is ready, seed `liveTelemetryRef.current` and call the existing `renderLivePosition`:

```ts
const latest = getLatestRouteLiveTelemetry();
if (latest) {
  liveTelemetryRef.current = latest;
  renderLivePosition(latest);
}
```

Keep subsequent updates on `ROUTE_LIVE_TELEMETRY_EVENT`.

- [ ] **Step 4: Update only the stale E2E copy expectations**

In `e2e/magina-adventure.spec.ts`, assert the current approved premium labels:

```ts
await expect(page.getByRole('link', { name: /Comenzar aventura/ })).toBeVisible();
await expect(page.getByRole('link', { name: /Ver todas las rutas/ })).toHaveAttribute('href', /^\/rutas\/?$/);
```

Do not weaken the GPS marker assertion.

- [ ] **Step 5: Verify Bedmar locally**

```bash
pnpm --filter @magina/web typecheck
pnpm exec playwright test e2e/magina-adventure.spec.ts
pnpm check:fast
pnpm build
pnpm e2e:beta
```

Expected: PASS, including `Tu posición · precisión ±7 m` inside the map region.

- [ ] **Step 6: Commit Bedmar repair on the specialist branch**

```bash
git add apps/web/src/lib/route-live-telemetry.ts apps/web/src/app/rutas/detalle/route-map.tsx e2e/magina-adventure.spec.ts
git commit -m "fix: stabilize Bedmar live GPS E2E contract"
```

- [ ] **Step 7: Re-run #147 remote gates and merge into RC1 only when green**

After the specialist SHA is green, merge it into RC1 and repeat `pnpm check:fast`, Adventure/Activity E2E and full Browser E2E on the resulting RC SHA.

---

### Task 3: Repair and port Weather Visuals #152 onto the current RC line

**Files:**
- Create/port: `apps/web/src/app/aventura/en-curso/weather-scene/weather-particles.tsx`
- Create/port: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene-model.ts`
- Create/port: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene.module.css`
- Create/port: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/adventure-weather.tsx`
- Create/port: `packages/weather/src/weather-visual-model.ts`
- Create/port: `packages/weather/src/visual.ts`
- Modify: `packages/weather/src/index.ts`
- Modify: `e2e/magina-adventure-weather.spec.ts`

**Interfaces:**
- Consumes: `AdventureWeatherState` and Weather Engine state from #142.
- Produces: `WeatherVisualModel`, `buildWeatherVisualModel`, `WeatherScene`.

The current #152 Browser E2E failure is deterministic: the `Nieve` lab preset uses intensity `2`, which maps to `snowAmount=0.55`; the balanced particle formula therefore yields `round(16 * 0.55 / 0.85) = 10`, while the test incorrectly expects `16`.

- [ ] **Step 1: Keep the failing deterministic test red**

```bash
pnpm exec playwright test e2e/magina-adventure-weather.spec.ts -g "deterministic rain and snow particles"
```

Expected before correction: snow particle count expected `16`, actual `10`.

- [ ] **Step 2: Fix the test to match the declared model, not vice versa**

```ts
await page.getByRole('button', { name: 'Nieve' }).click();
await expect(animated).toHaveAttribute('data-weather-particle-kind', 'snow');
await expect(animated).toHaveAttribute('data-weather-particle-count', '10');
```

Keep the `Nieve` lab preset at intensity `2`; do not artificially promote it to intensity `3` just to satisfy the old assertion.

- [ ] **Step 3: Port only the #152 visual delta onto RC1**

Use the 15 changed files from #152 as the source list. Reconcile `adventure-weather.tsx`, `packages/weather/src/index.ts`, `apps/web/tsconfig.json` and `deploy/staging/Dockerfile.web` against the RC version instead of replacing them blindly.

- [ ] **Step 4: Verify model + renderer**

```bash
pnpm --filter @magina/weather typecheck
pnpm --filter @magina/weather build
pnpm --filter @magina/web typecheck
pnpm exec playwright test e2e/magina-adventure-weather.spec.ts
pnpm check:fast
pnpm build
pnpm e2e:beta
```

Expected: rain count 24 for `Lluvia intensa`, snow count 10 for intensity-2 `Nieve`, reduced-motion animated field hidden and static fallback visible.

- [ ] **Step 5: Commit and push**

```bash
git add apps/web/src/app/aventura/en-curso packages/weather/src e2e/magina-adventure-weather.spec.ts apps/web/tsconfig.json deploy/staging/Dockerfile.web package.json
git commit -m "feat: integrate premium weather visuals into Adventure RC1"
```

---

### Task 4: Port Geometry Ingestion #153 without importing its old ancestry

**Files:**
- Create: `apps/worker/src/routes/geometry/normalize.ts`
- Create: `apps/worker/src/routes/geometry/ingest.ts`
- Create: `apps/worker/src/routes/geometry/source-fetch.ts`
- Create: `apps/worker/src/testing/route-geometry-normalize-smoke.ts`
- Create: `apps/worker/src/testing/route-geometry-gml-smoke.ts`
- Create: `apps/worker/src/testing/route-geometry-kmz-smoke.ts`
- Create: `apps/worker/src/testing/route-geometry-utm-smoke.ts`
- Create: `apps/worker/src/testing/route-geometry-ingestion-smoke.ts`
- Create: `apps/worker/src/testing/route-source-fetch-blocked-smoke.ts`
- Create: `apps/worker/src/testing/route-source-fetch-retry-smoke.ts`
- Create: `database/migrations/0086_route_geometry_ingestion.sql`
- Modify: `apps/worker/package.json`
- Modify: `package.json`

**Interfaces:**
- Produces: `normalizeRouteGeometry`, `normalizeRouteAsset`, `prepareRouteGeometryIngestion`.
- Formats: `kml | gml | kmz`.
- CRS: `EPSG:4326 | EPSG:25830`.
- Output: canonical WGS84 `LineString`, start/end, distance, altitude min/max, SHA-256, bbox and discrepancy recommendation.

- [ ] **Step 1: Start from a fresh RC-derived integration branch**

```bash
git checkout release/v20-magina-aventura-rc1
git checkout -b integrate/rc1-route-geometry
```

Do not merge `feat/v20-routes-adventure-live-telemetry-v3`.

- [ ] **Step 2: Port only the 13-file #153 delta**

Copy/reconcile the exact PR #153 changed files listed above. Preserve the RC versions of unrelated files.

- [ ] **Step 3: Preserve the explicit geometry contract**

`normalize.ts` must keep these public types/functions:

```ts
export type RouteGeometryFormat = 'kml' | 'gml' | 'kmz';
export type SupportedRouteCrs = 'EPSG:4326' | 'EPSG:25830';
export function normalizeRouteGeometry(input: NormalizeRouteGeometryInput): NormalizedRouteGeometry;
export function normalizeRouteAsset(input: NormalizeRouteAssetInput): NormalizedRouteGeometry;
```

`ingest.ts` must keep:

```ts
export function prepareRouteGeometryIngestion(
  input: PrepareRouteGeometryIngestionInput,
): PreparedRouteGeometryIngestion;
```

- [ ] **Step 4: Run geometry tests before the whole workspace**

```bash
pnpm --filter @magina/worker typecheck
pnpm --filter @magina/worker build
pnpm --filter @magina/worker smoke:route-geometry
pnpm check:route-geometry
```

Expected: PASS for KML, GML, KMZ, EPSG:25830 conversion and size/format guards.

- [ ] **Step 5: Fix the current #153 workspace TypeScript failure on the RC-derived branch**

Run:

```bash
pnpm typecheck
```

The existing #153 Foundation failure occurs specifically at `Typecheck workspace`; therefore no merge is allowed until this command passes on the fresh RC-derived port. Correct the concrete compiler error in the ported geometry files or package script, not by loosening TypeScript settings.

- [ ] **Step 6: Verify migration and full release gates**

```bash
pnpm check:migrations
pnpm check:fast
pnpm build
pnpm e2e:beta
```

Expected: Foundation, Full Candidate and Staging Readiness return to green on the new geometry integration SHA.

- [ ] **Step 7: Commit**

```bash
git add apps/worker/src/routes/geometry apps/worker/src/testing/route-geometry-* apps/worker/src/testing/route-source-* apps/worker/package.json database/migrations/0086_route_geometry_ingestion.sql package.json
git commit -m "feat: port official route geometry ingestion into Adventure RC1"
```

---

### Task 5: Absorb the audited route-catalogue truth from #93 selectively

**Files:**
- Data: `data/routes/sierra-magina-*.json`
- Scripts: `scripts/check-route-*.mjs`, `scripts/audit-*.mjs|py`, `scripts/discover-*.py`
- Workflows: `.github/workflows/routes-*.yml`
- Docs: `docs/routes/REDIAM_GEOMETRY_VALIDATION.md`
- Modify: `package.json`

**Interfaces:**
- Produces: audited source registry, operational status, geometry queue/blockers and CI publication gates.

- [ ] **Step 1: Compare #93 against the RC before copying anything**

```bash
git diff --name-status release/v20-magina-aventura-rc1...feat/v20-routes-catalog-completion
```

Classify every file as `already absorbed`, `new data truth`, `new gate`, or `obsolete ancestry`.

- [ ] **Step 2: Port only source-truth/data/gate files not already present**

Preserve these publication rules in code and data:

```text
publishable = validated geometry
           + coherent technical source
           + operational state compatible with use
```

Do not convert REDIAM 403, invalid TLS, missing authoritative artifacts or GR-7 maintenance warnings into a green publication state.

- [ ] **Step 3: Run catalogue gates**

```bash
pnpm check:routes-catalog
node scripts/check-route-core-enrichment.mjs
node scripts/check-route-geometry-blockers.mjs
node scripts/check-route-geometry-queue.mjs
node scripts/check-route-operational-status.mjs
node scripts/check-route-track-validation.mjs
node scripts/check-route-municipal-track-coverage.mjs
```

Expected: 17/17 official technical records remain audited, 16/16 municipality coverage remains represented, and unresolved authoritative-geometry blockers remain explicit rather than fabricated.

- [ ] **Step 4: Verify full RC**

```bash
pnpm check:fast
pnpm build
pnpm e2e:beta
```

- [ ] **Step 5: Commit**

```bash
git add data/routes scripts .github/workflows/routes-* docs/routes package.json
git commit -m "data: consolidate audited Sierra Mágina route catalogue"
```

---

### Task 6: Apply the transversal visual system without overwriting specialist Adventure UI

**Files:**
- Modify/port selectively: `apps/web/src/app/design-system.css`
- Modify/port selectively: `apps/web/src/app/layout.tsx`
- Modify/port selectively: `apps/web/src/components/topbar.tsx`
- Modify/port selectively: `apps/web/src/components/topbar.module.css`
- Modify/port selectively: `apps/web/src/components/bottom-nav.tsx`
- Modify/port selectively: `apps/web/src/components/bottom-nav.module.css`
- Reconcile only: `apps/web/src/app/aventura/adventure-hub-client.tsx`
- Reconcile only: `apps/web/src/app/aventura/page.tsx`
- Reconcile only: `apps/web/src/app/rutas/detalle/route-detail-client.tsx`
- Reconcile only: `apps/web/src/app/rutas/page.tsx`

**Interfaces:**
- Consumes: specialist Adventure UI already in RC1.
- Produces: shared shell/tokens/navigation; no new Adventure business logic.

- [ ] **Step 1: Port shell/tokens first**

Integrate `--ui-*` tokens, Topbar and BottomNav in isolation. Do not copy unrelated Almazaras, Empresas, Experiencias, Mágina Pass or Mi Campo page changes into RC1 just because they coexist in #138.

- [ ] **Step 2: Reconcile Adventure files manually**

For each overlapping Adventure/Routes file, specialist RC1 behavior wins over #138. #138 may contribute shell wrappers, spacing tokens and navigation only.

- [ ] **Step 3: Run responsive and accessibility contract**

```bash
pnpm --filter @magina/web typecheck
pnpm exec playwright test e2e/magina-adventure.spec.ts e2e/magina-adventure-weather.spec.ts
pnpm e2e:beta
```

Additionally validate viewport widths `360, 390, 430, 768, 1024, 1280, 1440, 1920` with no horizontal overflow, >=44 px principal targets, keyboard focus and reduced motion.

- [ ] **Step 4: Commit**

```bash
git add apps/web/src/app/design-system.css apps/web/src/app/layout.tsx apps/web/src/components/topbar* apps/web/src/components/bottom-nav* apps/web/src/app/aventura apps/web/src/app/rutas
git commit -m "style: unify Adventure RC1 shell and navigation"
```

---

### Task 7: Close offline, degraded GPS and connectivity states without promising native background tracking

**Files:**
- Modify: `apps/web/src/app/rutas/detalle/route-activity-recorder.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/live.module.css`
- Test: `e2e/magina-adventure.spec.ts`
- Create if required by the existing recorder contract: `apps/web/src/lib/route-activity-outbox.ts`

**Interfaces:**
- Consumes: existing recorder API and live telemetry.
- Produces: explicit `online / pending_sync / gps_denied / gps_degraded / resumed` UX states.

- [ ] **Step 1: Add E2E contracts for degraded conditions**

Use Playwright routing/geolocation to cover:

```text
GPS permission denied -> visible recovery guidance
GPS accuracy degraded -> visible degraded state, no fake precision
point upload temporary failure -> activity remains visible and pending
network restored -> pending points retry in order
screen/background -> documented limitation, not a false success state
```

- [ ] **Step 2: Reuse the current recorder state before adding storage**

Only create `route-activity-outbox.ts` if the current recorder loses unsent points during a transient network failure. If needed, use bounded IndexedDB storage keyed by activity id and ordered timestamp; clear acknowledged batches after successful sync.

- [ ] **Step 3: Do not add full offline map caching in RC1**

No tile-cache or service-worker map promise enters RC1 unless provider licensing/cache policy is explicitly verified. Offline UI must say when the basemap is unavailable while preserving validated route data already loaded/cached by the app.

- [ ] **Step 4: Verify**

```bash
pnpm --filter @magina/web typecheck
pnpm exec playwright test e2e/magina-adventure.spec.ts
pnpm check:fast
pnpm build
```

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/app/rutas/detalle/route-activity-recorder.tsx apps/web/src/app/aventura/en-curso apps/web/src/lib/route-activity-outbox.ts e2e/magina-adventure.spec.ts
git commit -m "feat: harden Adventure GPS and connectivity states"
```

If `route-activity-outbox.ts` is not needed, omit it from `git add`.

---

### Task 8: Verify Adventure progression, collections, community and Admin contracts

**Files:**
- Verify/modify only on failing tests: `apps/web/src/app/aventura/**`
- Verify/modify only on failing tests: Adventure API/admin route files already present in the candidate.
- Test: `e2e/magina-adventure.spec.ts`
- Test: relevant Admin E2E/check scripts already in repository.

**Interfaces:**
- Progression: levels 1–10, XP, badges, expeditions, kilometres conquered, explored territory.
- Collections: Flora, Fauna, Patrimonio, Olivar, Tradiciones, Paisaje.
- Community: real approved content only.
- Admin: permission-safe management of Adventure/routing content.

- [ ] **Step 1: Add/confirm one E2E assertion per RC1 contract**

Verify the UI can represent all six collection categories and does not render synthetic activity when APIs return empty datasets.

- [ ] **Step 2: Verify completion idempotency**

Run existing API/integration coverage for Adventure completion twice against the same logical run and assert no duplicate XP/badges/progress rows are produced.

- [ ] **Step 3: Verify Admin permissions**

Run Platform Admin and Admin Governance checks and confirm a non-admin session cannot mutate Adventure management surfaces.

- [ ] **Step 4: Full gate**

```bash
pnpm check:fast
pnpm build
pnpm e2e:beta
```

Commit only if a real contract gap required code changes.

---

### Task 9: Cut the single-SHA RC1 candidate and run the complete release matrix

**Files:**
- Update: `docs/superpowers/plans/2026-09-16-magina-aventura-rc1.md` checkboxes/status only after evidence exists.
- Create: `docs/v20/MAGINA_AVENTURA_RC1_STATUS.md`

**Interfaces:**
- Produces: one immutable candidate SHA with documented evidence.

- [ ] **Step 1: Run the complete local matrix**

```bash
pnpm install --frozen-lockfile
pnpm check:runtime
pnpm check:lockfile
pnpm check:env
pnpm check:staging
pnpm check:migrations
pnpm check:containers
pnpm check:admin-unified
pnpm check:routes-catalog
pnpm typecheck
pnpm check:route-geometry
pnpm build
pnpm exec playwright test e2e/magina-adventure.spec.ts e2e/magina-adventure-weather.spec.ts
pnpm e2e:beta
```

- [ ] **Step 2: Push and record the exact SHA**

```bash
git rev-parse HEAD
git push origin release/v20-magina-aventura-rc1
```

- [ ] **Step 3: Require same-SHA remote green**

Do not accept mixed evidence from ancestor SHAs. Record every required GitHub Action name/run against the exact RC SHA.

- [ ] **Step 4: Write RC status**

`MAGINA_AVENTURA_RC1_STATUS.md` must list:
- exact SHA;
- integrated specialist deltas;
- checks green on that SHA;
- known non-software/field limitations;
- staging deployment state;
- explicit statement that `main` remains untouched.

---

### Task 10: Deploy real staging and run deployed smoke journeys

**Files:**
- Existing: `deploy/staging/**`
- Existing: environment/staging contract scripts.
- Update: `docs/v20/MAGINA_AVENTURA_RC1_STATUS.md` with evidence only.

**Interfaces:**
- Consumes: RC1 SHA and deployment secrets stored in GitHub Environment/host configuration.
- Produces: HTTPS staging deployment with actual Adventure dependencies.

- [ ] **Step 1: Validate deployment prerequisites without exposing secrets**

Verify configured environment values for the staging host, URL, SSH, PostgreSQL/PostGIS, required media/storage, auth and weather/radar access.

- [ ] **Step 2: Deploy the exact RC SHA**

Use the repository staging workflow; do not rebuild/deploy a different unrecorded commit.

- [ ] **Step 3: Run deployed smoke journeys**

At minimum:

```text
Inicio Aventura -> Rutas -> Ficha -> Preparar -> Aventura en curso
GPS permission path
weather activation path
route track/checkpoint visibility
empty community state
authenticated progress/profile path
Admin authorization path
```

- [ ] **Step 4: Verify persistence and operational dependencies**

Confirm migrations, PostGIS geometry reads, weather access, API/worker health, media needed by Adventure, HTTPS and backup/restore path.

---

### Task 11: Execute the Sierra Mágina field-validation gate

**Files:**
- Create: `docs/v20/MAGINA_AVENTURA_FIELD_QA.md`
- Update: `docs/v20/MAGINA_AVENTURA_RC1_STATUS.md`

**Interfaces:**
- Produces: field evidence separate from software CI.

- [ ] **Step 1: Select an operationally open, validated Bedmar route**

Do not perform the field run if the authoritative operational state reports closure or incompatible safety conditions.

- [ ] **Step 2: Record physical observations**

Document:

```text
GPS acquisition time
practical position accuracy
map recenter usability
checkpoint trigger behaviour
recorded distance plausibility
ascent/elevation plausibility
weather + GPS coexistence
battery consumption
screen-lock behaviour
background behaviour
poor/no coverage behaviour
resume/recovery
completion/progression sync
safety/closure presentation
```

- [ ] **Step 3: Classify every field result**

Use only `pass`, `fail`, or `platform limitation accepted`. Do not reinterpret a browser limitation as a pass.

- [ ] **Step 4: Fix software defects on RC1 and repeat affected gates**

Every code fix after field testing invalidates the previous SHA evidence. Cut a new RC SHA and rerun Task 9 for affected/full gates before release.

---

### Task 12: Release decision and branch hygiene

**Files:**
- Update: `docs/v20/MAGINA_AVENTURA_RC1_STATUS.md`
- No code changes unless a release blocker remains.

- [ ] **Step 1: Confirm Definition of Done**

The release status may move from RC to external Beta only when:

```text
one RC branch contains the intended Adventure feature set
all required automated gates are green on one SHA
real staging is deployed and smoke-tested
route/source/safety rules remain intact
GPS/weather/checkpoints work together
progression/community/admin contracts are verified
mobile/tablet/desktop QA is closed
field QA is green or explicit platform limitations are accepted/documented
main was not used as an integration shortcut
```

- [ ] **Step 2: Close or label superseded specialist PRs**

After their useful delta is in RC1, mark old stacked/ancestor PRs as superseded rather than leaving them looking like alternative release trunks.

- [ ] **Step 3: Preserve `main` until the explicit promotion decision**

RC1 completion is not implicit authorization to merge to `main`.

---

## Spec Coverage Self-Review

- Product boundary / Adventure-only release: Tasks 1, 8, 12.
- Premium Adventure + routes + live GPS: Tasks 1–2.
- Weather Engine + visuals + reduced motion: Tasks 1, 3.
- Official KML/GML/KMZ/PostGIS ingestion: Task 4.
- Audited route truth / closures / publication gate: Task 5.
- Unified visual shell and viewport/accessibility QA: Task 6.
- Offline/poor network/GPS-denied/background boundary: Task 7.
- Levels/XP/badges/collections/community/Admin: Task 8.
- Same-SHA CI release gate: Task 9.
- Real staging: Task 10.
- Physical GPS/battery/background/no-coverage validation: Task 11.
- Release hygiene and no-`main` shortcut: Task 12.

No requirement from the approved RC1 design is intentionally deferred outside this plan except native/Capacitor implementation, which remains out of RC1 unless independently implemented and verified in-repo.