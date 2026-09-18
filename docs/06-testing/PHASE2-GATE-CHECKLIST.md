# Phase 2 — Gate Checklist

**Phase:** Base Application Architecture  
**Baseline:** `RC1.2-BASELINE-2026-09-18`

## Architecture

- [x] AppResult/AppError contract exists.
- [x] Validation precondition helper exists.
- [x] AppClock + SystemAppClock exist.
- [x] IdGenerator + UUID implementation exist.
- [x] AppDispatchers exists.
- [x] AppLogger exists.
- [x] RegionalContext is explicit.
- [x] UnitPreferences are explicit.
- [x] AppEnvironment is typed.
- [x] AppCompositionRoot is explicit.
- [x] AppRoot sits between MainActivity and Compose content.
- [x] No Hilt/Koin/service locator introduced.
- [x] No Gradle multi-module split introduced.

## Scope protection

- [x] No Room.
- [x] No Supabase.
- [x] No MapLibre.
- [x] No WorkManager.
- [x] No Farm/Parcel/Campaign entities.
- [x] No navigation shell.
- [x] No OCR/weather/notification implementation.

## Automated tests

- [ ] `lintDevDebug` passes.
- [ ] `testDevDebugUnitTest` passes.
- [ ] `assembleDevDebug` passes.
- [ ] `assembleStagingDebug` passes.
- [ ] `assembleProductionDebug` passes.
- [ ] instrumented test APK compiles.
- [ ] existing launcher smoke test remains valid.
- [ ] architecture boundary tests pass.

## Review

- [ ] Compose UI does not create database/network/provider infrastructure.
- [ ] Core contracts do not contain Jaén-only assumptions.
- [ ] production application ID remains stable.
- [ ] no sensitive-data logging is introduced.

## Physical

- [ ] Foundation launcher still installs/opens after Phase 2 refactor.

## Gate result

**PENDING CI + review + physical smoke.**

Phase 3 must not start in implementation until this Gate is PASS.
