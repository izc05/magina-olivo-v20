# CR-006 — Weather through Supabase Edge Functions before Phase 22

**Status:** APPROVED BY OWNER — 2026-09-25  
**Baseline:** RC1.2 remains in force. This CR is a narrow, explicit exception to the "no Supabase
before Phase 22" boundary, for weather only.

## Motivation

Phase 20B shows real weather on Inicio (owner decision D1: AEMET OpenData). AEMET needs an API
key. Putting that key in the APK would expose it. The owner already stores `AEMET_API_KEY` in
Supabase → Edge Functions → Secrets and has two functions, `weather-forecast` and
`weather-radar`, that read it server-side.

## Proposed change

1. Android calls **only** these two functions, over plain HTTPS POST:
   - `https://zzelvbcuxsboafibfxch.supabase.co/functions/v1/weather-forecast`
   - `https://zzelvbcuxsboafibfxch.supabase.co/functions/v1/weather-radar`
2. Authentication: `verify_jwt = true`; the app sends the project's **public anon key** only.
   Never `service_role` or a secret key (the build fails if one is configured).
3. **No** Supabase SDK, Auth, database access or synchronization in the app before Phase 22.
4. `AEMET_API_KEY` never leaves the function secrets: not in GitHub, Gradle, BuildConfig or the APK.
5. Provider chain inside `weather-forecast`: **AEMET → MET Norway**; the app completes it with
   **its last valid cached value → "no disponible"** (Phase 20A cache, offline-first).
6. Every answer states the provider actually used, its attribution and when it was updated.
7. The functions' source is versioned in `supabase/functions/` without secrets.
8. Municipality resolution is generic (AEMET master list); the old four-town allowlist
   (Bedmar, Jódar, Jimena, Albanchez) is removed.
9. CI uses fixtures only; no live AEMET / MET Norway / RainViewer call.

## Affected baseline sections

- `ArchitectureBoundaryTest`: "supabase" stays forbidden in app sources except the weather
  client package `data/remote/weather/`, which must not import any Supabase SDK.

## Affected phases

- Phase 20B (weather) and the radar entry. Phase 22 (backend contract) is unchanged.

## Data impact

None in Room (the v1 `weather_cache` table already stores the provider as `source`).

## Offline/sync impact

None. Weather is read from the local cache; a failed call never blocks Mi Olivar (Gate 20).

## UX impact

Inicio shows "Fuente: AEMET" or "Fuente: MET Norway", the update age and the provider's credit.

## Risks

- The anon key is public by design; abuse is limited by `verify_jwt`, function input validation
  and provider rate limits.
- Deployed functions must be redeployed from the versioned source for the new contract.

## Alternatives considered

- AEMET key in the APK: rejected (key exposure).
- Waiting for Phase 22: rejected by the owner; weather is a Phase 20 requirement.

## Decision

Approved by the owner on 2026-09-25 in the Phase 20B conversation, limited exactly as above.

## New baseline version

RC1.2 + CR-006.
