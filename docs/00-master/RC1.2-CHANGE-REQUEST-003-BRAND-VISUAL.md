# CR-003 — Mágina Olivo Brand + Canonical Visual System

**Decision:** APPROVED by product owner on 2026-09-18  
**Baseline:** RC1.2  
**Scope:** branding, visual language, onboarding composition and Phase 3 references  
**Does not change:** offline-first architecture, domain hierarchy, data truth rules, phase ordering or frozen RC1.2 root navigation.

## Motivation

The product owner has explicitly approved the visual direction created on 2026-09-18 and requires the Android application to follow it as the canonical visual reference rather than treating it as a moodboard.

The approved direction includes:

- final customer-facing display name **Mágina Olivo**;
- olive branch/olive logo;
- warm cream + olive visual identity;
- editorial serif + operational sans typography pairing;
- soft cards, restrained elevation and organic decoration;
- Sierra Mágina / olive-grove territorial photography as brand imagery;
- six-screen onboarding;
- approved reference screens for Home, farms, map/Catastro, campaign, activity, harvest, expenses/documents and weather/market.

This decision supersedes the previous RC1.2 temporary naming instruction that treated “Mágina Olivo” as retired/TBD.

## Proposed change

1. Freeze **Mágina Olivo** as the customer-facing display brand for the current product line.
2. Keep the existing Android package/namespace as an engineering identifier; no package-ID migration is performed in Phase 3.
3. Freeze `docs/design/VISUAL_DESIGN_LOCK.md` and `docs/design/design-lock.json` as normative visual contracts.
4. Freeze the committed images under `docs/design/reference/` as canonical implementation references.
5. Change onboarding visual reference from five pages to **six pages**:
   1. Bienvenida.
   2. Tus fincas y parcelas.
   3. Mapa y Catastro.
   4. Actividad y campaña.
   5. Cosecha, gastos y documentos.
   6. Tiempo, mercado y alertas.
6. Implement project-owned Compose tokens and reusable components before feature-screen production work.
7. Require visual comparison against the canonical references before Gate 3 can pass.
8. Preserve geographic-neutral domain/data architecture even though brand imagery references Sierra Mágina.
9. Preserve the frozen RC1.2 primary navigation contract during Phase 3: `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`. Reference artwork showing alternative labels is a visual/composition reference, not permission to change root navigation before Phase 4 change control.

## Affected baseline sections

- RC1.2 Product Lock — Public brand / Brand transition.
- Phase 3 Design System + Reference Screens.
- Naming Gate: naming decision is now resolved for display branding.
- Agent instructions regarding “Mágina Olivo” as a retired final brand.

## Affected phases

- **Phase 3:** canonical visual implementation and six-screen onboarding.
- **Phase 4:** no navigation change; only the approved visual shell is carried forward.
- Later feature phases: must reuse the Phase 3 design system and reference language.

## Data impact

None. The decision does not change Room schema, entity identifiers, parcel/Catastro provider abstraction, campaign history, OCR, expenses, synchronization or Supabase contracts.

## Offline/sync impact

None. All visual assets and design tokens are local application resources. External imagery/data feeds remain optional and must not block field operation.

## UX impact

The app gains a frozen customer-facing visual identity and six-screen first-run onboarding. The visual hierarchy prioritizes agricultural work and the user's own holdings, then campaign/upcoming work, then contextual weather/market/cooperative information.

## Risks

- generated reference images can contain illustrative values that must never ship as production truth;
- screenshot typography cannot be copied blindly if it harms Android accessibility;
- territorial imagery must not leak into domain restrictions;
- exact appearance must still respect Android safe areas, font scaling and minimum 48 dp touch targets.

## Alternatives considered

- keep brand TBD: rejected by product owner;
- use references only as inspiration: rejected;
- redesign with a generic Material theme: rejected;
- change root navigation to match every screenshot literally: rejected for Phase 3 because navigation is a separate frozen RC1.2 contract.

## Decision

**APPROVED**

The visual system is now a controlled product contract. Any future visual redesign requires an explicit design-lock change or Change Request.
