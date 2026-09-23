package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstMachineRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineDraft
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
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
 * Phase 15 — Machinery.
 *
 * Gate 15: machinery adds value without making activities mandatory/complex.
 */
@RunWith(AndroidJUnit4::class)
class MachineryContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000e1")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-0000000000e1")
    private val now = Instant.parse("2026-12-02T08:00:00Z")
    private val day = LocalDate.parse("2026-11-20")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var machines: OfflineFirstMachineRepository
    private lateinit var activities: OfflineFirstActivityRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    // ------------------------------------------------------------ never mandatory

    @Test
    fun anActivityNeedsNoMachineAndNoHours() = runBlocking {
        val id = ok(activities.create(activity()))
        assertTrue(activities.observe(id).first()!!.machines.isEmpty())

        val tractor = ok(machines.create(MachineDraft("Tractor")))
        val withMachine = ok(activities.create(activity(MachineUseInput(tractor))))
        val used = activities.observe(withMachine).first()!!.machines.single()
        assertEquals("Tractor", used.name)
        assertEquals(null, used.hoursUsed)
    }

    @Test
    fun aMachineNeedsOnlyAName() = runBlocking {
        val id = ok(machines.create(MachineDraft("Atomizador")))
        val machine = machines.observe(id).first()!!
        assertEquals(MachineCategory.OTHER, machine.category)
        assertEquals(null, machine.currentHours)
        assertValidation("name", machines.create(MachineDraft("  ")))
        assertEquals(AppError.Conflict("duplicate_machine"), (machines.create(MachineDraft("atomizador")) as AppResult.Failure).error)
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.MACHINE, id).map { it.operation },
        )
    }

    // ------------------------------------------------------------ part of the Activity aggregate

    @Test
    fun machinesAreChildrenOfTheActivityWithOneIntent() = runBlocking {
        val tractor = ok(machines.create(MachineDraft("Tractor", MachineCategory.TRACTOR)))
        val mower = ok(machines.create(MachineDraft("Desbrozadora", MachineCategory.MOWER)))
        val id = ok(activities.create(activity(MachineUseInput(tractor, usageHours = 3.5), MachineUseInput(mower))))
        val tractorVersion = machines.observe(tractor).first()!!.version

        ok(activities.update(id, changes(MachineUseInput(mower, startHours = 100.0, endHours = 102.5))))

        val activity = activities.observe(id).first()!!
        assertEquals(2L, activity.version)
        assertEquals(listOf("Desbrozadora"), activity.machines.map { it.name })
        assertEquals(2.5, activity.machines.single().hoursUsed!!, 0.0)
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id).map { it.operation },
        )
        // Naming a machine on an Activity never touches the machine itself.
        assertEquals(tractorVersion, machines.observe(tractor).first()!!.version)
    }

    @Test
    fun inconsistentHoursAreRefusedWithoutWritingTheActivity() = runBlocking {
        val tractor = ok(machines.create(MachineDraft("Tractor")))
        assertValidation("machines", activities.create(activity(MachineUseInput(tractor, startHours = 1200.0, endHours = 1100.0))))
        assertValidation("machines", activities.create(activity(MachineUseInput(tractor, startHours = 1200.0, endHours = 1203.0, usageHours = 1.0))))
        assertValidation("machines", activities.create(activity(MachineUseInput(UUID.randomUUID()))))
        assertEquals(0, activities.observeForFarm(farmId).first().size)
    }

    // ------------------------------------------------------------ history stays true

    @Test
    fun aRetiredMachineStaysOnPastActivitiesButCannotBeChosenAgain() = runBlocking {
        val tractor = ok(machines.create(MachineDraft("Tractor viejo")))
        val id = ok(activities.create(activity(MachineUseInput(tractor, usageHours = 2.0))))
        ok(machines.archive(tractor))

        assertTrue(activities.observeSelectableMachines().first().none { it.id == tractor })
        assertTrue(machines.observeActive().first().none { it.id == tractor })
        assertEquals(listOf(tractor), machines.observeArchived().first().map { it.id })
        val kept = activities.observe(id).first()!!.machines.single()
        assertEquals("Tractor viejo", kept.name)
        assertTrue(kept.archived)
        // Editing the old Activity keeps it; a new Activity cannot pick it.
        ok(activities.update(id, changes(MachineUseInput(tractor, usageHours = 2.5))))
        assertValidation("machines", activities.create(activity(MachineUseInput(tractor))))

        ok(machines.restore(tractor))
        val again = ok(activities.create(activity(MachineUseInput(tractor))))
        assertEquals(false, activities.observe(again).first()!!.machines.single().archived)
    }

    @Test
    fun aMachineShowsTheActivitiesThatUsedItAndOnlyRecordedHours() = runBlocking {
        val tractor = ok(machines.create(MachineDraft("Tractor")))
        ok(activities.create(activity(MachineUseInput(tractor, usageHours = 3.0))))
        ok(activities.create(activity(MachineUseInput(tractor, startHours = 10.0, endHours = 11.5))))
        ok(activities.create(activity(MachineUseInput(tractor))))

        val uses = machines.observeUses(tractor).first()
        assertEquals(3, uses.size)
        assertEquals(4.5, uses.mapNotNull { it.hoursUsed }.sum(), 0.0)
        assertEquals(1, uses.count { it.hoursUsed == null })
    }

    @Test
    fun machinesAndTheirUseSurviveARestart() = runBlocking {
        val tractor = ok(machines.create(MachineDraft("Tractor", MachineCategory.TRACTOR, currentHours = 1250.5)))
        val id = ok(activities.create(activity(MachineUseInput(tractor, usageHours = 3.0))))
        db.close()
        open()
        assertEquals(1250.5, machines.observe(tractor).first()!!.currentHours!!, 0.0)
        assertEquals(3.0, activities.observe(id).first()!!.machines.single().hoursUsed!!, 0.0)
    }

    private fun activity(vararg uses: MachineUseInput) =
        NewActivity(farmId, null, ActivityType.SOIL_WORK, day, "Desbroce", setOf(parcelId), machines = uses.toList())

    private fun changes(vararg uses: MachineUseInput) =
        ActivityChanges(ActivityType.SOIL_WORK, day, "Desbroce", setOf(parcelId), machines = uses.toList())

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        machines = OfflineFirstMachineRepository(db, workspaces, FixedClock(now), RandomIds, TestDispatchers)
        activities = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
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
        const val DB = "machinery-contract-test.db"
    }
}
