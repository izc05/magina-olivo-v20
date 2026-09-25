# Magina Olivo — Supabase Edge Functions (weather only, CR-006)

Only the two weather functions live here. The Android app calls them over HTTPS with the
project's **public anon key** (`verify_jwt = true`). No Supabase SDK, Auth or sync in the app
before Phase 22.

| Function | Request | Providers |
|---|---|---|
| `weather-forecast` | `{"municipality":"Bedmar","province":"Jaén"}` or `{"municipalityCode":"23019"}` (+ optional `latitude`/`longitude`) | AEMET → MET Norway |
| `weather-radar` | `{"operation":"frames"}` | RainViewer |

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

## Tests

CI runs the fixture tests; no live AEMET, MET Norway or RainViewer call is made:

```sh
node --experimental-strip-types --test supabase/functions/*/*.test.ts
```

The fixtures follow each provider's documented response format; they are not recordings of
live responses. Replace them with real recordings when a live call has been captured.
