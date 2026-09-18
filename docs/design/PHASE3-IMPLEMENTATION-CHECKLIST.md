# Phase 3 — Mágina Olivo Visual Implementation Checklist

**Status:** ACTIVE  
**Branch:** `feat/rc1-2-visual-system`  
**Canonical visual source:** `docs/design/VISUAL_DESIGN_LOCK.md`

## Completed in this slice

- [x] CR-003 approves Mágina Olivo display brand and canonical visual system.
- [x] RC1.2 Product Lock reconciled with CR-003.
- [x] CURRENT-STATE advanced to Phase 3.
- [x] Roadmap advanced to Phase 3.
- [x] Agent instructions point to the visual lock/design system.
- [x] Six-screen onboarding frozen.
- [x] Canonical Design System document created.
- [x] Compose color tokens created.
- [x] Compose spacing/size tokens created.
- [x] Compose shape tokens created.
- [x] Compose dual typography created.
- [x] AppRoot wrapped in Mágina Olivo theme.
- [x] Foundation screen uses canonical theme.
- [x] App display labels changed to Mágina Olivo.
- [x] Primary/secondary button primitives created.
- [x] Metric card + section header primitives created.
- [x] Text field primitive created.
- [x] Semantic status chip created.
- [x] Pull request opened for CI/review.

## Visual references already committed

- [x] Brand / logo board.
- [x] Inicio.
- [x] Mis fincas.
- [x] Mapa / Catastro.
- [x] Detalle de finca.
- [x] Campaña.
- [x] Registrar actuación.
- [x] Cosecha.
- [x] Gastos y documentos.
- [x] Tiempo y mercado.
- [x] Onboarding 1–6.

## Missing canonical visual boards

- [ ] Parcel detail.
- [ ] Delivery / OCR review.

These two must be designed in the same locked visual language before Gate 3 PASS.

## Remaining component catalogue

- [ ] MoIconButton.
- [ ] MoSelectField.
- [ ] MoDateField.
- [ ] MoFarmCard.
- [ ] MoParcelRow.
- [ ] MoEmptyState.
- [ ] MoErrorState.
- [ ] MoOfflineBanner.
- [ ] MoSyncStatus.
- [ ] MoSourceFreshness.
- [ ] MoPhotoCover.
- [ ] MoChartContainer.
- [ ] MoListSkeleton.
- [ ] MoConfirmationSheet.
- [ ] MoBottomActionSheet.
- [ ] MoBottomBar visual primitive.
- [ ] MoTopAppBar visual primitive.

## Reference-screen implementation order

1. [ ] Component catalogue screen.
2. [ ] Onboarding 1–6.
3. [ ] Inicio reference.
4. [ ] Mi Olivar / Fincas reference.
5. [ ] Farm detail reference.
6. [ ] Parcel detail reference.
7. [x] Map / Catastro reference.
8. [ ] Register reference.
9. [ ] Campaign history reference.
10. [ ] Harvest reference.
11. [ ] Expenses/documents reference.
12. [ ] Weather/market reference.
13. [ ] Delivery/OCR review reference.

## Gate 3 evidence still required

- [ ] CI green after each implementation slice.
- [ ] 360dp compact rendering.
- [ ] ~393–412dp common phone rendering.
- [ ] 480dp large-phone rendering.
- [ ] font-scale verification.
- [ ] TalkBack semantics review.
- [ ] reduced-motion review where relevant.
- [ ] outdoor contrast review.
- [ ] empty/loading/error/offline reference states.
- [ ] physical Android screenshot comparison against canonical boards.
- [ ] no ad-hoc raw colors/spacing outside approved tokens.
- [ ] no Phase 4 navigation behavior implemented early.

## Guardrail

Phase 3 is visual-system/reference work only. Do not introduce Room entities, agricultural persistence, Supabase feature coupling, sync, Catastro live integration or later-phase production workflows.
