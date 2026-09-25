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
- `WeatherSource` over HTTPS, keyless provider (candidate: Open-Meteo forecast API;
  attribution shown with the value). Current temp, condition, rain probability next hours,
  wind; optional alert chip only if the source provides one.
- Refresh on Home open when stale (short timeout), never in a loop; manual retry.
- "Ver radar": requires connection and says so offline (spec §10). Radar tiles provider to
  be confirmed (candidate: RainViewer tiles on the existing MapLibre view).
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

## Owner decisions needed

| # | Decision | Needed before |
|---|---|---|
| D1 | Weather provider (proposal: Open-Meteo, keyless, CC BY 4.0 attribution) | 20B |
| D2 | Radar tiles provider (proposal: RainViewer) or radar deferred | 20B radar |
| D3 | Oil-market source and its terms of use | 20D |
| D4 | Cooperative notices source (Admin surface vs. feed) and interim preferred cooperative | 20E |

## Non-goals
- No push notifications from feeds; no background polling workers for feeds in this phase.
- No Profile screen work (Phase 21) beyond reading what exists.
- No price prediction, no advice derived from market data.
