# Mágina Olivo Android — RC1 Baseline Gate Review

**Baseline:** `RC1-BASELINE-2026-09-17`  
**Review date:** 2026-09-17  
**Scope reviewed:** phases 0.3 through 0.7 before any Android production implementation.

## Review objective

Check that the pre-implementation contracts are mutually consistent and sufficient to begin Phase 1 without requiring Codex/Antigravity to invent architecture or domain rules.

## Documents reviewed

- `docs/00-master/RC1-BASELINE.md`
- `docs/00-master/MASTER-SPEC-RC1.md`
- `docs/00-master/CHANGE-CONTROL.md`
- `docs/02-domain/DATA-MODEL-RC1-FUTURE.md`
- `docs/03-maps/CADASTRE-CONTRACT-RC1.md`
- `docs/01-architecture/OFFLINE-SYNC-CONTRACT-RC1.md`
- `docs/04-ui/SCREEN-MAP-RC1.md`
- `docs/04-ui/DESIGN-SYSTEM-RC1.md`
- `docs/07-plans/ROADMAP-RC1.md`

## Findings resolved before approval

### F1 — Financial double-count risk

The domain draft contained `activities.cost_cents` while also defining `expenses` as linked financial records. This could allow reports to count the same cost twice.

**Resolution:** `expenses` is the only authoritative financial ledger. Activity form cost is a convenience UI that creates/edits linked Expense records. See Normative Addendum D2/D11.

### F2 — RLS membership ambiguity

`workspace_members` was described as optional infrastructure while RLS authorization depends on workspace membership.

**Resolution:** membership is mandatory RC1 infrastructure, even though shared-workspace UI remains post-RC1. See D1.

### F3 — Historical farm rename risk

Campaign snapshot stored `farm_id_at_start` but not the farm name. Renaming a farm later could therefore alter the human-readable historical report.

**Resolution:** snapshot includes `farm_name_at_start`. See D3.

### F4 — Snapshot finalization timing

The contracts described snapshots but did not explicitly freeze the participant set at one lifecycle transition.

**Resolution:** snapshot is finalized atomically when PREPARATION becomes ACTIVE. See D4/D7.

### F5 — Partial sync risk for typed activity children

Normalized activity detail tables could be interpreted as separately versioned/synchronized records.

**Resolution:** Activity is one synchronization aggregate containing its typed detail and parcel joins. See D5/D10.

### F6 — Partial sync risk for harvest allocation

Same issue for `harvest_parcels`.

**Resolution:** Harvest is one synchronization aggregate. See D6/D10.

### F7 — Future Product module accidentally becoming an RC1 dependency

Reserved `product_id` fields could be interpreted as hard FKs before Products exists.

**Resolution:** RC1 persists `product_name`; Product linkage remains a future migration/optional extension. See D8.

### F8 — Campaign lifecycle vs soft-delete rule

Campaign draft did not clearly state safe deletion semantics.

**Resolution:** only preparation/cancel scenarios may tombstone where valid; active/harvest/closed campaigns are protected historical data. See D9.

## Phase 0.3 — Data Model Gate

Result: **PASS WITH NORMATIVE ADDENDUM**.

Verified:

- workspace ownership is explicit;
- M:N relations are modeled explicitly;
- farm/parcel history is non-destructive;
- campaign history uses snapshots;
- client UUIDs permit offline creation;
- units and money representation are explicit;
- parcel geometry persists independently of Catastro;
- typed activity details avoid a giant JSON/form model;
- harvest and delivery are separate;
- unknown harvest allocation remains representable;
- attachments are local-first;
- conflicts can retain both local and remote versions;
- future Mi Campo modules do not become RC1 dependencies;
- RLS authorization path is now unambiguous;
- aggregate sync boundaries are now explicit.

## Phase 0.4 — Catastro Contract Gate

Result: **PASS**.

Verified:

- WMS is discovery/visualization only;
- WFS/GML provides normalized candidate geometry;
- reference and map search converge on `ParcelCandidate`;
- CRS conversion is isolated inside the adapter;
- app geometry is persisted in a single normalized representation;
- confirmed parcels remain available offline;
- duplicate checks and provider failure behavior are defined;
- no mass WFS download is part of RC1;
- provenance/licence boundaries are documented;
- protected ownership data is outside RC1;
- Phase 9 real-parcel spike remains mandatory before productionizing parser/geometry behavior.

## Phase 0.5 — Offline/Sync Gate

Result: **PASS WITH NORMATIVE ADDENDUM**.

Verified:

- Room/local repository is UI source of truth;
- accepted writes commit locally first;
- domain mutation and outbox are atomic;
- client IDs support offline relation creation;
- retries are designed to be idempotent;
- remote concurrency uses server-controlled version metadata;
- critical records do not use blind last-write-wins;
- tombstone/soft-delete behavior is defined;
- attachments are local-first;
- WorkManager schedules durable outbox work rather than replacing it;
- bootstrap and incremental pull are separate;
- new-device recovery is defined;
- pending work survives upgrade/migration by contract;
- aggregate boundaries are clarified by the normative addendum.

## Phase 0.6 — Screen Map Gate

Result: **PASS WITH NORMATIVE COST INTERPRETATION**.

Verified:

- root navigation remains `Inicio · Fincas · Campaña · Registrar · Más`;
- all core RC1 entities have required access/create/edit flows;
- contextual Register preselection is defined;
- Catastro provider failure does not hide owned parcels;
- harvest and deliveries are separate workflows;
- sync/conflict states have a destination;
- future modules can enter under secondary navigation without adding root tabs;
- historical campaigns have a protected/read-focused flow;
- activity-form cost inputs mean linked Expense creation, per Addendum D11.

## Phase 0.7 — Design System Gate

Result: **PASS**.

The design contract is sufficient to begin a reusable Compose implementation without inventing a new visual language per screen. Detailed pixel-level iteration remains allowed during implementation provided it does not change navigation/domain contracts or reduce accessibility/usability.

## Baseline review conclusion

Phases **0.3, 0.4, 0.5, 0.6 and 0.7 pass their pre-implementation documentation Gates** subject to the normative addendum, which is part of the baseline.

The next allowed phase after this review is:

```text
PHASE 1 — ANDROID PROJECT FOUNDATION
```

No production feature implementation from later phases should start before Gate 1 is completed.
