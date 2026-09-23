# Phase 16 — Calendar, agenda, reminders and Android notifications

**Status:** emulator evidence PASS on `5566c8ec` (PR #212); Gate 16 physical-device clause pending owner verification
**Precondition:** Gate 15 PASS, merged into `main` through PR #211 as `216d576b`
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 16:

- planned activity projection;
- expected people/crew/provider/planned duration;
- harvest, pruning, irrigation, treatment and external-work appointments;
- previous-day/same-day/custom reminders;
- local notification scheduling;
- notification deep link;
- mark planned work completed/cancelled.

**Gate 16:** reminders fire offline on physical Android under supported OS restrictions.

Normative sources: `RC1.2-PRODUCT-LOCK` §9 (a planned activity is completed later on the
same record, never duplicated), `DATA-MODEL-RC1.1-ADDENDUM` §7 (`reminders`, scheduling is
local-first), `DATA-MODEL-RC1.2-ADDENDUM` §6 (`activity_planning_details`),
`RC1-NORMATIVE-ADDENDUM` D5 (Activity children share its version and intent).

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| What is "planned work"? | An Activity in status PLANNED. The Calendar is a projection of those rows; completing or cancelling from it is the same transition as in the Activity detail, on the same record. |
| Where is planning entered? | An optional "Planificación (opcional)" block in the existing Activity editor: hour, expected duration, expected people, crew/company/provider text, and reminders. Nothing is required. |
| Appointment types | Pruning, irrigation, treatment (phytosanitary) and external work (any type + crew/provider text) already exist. `ActivityType.HARVEST_DAY` ("Jornada de cosecha") is added as an appointment only: it carries no kilos, which always live in a Harvest record. |
| Provider | Free text in `crew_text`. `provider_organization_id` exists in the table but stays null until a provider picker is justified; no organization is invented. |
| Reminder moments | Previous day at 19:00; same day one hour before the planned hour, or 07:00 without one (never slipping into the night before); custom moment. Read in the phone's time zone, because the phone rings them. |
| Aggregate | `activity_planning_details` and the Activity's `reminders` are children of the Activity (D5): written in its transaction, one version, one outbox intent. Marking a reminder fired is device state: no version bump, no intent. |
| Editing | A reminder of the same kind keeps its row and alarm slot when the date or hour moves; a dropped reminder is switched off (not deleted) so its alarm can still be found and cancelled. A moment already gone when saved is marked spent and never rung late. |
| Alarms | `AlarmManager`: exact when Android allows it (`SCHEDULE_EXACT_ALARM` declared, `canScheduleExactAlarms()` checked), otherwise inexact `setAndAllowWhileIdle`. The rows are the truth; alarms are rebuilt after every planned-work change, at app start, after reboot, app update, clock/time-zone change and exact-alarm permission change. A reminder missed while the phone was off still rings if at most 12 h late. |
| Notifications | Channel "Trabajos planificados". `POST_NOTIFICATIONS` is asked in context from the Calendar, only when reminders exist and notifications are off. Tapping opens the Activity over the Calendar (deep link through `MainActivity`, `singleTop`). |
| Navigation | The frozen Calendario root replaces its placeholder; no root, tab or route outside the Activity detail is added. |

## Room v10

`MIGRATION_9_10` creates `activity_planning_details` and `reminders` empty. `10.json` is
exported by the Room compiler in CI.

## Implementation

| Slice | Files |
| --- | --- |
| A — rules | `domain/agenda/Planning.kt` (planning, reminder rules), `Agenda.kt` (buckets), `ReminderMessage.kt` |
| B — Room v10 | `entity/AgendaEntities.kt`, `dao/AgendaDao.kt`, `DatabaseMigrations.kt`, `ActivityWithTargets` relations |
| C — repository | `OfflineFirstActivityRepository` writes planning and reminders with the Activity and exposes `observeAgenda()` |
| D — Android | `data/reminder/*`: `ReminderCoordinator`, `AndroidReminderScheduler`, `ReminderNotifier`, alarm and reschedule receivers; manifest; `MainActivity` deep link |
| E — UI | `feature/agenda/*` (Calendario), `feature/activities/PlanningFields.kt`, `PlanningInput.kt` |
| F — tests | `AgendaReminderContractTest` (offline suite), `RoomMigrationTest` 9→10, `ReminderRulesTest`, `AgendaTest`, `ReminderMessageTest`, `PlanningInputTest` |

Out of scope: irrigation-owner reminders (the `owner_type` column allows them later),
provider organization picker, recurring plans, month-grid calendar, remote sync of
reminders, Home widgets.

## Gate 16 evidence plan

- Emulator (CI, airplane mode): `AgendaReminderContractTest` — the notifier posts from
  local data only, the alarm service registers/cancels, receivers are declared for alarms
  and reboots, alarms follow completion/cancellation, reminders survive a restart.
- **Physical Android:** pending owner verification — plan work for a few minutes ahead with
  a custom reminder, switch on airplane mode, lock the phone and confirm the notification
  arrives and opens the Activity; repeat once after a reboot. This cannot be produced from
  the CI environment and is reported as pending, not as PASS.
