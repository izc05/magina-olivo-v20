# CR-008 — «Seguir mi ubicación» para el tiempo (con 21A)

**Status:** PROPOSED — pending owner decision (drafted 2026-09-28). Not approved: no production
work may start from this document.
**Baseline:** `main` at `bc3a1b7b`. Plan and estimates: `docs/07-plans/PHASE20B-FOLLOWUP-WEATHER-WEEK-LOCATION.md` (W2).

**Scope note (2026-09-28):** the 7-day forecast (W1 in the plan) is **not** in this CR any more.
The owner confirmed it inside Phase 20 with the simplified Inicio: CR-009 / PR #284 (Codex), which
re-validates Gate 20 with checks B4/D5. This CR keeps only the location part, which CR-009 leaves
out of scope («sin GPS ni selector nuevo»).

## Motivation

The owner's criterion (Codex review on #281): header and weather share one municipality, the manual
choice stays, and «Seguir mi ubicación» is offered only with explicit permission, foreground only,
never hidden background tracking. Phase 21A already owns «Tu municipio» (Room v19
`profile_settings`, approved as P1); location following is in no approved scope.

## Proposed change

«Seguir mi ubicación», delivered **with 21A** after Gate 20 PASS:

- **two mutually exclusive modes** for Inicio's place: «Municipio elegido» (manual) or «Seguir mi
  ubicación» (only while switched on). Turning one on turns the other off. With neither set: the
  common Farm municipality → «Elige tu municipio»;
- opt-in switch; asks `ACCESS_COARSE_LOCATION` only then; reads the position only while Inicio is in
  the foreground; never stored remotely. Without a position Inicio says so and keeps the last shown
  value with its age — it never silently switches back to the manual municipality;
- `weather-forecast` accepts coordinate-only requests (today `validRequest()` answers 400): nearest
  municipality from the AEMET list (it carries `latitud_dec`/`longitud_dec`), MET Norway by
  coordinates when the list is unreachable; coordinate-only fixture test; authorized redeploy.

## Affected baseline sections

- `docs/07-plans/PHASE21-PROFILE.md` (21A scope line); `docs/07-plans/ROADMAP-RC1.2.md` Phase 21.
- CR-006 contract (request accepts coordinates only).

## Affected phases

Phase 21: 21A grows by ~1.5–2 days; Gate 21 gains the location-mode check. Gate 20 unchanged.

## Data impact

Rides 21A's approved Room v19 `profile_settings` (one extra mode field); no extra migration.

## Offline/sync impact

None synchronized; the position is never persisted remotely. Offline keeps the cached weather with
its age.

## UX impact

Perfil (21A) gains the location mode; Inicio shows which mode/place it uses. No root-tab change.

## Risks

Location privacy (mitigated: opt-in, foreground, coarse, no remote storage); a wrong nearest
municipality near borders (mitigated: the header always names the municipality used).

## Alternatives considered

1. Keep manual municipality only (21A as approved) — simplest; no permission at all.
2. This CR: add the opt-in location mode to 21A.

## Decision

PENDING — owner decision before 21A starts. Recommended: this CR, or alternative 1 if the owner
prefers no location permission in RC1.2.
