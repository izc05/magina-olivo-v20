# Phase 4 — Navigation Shell Execution Plan

**Status:** PREPARED ONLY — IMPLEMENTATION BLOCKED UNTIL GATE 3 PASS  
**Baseline:** `RC1.2-BASELINE-2026-09-18`

## Goal

Replace the DEV design-gallery routing with the real application navigation shell while keeping feature roots intentionally shallow.

Frozen roots:

```text
Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil
```

Phase 4 is navigation infrastructure, not feature implementation.

## Root destinations

### Inicio
Route: `home`

Initial content after Phase 4:
- Phase 3 Home reference promoted to a real root shell;
- external data remains fixture-free / placeholder-safe until later phases;
- no hard dependency on weather/market/network SDKs.

### Mi Olivar
Route: `olive-grove`

Initial content:
- empty/local placeholder root;
- links to Farm/Parcel destinations may exist only where reference data is safe;
- real farm persistence starts in Phase 6.

### Registrar
Not a permanent stack root screen.

Behavior:
- center bottom action opens a modal/bottom action sheet;
- action families are shown;
- selecting an action may route to a typed placeholder screen;
- no agricultural save logic until the corresponding feature phase.

### Calendario
Route: `calendar`

Initial content:
- shell/reference view only;
- no notification scheduler or persisted planning until later phases.

### Perfil
Route: `profile`

Initial content:
- shell/reference settings surface;
- no remote account mutation until backend/account phases.

## Nested reference routes

Allowed shell-only nested routes:

```text
farm/{id}
parcel/{id}
campaign/{id}
campaign/{id}/production
campaign/{id}/costs
campaign/{id}/profitability
ocr/delivery-review
ocr/invoice-review
```

These routes may render controlled placeholder/reference states in Phase 4. They must not imply persisted entities exist before their feature phases.

## Back-stack rules

1. Tapping a root destination switches root without stacking duplicate roots.
2. Back from a nested route returns to its parent context.
3. Back from a root does not cycle through previously tapped root tabs.
4. Register sheet dismisses with Back before leaving the current root.
5. Re-tapping the selected root returns to that root's top state where practical.
6. Deep-link support is deferred unless explicitly required by a later phase.

## State restoration

Required:

- selected root survives ordinary Compose recreation;
- nested route arguments are stable;
- orientation change does not lose the selected root;
- process-death persistence is not required until navigation/state infrastructure justifies it, but route parsing must remain deterministic.

## Navigation implementation

Preferred:

- AndroidX Navigation Compose;
- typed/project-owned route constants or route model;
- one NavHost at app-shell level;
- bottom bar visibility derived from destination hierarchy.

Avoid:

- hand-written mutable screen booleans;
- multiple competing NavHosts;
- feature-specific navigation libraries;
- route strings scattered across UI components.

## Register action sheet

Initial choices:

- Trabajo
- Gasto / compra
- Entrega
- Documento

Trabajo expands or routes to type choice:

- Riego
- Tratamiento
- Abonado
- Poda
- Suelo/desbroce
- Observación/incidencia
- Otro

The Phase 4 shell may only demonstrate routing. Saving belongs to later feature phases.

## DEV reference gallery

The Phase 3 design gallery remains accessible in DEV through a developer-only entry, not as the production navigation model.

Suggested DEV entry:

`Perfil → Desarrollo → Galería de diseño`

This preserves visual QA without contaminating normal navigation.

## Accessibility

- root destinations expose selected state to TalkBack;
- Register center action has explicit content description;
- no icon-only ambiguous actions;
- Back behavior is predictable;
- bottom navigation labels remain visible.

## Instrumentation tests

Minimum:

1. app opens on Inicio;
2. each root can be selected;
3. roots do not stack duplicates;
4. Register sheet opens/closes;
5. Back closes Register sheet first;
6. nested sample route returns to parent;
7. rotation preserves selected root;
8. DEV gallery remains reachable only in DEV.

## Gate 4 PASS

- all five roots work;
- no dead routes;
- no duplicate-root back-stack defects;
- Register modal behavior is stable;
- instrumentation navigation tests pass;
- no Phase 5+ persistence/domain logic introduced;
- physical Android navigation smoke reviewed.

Do not begin Room/local database implementation until Gate 4 PASS.
