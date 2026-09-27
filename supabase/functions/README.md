# Magina Olivo — Supabase Edge Functions (weather CR-006, oil market Phase 20D)

The two weather functions and the oil-market function live here. The Android app calls them over HTTPS with the
project's **public anon key** (`verify_jwt = true`). No Supabase SDK, Auth or sync in the app
before Phase 22.

| Function | Request | Providers |
|---|---|---|
| `weather-forecast` | `{"municipality":"Bedmar","province":"Jaén"}` or `{"municipalityCode":"23019"}` (+ optional `latitude`/`longitude`) | AEMET → MET Norway |
| `weather-radar` | `{"operation":"frames"}` | RainViewer |
| `oil-market` | `{"operation":"series","geography":"andalucia","weeks":12}` or `{"operation":"latest","geography":"andalucia"}` | Junta de Andalucía, Observatorio de Precios y Mercados (weekly, almazara/bodega) |

`oil-market` reads the Junta's public "Últimos precios" page server side (the app never parses
HTML) and answers the normalized contract of `docs/07-plans/PHASE20D-OIL-MARKET-CONTRACT.md`:
one series per category (AOVE / AOV / AOL), each week with its value as published in €/kg. The
table is found by its labels (week headers, LAMPANTE / VIRGEN / VIRGEN-EXTRA), not by column
positions; a "--" or empty cell stays missing; an unknown category, a value outside 0,5–30 €/kg
or a page without the table is `502 {"error":"source_unreadable"}`, and the app keeps its cache.

Every 200 response says which `provider` answered, its `attribution`, `updatedAt` (when the
provider produced it) and `fetchedAt`. When every provider fails the function answers
`502 {"error":"providers_unavailable"}` and the app keeps its last valid value (marked
stale) or shows "no disponible". Municipalities are resolved generically from AEMET's master
list (no allowlist).

## Secrets

`AEMET_API_KEY` is set in **Supabase → Edge Functions → Secrets**. It is never committed, never
sent to the app and never logged. MET Norway and RainViewer need no key.

## Deploy

```sh
supabase functions deploy weather-forecast --project-ref zzelvbcuxsboafibfxch
supabase functions deploy weather-radar --project-ref zzelvbcuxsboafibfxch
```

Do not pass `--no-verify-jwt`.

```sh
supabase functions deploy oil-market --project-ref zzelvbcuxsboafibfxch
```

Or run the manual workflow **Deploy oil-market function** (`.github/workflows/deploy-oil-market-function.yml`):
it deploys, then makes one real call from Supabase's Paris region (`x-region: eu-west-3`, as a phone
in Spain would), checks that a call without the key is refused (401) and keeps the response as the
`oil-market-validation` artifact. The job is red unless that call returns the Junta series; a 502
says why (`source_unavailable` + `detail`: `timeout`, `network`, `http_NNN`; or `source_unreadable`
+ the parser's reason). The runner's own attempt to read the page is informative only (GitHub's
runners could not connect to juntadeandalucia.es on 2026-09-27). The app's
`OIL_MARKET_FUNCTION_DEPLOYED` stays off until a green run.

For the weather, run the manual workflow **Deploy weather functions** (`.github/workflows/deploy-weather-functions.yml`).
It needs two repository secrets: `SUPABASE_ACCESS_TOKEN_FULL` (a Supabase personal access token
with Edge Functions write access, read by the CLI as `SUPABASE_ACCESS_TOKEN`, for the deploy only)
and `SUPABASE_ANON_KEY` (public anon key). After deploying it makes one real call for Bedmar asked
by name and province (`{"municipality":"Bedmar","province":"Jaén"}`, as the app asks) and the radar,
checks that a call without the key is refused (401), writes which provider answered to the run
summary, and keeps the real responses as the `weather-functions-validation` artifact.

## Tests

CI runs the fixture tests; no live AEMET, MET Norway, RainViewer or Junta call is made:

```sh
node --experimental-strip-types --test supabase/functions/*/*.test.ts
```

The fixtures follow each provider's documented response format; they are not recordings of
live responses. The Junta fixture (`oil-market/fixtures/junta-ultimos-precios.synthetic.html`)
carries the owner's verified values for weeks 31–38 of 2026 inside synthetic markup. Replace them with real recordings when a live call has been captured.
