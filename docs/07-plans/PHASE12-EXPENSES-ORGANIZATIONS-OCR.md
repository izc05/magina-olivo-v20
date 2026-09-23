# Phase 12 — Expenses, purchases, organizations + generic document OCR

**Status:** Gate 12 PASS — validated on `f0725b2a` (Android CI #346), merged through PR #208
**Precondition:** Gate 11 PASS, merged into `main` as `e42754ac`
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 12:

- authoritative Expense ledger;
- agricultural organizations with reusable roles;
- supplier/cooperative/company selection;
- purchase items;
- invoice/ticket attachment;
- generic OCR service for purchase invoices/receipts, phytosanitary/fertilizer and irrigation documents;
- extracted-draft → human review → Purchase/Expense draft;
- activity/farm/parcel/campaign relations.

**Gate 12:** no monetary double counting; organization reuse works across contexts; OCR
cannot auto-post money or silently confirm extracted values.

Normative sources: `RC1-NORMATIVE-ADDENDUM` D2 (Expenses are the single ledger; the
Activity "Coste" edits a linked Expense), D10 (Expense is its own aggregate), D11;
`DATA-MODEL-RC1-FUTURE` §10; `DATA-MODEL-RC1.1-ADDENDUM` §3 (organizations + roles) and §8
(purchases); `DATA-MODEL-RC1.2-ADDENDUM` §3–§4 (generic OCR + purchase proposal);
`RC1.2-PRODUCT-LOCK` §4–§6; `SCREEN-MAP-RC1` S60–S62.

## Normative reconciliation

1. **Column names stay as shipped.** Schema v2 created `expenses` with `concept`,
   `amount_minor` and `provider`; the data model calls them `description`, `amount_cents`
   and `supplier_text`. They mean the same thing and are kept: renaming them would be a
   migration with no behavioural gain.
2. **"Draft" needed a status.** The contract says OCR may create a *draft* Expense and
   must never post money, but `expenses` had no state. v6 adds `status` (`DRAFT` |
   `POSTED`). Every total is computed from `POSTED` rows only. Anything a person typed
   into the Expense form or an Activity's Coste is `POSTED`; what a reviewed document
   proposes is `DRAFT` until a person confirms it on the Expense itself.
3. **Which Expense does the Activity "Coste" edit?** D2 allows several Expenses on one
   Activity but says the convenience cost edits "the linked expense". v6 adds `origin`
   (`MANUAL` | `ACTIVITY_COST` | `DOCUMENT_OCR`); the Activity form edits only its single
   `ACTIVITY_COST` row, written in the same transaction as the Activity, and never touches
   other Expenses linked to it. Clearing the Coste deletes that row. `activities.cost_minor`
   is still never read or written.
4. **Roles are aggregate children.** `organization_roles` has a composite key and no
   metadata tail: roles are replaced with their organization and never synchronize alone.
5. **Delivery tickets are not in this phase.** `DELIVERY_TICKET` exists in the OCR model
   (it is one service), but uploading one is offered with Deliveries in Phase 14.

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| OCR engine | ML Kit Text Recognition with the **bundled** Latin model (`com.google.mlkit:text-recognition`): on-device, works without signal, never uploads a document. PDFs are rendered page by page (first 3 pages). |
| What does the parser propose? | Supplier, tax id, invoice number, date, subtotal, tax, total, currency. Line items are left to the person: guessing them from free text is where OCR is least reliable. A field is proposed only when the text says it plainly. |
| When is an extraction `NEEDS_REVIEW`? | When the engine read text but not both a date and a total. `EXTRACTED` still requires review; neither status writes money. |
| What does confirming a document do? | Creates one `DRAFT` Expense (origin `DOCUMENT_OCR`), marks the extraction `CONFIRMED` with `reviewed_at`, and moves the original attachment to the Expense. It runs at most once. |
| Can a document be kept without money? | Yes: "Guardar solo como documento" confirms it with no Expense. Discarding removes the extraction and its file. |
| Expense on a Farm with a running Campaign | Linked to that Campaign automatically, so campaign totals include it. |
| Duplicate organizations | Refused by name within the workspace (case-insensitive). |
| Ambiguous amounts | "65,555" or "1,234" is rejected, never read as a large number. |

## Architecture

```text
Expense form ─┐
Activity Coste ├──►  ExpenseLedgerWriter  ──►  expenses (+ purchases, purchase_items)
Reviewed doc ─┘      (one path for money)       └─ one EXPENSE outbox intent

Upload ─► attachment (owner DOCUMENT) ─► document_ocr_extractions PENDING
       ─► OcrEngine (on device) ─► raw_text + extracted_json, EXTRACTED | NEEDS_REVIEW | FAILED
       ─► person reviews ─► createExpenseDraft ─► DRAFT expense ─► person posts ─► POSTED
```

## Room v6

`MIGRATION_5_6` rebuilds `expenses` (SQLite cannot add a NOT NULL column without a
default) carrying every row over as `POSTED` / `MANUAL`, and creates
`agricultural_organizations`, `organization_roles`, `purchases`, `purchase_items` and
`document_ocr_extractions` empty. `6.json` is exported by the Room compiler in CI.

## Implementation

| Slice | Files |
| --- | --- |
| A — contract | `domain/expense/*`, `domain/organization/*`, `domain/ocr/*` |
| B — Room v6 | `entity/ExpenseEntities.kt`, `entity/CoreEntities.kt`, `dao/ExpenseDao.kt`, `dao/OrganizationDao.kt`, `dao/DocumentOcrDao.kt`, `DatabaseMigrations.kt` |
| C — repositories | `ExpenseLedgerWriter`, `OfflineFirstExpenseRepository`, `OfflineFirstOrganizationRepository`, `OfflineFirstDocumentOcrRepository`, `MlKitOcrEngine`, `ProposalCodec`, D2 in `OfflineFirstActivityRepository` |
| D — UI | `feature/expenses/*` (S60 list, S61 form, S62 detail, organizations, document review); Activity form gains "Coste" |
| E — tests | `ExpenseLedgerContractTest`, `RoomMigrationTest` 5→6, `MoneyTest`, `PurchaseDocumentParserTest`, `ExpenseFormTest` |

Out of scope: Harvest and Delivery relations in the form (their tables arrive in Phases
13–14; the columns exist), delivery-ticket OCR (Phase 14), a Products module, remote sync.
