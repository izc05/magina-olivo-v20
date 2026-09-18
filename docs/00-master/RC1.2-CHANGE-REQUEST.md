# CR-002 — RC1.2 Global Olive Product Scope and Generic OCR

**Decision:** APPROVED by product owner on 2026-09-18  
**Target baseline:** `RC1.2-BASELINE-2026-09-18`

## Motivation

The product is no longer a Jaén/Sierra Mágina-branded application. It becomes a specialized olive-farm management application suitable for olive growers anywhere in Spain and architecturally ready for use outside Spain.

The current public product name “Mágina Olivo” is retired. A new brand name will be selected before public release. The repository name may remain temporarily for engineering continuity.

At the same time, OCR must not be limited to olive-delivery tickets. It must also support purchase invoices/receipts and other agricultural documents with human review before creating or modifying financial/agricultural records.

## Approved changes

1. Public product identity becomes geographic-neutral and olive-specific.
2. Public product name is **TBD** until the naming gate is completed.
3. “Mágina Olivo” may remain only as a temporary internal repository/history identifier; it must not be treated as the final customer-facing brand.
4. Initial commercial/operational focus is Spain, but domain/data architecture must support other countries.
5. Country, locale, timezone, currency and units are explicit configuration/context, never hard-coded to Jaén.
6. Spanish Catastro remains an excellent Spain integration, but parcel identity is provider-neutral and app-owned.
7. Land-registry/geospatial import must support provider abstractions; Spain can use Catastro while other countries can use manual/imported geometry and future registry providers.
8. Preferred cooperative/organization is optional. Users may create/select their own organizations even when no curated directory exists.
9. Weather/radar uses the user's selected farm/location and is provider-neutral.
10. Olive-oil market data is source/country aware; AOVE/Virgen/Lampante series are shown only when a configured reliable source supports them.
11. OCR becomes a generic document service with typed extraction profiles.
12. Initial OCR document profiles:
    - olive delivery ticket / weight ticket;
    - supplier invoice;
    - supplier receipt/ticket;
    - phytosanitary product invoice;
    - fertilizer invoice;
    - irrigation invoice/receipt;
    - generic agricultural document.
13. OCR output is always draft/extracted data until human review/confirmation.
14. Purchase-document OCR may prefill a draft Purchase/Expense but never auto-post money to the authoritative Expense ledger.
15. Original document/image/PDF is retained independently from extracted/confirmed structured values.
16. Irrigation can record a historical price/tariff snapshot by date and basis (per m³, hour, event, area or invoice total) while Expense remains the authoritative financial ledger.
17. Planned work/agenda supports expected date/time, farm/parcel(s), type, expected people count, provider/crew when useful, notes and reminders.
18. Home weather can use subtle contextual visual effects (rain, sun, clouds, wind/fog) derived from current conditions.
19. Weather visuals must respect reduced motion, performance tier and battery/thermal constraints and never obstruct readability.
20. The product remains strictly olive-specialized; this scope change does not turn it into a generic all-crops farm ERP.
21. Professional/client mode remains post-RC1.2 unless separately approved.

## Naming gate

Before public beta branding/store assets:

- shortlist distinctive names;
- screen for obvious app/brand conflicts;
- check usable domain/social naming where practical;
- approve final display name;
- then update Android app label, icon, package/namespace only if the final technical rename is approved.

Do not repeatedly rename package IDs during development. Keep the current technical identifier until the naming decision is frozen or adopt one neutral technical ID in a single controlled migration.

## Data impact

Add/extend:

- workspace country/locale/timezone/currency/unit preferences;
- parcel external land-registry links with provider type;
- generic document OCR extraction entity/profile;
- confirmed-document-to-domain mapping;
- irrigation pricing snapshot;
- planned work people/provider fields;
- weather visual state as a presentation projection, not durable agricultural truth.

## Offline impact

All agricultural records, attachments and OCR-review drafts remain local-first. External OCR execution may require connectivity depending on the engine, but attachment capture and manual record completion must still work offline.

Weather/radar/market/cooperative content remains optional external context and cannot block farm operation.

## New baseline

`RC1.2-BASELINE-2026-09-18`
