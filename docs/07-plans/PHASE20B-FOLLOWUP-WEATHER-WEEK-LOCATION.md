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
  Proposal: implement W2 **with 21A** after Gate 20 PASS. The shared municipality is already in
  21A's approved scope («Inicio uses it»); «Seguir mi ubicación» is **not**, so it needs the same
  Change Request as W1 (§5, W-1) before it is built:
  - **two mutually exclusive modes** for Inicio's place: «Municipio elegido» (manual, the default
    once chosen) or «Seguir mi ubicación» (only while switched on). Turning one on turns the other
    off, so an enabled location mode is never hidden behind an older manual choice. With neither
    set: the common Farm municipality → «Elige tu municipio»;
  - «Seguir mi ubicación»: explicit opt-in switch; asks `ACCESS_COARSE_LOCATION` only then; reads
    the position only while Inicio is in the foreground; never stored remotely. If the position is
    unavailable (permission revoked, no fix) Inicio says so and keeps the last shown value with its
    age — it does not silently switch back to the manual municipality;
  - **function change required:** today `validRequest()` (`weather-forecast/handler.ts`) rejects a
    request without `municipalityCode`/`municipality` (HTTP 400). W2 must accept coordinate-only
    requests: resolve the nearest municipality from the AEMET municipality list (it carries
    `latitud_dec`/`longitud_dec`, see `aemet-municipios.json`) so AEMET can answer, and fall back to
    MET Norway by coordinates when the list is unreachable. Adds a coordinate-only fixture test,
    range validation (already present for lat/lon) and a **redeploy** of the function;
  - header shows the municipality; the header photo stays generic until a licensed per-municipality
    photo source exists (none approved — not invented).
- **Estimate:** adds ~1.5–2 days on top of 21A (function change + redeploy, permission flow,
  exclusive modes, tests).

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
| W-1 | When to build W1 | (a) inside Phase 20, before Gate 20 closes: one more APK and device test with B4/D5 added; (b) after Gate 20 PASS, **only through a Change Request** (`CHANGE-CONTROL.md`; draft ready: `docs/00-master/RC1.2-CHANGE-REQUEST-008-WEATHER-WEEK-LOCATION.md`) that registers W1 as an approved 20B follow-up and updates `CURRENT-STATE.md` before any code — it is not Phase 21 (Profile) scope | **(b) with CR-008**: Gate 20 closes on the APK 580 test and W1 does not block field work; without an approved CR the only compliant option is (a) |
| W-2 | Where W2 lives | (a) with 21A (shared municipality already in scope; follow-location added by the same CR-008); (b) separate slice after 21A, also by CR | **(a)**: same municipality, same Room v17 table, one migration |
| W-3 | Freshness wording | «Previsión {fuente} de las HH:MM · consultada hace …» | as proposed |

Nothing in this document changes code, Room, the Edge Functions or their secrets, and nothing in
it authorizes production work: W1/W2 start only after the owner's choice above and, for option (b),
an approved CR-008.
