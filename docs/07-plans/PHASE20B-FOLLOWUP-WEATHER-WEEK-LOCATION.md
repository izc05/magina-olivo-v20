# Phase 20B follow-up — 7-day forecast, freshness and weather location (plan)

Status: **PROPOSED — documentation only, nothing implemented** (2026-09-28).
Origin: Codex review on #281 (APK 0.5.0 build 574/575) and the owner's approved criterion quoted there.
Production code for any slice below starts only with the owner's explicit authorization.

## 1. What exists today (verified in `main` bc3a1b7b)

| Piece | Today |
|---|---|
| Edge Function `weather-forecast` | Returns **one** reading: `current` (`validAt`, °C, condition, rain probability or `null`, wind or `null`) plus `updatedAt` (provider) and `fetchedAt` (function). AEMET hourly product (`aemet-horaria-bedmar.json` fixture) → MET Norway `locationforecast/2.0/compact`. |
| Android model | `WeatherNow` (current only). Cached in `weather_cache.payload_json` (no Room change needed to add fields). |
| Freshness | `FeedKind.WEATHER` is stale after **3 h since the app fetched it** (`fetchedAt`). The label «Actualizado hace …» reads the **provider's** `updatedAt` (`HomeScreen.kt:307`). |
| Location | Inferred from the Farms (`FeedLocation.common`): only when every Farm shares one municipality. No preferred municipality; no GPS for weather. |
| Inicio | Weather card not tappable; header photo generic, no municipality in the header. |

### Why Codex saw «Actualizado hace 10 h» without «Desactualizado»
Both facts are true at once: AEMET **produced** that forecast 10 h earlier (label), but the app
**fetched** it less than 3 h earlier (stale rule). The screen mixes two clocks under one word.
Nothing is wrong with the data; the wording is. Fix proposed in W1.

## 2. Owner criterion (from #281, to be preserved)
- One shared municipality for header/photo and weather; manual choice kept; «Seguir mi ubicación»
  only with explicit permission, foreground refresh only, never hidden background tracking.
- Tapping the weather opens the **next 7 days**: min/max, rain and wind where the provider gives
  them, source and age. Missing values stay unknown; no sample forecast (the reference screen's
  week is a mock-up and must not be reused).
- Keep the current weather look and its accessibility safeguards.

## 3. Proposed slices

### W1 — 7-day forecast + honest freshness (20B follow-up, no Room change)
- **Function contract (additive, backwards compatible):** `daily: DailyForecast[]` (up to 7), each
  `{ date, minC|null, maxC|null, condition|null, rainProbabilityPercent|null, rainMm|null, windKmh|null }`.
  Old clients ignore it; `current` unchanged.
  - AEMET: the municipal **daily** product (`/prediccion/especifica/municipio/diaria/{code}`) gives
    max/min, sky, precipitation probability by period and wind. *Pending to verify with a fixture
    captured from the live endpoint before coding (no fixture exists yet).*
  - MET Norway compact: min/max and a symbol are derivable per day from the timeseries; the compact
    product has **no precipitation probability** → `rainProbabilityPercent: null`, and at most an
    amount (`rainMm`) where `next_6_hours`/`next_12_hours` give one. The existing
    `metno-compact.json` fixture has only 2 hourly steps (it carries `precipitation_amount`), so a
    new multi-day fixture must be captured before coding.*
  - One provider answers the whole response (no mixing AEMET days with MET Norway days).
- **App:** `WeatherNow` gains `daily`; «Tiempo» card becomes tappable → new nested route
  «Próximos días» (under Inicio, like «Mercado del aceite»): 7 rows, unknown shown as «—»,
  source + attribution + both clocks.
- **Freshness wording:** «Previsión AEMET de las 06:00 · consultada hace 5 min»; «Desactualizado»
  keeps the existing 3 h fetch rule; offline shows the cached week with its age, never as current.
- **Tests:** node fixture tests for both providers' daily parsing (including missing values);
  `EdgeWeatherResponseTest` (old payload without `daily` still parses); screen test for the week
  (unknown ≠ 0, source visible, offline label).
- **Estimate:** 1 PR, ~1.5–2 working days including the AEMET fixture capture; a function
  **redeploy** is needed (same manual workflow as 20B; secrets untouched, `verify_jwt=true`).

### W2 — Shared municipality + «Seguir mi ubicación» (depends on 21A)
- The shared municipality **is** 21A's «Tu municipio» (`profile_settings`, Room v17 already
  approved as P1). Doing it inside Phase 20 would pull 21A forward, which the gate order forbids.
  Proposal: implement W2 **as part of 21A** after Gate 20 PASS:
  - resolution order for Inicio: manual profile municipality → (if the user turned it on)
    «Seguir mi ubicación» → common Farm municipality → «Elige tu municipio»;
  - «Seguir mi ubicación»: explicit opt-in switch; asks `ACCESS_COARSE_LOCATION` only then; reads
    the position only while Inicio is in the foreground; the coordinates are sent to the function
    only as `latitude/longitude` (already accepted by the contract) and never stored remotely;
  - header shows the municipality; the header photo stays generic until a licensed per-municipality
    photo source exists (none approved — not invented).
- **Estimate:** adds ~1–1.5 days on top of 21A (permission flow + resolution + tests).

## 4. Gate impact
- **Gate 20 does not need W1/W2 to close:** its criterion is «failure of every external feed still
  leaves Mi Olivar fully operational», and G/B checks cover current weather, radar, cache/offline and
  effects. Codex's note on #281 agrees this is a follow-up.
- If the owner wants W1 before closing Gate 20, add to the checklist:
  B4 «Tocar Tiempo → Próximos días: 7 filas, «—» donde no hay dato, fuente y ambas horas»;
  D5 «Modo avión → Próximos días muestra la semana guardada con su antigüedad».
- W2 checks belong to Gate 21 (21A).

## 5. Owner decisions needed

| # | Decision | Options | Recommendation |
|---|---|---|---|
| W-1 | When to build W1 | (a) now, before closing Gate 20, adding B4/D5; (b) after Gate 20, as the first PR of Phase 21 | **(b)**: Gate 20 closes on the APK 580 test; W1 does not block field work |
| W-2 | Where W2 lives | (a) inside 21A; (b) separate slice after 21A | **(a)**: same municipality, same Room v17 table, one migration |
| W-3 | Freshness wording | «Previsión {fuente} de las HH:MM · consultada hace …» | as proposed |

Nothing in this document changes code, Room, the Edge Functions or their secrets.
