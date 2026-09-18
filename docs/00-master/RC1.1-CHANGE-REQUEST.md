# CR-001 — RC1.1 Olive-first Jaén product lock

**Decision:** APPROVED by product owner on 2026-09-18  
**Target baseline:** `RC1.1-BASELINE-2026-09-18`

## Motivation

The original RC1 correctly froze the Android/offline agricultural core, but several product decisions made after that freeze are now required for the first complete Mágina Olivo release. In particular, Home context, delivery OCR/yield workflow, machinery, irrigation scheduling, suppliers/purchases, historical charts and onboarding must no longer be treated as vague future ideas.

The goal remains a simpler product than the old V20 web platform: a private Android app focused on the user's own olive farms, initially oriented to Jaén.

## Approved changes

1. Keep the immutable agricultural hierarchy: Farm → Parcel → Campaign → Activity / Harvest / Expense / Document.
2. RC1.1 remains Android-native, Kotlin/Compose and offline-first.
3. Initial user model is the farmer managing their own holdings. Professional/client workspaces are post-RC1.1.
4. A Farm is a user-defined named grouping containing one or many agricultural Parcels.
5. Parcel display name/alias is independent from Catastro identifiers.
6. Farms may have a user-selected cover photo.
7. Add a 4–6 screen first-run onboarding.
8. Historical campaigns must be quick to access and may show useful charts for kg, dates and yields.
9. Harvest and Delivery remain distinct.
10. Delivery must support ticket/photo/PDF attachment, OCR extraction with human confirmation, and later yield analysis without rewriting the original delivery.
11. Mixed-origin deliveries must never fabricate per-parcel kg splits.
12. Cooperative/mill/provider becomes a reusable organization/contact concept for deliveries, purchases and preferred Home content.
13. Expenses/purchases may record supplier/cooperative/company, products, quantities, amount and document.
14. Machinery becomes an RC1.1 operational resource and can be related to activities.
15. Irrigation records may store irrigation community/company and sector.
16. Planned activities/irrigation may create Android local reminders for previous day and/or same day.
17. Home may show contextual non-core information: weather/radar, selected cooperative notices/news and reference olive-oil market prices.
18. Oil-market UI may expose AOVE, Virgen and Lampante as separate synchronized series when the configured source supports them; source and last update must always be visible.
19. Contextual Home information must never block agricultural operation or turn the app into a territorial/news portal.
20. Admin is a separate private web surface for users/content/cooperatives/sources/notifications/operations.
21. Loyalty / Mi Olivo remains modular and disabled until the technical agricultural product is stable.
22. Bottom navigation changes to: Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil.
23. Visual implementation must be attractive, modern and coherent, but data integrity/offline usability remain higher priority.
24. Before feature UI coding, agents must follow the frozen design system/screen map and may not invent alternative architecture or navigation.

## Explicitly deferred

- professional mode for farms belonging to third-party clients;
- public territorial portal;
- advertising/marketplace;
- loyalty/gamification activation;
- AI agronomic assistant;
- IoT/sensors/automation;
- general tourism content.

## Data impact

RC1.1 requires or reserves normalized entities for:

- agricultural organizations/contacts with reusable roles;
- delivery OCR extraction and review state;
- yield analysis linked to a delivery;
- machinery;
- irrigation provider/community and irrigation sector;
- reminders;
- purchase/product/supplier relations;
- farm cover attachment;
- user preferred municipality/cooperative;
- oil-market observations/cache.

No existing historical record may be rewritten to adopt current metadata.

## Offline/sync impact

Core field work remains local-first. External Home feeds are cacheable/optional and must degrade gracefully offline. Local reminders do not require server connectivity.

## UX impact

Home becomes contextual but not crowded. Primary operational navigation changes to `Inicio · Mi Olivar · + · Calendario · Perfil`.

## Phase impact

The ordered roadmap remains gate-driven. Phase 1 Android Foundation is still the only currently allowed implementation phase. Later phases are revised to include the approved RC1.1 scope; no later feature may be started before preceding gates pass.

## New baseline

`RC1.1-BASELINE-2026-09-18`
