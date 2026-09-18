# Phase 3 — Mágina Olivo Visual Implementation Checklist

**Status:** ACTIVE  
**Implementation state:** merged into `main`; Gate 3 evidence remains open  
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

## Additional canonical device evidence pending

- [x] Parcel detail — Compose reference implemented; canonical device screenshot still pending.
- [x] Delivery / OCR review — Compose reference implemented; canonical device screenshot still pending.

Both screens are implemented in the locked visual language. Their representative Android screenshots must be captured and approved before Gate 3 PASS.

## Remaining component catalogue

- [x] MoIconButton.
- [x] MoSelectField.
- [x] MoDateField.
- [x] MoFarmCard.
- [x] MoParcelRow.
- [x] MoEmptyState.
- [x] MoErrorState.
- [x] MoOfflineBanner.
- [x] MoSyncStatus.
- [x] MoSourceFreshness.
- [x] MoPhotoCover.
- [x] MoChartContainer.
- [x] MoListSkeleton.
- [x] MoConfirmationSheet.
- [x] MoBottomActionSheet.
- [x] MoBottomBar visual primitive.
- [x] MoTopAppBar visual primitive.

## Reference-screen implementation order

1. [x] Component catalogue screen.
2. [x] Onboarding 1–6.
3. [x] Inicio reference.
4. [x] Mi Olivar / Fincas reference.
5. [x] Farm detail reference.
6. [x] Parcel detail reference.
7. [x] Map / Catastro reference.
8. [x] Register reference.
9. [x] Campaign history reference.
10. [x] Harvest reference.
11. [x] Expenses/documents reference.
12. [x] Weather/market reference.
13. [x] Delivery/OCR review reference.

## Gate 3 evidence still required

- [x] CI green after each implementation slice.
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
- [x] no Phase 4 navigation behavior implemented early.

## Guardrail

Phase 3 is visual-system/reference work only. Do not introduce Room entities, agricultural persistence, Supabase feature coupling, sync, Catastro live integration or later-phase production workflows.
