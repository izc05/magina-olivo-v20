# Phase 14 — Deliveries, weight-ticket OCR and later yield

**Status:** IMPLEMENTED — awaiting CI evidence for Gate 14
**Precondition:** Gate 13 PASS, merged into `main` through PR #209 as `c75cad56`
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 14:

- destination cooperative/mill;
- kg / ticket / albarán;
- image / PDF;
- reuse the generic OCR engine with the `DELIVERY_TICKET` profile;
- user correction / confirmation;
- mixed-origin delivery;
- later yield analysis;
- yield coverage;
- settlement extension point.

**Gate 14:** the original delivery survives OCR/yield updates unchanged; OCR cannot
auto-confirm; weighted metrics are correct.

Normative sources: `MASTER-SPEC-RC1` §13, `DATA-MODEL-RC1-FUTURE` §12 (`deliveries`),
`DATA-MODEL-RC1.1-ADDENDUM` §9–§11 (delivery parcels, delivery OCR, yield analyses and the
weighted-yield formula with coverage), `DATA-MODEL-RC1.2-ADDENDUM` §3–§4 (one generic OCR
service, delivery ticket proposal), `RC1.2-PRODUCT-LOCK` §7, `RC1-NORMATIVE-ADDENDUM` D10
(Delivery is a synchronization root), `SCREEN-MAP-RC1` S80–S82.

## Normative reconciliation

1. **Weights in grams, yields in hundredths of a percent.** As for Harvest, `net_grams`,
   `gross_grams` and `tare_grams` are integers, and yields are stored as integer hundredths
   ("21,35 %" → 2135), so `SUM(kg × yield) / SUM(kg analysed)` is exact.
2. **Yield is not a column of `deliveries`.** `DATA-MODEL-RC1-FUTURE` §12 lists
   `fat_yield_percent` on the delivery; the later RC1.1 addendum §11 moves it to
   `delivery_yield_analyses` so that adding it "must not mutate" the delivery. The later
   contract wins: the analysis is its own record with its own version and outbox intent
   (`DELIVERY_YIELD`). One live analysis per delivery; a correction updates it.
3. **No separate `delivery_ocr_extractions` table.** The RC1.1 addendum sketched one; the
   RC1.2 addendum replaced it with the generic `document_ocr_extractions` (Phase 12). A
   weight ticket is an extraction of type `DELIVERY_TICKET` with a `delivery_ticket_v1`
   proposal.
4. **Delivery parcels reuse the Harvest split rule.** `allocation_state EXACT |
   UNALLOCATED_MIXED` is stored as `EXACT | UNALLOCATED`, with the same shared rule
   (`ParcelSplit`): exact kilos reconcile with the net to the gram, a mixed load keeps them
   unallocated, nothing is invented.
5. **Settlement extension point.** No settlement data is modelled; the Delivery and its
   analysis are separate records so a later settlement can link to either without changing
   them.

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| What does OCR do with a ticket? | Proposes cooperative/mill name, ticket number, date, gross, tare, net, member and vehicle references. It never computes a missing net from gross − tare. `EXTRACTED` needs a net and a date and weights that agree; otherwise `NEEDS_REVIEW`. Neither status records a Delivery. |
| How does a ticket become a Delivery? | One explicit reviewed command, `confirmDeliveryTicket`: the Delivery is written from the values the person confirmed (not from the proposal), the extraction becomes `CONFIRMED`, the ticket file moves to the Delivery. It runs once; afterwards the reading can neither run again nor be discarded, and the proposal is kept as read. |
| Gross / tare / net | Optional; when all three are given they must agree, and the confirmed net is never silently recomputed. |
| Destination | A saved organization with the Cooperative or Mill role (copied by name onto the Delivery) or a name typed by hand. |
| Campaign | The Farm's running Campaign, as for Harvest. A closed Campaign's deliveries are read-only history, but a yield analysis can still be added: laboratory results arrive late. |
| Weighted yield | Per Campaign, fat and industrial yield each weighted by delivered kilos with its own coverage ("con análisis el 66 % de los kilos"). No analysis means no yield, never zero. |
| Can a ticket become money? | No: `createExpenseDraft` refuses a `DELIVERY_TICKET`, and tickets are not listed among expense documents. |

## Room v8

`MIGRATION_7_8` creates `deliveries`, `delivery_parcels` and `delivery_yield_analyses`
empty. `8.json` is exported by the Room compiler in CI.

## Implementation

| Slice | Files |
| --- | --- |
| A — rules | `domain/production/ParcelSplit.kt` (shared with Harvest), `domain/delivery/DeliveryRules.kt` (validation, `Percent`, `YieldRules`, `DeliverySummary`), `domain/delivery/DeliveryRepository.kt`, `domain/ocr/DeliveryTicketParser.kt` |
| B — Room v8 | `entity/DeliveryEntities.kt`, `dao/DeliveryDao.kt`, `model/DeliveryRows.kt`, `DatabaseMigrations.kt`, `SyncEntityType` `DELIVERY`, `DELIVERY_YIELD` |
| C — repositories | `DeliveryWriter` (the one path that writes deliveries), `OfflineFirstDeliveryRepository`, `OfflineFirstDocumentOcrRepository.confirmDeliveryTicket`, `ProposalCodec` delivery schema |
| D — UI | `feature/deliveries/*`: S80 list with weighted yield and coverage, S81 form, S82 detail with the separate yield section, ticket capture and review. "Registrar entrega" in the Registrar sheet and a link from Cosecha; no new root. |
| E — tests | `DeliveryContractTest`, `RoomMigrationTest` 7→8, `DeliveryRulesTest`, `DeliveryTicketParserTest`, `DeliveryFormTest` |

Out of scope: settlements and prices, machinery (Phase 15), delivery charts and campaign
analytics, the Expense form's Delivery relation, remote sync.
