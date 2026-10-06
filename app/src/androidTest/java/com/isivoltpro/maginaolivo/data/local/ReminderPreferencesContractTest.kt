package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.reminder.ReminderCoordinator
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.data.reminder.ReminderOutcome
import com.isivoltpro.maginaolivo.data.reminder.ReminderScheduler
import com.isivoltpro.maginaolivo.data.reminder.ScheduledReminder
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstProfileRepository
import com.isivoltpro.maginaolivo.data.repository.RoomReminderPreferences
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import com.isivoltpro.maginaolivo.domain.profile.ProfileDraft
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 21B — Perfil → Avisos.
 *
 * Gate 21 (part): the reminder preferences persist offline, the day-before reminder rings at the
 * chosen hour (08:00 by default, owner decision P2), and switching reminders off withdraws every
 * alarm while keeping every reminder.
 */
@RunWith(AndroidJUnit4::class)
class ReminderPreferencesContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000021b1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000021b1")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-0000000021b1")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val day = LocalDate.parse("2026-11-20")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var alarms: RecordingScheduler
    private lateinit var coordinator: ReminderCoordinator
    private lateinit var activities: OfflineFirstActivityRepository
    private lateinit var profile: OfflineFirstProfileRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        alarms = RecordingScheduler()
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun withoutAChoiceRemindersAreOnAndTheDayBeforeRingsAt0800() = runBlocking {
        assertEquals(ReminderPreferences(enabled = true, previousDayTime = LocalTime.of(8, 0)), RoomReminderPreferences(db).current())
        assertEquals(ReminderPreferences(), profile.observe().first().reminders)

        val id = ok(activities.create(planned(ReminderRequest(ReminderKind.PREVIOUS_DAY))))
        val reminder = db.agendaDao().listForOwner("ACTIVITY", id).single()
        // 19 November 2026, 08:00 in Madrid (UTC+1).
        assertEquals(Instant.parse("2026-11-19T07:00:00Z"), reminder.triggerAt)
        assertEquals(reminder.triggerAt, alarms.active.getValue(reminder.id).triggerAt)
    }

    @Test
    fun aNewDayBeforeHourMovesEveryDayBeforeReminderAndSurvivesARestart() = runBlocking {
        val id = ok(activities.create(planned(ReminderRequest(ReminderKind.PREVIOUS_DAY), ReminderRequest(ReminderKind.SAME_DAY))))
        ok(profile.saveReminders(ReminderPreferences(enabled = true, previousDayTime = LocalTime.of(20, 0))))
        coordinator.reconcile()

        db.close()
        open()
        coordinator.reconcile()

        val rows = db.agendaDao().listForOwner("ACTIVITY", id).associateBy { it.kind }
        assertEquals(Instant.parse("2026-11-19T19:00:00Z"), rows.getValue("PREVIOUS_DAY").triggerAt)
        // The same-day reminder does not depend on the day-before hour.
        assertEquals(Instant.parse("2026-11-20T06:00:00Z"), rows.getValue("SAME_DAY").triggerAt)
        assertEquals(LocalTime.of(20, 0), profile.observe().first().reminders.previousDayTime)
    }

    @Test
    fun switchingRemindersOffWithdrawsEveryAlarmAndKeepsEveryReminder() = runBlocking {
        val id = ok(activities.create(planned(ReminderRequest(ReminderKind.PREVIOUS_DAY), ReminderRequest(ReminderKind.SAME_DAY))))
        assertEquals(2, alarms.active.size)

        ok(profile.saveReminders(ReminderPreferences(enabled = false)))
        coordinator.reconcile()
        assertTrue(alarms.active.isEmpty())
        assertEquals(2, db.agendaDao().listForOwner("ACTIVITY", id).count { it.enabled })
        // Even an alarm already set on the device stays silent.
        val reminder = db.agendaDao().listForOwner("ACTIVITY", id).first()
        assertEquals(ReminderOutcome.NOT_DUE, ReminderNotifier(context, db, FixedClock(now)).fire(reminder.id))

        ok(profile.saveReminders(ReminderPreferences(enabled = true)))
        coordinator.reconcile()
        assertEquals(2, alarms.active.size)
    }

    @Test
    fun theLocationAndTheRemindersAreSavedWithoutUndoingEachOther() = runBlocking {
        ok(profile.saveReminders(ReminderPreferences(enabled = false, previousDayTime = LocalTime.of(7, 0))))
        ok(profile.save(ProfileDraft("Bedmar", "Jaén", null)))
        val afterLocation = profile.observe().first()
        assertEquals(ReminderPreferences(enabled = false, previousDayTime = LocalTime.of(7, 0)), afterLocation.reminders)

        ok(profile.saveReminders(ReminderPreferences(enabled = true, previousDayTime = LocalTime.of(9, 0))))
        assertEquals("Bedmar", profile.observe().first().municipality)
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM profile_settings").use { it.moveToFirst(); it.getInt(0) })
    }

    private fun planned(vararg reminders: ReminderRequest) =
        NewActivity(farmId, null, ActivityType.PRUNING, day, "Poda", setOf(parcelId), reminders = reminders.toList())

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val preferences = RoomReminderPreferences(db)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        coordinator = ReminderCoordinator(db, alarms, clock, preferences) { madrid }
        activities = OfflineFirstActivityRepository(db, clock, RandomIds, TestDispatchers, coordinator, preferences, zone = { madrid })
        val organizations = OfflineFirstOrganizationRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        profile = OfflineFirstProfileRepository(db, workspaces, organizations, clock, RandomIds, TestDispatchers)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelId, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelId, now, metadata = meta))
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    /** What would be registered with the alarm service, keyed by reminder. */
    private class RecordingScheduler : ReminderScheduler {
        val active = mutableMapOf<UUID, ScheduledReminder>()

        override fun schedule(reminder: ScheduledReminder) {
            active[reminder.reminderId] = reminder
        }

        override fun cancel(requestCode: Int) {
            active.values.removeAll { it.requestCode == requestCode }
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
        const val DB = "reminder-preferences-contract-test.db"
    }
}
