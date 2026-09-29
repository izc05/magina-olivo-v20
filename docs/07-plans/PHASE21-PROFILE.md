# Phase 21 — Profile (plan)

Status: **ALLOWED 2026-09-29** (Gate 20 PASS 2026-09-28; Gate CR-010 PASS and CR-011 closed
2026-09-29). Prepared 2026-09-27.

**Gate 21:** preferences persist offline and account-sensitive operations are protected.

## What exists today (Perfil)
Maquinaria · Notificaciones (system settings) · Modo sin conexión (explanation) · Cuenta y
sincronización («Pronto») · Acerca de (version + build, CHANGELOG-APP) · Datos de parcelas
(Catastro notice) · DEV catalogue. The workspace row already stores country, timezone, locale and
currency; reusable Organizations (cooperative/mill/supplier/irrigation) already exist.

## Principles
- Local first: every preference is written on the phone and works in airplane mode; sync of the
  profile belongs to Phase 22/23 (it only needs to be representable, not synchronized yet).
- Nothing account-sensitive is offered before auth exists (Phase 22): «Cuenta» stays honest.
- Geographic-neutral model; Spain is the first data set (municipality list as for weather).
- No duplicate cooperative: the preferred cooperative is a reference to an existing
  Organization, not a copied name.
- Directory research: DOP Sierra Mágina is an official territorial reference for entity/brand
  verification, but its current legal notice reserves commercial reuse. Do not bulk-import or
  automate its directory into production without permission/licensing; allow user-created
  Organizations and keep the model source-agnostic.

## Slices (one PR each)

### 21A — Mi perfil: locality + preferred cooperative
- «Tu municipio» (Spain list, same source as the weather municipality lookup) and
  «Tu cooperativa» (pick an existing cooperative/mill Organization or create one).
- Stored locally; proposed **Room v19** `profile_settings` (v17 and v18 are taken by CR-010, Amendment 1 A4) (one row per workspace: municipality
  code/name, preferred organization id, updated_at, sync metadata) so it is sync-ready.
- Inicio uses it: weather for the municipality when the active Farm has no location; the
  cooperative card names the chosen cooperative (still no notices until 20E).
- Tests: migration 18→19, contract (persists across restart, organization reference kept after
  rename, cleared if the organization is archived), screen test.

### 21B — Preferencias
- Avisos: planned-work reminders on/off and default advance (today: per activity); irrigation
  reminders follow the same switch. Respect Android's notification permission state.
- Formato: date/number format and units shown read-only from the workspace (kg, ha, €); no
  conversion engine in RC1.2.
- Tests: preferences survive restart; reminders scheduled/unscheduled accordingly.

### 21C — Ayuda, privacidad y datos
- «Qué hay de nuevo» (reads docs/CHANGELOG-APP.md content bundled at build time),
  privacy text (what stays on the phone, which external services are called: weather, radar,
  Catastro), help for offline use.
- «Exportar copia» only if cheap and local (JSON/CSV of the Cuaderno to share); otherwise
  deferred to Phase 25 (Reports/PDF).

## Out of scope
Login, account deletion, cloud backup and multi-device sync (Phases 22–23); professional/client
mode (post-RC1.2); loyalty/Mi Olivo.

## Owner decisions needed
| # | Decision | Answer (owner «Ok», 2026-09-27) |
|---|---|---|
| P1 | Store the profile in Room (sync-ready) or device preferences | **Room** (v19 after CR-010 takes v17 and v18) |
| P2 | Default reminder advance for planned work | **1 day before, 08:00** |
| P3 | «Exportar copia» in 21C or wait for Phase 25 | **wait for Phase 25** |
