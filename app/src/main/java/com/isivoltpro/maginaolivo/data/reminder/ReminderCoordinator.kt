package com.isivoltpro.maginaolivo.data.reminder

import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.agenda.ReminderReconciler
import java.time.Duration
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferencesSource
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRules
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One alarm to register on the device. */
data class ScheduledReminder(
    val reminderId: UUID,
    val activityId: UUID,
    val requestCode: Int,
    val triggerAt: Instant,
)

/** The device's alarm service, behind a boundary so the rules can be tested without it. */
interface ReminderScheduler {
    fun schedule(reminder: ScheduledReminder)
    fun cancel(requestCode: Int)
}

/**
 * Projects the stored reminders onto device alarms (`DATA-MODEL-RC1.1-ADDENDUM` §7).
 *
 * The `reminders` rows are the truth; alarms are rebuilt from them after every change to
 * planned work, at app start and after a reboot or clock change, so a reminder survives
 * everything the phone does to its alarms. It works entirely offline.
 */
class ReminderCoordinator(
    private val database: MaginaOlivoDatabase,
    private val scheduler: ReminderScheduler,
    private val clock: AppClock,
    /** Phase 21B: Perfil → Avisos. Without one, reminders ring at the rules' default hour. */
    private val preferences: ReminderPreferencesSource = ReminderPreferencesSource {
        ReminderPreferences(previousDayTime = ReminderRules.PREVIOUS_DAY_TIME)
    },
    /** The wall clock reminders are read in: the phone's, as when they were saved. */
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReminderReconciler {
    private val lock = Mutex()

    override suspend fun reconcile() = lock.withLock {
        repairLegacyRequestCodeCollisions()
        val current = preferences.current()
        followWallClock(current)
        if (!current.enabled) {
            // Reminders switched off in Perfil: every alarm is withdrawn, every reminder kept.
            database.agendaDao().listUnfired().forEach { scheduler.cancel(it.localNotificationId) }
            return@withLock
        }
        val due = database.agendaDao().listDue(clock.nowInstant().minus(MISSED_GRACE))
        val dueIds = due.map { it.id }.toSet()
        database.agendaDao().listUnfired()
            .filter { it.id !in dueIds }
            .forEach { scheduler.cancel(it.localNotificationId) }
        due.forEach { scheduler.schedule(ScheduledReminder(it.id, it.ownerId, it.localNotificationId, it.triggerAt)) }
    }

    /**
     * #573 upgrade path without a Room schema bump. Request codes are device state, so duplicate
     * legacy slots can be reassigned in place. Cancelling the shared old slot first is safe:
     * the normal reconcile below immediately rebuilds every alarm that should still exist.
     */
    private suspend fun repairLegacyRequestCodeCollisions() {
        val dao = database.agendaDao()
        val rows = dao.listAllReminders()
        if (rows.size < 2) return
        val repaired = repairReminderRequestCodes(rows.map { it.id to it.localNotificationId })
        rows.forEach { row ->
            val requestCode = repaired[row.id] ?: return@forEach
            if (requestCode != row.localNotificationId) {
                scheduler.cancel(row.localNotificationId)
                dao.updateLocalNotificationId(row.id, requestCode)
            }
        }
    }

    /**
     * "The day before at 08:00" means 08:00 where the phone is. After a time-zone change — or a
     * new day-before hour chosen in Perfil — each derived reminder is re-read from its Activity's
     * date and planned hour, so it keeps its local meaning. Idempotent.
     */
    private suspend fun followWallClock(current: ReminderPreferences) {
        val zoneId = zone()
        database.agendaDao().listDerived().forEach { row ->
            val kind = ReminderKind.entries.firstOrNull { it.name == row.kind } ?: return@forEach
            val start = row.plannedStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val trigger = ReminderRules.triggerAt(row.activityDate, start, ReminderRequest(kind), zoneId, current.previousDayTime)
            if (trigger != row.triggerAt) database.agendaDao().moveTrigger(row.id, trigger)
        }
    }

    companion object {
        /** A reminder missed while the phone was off still rings if it is at most this late. */
        val MISSED_GRACE: Duration = Duration.ofHours(12)
    }
}
