# CR-008 — Tiempo: próximos 7 días y ubicación del tiempo (seguimiento de 20B)

**Status:** PROPOSED — pending owner decision (drafted 2026-09-28). Not approved: no production
work may start from this document.
**Baseline:** `main` at `bc3a1b7b`. Plan and estimates: `docs/07-plans/PHASE20B-FOLLOWUP-WEATHER-WEEK-LOCATION.md`.

## Motivation

The owner approved (Codex review on #281) that tapping the weather opens the next 7 days and that
header and weather share one municipality, with an optional «Seguir mi ubicación». Neither is in the
approved scope: Phase 20B delivered current weather only, and Phase 21 (Profile) covers the
preferred municipality but not location following. Gate 20 does not need them to close.

This CR exists so the work can be done **after Gate 20 PASS without mixing it into Phase 21's
Profile scope** (owner option W-1 (b)). If the owner chooses W-1 (a) instead — build W1 inside
Phase 20 before closing Gate 20 — this CR is withdrawn for W1 and only its W2 part remains.

## Proposed change

1. **W1 — Próximos 7 días (20B follow-up slice «20B-2»)**, executed after Gate 20 PASS and before
   21A production starts:
   - `weather-forecast` gains an additive `daily[]` (≤ 7 days; min/max, sky, rain probability,
     rain mm, wind; every field nullable). `current` unchanged; old clients unaffected.
   - Inicio's weather card opens «Próximos días» (nested route under Inicio); unknown values show
     «—»; source, attribution and both clocks («Previsión {fuente} de las HH:MM · consultada hace …»).
   - Function redeploy through the existing manual workflow; `verify_jwt=true`; secrets untouched.
   - Own gate check added to `PHASE20-GATE-CHECKLIST.md` as a 20B-2 addendum (B4, D5).
2. **W2 — «Seguir mi ubicación»**, delivered **with 21A** (which already owns «Tu municipio»):
   manual municipality and follow-location are mutually exclusive modes; opt-in coarse location,
   foreground only, never stored remotely; the function accepts coordinate-only requests
   (nearest AEMET municipality, MET Norway fallback). Gate 21 gains its check.

## Affected baseline sections

- `docs/07-plans/ROADMAP-RC1.2.md`: Phase 20 (20B-2 follow-up) and Phase 21 (21A adds follow-location).
- `docs/00-master/CURRENT-STATE.md`: 20B-2 listed as the only allowed production slice between
  Gate 20 PASS and 21A.
- `docs/07-plans/PHASE21-PROFILE.md` (21A scope line).
- CR-006 contract (`weather-forecast` response gains `daily[]`; request accepts coordinates only).

## Affected phases

- Phase 20: adds slice 20B-2 after the Gate, with its own addendum check; Gate 20's criterion is
  unchanged.
- Phase 21: 21A grows by «Seguir mi ubicación» (~1.5–2 days).

## Data impact

- W1: none in Room (the week is cached in `weather_cache.payload_json`); older cached payloads
  without `daily` still parse.
- W2: rides 21A's already-approved Room v17 `profile_settings` (one extra mode field); no extra
  migration.

## Offline/sync impact

Offline shows the last cached week with its age, never as current. Nothing is synchronized; the
position is never persisted remotely.

## UX impact

One new nested screen under Inicio; no root-tab change (CR-007 navigation untouched). Perfil gains
the location mode switch in 21A.

## Risks

- AEMET daily product format differs from the hourly one: mitigated by capturing a live fixture
  before coding; any invalid document falls back to MET Norway, never to invented values.
- Location privacy: opt-in, foreground only, coarse permission, no remote storage.
- Schedule: ~2 days (W1) delay before 21A starts.

## Alternatives considered

1. Keep the current baseline: current weather only; revisit after RC1.2.
2. W-1 (a): build W1 inside Phase 20 before closing Gate 20 (needs another APK and device test).
3. This CR (W-1 (b)): close Gate 20 on APK 580, then 20B-2, then 21A.

## Decision

PENDING — owner to choose W-1 (a) or (b). Recommended: (b), this CR.
