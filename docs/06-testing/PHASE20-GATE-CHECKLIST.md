# Phase 20 Gate Checklist — Home contextual services + weather visuals

Status: **OPEN — PR #284 changes need a new APK and owner device test** (updated 2026-09-28).
Gate 20 closes only when every check below is ☑ on a physical phone. Phase 21 does not start before.

**Gate 20:** failure of every external feed still leaves Mi Campo, Cuaderno and the harvest flow
fully operational; every external value says its source and age and is never presented as fresh
when it is not.

## Slices

| Slice | PR | Room | Status |
|---|---|---|---|
| 20A — Feed foundation + Home external cards | #243 | — (reuses `weather_cache`) | merged |
| 20B — Weather via Edge Functions (AEMET → MET Norway, CR-006) | #247, #257 | — | merged; deployed and validated live |
| 20B-radar — «Ver radar» (RainViewer), live-only | #259 | — | merged |
| 20C — weather look on Inicio's header (reduced-motion safe) | #260 | — | merged |
| 20D-1 — «Mercado del aceite» card: AOVE.net daily pulse + official weekly trend | #273, #279 | — (reuses `weather_cache`) | merged |
| 20D-2 — `oil-market` Edge Function (Junta de Andalucía Observatorio) | #274, #276, #278 | — | merged; **deployed 2026-09-28** (deploy run #3: HTTP 200 from eu-west-3, 401 without key) |
| 20D-3 — official source on + «Mercado del aceite» screen, 12-week chart (0.5.0) | #280 | — | merged |
| 20E — preferred-cooperative notices | — | — | **deferred** (D4): waits for the private Admin surface / an approved per-cooperative feed |

Also in 0.5.0 (not Phase 20 slices, checked in E0–E3 below): the owner's UX review (#277:
Mi Campo empty state, shorter farm/parcel forms, single «Añadir», «Registrar hoy» type-first).
Gate 20 fixes from the emulator run on build 575 (#282): «Abrir jornada de hoy» before any
Pesada, «Kg pendientes de pesada» instead of 0 kg, farm card during «Recolección» (E4–E6).

## Automated evidence (CI, fixtures only — no live AEMET/MET Norway/RainViewer/Junta calls)

| Criterion | Test evidence |
|---|---|
| Every feed failing leaves Home and Mi Campo usable | `HomeFeedsScreenTest` |
| Stale/unknown weather is never shown as fresh; provider named | `WeatherFeedContractTest`, `EdgeWeatherResponseTest` |
| Radar is live-only: offline says so, never an old picture | `RadarScreenTest`, `EdgeRadarResponseTest` |
| Weather look: mapping table; reduced motion ⇒ still frame; unknown ⇒ none | `WeatherMoodsTest` (JVM), `WeatherMoodLayerTest` |
| Oil market: weekly change only between consecutive weeks; a missing week stays missing; «Dato antiguo» by the weekly rule; never «hoy» | `OilTrendsTest`, `EdgeOilMarketResponseTest`, `OilMarketFeedContractTest`, `HomeOilMarketScreenTest`, `OilMarketScreenTest` |
| Oil market function: Junta table by labels (live header format), refusals with a reason, 8 s under the app's 10 s | `supabase/functions/oil-market/oil-market.test.ts` (`weather-fixtures` job) |
| Harvest flow offline: Jornada + Pesadas + costs, no duplicates | `RecollectionFlowContractTest`, `JornadaPesadasContractTest`, `AppNavigationTest` |
| Boundary: no Supabase/WorkManager outside `data/remote/weather/` | `ArchitectureBoundaryTest` |

## Before starting

- Install the **0.5.0** APK from the `main` build that includes #282 (Actions → Android CI →
  the run for «Gate 20: abrir la jornada…» → artifact `magina-olivo-dev-debug`).
- ⚠️ CI signs `devDebug` with the runner's default debug key, which is **not stable between runs**.
  Installing this APK over any earlier one (CI or local) normally makes Android ask to uninstall
  first, and **all local data on the phone is lost**. Do the device test on a fresh install and
  create the test data during section E; do not install it over a phone whose data must be kept.
  (A stable signing key for CI is a separate task, not part of Gate 20.)
- Have coverage for the first opening (so the official oil weeks and the weather are fetched once),
  then use airplane mode where a check says so.

## Owner device checks — APK 0.5.0

### A. Perfil / versionado

| # | Check | Expected | Result |
|---|---|---|---|
| A1 | Perfil → Acerca de | «Versión 0.5.0-dev · compilación N», N = the CI run number of the APK | ☐ |

### B. Tiempo y radar

| # | Check | Expected | Result |
|---|---|---|---|
| B1 | Inicio with coverage and a farm with municipality | Tiempo with its source («AEMET» or «MET Norway») and «Actualizado hace …»; header look matches the sky | ☐ |
| B2 | «Ver radar de lluvia» with coverage | Radar over the farm, with the frame time and «RainViewer» | ☐ |
| B3 | Ajustes del teléfono → Accesibilidad → quitar animaciones, open Inicio | Header without movement; text readable | ☐ |
| B4 | Inicio → «Ver previsión» sobre la foto, también con letra ampliada | Temperatura grande, icono del estado y municipio consultado; proveedor y antigüedad visibles; detalle con lluvia/viento disponibles y hasta siete días; valores ausentes como «—»/no disponibles; radar y regreso a Inicio; sin textos/botones recortados | ☐ |

### C. Mercado del aceite

| # | Check | Expected | Result |
|---|---|---|---|
| C1 | Inicio → tarjeta compacta «Mercado del aceite» con cobertura | Gráfica oficial de 12 semanas con tres líneas/leyenda y últimos valores AOVE, Virgen y Lampante en €/kg; semana y fuente visibles; sin widget largo incrustado | ☐ |
| C2 | Inicio → «Ver mercado» | Detalle mantiene el pulso AOVE.net, la tendencia oficial y el gráfico de 12 semanas; precio del aceite claramente separado del precio de aceituna | ☐ |
| C4 | Compare with the Junta page (Observatorio → Aceites de oliva → Últimos precios) | Last week's AOVE / Virgen / Lampante match | ☐ |

### D. Caché / sin conexión

| # | Check | Expected | Result |
|---|---|---|---|
| D1 | Airplane mode, open Inicio | Last weather with its age or «Sin conexión»; never presented as current | ☐ |
| D2 | Airplane mode, «Ver radar» | «El radar necesita conexión» + Reintentar; no old picture | ☐ |
| D3 | Airplane mode, Mercado del aceite | Official weeks still shown (from the phone) with their week and source; «Pulso diario no disponible sin conexión» | ☐ |
| D4 | Back online, wait / reopen Inicio | Pulse loads again by itself; weather refreshes | ☐ |
| D5 | Airplane mode → Inicio → abrir Tiempo | Semana en caché con fuente y antigüedad; si no hay semana guardada, mensaje claro de que se necesita conexión | ☐ |

### E. Mi Campo → Cuaderno → Jornada / Pesada (in airplane mode)

Re-run on the APK that includes #282 (emulator run on build 575 found no way to open a Jornada before its Pesadas, and «Sin campaña activa» during «Recolección»).

| # | Check | Expected | Result |
|---|---|---|---|
| E0 | Fresh install → Mi Campo | «Aún no tienes fincas» with one action «Crear mi primera finca»; the farm form shows the essentials and folds the rest under «Más detalles» | ☐ |
| E1 | Mi Campo → Finca → Parcelas → «Añadir» → «A mano» → alias + superficie → Guardar | Short form: alias + superficie; the rest under «Más datos del olivar». Back on the parcel list with the new parcel | ☐ |
| E2 | Cuaderno → «Registrar hoy» → the type is chosen first (e.g. Poda) → parcela → Guardar | Appears in Cuaderno → Diario as **Completada**, today | ☐ |
| E3 | Cuaderno → «Registrar hoy» with a future date or a reminder | Stays **Planificada** and appears in Avisos | ☐ |
| E4 | Cuaderno → Recolección → «Abrir jornada de hoy» (choose the farm if asked) | Jornada of today opens; «Kg pendientes de pesada», never «0 kg»; Recolección counts 1 Jornada; the farm card shows the campaign, not «Sin campaña activa» | ☐ |
| E5 | Inside that Jornada → «Añadir pesada» (kg, cooperativa) → Guardar; add a second one | Both Pesadas inside the Jornada; its kilos are their sum; campaign totals updated; nothing listed twice | ☐ |
| E6 | Cuaderno → Nueva pesada → «Nueva jornada de este día» on another farm/day | The Pesada opens its own Jornada with its kilos | ☐ |
| E7 | Close the app (swipe away) and reopen, still offline | Everything from E1–E6 is still there | ☐ |

### F. Cooperativa

| # | Check | Expected | Result |
|---|---|---|---|
| F1 | «Mi cooperativa» card | Says notices will come with the admin panel; no invented news | ☐ |

Please report the **phone model and Android version** with the results, and a screenshot of any ☒.
If everything is ☑, Gate 20 is PASS and Phase 21 (Perfil) may start.
