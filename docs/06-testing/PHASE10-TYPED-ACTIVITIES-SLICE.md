# Phase 10 — Typed agricultural activities + irrigation

**Phase decision:** PENDING — filled in from a real run when CI is green
**Reviewed:** 2026-09-22
**Base commit:** `f82be163` (`main`, Phase 9 merged)
**Branch:** `feat/android-typed-activities`

> Every CI, emulator and artifact figure below is copied from a real run. Nothing is
> estimated, and no evidence from an earlier phase is reused or rewritten.

## Scope

The typed agronomic detail of an Activity. Phase 9 built the common Activity aggregate —
header, lifecycle, multi-parcel targeting, one outbox intent. Phase 10 adds **what kind of
work it was**, as structured fields, without creating a second Activity and without a
giant form.

In scope:

- the seven typed detail tables the contract defines, each one-to-one with its Activity;
- the irrigation tariff snapshot from `RC1.2-PRODUCT-LOCK` §8;
- a detail that always matches the Activity's type, enforced before anything is written;
- the detail as an aggregate child: same transaction, same version, same single intent;
- per-type validation of the numbers that are always wrong when negative;
- a real editor that shows only the block of the chosen type;
- Room v5 with a purely additive `MIGRATION_4_5`.

Explicitly out of scope: a Products module, expenses and cost, attachments, harvest,
deliveries, machinery, catastro, geometry capture and any remote work.

## Normative reconciliation

Five findings, all resolved from the documents themselves. None required inventing a
product decision, and none is a contradiction that blocks the phase.

1. **The typed tables already exist in the contract.** `DATA-MODEL-RC1-FUTURE.md` §9
   defines all seven with their exact columns, and `RC1-NORMATIVE-ADDENDUM` D5 adopts
   those names normatively. Phase 10 implements them as written.
2. **`irrigation_details` is defined twice.** `DATA-MODEL-RC1-FUTURE.md` gives
   `sector_text` / `system_text`; `DATA-MODEL-RC1.1-ADDENDUM.md` replaces them with
   `sector_id` / `provider_organization_id` / `irrigation_system`. The later version is
   not buildable in RC1 — neither `irrigation_sectors` nor `agricultural_organizations`
   exists and no phase before 12 or 15 owns them — and the owner's Phase 10 brief names
   the `_text` set explicitly. The `_text` columns are implemented, which is also the
   pattern every other fallback follows (`product_name`, `supplier_text`, `asset_text`).
3. **The irrigation tariff snapshot is normative and not contradictory.**
   `RC1.2-CHANGE-REQUEST` CR-002 item 16, `RC1.2-PRODUCT-LOCK` §8 and
   `DATA-MODEL-RC1.2-ADDENDUM` §5 all define it, all in the same breath as "the Expense
   ledger remains authoritative". It is its own table keyed by `activity_id`, not columns
   on the irrigation detail and not a field on the Expense. It is implemented as
   specified, as an estimate that is never summed into a financial total.
4. **Cost stays out of `activities`.** The clause that supersedes
   `activities.cost_cents` / `activities.currency` is **D2**, not D3 — two documents cited
   the wrong letter and are corrected in this branch. The columns still exist from schema
   v2 and Phase 10 neither reads nor writes them.
5. **`OBSERVATION` and `OTHER` get no table.** The contract defines none for them, and
   `DATA-MODEL-RC1-FUTURE.md:470` conditions the one-detail rule on "activity types that
   require structured details". They carry no detail at all.

### Decisions this phase had to make, and why

The contract leaves these open. Each was settled the least surprising way and is recorded
here rather than buried in code.

| Question | Decision |
| --- | --- |
| Seven enum sets are undefined (`pruning_type`, `residue_management`, `application_method`, soil `work_type` and `method`, `maintenance_type`, incident `category`) | Left as free text. Inventing a closed list is a product decision, and a wrong list is worse than a free field. Only `severity` and `incident_status`, which the contract does define, are enums. |
| Is a detail required? | No. The rule is that whatever detail exists matches the type. A draft may still be nothing but a header, and an untouched block writes no row. |
| What happens when an Activity is retyped? | Its detail is replaced in the same transaction. Keeping the old row would hide a record under a type that can no longer read it. |
| Metadata columns on the detail tables | The same `workspace_id` and metadata tail as every other table here, so the sync layer never special-cases them. D5 governs synchronization semantics, not physical columns. |
| `operator_text` / `machinery_text` | Not added. They are in the data-model draft but not in the shipped `activities` table, and no Phase 10 detail table needs them. Recorded as a gap rather than invented. |

## Architecture

Unchanged. The Activity is still the aggregate root.

```text
Compose  →  ViewModel (StateFlow)  →  ActivityRepository  →  Room  →  Outbox
                                          │
                     activities ──┬── activity_parcels        (Phase 9)
                                  └── <type>_details          (Phase 10)
                                        └── irrigation_price_snapshots
```

- A detail is validated against the Activity's type **before** anything is written.
- Header, detail and Parcel targets are written in one transaction, in that order, so a
  rejected Parcel rolls the detail back with everything else.
- The detail moves the Activity's own version and queues no intent of its own: one
  Activity, one outbox row, however many children changed (`RC1-NORMATIVE-ADDENDUM` D5).
- `product_id` is reserved, nullable and carries no foreign key (D8).

## No giant form

```text
Nueva actuación
  ├── descripción, fecha            (common header, always)
  ├── tipo de trabajo               (choose one)
  ├── ▸ block of that type only     ← pruning | fertilisation | treatment |
  │                                    soil work | irrigation | maintenance | incident
  ├── parcelas                      (many, still one Activity)
  └── notas
```

Choosing a type swaps the block; the previous type's fields are gone, not hidden below.
`OBSERVATION` and `OTHER` show no block at all. Turning the form into a typed detail is a
pure function, which is why the rule is provable on the JVM as well as on a device.

## Implementation

| Slice | Files |
| --- | --- |
| A — contract | `domain/activity/ActivityDetail.kt`, `domain/activity/ActivityRepository.kt` |
| B — Room v5 | `entity/ActivityDetailEntities.kt`, `model/ActivityRows.kt`, `dao/ActivityDao.kt`, `DatabaseMigrations.kt`, `MaginaOlivoDatabase.kt`, `RoomConverters.kt` |
| C — repository | `data/repository/OfflineFirstActivityRepository.kt` |
| D — form model | `feature/activities/ActivityDetailForm.kt` |
| E — UI | `feature/activities/ActivityScreens.kt`, `feature/activities/ActivityViewModels.kt` |
| F — tests | `TypedActivityDetailContractTest.kt`, `RoomMigrationTest.kt`, `AppNavigationTest.kt`, `ActivityDetailFormTest.kt` |
| G — evidence | this document |

## Tests added

`TypedActivityDetailContractTest` — instrumented, and also run under airplane mode:

| Test | Contract clause |
| --- | --- |
| `pruningKeepsItsOwnAgronomicFields` | PRUNING round trip |
| `fertilizationKeepsTheProductAsHistoricalTextWithoutAProductsModule` | FERTILIZATION round trip; `product_id` stays null |
| `phytosanitaryKeepsSubstanceReasonAndEquipment` | PHYTOSANITARY round trip |
| `soilWorkKeepsItsWorkTypeAndMethod` | SOIL_WORK round trip |
| `irrigationKeepsDurationVolumeSectorAndSystem` | IRRIGATION round trip |
| `maintenanceKeepsTypeAndAsset` | MAINTENANCE round trip |
| `incidentKeepsCategorySeverityAndState` | INCIDENT round trip |
| `theIrrigationTariffIsAHistoricalSnapshotAndNotASecondLedger` | the snapshot never becomes a cost on the Activity, and creates no Expense |
| `aDetailOfAnotherTypeIsRejectedWithoutPersistingAnything` | an irrigation can never carry fertilisation figures |
| `retypingAnActivityReplacesItsDetailInsteadOfKeepingTwo` | exactly one detail, always matching |
| `negativeAgronomicNumbersAreRejected` | typed validation |
| `editingADetailMovesTheActivityAggregateVersion` | D5 clause 2 |
| `aDetailNeverQueuesAnIntentOfItsOwn` | D5 clause 3: one intent for the whole aggregate |
| `theDetailIsWrittenInTheSameTransactionAsTheHeader` | D5 clause 1: atomicity |
| `multiParcelTargetingIsUntouchedByTypedDetails` | Phase 9 guarantee intact |
| `typedDetailsSurviveAProcessRestart` | restart persistence |
| `observationAndOtherCarryNoDetailTable` | no table for the untyped kinds |

`RoomMigrationTest.migration4To5KeepsTheActivityAggregateAndAddsTypedDetails` — validated
against the compiler-exported schema: the Phase 9 Activity and its `activity_parcels`
relation survive untouched, the eight new tables appear empty, and the irrigation columns
are the ones the contract names.

`AppNavigationTest.theActivityEditorShowsOnlyTheTypedBlockOfTheChosenType` — E2E on a real
emulator: Observación shows no block, Poda shows pruning fields and no irrigation field,
switching to Riego removes every pruning field, and what was typed into the block that was
showing is what the saved Activity carries, across a process restart.

`ActivityDetailFormTest` — JVM: what a block writes, the comma as a decimal separator, the
derived tariff estimate, and an unreadable value dropped rather than guessed.

## Room v5 schema

Exported by the Room compiler during a real Gradle build and committed verbatim from CI
(`d7ea2914`). The `identityHash` is the compiler's own,
`a1fcd78acb39c2497f0f20efb5602598`, and was never written by hand. The hand-written
`MIGRATION_4_5` statements were then checked column by column and index by index against
that export before the migration test ran.

A new Room version costs one bootstrap run: `foundation` and `gate3-emulator` start from
the same commit, so the emulator job cannot see a schema that `foundation` commits during
that same run. The first run after a version bump publishes the schema; the next one runs
the migration test against it.

## Evidence

PENDING — filled in from the green run.
