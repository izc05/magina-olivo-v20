# Mágina Olivo Android — RC1 Normative Addendum

**Baseline:** `RC1-BASELINE-2026-09-17`  
**Status:** NORMATIVE  
**Purpose:** resolve cross-document ambiguities found during the final baseline Gate review before implementation.

This document is part of the RC1 baseline. Where an RC1 component draft conflicts with a rule below, **this addendum wins**. Future structural changes require the normal Change Request process.

## D1 — Workspace membership is required RC1 infrastructure

`workspace_members` is not optional in the implemented RC1 schema.

Every personal workspace created for an account must also create an active membership:

```text
workspace_members
- workspace_id
- user_id
- role = OWNER
- status = ACTIVE
```

RC1 does not need a collaboration UI, but remote authorization/RLS must use workspace membership from day one. `workspaces.owner_user_id` may remain as the stable owner anchor, but it does not replace membership-based authorization.

**Reason:** every remote row is authorized through `workspace_id`; leaving membership optional would make the RLS contract ambiguous.

---

## D2 — Expenses are the single authoritative financial ledger

RC1 financial totals are derived **only from `expenses` records**.

The `activities.cost_cents` / `activities.currency` fields shown in the Phase 0.3 draft are **superseded and must not be implemented as an independent authoritative cost field**.

When an activity form exposes a convenient `Coste` input, saving that value creates or updates an `expenses` row linked through `activity_id` in the same local transaction.

Conceptually:

```text
Activity form
  └── Coste 65,00 €
          ↓
Activity
+
Expense(activity_id = Activity.id, amount_cents = 6500)
```

Rules:

1. campaign/farm/parcel/activity cost summaries aggregate `expenses` only;
2. an activity-linked expense is counted once even if it is visible from several contexts;
3. editing the convenience cost edits the linked expense, not a second number on the activity;
4. additional expenses may also be linked to the same activity when required;
5. reports must never sum activity cost plus expense rows.

**Reason:** prevents double counting and establishes one financial source of truth.

---

## D3 — Campaign parcel snapshot also freezes farm identity/name

The implemented `campaign_parcels` snapshot must include:

```text
farm_id_at_start UUID NOT NULL
farm_name_at_start String NOT NULL
parcel_name_at_start String NOT NULL
managed_area_m2_at_start Decimal?
cadastral_reference_at_start String?
geometry_snapshot Polygon/MultiPolygon?
```

If future flows ever allow a campaign parcel without a farm, that is a baseline change; RC1 does not.

Historical campaign reports use `farm_name_at_start`, not the current farm name.

**Reason:** renaming or reorganizing a farm years later must not rewrite campaign history.

---

## D4 — Campaign snapshot is finalized on activation

During `PREPARATION`, parcel selection may change.

Transition:

```text
PREPARATION
   ↓ activate
ACTIVE
```

must atomically materialize/finalize the campaign parcel snapshot used for history.

After activation, RC1 normal UI does not silently add/remove parcels from that historical participant set. Any later need for campaign amendments must be an explicit audited feature and is not invented during implementation.

**Reason:** makes campaign history deterministic.

---

## D5 — Activity typed details are part of the Activity aggregate

These tables:

```text
pruning_details
fertilization_details
phytosanitary_details
soil_work_details
irrigation_details
maintenance_details
incident_details
activity_parcels
```

are aggregate children of `activities` for RC1 synchronization.

They do **not** independently decide conflict/version policy.

A change to a typed detail or activity-parcel relation:

1. occurs in the same local transaction as the parent activity mutation;
2. increments/mutates the Activity aggregate version/state;
3. queues the Activity aggregate for synchronization;
4. is resolved as part of the Activity conflict payload.

Remote implementation may use multiple relational tables, but synchronization semantics operate on one Activity aggregate.

**Reason:** avoids partial synchronization where an activity header and its agronomic details disagree.

---

## D6 — Harvest distribution rows are part of the Harvest aggregate

`harvest_parcels` are synchronized/versioned as children of `harvests`, following the same aggregate principle as activities.

Changing parcel distribution must update the Harvest aggregate atomically.

Unknown allocation remains explicitly `UNALLOCATED`; synchronization never invents a split.

---

## D7 — Campaign snapshot rows are aggregate children

`campaign_parcels` are managed as children of the `campaigns` aggregate. Snapshot creation/finalization is atomic with campaign activation.

They are not independently editable historical records after activation.

---

## D8 — Future Product references must not create an RC1 dependency

`product_id` shown in fertilization/phytosanitary draft schemas is a reserved nullable extension identifier.

Until the Product module exists:

- RC1 must not require a `products` table to create a fertilizer/treatment activity;
- no hard RC1 FK dependency on the future module is required;
- `product_name` is the persisted historical text used by RC1.

When Products arrive in RC2, `product_id` can be introduced/activated through an explicit migration while preserving historical `product_name` snapshots.

**Reason:** future scalability must not make RC1 depend on an optional module.

---

## D9 — Campaign deletion policy

`campaigns` must support safe lifecycle semantics consistent with the baseline soft-delete rule.

Implementation requirements:

- PREPARATION campaigns may be soft-deleted/cancelled subject to no protected historical data;
- ACTIVE/HARVEST/CLOSED campaigns are not physically deleted by normal RC1 UI;
- CLOSED campaigns remain historical records;
- remote synchronization uses tombstone semantics wherever a campaign deletion is allowed.

The concrete schema may use `deleted_at` on `campaigns`; physical delete is not a normal user operation.

---

## D10 — Aggregate boundaries for synchronization

RC1 synchronization roots are conceptually:

```text
Workspace / Membership
Farm
Parcel
FarmParcelMembership
Campaign (+ CampaignParcels snapshot)
Activity (+ ActivityParcels + typed detail)
Expense
Harvest (+ HarvestParcels)
Delivery
Attachment
```

This list defines how implementation should avoid partial aggregate state even if PostgreSQL/Room use normalized child tables.

---

## D11 — Cost UI wording

Where screen specifications currently say an Activity form includes `cost`, interpret it as:

> convenience creation/editing of linked Expense data.

It does not restore `activities.cost_cents` as a second ledger.

---

## Precedence order

When implementing RC1, read contracts in this order:

1. `docs/00-master/RC1-BASELINE.md`
2. `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`
3. `docs/00-master/MASTER-SPEC-RC1.md`
4. architecture/domain/map/UI contracts
5. roadmap/implementation plan

A lower document may add detail but may not contradict a higher one.
