# Phase 3 — Design System + Reference Screens Execution Plan (RC1.2)

**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Precondition:** Gate 2 PASS.  
**Status:** ACTIVE — Gate 2 passed on 2026-09-18. Phase 3 implementation is allowed.

**Normative visual decision:** CR-003 + `docs/design/VISUAL_DESIGN_LOCK.md` + `docs/design/DESIGN_SYSTEM.md` supersede earlier provisional visual/naming language.

## Mandatory reference

Read all of these before creating reference screens:

- `docs/00-master/RC1.2-CHANGE-REQUEST-003-BRAND-VISUAL.md`
- `docs/design/VISUAL_DESIGN_LOCK.md`
- `docs/design/DESIGN_SYSTEM.md`
- `docs/design/reference/README.md`

- `docs/04-ui/BRAND-DIRECTION-RC1.2.md`
- `docs/04-ui/VISUAL-TOKENS-RC1.2.md`
- `docs/04-ui/COMPETITIVE-BENCHMARK-RC1.2.md`
- `docs/04-ui/CAMPAIGN-ANALYTICS-SCREEN-SPEC-RC1.2.md`
- `docs/04-ui/HOME-ONBOARDING-SPEC-RC1.2.md`
- `docs/04-ui/BRAND-ASSET-BRIEF-RC1.2.md`
- `docs/04-ui/FIGMA-BUILD-MANIFEST-RC1.2.md`


Before designing Campaign/Producción/Costes/Rentabilidad, read `docs/04-ui/COMPETITIVE-BENCHMARK-RC1.2.md` together with the approved Brand Direction.

## Goal

Turn the approved visual direction into a reusable Compose design system and a small set of reference screens that later feature branches must follow.

The product must feel modern, calm and agricultural without becoming rustic or visually overloaded.

## Design principles

Priority:

```text
1. field readability
2. simple actions
3. truthful information
4. visual consistency
5. attractive/premium finish
```

Visual character:

- light, clean surfaces;
- olive identity;
- strong contrast outdoors;
- generous touch targets;
- real olive/farm photography where useful;
- restrained cards;
- clear maps/charts;
- no wood/rustic cliché;
- no decorative dashboard clutter.

## Frozen primary navigation

`Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`

Phase 3 creates visual references only. Full navigation behavior belongs to Phase 4.

## Token system

Create project-owned Compose tokens rather than scattering raw values.

### Color direction

Canonical palette:

```text
OlivePrimary        #3E5A32
OlivePrimaryDark    #173122
Cream               #F8F6EE
WarmWhite           #FCFBF7
Sage                #A7B08F
Earth               #B88B6B
SoftGold            #D4B76A
TextSecondary       #6D746B
Outline             #E5E1D6
SurfaceSoft         #F4F1E8
Success             #4E7A45
Info                #5E7D8C
Warning             #C49842
Error               #B5534F
```

Do not communicate state by color alone.

### Typography

Prefer Android-native/system-friendly typography with excellent Spanish readability.

Scale:

- display/title for Farm/Home hero;
- headline for screen titles;
- title for cards/sections;
- body large for primary data entry;
- body medium for supporting detail;
- label for chips/status/source/freshness.

Avoid tiny captions outdoors.

### Spacing

Base grid:

```text
4 / 8 / 12 / 16 / 24 / 32 dp
```

Default screen horizontal padding: 20 dp. Compact widths may fall back to 16 dp.  
Large visual/hero sections use 20–24 dp internal spacing.

### Shape

Use restrained rounded corners:

- small controls: 10–12 dp;
- cards: 16–20 dp;
- hero/cover cards: 20–24 dp.

Do not make every surface a floating pill.

### Touch

Minimum touch target 48 dp.

## Core components to implement

1. `MoTopAppBar`
2. `MoBottomBar`
3. `MoPrimaryButton`
4. `MoSecondaryButton`
5. `MoIconButton`
6. `MoTextField`
7. `MoSelectField`
8. `MoDateField`
9. `MoStatusChip`
10. `MoSectionHeader`
11. `MoMetricCard`
12. `MoFarmCard`
13. `MoParcelRow`
14. `MoEmptyState`
15. `MoErrorState`
16. `MoOfflineBanner`
17. `MoSyncStatus`
18. `MoSourceFreshness`
19. `MoPhotoCover`
20. `MoChartContainer`
21. `MoListSkeleton`
22. `MoConfirmationSheet`
23. `MoBottomActionSheet`

All components must support accessibility semantics and dark-text contrast on outdoor-friendly light surfaces.

## Farm cover treatment

User-provided farm photography can appear as a visual identifier.

Rules:

- preserve image aspect ratio;
- center-crop for cards/hero;
- apply controlled scrim/gradient only where text overlays image;
- never place small low-contrast text directly on unknown photography;
- provide neutral fallback when no image exists;
- do not make a farm unusable if its image is local-only/pending sync.

## Chart language

Charts are analytical, never decorative.

Supported initial visual primitives:

- line chart;
- bar chart;
- simple comparison markers;
- empty/partial-data state.

Requirements:

- labels readable;
- accessible description/summary;
- no 3D charts;
- no misleading truncated axis without explicit intent;
- historical yield must disclose coverage if not all kg have analysis.

## Reference screen R1 — Onboarding

Create **6** reference pages matching the approved visual boards:

1. Bienvenido a Mágina Olivo.
2. Tus fincas y parcelas.
3. Mapa y Catastro.
4. Actividad y campaña.
5. Cosecha, gastos y documentos.
6. Tiempo, mercado y alertas.

Rules:

- one primary message per page;
- large visual;
- progress indicator;
- `Saltar` and `Siguiente`;
- final `Comenzar` action;
- cream/olive visual system;
- no account form embedded inside onboarding artwork.

## Reference screen R2 — Inicio

The Home hero/context area must include a weather-responsive visual prototype with CLEAR/CLOUDY/RAIN/WIND/FOG states. The effect must remain subtle, performant, readable and reduced-motion compatible.


Hierarchy:

1. location/weather compact header;
2. current olive-grove/campaign summary;
3. upcoming work;
4. oil market compact chart/card;
5. preferred cooperative notice/news.

Important:

- Mi Olivar and upcoming work visually outrank external information;
- external source freshness visible;
- stale/offline state graceful;
- no endless news feed.

## Reference screen R3 — Mi Olivar

Show:

- screen title;
- total farms/parcels/area summary;
- Farm cards with optional cover image;
- clear Add farm action;
- useful empty state.

Farm card prioritizes:

- farm name;
- parcel count;
- area when known;
- active campaign status;
- cover image if present.

## Reference screen R4 — Farm detail

Hero:

- farm cover image/fallback;
- farm name;
- municipality;
- parcel count/area;
- current campaign.

Then:

- parcel list;
- campaign summary;
- recent activity;
- add parcel action.

Map preview is only shown when geometry exists; it must not dominate before Phase 18.

## Reference screen R5 — Parcel detail

**Visual-reference gap:** no final generated board is committed yet. This screen must be designed during Phase 3 using the locked system before Gate 3.


Use a visually simple action chooser.

Families:

- Abonado;
- Tratamiento;
- Riego;
- Poda;
- Suelo/desbroce;
- Observación/Incidencia;
- Otro;
- separate Harvest/Delivery/Expense shortcuts where appropriate.

After type selection, typed form uses:

- common header;
- type-specific fields only;
- sticky primary Save when useful;
- draft protection.

## Reference screen R8 — Campaign history

Show:

- current/past campaign selector;
- kg summary;
- dates;
- yield summary + analysis coverage;
- expense summary;
- simple historical chart;
- comparison entry.

Avoid giant dashboards.

## Reference screen R9 — Cosecha

Use the committed harvest board as the visual target, but keep harvest/delivery truth rules intact.

## Reference screen R10 — Gastos y documentos

Use the committed expenses/documents board as the visual target. Expense ledger remains authoritative.

## Reference screen R11 — Tiempo, mercado y alertas

Use the committed weather/market board as the visual target. External data requires source/freshness states.

## Reference screen R12 — Delivery / OCR review

Flow reference:

```text
Add ticket image/PDF
→ OCR processing
→ extracted fields
→ confidence/review indicators
→ user corrects
→ Confirm delivery
→ yield remains Pending
```

Later analysis state:

```text
Delivery confirmed
→ Yield pending
→ Add yield analysis
→ history/chart updates
```

Critical visual rule: raw OCR output is visibly “Por revisar”; it must never look confirmed before user action.

## Responsive target

Primary focus is portrait Android phone.

Reference widths:

- compact ~360 dp;
- common ~393–412 dp;
- large phone ~480 dp.

Tablet adaptation can use wider content constraints but must not delay phone RC1.2.

## Accessibility gate

Each reference screen must pass:

- minimum touch targets;
- TalkBack semantics for actionable controls;
- no color-only state;
- scalable text without clipped primary actions;
- meaningful content descriptions for non-decorative imagery;
- reduced-motion friendly behavior for any animation.

## Motion

Use subtle motion only for:

- screen/section transitions;
- expanding/collapsing forms;
- loading/confirmation feedback.

Do not use constant decorative animation on core field screens.

## Approved branding rule

CR-003 freezes the customer-facing display brand as **Mágina Olivo** and the approved olive-branch logo direction.

The current Android package/namespace remains unchanged in Phase 3. Store-ready icon/export verification remains a later Branding Gate task.

## Visual asset rule

Before generating/commissioning final promotional illustrations:

1. freeze layout;
2. freeze image aspect ratios;
3. define required visual subject;
4. then create/source images.

This prevents design from being driven by random imagery.

## Screenshot/reference approval package

Phase 3 should produce:

- 8 reference screens at common phone size;
- 3 key screens at compact width;
- light-theme component catalogue;
- empty/error/offline states;
- chart examples with truthful sample labels marked as design fixtures;
- component/token documentation.

Design fixtures must never be shipped as production agricultural data.

## Gate 3 PASS condition

- visual references are coherent;
- all primary components are reusable Compose components;
- no branch-specific ad-hoc theme values;
- navigation appearance matches RC1.2;
- six-page onboarding, Home, Mi Olivar, Farm, Parcel, Map/Catastro, Register, history, harvest, expenses/documents, weather/market and OCR review references approved;
- accessibility checks pass;
- representative physical Android rendering reviewed.

Only after Gate 3 may Phase 4 implement the real navigation shell.