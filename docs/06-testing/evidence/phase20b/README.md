# Phase 20B — live validation of the weather Edge Functions (CR-006)

Real responses captured by the manual workflow `.github/workflows/deploy-weather-functions.yml`,
run #5 (Actions run `36127846780`, job `deploy-and-validate`, 2026-09-25 11:09 UTC), artifact
`weather-functions-validation` (id `10860472882`, sha256
`892858afb532a465612dc2f4530d857f2098a333f24aaf4576c3266664f1f7be`). The two JSON files are
stored **verbatim** (only the file names from the workflow were kept). They contain no key or
secret: the anon key travels in request headers, never in the body.

These are evidence, not CI fixtures: CI keeps using the recorded fixtures under
`supabase/functions/*/fixtures/` and never calls AEMET, MET Norway or RainViewer live.

## Results

| Call | HTTP | Evidence |
|---|---|---|
| `weather-forecast` `{"municipalityCode":"23019"}` with the anon key | 200 | `weather-forecast-bedmar-23019.json` |
| `weather-radar` `{"operation":"frames"}` with the anon key | 200 | `weather-radar-frames.json` (13 RainViewer frames, 09:00–11:00 UTC) |
| `weather-forecast` without any key | **401** | workflow step asserts `test "$unauth" = "401"`; the step passed |

`verify_jwt = true` is therefore confirmed live: without the public anon key the function refuses.

## Findings (to act on, not hidden)

1. **Code 23019 is Campillo de Arenas, not Bedmar y Garcíez.** The live answer resolved
   `23019` through AEMET's own municipality master list to `"Campillo de Arenas" (Jaén)`. The
   workflow step name, the evidence file name and the synthetic master list in
   `supabase/functions/weather-forecast/forecast.test.ts` assumed 23019 = Bedmar y Garcíez; that
   assumption was wrong. Bedmar y Garcíez's INE code is **pending verification** (not guessed
   here). The app itself asks by name and province (`{"municipality","province"}`), which the
   function resolves through the same master list, so the app is not affected; the validation
   call and the test labels are.
2. **AEMET's hourly forecast did not answer; MET Norway did.** The response says
   `"provider":"MET_NORWAY"`. Because the place name came from AEMET's master list, the AEMET key
   works for that endpoint; the failure was on
   `/prediccion/especifica/municipio/horaria/23019` (error, timeout, rate limit or invalid
   document; the reason is logged by the function as `AEMET failed, falling back: …` in the
   Supabase function logs). The fallback behaved as designed (AEMET → MET Norway), and
   `rainProbabilityPercent` is `null` because MET Norway's compact feed has no probability; Inicio simply
   omits the rain line, never shows 0 %.

## Follow-up (Phase 20, when it resumes after Issue #246)

- Owner: read the Supabase logs of `weather-forecast` for 2026-09-25 11:09 UTC and confirm
  the AEMET failure reason.
- Change the validation call to ask by name (`{"municipality":"Bedmar","province":"Jaén"}`) so it
  does not depend on a remembered code; correct the "Bedmar = 23019" labels in the workflow and
  the synthetic test master list once the real code is confirmed.
- Re-run the deploy workflow and store the new evidence next to this one.
