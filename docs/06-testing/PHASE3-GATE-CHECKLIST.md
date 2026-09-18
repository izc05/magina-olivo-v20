# Phase 3 — Gate Checklist

**Phase:** Design System + Reference Screens  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Status:** DRAFT — Gate 2 is closed; Gate 3 visual review remains.

## Design tokens

- [x] Olive/cream/sage/earth semantic palette exists in Compose.
- [x] Typography scale exists.
- [x] Shape/radius tokens exist.
- [x] Spacing/touch-size tokens exist.
- [x] Token-level contrast review completed.
- [ ] 360dp compact-width visual review completed.
- [x] 360dp reference previews defined.
- [ ] 412dp common-width visual review completed.
- [x] 412dp reference previews defined.

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
- [x] Top app bar.
- [x] Icon button.
- [x] Sync status.
- [x] Photo-cover fallback.
- [x] Chart container.
- [x] Confirmation sheet.
- [x] Bottom action sheet.
- [x] List skeleton.

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

### Current automated evidence — 2026-09-18

- Android CI run: `35327564325`
- Head SHA: `24e8586e9dbc054de47cec183e294302372d0a41`
- Result: `success`
- DEV artifact id: `10539922497`
- Artifact digest: `sha256:9ad945678a041d071dbb66d7e7e5007b9b02c1726d1257c7a9bcb2d0bb9716aa`
- Extracted APK SHA-256: `93095387244cb4a91c7455d5786b5129b3f6e0b2db2c96aa26cd233d3dd004c9`

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
