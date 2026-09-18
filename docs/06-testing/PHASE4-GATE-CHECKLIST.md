# Phase 4 — Gate Checklist

**Status:** PREPARED / NOT ACTIVE  
**Prerequisite:** Gate 3 PASS

## Navigation shell

- [ ] AndroidX Navigation Compose introduced only after Gate 3.
- [ ] One app-level NavHost.
- [ ] Root destinations:
  - [ ] Inicio
  - [ ] Mi Olivar
  - [ ] Calendario
  - [ ] Perfil
- [ ] Registrar uses a center action + action sheet.
- [ ] Bottom bar visibility follows destination hierarchy.
- [ ] No duplicate root entries in back stack.
- [ ] Back from nested screen returns to parent.
- [ ] Back dismisses Register sheet before leaving current root.
- [ ] Re-tapping root returns to root/top state where practical.

## Nested shell routes

- [ ] Finca route.
- [ ] Parcela route.
- [ ] Campaña route.
- [ ] Producción route.
- [ ] Costes route.
- [ ] Rentabilidad route.
- [ ] Entrega OCR review route.
- [ ] Factura OCR review route.

These are shell/reference routes only in Phase 4. Do not imply persisted agricultural records exist before their feature phases.

## DEV-only design gallery

- [ ] Gallery removed from normal DEV startup.
- [ ] Gallery remains accessible through a developer-only entry.
- [ ] STAGING/PRODUCTION never expose the design gallery.

## State

- [ ] Selected root survives ordinary recreation.
- [ ] Route parsing is deterministic.
- [ ] Rotation does not reset selected root unexpectedly.
- [ ] No feature business data is stored inside navigation state.

## Accessibility

- [ ] Selected root announced to TalkBack.
- [ ] Register action has explicit description.
- [ ] Bottom-nav labels remain visible.
- [ ] Back behavior is predictable.

## Tests

- [ ] Opens on Inicio.
- [ ] Each root can be selected.
- [ ] Roots do not duplicate in stack.
- [ ] Register sheet opens/closes.
- [ ] Back closes Register sheet first.
- [ ] Nested sample route returns to parent.
- [ ] Rotation preserves selected root.
- [ ] DEV gallery is reachable only in DEV.

## Scope protection

- [ ] No Room.
- [ ] No Supabase.
- [ ] No real Farm/Parcel persistence.
- [ ] No OCR engine.
- [ ] No weather provider.
- [ ] No notification scheduler.
- [ ] No Catastro provider.

## Physical

- [ ] Navigation reviewed on real Android phone.
- [ ] No dead destinations.
- [ ] No repeated/crashing Back behavior.
- [ ] Bottom action is easy to reach one-handed.

## Gate result

**NOT STARTED.**

Phase 5 local database work remains blocked until Gate 4 PASS.
