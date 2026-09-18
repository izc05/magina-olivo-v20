# Phase 3 — Reference Implementation Index

**Status:** implementation complete; Gate 3 validation pending.

This file maps the locked visual boards to the Compose references now merged into the Android application.

| Area | Compose reference | Canonical visual source |
|---|---|---|
| Component catalogue | `ui/reference/components/ComponentCatalogueReferenceScreen.kt` | `DESIGN_SYSTEM.md` |
| Onboarding 1–6 | `ui/reference/onboarding/OnboardingReferenceScreen.kt` | `20-onboarding-a.svg`, `21-onboarding-b.svg` |
| Inicio | `ui/reference/home/HomeReferenceScreen.kt` | `10-core-a.svg` |
| Mi Olivar / Fincas | `ui/reference/olivar/OlivarReferenceScreen.kt` | `10-core-a.svg` |
| Detalle de finca | `ui/reference/farm/FarmDetailReferenceScreen.kt` | `11-core-b.svg` |
| Detalle de parcela | `ui/reference/parcel/ParcelDetailReferenceScreen.kt` | locked design system; Android canonical screenshot pending |
| Mapa / Catastro | `ui/reference/map/MapCatastroReferenceScreen.kt` | `10-core-a.svg` |
| Campaña | `ui/reference/campaign/CampaignReferenceScreen.kt` | `11-core-b.svg` |
| Registrar actuación | `ui/reference/register/RegisterActivityReferenceScreen.kt` | `11-core-b.svg` |
| Cosecha | `ui/reference/harvest/HarvestReferenceScreen.kt` | `12-core-c.svg` |
| Gastos y documentos | `ui/reference/expenses/ExpensesDocumentsReferenceScreen.kt` | `12-core-c.svg` |
| Tiempo y mercado | `ui/reference/weather/WeatherMarketReferenceScreen.kt` | `12-core-c.svg` |
| Revisión entrega / OCR | `ui/reference/ocr/DeliveryOcrReviewReferenceScreen.kt` | locked design system; Android canonical screenshot pending |

## Shared Compose implementation

Theme/tokens:

- `ui/theme/Color.kt`
- `ui/theme/Spacing.kt`
- `ui/theme/Shape.kt`
- `ui/theme/Type.kt`
- `ui/theme/Theme.kt`

Reusable components live under:

`app/src/main/java/com/isivoltpro/maginaolivo/ui/components/`

The component catalogue includes buttons, fields, cards, status/freshness/offline/sync treatments, farm/parcel patterns, photo cover, loading/empty/error states, chart container, top/bottom navigation chrome and action/confirmation sheet primitives.

## Gate 3 rule

Implementation presence does **not** equal Gate 3 PASS.

For each representative screen:

1. render on target Android widths;
2. compare against the canonical visual source;
3. review font scaling and accessibility semantics;
4. review non-happy states;
5. verify no accidental Phase 4 navigation/persistence behavior was introduced;
6. fix any visual/accessibility defect before approval.

Do not redesign the visual language during validation.
