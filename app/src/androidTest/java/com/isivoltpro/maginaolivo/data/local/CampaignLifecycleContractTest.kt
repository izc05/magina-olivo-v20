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
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
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
 * Gate 6 — Campaign lifecycle contract.
 *
 * Granular, independently named proof of every rule stated in
 * docs/07-plans/PHASE6-CAMPAIGNS.md ("Global constraints" and "Review focus")
 * and docs/00-master/MASTER-SPEC-RC1.md section 8.
 *
 * OfflineFirstCampaignRepositoryTest keeps the original end-to-end activation
 * and restart proof; this suite isolates each contract clause so a regression
 * names the exact broken rule instead of failing one aggregate test.
 */
@RunWith(AndroidJUnit4::class)
class CampaignLifecycleContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000a1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000a1")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-0000000000a1")
    private val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000b2")
    private val otherParcelId = UUID.fromString("30000000-0000-0000-0000-0000000000b2")
    private val start = LocalDate.parse("2026-10-01")
    private val now = Instant.parse("2026-09-21T09:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: CampaignRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        repository = OfflineFirstCampaignRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    // ---------------------------------------------------------------- outbox

    @Test
    fun createEnqueuesExactlyOneDeterministicCreateIntent() = runBlocking {
        val id = created("2026/27", setOf(parcelId))

        val intents = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id)
        assertEquals(1, intents.size)
        assertEquals(OutboxOperation.CREATE, intents.single().operation)
        assertEquals(CampaignStatus.PREPARATION, db.campaignDao().findById(id)?.status)
    }

    @Test
    fun repeatedMutationsNeverDuplicateTheCampaignOutboxIntent() = runBlocking {
        val id = created("2026/27", setOf(parcelId))

        repeat(3) { index ->
            assertOk(
                repository.updatePreparation(
                    id,
                    CampaignPreparationChanges("Campaña $index", start, setOf(parcelId)),
                ),
            )
        }
        assertOk(repository.activate(id))
        assertOk(repository.markHarvest(id))
        assertOk(repository.close(id, LocalDate.parse("2027-02-01")))

        // One aggregate, one pending intent; snapshots never synchronize on their own.
        val intents = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id)
        assertEquals(1, intents.size)
        assertEquals(OutboxOperation.CREATE, intents.single().operation)
        // Snapshots are aggregate children: they never queue an intent of their own.
        assertEquals(1, db.campaignDao().listSnapshots(id).size)
    }

    // ------------------------------------------------------------ activation

    @Test
    fun activationWithoutParcelsFailsWithoutMutatingStatusOrOutbox() = runBlocking {
        val id = created("Sin parcelas", emptySet())
        val versionBefore = db.campaignDao().findById(id)!!.metadata.version

        val result = repository.activate(id)

        assertValidation("parcelIds", result)
        val stored = db.campaignDao().findById(id)!!
        assertEquals(CampaignStatus.PREPARATION, stored.status)
        assertEquals(versionBefore, stored.metadata.version)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id).size)
        assertEquals(
            OutboxOperation.CREATE,
            db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id).single().operation,
        )
    }

    @Test
    fun secondCurrentCampaignForTheSameFarmIsRejectedAtomically() = runBlocking {
        val first = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(first))
        val second = created("2027/28", setOf(parcelId))
        val versionBefore = db.campaignDao().findById(second)!!.metadata.version

        assertConflict("active_campaign_exists", repository.activate(second))

        val stored = db.campaignDao().findById(second)!!
        assertEquals(CampaignStatus.PREPARATION, stored.status)
        assertEquals(versionBefore, stored.metadata.version)
        assertEquals(CampaignStatus.ACTIVE, db.campaignDao().findById(first)?.status)

        // A campaign already in HARVEST still occupies the single current slot.
        assertOk(repository.markHarvest(first))
        assertConflict("active_campaign_exists", repository.activate(second))
        assertEquals(CampaignStatus.PREPARATION, db.campaignDao().findById(second)?.status)
    }

    @Test
    fun parcelOutsideTheFarmIsRejectedWithoutPersistingTheCampaign() = runBlocking {
        val before = repository.observeForFarm(farmId).first().size

        val result = repository.create(NewCampaign(farmId, "Parcela ajena", start, setOf(otherParcelId)))

        assertValidation("parcelIds", result)
        assertEquals(before, repository.observeForFarm(farmId).first().size)
    }

    // ------------------------------------------------------------ transitions

    @Test
    fun illegalTransitionsLeaveThePersistedStateUntouched() = runBlocking {
        val id = created("2026/27", setOf(parcelId))

        // PREPARATION rejects harvest, close and reopen.
        assertIllegal(id, CampaignStatus.PREPARATION) { repository.markHarvest(id) }
        assertIllegal(id, CampaignStatus.PREPARATION) { repository.close(id, LocalDate.parse("2027-02-01")) }
        assertIllegal(id, CampaignStatus.PREPARATION) { repository.reopen(id) }

        // ACTIVE rejects a second activation, a reopen and a direct close:
        // the canonical lifecycle forces ACTIVE to pass through HARVEST.
        assertOk(repository.activate(id))
        assertIllegal(id, CampaignStatus.ACTIVE) { repository.activate(id) }
        assertIllegal(id, CampaignStatus.ACTIVE) { repository.reopen(id) }
        assertIllegal(id, CampaignStatus.ACTIVE) { repository.close(id, LocalDate.parse("2027-02-01")) }

        // HARVEST rejects activation and a repeated harvest transition.
        assertOk(repository.markHarvest(id))
        assertIllegal(id, CampaignStatus.HARVEST) { repository.activate(id) }
        assertIllegal(id, CampaignStatus.HARVEST) { repository.markHarvest(id) }

        // CLOSED rejects every forward transition; only reopen is legal.
        assertOk(repository.close(id, LocalDate.parse("2027-02-01")))
        assertIllegal(id, CampaignStatus.CLOSED) { repository.activate(id) }
        assertIllegal(id, CampaignStatus.CLOSED) { repository.markHarvest(id) }
        assertIllegal(id, CampaignStatus.CLOSED) { repository.close(id, LocalDate.parse("2027-03-01")) }
    }

    @Test
    fun lifecycleIsStrictlyLinearActiveCannotCloseHarvestCanAndClosedCanReopen() = runBlocking {
        val id = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(id))

        // ACTIVE -> CLOSED is rejected.
        assertIllegal(id, CampaignStatus.ACTIVE) { repository.close(id, LocalDate.parse("2027-02-01")) }

        // ACTIVE -> HARVEST -> CLOSED is the only legal way to close.
        assertOk(repository.markHarvest(id))
        assertOk(repository.close(id, LocalDate.parse("2027-02-01")))
        assertEquals(CampaignStatus.CLOSED, db.campaignDao().findById(id)?.status)

        // CLOSED -> HARVEST is legal through the explicit reopen action.
        assertOk(repository.reopen(id))
        assertEquals(CampaignStatus.HARVEST, db.campaignDao().findById(id)?.status)
        assertNull(db.campaignDao().findById(id)?.endDate)
    }

    @Test
    fun endDateBeforeStartDateIsRejectedWithoutClosingTheCampaign() = runBlocking {
        val id = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(id))
        assertOk(repository.markHarvest(id))
        val versionBefore = db.campaignDao().findById(id)!!.metadata.version

        val result = repository.close(id, start.minusDays(1))

        assertValidation("endDate", result)
        val stored = db.campaignDao().findById(id)!!
        assertEquals(CampaignStatus.HARVEST, stored.status)
        assertNull(stored.endDate)
        assertEquals(versionBefore, stored.metadata.version)
    }

    @Test
    fun closeThenReopenIsExplicitAuditedAndClearsTheEndDate() = runBlocking {
        val id = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(id))
        assertOk(repository.markHarvest(id))
        val endDate = LocalDate.parse("2027-02-01")
        assertOk(repository.close(id, endDate))

        val closed = db.campaignDao().findById(id)!!
        assertEquals(CampaignStatus.CLOSED, closed.status)
        assertEquals(endDate, closed.endDate)

        assertOk(repository.reopen(id))

        val reopened = db.campaignDao().findById(id)!!
        assertEquals(CampaignStatus.HARVEST, reopened.status)
        assertNull(reopened.endDate)
        // Reopening is audited: the aggregate version advances and stays pending.
        assertTrue(reopened.metadata.version > closed.metadata.version)
        assertTrue(reopened.metadata.deletedAt == null)

        // Snapshots frozen at activation survive the reopen untouched.
        assertEquals(1, db.campaignDao().listSnapshots(id).size)

        // The campaign can be closed again.
        assertOk(repository.close(id, LocalDate.parse("2027-03-01")))
        assertEquals(CampaignStatus.CLOSED, db.campaignDao().findById(id)?.status)
    }

    @Test
    fun reopenIsRejectedWhileAnotherCurrentCampaignHoldsTheFarmSlot() = runBlocking {
        val first = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(first))
        assertOk(repository.markHarvest(first))
        assertOk(repository.close(first, LocalDate.parse("2027-02-01")))

        val second = created("2027/28", setOf(parcelId))
        assertOk(repository.activate(second))

        assertConflict("active_campaign_exists", repository.reopen(first))
        assertEquals(CampaignStatus.CLOSED, db.campaignDao().findById(first)?.status)
    }

    // --------------------------------------------------------------- archival

    @Test
    fun archiveIsRejectedForActiveHarvestAndClosedCampaigns() = runBlocking {
        val id = created("2026/27", setOf(parcelId))

        assertOk(repository.activate(id))
        assertConflict("protected_campaign", repository.archivePreparation(id))
        assertNull(db.campaignDao().findById(id)?.metadata?.deletedAt)

        assertOk(repository.markHarvest(id))
        assertConflict("protected_campaign", repository.archivePreparation(id))
        assertNull(db.campaignDao().findById(id)?.metadata?.deletedAt)

        assertOk(repository.close(id, LocalDate.parse("2027-02-01")))
        assertConflict("protected_campaign", repository.archivePreparation(id))
        assertNull(db.campaignDao().findById(id)?.metadata?.deletedAt)
        assertEquals(CampaignStatus.CLOSED, db.campaignDao().findById(id)?.status)
    }

    @Test
    fun archivedPreparationIsRemovedFromTheFarmAndCannotBeResurrected() = runBlocking {
        val id = created("Borrador", setOf(parcelId))
        assertOk(repository.archivePreparation(id))

        val archived = db.campaignDao().findById(id)!!
        assertTrue(archived.metadata.deletedAt != null)
        assertTrue(repository.observeForFarm(farmId).first().none { it.id == id })
        assertNull(repository.observe(id).first())

        // A soft-deleted aggregate is not an editable aggregate: no mutation may
        // resurrect it, and activating it must not consume the farm's current slot.
        assertConflict("archived_campaign", repository.activate(id))
        assertConflict(
            "archived_campaign",
            repository.updatePreparation(id, CampaignPreparationChanges("Resucitada", start, setOf(parcelId))),
        )
        assertConflict("archived_campaign", repository.markHarvest(id))
        assertConflict("archived_campaign", repository.close(id, LocalDate.parse("2027-02-01")))
        assertConflict("archived_campaign", repository.reopen(id))

        val stillArchived = db.campaignDao().findById(id)!!
        assertEquals(CampaignStatus.PREPARATION, stillArchived.status)
        assertEquals("Borrador", stillArchived.name)
        assertTrue(stillArchived.metadata.deletedAt != null)

        // Archiving twice is idempotent, mirroring the Farm aggregate contract.
        assertOk(repository.archivePreparation(id))
    }

    // -------------------------------------------------------------- snapshots

    @Test
    fun historicalSnapshotsSurviveLaterFarmAndParcelRenames() = runBlocking {
        val id = created("2026/27", setOf(parcelId))
        assertOk(repository.activate(id))
        assertOk(repository.markHarvest(id))
        assertOk(repository.close(id, LocalDate.parse("2027-02-01")))

        db.farmDao().upsert(db.farmDao().findById(farmId)!!.copy(name = "Finca renombrada"))
        db.parcelDao().upsert(
            db.parcelDao().findById(parcelId)!!.copy(
                displayName = "Parcela renombrada",
                managedAreaM2 = 9999.0,
                cadastralReference = "CAMBIADA",
            ),
        )

        val snapshot = repository.observe(id).first()!!.snapshots.single()
        assertEquals("Finca inicial", snapshot.farmName)
        assertEquals("Parcela inicial", snapshot.parcelName)
        assertEquals(1200.0, snapshot.managedAreaM2!!, 0.001)
        assertEquals(parcelId, snapshot.parcelId)
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun created(name: String, parcelIds: Set<UUID>): UUID {
        val result = repository.create(NewCampaign(farmId, name, start, parcelIds))
        assertTrue("create($name) failed: $result", result is AppResult.Success)
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
        expected: CampaignStatus,
        block: suspend () -> AppResult<Unit>,
    ) {
        val before = db.campaignDao().findById(id)!!
        assertConflict("illegal_campaign_transition", block())
        val after = db.campaignDao().findById(id)!!
        assertEquals(expected, after.status)
        assertEquals(before.metadata.version, after.metadata.version)
        assertEquals(before.endDate, after.endDate)
        assertEquals(before.name, after.name)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca inicial", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(parcelId, workspaceId, "Parcela inicial", source = "MANUAL", managedAreaM2 = 1200.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelId, now, metadata = meta),
        )

        db.farmDao().upsert(FarmEntity(otherFarmId, workspaceId, "Finca vecina", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(otherParcelId, workspaceId, "Parcela vecina", source = "MANUAL", managedAreaM2 = 800.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, otherFarmId, otherParcelId, now, metadata = meta),
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
        private const val DB = "campaign-lifecycle-contract-test.db"
    }
}
