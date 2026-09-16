# Mágina Aventura — Weather Visuals Design

## Context

The Weather Engine already provides a validated, platform-neutral `WeatherState` sourced from Open-Meteo and enriched with AEMET official alerts. The current web experience renders weather through a CSS-driven `AdventureWeather` visual layer and a separate HUD. This phase must improve visual fidelity without moving weather classification, safety logic, GPS permissions, provider access, or AEMET precedence into the renderer.

The validated base commit is `a417794337dacea797d287beaa1ba59d7d6f4974`. This work lives on `feat/v20-adventure-weather-visuals` and must not modify `main`.

## Goal

Build a reusable visual contract between `WeatherState` and presentation so the existing web app can gain richer, smoother Sierra Mágina weather ambience now, while a future React Native client can reuse the same visual decisions with Skia, particles, and Rive.

The target architecture is:

`Open-Meteo + AEMET -> WeatherState -> WeatherVisualModel -> platform renderer`

The renderer never decides what the weather is. It only renders a visual model derived from trusted weather state.

## Scope

This phase includes:

1. A new platform-neutral `WeatherVisualModel` in `@magina/weather`.
2. A deterministic builder that converts `WeatherState` into visual parameters.
3. Smooth transitions between visual states without altering the underlying weather state.
4. A modular web renderer for sky, clouds, fog, precipitation, wind, olive leaves, lightning, dust, and ambient tint.
5. Sierra Mágina-specific ambient details that are aesthetic only and never represent official meteorological facts.
6. An expanded local Weather Lab that can exercise every important visual state without GPS or network calls.
7. Performance and accessibility degradation rules.
8. Tests proving the visual model and web integration are deterministic, non-blocking, and safe.
9. A stable contract ready for later React Native Skia / particle / Rive renderers.

## Non-goals

This phase does not:

- change Open-Meteo or AEMET provider integration;
- change GPS permission behavior;
- change the 10-minute weather refresh cadence;
- add background GPS;
- add a React Native application;
- install React Native Skia in the Next.js web app;
- install `rive-react-native` in the web app;
- create safety decisions from visual effects;
- infer official alerts from animation intensity;
- expose provider keys or raw provider payloads;
- allow production users to enable simulated weather.

## Existing contract that remains authoritative

`WeatherState` remains the source of truth for:

- `condition`: `clear | cloudy | rain | storm | fog | snow | wind`;
- `intensity`: `0 | 1 | 2 | 3`;
- `temperatureC`;
- `feelsLikeC`;
- `windSpeedKmh`;
- `windDirectionDeg`;
- `windGustsKmh`;
- `precipitationMm`;
- `visibilityM`;
- `cloudCoverPercent`;
- `dayPhase`: `day | golden_hour | dusk | night`;
- `officialAlert`;
- `stale`.

The renderer must not reclassify weather from these numeric values.

## WeatherVisualModel

Create `packages/weather/src/weather-visual-model.ts` and export it through the package public API.

The model is presentation-only and must contain these fields:

```ts
export type WeatherPerformanceTier = 'full' | 'balanced' | 'minimal';
export type WeatherSkyPreset = 'clear' | 'soft-cloud' | 'overcast' | 'storm' | 'fog' | 'snow' | 'night';
export type WeatherAmbientTone = 'neutral' | 'warm' | 'cool' | 'storm';

export type WeatherVisualModel = {
  skyPreset: WeatherSkyPreset;
  ambientTone: WeatherAmbientTone;
  ambientOpacity: number;
  cloudDensity: number;
  fogDensity: number;
  rainAmount: number;
  rainAngleDeg: number;
  snowAmount: number;
  windStrength: number;
  leafAmount: number;
  dustAmount: number;
  lightningAmount: number;
  transitionDurationMs: number;
  performanceTier: WeatherPerformanceTier;
  reducedMotion: boolean;
  safetyEmphasis: boolean;
};
```

All normalized amount fields use the inclusive range `0..1`. `rainAngleDeg` uses degrees, where `0` means vertical rain and positive values lean rain to the right. `transitionDurationMs` controls visual interpolation only.

The builder signature is:

```ts
export function buildWeatherVisualModel(
  weather: WeatherState,
  options?: {
    reducedMotion?: boolean;
    performanceTier?: WeatherPerformanceTier;
  },
): WeatherVisualModel;
```

The builder must be pure and deterministic.

## Mapping rules

### Clear

- `skyPreset`: `clear`, except `night` phase -> `night`.
- `cloudDensity`: derived only from condition/intensity presets, not reclassification.
- precipitation, fog, snow, lightning: `0`.
- warm ambient tone during `golden_hour`, cool tone at `dusk` and `night`.
- `dustAmount` may reach `0.15` only in `clear` day/golden-hour conditions with intensity `0`; this is Sierra Mágina ambience, not a meteorological claim.

### Cloudy

- `skyPreset`: `soft-cloud` for intensity `1`, `overcast` for `2` or `3`.
- `cloudDensity`: `0.45`, `0.72`, `0.9` for intensities `1`, `2`, `3`.
- no precipitation particles.

### Rain

- `skyPreset`: `overcast`.
- `rainAmount`: `0.25`, `0.55`, `0.9` for intensities `1`, `2`, `3`.
- `cloudDensity`: at least `0.7`.
- rain angle is derived from `windDirectionDeg` and capped to `-25..25` degrees; if direction is absent, use `0`.
- `leafAmount` increases only when the condition is `wind` or when wind strength is already represented by the trusted weather state.

### Storm

- `skyPreset`: `storm`.
- `rainAmount`: at least `0.65`.
- `lightningAmount`: `0.35` for intensity `2`, `0.7` for intensity `3`.
- `ambientTone`: `storm`.
- `safetyEmphasis`: always `true`.

### Fog

- `skyPreset`: `fog`.
- `fogDensity`: `0.35`, `0.62`, `0.85` for intensities `1`, `2`, `3`.
- precipitation effects remain `0` unless the authoritative condition is rain/storm; the visual builder must not combine synthetic rain with fog.

### Snow

- `skyPreset`: `snow`.
- `snowAmount`: `0.25`, `0.55`, `0.85` for intensities `1`, `2`, `3`.
- ambient tone is `cool`.

### Wind

- sky follows day phase: `clear` or `night`.
- `windStrength`: `0.35`, `0.65`, `0.9` for intensities `1`, `2`, `3`.
- `leafAmount`: `0.2`, `0.5`, `0.8` for intensities `1`, `2`, `3`.
- olive leaves are decorative particles and must not display warnings or imply a specific wind speed.

## Safety precedence

If `officialAlert` exists:

- `safetyEmphasis` must be `true` regardless of visual condition;
- no visual layer may hide, overlap, dim below legibility, or intercept the official alert UI;
- visual intensity is never increased solely because an alert exists;
- AEMET copy remains outside the decorative renderer;
- the renderer remains `aria-hidden`.

Storm intensity `2` or `3` also sets `safetyEmphasis = true`, but this is only a styling signal for the existing safety UI and must not create new warning text.

## Transition behavior

Visual transitions are presentation-level interpolation between previous and next `WeatherVisualModel` values. They do not synthesize intermediate `WeatherState` objects.

Default transitions:

- ordinary change: `2400 ms`;
- precipitation/fog/storm change: `3200 ms`;
- reduced motion: `0 ms`;
- minimal performance tier: maximum `1000 ms`.

Examples:

- storm -> rain: lightning fades first while rain remains;
- rain -> cloudy: rain fades to zero before cloud density drops;
- cloudy -> clear: cloud density fades gradually;
- day -> golden hour -> dusk -> night: ambient tint and sky preset transition without a hard flash.

The implementation may use CSS transitions and a small requestAnimationFrame-based interpolation helper where necessary, but must not create an unbounded animation loop when all dynamic layers are disabled.

## Web renderer

Create a focused `WeatherScene` component under `apps/web/src/app/aventura/en-curso/weather-scene/`.

Recommended files:

- `weather-scene.tsx`: orchestration and accessibility boundary;
- `weather-scene.module.css`: shared scene positioning and CSS-only layers;
- `weather-particles.tsx`: deterministic DOM/Canvas particle layer for rain, snow, leaves, and dust;
- `weather-lightning.tsx`: isolated lightning flashes;
- `weather-scene-model.ts`: browser-side adaptation of `WeatherVisualModel` for reduced motion and performance tier.

`AdventureWeather` continues to own:

- GPS opt-in;
- weather loading and refresh;
- Weather Lab state;
- the HUD and human-readable metrics;
- official alert/safety copy.

`WeatherScene` receives only the current `AdventureWeatherState` or prebuilt visual model and renders decorative output.

The visual root must use:

- `position: absolute` or `fixed` within the existing adventure visual stage;
- `pointer-events: none`;
- `aria-hidden="true"`;
- no focusable descendants;
- z-index below HUD, alert, navigation, checkpoint, and emergency controls.

## Performance tiers

The web renderer must support three tiers.

### Full

- all supported layers;
- maximum particle counts;
- lightning;
- Sierra Mágina leaf and dust ambience.

### Balanced

- fewer particles;
- simplified fog/cloud layers;
- lightning remains but at reduced visual complexity;
- no more than one decorative ambient particle family at a time.

### Minimal

- no continuous particle simulation;
- CSS-only tint, clouds, fog, and static precipitation texture where needed;
- no lightning animation loop;
- no olive leaves or dust particles.

The default browser tier is `balanced`. The UI does not expose a user-facing performance selector in this phase.

## Reduced motion

`prefers-reduced-motion: reduce` must force:

- `reducedMotion = true`;
- `transitionDurationMs = 0`;
- no continuous particle drift;
- no lightning flashes;
- static atmospheric representation only.

Weather information and safety content remain fully available.

## Sierra Mágina identity

Decorative regional ambience is intentionally limited to:

- olive leaves during authoritative wind conditions;
- subtle warm dust in clear daytime/golden-hour scenes;
- stronger mountain-fog depth when the authoritative condition is fog;
- warm golden-hour tint;
- cool, dark night tint.

These details must never appear in textual weather summaries and must never be described as measured conditions.

## Weather Lab expansion

The local-only Weather Lab remains gated by:

- hostname `localhost` or `127.0.0.1`;
- query parameter `weatherLab=1`.

Add presets for:

- clear day;
- golden hour;
- cloudy light;
- overcast;
- rain light;
- rain medium;
- rain heavy;
- storm medium;
- storm heavy;
- fog light;
- fog dense;
- snow light;
- snow heavy;
- wind light;
- wind strong;
- night clear.

Simulation must never invoke geolocation or network weather APIs and must remain labelled `LAB` with the existing warning that it is not for safety decisions.

## Future React Native renderer

This phase must not add native dependencies. The contract must nevertheless be sufficient for a future client to map:

- `WeatherVisualModel` -> React Native Skia shaders/canvas;
- `rainAmount`, `snowAmount`, `leafAmount`, `dustAmount` -> particle system;
- transition and HUD event state -> Rive assets.

The native renderer must consume the same model rather than duplicate weather classification.

## Error handling

If weather is unavailable:

- `AdventureWeather` keeps the existing non-blocking message;
- no `WeatherScene` is mounted;
- adventure navigation, route tracking, and checkpoints continue working.

If visual rendering throws or a layer cannot initialize:

- weather metrics and safety content remain visible;
- the scene falls back to CSS-only/minimal representation;
- no visual error may block the adventure.

If weather is stale:

- the visual model may continue to render the last known condition;
- the existing stale-data copy remains authoritative;
- no additional warning is invented by the renderer.

## Testing

### Package tests

Add unit tests for `buildWeatherVisualModel` covering:

- every condition;
- intensity mapping;
- day/golden-hour/dusk/night tone;
- official alert safety emphasis;
- reduced motion;
- performance tier behavior;
- wind/rain angle bounds;
- deterministic output for identical input.

### Web tests

Add component/integration coverage proving:

- scene is `aria-hidden`;
- scene has no interactive descendants;
- lab simulation does not request geolocation;
- reduced motion disables animated classes/particle loop;
- safety/HUD content remains outside the decorative scene;
- disabling weather removes the scene without affecting adventure controls.

### Browser E2E

Extend the existing weather E2E only where needed to confirm:

- Weather Lab presets render expected `data-weather-*` state;
- production host cannot enable Weather Lab;
- adventure start and navigation remain unchanged;
- canonical trailing-slash behavior remains accepted.

## CI and completion gate

Do not mark this phase ready until the same final HEAD has passed:

- weather package tests;
- web typecheck;
- web build;
- Weather Engine check;
- Full Candidate check;
- Browser E2E;
- Environment contract;
- Staging readiness;
- relevant admin/foundation gates triggered by the diff.

No gate may be claimed from an older commit.

## Delivery strategy

Implement in this order:

1. shared `WeatherVisualModel` contract and tests;
2. pure builder and tests;
3. web `WeatherScene` shell and minimal fallback;
4. rain/cloud/fog/snow/wind layers;
5. olive leaves, dust, lightning, phase tint;
6. reduced-motion and performance-tier behavior;
7. Weather Lab expansion;
8. E2E and full CI validation.

Each task must be reviewable and independently testable. Production weather and safety behavior must remain unchanged throughout the sequence.