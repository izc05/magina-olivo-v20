package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocationMode
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 13 — Harvest.
 *
 * Gate 13: totals remain truthful and no parcel split is fabricated.
 */
@RunWith(AndroidJUnit4::class)
class HarvestContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000a1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000a1")
    private val idleFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000a2")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000000a1")
    private val idleCampaignId = UUID.fromString("40000000-0000-0000-0000-0000000000a2")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000000a1")
    private val south = UUID.fromString("30000000-0000-0000-0000-0000000000a2")
    private val east = UUID.fromString("30000000-0000-0000-0000-0000000000a3")
    private val outsider = UUID.fromString("30000000-0000-0000-0000-0000000000a4")
    private val now = Instant.parse("2026-11-20T08:00:00Z")
    private val day = LocalDate.parse("2026-11-18")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var harvests: OfflineFirstHarvestRepository

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

    // ------------------------------------------------------------ no fabricated split

    @Test
    fun anUnknownSplitStoresOnlyTheTotalAndNoParcelKilos() = runBlocking {
        val id = ok(harvests.create(draft(5_000_000, north to null, south to null)))

        val rows = db.harvestDao().listParcels(id)
        assertEquals(setOf(north, south), rows.map { it.parcelId }.toSet())
        assertTrue(rows.all { it.allocationMode == HarvestAllocation.UNALLOCATED.name && it.weightGrams == null })

        val harvest = harvests.observe(id).first()!!
        assertEquals(HarvestAllocationMode.UNALLOCATED, harvest.allocationMode)
        assertEquals(5_000_000L, harvest.unallocatedGrams)
        val summary = HarvestSummary.of(listOf(harvest))
        assertTrue(summary.parcels.all { it.exactGrams == 0L && it.sharesUnallocated })
    }

    @Test
    fun anExactSplitMustReconcileWithTheTotalToTheGram() = runBlocking {
        assertValidation("parcels", harvests.create(draft(5_000_000, north to 3_000_000, south to 1_999_999)))
        assertValidation("parcels", harvests.create(draft(5_000_000, north to 3_000_000, south to 2_000_001)))
        assertEquals(0, count("harvests"))
        assertEquals(0, count("harvest_parcels"))
        assertEquals(0, count("sync_outbox"))

        val id = ok(harvests.create(draft(5_000_000, north to 3_000_000, south to 2_000_000)))
        val harvest = harvests.observe(id).first()!!
        assertEquals(HarvestAllocationMode.EXACT, harvest.allocationMode)
        assertEquals(0L, harvest.unallocatedGrams)
        assertEquals(
            mapOf(north to 3_000_000L, south to 2_000_000L),
            harvest.shares.associate { it.parcelId to it.weightGrams },
        )
    }

    @Test
    fun aPartialSplitKeepsTheRestUnallocatedAndNeverAssignsIt() = runBlocking {
        val id = ok(harvests.create(draft(5_000_000, north to 3_000_000, south to null, east to null)))
        val harvest = harvests.observe(id).first()!!

        assertEquals(HarvestAllocationMode.PARTIAL, harvest.allocationMode)
        assertEquals(2_000_000L, harvest.unallocatedGrams)
        assertNull(harvest.shares.single { it.parcelId == south }.weightGrams)
        assertNull(harvest.shares.single { it.parcelId == east }.weightGrams)
        // Marking the others unknown while the known kilos already fill the total would be
        // an exact split in disguise ("they gave nothing").
        assertValidation("parcels", harvests.create(draft(5_000_000, north to 5_000_000, south to null)))
    }

    @Test
    fun campaignTotalsStayTruthfulAcrossHarvests() = runBlocking {
        ok(harvests.create(draft(5_000_000, north to 3_000_000, south to 2_000_000)))
        ok(harvests.create(draft(4_000_000, north to null, east to null)))
        ok(harvests.create(draft(1_500_000, east to 500_000, south to null)))

        val summary = HarvestSummary.of(harvests.observeForCampaign(campaignId).first())
        assertEquals(3, summary.harvestCount)
        assertEquals(10_500_000L, summary.totalGrams)
        assertEquals(5_000_000L, summary.unallocatedGrams)
        assertEquals(
            mapOf(east to 500_000L, north to 3_000_000L, south to 2_000_000L),
            summary.parcels.associate { it.parcelId to it.exactGrams },
        )
        assertEquals(summary.totalGrams, summary.parcels.sumOf { it.exactGrams } + summary.unallocatedGrams)
    }

    // ------------------------------------------------------------ one aggregate (D6)

    @Test
    fun aHarvestAndItsParcelsAreOneAggregateWithOneIntent() = runBlocking {
        val id = ok(harvests.create(draft(5_000_000, north to null, south to null)))
        ok(
            harvests.update(
                id,
                draft(5_200_000, north to 3_000_000, east to 2_200_000).copy(
                    collectionMethod = CollectionMethod.TRUNK_SHAKER,
                    workerCount = 4,
                    machineryText = "Vibrador y remolque",
                ),
            ),
        )

        val harvest = harvests.observe(id).first()!!
        assertEquals(2L, harvest.version)
        assertEquals(5_200_000L, harvest.totalGrams)
        assertEquals(setOf(north, east), harvest.shares.map { it.parcelId }.toSet())
        assertEquals(CollectionMethod.TRUNK_SHAKER, harvest.collectionMethod)
        assertEquals(4, harvest.workerCount)
        assertEquals(2, db.harvestDao().listParcels(id).size)
        // Created and edited offline: still a single CREATE for the whole aggregate.
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, id).map { it.operation },
        )
        assertEquals(1, count("sync_outbox"))
    }

    @Test
    fun originParcelsMustBelongToTheCampaign() = runBlocking {
        assertValidation("parcels", harvests.create(draft(1_000_000, north to null, outsider to null)))
        assertEquals(0, count("harvests"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun onlyFarmsWithARunningCampaignAreOfferedAndCanRecordHarvest() = runBlocking {
        val contexts = harvests.observeContexts().first()
        assertEquals(listOf(farmId), contexts.map { it.farmId })
        assertEquals(setOf(north, south, east), contexts.single().parcels.map { it.parcelId }.toSet())

        val result = harvests.create(draft(1_000_000, outsider to null).copy(farmId = idleFarmId))
        assertEquals(AppError.Conflict("no_running_campaign"), (result as AppResult.Failure).error)
    }

    @Test
    fun aFutureDateOrADateBeforeTheCampaignIsRefused() = runBlocking {
        assertValidation("harvestDate", harvests.create(draft(1_000_000, north to null).copy(harvestDate = LocalDate.parse("2026-11-21"))))
        assertValidation("harvestDate", harvests.create(draft(1_000_000, north to null).copy(harvestDate = LocalDate.parse("2026-09-30"))))
        assertEquals(0, count("harvests"))
    }

    // ------------------------------------------------------------ history

    @Test
    fun aClosedCampaignsHarvestIsHistoryAndCannotChange() = runBlocking {
        val id = ok(harvests.create(draft(5_000_000, north to null, south to null)))
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = LocalDate.parse("2026-11-19")))

        val update = harvests.update(id, draft(6_000_000, north to null, south to null))
        assertEquals(AppError.Conflict("closed_campaign"), (update as AppResult.Failure).error)
        val delete = harvests.delete(id)
        assertEquals(AppError.Conflict("closed_campaign"), (delete as AppResult.Failure).error)

        val harvest = harvests.observe(id).first()!!
        assertFalse(harvest.editable)
        assertEquals(5_000_000L, harvest.totalGrams)
        assertEquals(1L, harvest.version)
    }

    @Test
    fun deletingAHarvestRemovesItsKilosAndQueuesOneTombstone() = runBlocking {
        val kept = ok(harvests.create(draft(2_000_000, north to null)))
        val id = ok(harvests.create(draft(5_000_000, north to null, south to null)))

        ok(harvests.delete(id))
        ok(harvests.delete(id))

        assertNull(harvests.observe(id).first())
        assertEquals(listOf(kept), harvests.observeAll().first().map { it.id })
        assertEquals(2_000_000L, HarvestSummary.of(harvests.observeAll().first()).totalGrams)
        assertEquals(
            listOf(OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, id).map { it.operation },
        )
    }

    @Test
    fun aHarvestSurvivesARestart() = runBlocking {
        val id = ok(harvests.create(draft(5_000_000, north to 3_000_000, south to null)))
        db.close()
        open()
        val harvest = harvests.observe(id).first()!!
        assertEquals(5_000_000L, harvest.totalGrams)
        assertEquals(HarvestAllocationMode.PARTIAL, harvest.allocationMode)
        assertEquals(2_000_000L, harvest.unallocatedGrams)
    }

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        harvests = OfflineFirstHarvestRepository(db, FixedClock(now), RandomIds, TestDispatchers) { ZoneOffset.UTC }
    }

    private fun draft(total: Long?, vararg shares: Pair<UUID, Long?>) = HarvestDraft(
        farmId = farmId,
        harvestDate = day,
        totalGrams = total,
        shares = shares.map { HarvestShareInput(it.first, it.second) },
    )

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        db.farmDao().upsert(FarmEntity(idleFarmId, workspaceId, "El Cerro", metadata = meta))
        listOf(north to "Norte", south to "Sur", east to "Este", outsider to "Cerro").forEach { (id, name) ->
            db.parcelDao().upsert(ParcelEntity(id, workspaceId, name, source = "MANUAL", metadata = meta))
        }
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsert(
            CampaignEntity(idleCampaignId, workspaceId, idleFarmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.PREPARATION, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(north to "Norte", south to "Sur", east to "Este").map { (id, name) ->
                CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, id, farmId, "La Solana", name, metadata = meta)
            } + CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, idleCampaignId, outsider, idleFarmId, "El Cerro", "Cerro", metadata = meta),
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

    private companion object {
        const val DB = "harvest-contract-test.db"
    }
}
