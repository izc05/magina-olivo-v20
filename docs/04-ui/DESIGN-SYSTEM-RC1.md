# Olive Farm App — Design System RC1.2

**Status:** Phase 0.7 draft
**Intent:** modern olive agriculture, simple field technology, excellent readability and low cognitive load.
**Baseline:** `RC1.2-BASELINE-2026-09-18` — follow `RC1.2-PRODUCT-LOCK.md`.

## 1. Visual identity

The olive-farm product should feel:

- calm;
- precise;
- modern;
- agricultural without becoming rustic;
- trustworthy;
- easy to use outdoors.

Avoid:

- wood/earth texture clichés;
- overly dark agricultural palettes;
- ornamental dashboards;
- tiny controls;
- excessive card nesting;
- decorative charts without operational value.

## 2. Color system

Use semantic design tokens, not hard-coded feature colors.

Token families:

```text
primary / onPrimary
primaryContainer / onPrimaryContainer
secondary / onSecondary
surface / surfaceVariant / surfaceContainer
onSurface / onSurfaceVariant
outline
success / warning / error / info
mapParcelOwned
mapParcelSelected
mapParcelCandidate
mapParcelHistoric
```

Identity direction:

- olive green as primary accent;
- light neutral surfaces;
- restrained secondary tones;
- strong contrast for outdoor readability.

Exact production color values are approved during visual implementation/prototyping and then frozen as tokens.

Map semantics must remain distinguishable by more than color alone where practical (stroke weight/fill/pattern/labels).

## 3. Typography

Use Android/Material-compatible typography optimized for legibility.

Semantic styles:

```text
DisplayCampaignMetric
ScreenTitle
SectionTitle
CardTitle
Body
BodyStrong
Label
Caption
NumericMetric
```

Rules:

- campaign/production numbers use tabular or stable-width numeric presentation if the selected font supports it;
- never use tiny text to fit more fields;
- support Android font scaling;
- long farm/parcel names wrap gracefully.

## 4. Spacing

Base spacing scale conceptually:

```text
4
8
12
16
24
32
48 dp
```

Use semantic spacing tokens rather than arbitrary per-screen values.

Primary screen horizontal padding should be consistent across the app.

## 5. Touch targets

Important controls must be comfortably usable with one hand in the field.

Rules:

- respect Android/Material minimum touch-target guidance;
- primary actions use large surfaces;
- avoid tiny icon-only destructive actions;
- overflow menus hold infrequent/destructive options.

## 6. Main shell

Bottom navigation is fixed to:

```text
Inicio
Fincas
Campaña
Registrar
Más
```

`Registrar` must be visually prominent but not so unusual that it breaks standard navigation expectations.

Implementation may choose a central action treatment/FAB pattern after real-device prototyping, while preserving destination semantics.

## 7. Top app bars

Root screens:

- simple title;
- optional contextual action/search.

Detail screens:

- back;
- title;
- overflow for edit/archive/delete where appropriate.

Do not crowd app bars with more than essential actions.

## 8. Buttons

Standard variants:

```text
PrimaryButton
SecondaryButton
TonalButton
TextButton
IconButton
DestructiveConfirmationButton
```

Primary action per screen should normally be singular and obvious.

Examples:

- `Crear finca`;
- `Añadir a mi finca`;
- `Guardar`;
- `Cerrar campaña` only in an explicit confirmation flow.

## 9. Cards

Reusable cards:

```text
FarmCard
ParcelCard
CampaignCard
MetricCard
ActivityTimelineItem
SyncStatusCard
EmptyStateCard
```

Cards must expose meaningful tap targets and avoid card-inside-card visual noise.

## 10. Forms

Form principles:

- only show fields relevant to selected record type;
- required fields are clearly indicated but minimal;
- numeric fields use appropriate keyboard/input formatting;
- date pickers default sensibly;
- units sit next to quantities;
- multi-parcel selection is visible and editable;
- validation errors appear next to the problem and in plain language;
- saving valid core data must not be blocked by missing optional metadata.

Reusable components:

```text
AppTextField
AppNumberField
MoneyField
QuantityWithUnitField
DateField
ParcelMultiSelector
FarmSelector
CampaignSelector
AttachmentPicker
PhotoCaptureRow
NotesField
```

## 11. Register hub

The Register screen should use a clean icon+label grid/list optimized for fast recognition.

Activity families should be visually grouped, for example:

- Trabajos;
- Observaciones/Incidencias;
- Producción/Economía.

Do not require nested menus before the user can choose common work types.

## 12. Map visual language

Four visually distinct concepts:

1. existing owned parcel;
2. selected owned parcel;
3. cadastral candidate not yet saved;
4. historical/snapshot geometry where shown.

Map screen must keep controls sparse:

- recenter/my location;
- layer/context controls only when needed;
- selection card/sheet;
- confirm/add action.

Do not cover most of the map with permanent panels.

## 13. Farm and parcel map headers

Maps are protagonists on farm/parcel details.

Parcel detail:

```text
[MAP / POLYGON]
Name
Area · cadastral identity
Campaign snapshot
[+ Registrar]
```

Farm detail:

```text
[MAP / ALL PARCELS]
Farm name
Area · parcel count
[+ Registrar]
```

## 14. Metric presentation

Only show metrics backed by real persisted data.

Recommended priority:

```text
surface
activities
expenses
harvest kg
kg/ha
€/ha
€/kg
yield
```

A metric should show:

- clear value;
- unit;
- concise label;
- optional comparison only when there is a valid historic baseline.

Avoid gauges/speedometers for static agricultural totals.

## 15. Timeline

Timeline items need fast scanning.

Each item shows:

- date;
- activity/event icon/type;
- title/key value;
- concise parcel/campaign context if needed;
- attachment indicator if useful.

Timeline uses chronology and grouping, not oversized decorative cards.

## 16. Status chips

Standard statuses:

Campaign:

```text
Preparación
En curso
Recolección
Cerrada
```

Incident:

```text
Abierta
Seguimiento
Resuelta
```

Sync:

```text
Sincronizado
Pendiente
Necesita atención
Conflicto
```

Status chips must remain readable without relying solely on color.

## 17. Empty states

Every empty state should answer:

1. what is empty;
2. why that is normal;
3. what the user can do now.

Examples:

`Aún no tienes fincas` → `Crear mi primera finca`.

`No hay trabajos en esta campaña` → `Registrar trabajo`.

Avoid generic illustrations that consume most of the screen without helping action.

## 18. Offline and sync messages

Success while offline:

`Guardado en el dispositivo. Se sincronizará cuando haya conexión.`

Compact passive status:

`3 cambios pendientes`.

Failure needing attention:

`1 elemento necesita atención`.

Do not display raw HTTP/XML/database errors to the user.

## 19. Loading behavior

If local data already exists, render it immediately and sync in the background.

Use full-screen loading only for genuine initial states where no usable local information exists.

Prefer skeleton/progress only where it improves comprehension rather than hiding the whole app.

## 20. Dialogs and confirmations

Use confirmation for actions with meaningful consequences:

- archive farm;
- soft delete important record;
- close campaign;
- resolve conflict choosing one version;
- discard an important unsaved draft.

Do not confirm normal saves or navigation repeatedly.

## 21. Accessibility

RC1 requirements:

- appropriate content descriptions;
- font scaling tolerance;
- sufficient contrast;
- no critical meaning by color alone;
- logical focus/accessibility order;
- touch targets suitable for field use;
- readable numeric units;
- map selection supplemented by textual parcel identity.

## 22. Outdoor use

The design must be tested under real daylight conditions on a physical Android device.

Check:

- contrast;
- glare/readability;
- one-hand reach;
- interaction with gloves if relevant during user testing;
- map boundary visibility;
- keyboard obstruction of fields;
- large-number readability.

## 23. Responsive Android layouts

Primary target: phone portrait.

Also ensure:

- small phones remain usable;
- larger phones do not excessively stretch content;
- landscape does not break critical forms/maps;
- tablets can use wider columns/panes later without changing domain/navigation semantics.

Do not design RC1 as a tablet-first desktop dashboard.

## 24. Iconography

Use one coherent icon family compatible with Compose/Material ecosystem.

Icons support text rather than replace essential labels in unfamiliar agricultural actions.

Do not introduce mixed illustrative icon packs per feature.

## 25. Photography

Use real user/field photos as content, not decorative stock backgrounds.

Where a farm has a cover photo, it must not reduce readability of essential text.

## 26. Future modules

Tasks, machinery, inventory, people and irrigation must reuse the same tokens/components.

Adding a module must not create a new unrelated visual language.

## 27. Component ownership

The codebase should eventually have a reusable design-system layer containing tokens/components used by feature modules.

Feature modules may compose components but should not duplicate core buttons/cards/fields with slightly different styling.

## 28. Phase 0.7 Gate

Phase 0.7 is ready for approval when:

- [ ] visual direction is modern/clean rather than rustic;
- [ ] stable semantic token families are defined;
- [ ] primary navigation visual role is clear;
- [ ] reusable button/card/form/status patterns are defined;
- [ ] farm/parcel maps have a consistent visual language;
- [ ] offline/sync status has standard copy;
- [ ] accessibility/outdoor-use requirements are explicit;
- [ ] future modules can reuse the same design system;
- [ ] no screen requires one-off styling to communicate core domain state.


---

## RC1.1 visual lock additions

- Farm cards/detail may use the user's own cover photo with a controlled gradient/scrim for legibility.
- Onboarding must feel premium but concise: 4–6 screens, one message per screen, strong photography/illustration and large CTA.
- Historical kg/yield charts must be operational and readable, never decorative.
- Home contextual blocks (weather/radar, oil market, preferred cooperative) use compact cards and cannot visually dominate Mi Olivar / upcoming work.
- Oil market charts use a shared time axis when comparing AOVE, Virgen and Lampante and always surface unit, source and last update.
- Offline/stale external information must be visually distinguishable from fresh data without alarming the user.
- Before broad feature UI implementation, create and freeze reference screens for: Onboarding, Inicio, Mi Olivar, Finca, Parcela, Registrar, Campaña histórico and Entrega/OCR. Agents must reuse those tokens/components rather than redesigning each branch independently.


## RC1.2 weather-responsive Home

Home may use a lightweight weather scene behind/around the top contextual area.

Supported visual states:

- clear/sun glow;
- clouds;
- rain;
- wind/olive-leaf motion;
- fog/mist;
- storm emphasis only when the source reports it.

Rules:

- effects are driven by fetched weather state, not randomized decoration;
- text/cards keep guaranteed contrast;
- no effect may obscure operational information;
- Android reduced-motion disables non-essential animation;
- performance/battery tier may simplify or disable particles;
- stale/offline weather uses the last-known visual state only when clearly marked stale;
- no heavy 3D/particle engine is required;
- effects belong to the Home presentation layer, not domain persistence.

## RC1.2 brand-neutral design rule

Until the Naming Gate passes:

- use neutral app-title placeholders in reference designs;
- do not create final logo/icon/store artwork around “Mágina Olivo”;
- do not encode Sierra Mágina shapes/landmarks as universal brand identity;
- olive-tree/leaf/fruit/agricultural motifs are acceptable if they remain modern and non-rustic.
