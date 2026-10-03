# RC1.2 Change Request 013 — Pesada manual + foto; OCR aplazado

Status: **APPROVED BY OWNER, 2026-10-03** (issue #342 «DECISIÓN 1.0 — Pesada manual + foto; OCR
aplazado», decision log #339, parent #318; restated by the owner in the executor session).

Executor: Claude. Implemented by PR #343.

## Motivation

OCR of delivery tickets and invoices is not reliable enough across cooperatives to be a 1.0
dependency, and a cooperative may give no document at all. A Pesada must be recordable quickly and
truthfully without a document and without an OCR understanding it.

## Proposed change

For Mágina Olivo 1.0 the **source of truth of a Pesada is what the farmer types and confirms**:

`Nueva Pesada → kg netos → cooperativa/almazara → nº vale/albarán opcional → parcela(s) →
Árbol/Vuelo o Suelo → foto/archivo del recibo opcional → Guardar`

- The receipt photo/file is evidence attached to that Pesada after it is saved. It never changes
  kilos, campaign, farm or costs and never creates another Pesada.
- A Pesada saves without photo and without ticket number.
- OCR entry points leave the normal experience: «Leer vale», «Añadir vale y leer datos» (Pesadas)
  and «Ticket o factura» (Gastos). Invoice photos are attached from the Expense.
- OCR code, stored documents and existing «por revisar» items stay as they are (dormant, still
  reachable if present). No new parsers, no recognition work, no repair of pending OCR flows.

## Affected baseline sections

- `RC1.2-PRODUCT-LOCK.md` §5 Generic document OCR, §6 Purchase OCR, §7 Delivery OCR: **deferred
  beyond 1.0**. The rule «OCR never auto-accepts values» still holds for any future return.
- `AGENTS.md` RC1.2 requirements «delivery ticket/photo/PDF + OCR review» and «OCR for delivery
  tickets, invoices/receipts and agricultural documents»: superseded for 1.0 by this CR.
- Gates 12/14 OCR evidence stays historical; it is not a 1.0 release requirement.

## Affected phases

No phase reordering. OCR is not required by CR-012, Gate 21, the candidate APK, Backend/Auth,
Sync, PDF, Web or release.

## Data impact

None. No schema change, no migration, no deletion of documents, extractions or attachments.

## Offline/sync impact

None. The receipt is an ordinary local-first attachment (`AttachmentOwner(DELIVERY, id)`) with its
own outbox intent; the Delivery's version and outbox are untouched by attaching it.

## UX impact

One entry «+ Nueva pesada»; an optional «Añadir foto del recibo» in the form. Gastos keeps
«Añadir gasto». Help/privacy text describes photos as attachments.

## Risks

Farmers who used ticket reading type the kilos again by hand (accepted by the owner). Existing
«por revisar» documents remain readable on the phone; the privacy text says so.

## Alternatives considered

Keeping OCR as a secondary path (rejected by the owner: it must not be the main path nor a 1.0
dependency). Removing OCR code (rejected: unnecessary risk).

## Future OCR

Only as a filling assistant: `foto → propuesta → revisión/corrección manual → confirmación
explícita → guardar`. Never source of truth, never auto-saves a Pesada; evaluated with real
documents from several cooperatives before becoming stable.

## Decision

APPROVED (owner, 2026-10-03).
