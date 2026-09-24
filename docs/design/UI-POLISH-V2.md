# UI polish v2 — owner-directed usability pass

**Status:** implemented on `feat/android-ui-polish-v2` — Draft PR, not merged
**Origin:** owner review on a physical Android device, 2026-09-23
**Scope:** visual/usability only. No change to architecture, Room, repositories,
synchronization, domain rules, offline behaviour or navigation roots.
**Contract:** keeps the locked identity (`VISUAL_DESIGN_LOCK.md`, `DESIGN_SYSTEM.md`):
warm cream surfaces, olive green, editorial serif titles, soft cards. The approved
mock-up board shared by the owner is a hierarchy reference only; none of its data is copied.

## A. Colour hierarchy

Device finding: too many elements (CTAs, chips, states, titles, secondary buttons) used
the same dark olive and competed with each other.

| Role | Token | Hex | Used for |
|---|---|---|---|
| Primary action | `MoOlivePrimary` | `#3E5A32` | primary CTA (`MoPrimaryButton`), selected bottom-bar item and the central “+” only |
| Selection / positive | `MoOliveMid` on `MoOliveTint` | `#4F6B39` / `#E9EEE0` | chips, success status, text actions, checkboxes, focused fields (Material `primary`) |
| Surfaces | `MoCream` / `MoWarmWhite` | `#F8F6EE` / `#FCFBF7` | background / cards |
| Body text | `MoInk` | `#2A2823` | default text and key figures (kg, €, hours) |
| Editorial titles | `MoOliveDark` | `#173122` | serif headlines (brand) |
| Secondary text | `MoTextSecondary` (warm grey) | `#67625A` | metadata, neutral status |
| Planning / info | `MoInfo` / `MoInfoText` | `#5E7D8C` / `#4C6876` | planned work, neutral information |
| Notices | `MoWarning` / `MoWarningText` | `#C49842` / `#805B18` | warnings and notices |
| Secondary buttons | `MoInk` on `MoWarmWhite`, border `MoOutlineStrong` `#D6D0C2` | | neutral, never competing with the CTA |

All text pairs are asserted ≥ 4.5:1 (WCAG AA) in `DesignContrastTest`.

Destructive actions (cancelar, eliminar, archivar, cerrar campaña) use soft red
(`MoError` border, `MoErrorText` label) in `MoDestructiveButton`; no saturated colours.

## Button roles

| Role | Component | Look |
|---|---|---|
| Primary | `MoPrimaryButton` | filled dark olive — one per screen/sheet |
| Secondary | `MoSecondaryButton` | outlined, warm ink |
| Destructive | `MoDestructiveButton` | outlined soft red; the action it opens is still confirmed |
| Tertiary | `MoTertiaryButton` | text only, warm grey (Cancelar, Volver) |

## What changed, by owner priority

1. **Quick Add (+)** — `navigation/QuickAddSheet.kt`: "¿Qué quieres registrar?", then the
   context (Finca · Campaña · Parcela) when the screen behind has one, then five compact
   rows with a line icon and one line of explanation (actuación, cosecha, entrega, gasto o
   documento, planificar trabajo). Cancelar is tertiary. The sheet opens fully
   (`skipPartiallyExpanded`) and scrolls, so nothing is cut on short phones. The Farm
   context is applied to the activity/planning flow.
2. **Calendar + date picker** — Agenda | Mes. Agenda groups *pendientes de días pasados*,
   *hoy*, *esta semana*, *más adelante*. Mes is `MoMonthCalendar`: Monday first, 48dp day
   targets, today ringed, selection filled, a dot per day with planned work; tapping a day
   lists its work. Every date field is now `MoDateInputField`, which opens
   `MoDatePickerSheet`: whole month, Hoy/Ayer, the chosen date spelt out, explicit
   Confirmar. Forms keep the same ISO value, so no validation or rule changed.
3. **Compaction** — editorial titles 27/23sp (were 30/26), section headers 17sp,
   48dp buttons (were 54dp), 52dp fields, 2–3 column summary metrics (`MoSummaryMetric`,
   `MoMetricGrid`), dense rows (`MoCompactListItem`).
4. **Colour hierarchy** — section A above.
5. **Farm cards** — `MoFarmCard` shows the cover photo or `MoFieldArtwork` (an illustrated
   olive grove in the brand palette, decorative only), name, location, area, parcels,
   campaign state and the next planned work when there is one. Farm detail: compact hero,
   three figures, quick access to Parcelas / Campañas / Trabajos (scrolls to the section)
   and Mapa, shown disabled with "Pronto" until the map phase exists.
6. **Campaign summary** — header (name, state, farm, start, parcels) and a two-column
   summary: kg recogidos, entregas, rendimiento graso, gastos, read from the Harvest,
   Delivery and Expense ledgers that own them. Unknown values show "—" plus a sentence,
   never zero. Links to Cosecha and Entregas. Cerrar campaña is destructive and confirmed.
7. **Empty states** — `MoEmptyState` takes an icon, a title, a useful sentence and an
   optional action ("Aún no has registrado cosecha", "Completa la ubicación para ver la
   finca en el mapa", "Añade superficie a tus parcelas para calcular rendimientos").
8. **Actions** — Activity detail leads with one card (type, state, date, parcels, hour,
   duration, crew, reminder); Marcar completada primary, Editar secondary, Cancelar
   actuación destructive. Harvest shows kg, registros, entregado and rendimiento first;
   Registrar cosecha primary, Entregas a cooperativa secondary.

Also: bottom bar with line icons (`MoIcons`) and a soft pill behind the active root, so the
selection does not depend on colour alone; `windowSoftInputMode=adjustResize` so the
keyboard never covers a field; every form's Cancelar is tertiary.

## Not changed

Room, migrations, repositories, sync/outbox, domain rules, campaigns, harvest, deliveries,
machinery, the calendar's data, offline behaviour and the five frozen roots.

## Evidence

- Screenshots: `Gate3EvidenceScreenshotTest.captureUiPolish*` — Inicio, Mi Olivar, Finca,
  Campaña, Calendario Agenda, Calendario Mes, Quick Add, Actuación, Cosecha — captured by the
  Gate 3 emulator run at 360/393/480 dp and at 130 % font, from sample data kept in the test
  sources (`UiPolishFixtures`). The "before" set comes from branch
  `feat/android-ui-polish-v2-baseline` (the same test on `e3d625f`, evidence only, never merged).
- `DesignContrastTest` asserts every new text/background pair ≥ 4.5:1.

## Inicio con datos reales (`feat/home-real-data`)

La revisión general detectó que Inicio seguía mostrando la pantalla de referencia con cifras
fijas (temperatura, kilos, precios de mercado y noticias). Eso incumple la regla de verdad del
producto. Inicio pasa a `feature/home` y solo lee lo que ya hay en Room:

- fincas, parcelas y superficie conocida (sin superficie si ninguna parcela la tiene);
- campañas en marcha con kilos recogidos y entregados de sus propios registros;
- los tres próximos trabajos planificados y el número de trabajos pendientes de días pasados;
- accesos rápidos a Mi Olivar, Cosecha, Entregas y Gastos.

Tiempo, mercado del aceite y avisos de cooperativa conservan su hueco con la etiqueta
«Pronto»: no se añade ningún servicio externo (Fase 20). Sin cambios en Room, migraciones,
repositorios, sync ni navegación raíz.
