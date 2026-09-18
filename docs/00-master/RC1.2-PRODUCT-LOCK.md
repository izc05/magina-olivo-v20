# Olive Farm App — RC1.2 Product Lock

**Status:** APPROVED / NORMATIVE  
**Baseline:** `RC1.2-BASELINE-2026-09-18`  
**Public brand:** TBD — “Mágina Olivo” is retired as final product name.

This file supersedes geographic/product-scope wording in RC1.1. All existing agricultural/offline/gate rules remain in force unless explicitly changed here.

## 1. Product identity

A native Android, offline-first application specialized in managing olive farms.

Initial target market: Spain.

Architecture target: usable in other olive-growing countries without rebuilding the agricultural core.

The application is not a general-crop ERP and not a regional information portal.

Core remains:

`Finca → Parcela → Campaña → Actuaciones / Cosecha / Entregas / Gastos / Documentos → Histórico → Informes`

## 2. Geographic neutrality

Do not hard-code:

- Jaén;
- Sierra Mágina;
- one municipality catalog;
- EUR as the only future currency;
- Spanish timezone;
- Spanish cadastral identifiers as Parcel identity.

Workspace/profile may store:

- country;
- administrative region;
- locality/municipality;
- timezone;
- locale/language;
- currency;
- preferred units.

Spain is the first complete implementation and may use sensible Spain defaults.

## 3. Parcel and land-registry providers

Parcel remains app-owned agricultural identity.

External geometry/registry link uses:

```text
provider
externalReference
providerMetadata
importedGeometry
importedAt
```

Spain provider: Catastro.

Other supported paths even without a registry integration:

- manual parcel;
- imported geometry;
- map drawing when that phase exists;
- future country-specific providers.

Never make a live registry service required for normal offline use after import.

## 4. Agricultural organizations

Organizations are user/workspace data and can include:

- cooperative;
- mill/almázara;
- supplier;
- irrigation community/company;
- workshop/service provider;
- contractor/crew provider.

Curated directories are optional enhancements, not prerequisites.

## 5. Generic document OCR

OCR is a shared service for attachments/documents, not a delivery-only feature.

Initial typed profiles:

```text
DELIVERY_TICKET
PURCHASE_INVOICE
PURCHASE_RECEIPT
PHYTOSANITARY_INVOICE
FERTILIZER_INVOICE
IRRIGATION_INVOICE
GENERIC_AGRICULTURAL_DOCUMENT
```

Flow:

`capture/import → OCR → extracted draft → human review/correction → confirm → optional domain draft/application`

Non-negotiable:

- preserve original file;
- preserve raw extraction;
- show review state;
- never silently overwrite confirmed domain data;
- never auto-post an Expense;
- never treat OCR confidence as truth.

## 6. Purchase OCR

For invoices/receipts, OCR may propose:

- supplier;
- invoice/ticket number;
- date;
- tax identifiers when present;
- line items;
- product names;
- quantity/unit;
- subtotal/tax/total;
- payment/reference text.

User confirms/corrects before a Purchase/Expense draft is finalized.

Products can later be linked to fertilizer/treatment activities.

## 7. Delivery OCR

Delivery/weight ticket OCR may propose:

- cooperative/mill;
- ticket number;
- date/time;
- gross/tare/net/delivered kg where present;
- vehicle/member/reference fields when useful.

User confirms the actual delivered kg and metadata.

Yield still arrives later as a separate linked analysis.

## 8. Irrigation price

Irrigation activity may keep a historical pricing snapshot:

```text
pricingBasis = PER_M3 | PER_HOUR | PER_EVENT | PER_HECTARE | INVOICE_TOTAL | OTHER
unitPrice?
pricedQuantity?
calculatedEstimate?
linkedExpenseId?
currency
priceDate
```

The Expense ledger remains authoritative for actual financial totals.

Tariff snapshots are historical: changing today's tariff never rewrites previous irrigation costs.

## 9. Agenda / scheduled work

Planned work is first-class enough to power Calendar and notifications.

A planned activity may include:

- date/time;
- farm;
- parcel(s);
- work type;
- expected duration;
- expected people count;
- contractor/provider/crew text or organization;
- machinery if known;
- notes;
- previous-day/same-day/custom reminders.

Examples: harvest day, pruning crew, irrigation turn, treatment, external service visit.

The same planned record can later be completed with actual values rather than being duplicated.

## 10. Weather and radar

Weather is contextual to farm/location.

Home may show:

- current temperature/conditions;
- rain probability/precipitation;
- wind;
- alerts when available;
- radar access.

Do not use weather as agronomic certainty. Source/freshness must be available.

## 11. Weather visual effects

Home may render subtle atmosphere derived from current weather:

- rain particles/overlay;
- cloud movement;
- sun/light glow;
- fog/mist;
- restrained wind/leaf movement.

Rules:

- content readability always wins;
- respect Android reduced-motion/accessibility;
- degrade/disable on low-performance or battery-sensitive contexts;
- no heavy 3D engine required;
- visual state reflects fetched weather state, not random decorative weather;
- offline stale weather must not pretend to be live.

## 12. Oil market

Market data is country/source aware.

For Spain, a configured reliable source may expose synchronized series for:

- AOVE;
- Virgen;
- Lampante.

If a country/source cannot provide equivalent categories, the UI adapts or hides unavailable series rather than fabricating them.

## 13. Brand transition

Until final naming approval:

- documentation calls the product “Olive Farm App” or “the app” when geographic neutrality matters;
- repository `magina-olivo-v20` may remain unchanged;
- current Android package/namespace remains a temporary engineering identifier;
- new customer-facing screens must not deepen Sierra Mágina branding.

Final name/logo/icon/package decision is one controlled branding migration before public beta/store release.

## 14. Scope that remains deferred

- third-party client/professional multi-owner mode;
- public territorial portal;
- tourism/community;
- active loyalty/reward economy;
- advertising marketplace;
- general multi-crop support;
- AI agronomic diagnosis;
- IoT automation.

## 15. Agent rule

If a branch assumes Jaén-only domain logic, hard-codes Catastro as Parcel identity, restricts OCR to delivery tickets, or reintroduces “Mágina Olivo” as final customer-facing brand, it conflicts with RC1.2 and must stop for correction.
