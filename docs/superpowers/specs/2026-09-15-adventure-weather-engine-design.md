# Mágina Aventura — Weather Engine Design

## Goal
Convert the existing `@magina/weather` package into the single weather domain for V20 and expose a dynamic, safety-first weather experience in Mágina Aventura without coupling UI code to AEMET or Open-Meteo payloads.

## Product flow
1. Route detail shows a concise forecast for the route.
2. `Preparar aventura` evaluates current/near-term conditions before the user starts.
3. `Aventura en curso` shows a compact Weather HUD and a visual weather layer.
4. Official warnings always take precedence over gamification and decorative effects.
5. The app degrades gracefully to the last known/cached weather snapshot when live refresh fails.

## Architecture
`AEMET + Open-Meteo + existing radar -> @magina/weather -> WeatherState -> API/cache -> web/mobile renderers`.

`@magina/weather` owns provider adapters, normalization, condition/intensity classification, day phase and safety interpretation. Consumers receive a stable `WeatherState`; they must not parse provider-specific data.

## WeatherState contract
- `condition`: `clear | cloudy | rain | storm | fog | snow | wind`
- `intensity`: `0 | 1 | 2 | 3`
- `temperatureC`, `feelsLikeC`
- `windSpeedKmh`, `windDirectionDeg`
- `precipitationMm`
- `visibilityM`
- `dayPhase`: `day | golden_hour | dusk | night`
- `observedAt`, `expiresAt`
- `provider`: provider provenance
- `officialAlert`: optional official warning summary
- `stale`: whether cached data is past its normal refresh window

## Providers
- Keep AEMET as official source for Spanish forecasts/warnings and existing radar capability.
- Add Open-Meteo as coordinate-level current/near-term source suitable for the user's GPS position during an adventure.
- Provider failures must not break route/adventure rendering.

## Rendering strategy
The current V20 front end is Next.js web. Phase 1 therefore implements a browser-compatible `WeatherVisualLayer` using CSS/canvas-friendly primitives and a `WeatherHUD`. The contract is renderer-agnostic so a future Expo/React Native client can use React Native Skia, particle emitters and Rive without changing provider/domain logic.

Visual states:
- clear: warmer light and subtle sun glow
- cloudy: cooler/softer ambience
- rain: animated rain overlay, intensity 1–3
- fog: translucent moving fog bands with reduced contrast
- wind: directional leaf/particle motion
- storm: darkened ambience; lightning kept subtle and accessibility-safe
- snow: light snow particles only when real conditions indicate snow
- dusk/night: map/UI ambience changes without reducing navigation contrast

## Safety
- Weather decoration never obscures the track, checkpoints, controls or warning copy.
- Respect `prefers-reduced-motion`; reduce/disable non-essential animation.
- Official AEMET alerts render as safety UI, not as a game effect.
- Weather-derived recommendations are informational and clearly distinct from official warnings.
- Route closures, signage and validated route safety remain authoritative.

## Refresh and cache
- Current conditions: refresh target 10–15 minutes while the adventure is active.
- Route preparation can fetch once on entry and refresh on explicit retry or stale expiry.
- API/server cache shields provider quotas and keys; clients never receive the AEMET API key.
- Cached stale data may be shown with an explicit stale indicator.

## Development weather lab
Provide a development-only/manual override that can simulate `clear`, `cloudy`, `rain`, `storm`, `fog`, `snow`, `wind`, dusk and night with intensity 0–3. Production defaults to `AUTO`.

## Integration points
- `packages/weather`: provider adapters + normalized domain.
- `apps/api/src/weather`: server orchestration/cache endpoint.
- `apps/web/src/lib/weather-source.ts`: typed client.
- `apps/web/src/app/aventura/preparar`: preparation experience.
- `apps/web/src/app/aventura/en-curso`: Weather HUD + visual layer.
- `apps/web/src/app/rutas/detalle`: compact route forecast/entry to preparation.

## Quality gates
- Do not touch `main`.
- Preserve current GPS privacy, route safety, checkpoint and XP behavior.
- No fictitious weather values in production UI.
- TypeScript/build + Adventure + Activity + Full Candidate + Browser E2E + Environment + Staging must be green on the same HEAD before promotion.
