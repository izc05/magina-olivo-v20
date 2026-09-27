# Phase 20 Gate Checklist — Home contextual services + weather visuals

Status: **PENDING owner device check** (prepared 2026-09-27).

**Gate 20:** failure of every external feed still leaves Mi Olivar fully operational.

## Slices

| Slice | PR | Room | Status |
|---|---|---|---|
| 20A — Feed foundation + Home external cards | #243 | — (reuses `weather_cache`) | merged |
| 20B — Weather via Edge Functions (AEMET → MET Norway, CR-006) | see Phase 20 plan; #247 (deploy token) | — | merged; deployed and validated live (run #5) |
| 20B-fix — validation body + provider in the run summary | #257 | — | merged |
| 20B-radar — «Ver radar» (RainViewer), live-only | #259 | — | merged |
| 20C — weather look on Inicio's header (reduced-motion safe) | #260 | — | merged |
| 20D — oil-market reference | — | — | **source approved 2026-09-27**: Junta de Andalucía Observatorio; implementation pending its own PR |
| 20E — preferred-cooperative notices | — | — | **deferred**: waits for the private Admin surface / approved per-cooperative feed (D4) |

Owner update 2026-09-27: D3 is now decided. 20D may be implemented from the official Junta de
Andalucía Observatorio source in a separate PR, after documenting stable access/reuse and fixtures.
20E remains deferred. Nothing in Mi Olivar depends on 20E, and no cooperative notice is invented.

## Automated evidence (CI, fixtures only — no live AEMET/MET Norway/RainViewer calls)

| Criterion | Test evidence |
|---|---|
| Every feed failing leaves Home and Mi Olivar usable | `HomeFeedsScreenTest` |
| Stale/unknown weather is never shown as fresh; provider named | `WeatherFeedContractTest`, `EdgeWeatherResponseTest` |
| Radar is live-only: offline says so, never an old picture | `RadarScreenTest`, `EdgeRadarResponseTest` |
| Weather look: mapping table; reduced motion ⇒ still frame; unknown ⇒ none | `WeatherMoodsTest` (JVM), `WeatherMoodLayerTest` |
| Edge Functions: fixtures, keyless call refused (401) | `supabase/functions/*` node tests + `weather-fixtures` job |
| Boundary: no Supabase/WorkManager outside `data/remote/weather/` | `ArchitectureBoundaryTest` |

## Owner device checks (latest APK, Perfil → «Versión 0.3.0-dev · compilación …»)

| # | Check | Expected | Result |
|---|---|---|---|
| G1 | Inicio with data and coverage | Tiempo with its source («AEMET» or «MET Norway») and time; header look matches the sky | ☐ |
| G2 | Airplane mode, open Inicio | The last weather with its age («hace …») or «Sin conexión»; never presented as current | ☐ |
| G3 | Airplane mode, «Ver radar» | «El radar necesita conexión» + Reintentar; no old picture | ☐ |
| G4 | Airplane mode, Mi Campo → Finca → Registrar hoy → guardar | Saved on the phone; appears in Cuaderno → Diario | ☐ |
| G5 | Airplane mode, Cuaderno → Nueva pesada → guardar | Saved; Jornada and totals updated | ☐ |
| G6 | Ajustes → Accesibilidad → quitar animaciones, open Inicio | Header without movement; text readable | ☐ |
| G7 | Aceite / Cooperativa cards | «Sin fuente configurada» / choose cooperative; no invented prices or news | ☐ |

Please report the device model and Android version with the results.
