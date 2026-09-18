# Phase 3 — Gate Checklist

**Phase:** Design System + Reference Screens  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Status:** STACKED / DRAFT until Gate 2 closes.

## Design tokens

- [x] Olive/cream/sage/earth semantic palette exists in Compose.
- [x] Typography scale exists.
- [x] Shape/radius tokens exist.
- [x] Spacing/touch-size tokens exist.
- [ ] Contrast review completed.
- [ ] 360dp compact-width review completed.
- [ ] 412dp common-width review completed.

## Reusable components

- [x] Primary/secondary buttons.
- [x] Metric card with partial/estimate states.
- [x] Status chip.
- [x] Section header.
- [x] Offline banner.
- [x] Farm card.
- [x] Weather hero.
- [x] Simple analytical bar chart.
- [x] Visual bottom navigation.
- [x] Text/select/date fields.
- [x] Parcel row.
- [x] Empty/error states.
- [x] Source/freshness component.
- [x] OCR review field/panel productionized.

## Reference screens

- [x] Onboarding reference.
- [x] Inicio reference.
- [x] Mi Olivar reference.
- [x] Campaña reference.
- [x] Producción reference.
- [x] Costes reference.
- [x] Rentabilidad reference.
- [x] Entrega/OCR reference.
- [ ] Compact-width reference proof.
- [x] Loading/empty/partial-state reference proof.

## Extended reference coverage

- [x] Finca reference.
- [x] Parcela reference.
- [x] Registrar reference.
- [x] Calendario reference.
- [x] Perfil reference.
- [x] Factura/OCR reference.
- [x] Interface states reference.

## Truth rules

- [x] Demo data is clearly marked as demonstration.
- [x] Yield coverage is visibly partial where applicable.
- [x] Estimated oil is labelled as estimate.
- [x] OCR values are visibly “Por revisar” before confirmation.
- [x] DEV-only gallery does not become real Phase 4 navigation.
- [x] STAGING/PRODUCTION remain free of demo gallery at runtime.

## Automated validation

- [x] lint passes.
- [x] unit tests pass.
- [x] DEV/STAGING/PRODUCTION builds pass.
- [x] instrumented-test APK compiles.
- [x] DEV launcher smoke test sees the design gallery.

### Automated evidence — 2026-09-18

- Android CI run: `35323113508`
- Head SHA: `f5467e91984a57d2c32768d73f7ab5812e3b41de`
- Result: `success`
- DEV artifact id: `10537594233`
- Artifact digest: `sha256:e268f459010b57a68daf0e1eb4c7cd9b00e9c1a4c3eee07bd03fcf233fe494b3`
- Extracted APK SHA-256: `ff081cee31168cebb3b3251c898fbd564b1df05c41ec1967d5e680bc096bf17e`

## Physical visual review

- [ ] APK installs/opens on physical Android.
- [ ] Onboarding reference readable.
- [ ] Inicio hierarchy readable.
- [ ] Mi Olivar cards readable.
- [ ] Campaign metrics readable.
- [ ] Production chart/delivery list readable.
- [ ] Costs breakdown readable.
- [ ] Profitability ratios readable.
- [ ] OCR review state understandable.
- [ ] No obvious clipping/overflow on the test phone.

## Figma

- [ ] Figma MCP access restored.
- [ ] Foundations transferred to Figma.
- [ ] Core components transferred to Figma.
- [ ] Eight reference screens transferred/validated in Figma.

Figma transfer is required before final Gate 3 approval, but Compose design work may continue while the external MCP limit is blocked.

## Gate result

**PENDING PHYSICAL VISUAL REVIEW + FIGMA TRANSFER.**

Phase 4 real navigation must not be merged before Gate 3 PASS.
