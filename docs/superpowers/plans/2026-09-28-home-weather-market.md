# Inicio: tiempo y mercado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify Home around the farm weather and a compact official oil-market chart while keeping existing destinations and detail screens intact.

**Architecture:** Extend the existing weather Edge Function contract additively; represent/cache the optional daily forecast beside current weather with the existing offline-first feed; add a nested week screen. Recompose Home in Compose, reusing its existing official market series/chart and keeping AOVE.net pulse on the existing market detail.

**Tech Stack:** Kotlin, Jetpack Compose, Room JSON-text cache, Supabase Edge Function (TypeScript/Deno), existing Gradle and Deno test harnesses.

**Spec:** `docs/00-master/RC1.2-CHANGE-REQUEST-009-HOME-WEATHER-MARKET.md`

## Global Constraints

- Preserve the root navigation: `Inicio · Mi Campo · Cuaderno · Avisos · Perfil`.
- Do not delete farm creation, quick-action destinations, market detail, radar, or existing records.
- Keep the weather source chain AEMET → MET Norway and show the provider that produced the complete response.
- Do not mix provider data, infer missing values, interpolate market observations, or misstate source freshness.
- Keep AOVE.net daily pulse separate from the Junta weekly oil-market series.
- No GPS/permission/municipality selector, Room migration, new market source, or Supabase deployment in this PR.
- Preserve support for previously cached current-only weather responses.
- No merge until explicit owner approval; Gate 20 remains open until a new APK is checked on a physical phone.

## Review Focus

- Old cache rows without a weekly forecast must still decode as valid current weather.
- Partial provider daily data must keep missing values null, not turn them into zero.
- Multiple farm municipalities must remain unresolved rather than silently selecting a town.
- Slow/offline refresh must keep the last cache visible with its age.
- Overlay text/controls over the hero image must remain readable and touchable on light/dark images.

---

### Task 1: Weekly forecast contract and provider parsing

**Files:**
- Modify: `supabase/functions/weather-forecast/contract.ts`
- Modify: `supabase/functions/weather-forecast/handler.ts`
- Modify: `supabase/functions/weather-forecast/aemet.ts`
- Modify: `supabase/functions/weather-forecast/metno.ts`
- Modify: `supabase/functions/weather-forecast/forecast.test.ts`
- Create: `supabase/functions/weather-forecast/fixtures/aemet-diaria-bedmar.json`

**Interface:** Add `DailyForecast[]` as an additive `WeatherResponse.daily` property (maximum seven local dates). Each row carries ISO date and nullable `minTemperatureC`, `maxTemperatureC`, condition, rain probability, rain amount (mm) and wind speed (km/h). Provider response is valid as a whole; if one provider cannot supply a usable current + daily response, continue the existing provider fallback rather than combine sources.

- [ ] Write fixture-based tests for AEMET daily mapping, MET Norway daily aggregation, seven-day maximum, nullable fields, and same-provider fallback.
- [ ] Run `node --experimental-strip-types --test supabase/functions/weather-forecast/forecast.test.ts` and confirm the new tests fail for the missing contract/parser.
- [ ] Implement minimal parsers against committed fixtures; include only provider observations and keep omitted measures null.
- [ ] Re-run `node --experimental-strip-types --test supabase/functions/weather-forecast/forecast.test.ts`; confirm pass.
- [ ] Commit as `feat(weather): return attributed daily forecast`.

### Task 2: Android model, response parsing and cache compatibility

**Files:**
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/domain/weather/Weather.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/remote/weather/EdgeWeatherSource.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/CachedWeatherFeed.kt` (only if cache wrapping needs an additive field)
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/remote/EdgeWeatherResponseTest.kt`
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/WeatherFeedContractTest.kt`

**Interface:** Add a daily forecast value type and optional forecast list to the existing cached weather value without changing Room schema. An absent `daily` property in old server/cache payloads means “week not cached,” not a failed current-weather row.

- [ ] Add failing tests for legacy current-only response, full forecast, partial nullable day, cache round-trip and stale cached week.
- [ ] Run the smallest Android test target and confirm expected missing-model/parse failures.
- [ ] Implement the additive model/codec and parser; retain the existing cache key and freshness rules.
- [ ] Run focused Android tests and confirm legacy and new cache representations pass.
- [ ] Commit as `feat(weather): cache optional daily forecast`.

### Task 3: Weekly weather destination and navigation

**Files:**
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/feature/home/WeatherWeekScreen.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/navigation/AppNavigation.kt`
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/AppNavigationTest.kt`
- Create/modify: an Android screen test under `app/src/androidTest/java/com/isivoltpro/maginaolivo/` for weekly weather.

**Interface:** A nested Home route reuses the existing `WeatherFeed`; each day shows its date, condition and available min/max/rain/wind fields. The screen shows the resolved municipality, actual provider, provider update time, fetch age, cached/offline state and a button to the existing radar route.

- [ ] Add failing navigation and UI assertions for 7-day list, unavailable values, source/location, radar navigation, back behavior, cached offline week, and no-cache state.
- [ ] Run the focused Android instrumentation tests and confirm they fail because the route/screen is absent.
- [ ] Implement the route and screen using existing design tokens and navigation shell; do not add location selection or new location permissions.
- [ ] Run the focused Android tests and confirm each weather state and route passes.
- [ ] Commit as `feat(weather): add weekly forecast screen`.

### Task 4: Home composition and compact official market preview

**Files:**
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/feature/home/OilMarketCard.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/feature/home/HomeViewModel.kt` only if callback/state wiring requires it.
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/HomeFeedsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/HomeOilMarketScreenTest.kt`

**Interface:** Home receives a week-open callback. The photo header gains an accessible, high-contrast weather button showing actual current data/location or a concise honest empty/offline state. The market preview uses official `OilMarketSeries` points and the established three categories; tapping opens the existing detailed market screen where the daily widget stays.

- [ ] Add failing tests that assert no first-farm card or quick-access grid remains, weather is tappable over hero and opens week, and market preview has legend/source/period and does not render the daily pulse.
- [ ] Run focused Home instrumentation tests and confirm failures against current Home.
- [ ] Implement the re-composition; keep farm creation discoverable in Mi Campo and retain campaign/upcoming-work/cooperative content not targeted here.
- [ ] Run Home and market screen tests; confirm empty, populated, loading, unavailable and stale states.
- [ ] Commit as `feat(home): surface weather and compact market chart`.

### Task 5: Gate checklist, integration verification and PR evidence

**Files:**
- Modify: `docs/06-testing/PHASE20-GATE-CHECKLIST.md`
- Modify: `docs/00-master/CURRENT-STATE.md` only if the repository's phase state requires a precise CR reference.
- Modify: `docs/CHANGELOG-APP.md`
- Modify: `docs/00-master/RC1.2-CHANGE-REQUEST-009-HOME-WEATHER-MARKET.md` to record final status/results.

- [ ] Add B4 weekly forecast and D5 cached-week offline checks without removing current Gate 20 checks.
- [ ] Run `node --experimental-strip-types --test supabase/functions/weather-forecast/forecast.test.ts`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` and `./gradlew :app:assembleDebug`.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` when an emulator is available; record clearly if this environment has none.
- [ ] Install in a clean emulator, capture Home, open week/radar/market, verify Mi Campo and Cuaderno destinations, airplane mode, close/reopen and crash log.
- [ ] Record exact results, unavailable external deployment/device limitations and APK artifact/commit in PR #284; do not claim physical Gate 20 passed.
- [ ] Commit as `docs: add weekly weather to Gate 20 validation` and push updates to PR #284.

## Deployment and release gate

The PR may include versioned Edge Function source but must not deploy it. A remotely functional seven-day forecast depends on a separately authorized deployment of `weather-forecast`; until then, label the detail honestly when the endpoint does not yet return `daily`. The updated APK must then be installed and the new B4/D5 checks repeated on the owner's physical phone before Gate 20 can close.
