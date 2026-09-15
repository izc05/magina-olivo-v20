# Mágina Aventura Weather Engine Implementation Plan

> Execute task-by-task on `feat/v20-adventure-weather-engine`; do not touch `main`.

**Goal:** Add real coordinate-level weather, official AEMET warnings, a preparation screen and a dynamic weather HUD/visual layer while preserving the existing AEMET/radar stack.

**Architecture:** `Open-Meteo + AEMET CAP + existing radar -> @magina/weather -> WeatherState -> Fastify API -> Next.js renderer`. Provider/domain logic stays reusable for the future mobile renderer with Skia/particles/Rive.

**Spec:** `docs/superpowers/specs/2026-09-15-adventure-weather-engine-design.md`

## Global constraints
- Preserve GPS privacy, validated route navigation, checkpoint/XP behavior and official-warning precedence.
- Do not render invented weather values in production.
- Respect `prefers-reduced-motion`.
- Keep provider payloads out of UI components.
- Required final gates: TypeScript/build, Adventure, Activity, Full Candidate, Browser E2E, Environment and Staging green on one HEAD.

### Task 1 — WeatherState + Open-Meteo
- [x] Write contract tests for storm/rain/fog/snow/wind/day-night and coordinate validation.
- [x] Add `packages/weather/src/weather-state.ts`.
- [x] Add `packages/weather/src/open-meteo.ts`.
- [x] Export the domain from `@magina/weather`.
- [ ] Verify package test/typecheck/build green on final HEAD.

### Task 2 — Privacy-safe coordinate weather API
- [x] Write API contract tests before endpoint implementation.
- [x] Add injectable current-weather provider with 10-minute ephemeral cache and stale fallback.
- [x] Add `POST /api/v1/public/weather/current` with coordinates in JSON body, `Cache-Control: no-store` and no coordinate echo in the response.
- [x] Add weather tests to root quality gates.
- [ ] Verify API tests/typecheck/build green on final HEAD.

### Task 3 — Official AEMET warnings
- [x] Confirm official current-warning endpoint `/api/avisos_cap/ultimoelaborado/area/{area}` and Andalucía code `61`.
- [x] Write CAP parsing/coordinate-selection tests.
- [x] Parse Spanish CAP blocks, Meteoalerta level and warning polygons; ignore green/no-warning messages.
- [x] Merge the highest relevant official warning into `WeatherState` without allowing AEMET failure to break Open-Meteo conditions.
- [ ] Verify CAP test/typecheck/build green on final HEAD.

### Task 4 — Typed web source + Preparar aventura
- [x] Add `apps/web/src/lib/weather-source.ts`.
- [x] Add failing Playwright coverage before the UI.
- [x] Add `/aventura/preparar?slug=...` with route facts, explicit GPS opt-in, live weather, stale/unavailable states and safety notes.
- [x] Link route detail to `Preparar aventura`.
- [x] Weather failure does not block starting the route.
- [ ] Verify mobile/desktop E2E and build green on final HEAD.

### Task 5 — Live Weather HUD + dynamic renderer
- [x] Add Weather HUD to `Aventura en curso`.
- [x] Add deterministic visual states for clear/cloudy/rain/storm/fog/snow/wind and day/dusk/night ambience.
- [x] Add 10-minute refresh after explicit opt-in.
- [x] Keep all effects pointer-transparent and reduce/disable animation under `prefers-reduced-motion`.
- [x] Give the weather HUD its own desktop grid row so it cannot collide with map/safety panels.
- [ ] Verify E2E/typecheck/build green on final HEAD.

### Task 6 — Local weather laboratory
- [x] Write E2E contract for local simulation without GPS/API calls.
- [x] Add `?weatherLab=1` laboratory restricted to localhost/127.0.0.1.
- [x] Add presets for sun, cloud, light/heavy rain, storm, fog, snow, wind and night.
- [x] Label simulated weather explicitly so it cannot be mistaken for real data.
- [ ] Verify E2E green on final HEAD.

### Task 7 — Final QA and promotion readiness
- [ ] Freeze one final HEAD and let all triggered workflows finish rather than pushing through them.
- [ ] If any gate fails, inspect the failing job/log, repair the smallest root cause and repeat.
- [ ] Confirm responsive/no-horizontal-overflow coverage at mobile 390 and desktop/tablet layouts.
- [ ] Confirm no provider key is exposed and production cannot enable simulated weather.
- [ ] Keep PR #148 Draft until every required gate is green on the same HEAD.
