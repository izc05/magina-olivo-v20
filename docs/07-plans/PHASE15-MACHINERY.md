# Phase 15 — Machinery implementation plan

**Status:** Gate 15 PASS — validated on `ce46234b` (Android CI run 35864581017), merged through PR #211
**Precondition:** Gate 14 PASS, merged into `main` through PR #210 as `a701d2b9`
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 15:

- machinery list/detail;
- activity relation;
- optional hours/usage;
- lightweight field workflow.

**Gate 15:** machinery adds value without making activities mandatory/complex.

Normative sources: `RC1.1-PRODUCT-LOCK` §8 (machinery is a resource; initial entry stays
lightweight; maintenance, insurance, ITV and fuel/hour-meter analytics can grow later
without changing the core), `DATA-MODEL-RC1.1-ADDENDUM` §5 (`machines`,
`activity_machines`), `RC1-NORMATIVE-ADDENDUM` D5 (Activity children share its version and
intent), `DESIGN-SYSTEM-RC1` (machinery reuses the same tokens and components).

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| What must a machine have? | A name. Type defaults to "Otra"; make, model, registration/serial, hour-meter reading and notes are optional. Two machines cannot share a name (case-insensitive), so a choice list is never ambiguous. |
| Where is it reached? | A "Maquinaria" link in the Mi Olivar header. Machinery is a shared resource, not a Farm or a root: the frozen navigation is unchanged. |
| How does an Activity use it? | An optional "Maquinaria (opcional)" section in the existing Activity editor, shown only when machines exist: tick a machine, optionally type its hours. Nothing is required, and an Activity without machines is saved exactly as before. |
| Aggregate | `activity_machines` rows are children of the Activity (D5): written in its transaction, sharing its version and single outbox intent. Naming a machine never changes the machine's own row or version. |
| Hours | `start_hours`, `end_hours`, `usage_hours` all optional. If both readings are given, end ≥ start; if all three, usage = end − start. The UI offers usage hours only. Hours used = typed usage, else the reading difference, else unknown — never guessed. |
| Retiring a machine | Archived, not deleted: it leaves the pickers, past Activities still name it (marked "retirada"), editing such an Activity keeps it, and it can be restored. |
| Machine detail | Lists the Activities that used it and the sum of recorded hours, with the number of uses that have no hours stated separately. |

## Room v9

`MIGRATION_8_9` creates `machines` and `activity_machines` empty. `9.json` is exported by
the Room compiler in CI.

## Implementation

| Slice | Files |
| --- | --- |
| A — rules | `domain/machinery/MachineRules.kt`, `domain/machinery/MachineRepository.kt`; `ActivityRepository` gains optional `machines` |
| B — Room v9 | `entity/MachineEntities.kt`, `dao/MachineDao.kt`, `DatabaseMigrations.kt`, `SyncEntityType.MACHINE` |
| C — repositories | `OfflineFirstMachineRepository`; `OfflineFirstActivityRepository` writes the machines with the Activity |
| D — UI | `feature/machinery/*` (list, form, detail with uses and hours); optional section in the Activity editor and detail; Mi Olivar header link |
| E — tests | `MachineryContractTest`, `RoomMigrationTest` 8→9, `MachineRulesTest` |

Out of scope: maintenance, insurance, ITV, fuel and hour-meter analytics, machinery in
Harvest (it keeps its free-text field), machinery costs (they stay in the Expense ledger),
remote sync.
