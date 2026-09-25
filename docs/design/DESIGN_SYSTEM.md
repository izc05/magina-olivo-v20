# Mágina Olivo — Design System Oficial

**Status:** LOCKED / canonical visual implementation contract  
**Baseline:** RC1.2 + CR-003  
**Reference:** `docs/design/VISUAL_DESIGN_LOCK.md`  
**Visual boards:** `docs/design/reference/`

## 1. Visual DNA

Mágina Olivo combines:

- olive-growing territory;
- warmth;
- clarity;
- simple technology;
- calm premium finish;
- outdoor readability;
- direct usability for farmers.

It must not drift into generic ERP, banking-dashboard, rustic cliché or overly decorative UI.

## 2. Core palette

| Token | Hex | Primary use |
|---|---:|---|
| MoOlivePrimary | #3E5A32 | primary actions, active states, brand |
| MoOliveDark | #173122 | headings, strong text, brand contrast |
| MoCream | #F8F6EE | app background |
| MoWarmWhite | #FCFBF7 | raised surfaces |
| MoSage | #A7B08F | supporting surfaces, muted accents |
| MoEarth | #B88B6B | warm territorial accents |
| MoSoftGold | #D4B76A | value/market/highlight accents |
| MoTextSecondary | #6D746B | secondary text |
| MoOutline | #E5E1D6 | soft borders/dividers |
| MoSurfaceSoft | #F4F1E8 | subtle card/field background |
| MoSuccess | #4E7A45 | success |
| MoInfo | #5E7D8C | information |
| MoWarning | #C49842 | warnings |
| MoError | #B5534F | errors |

Rules:

- color never communicates status alone;
- warm light surfaces dominate;
- saturated colors are accents, not large backgrounds;
- external-data freshness/staleness must have text/icon semantics in addition to color.

## 3. Typography

Use a dual-family system:

- **Editorial serif** for brand-led display/headline moments;
- **Operational sans serif** for data, forms, navigation, labels and dense information.

Phase 3 may use Android system Serif/SansSerif families until a production-safe font family is explicitly added.

Recommended semantic scale:

| Token | Suggested size | Family | Use |
|---|---:|---|---|
| displayLarge | 40sp | Serif | onboarding hero |
| displayMedium | 34sp | Serif | major editorial title |
| headlineLarge | 30sp | Serif | screen hero |
| headlineMedium | 26sp | Serif | screen title |
| titleLarge | 22sp | Sans | major section |
| titleMedium | 18sp | Sans | card title |
| bodyLarge | 17sp | Sans | primary reading/input |
| bodyMedium | 15sp | Sans | supporting text |
| labelLarge | 14sp | Sans | buttons/chips |
| labelMedium | 12sp | Sans | metadata only |

Primary actions and critical values must remain legible under Android font scaling.

## 4. Spacing

Base scale:

`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 dp`

Rules:

- default screen horizontal padding: **20dp**;
- compact fallback: **16dp** where width requires;
- section gaps: 24–32dp;
- card internal padding: 16–20dp;
- hero internal padding: 24dp;
- minimum touch target: 48dp.

## 5. Shapes

| Token | Radius |
|---|---:|
| extraSmall | 8dp |
| small | 12dp |
| field | 16dp |
| card | 20dp |
| cardLarge | 24dp |
| hero | 28dp |
| pill | 50% / full |

Do not make every component a pill. Large cards remain softly rounded rectangles.

## 6. Elevation

Elevation is restrained.

- base surface: 0dp;
- standard card: 1–2dp visual elevation;
- raised/interactive card: 2–4dp;
- modal/sheet: Material system elevation as needed.

Prefer tonal separation + outline over heavy shadows.

## 7. Iconography

Use one coherent outlined icon family with medium stroke.

Core concepts:

- finca;
- parcela;
- olivo;
- mapa;
- calendario;
- campaña;
- actividad;
- poda;
- tratamiento;
- riego;
- abonado;
- cosecha;
- entrega;
- gasto;
- documento;
- cámara;
- clima;
- mercado;
- alertas.

Icons must never mix filled, cartoon and photorealistic styles inside the same control family.

## 8. Photography and brand imagery

Approved language:

- real olive groves;
- mountain/territorial context;
- warm natural light;
- restrained saturation;
- olive branches and fruit;
- subtle topographic line motifs;
- decorative olive leaves with low visual weight.

Rules:

- unknown photography requires a scrim before overlaid text;
- photographs do not replace structured data;
- territorial imagery is branding, not a domain restriction;
- references may use illustrative values only as design fixtures.

## 9. Core Compose components

Phase 3 component catalogue:

1. `MoPrimaryButton`
2. `MoSecondaryButton`
3. `MoIconButton`
4. `MoTextField`
5. `MoSelectField`
6. `MoDateField`
7. `MoStatusChip`
8. `MoSectionHeader`
9. `MoMetricCard`
10. `MoFarmCard`
11. `MoParcelRow`
12. `MoEmptyState`
13. `MoErrorState`
14. `MoOfflineBanner`
15. `MoSyncStatus`
16. `MoSourceFreshness`
17. `MoPhotoCover`
18. `MoChartContainer`
19. `MoListSkeleton`
20. `MoConfirmationSheet`
21. `MoBottomActionSheet`
22. `MoBottomBar`
23. `MoTopAppBar`

Every component must expose semantics-friendly labels and work with font scaling.

## 10. Button contract

### Primary

- olive background;
- high-contrast light text;
- 52–56dp visual height;
- 16–20dp radius;
- optional trailing icon;
- disabled state uses tonal reduction, never opacity-only ambiguity.

### Secondary

- warm-light or transparent surface;
- olive/dark text;
- subtle outline when needed;
- same touch target as primary.

## 11. Card contract

Standard card:

- warm white / soft cream;
- 20dp radius;
- 16–20dp content padding;
- low elevation;
- optional hairline outline;
- no nested-card clutter unless hierarchy requires it.

Metric card:

- small label;
- dominant value;
- optional trend/state line;
- unit adjacent to value;
- truthful unknown state instead of fake zero.

## 12. Field contract

Inputs must be large, direct and field-friendly.

- 52dp+ touch height;
- visible label;
- large value text;
- optional leading icon;
- errors include text;
- select/date fields visually align with text fields;
- forms use progressive sections, never one giant agricultural form.

## 13. Navigation appearance

The **visual appearance** of navigation follows the approved references:

- warm light bar;
- five destinations;
- active item olive;
- icon + label;
- generous touch areas;
- minimal dividers/shadows.

The **root destinations** (CR-007, Issue #246):

`Inicio · Mi Campo · Cuaderno · Avisos · Perfil` — Cuaderno is the centre item.

Reference images with alternative labels remain composition/style references only.

## 14. Six-screen onboarding

1. **Bienvenido a Mágina Olivo**  
   Value proposition + territorial/brand connection.

2. **Tus fincas y parcelas**  
   Explain farm → parcel → campaign organization.

3. **Mapa y Catastro**  
   Explain locate/import/draw/reference parcel workflows.

4. **Actividad y campaña**  
   Explain work, dates, photos, costs and campaign traceability.

5. **Cosecha, gastos y documentos**  
   Explain kg, deliveries, yields, expenses and document organization.

6. **Tiempo, mercado y alertas**  
   Explain weather/radar, oil-market references and useful alerts; final CTA `Comenzar`.

Shared contract:

- logo/brand at top;
- `Saltar` action;
- large visual;
- one primary message;
- progress dots;
- olive CTA;
- cream background;
- no account form embedded in artwork.

## 15. Screen visual hierarchy

### Inicio

1. brand/context;
2. user's current agricultural status;
3. upcoming work;
4. weather/context;
5. oil-market reference;
6. preferred cooperative notice/news.

External feeds never outrank or block agricultural operation.

### Mi Olivar / Fincas

- title + summary;
- add farm action;
- visual farm cards;
- area/parcel/campaign state;
- useful empty state.

### Farm detail

- cover hero;
- name/location;
- area/parcel/campaign metrics;
- parcel list;
- campaign/recent activity;
- contextual actions.

### Map/Catastro

- map/orthophoto is protagonist when geometry exists;
- boundaries clearly visible;
- selected parcel uses translucent olive highlight;
- bottom information sheet;
- search/layers/location/zoom controls;
- no invented geometry.

### Campaign

- current/history switch;
- kg/yield/delivery/expense metrics;
- truthful partial-data states;
- simple historical charts;
- key dates.

### Register activity

- typed action chooser;
- finca/parcela context;
- date;
- only type-relevant fields;
- photos/notes;
- cost link where appropriate;
- clear save CTA.

### Harvest

- collected kg;
- delivery summary;
- yield only where confirmed;
- parcel/campaign breakdown without fabricated allocation.

### Expenses/Documents

- monthly/campaign summary;
- categories;
- add expense/upload document actions;
- recent documents;
- expense ledger remains authoritative.

### Weather/Market

- forecast;
- radar access;
- weather alerts;
- AOVE/Virgen/Lampante only when source supports categories;
- source/freshness;
- preferred-cooperative context.

## 16. Empty, loading, error and offline states

Visual parity includes non-happy paths.

Required states:

- first-run/no farm;
- farm without parcels;
- campaign without data;
- map without geometry;
- loading;
- error;
- external source unavailable;
- stale external source;
- offline;
- sync pending;
- attachment pending upload.

No state may fall back to raw/default Material styling.

## 17. Accessibility

Gate requirements:

- 48dp minimum touch targets;
- TalkBack semantics;
- no color-only meaning;
- scalable type;
- decorative images excluded from semantics;
- meaningful descriptions for informative images;
- reduced-motion support;
- outdoor contrast verification;
- primary actions remain visible at large font scale.

## 18. Visual acceptance gate

A screen is not done until compared against its canonical reference for:

- composition;
- hierarchy;
- spacing;
- typography;
- color;
- shapes;
- elevation;
- iconography;
- imagery;
- system insets;
- text scaling;
- empty/loading/error/offline state;
- representative physical Android rendering.

The target is deliberate reproduction of the approved visual language, not generic reinterpretation.
