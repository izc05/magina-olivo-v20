# Phase 16 — Calendar, agenda, reminders and Android notifications

**Phase decision:** EMULATOR EVIDENCE PASS — physical-device clause of Gate 16 **pending owner verification**
**Reviewed:** 2026-09-23
**Base commit:** `216d576b` (`main`, Phase 15 merged)
**Branch:** `claude/dreamy-dijkstra-tdui2c` — PR #212
**Validated commit:** `5566c8ec` (code `c5059df9` + the compiler-exported `10.json`)
**Plan:** `docs/07-plans/PHASE16-AGENDA-REMINDERS.md`

> Every CI, emulator and artifact figure below is copied from a real run. Nothing here
> claims a physical-device result that has not been recorded.

## Gate 16

Reminders fire offline on physical Android under supported OS restrictions.

| Clause | Proof | Status |
| --- | --- | --- |
| Planned work carries people/crew/provider/duration on the same record | `AgendaReminderContractTest.planningAndRemindersAreWrittenWithTheActivityAndOneIntent` (one Activity version and one outbox intent; moving the work keeps each reminder's row and moves its moment), `planningIsOptionalAndNonsenseIsRefusedBeforeWriting` | PASS (emulator) |
| Planned-work projection | `theAgendaListsOnlyPlannedWorkAcrossFarms` (drafts and completed work excluded; `HARVEST_DAY` appointment listed) | PASS (emulator) |
| Completed/cancelled work stops reminding | `completingOrCancellingSilencesTheWork`, `aDroppedReminderIsSwitchedOffAndItsAlarmCancelled`, `aMomentAlreadyGoneIsNeverRungLate` | PASS (emulator) |
| Reminders survive restarts | `remindersSurviveARestartAndAreRescheduled` (what the boot receiver does) | PASS (emulator) |
| A notification is posted offline, once, with a deep link | `theNotifierPostsOnceAndStaysSilentForFinishedWork` (real `NotificationManager`, channel `planned_work`, `contentIntent` present; second fire refused; cancelled work silent) — run in airplane mode | PASS (emulator) |
| The OS alarm service is used | `theAlarmServiceRegistersAndCancelsAReminder` (real `AlarmManager` PendingIntent registered and cancelled), `theReceiversAreDeclaredForAlarmsAndReboots` | PASS (emulator) |
| **Fires offline on a physical phone** | Owner check below | **PENDING** |

Also `RoomMigrationTest.migration9To10AddsEmptyPlanningAndReminderTables`. JVM tests
`ReminderRulesTest` (7), `AgendaTest` (3), `ReminderMessageTest` (2) and `PlanningInputTest` (6)
were compiled and run locally with the Kotlin 2.2.20 compiler before any CI run — 18/18 passed.

## Evidence

| Check | Workflow | Result | Run |
| --- | --- | --- | --- |
| Lint, unit tests, three debug builds, instrumented compilation | `foundation` | PASS | [Android CI run 35874808659](https://github.com/izc05/magina-olivo-v20/actions/runs/35874808659) on `5566c8ec` (dispatch), job `107227861353` |
| Full API 35 instrumentation | `gate3-emulator` | PASS | same run, job `107227861117`, artifact `10756463281` |
| Independent emulator run | `gate3-evidence` | PASS | [Gate 3 Android Emulator Evidence run 35874811956](https://github.com/izc05/magina-olivo-v20/actions/runs/35874811956), artifact `10756103406` |
| Installable DEV APK | `magina-olivo-dev-debug`, artifact `10756037827` | 34 618 028 bytes (zip) | `sha256:477f0034ee2f5145afe324895d9534bea7ba639459a5c364c6219e85fc1b8f23` |
| Suite size | same code on `c5059df9`, before `10.json` existed | 169 instrumented tests, 168 passing — the only failure was the migration test's missing `10.json`; 105/105 in airplane mode, including `AgendaReminderContractTest` 10/10 | Android CI run `35873707085`, job `107224072390` |
| Hand-written migration vs compiler export | comparison of `MIGRATION_9_10` with `10.json` | 2 tables and 4 indices identical | `10.json` identityHash `b78eb793422aa350e54cc390edf47124` |

## Physical-device check (owner) — required before Gate 16 PASS

Install the DEV APK above on a real Android phone, then:

1. Register or edit a pruning/irrigation Activity for today, with a custom reminder
   ("Otro aviso") 3–5 minutes ahead. Allow notifications when the Calendar asks.
2. Turn on airplane mode, lock the phone and wait.
3. Confirm the notification arrives and that tapping it opens that Activity.
4. Plan another reminder a few minutes ahead, reboot the phone, keep airplane mode on and
   confirm it still arrives.
5. Record phone model, Android version and whether "Alarmas y recordatorios" (exact alarms)
   was allowed; without it Android may deliver the reminder some minutes late by design.

## Known gaps

- Without the exact-alarm grant (Android 14+ default), reminders are inexact.
- The Calendar and the planning block are covered by the repository contract and JVM
  rules; there is no Compose UI test of them yet.
- A time-zone change re-reads previous-day and same-day reminders in the new zone at the
  next reconcile (app start, `TIMEZONE_CHANGED`), so "19:00 the evening before" stays 19:00
  local; custom reminders are an instant the farmer chose and do not move
  (`AgendaReminderContractTest.aTimeZoneChangeKeepsTheLocalMeaningOfEachReminder`).
