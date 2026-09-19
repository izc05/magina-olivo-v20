# Phase 4 — Production navigation shell implementation plan

**Status:** In progress  
**Precondition:** Gate 3 PASS  
**Branch:** `feat/android-navigation`  
**Stacked base:** `integrate/android-magina-olivo-rc1`

## Contract

Implement one production `NavHost` with the frozen roots:

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

This phase may route to approved reference content and explicit placeholders. It must not add Room, Supabase, remote calls or real agricultural persistence.

## Architecture

- AndroidX Navigation Compose 2.10.1, the current stable Navigation release compatible with the existing API 37 / AGP 9.2.1 toolchain.
- One route registry owned by the app navigation package.
- One root navigation helper implementing `popUpTo`, state saving/restoration and single-top behavior.
- One shared canonical bottom bar for the main shell.
- Root selection derived from the back stack, never held as a second competing state.
- `Registrar (+)` opens a contextual modal sheet before entering the register route.
- Nested shell routes cover farm, parcel, campaign, analytics, Map/Catastro, weather and OCR review.
- The component catalogue is reachable only in DEV builds through Perfil.
- First-run completion is stored behind an injected onboarding-state contract; this preference is not agricultural persistence.

## Implementation sequence

1. Add Navigation Compose and navigation-testing dependencies.
2. Add failing unit tests for route classification and root selection.
3. Add failing instrumentation tests for onboarding → Home, all five roots, repeated-root single-top behavior, nested back navigation, Register sheet and DEV gallery access.
4. Implement route definitions and root-navigation options.
5. Implement the single NavHost and shared shell.
6. Make Home omit its reference-only embedded bar when hosted by the production shell.
7. Add minimal callback seams to existing Mi Olivar/farm reference content so nested routes are genuinely reachable.
8. Add accessible Calendario and Perfil placeholders without inventing later-phase data.
9. Add the context-aware Register sheet and navigate to the approved register reference.
10. Add injected onboarding completion storage and verify relaunch behavior.
11. Run unit tests, lint, all environment builds and the full Android instrumentation suite.
12. Capture emulator evidence, document Gate 4 and update `CURRENT-STATE.md` only if every blocking check passes.

## Gate 4 tests

- first launch can complete/skip onboarding and enter Inicio;
- completed onboarding is not shown on relaunch;
- every root is reachable and selected correctly;
- revisiting a root does not create duplicate root destinations;
- back from a nested farm/parcel/campaign route returns predictably;
- back from a non-start root returns to Inicio before exiting;
- Register sheet opens, cancels and enters a register flow;
- process/configuration recreation restores the active navigation state reasonably;
- no route is dead;
- DEV gallery is unavailable outside DEV configuration;
- no crash/ANR appears in emulator evidence.

## Gate decision rule

Gate 4 remains FAIL until instrumentation navigation tests and Android emulator navigation smoke pass. A real-device navigation/install smoke remains required before RC1.
