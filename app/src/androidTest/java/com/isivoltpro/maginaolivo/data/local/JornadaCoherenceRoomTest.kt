package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
import com.isivoltpro.maginaolivo.domain.harvest.AutoDaySplit
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
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
 * #808 gate J (CR-010 amendment J) on Room, API 35: the AUTO_DAY split follows the server rule (EXACT sums, UNALLOCATED
 * when any share is not exact, a Pesada without Parcels adds kilos but no Parcel), a hand-recorded Jornada keeps its typed
 * kilos and split whatever its Pesadas do (no outbox for it), its form keeps the typed kilos, and the linked Pesadas and a
 * review hold are exposed apart. Every recomputation is counted against the outbox.
 */
@RunWith(AndroidJUnit4::class)
class JornadaCoherenceRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g808-j-${UUID.randomUUID()}"
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000008e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000008e1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000008e1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000008e1")
    private val south = UUID.fromString("30000000-0000-0000-0000-0000000008e2")
    private val now = Instant.parse("2026-12-02T08:00:00Z")
    private val day = LocalDate.parse("2026-11-18")
    private lateinit var db: MaginaOlivoDatabase

    @Before fun open() = runBlocking {
        db = MaginaOlivoDatabase.create(context, name)
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "La Solana", metadata = meta))
        for ((id, label) in listOf(north to "Norte", south to "Sur")) {
            db.parcelDao().upsert(ParcelEntity(id, workspaceId, label, source = "MANUAL", metadata = meta))
        }
        db.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null,
            CampaignStatus.HARVEST, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(
            CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, north, farmId, "La Solana", "Norte", metadata = meta),
            CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, south, farmId, "La Solana", "Sur", metadata = meta)))
    }

    @After fun close() { runCatching { db.close() }; context.deleteDatabase(name) }

    private val workspace = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
    }
    private val deliveries get() = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers, workspace) { ZoneOffset.UTC }
    private val harvests get() = OfflineFirstHarvestRepository(db, FixedClock(now), RandomIds, TestDispatchers, workspace) { ZoneOffset.UTC }

    private suspend fun pesada(net: Long, vararg shares: DeliveryShareInput): UUID =
        when (val r = deliveries.create(DeliveryDraft(farmId = farmId, deliveryDate = day, destinationOrganizationId = null,
            destinationName = "Cooperativa", netGrams = net, shares = shares.toList(), origin = PesadaOrigin.TREE))) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> error("create: ${r.error}")
        }

    private suspend fun dayOf(pesada: UUID) = db.deliveryDao().findById(pesada)!!.harvestId!!
    private suspend fun split(harvest: UUID) =
        db.harvestDao().listParcels(harvest).sortedBy { it.parcelId.toString() }.map { Triple(it.parcelId, it.allocationMode, it.weightGrams) }
    private fun outboxCount() = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun harvestOps(id: UUID) = db.syncOutboxDao().listForEntity(SyncEntityType.HARVEST, id)

    @Test fun anAutomaticDaySplitFollowsTheServerRuleAndNeverInventsKilos() = runBlocking<Unit> {
        val a = pesada(1_000_000, DeliveryShareInput(north, 600_000), DeliveryShareInput(south, 400_000))
        val auto = dayOf(a)
        assertEquals(AUTO_DAY, db.harvestDao().findById(auto)!!.dayOrigin)
        pesada(500_000, DeliveryShareInput(north, 500_000))
        assertEquals(listOf(Triple(north, "EXACT", 1_100_000L), Triple(south, "EXACT", 400_000L)).sortedBy { it.first.toString() }, split(auto))
        // A share without exact kilos makes its Parcel UNALLOCATED (no kilos); the other Parcel keeps its exact sum.
        val mixed = pesada(300_000, DeliveryShareInput(south, null))
        assertEquals(listOf(Triple(north, "EXACT", 1_100_000L), Triple(south, "UNALLOCATED", null)).sortedBy { it.first.toString() }, split(auto))
        // A legacy Pesada without Parcels (new ones are refused: parcels/empty) adds kilos to the total, never a Parcel and
        // never "the whole Farm". It is placed as an old row, then the day is recomputed by the next Pesada.
        val legacy = db.deliveryDao().findById(a)!!.copy(id = UUID.randomUUID(), netGrams = 200_000)
        db.deliveryDao().upsert(legacy)
        pesada(100_000, DeliveryShareInput(north, 100_000))
        assertTrue(db.deliveryDao().listParcels(legacy.id).isEmpty())
        assertEquals(2_100_000L, db.harvestDao().findById(auto)!!.weightGrams)
        assertEquals(setOf(north, south), split(auto).map { it.first }.toSet())
        // Attributed vs not attributed, as the screen shows them.
        val shown = harvests.observe(auto).first()!!
        assertEquals(1_200_000L, shown.allocatedGrams); assertEquals(900_000L, shown.unallocatedGrams)
        // The stored split is exactly the shared rule (the one R3-b verifies against derived_fingerprint).
        val shares = db.deliveryDao().listLiveForHarvest(auto).flatMap { db.deliveryDao().listParcels(it.id) }
            .map { AutoDaySplit.Share(it.parcelId, HarvestAllocation.valueOf(it.allocationMode), it.weightGrams) }
        assertEquals(AutoDaySplit.derive(shares).map { Triple(it.parcelId, it.allocation.name, it.weightGrams) }, split(auto))
        // Deleting the mixed Pesada brings the Parcel back to EXACT; kilos follow the live Pesadas.
        assertTrue(deliveries.delete(mixed) is AppResult.Success)
        assertEquals(Triple(south, "EXACT", 400_000L), split(auto).single { it.first == south })
        assertEquals(1_800_000L, db.harvestDao().findById(auto)!!.weightGrams)
    }

    @Test fun aHandRecordedJornadaKeepsItsTypedKilosAndSplitAndQueuesNothingWhateverItsPesadasDo() = runBlocking<Unit> {
        val manual = UUID.randomUUID()
        db.harvestDao().upsert(HarvestEntity(id = manual, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId, harvestDate = day,
            weightGrams = 4_242_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L), dayOrigin = "MANUAL"))
        db.harvestDao().upsertParcels(listOf(HarvestParcelEntity(UUID.randomUUID(), workspaceId, manual, north, null, "Norte", 4_242_000,
            "EXACT", LocalMetadata(now, now))))
        val p = pesada(900_000, DeliveryShareInput(north, 900_000))
        val q = pesada(100_000, DeliveryShareInput(north, 100_000))
        // Pre-CR-010 links: the Pesadas belong to the hand-recorded Jornada.
        for (id in listOf(p, q)) db.deliveryDao().findById(id)!!.let { db.deliveryDao().upsert(it.copy(harvestId = manual)) }
        val row = db.harvestDao().findById(manual); val typedSplit = split(manual); val ops = harvestOps(manual)
        // Edit one (same date keeps the link), delete the other: the typed kilos and split stay, no Jornada operation.
        val draft = DeliveryDraft(farmId = farmId, deliveryDate = day, destinationOrganizationId = null, destinationName = "Cooperativa",
            netGrams = 950_000, shares = listOf(DeliveryShareInput(north, 950_000)), origin = PesadaOrigin.TREE)
        assertTrue(deliveries.update(p, draft) is AppResult.Success)
        assertEquals(manual, db.deliveryDao().findById(p)!!.harvestId)
        assertTrue(deliveries.delete(q) is AppResult.Success)
        assertEquals(row, db.harvestDao().findById(manual))
        assertEquals(typedSplit, split(manual))
        assertEquals(ops, harvestOps(manual))
        // Exposed apart: the typed kilos, and the linked Pesadas as an informative figure.
        val shown = harvests.observe(manual).first()!!
        assertEquals(4_242_000L, shown.totalGrams); assertEquals(950_000L, shown.linkedPesadasGrams); assertFalse(shown.retainedForReview)
        // Moving the Pesada to another date leaves the hand-recorded Jornada as it is.
        assertTrue(deliveries.update(p, draft.copy(deliveryDate = day.plusDays(1))) is AppResult.Success)
        assertEquals(row, db.harvestDao().findById(manual))
        assertNull(harvests.observe(manual).first()!!.linkedPesadasGrams)
    }

    @Test fun theHandRecordedFormKeepsTheTypedKilosWithLinkedPesadasAndAReviewHoldIsShown() = runBlocking<Unit> {
        val manual = UUID.randomUUID()
        db.harvestDao().upsert(HarvestEntity(id = manual, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId, harvestDate = day,
            weightGrams = 4_000_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L), dayOrigin = "MANUAL"))
        val p = pesada(700_000, DeliveryShareInput(north, 700_000))
        db.deliveryDao().findById(p)!!.let { db.deliveryDao().upsert(it.copy(harvestId = manual)) }
        assertTrue(harvests.update(manual, HarvestDraft(farmId, day, 4_100_000, listOf(HarvestShareInput(north, null)))) is AppResult.Success)
        assertEquals(4_100_000L, db.harvestDao().findById(manual)!!.weightGrams)
        assertEquals(700_000L, harvests.observe(manual).first()!!.linkedPesadasGrams)
        // A retained review hold on its operation is exposed to the screen.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status = 'BLOCKED', last_error_code = 'MANUAL_KILOS_REVIEW' WHERE entity_type = 'HARVEST' AND entity_id = ?",
            arrayOf<Any>(manual.toString()))
        assertTrue(harvests.observe(manual).first()!!.retainedForReview)
    }

    @Test fun derivedRecomputationsNeverCreateOutboxForAHandRecordedJornada() = runBlocking<Unit> {
        val manual = UUID.randomUUID()
        db.harvestDao().upsert(HarvestEntity(id = manual, workspaceId = workspaceId, campaignId = campaignId, farmId = farmId, harvestDate = day,
            weightGrams = 1_234_000, metadata = LocalMetadata(now, now, syncStatus = SyncStatus.SYNCED, remoteVersion = 1L), dayOrigin = "MANUAL"))
        val p = pesada(500_000, DeliveryShareInput(north, 500_000))
        db.deliveryDao().findById(p)!!.let { db.deliveryDao().upsert(it.copy(harvestId = manual)) }
        val before = outboxCount(); val manualOps = harvestOps(manual)
        assertTrue(deliveries.delete(p) is AppResult.Success)
        // The Pesada's never-sent CREATE is replaced by its own DELETE marker (same count); nothing for the Jornada.
        assertEquals(before, outboxCount())
        assertEquals(listOf(com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, p).map { it.operation })
        assertEquals(manualOps, harvestOps(manual))
        assertEquals(1_234_000L, db.harvestDao().findById(manual)!!.weightGrams)
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
}
