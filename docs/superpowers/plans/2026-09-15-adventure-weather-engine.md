# Mágina Aventura Weather Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real coordinate-level weather to Mágina Aventura, a preparation screen, and a dynamic weather HUD/visual layer while preserving the existing AEMET/radar stack.

**Architecture:** Extend `@magina/weather` with a stable `WeatherState` and Open-Meteo adapter. Expose the normalized state through the existing Fastify weather area, then consume it from the Next.js web app. The renderer stays web-specific; provider/domain logic stays reusable for the future mobile app.

**Tech Stack:** TypeScript 6, pnpm 10, Fastify, Next.js, Playwright, existing `@magina/weather`, AEMET OpenData, Open-Meteo.

**Spec:** `docs/superpowers/specs/2026-09-15-adventure-weather-engine-design.md`

## Global Constraints
- Do not touch `main`.
- Preserve GPS privacy, validated route navigation, checkpoint/XP behavior and official-warning precedence.
- Do not render invented weather values in production.
- Respect `prefers-reduced-motion`.
- Keep provider payloads out of UI components.
- Required final gates: TypeScript/build, Adventure, Activity, Full Candidate, Browser E2E, Environment and Staging green on one HEAD.

---

### Task 1: WeatherState domain + Open-Meteo adapter

**Files:**
- Create: `packages/weather/src/weather-state.ts`
- Create: `packages/weather/src/open-meteo.ts`
- Create: `packages/weather/src/testing/weather-state-smoke.ts`
- Modify: `packages/weather/src/index.ts`

**Interfaces:**
- Produces `WeatherState`, `classifyWeatherState(input)`, `fetchOpenMeteoCurrentWeather(lat, lon)`.

- [ ] **Step 1: Write failing smoke assertions** covering rain/storm/fog/snow/wind/day-night classification and latitude/longitude validation.
- [ ] **Step 2: Verify the smoke file fails to compile because the new exports do not exist.**
- [ ] **Step 3: Implement the minimal normalized types/classifier and Open-Meteo parser/fetcher.**
- [ ] **Step 4: Run `pnpm --filter @magina/weather typecheck && pnpm --filter @magina/weather build`.**
- [ ] **Step 5: Commit the domain slice.**

### Task 2: Coordinate weather API

**Files:**
- Create: `apps/api/src/weather/current-provider.ts`
- Modify: `apps/api/src/routes/weather.ts`
- Modify: `apps/api/src/app.ts`

**Interfaces:**
- Adds `GET /api/v1/public/weather/current?lat=<number>&lon=<number>` returning `{ weather: WeatherState, cache_status, fetched_at }`.
- Uses an injectable provider so CI/E2E never depends on live Open-Meteo.

- [ ] **Step 1: Add a failing API-level contract assertion/test fixture for invalid coordinates and normalized weather output.**
- [ ] **Step 2: Confirm failure before endpoint implementation.**
- [ ] **Step 3: Add provider interface, coordinate validation and endpoint.**
- [ ] **Step 4: Run API typecheck/build and the relevant route checks.**
- [ ] **Step 5: Commit.**

### Task 3: Typed web weather source + development override

**Files:**
- Create: `apps/web/src/lib/weather-source.ts`

**Interfaces:**
- Produces `loadCurrentWeather(latitude, longitude)` and development-only override parsing.

- [ ] **Step 1: Extend Playwright mocks so the intended `/public/weather/current` request is observable and initially absent.**
- [ ] **Step 2: Confirm test fails on missing weather UI/request.**
- [ ] **Step 3: Implement typed source and safe error behavior.**
- [ ] **Step 4: Run web typecheck.**
- [ ] **Step 5: Commit.**

### Task 4: Preparar aventura screen

**Files:**
- Create: `apps/web/src/app/aventura/preparar/page.tsx`
- Create: `apps/web/src/app/aventura/preparar/adventure-prepare-client.tsx`
- Create: `apps/web/src/app/aventura/preparar/prepare.module.css`
- Modify: `apps/web/src/app/rutas/detalle/route-detail-client.tsx`
- Modify: `e2e/magina-adventure.spec.ts`

**Interfaces:**
- Route detail links to `/aventura/preparar?slug=...`.
- Screen requests geolocation only on user action, shows route facts, live weather when available, explicit stale/unavailable state, and links to start `/aventura/en-curso?slug=...`.

- [ ] **Step 1: Add failing mobile E2E for prepare screen and route-detail CTA.**
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement the screen and responsive styling.**
- [ ] **Step 4: Run focused Playwright + web typecheck/build.**
- [ ] **Step 5: Commit.**

### Task 5: Weather HUD + visual layer in active adventure

**Files:**
- Create: `apps/web/src/app/aventura/en-curso/adventure-weather.tsx`
- Create: `apps/web/src/app/aventura/en-curso/adventure-weather.module.css`
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `e2e/magina-adventure.spec.ts`

**Interfaces:**
- `AdventureWeather` owns opt-in location/weather refresh and renders `WeatherHUD` + `WeatherVisualLayer`.
- Visual layer uses `data-weather-condition` and `data-weather-intensity` for deterministic testing.

- [ ] **Step 1: Add failing E2E assertions for HUD, rain visual state and reduced-motion-safe markup.**
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement HUD/visual states for clear/cloudy/rain/storm/fog/snow/wind and day/dusk/night ambience.**
- [ ] **Step 4: Run focused Playwright, typecheck and build.**
- [ ] **Step 5: Commit.**

### Task 6: Final QA and promotion readiness

**Files:**
- Modify docs only if QA reveals contract differences.

- [ ] **Step 1: Run all project gates triggered by the PR HEAD.**
- [ ] **Step 2: If a gate fails, inspect its job/log, fix the smallest root cause, and rerun.**
- [ ] **Step 3: Check desktop 1440, tablet and mobile 390 layouts through E2E/no-overflow coverage.**
- [ ] **Step 4: Confirm no provider key is exposed and no production mock/fallback weather is rendered.**
- [ ] **Step 5: Keep the PR Draft until every gate is green on the same HEAD.**
