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
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 9 — Activity engine contract.
 *
 * Proves the rules that make one canonical Activity able to target many Parcels
 * without duplicating the header, and the offline-first aggregate guarantees the
 * engine inherits from the Farm/Parcel/Campaign slices.
 */
@RunWith(AndroidJUnit4::class)
class ActivityEngineContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000c1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000c1")
    private val parcelA = UUID.fromString("30000000-0000-0000-0000-0000000000c1")
    private val parcelB = UUID.fromString("30000000-0000-0000-0000-0000000000c2")
    private val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000d1")
    private val otherParcel = UUID.fromString("30000000-0000-0000-0000-0000000000d1")
    private val date = LocalDate.parse("2026-02-10")
    private val now = Instant.parse("2026-09-22T08:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: ActivityRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        repository = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    // ------------------------------------------------- multi-parcel targeting

    @Test
    fun oneActivityTargetsManyParcelsWithoutDuplicatingTheHeader() = runBlocking {
        val id = created("Poda de invierno", setOf(parcelA, parcelB))

        // Exactly one canonical Activity, two targets.
        assertEquals(1, repository.observeForFarm(farmId).first().size)
        assertEquals(2, db.activityDao().countTargets(id))

        val activity = repository.observe(id).first()!!
        assertEquals(2, activity.targets.size)
        assertEquals(setOf(parcelA, parcelB), activity.targets.map { it.parcelId }.toSet())

        // The same canonical Activity is visible from either Parcel.
        assertEquals(listOf(id), repository.observeForParcel(parcelA).first().map { it.id })
        assertEquals(listOf(id), repository.observeForParcel(parcelB).first().map { it.id })
    }

    @Test
    fun repeatedTargetingNeverDuplicatesAParcelRow() = runBlocking {
        val id = created("Abonado", setOf(parcelA, parcelB))
        assertOk(
            repository.update(
                id,
                ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado", setOf(parcelA, parcelA, parcelB)),
            ),
        )
        assertEquals(2, db.activityDao().countTargets(id))
    }

    @Test
    fun parcelOutsideTheFarmIsRejectedWithoutPersistingTheActivity() = runBlocking {
        val before = repository.observeForFarm(farmId).first().size

        val result = repository.create(
            NewActivity(farmId, null, ActivityType.OTHER, date, "Parcela ajena", setOf(otherParcel)),
        )

        assertValidation("parcelIds", result)
        assertEquals(before, repository.observeForFarm(farmId).first().size)
    }

    // ---------------------------------------------------------------- outbox

    @Test
    fun createEnqueuesExactlyOneDeterministicCreateIntent() = runBlocking {
        val id = created("Observación", setOf(parcelA))
        val intents = db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id)
        assertEquals(1, intents.size)
        assertEquals(OutboxOperation.CREATE, intents.single().operation)
    }

    @Test
    fun repeatedMutationsNeverDuplicateTheActivityOutboxIntent() = runBlocking {
        val id = created("Tratamiento", setOf(parcelA))
        repeat(3) { index ->
            assertOk(
                repository.update(
                    id,
                    ActivityChanges(ActivityType.PHYTOSANITARY, date, "Tratamiento $index", setOf(parcelA, parcelB)),
                ),
            )
        }
        assertOk(repository.complete(id))

        val intents = db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id)
        assertEquals(1, intents.size)
        assertEquals(OutboxOperation.CREATE, intents.single().operation)
        // Targets are aggregate children: they never queue an intent of their own.
        assertEquals(2, db.activityDao().countTargets(id))
    }

    // ------------------------------------------------------------- lifecycle

    @Test
    fun plannedActivityRequiresAtLeastOneParcel() = runBlocking {
        val result = repository.create(
            NewActivity(farmId, null, ActivityType.PRUNING, date, "Sin parcelas", emptySet()),
        )
        assertValidation("parcelIds", result)
    }

    @Test
    fun aDraftMayBeSavedEmptyAndResumedIntoPlanned() = runBlocking {
        val id = (repository.create(
            NewActivity(farmId, null, ActivityType.PRUNING, date, "Borrador", emptySet(), asDraft = true),
        ) as AppResult.Success).value
        assertEquals(ActivityStatus.DRAFT, db.activityDao().findById(id)?.status)

        // A draft with no target cannot be promoted yet.
        assertValidation("parcelIds", repository.plan(id))
        assertEquals(ActivityStatus.DRAFT, db.activityDao().findById(id)?.status)

        assertOk(repository.update(id, ActivityChanges(ActivityType.PRUNING, date, "Borrador", setOf(parcelA))))
        assertOk(repository.plan(id))
        assertEquals(ActivityStatus.PLANNED, db.activityDao().findById(id)?.status)
    }

    @Test
    fun illegalTransitionsLeaveThePersistedStateUntouched() = runBlocking {
        val id = created("Riego", setOf(parcelA))

        // PLANNED rejects plan() and reopen().
        assertIllegal(id, ActivityStatus.PLANNED) { repository.plan(id) }
        assertIllegal(id, ActivityStatus.PLANNED) { repository.reopen(id) }

        assertOk(repository.complete(id))
        assertIllegal(id, ActivityStatus.COMPLETED) { repository.complete(id) }
        assertIllegal(id, ActivityStatus.COMPLETED) { repository.cancel(id) }
        assertIllegal(id, ActivityStatus.COMPLETED) { repository.plan(id) }
    }

    @Test
    fun completedActivityIsProtectedFromEditsUntilItIsReopened() = runBlocking {
        val id = created("Desbroce", setOf(parcelA))
        assertOk(repository.complete(id))

        assertConflict(
            "protected_activity",
            repository.update(id, ActivityChanges(ActivityType.OTHER, date, "Mutada", setOf(parcelA))),
        )
        assertEquals("Desbroce", db.activityDao().findById(id)?.description)

        assertOk(repository.reopen(id))
        assertEquals(ActivityStatus.PLANNED, db.activityDao().findById(id)?.status)
        assertOk(repository.update(id, ActivityChanges(ActivityType.OTHER, date, "Editada", setOf(parcelA))))
        assertEquals("Editada", db.activityDao().findById(id)?.description)
    }

    @Test
    fun archiveIsProtectedForPlannedAndCompletedAndCannotResurrect() = runBlocking {
        val id = created("Incidencia", setOf(parcelA))
        assertConflict("protected_activity", repository.archive(id))
        assertNull(db.activityDao().findById(id)?.metadata?.deletedAt)

        assertOk(repository.complete(id))
        assertConflict("protected_activity", repository.archive(id))

        assertOk(repository.reopen(id))
        assertOk(repository.cancel(id))
        assertOk(repository.archive(id))
        assertTrue(db.activityDao().findById(id)?.metadata?.deletedAt != null)

        // A soft-deleted aggregate is not an editable aggregate.
        assertNull(repository.observe(id).first())
        assertTrue(repository.observeForFarm(farmId).first().none { it.id == id })
        assertConflict("archived_activity", repository.reopen(id))
        assertConflict(
            "archived_activity",
            repository.update(id, ActivityChanges(ActivityType.OTHER, date, "Resucitada", setOf(parcelA))),
        )
        // Archiving twice is idempotent.
        assertOk(repository.archive(id))
    }

    // --------------------------------------------------------------- restart

    @Test
    fun activityAndTargetsSurviveAProcessRestart() = runBlocking {
        val id = created("Poda", setOf(parcelA, parcelB))
        assertOk(repository.complete(id))
        db.close()

        db = MaginaOlivoDatabase.create(context, DB)
        val restored = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
            .observe(id).first()!!
        assertEquals(ActivityStatus.COMPLETED, restored.status)
        assertEquals(2, restored.targets.size)
        assertEquals("Poda", restored.description)
    }

    // --------------------------------------------------------------- helpers

    private suspend fun created(description: String, parcelIds: Set<UUID>): UUID {
        val result = repository.create(
            NewActivity(farmId, null, ActivityType.PRUNING, date, description, parcelIds),
        )
        assertTrue("create($description) failed: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }

    private fun assertOk(result: AppResult<Unit>) = assertEquals(AppResult.Success(Unit), result)

    private fun assertValidation(field: String, result: AppResult<*>) {
        assertTrue("expected validation failure, got $result", result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue("expected AppError.Validation, got $error", error is AppError.Validation)
        assertEquals(field, (error as AppError.Validation).field)
    }

    private fun assertConflict(code: String, result: AppResult<*>) {
        assertTrue("expected conflict '$code', got $result", result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue("expected AppError.Conflict, got $error", error is AppError.Conflict)
        assertEquals(code, (error as AppError.Conflict).resource)
    }

    private suspend fun assertIllegal(
        id: UUID,
        expected: ActivityStatus,
        block: suspend () -> AppResult<Unit>,
    ) {
        val before = db.activityDao().findById(id)!!
        assertConflict("illegal_activity_transition", block())
        val after = db.activityDao().findById(id)!!
        assertEquals(expected, after.status)
        assertEquals(before.metadata.version, after.metadata.version)
        assertEquals(before.description, after.description)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca principal", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(parcelA, workspaceId, "Parcela A", source = "MANUAL", managedAreaM2 = 1200.0, metadata = meta),
        )
        db.parcelDao().upsert(
            ParcelEntity(parcelB, workspaceId, "Parcela B", source = "MANUAL", managedAreaM2 = 800.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelA, now, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelB, now, metadata = meta),
        )

        db.farmDao().upsert(FarmEntity(otherFarmId, workspaceId, "Finca vecina", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(otherParcel, workspaceId, "Parcela vecina", source = "MANUAL", managedAreaM2 = 500.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, otherFarmId, otherParcel, now, metadata = meta),
        )
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

    companion object {
        private const val DB = "activity-engine-contract-test.db"
    }
}
