package com.isivoltpro.maginaolivo.data.local

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.reminder.AndroidReminderScheduler
import com.isivoltpro.maginaolivo.data.reminder.ReminderAlarmReceiver
import com.isivoltpro.maginaolivo.data.reminder.ReminderCoordinator
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.data.reminder.ReminderOutcome
import com.isivoltpro.maginaolivo.data.reminder.ReminderRescheduleReceiver
import com.isivoltpro.maginaolivo.data.reminder.ReminderScheduler
import com.isivoltpro.maginaolivo.data.reminder.ScheduledReminder
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 16 — Calendar, agenda, reminders and Android notifications.
 *
 * Gate 16: reminders fire offline on Android under supported OS restrictions. This suite
 * runs in airplane mode on the emulator; the physical-device run is recorded separately.
 */
@RunWith(AndroidJUnit4::class)
class AgendaReminderContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000f1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000f1")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-0000000000f1")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val day = LocalDate.parse("2026-11-20")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var alarms: FakeScheduler
    private lateinit var coordinator: ReminderCoordinator
    private lateinit var activities: OfflineFirstActivityRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        alarms = FakeScheduler()
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    // ------------------------------------------------------------ part of the Activity aggregate

    @Test
    fun planningAndRemindersAreWrittenWithTheActivityAndOneIntent() = runBlocking {
        val id = ok(
            activities.create(
                planned(
                    ActivityPlanning(LocalTime.of(8, 0), 240, 4, "Cuadrilla Pérez"),
                    ReminderRequest(ReminderKind.PREVIOUS_DAY),
                    ReminderRequest(ReminderKind.SAME_DAY),
                ),
            ),
        )
        val created = activities.observe(id).first()!!
        assertEquals(ActivityPlanning(LocalTime.of(8, 0), 240, 4, "Cuadrilla Pérez"), created.planning)
        assertEquals(
            listOf(Instant.parse("2026-11-19T18:00:00Z"), Instant.parse("2026-11-20T06:00:00Z")),
            created.reminders.map { it.triggerAt },
        )
        assertEquals(created.reminders.map { it.id }.toSet(), alarms.active.keys)

        // Moving the work keeps each reminder's row and alarm slot, and moves its moment.
        ok(activities.update(id, changes(day.plusDays(1), ActivityPlanning(LocalTime.of(9, 30)), ReminderRequest(ReminderKind.SAME_DAY))))
        val moved = activities.observe(id).first()!!
        assertEquals(2L, moved.version)
        assertEquals(ActivityPlanning(LocalTime.of(9, 30)), moved.planning)
        val sameDay = moved.reminders.single()
        assertEquals(created.reminders.single { it.kind == ReminderKind.SAME_DAY }.id, sameDay.id)
        assertEquals(Instant.parse("2026-11-21T07:30:00Z"), sameDay.triggerAt)
        assertEquals(setOf(sameDay.id), alarms.active.keys)
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id).map { it.operation },
        )
    }

    @Test
    fun planningIsOptionalAndNonsenseIsRefusedBeforeWriting() = runBlocking {
        val plain = ok(activities.create(planned(null)))
        assertNull(activities.observe(plain).first()!!.planning)
        assertTrue(activities.observe(plain).first()!!.reminders.isEmpty())

        assertValidation("expectedPeopleCount", activities.create(planned(ActivityPlanning(expectedPeopleCount = 0))))
        assertValidation("reminders", activities.create(planned(null, ReminderRequest(ReminderKind.CUSTOM))))
        assertEquals(1, activities.observeForFarm(farmId).first().size)
    }

    // ------------------------------------------------------------ the Calendar

    @Test
    fun theAgendaListsOnlyPlannedWorkAcrossFarms() = runBlocking {
        val planned = ok(activities.create(planned(ActivityPlanning(crewText = "Riegos Sur"), type = ActivityType.IRRIGATION)))
        val harvestDay = ok(activities.create(planned(null, type = ActivityType.HARVEST_DAY)))
        ok(activities.create(NewActivity(farmId, null, ActivityType.PRUNING, day, "Borrador", asDraft = true)))
        val done = ok(activities.create(planned(null)))
        ok(activities.complete(done))

        val agenda = activities.observeAgenda().first()
        assertEquals(setOf(planned, harvestDay), agenda.map { it.activityId }.toSet())
        val irrigation = agenda.single { it.activityId == planned }
        assertEquals("La Solana", irrigation.farmName)
        assertEquals(listOf("Norte"), irrigation.parcelNames)
        assertEquals("Riegos Sur", irrigation.planning?.crewText)
    }

    // ------------------------------------------------------------ alarms follow the rows

    @Test
    fun completingOrCancellingSilencesTheWork() = runBlocking {
        val first = ok(activities.create(planned(null, ReminderRequest(ReminderKind.PREVIOUS_DAY))))
        val second = ok(activities.create(planned(null, ReminderRequest(ReminderKind.SAME_DAY))))
        assertEquals(2, alarms.active.size)

        ok(activities.complete(first))
        ok(activities.cancel(second))

        assertTrue(alarms.active.isEmpty())
        assertEquals(2, alarms.cancelled.size)
        // Reopened work is planned again, so its reminder is back.
        ok(activities.reopen(first))
        assertEquals(1, alarms.active.size)
    }

    @Test
    fun aDroppedReminderIsSwitchedOffAndItsAlarmCancelled() = runBlocking {
        val id = ok(activities.create(planned(null, ReminderRequest(ReminderKind.CUSTOM, LocalDateTime.parse("2026-11-18T12:00")))))
        val code = db.agendaDao().listForOwner("ACTIVITY", id).single().localNotificationId
        ok(activities.update(id, changes(day, null)))

        assertTrue(activities.observe(id).first()!!.reminders.isEmpty())
        assertFalse(db.agendaDao().listForOwner("ACTIVITY", id).single().enabled)
        assertTrue(alarms.active.isEmpty())
        assertTrue(code in alarms.cancelled)
    }

    @Test
    fun aMomentAlreadyGoneIsNeverRungLate() = runBlocking {
        // Work for today with a "previous evening" reminder: that evening has passed.
        val id = ok(activities.create(NewActivity(farmId, null, ActivityType.OTHER, LocalDate.parse("2026-11-10"), "Hoy", setOf(parcelId), reminders = listOf(ReminderRequest(ReminderKind.PREVIOUS_DAY)))))
        assertNotNull(activities.observe(id).first()!!.reminders.single().firedAt)
        assertTrue(alarms.active.isEmpty())
    }

    @Test
    fun remindersSurviveARestartAndAreRescheduled() = runBlocking {
        val id = ok(activities.create(planned(null, ReminderRequest(ReminderKind.PREVIOUS_DAY))))
        db.close()
        alarms = FakeScheduler()
        open()
        // What the boot receiver does after a reboot wiped every alarm.
        coordinator.reconcile()
        assertEquals(activities.observe(id).first()!!.reminders.map { it.id }.toSet(), alarms.active.keys)
    }

    // ------------------------------------------------------------ Android

    @Test
    fun theNotifierPostsOnceAndStaysSilentForFinishedWork() = runBlocking {
        grantNotifications()
        val id = ok(activities.create(planned(ActivityPlanning(LocalTime.of(8, 0), expectedPeopleCount = 3), ReminderRequest(ReminderKind.PREVIOUS_DAY))))
        val reminder = db.agendaDao().listForOwner("ACTIVITY", id).single()
        val notifier = ReminderNotifier(context, db, FixedClock(now))

        assertEquals(ReminderOutcome.POSTED, notifier.fire(reminder.id))
        val manager = context.getSystemService(NotificationManager::class.java)
        // The notification service posts asynchronously; give it a moment.
        var shown = manager.activeNotifications.firstOrNull { it.id == reminder.localNotificationId }
        repeat(50) {
            if (shown == null) {
                Thread.sleep(100)
                shown = manager.activeNotifications.firstOrNull { it.id == reminder.localNotificationId }
            }
        }
        assertNotNull("The reminder notification was not shown", shown)
        shown!!
        assertEquals(ReminderNotifier.CHANNEL_ID, shown.notification.channelId)
        assertNotNull(shown.notification.contentIntent)
        assertNotNull(db.agendaDao().findReminder(reminder.id)!!.firedAt)
        assertEquals(ReminderOutcome.NOT_DUE, notifier.fire(reminder.id))
        manager.cancel(reminder.localNotificationId)

        val cancelled = ok(activities.create(planned(null, ReminderRequest(ReminderKind.SAME_DAY))))
        ok(activities.cancel(cancelled))
        val silent = db.agendaDao().listForOwner("ACTIVITY", cancelled).single()
        assertEquals(ReminderOutcome.NOT_DUE, notifier.fire(silent.id))
    }

    @Test
    fun theAlarmServiceRegistersAndCancelsAReminder() {
        val scheduler = AndroidReminderScheduler(context)
        val code = UUID.randomUUID().hashCode()
        scheduler.schedule(ScheduledReminder(UUID.randomUUID(), UUID.randomUUID(), code, Instant.now().plusSeconds(86_400)))
        assertTrue(scheduler.isScheduled(code))
        scheduler.cancel(code)
        assertFalse(scheduler.isScheduled(code))
    }

    @Test
    fun theReceiversAreDeclaredForAlarmsAndReboots() {
        val packageManager = context.packageManager
        val alarm = Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE)
        assertEquals(1, packageManager.queryBroadcastReceivers(alarm, 0).size)
        val boot = Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName)
        assertTrue(
            packageManager.queryBroadcastReceivers(boot, 0)
                .any { it.activityInfo.name == ReminderRescheduleReceiver::class.java.name },
        )
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun planned(
        planning: ActivityPlanning?,
        vararg reminders: ReminderRequest,
        type: ActivityType = ActivityType.PRUNING,
    ) = NewActivity(farmId, null, type, day, "Poda", setOf(parcelId), planning = planning, reminders = reminders.toList())

    private fun changes(date: LocalDate, planning: ActivityPlanning?, vararg reminders: ReminderRequest) =
        ActivityChanges(ActivityType.PRUNING, date, "Poda", setOf(parcelId), planning = planning, reminders = reminders.toList())

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        coordinator = ReminderCoordinator(db, alarms, FixedClock(now))
        activities = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers, coordinator) { madrid }
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelId, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelId, now, metadata = meta),
        )
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
    }

    /** Records what would be registered with the alarm service, keyed by reminder. */
    private class FakeScheduler : ReminderScheduler {
        val active = mutableMapOf<UUID, ScheduledReminder>()
        val cancelled = mutableSetOf<Int>()

        override fun schedule(reminder: ScheduledReminder) {
            active[reminder.reminderId] = reminder
        }

        override fun cancel(requestCode: Int) {
            val removed = active.values.filter { it.requestCode == requestCode }
            if (removed.isNotEmpty()) cancelled += requestCode
            removed.forEach { active.remove(it.reminderId) }
        }
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "agenda-reminder-contract-test.db"
    }
}
