# Phase 20 — Home contextual services + weather visuals (plan)

Status: **PLAN** (2026-09-25). Opened after Gate 19 PASS. Executor: Claude; reviewer: Codex/owner.

Sources of truth: `ROADMAP-RC1.2.md` §Phase 20, `docs/04-ui/HOME-ONBOARDING-SPEC-RC1.2.md`
§2–§12, AGENTS.md truth rules ("never present stale/external information as fresh"; "never
make Home external feeds a dependency for field work").

**Gate 20:** failure of every external feed still leaves Mi Olivar fully operational.

## Principles (apply to every slice)

1. **Farm first.** Home order stays: today → campaign → next work → weather → oil market →
   cooperative. External cards sit below the operational ones and never block them.
2. **Never block, never spin.** Home renders from Room immediately. Feeds load in the
   background; no full-screen or card-blocking spinner for external data.
3. **Cache with its timestamp.** Every feed value is stored locally with `fetchedAt` and its
   source. It is shown as `Actualizado hace …` and marked stale past its freshness window.
4. **Unknown is hidden, not invented.** A category or value the source does not give is not
   shown. No sample prices, no demo notices outside an explicit tutorial.
5. **No GPS required.** Location comes from the selected Farm (parcel geometry centroid, else
   municipality/province geocoded once), or a manually chosen place.
6. **External boundary.** Each feed is a `…Source` interface behind a repository; the UI never
   calls the network. Fakes drive every test; CI never hits a real endpoint.
7. **No new root tab, no redesign.** Brand tokens and existing Mo* components only.

## Slices

### 20A — Feed foundation + Home layout for external cards
- Domain `domain/feed`: `FeedState` (FRESH / STALE / UNAVAILABLE / NOT_CONFIGURED),
  `FeedSnapshot<T>(value, fetchedAt, source, state)`, freshness windows per feed.
- Room **v16**: `external_feed_cache` (feed key, location key, payload JSON, source,
  fetched_at). Not synchronised (device cache, not domain data; no outbox).
- `WeatherLocation` resolution: Farm parcel centroid → municipality/province → manual place.
- Home: the three external slots (weather / market / cooperative) replace the current
  "Pronto" placeholders with honest states: not configured, unavailable, stale, fresh.
- Tests: freshness/state rules (JVM), migration 15→16, Home renders all operational cards
  with every feed failing (Gate 20 core).

### 20B — Weather hero + radar entry
- `WeatherSource` over HTTPS: **AEMET OpenData** (owner decision D1, 2026-09-25).
  - Hourly municipal forecast `prediccion/especifica/municipio/horaria/{INE}`: two-step call
    (metadata → `datos` URL). Temp, sky state, rain probability, wind for the next hours.
  - Municipality resolved to its INE code with AEMET's municipality master list
    (`maestro/municipios`, cached), matched by Farm municipality/province or nearest to the
    parcel centroid.
  - **API key:** requested by the owner at AEMET; never committed. Read at build time from
    `local.properties`/environment (`AEMET_API_KEY`) into `BuildConfig`. Without a key the
    weather card shows *"Tiempo sin configurar"* (state NOT_CONFIGURED), never sample data.
  - Source line: "Fuente: AEMET" with the update time. Spain only; outside Spain the card
    says the source does not cover the location (architecture stays provider-neutral).
- Refresh on Home open when stale (short timeout), never in a loop; manual retry.
- "Ver radar": **RainViewer** tiles on the existing MapLibre view (owner decision D2).
  Requires connection and says so offline (spec §10); shows the radar frame time and source.
- Tests: parsing from recorded fixtures, stale marking, offline radar message.

### 20C — Weather-responsive visual layer
- States CLEAR / CLOUDY / RAIN / WIND / FOG / STORM derived **only** from the fetched
  condition; no state when weather is unknown.
- Lightweight Compose drawing, no particle engine; disabled when system animations are off
  (reduced motion) and simplified on low-performance devices; text contrast preserved.
- Tests: mapping condition → state; reduced motion yields a static layer.

### 20D — Oil market reference card — **BLOCKED on source decision**
- Spec §8: AOVE / Virgen / Lampante, shared time axis, source, last update, reference wording
  ("no es el precio exacto que pagará tu cooperativa"); unavailable categories hidden.
- No free, licensed, machine-readable source is confirmed yet. Until the owner approves one
  (and its terms of use), the card shows **"Sin fuente configurada"** and nothing else.
- Implementation starts only after the source is approved and recorded here.

### 20E — Preferred cooperative notices — **BLOCKED on source decision**
- Spec §9: one latest notice / news item of the preferred cooperative; no cooperative →
  optional setup CTA; Home works normally.
- Needs either the private Admin web surface (later phase) or an approved per-cooperative
  feed (e.g. RSS). Preferred cooperative itself belongs to Phase 21 Profile; 20E may read an
  Organization marked as preferred only if that is approved as an interim step.

## Owner decisions (answered 2026-09-25)

| # | Decision | Needed before |
|---|---|---|
| D1 | Weather provider | **AEMET OpenData** (owner, 2026-09-25); owner obtains the API key |
| D2 | Radar tiles provider | **RainViewer** (owner, 2026-09-25) |
| D3 | Oil-market source and its terms of use | **Blocked** — card shows "Sin fuente configurada" |
| D4 | Cooperative notices source | **Wait for the private Admin surface**; until then the card offers choosing a cooperative and shows no notices |

## Non-goals
- No push notifications from feeds; no background polling workers for feeds in this phase.
- No Profile screen work (Phase 21) beyond reading what exists.
- No price prediction, no advice derived from market data.

## 20A implementation notes (Claude, branch `claude/phase20a-feeds`)

- **No schema change (Room stays v15).** The `weather_cache` table shipped in v1 and was never
  used; 20A reuses it as the device cache for feeds (`cache_key` = `FEED|place`), with its
  `source`, `fetched_at` and `expires_at`. Not synchronised, no outbox (device cache, not
  domain data). This replaces the "Room v16 `external_feed_cache`" line of the plan above.
- `domain/feed/Feed.kt`: `FeedKind` (freshness windows), `FeedState` (NotConfigured /
  NoLocation / Unavailable / Value with source, fetchedAt, stale), `FeedAge` ("Actualizado
  hace …"), `FeedLocation.common(...)` — the one municipality/province all farms share,
  accent- and case-insensitive; several places or none → no location (no GPS).
- `domain/weather/Weather.kt`: `WeatherNow`, `WeatherCondition`, `WeatherSource` (20B: AEMET),
  `WeatherFeed`, `WeatherCodec` (plain `key=value`, partial values rejected).
- `data/repository/CachedWeatherFeed.kt`: UI reads only the cache; `refreshIfStale` asks the
  source only when stale/missing, 10 s timeout; any failure keeps the last value and its time.
  Wired with `source = null` until 20B, so the app says "Sin fuente configurada".
- Inicio: `HomeContext` replaces the single "Pronto" line — weather (all states), oil market
  "Sin fuente configurada" (D3), cooperative notices "llegarán con el panel de administración"
  (D4). External cards stay below the farm, campaign, work and quick access.
- Tests: `FeedTest` (JVM), `WeatherFeedContractTest` (cache-first, no refetch when fresh, stale
  value survives a failure, one cache per place), `HomeFeedsScreenTest` (Gate 20 core: every
  feed failing leaves the farm usable; source/age/stale shown).

## 20B implementation notes (Claude, branch `claude/phase20b-weather`, CR-006)

Owner decisions (2026-09-25): AEMET key only in Supabase Edge Functions secrets; MET Norway as
automatic keyless fallback **inside** `weather-forecast`; `verify_jwt = true` with the public anon
key; generic municipality resolution; fixtures only in CI. This supersedes the "API key in
BuildConfig" note of the 20B slice above.

- **`supabase/functions/weather-forecast/`** (versioned, no secrets):
  - `handler.ts`: AEMET hourly municipal forecast → MET Norway only on error, timeout, rate
    limit (429) or invalid document; `502 providers_unavailable` when both fail. Never a second
    call when AEMET answers.
  - `municipalities.ts`: any Spanish municipality from AEMET's master list, by name within the
    province ("Bedmar" → "Bedmar y Garcíez", "La Carolina" ↔ "Carolina, La"); ambiguous names
    are refused (409), never guessed. The old four-town allowlist is gone.
  - Response: `provider`, `providerName`, `attribution`, `updatedAt` (provider), `fetchedAt`,
    `location`, `current` (temp, condition, rain probability or null, wind km/h or null).
  - `forecast.test.ts`: primary provider, fallback on error/429/timeout/invalid, double failure,
    coordinates fallback without the master list, bad requests, generic resolution.
- **`supabase/functions/weather-radar/`**: `{"operation":"frames"}` → RainViewer past frames as
  tile templates with time, provider and attribution; `radar.test.ts`. The radar screen in the
  app is the next slice (20B-radar); it is not part of this PR.
- **CI**: `.github/workflows/edge-functions.yml` runs the fixture tests with Node 22.
- **Android**:
  - `data/remote/weather/EdgeWeatherSource.kt`: HTTPS POST `{municipality, province}` with the
    anon key; `EdgeWeatherResponse` parses the contract strictly (unknown → failure).
  - The cache stores the provider that answered; Inicio shows "Fuente: AEMET/MET Norway ·
    Actualizado hace …" (provider time) and the provider's credit.
  - Anon key: Gradle property / env / `local.properties` `SUPABASE_ANON_KEY`; CI secret
    `SUPABASE_ANON_KEY` for the APK artifact only. Empty → "Sin fuente configurada". A
    `service_role`/secret key fails the build.
  - `ArchitectureBoundaryTest` updated per CR-006.
- **Deploy (owner):** `supabase functions deploy weather-forecast` and `weather-radar` from this
  source (keep JWT verification). Until then the deployed functions keep their old contract.

## Live deploy evidence (2026-09-25, stored 2026-09-27)

`docs/06-testing/evidence/phase20b/`: forecast 200, radar 200, keyless 401 (`verify_jwt` live).
Two findings carried into the next slices: the validation code 23019 is Campillo de Arenas (not
Bedmar y Garcíez; real code pending verification), and AEMET's hourly endpoint failed on that call
so MET Norway answered (fallback worked; reason to be read in the Supabase logs).

## Prepared plan — resumes only after Issue #246 closes (UX-G device check)

Documentation only; no production code until the Issue #246 gate is closed and the owner says
"continue with Phase 20". One slice at a time, each its own PR.

### 20B-fix — validation and labels (small, first) — MERGED (PR #257, owner OK)
Started 2026-09-27 on the owner's "puedes seguir" (Issue #246 stays open until its device check).
Done in the branch: validation call by name; provider written to the run summary; keyless status
kept as evidence; synthetic fixtures use the sentinel code 23000 for Bedmar (23019 is Campillo de
Arenas; 23902 is the unconfirmed candidate) and say so in `fixtures/README.md`. The AEMET failure
reason still needs the owner's Supabase log line; no timeout change without it.
- Deploy workflow: validation call by name `{"municipality":"Bedmar","province":"Jaén"}`; step and
  evidence names without a hard-coded code.
- `forecast.test.ts` synthetic master list: stop pairing 23019 with Bedmar (use the verified code
  or a neutral example code); fixtures stay offline.
- Owner reads the AEMET failure reason in the Supabase logs; if it is a timeout, raise
  `timeoutMs` for the AEMET call only (the MET Norway fallback keeps the card alive meanwhile).
- DoD: fixture tests green; one manual deploy run with new evidence stored beside the old one.

### 20B-radar — "Ver radar" screen — MERGED (PR #259, owner OK)
Implemented: `domain/weather/Radar.kt` (RadarFrames, RadarSource), `EdgeRadarSource` +
`EdgeRadarResponse` (strict: https template with {z}/{x}/{y}, attribution, ≥1 frame), shared
`EdgeFunctionHttp` POST (the forecast client uses it too, same behaviour), `RadarScreen` nested
under Inicio (`radar` route) with the active Farm's boundaries, the RainViewer layer at 70 %
opacity (source maxzoom 7, enlarged beyond), frame slider, "Radar de las HH:mm", credit and
"Actualizar"; offline → "El radar necesita conexión" + Reintentar. Inicio shows "Ver radar de
lluvia" only when the build has the public key. No Room change; radar is never cached.
- `data/remote/weather/EdgeRadarSource.kt`: HTTPS POST `{"operation":"frames"}` to
  `weather-radar` with the anon key (same boundary as `EdgeWeatherSource`, allowed by CR-006);
  strict parse of `provider`, `attribution`, `updatedAt`, `frames[time, tileUrlTemplate]`.
- Domain `RadarFrames` + `RadarSource`; no Room table (radar is live-only; frames are not cached as
  if they were fresh — spec §10 "requires connection and says so offline").
- UI: Inicio weather card → "Ver radar" → `RadarRoute` on the existing MapLibre view: the Farm's
  area, latest frame as a raster layer, frame time ("Radar de las 11:00"), attribution
  "Radar: RainViewer"; optional play/scrub of the past frames. Offline or error → message
  "El radar necesita conexión" and a retry; never a blank map pretending to be current.
- Route nested under Inicio (no new root; frozen nav CR-007).
- Tests: parse from the stored real response (`weather-radar-frames.json`) and from fixtures;
  offline state; frame time shown; `ArchitectureBoundaryTest` still passes.

### 20C — weather-responsive visual layer — IN PROGRESS (branch claude/phase20c-weather-mood)
Implemented: `domain/weather/WeatherMood.kt` (`WeatherMoods.of`: current value only; stale,
unknown or snow → none; wind ≥ 35 km/h turns clear/cloudy into WIND), `WeatherMoodLayer` drawn in
Inicio's photo header under the text (low opacity Canvas: glow, clouds, rain, streaks, fog,
storm with a soft 4 %-of-cycle glow, never a strobe). Still frame when the system animator scale
is 0 or the phone is low-RAM; the progress is read in the draw phase only (no recomposition).
- Pure mapping `WeatherCondition → WeatherMood` (CLEAR / CLOUDY / RAIN / WIND / FOG / STORM);
  unknown or stale → no mood (static header).
- Lightweight Compose drawing behind the Inicio header only; off when system animations are
  disabled (reduced motion) and simplified on low-performance devices; text contrast kept
  (WCAG AA against the brand tokens).
- Tests: mapping table (JVM); reduced motion ⇒ static; unknown ⇒ none.

### Still blocked
- 20D oil market (no approved source, D3) and 20E cooperative notices (D4, admin surface).
