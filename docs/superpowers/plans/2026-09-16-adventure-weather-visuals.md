# Mágina Aventura Weather Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a platform-neutral weather visual contract and a premium, non-blocking web ambience renderer for Mágina Aventura without changing weather classification, GPS permissions, provider access, refresh cadence, or AEMET safety precedence.

**Architecture:** `WeatherState` remains authoritative. A pure `buildWeatherVisualModel()` maps trusted state into presentation-only normalized parameters. The Next.js adventure screen consumes that model through a decorative `WeatherScene`; future React Native Skia/particles/Rive renderers will consume the same model.

**Tech Stack:** TypeScript 6, Node test runner, React 19, Next.js, CSS Modules, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-16-adventure-weather-visuals-design.md`

## Global Constraints

- Work only on `feat/v20-adventure-weather-visuals`; do not modify `main`.
- Base is validated Weather Engine commit `a417794337dacea797d287beaa1ba59d7d6f4974` plus docs commits on this branch.
- `WeatherState` stays authoritative for condition, intensity, metrics, day phase, official alerts, and stale state.
- Do not change Open-Meteo/AEMET integration, GPS permission flow, or the 10-minute refresh cadence.
- Do not install React Native Skia or `rive-react-native` in the web app.
- Decorative rendering must be `aria-hidden`, non-interactive, below HUD/safety/navigation layers, and must never invent warning copy.
- `prefers-reduced-motion: reduce` must disable continuous motion and lightning.
- Production users must not be able to enable simulated weather.
- Every production change follows RED → GREEN → refactor with a real failing test/check first.

---

### Task 1: Shared `WeatherVisualModel` contract and pure builder

**Files:**
- Create: `packages/weather/src/testing/weather-visual-model.test.ts`
- Create: `packages/weather/src/weather-visual-model.ts`
- Modify: `packages/weather/src/index.ts`

**Interfaces:**
- Consumes: `WeatherState` from `weather-state.ts`.
- Produces: `WeatherPerformanceTier`, `WeatherSkyPreset`, `WeatherAmbientTone`, `WeatherVisualModel`, `buildWeatherVisualModel(weather, options?)`.

- [ ] **Step 1: Write failing package tests** covering clear/day/night, cloudy intensities, rain intensity and bounded angle, storm lightning+safety, fog, snow, wind+leaves, official-alert safety, reduced motion, minimal tier, and deterministic identical output.
- [ ] **Step 2: Verify RED** through the Weather Engine package test workflow; expected failure is missing `../weather-visual-model.js` / missing export.
- [ ] **Step 3: Implement minimal pure builder** with normalized `0..1` amounts, deterministic presets, transition duration rules, no mutation, and no provider access.
- [ ] **Step 4: Export from package public API.**
- [ ] **Step 5: Verify GREEN** with weather package tests, typecheck, and build.
- [ ] **Step 6: Commit** `feat(weather): add visual weather model`.

### Task 2: Web `WeatherScene` shell and browser adaptation

**Files:**
- Create: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene-model.ts`
- Create: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene.tsx`
- Create: `apps/web/src/app/aventura/en-curso/weather-scene/weather-scene.module.css`
- Create/extend test coverage under `e2e/magina-adventure-weather.spec.ts` only where browser behavior is needed.
- Modify: `apps/web/src/app/aventura/en-curso/adventure-weather.tsx`

**Interfaces:**
- Consumes: `AdventureWeatherState` and `buildWeatherVisualModel()`.
- Produces: decorative `WeatherScene` with `data-weather-condition`, `data-weather-intensity`, `data-weather-tier`, and no interactive descendants.

- [ ] **Step 1: Add failing browser assertions** for an `aria-hidden` scene and preserved HUD/safety controls.
- [ ] **Step 2: Verify RED** in Browser E2E.
- [ ] **Step 3: Add the scene shell** with pointer transparency, CSS-only minimal fallback, and HUD-independent placement.
- [ ] **Step 4: Integrate into `AdventureWeather`** without changing GPS/refresh/safety logic.
- [ ] **Step 5: Verify GREEN** in web typecheck/build and focused E2E.
- [ ] **Step 6: Commit** `feat(adventure): add weather scene shell`.

### Task 3: Cloud, fog, ambient tint and phase transitions

**Files:**
- Modify: `weather-scene.tsx`
- Modify: `weather-scene.module.css`
- Test: weather E2E and package visual-model tests if mapping changes.

**Interfaces:**
- Consumes: `skyPreset`, `ambientTone`, `ambientOpacity`, `cloudDensity`, `fogDensity`, `transitionDurationMs`.
- Produces: CSS-only atmospheric background that works in all performance tiers.

- [ ] **Step 1: Add failing assertions/data hooks** for cloudy/fog/night/golden-hour presets.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement sky, cloud, fog and ambient layers** with bounded CSS variables.
- [ ] **Step 4: Add transition timing variables** without an unbounded animation loop.
- [ ] **Step 5: Verify GREEN** including reduced-motion snapshot/state assertions.
- [ ] **Step 6: Commit** `feat(adventure): render atmospheric weather layers`.

### Task 4: Rain and snow particle renderer

**Files:**
- Create: `apps/web/src/app/aventura/en-curso/weather-scene/weather-particles.tsx`
- Modify: `weather-scene.tsx`
- Modify: `weather-scene.module.css`
- Test: focused weather E2E.

**Interfaces:**
- Consumes: `rainAmount`, `rainAngleDeg`, `snowAmount`, `performanceTier`, `reducedMotion`.
- Produces: deterministic decorative particles for full/balanced tiers; static texture/fallback for minimal or reduced motion.

- [ ] **Step 1: Add failing assertions** for rain/snow particle mode vs static mode.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement deterministic particle descriptors** derived from fixed indexes, not random runtime state.
- [ ] **Step 4: Disable continuous particle drift** for minimal/reduced-motion modes.
- [ ] **Step 5: Verify GREEN** and ensure no focusable descendants.
- [ ] **Step 6: Commit** `feat(adventure): add rain and snow ambience`.

### Task 5: Wind, olive leaves, dust and lightning

**Files:**
- Create: `apps/web/src/app/aventura/en-curso/weather-scene/weather-lightning.tsx`
- Modify: `weather-particles.tsx`
- Modify: `weather-scene.tsx`
- Modify: `weather-scene.module.css`
- Test: focused weather E2E.

**Interfaces:**
- Consumes: `windStrength`, `leafAmount`, `dustAmount`, `lightningAmount`, `performanceTier`, `reducedMotion`.
- Produces: Sierra Mágina decorative ambience and isolated storm flashes with zero semantic weather meaning.

- [ ] **Step 1: Add failing assertions** for authoritative wind leaves, clear-day dust, storm lightning, and no lightning in reduced motion.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement leaf/dust particle families** with balanced-tier limits.
- [ ] **Step 4: Implement isolated lightning** with no continuous loop in minimal/reduced-motion modes.
- [ ] **Step 5: Verify GREEN.**
- [ ] **Step 6: Commit** `feat(adventure): add Magina weather ambience`.

### Task 6: Performance tier and reduced-motion browser policy

**Files:**
- Modify: `weather-scene-model.ts`
- Modify: `weather-scene.tsx`
- Modify: `weather-particles.tsx`
- Modify: `weather-lightning.tsx`
- Test: focused E2E.

**Interfaces:**
- Produces browser-resolved default `balanced` tier and reduced-motion override.

- [ ] **Step 1: Add failing tests** for default balanced tier, minimal static fallback, and reduced-motion zero transition/zero continuous animation.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement browser policy** using `matchMedia('(prefers-reduced-motion: reduce)')` without exposing a user selector.
- [ ] **Step 4: Verify GREEN** and web build.
- [ ] **Step 5: Commit** `feat(adventure): adapt weather visuals to device motion policy`.

### Task 7: Expand local Weather Lab

**Files:**
- Modify: `apps/web/src/app/aventura/en-curso/adventure-weather.tsx`
- Modify: `e2e/magina-adventure-weather.spec.ts`

**Interfaces:**
- Produces presets: clear day, golden hour, cloudy light, overcast, rain light/medium/heavy, storm medium/heavy, fog light/dense, snow light/heavy, wind light/strong, night clear.

- [ ] **Step 1: Add failing E2E tests** for all required preset labels and representative visual data attributes.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Expand presets** while preserving localhost/127.0.0.1 + `weatherLab=1` gate.
- [ ] **Step 4: Prove simulation never requests geolocation/network weather API** before explicit live activation.
- [ ] **Step 5: Verify GREEN.**
- [ ] **Step 6: Commit** `feat(adventure): expand weather visual lab`.

### Task 8: Final regression and CI closure

**Files:**
- Modify only if a verified regression is found.
- Update plan checkboxes and PR description after evidence is available.

- [ ] **Step 1: Run/observe same-HEAD package tests, web typecheck, web build, Weather Engine check, Full Candidate, Browser E2E, Environment, Staging, Foundation/Admin gates triggered by the diff.**
- [ ] **Step 2: If a gate fails, inspect the exact job/log/artifact and fix only the root cause using TDD.**
- [ ] **Step 3: Re-run until the final HEAD is green across all required gates.**
- [ ] **Step 4: Verify production host cannot enable Weather Lab and no provider key/raw payload is exposed.**
- [ ] **Step 5: Keep the PR Draft until all evidence is green on the same HEAD.**
- [ ] **Step 6: Commit documentation-only closure if needed** `docs: record weather visuals verification`.
