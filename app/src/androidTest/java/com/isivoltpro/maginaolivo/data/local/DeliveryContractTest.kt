package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.room.withTransaction
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
import com.isivoltpro.maginaolivo.data.repository.DeliverySyncReceipt
import com.isivoltpro.maginaolivo.data.repository.DeliveryReceiptOutcome
import com.isivoltpro.maginaolivo.data.repository.applyConfirmedDeliveryReceipt
import com.isivoltpro.maginaolivo.data.repository.rebaseConfirmedDeliveryEdit
import com.isivoltpro.maginaolivo.data.repository.DeliveryRebaseOutcome
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.AuthenticatedDeliveryAck
import com.isivoltpro.maginaolivo.data.repository.HeldReceiptOutcome
import com.isivoltpro.maginaolivo.data.repository.retainAckAfterRevocation
import com.isivoltpro.maginaolivo.data.repository.AckCorrelationOutcome
import com.isivoltpro.maginaolivo.data.repository.DispatchIdentity
import com.isivoltpro.maginaolivo.data.repository.PrepareOutcome
import com.isivoltpro.maginaolivo.data.repository.correlateAuthenticatedDeliveryAck
import com.isivoltpro.maginaolivo.data.repository.prepareDeliveryDispatch
import com.isivoltpro.maginaolivo.data.repository.settleAbandonedPreparedDispatches
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryDispatchPrepareEntity
import com.isivoltpro.maginaolivo.data.repository.HeldReconciliationOutcome
import com.isivoltpro.maginaolivo.data.repository.RecoveredDeliveryReceipt
import com.isivoltpro.maginaolivo.data.repository.RemoteDeliveryState
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryHeldReceiptEntity
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SimulatedAccountAuthApi
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchWorker
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DispatchOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import org.junit.Assert.fail
import com.isivoltpro.maginaolivo.data.repository.sync.claimDeliveryRecoveryWithLease
import com.isivoltpro.maginaolivo.data.repository.sync.recoverAbandonedDeliveryClaim
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.JsonProposalCodec
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDocumentOcrRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.WeightedYield
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrEngine
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.OcrText
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 14 — Deliveries, weight-ticket OCR and later yield.
 *
 * Gate 14: the original delivery survives OCR/yield updates unchanged; OCR cannot
 * auto-confirm; weighted metrics are correct.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryContractTest {
    private val context = attachmentFixtureContext(ApplicationProvider.getApplicationContext<Context>(), "delivery")
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000d1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000d1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000000d1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000000d1")
    private val south = UUID.fromString("30000000-0000-0000-0000-0000000000d2")
    private val now = Instant.parse("2026-12-02T08:00:00Z")
    private val day = LocalDate.parse("2026-11-18")
    private val sources = File(context.cacheDir, "camera")
    private val attachmentsRoot = File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY)

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var deliveries: OfflineFirstDeliveryRepository
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var organizations: OfflineFirstOrganizationRepository
    private lateinit var attachments: OfflineFirstAttachmentRepository
    private lateinit var documents: OfflineFirstDocumentOcrRepository
    private val engine = FakeEngine()
    private val linkedFixtureCleanup = mutableListOf<() -> Unit>()

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        sources.deleteRecursively()
        attachmentsRoot.deleteRecursively()
        open()
        seed()
    }

    @After
    fun after() {
        linkedFixtureCleanup.forEach { it() }
        linkedFixtureCleanup.clear()
        db.close()
        context.deleteDatabase(DB)
        sources.deleteRecursively()
        attachmentsRoot.deleteRecursively()
    }

    // ------------------------------------------------------------ delivery ≠ harvest

    @Test
    fun addingAPesadaCannotOverflowItsJornadaTotal() = runBlocking {
        val first = ok(deliveries.create(draft(Long.MAX_VALUE, north to Long.MAX_VALUE)))
        val beforeIds = deliveries.observeAll().first().map { it.id }

        val result = deliveries.create(draft(1, north to 1))
        assertValidation("netGrams", result)
        val error = (result as AppResult.Failure).error as AppError.Validation
        assertEquals("day_total_overflow", error.code)

        assertEquals(beforeIds, deliveries.observeAll().first().map { it.id })
        assertEquals(first, deliveries.observeAll().first().single().id)
        assertEquals(Long.MAX_VALUE, harvests.observeForCampaign(campaignId).first().single().totalGrams)
    }


    @Test
    fun deliveryIdFromAnotherWorkspaceCannotBeReadOrMutated() = runBlocking {
        val otherWorkspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000d2")
        val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000d2")
        val otherCampaignId = UUID.fromString("40000000-0000-0000-0000-0000000000d2")
        val otherParcelId = UUID.fromString("30000000-0000-0000-0000-0000000000d3")
        val meta = LocalMetadata(now, now)

        db.workspaceDao().upsert(
            WorkspaceEntity(otherWorkspaceId, "Otro olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(otherFarmId, otherWorkspaceId, "Finca B", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(otherParcelId, otherWorkspaceId, "Parcela B", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(otherCampaignId, otherWorkspaceId, otherFarmId, "2026/27 B", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(
                CampaignParcelSnapshotEntity(
                    UUID.randomUUID(), otherWorkspaceId, otherCampaignId, otherParcelId, otherFarmId,
                    "Finca B", "Parcela B", metadata = meta,
                ),
            ),
        )

        val workspaceA = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        val workspaceB = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(otherWorkspaceId)
        }
        val repoA = OfflineFirstDeliveryRepository(
            db, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = workspaceA,
            zoneId = { ZoneOffset.UTC },
        )
        val repoB = OfflineFirstDeliveryRepository(
            db, FixedClock(now), RandomIds, TestDispatchers,
            workspaceRepository = workspaceB,
            zoneId = { ZoneOffset.UTC },
        )
        val otherId = ok(
            repoB.create(
                DeliveryDraft(
                    farmId = otherFarmId,
                    deliveryDate = day,
                    destinationOrganizationId = null,
                    destinationName = "Cooperativa B",
                    netGrams = 900_000,
                    shares = listOf(DeliveryShareInput(otherParcelId, null)),
                    origin = PesadaOrigin.TREE,
                ),
            ),
        )

        assertNull(repoA.observe(otherId).first())
        assertValidation(
            "workspaceId",
            repoA.update(
                otherId,
                DeliveryDraft(
                    farmId = otherFarmId,
                    deliveryDate = day,
                    destinationOrganizationId = null,
                    destinationName = "Cooperativa B",
                    netGrams = 950_000,
                    shares = listOf(DeliveryShareInput(otherParcelId, null)),
                    origin = PesadaOrigin.TREE,
                ),
            ),
        )
        assertValidation("workspaceId", repoA.delete(otherId))
        assertValidation("workspaceId", repoA.recordYield(otherId, YieldDraft(null, 2_100, null)))
        assertValidation("workspaceId", repoA.removeYield(otherId))

        assertEquals(900_000L, repoB.observe(otherId).first()!!.netGrams)
        assertNull(repoB.observe(otherId).first()!!.analysis)
    }

    @Test
    fun pesadaAndYieldUseWorkspaceCalendarAtMidnightBoundaries() = runBlocking {
        val workspace = db.workspaceDao().findById(workspaceId)!!
        db.workspaceDao().upsert(workspace.copy(timezone = "Pacific/Honolulu"))
        val repository = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        val utcDay = LocalDate.parse("2026-12-02")

        assertValidation(
            "deliveryDate",
            repository.create(draft(2_000_000, north to null).copy(deliveryDate = utcDay)),
        )

        val validId = ok(repository.create(draft(2_000_000, north to null).copy(deliveryDate = utcDay.minusDays(1))))
        assertValidation(
            "analysisDate",
            repository.recordYield(validId, YieldDraft(analysisDate = utcDay, fatYieldHundredths = 2_000, industrialYieldHundredths = null)),
        )
    }

    @Test
    fun aDeliveryIsItsOwnRecordAndNeedNotMatchTheHarvest() = runBlocking {
        ok(harvests.create(HarvestDraft(farmId, day, 5_000_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        val id = ok(deliveries.create(draft(2_850_000, north to null, south to null)))

        val delivery = deliveries.observe(id).first()!!
        assertEquals(2_850_000L, delivery.netGrams)
        assertEquals(DeliverySource.MANUAL, delivery.source)
        // The hand-recorded harvest keeps its kilos; the Pesada has its own automatic day (CR-010).
        val (automatic, recorded) = harvests.observeAll().first().partition { it.automatic }
        assertEquals(5_000_000L, recorded.single().totalGrams)
        assertEquals(2_850_000L, automatic.single().totalGrams)
        // A mixed load keeps every kilo unallocated.
        assertEquals(2_850_000L, delivery.unallocatedGrams)
        assertTrue(delivery.shares.all { it.allocation == HarvestAllocation.UNALLOCATED && it.weightGrams == null })
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).map { it.operation },
        )
    }

    @Test
    fun anExactSplitMustReconcileWithTheDeliveredKilos() = runBlocking {
        assertValidation("parcels", deliveries.create(draft(2_850_000, north to 2_000_000, south to 800_000)))
        assertValidation("netGrams", deliveries.create(draft(2_850_000, north to null).copy(grossGrams = 12_340_000, tareGrams = 9_500_000)))
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
        val id = ok(deliveries.create(draft(2_850_000, north to 2_000_000, south to 850_000)))
        assertEquals(0L, deliveries.observe(id).first()!!.unallocatedGrams)
    }

    @Test
    fun newPesadasRequireOriginAtRepositoryBoundaryAndLegacyNullSurvivesEdit() = runBlocking {
        assertValidation("origin", deliveries.create(draft(500_000, north to null).copy(origin = null)))

        val tree = ok(deliveries.create(draft(500_000, north to null).copy(origin = PesadaOrigin.TREE)))
        val ground = ok(deliveries.create(draft(600_000, south to null).copy(origin = PesadaOrigin.GROUND)))
        assertEquals(PesadaOrigin.TREE, deliveries.observe(tree).first()!!.origin)
        assertEquals(PesadaOrigin.GROUND, deliveries.observe(ground).first()!!.origin)

        // Simula una Pesada guardada antes de #254: editar otros datos no inventa su origen.
        val stored = db.deliveryDao().findById(tree)!!
        db.deliveryDao().upsert(stored.copy(origin = null))
        ok(deliveries.update(tree, draft(500_000, north to null).copy(origin = null, notes = "Dato legacy")))
        assertNull(deliveries.observe(tree).first()!!.origin)
    }

    // ------------------------------------------------------------ yield does not rewrite the delivery

    @Test
    fun aLaterYieldNeverChangesTheOriginalDelivery() = runBlocking {
        val id = ok(deliveries.create(draft(2_850_000, north to null).copy(ticketNumber = "004512")))
        val before = db.deliveryDao().findById(id)!!

        ok(deliveries.recordYield(id, YieldDraft(LocalDate.parse("2026-11-28"), 2_150, 1_780)))
        ok(deliveries.recordYield(id, YieldDraft(LocalDate.parse("2026-11-29"), 2_210, 1_800)))

        assertEquals(before, db.deliveryDao().findById(id))
        val delivery = deliveries.observe(id).first()!!
        assertEquals(2_210, delivery.analysis!!.fatYieldHundredths)
        assertEquals(2L, delivery.analysis!!.version)
        assertEquals(1L, delivery.version)
        // The delivery's own intent is untouched; the analysis carries its own.
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).map { it.operation },
        )
        assertEquals(
            listOf(OutboxOperation.CREATE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY_YIELD, delivery.analysis!!.id).map { it.operation },
        )

        ok(deliveries.removeYield(id))
        assertEquals(before, db.deliveryDao().findById(id))
        assertNull(deliveries.observe(id).first()!!.analysis)
    }

    @Test
    fun yieldCanArriveAfterCloseButConfirmedYieldStaysHistoricalUntilReopen() = runBlocking {
        val id = ok(deliveries.create(draft(2_850_000, north to null)))
        closeCampaign()

        assertEquals(AppError.Conflict("closed_campaign"), (deliveries.update(id, draft(3_000_000, north to null)) as AppResult.Failure).error)
        assertEquals(AppError.Conflict("closed_campaign"), (deliveries.delete(id) as AppResult.Failure).error)

        // A pending cooperative analysis may legitimately arrive after the Campaign was closed.
        ok(deliveries.recordYield(id, YieldDraft(null, 2_000, null)))
        val confirmed = deliveries.observe(id).first()!!
        assertEquals(2_850_000L, confirmed.netGrams)
        assertEquals(false, confirmed.editable)
        assertEquals(2_000, confirmed.analysis!!.fatYieldHundredths)

        // Once confirmed, the closed historical result is immutable through the normal path.
        assertEquals(
            AppError.Conflict("closed_campaign_yield_confirmed"),
            (deliveries.recordYield(id, YieldDraft(null, 2_200, null)) as AppResult.Failure).error,
        )
        assertEquals(
            AppError.Conflict("closed_campaign_yield_confirmed"),
            (deliveries.removeYield(id) as AppResult.Failure).error,
        )
        assertEquals(2_000, deliveries.observe(id).first()!!.analysis!!.fatYieldHundredths)

        // Reopening the Campaign explicitly restores the normal correction tools.
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.HARVEST))
        ok(deliveries.recordYield(id, YieldDraft(null, 2_200, null)))
        assertEquals(2_200, deliveries.observe(id).first()!!.analysis!!.fatYieldHundredths)
        ok(deliveries.removeYield(id))
        assertNull(deliveries.observe(id).first()!!.analysis)
    }

    @Test
    fun reopenedHistoricalCampaignKeepsPesadasInsideItsOriginalBoundary() = runBlocking {
        val existing = ok(deliveries.create(draft(1_200_000, north to null)))
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(
            campaign.copy(status = CampaignStatus.ACTIVE, endDate = day),
        )
        val afterBoundary = day.plusDays(1)

        val create = deliveries.create(
            draft(900_000, north to null).copy(deliveryDate = afterBoundary),
        )
        val createError = (create as? AppResult.Failure)?.error as? AppError.Validation
        assertEquals("deliveryDate", createError?.field)
        assertEquals("after_campaign", createError?.code)

        val update = deliveries.update(
            existing,
            draft(1_300_000, north to null).copy(deliveryDate = afterBoundary),
        )
        val updateError = (update as? AppResult.Failure)?.error as? AppError.Validation
        assertEquals("deliveryDate", updateError?.field)
        assertEquals("after_campaign", updateError?.code)
        assertEquals(day, deliveries.observe(existing).first()!!.deliveryDate)
        assertEquals(1_200_000L, deliveries.observe(existing).first()!!.netGrams)
    }

    // ------------------------------------------------------------ weighted metrics

    @Test
    fun yieldIsWeightedByDeliveredKilosWithItsCoverage() = runBlocking {
        val first = ok(deliveries.create(draft(1_000_000, north to null)))
        val second = ok(deliveries.create(draft(3_000_000, south to null)))
        ok(deliveries.create(draft(2_000_000, north to null)))
        ok(deliveries.recordYield(first, YieldDraft(null, 2_000, 1_600)))
        ok(deliveries.recordYield(second, YieldDraft(null, 2_400, null)))

        val summary = DeliverySummary.of(deliveries.observeForCampaign(campaignId).first())
        assertEquals(6_000_000L, summary.deliveredGrams)
        assertEquals(WeightedYield(2_300, 4_000_000), summary.fatYield)
        assertEquals(66, summary.coveragePercent(summary.fatYield))
        assertEquals(WeightedYield(1_600, 1_000_000), summary.industrialYield)
    }

    @Test
    fun aDeletedDeliveryLeavesTheTotalsWithItsYield() = runBlocking {
        val kept = ok(deliveries.create(draft(1_000_000, north to null)))
        val gone = ok(deliveries.create(draft(3_000_000, south to null)))
        ok(deliveries.recordYield(kept, YieldDraft(null, 2_000, null)))
        ok(deliveries.recordYield(gone, YieldDraft(null, 2_400, null)))

        ok(deliveries.delete(gone))

        val summary = DeliverySummary.of(deliveries.observeAll().first())
        assertEquals(1_000_000L, summary.deliveredGrams)
        assertEquals(WeightedYield(2_000, 1_000_000), summary.fatYield)
        assertEquals(
            listOf(OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, gone).map { it.operation },
        )
    }

    @Test
    fun deletedDeliveryKeepsFarmScopeSoRevocationBlocksItsLocalIntent() = runBlocking {
        val id = ok(deliveries.create(draft(3_000_000, south to null)))
        assertEquals(farmId, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single().farmId)
        ok(deliveries.delete(id))
        val deletion = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertEquals(OutboxOperation.DELETE, deletion.operation)
        assertEquals(farmId, deletion.farmId)
        // Preserve local-only behavior: farm scope is not upload authorization.
        assertNull(deletion.accountId)
        assertNull(deletion.payloadJson)
        db.syncOutboxDao().updateStatusForFarm(
            farmId,
            com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.BLOCKED,
            now.toEpochMilli(),
        )
        assertEquals(com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.BLOCKED,
            db.syncOutboxDao().findById(deletion.id)!!.status)
        assertTrue(db.syncOutboxDao().listPendingForFarm(farmId).none { it.id == deletion.id })
        assertNull(deliveries.observe(id).first())
    }

    @Test
    fun parcelOnlyEditChangesImmutableSnapshotAndOperationId() = runBlocking {
        val id = ok(deliveries.create(draft(3_000_000, north to 1_000_000L, south to 2_000_000L)))
        val before = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertEquals(2, before.payloadVersion)
        val snapshot = org.json.JSONObject(before.payloadJson!!)
        val parcels = snapshot.getJSONArray("parcels")
        assertEquals(2, parcels.length())
        val ids = (0 until parcels.length()).map { parcels.getJSONObject(it).getString("parcelId") }
        assertEquals(listOf(north, south).map { it.toString() }.sorted(), ids)
        val historic = db.deliveryDao().listParcels(id).associateBy { it.parcelId.toString() }
        for (index in 0 until parcels.length()) {
            val share = parcels.getJSONObject(index)
            val local = historic.getValue(share.getString("parcelId"))
            assertEquals(local.id.toString(), share.getString("id"))
            assertEquals(local.parcelNameAtDelivery, share.getString("parcelNameAtDelivery"))
            assertEquals(local.allocationMode, share.getString("allocationMode"))
            assertEquals(local.weightGrams, share.getLong("weightGrams"))
        }
        ok(deliveries.update(id, draft(3_000_000, north to 1_500_000L, south to 1_500_000L)))
        val after = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertTrue(before.id != after.id)
        assertTrue(before.payloadJson != after.payloadJson)
        assertEquals(3_000_000L, org.json.JSONObject(after.payloadJson!!).getLong("netGrams"))
        val revised = org.json.JSONObject(after.payloadJson!!).getJSONArray("parcels")
        assertEquals(listOf(1_500_000L, 1_500_000L),
            (0 until revised.length()).map { revised.getJSONObject(it).getLong("weightGrams") })
    }

    @Test
    fun unallocatedParcelSnapshotKeepsUnknownWeightAsNull() = runBlocking {
        val id = ok(deliveries.create(draft(3_000_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        val share = org.json.JSONObject(op.payloadJson!!).getJSONArray("parcels").getJSONObject(0)
        assertTrue(share.isNull("weightGrams"))
        assertEquals("UNALLOCATED", share.getString("allocationMode"))
    }

    // ------------------------------------------------------------ OCR cannot auto-confirm

    @Test
    fun readingATicketNeverRecordsADelivery() = runBlocking {
        engine.text = TICKET
        val extractionId = ok(documents.importDocument(DocumentType.DELIVERY_TICKET, source("ticket.jpg")))
        ok(documents.runExtraction(extractionId))

        val extraction = documents.observe(extractionId).first()!!
        assertEquals(OcrStatus.EXTRACTED, extraction.status)
        assertEquals(2_850_000L, extraction.deliveryProposal!!.netGrams)
        assertEquals("004512", extraction.deliveryProposal!!.ticketNumber)
        assertNull(extraction.proposal)
        assertEquals(0, count("deliveries"))
        // A weight ticket never becomes money.
        assertValidation("documentType", documents.createExpenseDraft(extractionId, ExpenseDraft(day, "Vale", ExpenseCategory.OTHER, 100)))
        assertEquals(0, count("expenses"))
    }

    @Test
    fun confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading() = runBlocking {
        engine.text = TICKET
        val extractionId = ok(documents.importDocument(DocumentType.DELIVERY_TICKET, source("ticket.jpg")))
        ok(documents.runExtraction(extractionId))

        // The farmer corrects the net weight the engine read (2.850 → 2.840) and the tare.
        val reviewed = draft(2_840_000, north to null, south to null)
            .copy(grossGrams = 12_340_000, tareGrams = 9_500_000, ticketNumber = "004512")
        val deliveryId = ok(documents.confirmDeliveryTicket(extractionId, reviewed))

        val delivery = deliveries.observe(deliveryId).first()!!
        assertEquals(2_840_000L, delivery.netGrams)
        assertEquals(9_500_000L, delivery.tareGrams)
        assertEquals(DeliverySource.TICKET_OCR, delivery.source)
        val extraction = documents.observe(extractionId).first()!!
        assertEquals(OcrStatus.CONFIRMED, extraction.status)
        assertEquals(deliveryId, extraction.deliveryId)
        assertNotNull(extraction.reviewedAt)
        // The engine's reading is kept as read.
        assertEquals(2_850_000L, extraction.deliveryProposal!!.netGrams)
        // The ticket file now belongs to the Delivery.
        assertEquals(1, attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.DELIVERY, deliveryId)).first().size)

        val before = db.deliveryDao().findById(deliveryId)!!
        assertEquals(AppError.Conflict("already_confirmed"), (documents.confirmDeliveryTicket(extractionId, reviewed) as AppResult.Failure).error)
        assertEquals(AppError.Conflict("already_confirmed"), (documents.runExtraction(extractionId) as AppResult.Failure).error)
        assertEquals(AppError.Conflict("linked_to_delivery"), (documents.discard(extractionId) as AppResult.Failure).error)
        assertEquals(before, db.deliveryDao().findById(deliveryId))
        assertEquals(1, count("deliveries"))
    }

    @Test
    fun aConfirmedTicketGoesToItsAutomaticDayAndNeverToAHandRecordedJornada() = runBlocking {
        // CR-010 (notes 3–4): the ticket saves through the Pesada writer, so it gets the day too.
        val legacy = ok(harvests.create(HarvestDraft(farmId, day, 1_000_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        engine.text = TICKET
        val extractionId = ok(documents.importDocument(DocumentType.DELIVERY_TICKET, source("ticket.jpg")))
        ok(documents.runExtraction(extractionId))
        val deliveryId = ok(documents.confirmDeliveryTicket(extractionId, draft(2_850_000, north to null, south to null)))
        val dayId = deliveries.observe(deliveryId).first()!!.harvestId!!
        assertTrue(dayId != legacy)
        assertEquals(2_850_000L, harvests.observe(dayId).first()!!.totalGrams)
        assertEquals(1_000_000L, harvests.observe(legacy).first()!!.totalGrams)
    }

    @Test
    fun anIncompleteReadingNeedsReviewAndAnInvalidReviewWritesNothing() = runBlocking {
        engine.text = "Cooperativa San Isidro\nBruto 12.340\nTara 9.490"
        val extractionId = ok(documents.importDocument(DocumentType.DELIVERY_TICKET, source("ticket.jpg")))
        ok(documents.runExtraction(extractionId))
        assertEquals(OcrStatus.NEEDS_REVIEW, documents.observe(extractionId).first()!!.status)
        assertNull(documents.observe(extractionId).first()!!.deliveryProposal!!.netGrams)

        assertValidation("netGrams", documents.confirmDeliveryTicket(extractionId, draft(null, north to null)))
        assertEquals(OcrStatus.NEEDS_REVIEW, documents.observe(extractionId).first()!!.status)
        assertEquals(0, count("deliveries"))
    }

    @Test
    fun aSavedCooperativeIsCopiedByNameOntoTheDelivery() = runBlocking {
        val cooperative = ok(organizations.create(OrganizationDraft("Cooperativa San Isidro", setOf(OrganizationRole.COOPERATIVE))))
        val id = ok(deliveries.create(draft(1_000_000, north to null).copy(destinationOrganizationId = cooperative, destinationName = null)))
        val delivery = deliveries.observe(id).first()!!
        assertEquals(cooperative, delivery.destinationOrganizationId)
        assertEquals("Cooperativa San Isidro", delivery.destinationName)
    }

    /** #451: editing a Pesada keeps the cooperative name it was saved with, even once archived. */
    @Test
    fun editingAPesadaKeepsTheCooperativeNameAsCaptured() = runBlocking {
        val cooperative = ok(organizations.create(OrganizationDraft("Cooperativa San Isidro", setOf(OrganizationRole.COOPERATIVE))))
        val saved = draft(1_000_000, north to null).copy(destinationOrganizationId = cooperative, destinationName = null)
        val id = ok(deliveries.create(saved))
        ok(organizations.update(cooperative, OrganizationDraft("S.C.A. San Isidro", setOf(OrganizationRole.COOPERATIVE))))

        ok(deliveries.update(id, saved.copy(notes = "Vale 1234")))
        assertEquals("Cooperativa San Isidro", deliveries.observe(id).first()!!.destinationName)

        ok(organizations.archive(cooperative))
        ok(deliveries.update(id, saved.copy(ticketNumber = "A-77")))
        val kept = deliveries.observe(id).first()!!
        assertEquals("Cooperativa San Isidro", kept.destinationName)
        assertEquals(cooperative, kept.destinationOrganizationId)
        assertEquals("A-77", kept.ticketNumber)

        val mill = ok(organizations.create(OrganizationDraft("Almazara La Loma", setOf(OrganizationRole.MILL))))
        ok(deliveries.update(id, saved.copy(destinationOrganizationId = mill)))
        assertEquals("Almazara La Loma", deliveries.observe(id).first()!!.destinationName)
    }

    /** #455: a Pesada is never moved after its own yield analysis; an undated analysis asks nothing. */
    @Test
    fun aPesadaIsNeverDatedAfterItsYieldAnalysis() = runBlocking {
        val id = ok(deliveries.create(draft(1_000_000, north to null)))
        ok(deliveries.recordYield(id, YieldDraft(day.plusDays(2), 2_100, null)))

        ok(deliveries.update(id, draft(1_000_000, north to null).copy(deliveryDate = day.plusDays(1))))
        ok(deliveries.update(id, draft(1_000_000, north to null).copy(deliveryDate = day.plusDays(2))))
        assertValidation("deliveryDate", deliveries.update(id, draft(1_000_000, north to null).copy(deliveryDate = day.plusDays(3))))
        assertEquals(day.plusDays(2), deliveries.observe(id).first()!!.deliveryDate)
        // Unrelated edits that keep the date still save, and the analysis is never touched.
        ok(deliveries.update(id, draft(1_000_000, north to null).copy(deliveryDate = day.plusDays(2), notes = "Vale 12")))
        assertEquals(day.plusDays(2), deliveries.observe(id).first()!!.analysis?.analysisDate)

        val undated = ok(deliveries.create(draft(500_000, south to null)))
        ok(deliveries.recordYield(undated, YieldDraft(null, 2_000, null)))
        ok(deliveries.update(undated, draft(500_000, south to null).copy(deliveryDate = day.plusDays(5))))
        assertEquals(day.plusDays(5), deliveries.observe(undated).first()!!.deliveryDate)
    }

    @Test
    fun deliveriesAndYieldSurviveARestart() = runBlocking {
        val id = ok(deliveries.create(draft(2_850_000, north to 2_000_000, south to 850_000)))
        ok(deliveries.recordYield(id, YieldDraft(null, 2_150, null)))
        db.close()
        open()
        val delivery = deliveries.observe(id).first()!!
        assertEquals(2_850_000L, delivery.netGrams)
        assertEquals(0L, delivery.unallocatedGrams)
        assertEquals(2_150, delivery.analysis!!.fatYieldHundredths)
    }

    /**
     * #342 — Pesada 1.0: the typed kilos are canonical. Two Pesadas of the same day from two
     * cooperatives, one without photo or ticket number and one with a receipt photo: the photo
     * is only attached, never changes either Pesada, and the totals are the typed kilos, also
     * after a restart.
     */
    @Test
    fun manualPesadasAreCanonicalAndAReceiptPhotoIsOnlyEvidence() = runBlocking {
        val plain = ok(deliveries.create(draft(1_200_000, north to null).copy(destinationName = "Almazara El Molino", origin = PesadaOrigin.GROUND)))
        val withPhoto = ok(deliveries.create(draft(2_850_000, north to null, south to null).copy(origin = PesadaOrigin.TREE)))
        val before = deliveries.observe(withPhoto).first()!!
        val outboxBefore = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, withPhoto).size

        val photo = ok(attachments.attach(AttachmentOwner(AttachmentOwnerType.DELIVERY, withPhoto), source("recibo.jpg")))

        assertEquals(before, deliveries.observe(withPhoto).first())
        assertEquals(outboxBefore, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, withPhoto).size)
        assertEquals(2, count("deliveries"))
        assertEquals(listOf(photo), attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.DELIVERY, withPhoto)).first().map { it.id })
        assertTrue(attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.DELIVERY, plain)).first().isEmpty())
        with(deliveries.observe(plain).first()!!) {
            assertNull(ticketNumber)
            assertEquals("Almazara El Molino", destinationName)
            assertEquals(day, deliveryDate)
        }
        assertEquals(4_050_000L, DeliverySummary.of(deliveries.observeForCampaign(campaignId).first()).deliveredGrams)

        db.close()
        open()
        assertEquals(2_850_000L, deliveries.observe(withPhoto).first()!!.netGrams)
        assertEquals(campaignId, deliveries.observe(withPhoto).first()!!.campaignId)
        assertEquals(farmId, deliveries.observe(withPhoto).first()!!.farmId)
        assertEquals(1, attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.DELIVERY, withPhoto)).first().size)
        assertEquals(4_050_000L, DeliverySummary.of(deliveries.observeForCampaign(campaignId).first()).deliveredGrams)
        // The automatic recolección day of that date counts the same typed kilos.
        assertEquals(4_050_000L, harvests.observeAll().first().single { it.automatic && it.harvestDate == day }.totalGrams)
    }

    /** Issue #254: árbol/vuelo or suelo is stored with the Pesada, kept on edit, survives a restart. */
    @Test
    fun theOriginOfTheOlivesIsKeptWithThePesada() = runBlocking {
        val id = ok(deliveries.create(draft(2_390_000, north to null, south to null).copy(origin = PesadaOrigin.TREE)))
        assertEquals(PesadaOrigin.TREE, deliveries.observe(id).first()!!.origin)
        // An edit that does not mention the origin keeps the one stored.
        ok(deliveries.update(id, draft(2_400_000, north to null, south to null)))
        assertEquals(PesadaOrigin.TREE, deliveries.observe(id).first()!!.origin)
        ok(deliveries.update(id, draft(2_400_000, north to null, south to null).copy(origin = PesadaOrigin.GROUND)))
        db.close()
        open()
        assertEquals(PesadaOrigin.GROUND, deliveries.observe(id).first()!!.origin)
    }

    /** Issue #454: editing a Pesada keeps the origin rows it had and their recorded names. */
    @Test
    fun editingAPesadaKeepsItsParcelRowsAndTheirRecordedNames() = runBlocking {
        val id = ok(deliveries.create(draft(2_000_000, north to null, south to null)))
        val recorded = db.deliveryDao().listParcels(id).associateBy { it.parcelId }
        rename(north, "Parcela 1")

        // Only the notes change: same rows, same ids, same recorded names.
        ok(deliveries.update(id, draft(2_000_000, north to null, south to null).copy(notes = "Vale en la guantera")))
        assertEquals(recorded.values.toSet(), db.deliveryDao().listParcels(id).toSet())

        // Only the kilos change: same rows and names, new kilos.
        ok(deliveries.update(id, draft(2_000_000, north to 1_200_000, south to 800_000)))
        val split = db.deliveryDao().listParcels(id).associateBy { it.parcelId }
        assertEquals(recorded.mapValues { it.value.id }, split.mapValues { it.value.id })
        assertEquals("Norte", split.getValue(north).parcelNameAtDelivery)
        assertEquals(recorded.getValue(north).campaignParcelId, split.getValue(north).campaignParcelId)
        assertEquals(1_200_000L, split.getValue(north).weightGrams)
        assertEquals(HarvestAllocation.EXACT.name, split.getValue(north).allocationMode)

        // Adding a Parcel creates only its row, named as it is now; taking one out drops only its own.
        val east = UUID.fromString("30000000-0000-0000-0000-0000000000d3")
        addToCampaign(east, "Este")
        rename(east, "Olivar del Este")
        ok(deliveries.update(id, draft(2_000_000, north to null, east to null)))
        val after = db.deliveryDao().listParcels(id).associateBy { it.parcelId }
        assertEquals(setOf(north, east), after.keys)
        assertEquals(recorded.getValue(north).id, after.getValue(north).id)
        assertEquals("Norte", after.getValue(north).parcelNameAtDelivery)
        assertEquals("Olivar del Este", after.getValue(east).parcelNameAtDelivery)
        assertEquals("Norte", deliveries.observe(id).first()!!.shares.single { it.parcelId == north }.parcelName)
    }

    private suspend fun rename(parcelId: UUID, name: String) {
        val parcel = db.parcelDao().findById(parcelId)!!
        db.parcelDao().upsert(parcel.copy(displayName = name))
    }

    private suspend fun addToCampaign(parcelId: UUID, name: String) {
        val meta = LocalMetadata(now, now)
        db.parcelDao().upsert(ParcelEntity(parcelId, workspaceId, name, source = "MANUAL", metadata = meta))
        db.campaignDao().upsertSnapshots(
            listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, parcelId, farmId, "La Solana", name, metadata = meta)),
        )
    }

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        val clock = FixedClock(now)
        deliveries = OfflineFirstDeliveryRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        organizations = OfflineFirstOrganizationRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        attachments = OfflineFirstAttachmentRepository(db, AndroidAttachmentFileStore(context), workspaces, clock, RandomIds, TestDispatchers)
        documents = OfflineFirstDocumentOcrRepository(
            db, attachments, workspaces, engine, JsonProposalCodec(), clock, RandomIds, TestDispatchers,
        ) { ZoneOffset.UTC }
    }

    private suspend fun closeCampaign() {
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = LocalDate.parse("2026-12-01")))
    }

    private fun draft(net: Long?, vararg shares: Pair<UUID, Long?>) = DeliveryDraft(
        farmId = farmId,
        deliveryDate = day,
        destinationOrganizationId = null,
        destinationName = "Cooperativa San Isidro",
        netGrams = net,
        shares = shares.map { DeliveryShareInput(it.first, it.second) },
        origin = PesadaOrigin.TREE,
    )

    private fun source(name: String): String {
        sources.mkdirs()
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val file = File(sources, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context.applicationContext, "${context.packageName}.attachments", file).toString()
    }

    /** Rows of [table]; live rows only for tables with a metadata tail. */
    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query(
            if (table in setOf("sync_outbox", "delivery_held_receipts", "delivery_dispatch_prepare")) "SELECT COUNT(*) FROM $table" else "SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun g23DeliveryWriterCapturesCanonicalJsonAndStrictRemoteVersion() = runBlocking {
        val id = ok(deliveries.create(draft(2_850_000, north to null)))
        val outboxRows = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id)
        assertEquals(1, outboxRows.size)
        val createOp = outboxRows.single()
        assertEquals(OutboxOperation.CREATE, createOp.operation)
        assertEquals(farmId, createOp.farmId)
        assertNull(createOp.baseRemoteVersion)
        assertNotNull(createOp.payloadJson)

        val createPayload = org.json.JSONObject(createOp.payloadJson!!)
        assertEquals(id.toString(), createPayload.getString("id"))
        assertEquals(farmId.toString(), createPayload.getString("farmId"))
        assertEquals(campaignId.toString(), createPayload.getString("campaignId"))
        assertEquals("2026-11-18", createPayload.getString("deliveryDate"))
        assertEquals(2_850_000L, createPayload.getLong("netGrams"))
        assertEquals("MANUAL", createPayload.getString("source"))
        assertFalse(createPayload.has("baseVersion"))

        // Simulate local rewrite without server remoteVersion (remoteVersion remains null)
        ok(deliveries.update(id, draft(3_100_000, north to null)))
        val afterFirstUpdate = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertEquals(OutboxOperation.CREATE, afterFirstUpdate.operation) // collapsed create + update remains CREATE
        assertNull(afterFirstUpdate.baseRemoteVersion)
        val firstUpdatePayload = org.json.JSONObject(afterFirstUpdate.payloadJson!!)
        assertEquals(3_100_000L, firstUpdatePayload.getLong("netGrams"))
        assertFalse(firstUpdatePayload.has("baseVersion"))

        // Simulate delivery having been confirmed remotely with remoteVersion = 5
        val currentDelivery = db.deliveryDao().findById(id)!!
        db.deliveryDao().upsert(currentDelivery.copy(metadata = currentDelivery.metadata.copy(remoteVersion = 5L, syncStatus = com.isivoltpro.maginaolivo.data.local.model.SyncStatus.SYNCED)))
        // Mark previous outbox operation as already dispatched / removed
        db.syncOutboxDao().deletePendingForEntity(SyncEntityType.DELIVERY, id)

        // Now perform rewrite on synced delivery
        ok(deliveries.update(id, draft(3_250_000, north to null)))
        val updateOp = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertEquals(OutboxOperation.UPDATE, updateOp.operation)
        assertEquals(5L, updateOp.baseRemoteVersion) // Strict remoteVersion: must be 5L, NOT local version!
        val updatePayload = org.json.JSONObject(updateOp.payloadJson!!)
        assertEquals(5L, updatePayload.getLong("baseVersion"))
        assertEquals(3_250_000L, updatePayload.getLong("netGrams"))

        // Verify UUID behavior:
        // Editing again creates a new operation ID to prevent in-flight tampering
        val prevOpId = updateOp.id
        ok(deliveries.update(id, draft(3_300_000, north to null)))
        val secondUpdateOp = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        assertTrue("Expected new operation UUID on payload edit", secondUpdateOp.id != prevOpId)
        assertEquals(3_300_000L, org.json.JSONObject(secondUpdateOp.payloadJson!!).getLong("netGrams"))
    }

    @Test
    fun g24ConfirmedReceiptUpdatesOnlyRemoteMetadataAndPreservesIdempotency() = runBlocking {
        val deliveryId = ok(deliveries.create(draft(2_850_000, north to null)))
        val accountId = UUID.randomUUID().toString()
        val dao = db.syncOutboxDao()
        val created = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single()
        // Network adoption is explicitly out of scope: simulate an intent bound to
        // a verified account without granting that binding automatically in production.
        dao.deletePendingForEntity(SyncEntityType.DELIVERY, deliveryId)
        val bound = created.copy(accountId = accountId)
        dao.insert(bound)
        val before = db.deliveryDao().findById(deliveryId)!!
        val receipt = DeliverySyncReceipt(
            operationId = bound.id,
            deliveryId = deliveryId,
            farmId = farmId,
            accountId = accountId,
            payloadJson = bound.payloadJson!!,
            remoteVersion = 1L,
        )

        assertEquals(DeliveryReceiptOutcome.SYNCED, db.applyConfirmedDeliveryReceipt(receipt, now.plusSeconds(10)))
        val synced = db.deliveryDao().findById(deliveryId)!!
        assertEquals(before.netGrams, synced.netGrams)
        assertEquals(before.metadata.version, synced.metadata.version)
        assertEquals(before.metadata.updatedAt, synced.metadata.updatedAt)
        assertEquals(1L, synced.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, synced.metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, dao.findById(bound.id)!!.status)
        assertTrue(dao.listReadyForAccount(accountId, now.plusSeconds(11).toEpochMilli()).isEmpty())

        assertEquals(DeliveryReceiptOutcome.ALREADY_CONFIRMED, db.applyConfirmedDeliveryReceipt(receipt, now.plusSeconds(12)))
        ok(deliveries.update(deliveryId, draft(3_000_000, north to null)))
        val changed = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single { it.status == OutboxStatus.PENDING }
        assertEquals(OutboxOperation.UPDATE, changed.operation)
        assertEquals(1L, changed.baseRemoteVersion)
        assertEquals(1L, org.json.JSONObject(changed.payloadJson!!).getLong("baseVersion"))
    }

    @Test
    fun g24LateConfirmationQuarantinesNewerSnapshotWithoutLosingKilos() = runBlocking {
        val deliveryId = ok(deliveries.create(draft(2_850_000, north to null)))
        val accountId = UUID.randomUUID().toString()
        val dao = db.syncOutboxDao()
        val original = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single()
        dao.deletePendingForEntity(SyncEntityType.DELIVERY, deliveryId)
        dao.insert(original.copy(accountId = accountId))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status='PROCESSING' WHERE id=?",
            arrayOf(original.id.toString()),
        )

        ok(deliveries.update(deliveryId, draft(3_300_000, north to null)))
        val latestBefore = db.deliveryDao().findById(deliveryId)!!
        val newer = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single { it.status == OutboxStatus.PENDING }
        assertEquals(OutboxOperation.CREATE, newer.operation)

        val receipt = DeliverySyncReceipt(
            operationId = original.id,
            deliveryId = deliveryId,
            farmId = farmId,
            accountId = accountId,
            payloadJson = original.payloadJson!!,
            remoteVersion = 1L,
        )
        assertEquals(DeliveryReceiptOutcome.NEEDS_REBASE, db.applyConfirmedDeliveryReceipt(receipt, now.plusSeconds(20)))
        assertEquals(OutboxStatus.CONFIRMED, dao.findById(original.id)!!.status)
        assertEquals(OutboxStatus.BLOCKED, dao.findById(newer.id)!!.status)
        val local = db.deliveryDao().findById(deliveryId)!!
        assertEquals(3_300_000L, local.netGrams)
        assertEquals(latestBefore.metadata.version, local.metadata.version)
        assertEquals(latestBefore.metadata.updatedAt, local.metadata.updatedAt)
        assertEquals(1L, local.metadata.remoteVersion)
        assertEquals(SyncStatus.CONFLICT, local.metadata.syncStatus)
        assertTrue(dao.listReadyForAccount(accountId, now.plusSeconds(21).toEpochMilli()).isEmpty())
        assertEquals(DeliveryReceiptOutcome.ALREADY_CONFIRMED, db.applyConfirmedDeliveryReceipt(receipt, now.plusSeconds(22)))
        assertEquals(3_300_000L, db.deliveryDao().findById(deliveryId)!!.netGrams)
    }

    @Test
    fun g24RejectsWrongAccountFarmAndPayloadWithoutMutatingLocalData() = runBlocking {
        val deliveryId = ok(deliveries.create(draft(2_850_000, north to null)))
        val accountId = UUID.randomUUID().toString()
        val dao = db.syncOutboxDao()
        val initial = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single()
        dao.deletePendingForEntity(SyncEntityType.DELIVERY, deliveryId)
        val bound = initial.copy(accountId = accountId)
        dao.insert(bound)
        val valid = DeliverySyncReceipt(
            operationId = initial.id, deliveryId = deliveryId, farmId = farmId,
            accountId = accountId, payloadJson = initial.payloadJson!!, remoteVersion = 1L,
        )
        val before = db.deliveryDao().findById(deliveryId)!!
        assertEquals(DeliveryReceiptOutcome.REJECTED, db.applyConfirmedDeliveryReceipt(valid.copy(accountId = UUID.randomUUID().toString()), now))
        assertEquals(DeliveryReceiptOutcome.REJECTED, db.applyConfirmedDeliveryReceipt(valid.copy(farmId = UUID.randomUUID()), now))
        assertEquals(DeliveryReceiptOutcome.REJECTED, db.applyConfirmedDeliveryReceipt(valid.copy(payloadJson = "{}"), now))
        assertEquals(DeliveryReceiptOutcome.REJECTED, db.applyConfirmedDeliveryReceipt(valid.copy(remoteVersion = 0L), now))
        assertEquals(before, db.deliveryDao().findById(deliveryId))
        assertEquals(OutboxStatus.PENDING, dao.findById(initial.id)!!.status)
    }

    @Test
    fun g24FailedThenEditedRecoversOldIntentAndSyncsLatestKilosExactlyOnce() = runBlocking {
        val (receipt, oldPayload) = failedWithNewerEdit()
        val dao = db.syncOutboxDao()
        assertTrue(dao.listReadyForAccount(receipt.accountId, now.toEpochMilli()).isEmpty())
        assertEquals(0, dao.claimFailedDeliveryForRecovery(receipt.operationId, "other", farmId, now.toEpochMilli()))
        assertEquals(1, dao.claimFailedDeliveryForRecovery(receipt.operationId, receipt.accountId, farmId, now.toEpochMilli()))
        assertEquals(0, dao.claimFailedDeliveryForRecovery(receipt.operationId, receipt.accountId, farmId, now.toEpochMilli()))
        assertEquals(oldPayload, dao.findById(receipt.operationId)!!.payloadJson)
        assertEquals(DeliveryReceiptOutcome.NEEDS_REBASE, db.applyConfirmedDeliveryReceipt(receipt, now))
        val hash = deliveryPayloadHash(receipt.payloadJson, 2)!!
        assertEquals(DeliveryRebaseOutcome.REBASED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, hash, { _, _ -> true }, RandomIds, now,
        ))
        val next = dao.listReadyForAccount(receipt.accountId, now.toEpochMilli()).single()
        assertEquals(OutboxOperation.UPDATE, next.operation)
        assertEquals(7L, next.baseRemoteVersion)
        assertTrue(next.id != receipt.operationId)
        assertEquals(3_300_000L, org.json.JSONObject(next.payloadJson!!).getLong("netGrams"))
        assertEquals(DeliveryRebaseOutcome.ALREADY_REBASED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, hash, { _, _ -> true }, RandomIds, now,
        ))
        assertEquals(next.id, dao.listReadyForAccount(receipt.accountId, now.toEpochMilli()).single().id)
        assertEquals(DeliveryReceiptOutcome.SYNCED, db.applyConfirmedDeliveryReceipt(
            receipt.copy(operationId = next.id, payloadJson = next.payloadJson!!, remoteVersion = 8L), now,
        ))
        assertEquals(3_300_000L, db.deliveryDao().findById(receipt.deliveryId)!!.netGrams)
        assertEquals(SyncStatus.SYNCED, db.deliveryDao().findById(receipt.deliveryId)!!.metadata.syncStatus)
        assertEquals(2, dao.listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId).size)
        assertTrue(dao.listReadyForAccount(receipt.accountId, now.toEpochMilli()).isEmpty())
    }

    @Test
    fun g24ConcurrentRemoteEditPreventsRebaseAtVersionSeven() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        db.applyConfirmedDeliveryReceipt(receipt, now)
        val before = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId)
        assertEquals(DeliveryRebaseOutcome.REMOTE_CONFLICT, db.rebaseConfirmedDeliveryEdit(
            receipt, 8L, deliveryPayloadHash(receipt.payloadJson, 2)!!, { _, _ -> true }, RandomIds, now,
        ))
        assertEquals(before, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId))
        assertEquals(3_300_000L, db.deliveryDao().findById(receipt.deliveryId)!!.netGrams)
    }

    @Test
    fun g24RevokedConsentAndAlteredHashCannotUnlockEdits() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        db.applyConfirmedDeliveryReceipt(receipt, now)
        val before = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId)
        assertEquals(DeliveryRebaseOutcome.NOT_AUTHORIZED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, deliveryPayloadHash(receipt.payloadJson, 2)!!, { _, _ -> false }, RandomIds, now,
        ))
        assertEquals(DeliveryRebaseOutcome.REMOTE_CONFLICT, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, "0".repeat(64), { _, _ -> true }, RandomIds, now,
        ))
        assertEquals(before, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId))
    }

    @Test
    fun g24RecoveryClaimCannotBypassBlockedSibling() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status='BLOCKED' WHERE entity_id=? AND id!=?",
            arrayOf(receipt.deliveryId.toString(), receipt.operationId.toString()),
        )
        assertEquals(0, db.syncOutboxDao().claimFailedDeliveryForRecovery(
            receipt.operationId, receipt.accountId, farmId, now.toEpochMilli(),
        ))
    }

    @Test
    fun g24RebaseRejectsAccountlessOrPreviouslyAttemptedEdits() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        db.applyConfirmedDeliveryReceipt(receipt, now)
        val hash = deliveryPayloadHash(receipt.payloadJson, 2)!!
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET account_id=NULL WHERE entity_id=? AND status='BLOCKED'",
            arrayOf(receipt.deliveryId.toString()),
        )
        assertEquals(DeliveryRebaseOutcome.REJECTED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, hash, { _, _ -> true }, RandomIds, now,
        ))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET account_id=?, attempt_count=1 WHERE entity_id=? AND status='BLOCKED'",
            arrayOf(receipt.accountId, receipt.deliveryId.toString()),
        )
        assertEquals(DeliveryRebaseOutcome.REJECTED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, hash, { _, _ -> true }, RandomIds, now,
        ))
        assertEquals(2, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId).size)
    }

    @Test
    fun g24RebaseDoesNotMakeADeletedDeliveryDispatchable() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        db.applyConfirmedDeliveryReceipt(receipt, now)
        val local = db.deliveryDao().findById(receipt.deliveryId)!!
        db.deliveryDao().upsert(local.copy(metadata = local.metadata.copy(deletedAt = now)))
        assertEquals(DeliveryRebaseOutcome.REJECTED, db.rebaseConfirmedDeliveryEdit(
            receipt, 7L, deliveryPayloadHash(receipt.payloadJson, 2)!!, { _, _ -> true }, RandomIds, now,
        ))
        assertTrue(db.syncOutboxDao().listReadyForAccount(receipt.accountId, now.toEpochMilli()).isEmpty())
        assertEquals(now, db.deliveryDao().findById(receipt.deliveryId)!!.metadata.deletedAt)
    }

    @Test
    fun g24LeaseHandoffRecoversSameOperationAndRejectsWrongNamespaceOrRevocation() = runBlocking {
        val (receipt, payload) = failedWithNewerEdit()
        val directory = File(context.noBackupFilesDir, "g24-lease-${UUID.randomUUID()}")
        val otherDirectory = File(context.noBackupFilesDir, "g24-other-${UUID.randomUUID()}")
        try {
            checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { lease ->
                assertTrue(db.claimDeliveryRecoveryWithLease(lease, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
                assertFalse("The current owner must not recover its own live request", db.recoverAbandonedDeliveryClaim(
                    lease, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now,
                ))
                assertEquals(OutboxStatus.PROCESSING, db.syncOutboxDao().findById(receipt.operationId)!!.status)
                assertNull(DeliveryDispatchLease.tryAcquire(directory))
                checkNotNull(DeliveryDispatchLease.tryAcquire(otherDirectory)).use { wrong ->
                    assertFalse(db.recoverAbandonedDeliveryClaim(wrong, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
                }
            }
            checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { next ->
                assertFalse(db.recoverAbandonedDeliveryClaim(next, receipt.operationId, receipt.accountId, farmId, { _, _ -> false }, now))
                assertTrue(db.recoverAbandonedDeliveryClaim(next, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
                assertFalse(db.recoverAbandonedDeliveryClaim(next, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
                val recovered = db.syncOutboxDao().findById(receipt.operationId)!!
                assertEquals(OutboxStatus.FAILED, recovered.status)
                assertEquals("OUTCOME_UNKNOWN", recovered.lastErrorCode)
                assertEquals(payload, recovered.payloadJson)
                assertEquals(2, recovered.attemptCount)
                assertEquals(3_300_000L, db.deliveryDao().findById(receipt.deliveryId)!!.netGrams)
                assertTrue(db.claimDeliveryRecoveryWithLease(next, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
            }
        } finally { directory.deleteRecursively(); otherDirectory.deleteRecursively() }
    }

    @Test
    fun g24LeaseIsNotHandedOverWhileTheRequestRunsAndItsResultIsKept() = runBlocking {
        val (receipt, payload) = failedWithNewerEdit()
        val directory = File(context.noBackupFilesDir, "g24-inflight-${UUID.randomUUID()}")
        linkedFixtureCleanup.add { directory.deleteRecursively() }
        val lease = checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
        assertTrue(db.claimDeliveryRecoveryWithLease(lease, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val http = kotlinx.coroutines.CompletableDeferred<Long>()
        val request = launch(Dispatchers.Default) {
            lease.withActiveRequest {
                started.complete(Unit)
                val version = http.await()
                db.applyConfirmedDeliveryReceipt(receipt.copy(remoteVersion = version), now)
            }
        }
        started.await()
        lease.close() // e.g. the worker is stopped while the request is still on the wire
        assertNull("No new epoch while the old request runs", DeliveryDispatchLease.tryAcquire(directory))
        assertTrue("A closing lease starts no new claim", runCatching {
            db.claimDeliveryRecoveryWithLease(lease, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now)
        }.exceptionOrNull() is IllegalStateException)
        assertEquals(OutboxStatus.PROCESSING, db.syncOutboxDao().findById(receipt.operationId)!!.status)
        http.complete(7L)
        request.join()
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(receipt.operationId)!!.status)
        checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { next ->
            assertFalse("A finished request is not recovered again",
                db.recoverAbandonedDeliveryClaim(next, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
        }
        val kept = db.syncOutboxDao().findById(receipt.operationId)!!
        assertEquals(OutboxStatus.CONFIRMED, kept.status)
        assertEquals(payload, kept.payloadJson)
        assertEquals(3_300_000L, db.deliveryDao().findById(receipt.deliveryId)!!.netGrams)
        assertEquals(7L, db.deliveryDao().findById(receipt.deliveryId)!!.metadata.remoteVersion)
    }

    @Test
    fun g24LeaseCannotAdoptLegacyUnmarkedProcessing() = runBlocking {
        val (receipt, _) = failedWithNewerEdit()
        assertEquals(1, db.syncOutboxDao().claimFailedDeliveryForRecovery(receipt.operationId, receipt.accountId, farmId, now.toEpochMilli()))
        val directory = File(context.noBackupFilesDir, "g24-legacy-${UUID.randomUUID()}")
        try {
            checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use {
                assertFalse(db.recoverAbandonedDeliveryClaim(it, receipt.operationId, receipt.accountId, farmId, { _, _ -> true }, now))
                assertEquals(OutboxStatus.PROCESSING, db.syncOutboxDao().findById(receipt.operationId)!!.status)
            }
        } finally { directory.deleteRecursively() }
    }

    private suspend fun failedWithNewerEdit(): Pair<DeliverySyncReceipt, String> {
        val (account, _) = configureConsentedWrites()
        val accountId = account.state.value.userId!!.toString()
        val deliveryId = ok(deliveries.create(draft(2_850_000, north to null)))
        val dao = db.syncOutboxDao()
        val original = dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single()
        assertEquals(accountId, original.accountId)
        // Only simulate the failed network outcome; account binding uses production writers.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status='FAILED', attempt_count=1 WHERE id=?",
            arrayOf(original.id.toString()),
        )
        ok(deliveries.update(deliveryId, draft(3_300_000, north to null)))
        assertEquals(accountId, dao.listForEntity(SyncEntityType.DELIVERY, deliveryId).single { it.status == OutboxStatus.PENDING }.accountId)
        return DeliverySyncReceipt(original.id, deliveryId, farmId, accountId, original.payloadJson!!, 7L) to original.payloadJson!!
    }

    @Test
    fun g24AccountBindingUsesConsentAndDoesNotAdoptLegacyOrLoggedOutEdits() = runBlocking {
        val (account, links) = configureConsentedWrites(consent = false)
        val legacy = ok(deliveries.create(draft(2_000_000, north to null)))
        assertNull(db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, legacy).single().accountId)
        val userId = account.state.value.userId!!
        links.consent(userId, links.choices().filter { it.id == farmId }, true)
        val bound = ok(deliveries.create(draft(2_100_000, north to null)))
        assertEquals(userId.toString(), db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, bound).single().accountId)
        assertNull("Consent must not adopt a historical local operation", db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, legacy).single().accountId)
        account.logout(false)
        ok(deliveries.update(bound, draft(2_200_000, north to null)))
        assertNull(db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, bound).single().accountId)
        account.login("b@example.test", "password42", true)
        val other = ok(deliveries.create(draft(2_300_000, north to null)))
        assertNull("Account B must not inherit A's consent", db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, other).single().accountId)
    }

    @Test
    fun g24EditsAfterRevocationStayUnboundAndKeepOldIntentBlocked() = runBlocking {
        val (_, links) = configureConsentedWrites()
        val delivery = ok(deliveries.create(draft(2_000_000, north to null)))
        val previous = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, delivery).single()
        links.revokeConsentForFarm(farmId)
        ok(deliveries.update(delivery, draft(2_500_000, north to null)))
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(previous.id)!!.status)
        assertNull(db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, delivery).single { it.status == OutboxStatus.PENDING }.accountId)
        assertEquals(2_500_000L, db.deliveryDao().findById(delivery)!!.netGrams)
    }

    /**
     * G2.4 block 1: with a very poor signal the Auth refresh never returns, yet a Pesada saves at
     * once with the cached local identity, and a consent revocation also completes without
     * waiting. Edits after revocation stay local (no account) and nothing is lost or duplicated.
     */
    @Test
    fun g24PesadaSavesAndConsentRevokesWhileAuthRefreshIsStuck() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delegate = SimulatedAccountAuthApi()
        val api = object : AccountAuthApi by delegate {
            override val simulated = false
            // Close to expiry, so resume(online) must refresh before anything else.
            override suspend fun signIn(email: String, password: String) =
                delegate.signIn(email, password).let {
                    com.isivoltpro.maginaolivo.feature.account.AuthSession(
                        it.userId, it.email, it.accessToken, it.refreshToken, java.time.Instant.now().plusSeconds(20),
                    )
                }
            override suspend fun refresh(session: com.isivoltpro.maginaolivo.feature.account.AuthSession) =
                gate.await().let { delegate.refresh(session) }
        }
        val account = AccountRepository(api, MemoryAccountSessionStore())
        account.login("slow@example.test", "password42", true)
        val userId = account.state.value.userId!!
        val file = File(context.noBackupFilesDir, "g24-slow-${UUID.randomUUID()}.json")
        val store = AndroidFarmLinkLedger(file)
        val links = LocalFarmLinkRepository(context, db, account, "fixture-project", store)
        linkedFixtureCleanup.add {
            store.read()?.consents?.forEach {
                File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively()
            }
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
        links.consent(userId, links.choices().filter { it.id == farmId }, true)
        deliveries = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers, writeScope = links) { ZoneOffset.UTC }
        val id = ok(deliveries.create(draft(2_000_000, north to null)))

        val stuck = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            .launch { account.resume(true) }
        kotlinx.coroutines.delay(200)
        assertFalse("The refresh must still be waiting for the server", stuck.isCompleted)

        kotlinx.coroutines.withTimeout(5_000) { ok(deliveries.update(id, draft(2_400_000, north to null))) }
        val bound = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id)
        assertEquals("Collapsed into one CREATE, never duplicated", 1, bound.size)
        assertEquals(userId.toString(), bound.single().accountId)
        assertEquals(2_400_000L, db.deliveryDao().findById(id)!!.netGrams)

        kotlinx.coroutines.withTimeout(5_000) { links.revokeConsentForFarm(farmId) }
        kotlinx.coroutines.withTimeout(5_000) { ok(deliveries.update(id, draft(2_600_000, north to null))) }
        val after = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id)
        assertEquals(OutboxStatus.BLOCKED, after.single { it.id == bound.single().id }.status)
        assertNull("After revocation the edit stays local, without account", after.single { it.status == OutboxStatus.PENDING }.accountId)
        assertEquals(2_600_000L, db.deliveryDao().findById(id)!!.netGrams)
        assertFalse(stuck.isCompleted)

        gate.complete(Unit); stuck.join()
        assertEquals(userId, account.state.value.userId)
        assertEquals(2_600_000L, db.deliveryDao().findById(id)!!.netGrams)
        assertEquals(2, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).size)
    }

    private suspend fun configureConsentedWrites(consent: Boolean = true): Pair<AccountRepository, LocalFarmLinkRepository> {
        val file = File(context.noBackupFilesDir, "g24-write-${UUID.randomUUID()}.json")
        val store = AndroidFarmLinkLedger(file)
        // Fake Auth transport, real account repository, ledger/backup, consent and writers.
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        val account = AccountRepository(api, MemoryAccountSessionStore())
        account.login("a@example.test", "password42", true)
        val links = LocalFarmLinkRepository(context, db, account, "fixture-project", store)
        linkedFixtureCleanup.add {
            store.read()?.consents?.forEach {
                File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively()
            }
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
        if (consent) links.consent(account.state.value.userId!!, links.choices().filter { it.id == farmId }, true)
        deliveries = OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers, writeScope = links) { ZoneOffset.UTC }
        return account to links
    }

    @Test
    fun g24AckAfterRevocationStaysBlockedWithoutChangingLocalKilosOrVersion() = runBlocking {
        val (_, links) = configureConsentedWrites()
        val id = ok(deliveries.create(draft(2_800_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        // El ambito se prepara ANTES de la peticion simulada, en la tabla canonica.
        val consent = links.state.value.ledger!!.consents.single()
        val identity = DispatchIdentity(
            accountId = op.accountId!!, issuer = "fixture-project",
            subject = links.subject(UUID.fromString(op.accountId!!)), localWorkspaceId = workspaceId,
        )
        leaseIn(dir("blocked-ack")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        val before = db.deliveryDao().findById(id)!!
        links.revokeConsentForFarm(farmId)
        val ack = DeliverySyncReceipt(op.id, id, farmId, op.accountId!!, op.payloadJson!!, 7L)
        assertEquals(DeliveryReceiptOutcome.REJECTED, db.applyConfirmedDeliveryReceipt(ack, now))
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(before, db.deliveryDao().findById(id))
        assertTrue(db.syncOutboxDao().listReadyForAccount(op.accountId!!, now.toEpochMilli()).isEmpty())
        // La evidencia preparada sobrevive a la revocacion, con su hash y version base intactos.
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals(deliveryPayloadHash(op.payloadJson!!, op.payloadVersion), record.payloadHash)
        assertEquals(op.baseRemoteVersion, record.baseRemoteVersion)
        assertEquals(consent.operationId, record.consentOperationId)
        assertEquals(consent.textVersion, record.consentTextVersion)
        assertEquals(workspaceId, record.localWorkspaceId)
    }

    /** A request in flight (attempted, PROCESSING) when the farmer revokes the farm's consent. */
    private data class HeldFixture(
        val links: LocalFarmLinkRepository,
        val op: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
        val ack: AuthenticatedDeliveryAck,
        val account: AccountRepository,
    )

    private suspend fun inFlightThenRevoked(): HeldFixture {
        val (account, links) = configureConsentedWrites()
        val id = ok(deliveries.create(draft(2_800_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET status='PROCESSING', attempt_count=1 WHERE id=?", arrayOf(op.id.toString()),
        )
        links.revokeConsentForFarm(farmId)
        val ack = AuthenticatedDeliveryAck(op.id, id, farmId, op.accountId!!, op.accountId!!, 7L,
            deliveryPayloadHash(op.payloadJson!!, op.payloadVersion)!!, op.payloadVersion)
        return HeldFixture(links, db.syncOutboxDao().findById(op.id)!!, ack, account)
    }

    private fun LocalFarmLinkRepository.lastRevocation() = state.value.ledger!!.revocations.last().operationId

    @Test
    fun g24LateAckAfterRevocationIsHeldOnceWithoutTouchingTheDelivery() = runBlocking {
        val (links, op, ack) = inFlightThenRevoked()
        val before = db.deliveryDao().findById(ack.deliveryId)!!
        val revocation = links.lastRevocation()
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, revocation, now))
        assertEquals(HeldReceiptOutcome.ALREADY_HELD, db.retainAckAfterRevocation(ack, revocation, now.plusSeconds(5)))
        val receipt = db.deliveryHeldReceiptDao().findByOperation(op.id)!!
        assertEquals(DeliveryHeldReceiptEntity.HELD, receipt.state)
        assertEquals(7L, receipt.remoteVersion)
        assertEquals(revocation, receipt.revocationOperationId)
        assertEquals(now, receipt.receivedAt)
        val blocked = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, blocked.status)
        assertEquals("REVOKED_ACK_HELD", blocked.lastErrorCode)
        assertEquals(op.payloadJson, blocked.payloadJson)
        // Kilos, field data, remoteVersion and syncStatus are exactly as before.
        assertEquals(before, db.deliveryDao().findById(ack.deliveryId))
        assertEquals(1, count("delivery_held_receipts"))
    }

    @Test
    fun g24HeldAckFreezesLaterEditsOfThatDeliveryAcrossRestart() = runBlocking {
        val (links, op, ack) = inFlightThenRevoked()
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        ok(deliveries.update(ack.deliveryId, draft(3_100_000, north to null)))
        // Make the newer local snapshot look dispatch-capable: the held receipt alone must stop it.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET account_id=?, status='FAILED' WHERE entity_id=? AND id != ?",
            arrayOf(ack.accountId, ack.deliveryId.toString(), op.id.toString()),
        )
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id=?", arrayOf(op.id.toString()))
        val edit = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, ack.deliveryId).single { it.id != op.id }
        assertTrue(db.syncOutboxDao().listReadyForAccount(ack.accountId, now.toEpochMilli()).isEmpty())
        assertTrue(db.syncOutboxDao().observeReady(now.toEpochMilli()).first().none { it.id == edit.id })
        assertEquals(0, db.syncOutboxDao().claimFailedDeliveryForRecovery(edit.id, ack.accountId, farmId, now.toEpochMilli()))
        db.close(); open()
        assertEquals(DeliveryHeldReceiptEntity.HELD, db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state)
        assertTrue(db.syncOutboxDao().listReadyForAccount(ack.accountId, now.toEpochMilli()).isEmpty())
        assertEquals(3_100_000L, db.deliveryDao().findById(ack.deliveryId)!!.netGrams)
    }

    @Test
    fun g24IncompatibleAckIsAConflictAndNeverReplacesTheHeldReceipt() = runBlocking {
        val (links, op, ack) = inFlightThenRevoked()
        val revocation = links.lastRevocation()
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, revocation, now))
        assertEquals(HeldReceiptOutcome.RECEIPT_CONFLICT, db.retainAckAfterRevocation(ack.copy(remoteVersion = 9L), revocation, now))
        assertEquals(HeldReceiptOutcome.RECEIPT_CONFLICT, db.retainAckAfterRevocation(ack.copy(remoteVersion = 11L), revocation, now))
        // Once in conflict, even the original ACK cannot quietly clear it.
        assertEquals(HeldReceiptOutcome.RECEIPT_CONFLICT, db.retainAckAfterRevocation(ack, revocation, now))
        val receipt = db.deliveryHeldReceiptDao().findByOperation(op.id)!!
        assertEquals(DeliveryHeldReceiptEntity.CONFLICT, receipt.state)
        assertEquals(7L, receipt.remoteVersion)
        assertEquals(ack.payloadHash, receipt.payloadHash)
        assertEquals(9L, receipt.conflictRemoteVersion)
        assertEquals("RECEIPT_CONFLICT", db.syncOutboxDao().findById(op.id)!!.lastErrorCode)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
    }

    @Test
    fun g24HeldAckRejectsWrongOriginBindingOrBytes() = runBlocking {
        val (links, op, ack) = inFlightThenRevoked()
        val revocation = links.lastRevocation()
        val before = db.deliveryDao().findById(ack.deliveryId)!!
        listOf(
            ack.copy(transportSubject = UUID.randomUUID().toString()),
            ack.copy(transportSubject = ""),
            UUID.randomUUID().toString().let { other -> ack.copy(accountId = other, transportSubject = other) },
            ack.copy(farmId = UUID.randomUUID()),
            ack.copy(deliveryId = UUID.randomUUID()),
            ack.copy(operationId = UUID.randomUUID()),
            ack.copy(payloadVersion = if (op.payloadVersion == 2) 1 else 2),
            ack.copy(remoteVersion = 0L),
            ack.copy(payloadHash = "not-a-hash"),
        ).forEach { assertEquals(it.toString(), HeldReceiptOutcome.REJECTED, db.retainAckAfterRevocation(it, revocation, now)) }
        assertEquals(HeldReceiptOutcome.RECEIPT_CONFLICT, db.retainAckAfterRevocation(ack.copy(payloadHash = "0".repeat(64)), revocation, now))
        assertEquals(0, count("delivery_held_receipts"))
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(before, db.deliveryDao().findById(ack.deliveryId))
    }

    @Test
    fun g24AckForAConsentedOperationIsNotHeld() = runBlocking {
        val (_, _) = configureConsentedWrites()
        val id = ok(deliveries.create(draft(2_800_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        val ack = AuthenticatedDeliveryAck(op.id, id, farmId, op.accountId!!, op.accountId!!, 1L,
            deliveryPayloadHash(op.payloadJson!!, op.payloadVersion)!!, op.payloadVersion)
        assertEquals(HeldReceiptOutcome.REJECTED, db.retainAckAfterRevocation(ack, UUID.randomUUID(), now))
        assertEquals(0, count("delivery_held_receipts"))
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
    }

    private fun lease(name: String): DeliveryDispatchLease {
        val directory = File(context.noBackupFilesDir, "g24-$name-${UUID.randomUUID()}")
        linkedFixtureCleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    private suspend fun HeldFixture.reconcile(
        lease: DeliveryDispatchLease, head: RemoteDeliveryState, confirmed: Boolean = true,
    ) = links.reconcileBlockedDelivery(op.id, confirmed, lease, { head }, RandomIds, now)

    private fun HeldFixture.headAtReceipt() = RemoteDeliveryState(null, ack.remoteVersion, ack.payloadHash)

    @Test
    fun g24HeldAckReconcilesOnlyAfterConfirmationRenewedConsentAndAnUnchangedHead() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        val before = db.deliveryDao().findById(ack.deliveryId)!!
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        lease("reconcile").use { lease ->
            assertEquals(HeldReconciliationOutcome.NOT_CONFIRMED, fixture.reconcile(lease, fixture.headAtReceipt(), confirmed = false))
            assertEquals("No renewed consent yet", HeldReconciliationOutcome.NOT_AUTHORIZED, fixture.reconcile(lease, fixture.headAtReceipt()))
            links.reconsentFarm(account.state.value.userId!!, farmId, true)
            // Another device moved the server row on: the old receipt must not be applied.
            assertEquals(HeldReconciliationOutcome.REMOTE_CONFLICT,
                fixture.reconcile(lease, RemoteDeliveryState(null, 8L, "b".repeat(64))))
            assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
            assertEquals(DeliveryHeldReceiptEntity.HELD, db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state)
            assertEquals(before, db.deliveryDao().findById(ack.deliveryId))
            assertEquals(HeldReconciliationOutcome.SYNCED, fixture.reconcile(lease, fixture.headAtReceipt()))
        }
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(DeliveryHeldReceiptEntity.RECONCILED, db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state)
        val after = db.deliveryDao().findById(ack.deliveryId)!!
        assertEquals(2_800_000L, after.netGrams)
        assertEquals(7L, after.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, after.metadata.syncStatus)
        assertEquals(op.payloadJson, db.syncOutboxDao().findById(op.id)!!.payloadJson)
    }

    @Test
    fun g24RenewalFollowedByAnotherRevocationDoesNotReconcile() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        links.revokeConsentForFarm(farmId)
        lease("revoked-again").use { lease ->
            assertEquals(HeldReconciliationOutcome.NOT_AUTHORIZED, fixture.reconcile(lease, fixture.headAtReceipt()))
        }
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(3, links.state.value.ledger!!.let { it.revocations.size + it.reconsents.size })
    }

    @Test
    fun g24ReconciliationNeverAdoptsAccountLessEditsMadeAfterRevocation() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        ok(deliveries.update(ack.deliveryId, draft(3_100_000, north to null)))
        val edit = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, ack.deliveryId).single { it.id != op.id }
        assertNull(edit.accountId)
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        lease("unbound").use { lease ->
            assertEquals(HeldReconciliationOutcome.NEEDS_REBASE, fixture.reconcile(lease, fixture.headAtReceipt()))
        }
        val frozen = db.syncOutboxDao().findById(edit.id)!!
        assertNull("The account-less edit is not adopted", frozen.accountId)
        assertEquals(OutboxStatus.BLOCKED, frozen.status)
        assertEquals(edit.payloadJson, frozen.payloadJson)
        assertEquals(3_100_000L, db.deliveryDao().findById(ack.deliveryId)!!.netGrams)
        assertTrue(db.syncOutboxDao().listReadyForAccount(ack.accountId, now.toEpochMilli()).isEmpty())
    }

    @Test
    fun g24BlockedWithoutReceiptKeepsTheSameOperationWhenTheServerResultIsUnknown() = runBlocking {
        // ACK lost: the request may or may not have reached the server before revocation.
        val fixture = inFlightThenRevoked()
        val (links, op, _, account) = fixture
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        lease("unknown").use { lease ->
            assertEquals(HeldReconciliationOutcome.REOPENED, fixture.reconcile(lease, RemoteDeliveryState(null, null, null)))
        }
        val reopened = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.FAILED, reopened.status)
        assertEquals("OUTCOME_UNKNOWN", reopened.lastErrorCode)
        assertEquals(op.payloadJson, reopened.payloadJson)
        assertEquals(op.baseRemoteVersion, reopened.baseRemoteVersion)
        assertEquals(1, reopened.attemptCount)
        assertEquals(listOf(op.id), db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId).map { it.id })
    }

    @Test
    fun g24BlockedWithoutReceiptAppliesAVerifiedServerRecordOfTheSameOperation() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        val record = RecoveredDeliveryReceipt(
            op.id, op.entityId, SyncEntityType.DELIVERY, ack.accountId, 7L, ack.payloadHash, op.payloadVersion,
        )
        lease("server-record").use { lease ->
            assertEquals("A different payload for this UUID is a conflict", HeldReconciliationOutcome.RECEIPT_CONFLICT,
                fixture.reconcile(lease, RemoteDeliveryState(record.copy(payloadHash = "c".repeat(64)), 7L, "c".repeat(64))))
            assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
            assertEquals(HeldReconciliationOutcome.REMOTE_CONFLICT, fixture.reconcile(lease, RemoteDeliveryState(record, 9L, "d".repeat(64))))
            assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
            assertEquals(HeldReconciliationOutcome.SYNCED, fixture.reconcile(lease, RemoteDeliveryState(record, 7L, ack.payloadHash)))
        }
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(7L, db.deliveryDao().findById(op.entityId)!!.metadata.remoteVersion)
        assertEquals(2_800_000L, db.deliveryDao().findById(op.entityId)!!.netGrams)
    }

    @Test
    fun g24ReconciliationNeedsTheLeaseAndTheSameAccountThroughout() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        val released = lease("released").also { it.close() }
        assertTrue(runCatching { fixture.reconcile(released, fixture.headAtReceipt()) }.exceptionOrNull() is IllegalStateException)
        lease("logout").use { lease ->
            // The farmer signs out while the server lookup is in flight: nothing is committed.
            val failure = runCatching {
                links.reconcileBlockedDelivery(op.id, true, lease, { account.logout(false); fixture.headAtReceipt() }, RandomIds, now)
            }.exceptionOrNull()
            assertTrue(failure is com.isivoltpro.maginaolivo.feature.account.AuthFailure)
        }
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(DeliveryHeldReceiptEntity.HELD, db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state)
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
        listOf(north to "Norte", south to "Sur").forEach { (id, name) ->
            db.parcelDao().upsert(ParcelEntity(id, workspaceId, name, source = "MANUAL", metadata = meta))
        }
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(north to "Norte", south to "Sur").map { (id, name) ->
                CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, id, farmId, "La Solana", name, metadata = meta)
            },
        )
    }

    private class FakeEngine : OcrEngine {
        var text: String = ""
        override val name: String = "fake-engine"

        override suspend fun recognize(localUri: String, mimeType: String): OcrText = OcrText(text, name, "test")
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

    // ---------------------------------------------------------------------------------------
    // G2.4 WIRING. Every test below must fail if the durable PREPARE record and the ACK
    // correlation are not actually connected: they assert that an unprepared ACK changes
    // nothing, instead of exercising the helpers in isolation.
    // ---------------------------------------------------------------------------------------

    /** A lease on a FIXED directory, so a second acquisition shares its recovery namespace. */
    private fun leaseIn(directory: File): DeliveryDispatchLease {
        linkedFixtureCleanup.add { directory.deleteRecursively() }
        return checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
    }

    private data class PreparedFixture(
        val links: LocalFarmLinkRepository,
        val deliveryId: UUID,
        val op: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
        val identity: DispatchIdentity,
        val ack: AuthenticatedDeliveryAck,
        // Last on purpose: the existing tests destructure the first five components.
        val account: AccountRepository,
    )

    private suspend fun preparable(): PreparedFixture {
        val (account, links) = configureConsentedWrites()
        val id = ok(deliveries.create(draft(2_800_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, id).single()
        val userId = account.state.value.userId!!
        val identity = DispatchIdentity(
            accountId = userId.toString(), issuer = "fixture-project",
            subject = links.subject(userId), localWorkspaceId = workspaceId,
        )
        val ack = AuthenticatedDeliveryAck(
            op.id, id, farmId, op.accountId!!, op.accountId!!, 7L,
            deliveryPayloadHash(op.payloadJson!!, op.payloadVersion)!!, op.payloadVersion,
        )
        return PreparedFixture(links, id, op, identity, ack, account)
    }

    private fun dir(name: String) = File(context.noBackupFilesDir, "g24-$name-${UUID.randomUUID()}")

    @Test
    fun g24AnAckWithoutADurablePrepareRecordIsNeverApplied() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        val before = db.deliveryDao().findById(id)!!
        assertEquals(
            AckCorrelationOutcome.NOT_PREPARED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, links.state.value.ledger, now),
        )
        assertEquals("No PREPARE, no local change", before, db.deliveryDao().findById(id))
        assertEquals(0, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        assertEquals(0, count("delivery_held_receipts"))
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
    }

    @Test
    fun g24PrepareRecordsIdentityConsentAndImmutableBytesAndClaimsTheOperation() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        val consent = links.state.value.ledger!!.consents.single()
        leaseIn(dir("prepare")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
            val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
            assertEquals(id, record.deliveryId)
            assertEquals(farmId, record.farmId)
            assertEquals(op.accountId, record.accountId)
            assertEquals("fixture-project", record.issuer)
            assertEquals(links.subject(UUID.fromString(op.accountId!!)), record.subject)
            assertEquals(workspaceId, record.localWorkspaceId)
            assertEquals("Remote workspace is never invented", null, record.remoteWorkspaceId)
            assertEquals(consent.operationId, record.consentOperationId)
            assertEquals(consent.textVersion, record.consentTextVersion)
            assertEquals(op.payloadVersion, record.payloadVersion)
            assertEquals(deliveryPayloadHash(op.payloadJson!!, op.payloadVersion), record.payloadHash)
            assertEquals(op.baseRemoteVersion, record.baseRemoteVersion)
            assertEquals(lease.claimMarker(), record.leaseMarker)
            assertEquals("Unsettled while the outcome is unknown", null, record.settledAt)
            val claimed = db.syncOutboxDao().findById(op.id)!!
            assertEquals(OutboxStatus.PROCESSING, claimed.status)
            assertEquals(lease.claimMarker(), claimed.lastErrorCode)
            assertEquals("Bytes and base version preserved", op.payloadJson, claimed.payloadJson)
            assertEquals(
                PrepareOutcome.ALREADY_PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now.plusSeconds(5)),
            )
            assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        }
    }

    @Test
    fun g24PrepareNeverReplacesARecordThatDescribesOtherBytes() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        db.deliveryDispatchPrepareDao().insert(
            DeliveryDispatchPrepareEntity(
                operationId = op.id, deliveryId = id, farmId = farmId, accountId = op.accountId!!,
                issuer = "fixture-project", subject = links.subject(UUID.fromString(op.accountId!!)),
                localWorkspaceId = workspaceId,
                consentOperationId = links.state.value.ledger!!.consents.single().operationId,
                consentTextVersion = 2, payloadVersion = op.payloadVersion, payloadHash = "c".repeat(64),
                baseRemoteVersion = op.baseRemoteVersion, leaseMarker = "foreign", preparedAt = now,
            ),
        )
        leaseIn(dir("conflict")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARE_CONFLICT,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        assertEquals("c".repeat(64), db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.payloadHash)
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
    }

    @Test
    fun g24ARevokedFarmIsNeverPreparedForDispatch() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        links.revokeConsentForFarm(farmId)
        leaseIn(dir("revoked")).use { lease ->
            assertEquals(
                PrepareOutcome.NOT_AUTHORIZED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        assertEquals(0, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
    }

    @Test
    fun g24AConsentedCorrelatedAckIsAppliedExactlyOnce() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        leaseIn(dir("applied")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        assertEquals(
            AckCorrelationOutcome.APPLIED_SYNCED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, links.state.value.ledger, now),
        )
        val after = db.deliveryDao().findById(id)!!
        assertEquals("Kilos are never rewritten by a receipt", 2_800_000L, after.netGrams)
        assertEquals(7L, after.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, after.metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals(DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, record.settledOutcome)
        assertEquals(
            AckCorrelationOutcome.ALREADY_SETTLED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, links.state.value.ledger, now.plusSeconds(9)),
        )
        assertEquals(record, db.deliveryDispatchPrepareDao().findByOperation(op.id))
    }

    @Test
    fun g24ACorrelatedAckForOtherBytesOrAnotherFarmIsAMismatchAndWritesNothing() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        leaseIn(dir("mismatch")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        val before = db.deliveryDao().findById(id)!!
        val ledger = links.state.value.ledger
        assertEquals(
            AckCorrelationOutcome.PREPARE_MISMATCH,
            db.correlateAuthenticatedDeliveryAck(ack.copy(payloadHash = "d".repeat(64)), identity, ledger, now),
        )
        assertEquals(
            AckCorrelationOutcome.PREPARE_MISMATCH,
            db.correlateAuthenticatedDeliveryAck(ack.copy(farmId = UUID.randomUUID()), identity, ledger, now),
        )
        assertEquals(
            AckCorrelationOutcome.PREPARE_MISMATCH,
            db.correlateAuthenticatedDeliveryAck(ack.copy(transportSubject = "someone-else"), identity, ledger, now),
        )
        assertEquals(before, db.deliveryDao().findById(id))
        assertEquals(
            "Nothing settled on a mismatch", null,
            db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledAt,
        )
        assertEquals(0, count("delivery_held_receipts"))
    }

    @Test
    fun g24AnAckAfterRevocationIsCorrelatedThenHeldAndTheDeliveryIsUntouched() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        leaseIn(dir("held-wire")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        val before = db.deliveryDao().findById(id)!!
        links.revokeConsentForFarm(farmId)
        assertEquals(
            AckCorrelationOutcome.HELD_AFTER_REVOCATION,
            db.correlateAuthenticatedDeliveryAck(ack, identity, links.state.value.ledger, now),
        )
        assertEquals("The Pesada is evidence-only, never rewritten", before, db.deliveryDao().findById(id))
        val held = db.deliveryHeldReceiptDao().findByOperation(op.id)!!
        assertEquals(DeliveryHeldReceiptEntity.HELD, held.state)
        assertEquals(7L, held.remoteVersion)
        assertEquals(links.lastRevocation(), held.revocationOperationId)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(
            DeliveryDispatchPrepareEntity.SETTLED_HELD,
            db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome,
        )
        assertTrue(db.syncOutboxDao().listReadyForAccount(op.accountId!!, now.toEpochMilli()).isEmpty())
    }

    @Test
    fun g24PrepareIsRefusedWhileAHeldReceiptFreezesThatDelivery() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack, account) = fixture
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, links.lastRevocation(), now))
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        val identity = DispatchIdentity(
            accountId = op.accountId!!, issuer = "fixture-project",
            subject = links.subject(account.state.value.userId!!), localWorkspaceId = workspaceId,
        )
        leaseIn(dir("frozen")).use { lease ->
            assertEquals(
                "Renewed consent alone does not re-prepare a frozen Pesada",
                PrepareOutcome.REJECTED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        assertEquals(0, db.deliveryDispatchPrepareDao().listForDelivery(op.entityId).size)
    }

    @Test
    fun g24AnInterruptedPreparedDispatchSettlesAsUnknownOnlyForTheSameLockOwner() = runBlocking {
        val (links, _, op, identity, _) = preparable()
        val directory = dir("interrupted")
        leaseIn(directory).use { first ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(first, op.id, identity, links.state.value.ledger, now),
            )
        }
        leaseIn(dir("foreign")).use { foreign ->
            assertEquals(0, db.settleAbandonedPreparedDispatches(foreign, op.accountId!!, farmId, now))
        }
        assertEquals(null, db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledAt)
        leaseIn(directory).use { second ->
            assertEquals(
                1,
                db.settleAbandonedPreparedDispatches(second, op.accountId!!, farmId, now.plusSeconds(30)),
            )
        }
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals(DeliveryDispatchPrepareEntity.SETTLED_UNKNOWN, record.settledOutcome)
        assertEquals(
            "The prepared bytes are kept for the server's idempotency",
            op.payloadJson, db.syncOutboxDao().findById(op.id)!!.payloadJson,
        )
        assertEquals(0, db.deliveryDispatchPrepareDao().countUnsettled())
    }

    @Test
    fun g24NeedsRebaseWithARevocationAndNoReceiptIsItsOwnTerminalOutcome() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, _, account) = fixture
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET last_error_code='NEEDS_REBASE' WHERE id=?", arrayOf(op.id.toString()),
        )
        links.reconsentFarm(account.state.value.userId!!, farmId, true)
        lease("needs-rebase").use { lease ->
            assertEquals(
                "P1: its own state, never reopened nor silently rejected",
                HeldReconciliationOutcome.NEEDS_REBASE_WITHOUT_RECEIPT,
                fixture.reconcile(lease, fixture.headAtReceipt()),
            )
        }
        val still = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, still.status)
        assertEquals("NEEDS_REBASE", still.lastErrorCode)
        assertEquals(0, count("delivery_held_receipts"))
    }

    // ---------------------------------------------------------------------------------------
    // G2.4 PARIDAD journal -> Room. Las cuatro garantias que hoy solo estaban probadas contra
    // el diario de ficheros, hoy retirado, y ahora contra la tabla canonica Room.
    // Son la cobertura efectiva que sustituye a aquellas garantias.
    // ---------------------------------------------------------------------------------------

    /** 1) Interrupcion entre registrar el ACK y actualizar el outbox: o todo, o nada. */
    @Test
    fun g24ParityAnInterruptionBetweenTheReceiptAndTheOutboxLeavesNoHalfState() = runBlocking {
        val fixture = inFlightThenRevoked()
        val (links, op, ack) = fixture
        val revocation = links.lastRevocation()
        val before = db.syncOutboxDao().findById(op.id)!!
        val died = runCatching {
            db.withTransaction {
                assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, revocation, now))
                // El recibo ya esta escrito y el outbox anotado, pero el proceso muere aqui,
                // antes de que la transaccion externa confirme.
                assertEquals(
                    DeliveryHeldReceiptEntity.HELD,
                    db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state,
                )
                throw java.io.IOException("process died before commit")
            }
        }
        assertTrue(died.isFailure)
        // Ni recibo huerfano ni anotacion huerfana: exactamente como el rename atomico del diario.
        assertNull(db.deliveryHeldReceiptDao().findByOperation(op.id))
        assertEquals(before.lastErrorCode, db.syncOutboxDao().findById(op.id)!!.lastErrorCode)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals("ok", integrity())
        // Y nada se perdio: el mismo ACK se puede retener despues.
        assertEquals(HeldReceiptOutcome.HELD, db.retainAckAfterRevocation(ack, revocation, now))
        assertEquals(1, count("delivery_held_receipts"))
    }

    /** 2) Muerte del proceso durante una transaccion y recuperacion al reabrir la base. */
    @Test
    fun g24ParityAnAbandonedTransactionIsRolledBackAndACommittedOneSurvivesReopening() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        leaseIn(dir("parity-death")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity, links.state.value.ledger, now),
            )
        }
        // Transaccion abandonada a nivel SQLite: nunca se marca correcta.
        val raw = db.openHelper.writableDatabase
        raw.beginTransaction()
        raw.execSQL(
            "INSERT INTO delivery_held_receipts (operation_id, delivery_id, account_id, farm_id, remote_version," +
                " payload_hash, payload_version, revocation_operation_id, state, conflict_remote_version," +
                " conflict_payload_hash, received_at, resolved_at) VALUES (?,?,?,?,7,?,2,?,'HELD',NULL,NULL,2000,NULL)",
            arrayOf(
                UUID.randomUUID().toString(), id.toString(), op.accountId!!, farmId.toString(),
                "a".repeat(64), UUID.randomUUID().toString(),
            ),
        )
        raw.endTransaction()
        // Reapertura = recuperacion tras muerte del proceso.
        db.close()
        open()
        assertEquals("ok", integrity())
        assertEquals("La transaccion abandonada no deja fila", 0, count("delivery_held_receipts"))
        // El PREPARE confirmado antes de la muerte sigue intacto, con sus bytes inmutables.
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals(id, record.deliveryId)
        assertEquals(deliveryPayloadHash(op.payloadJson!!, op.payloadVersion), record.payloadHash)
        assertEquals(op.baseRemoteVersion, record.baseRemoteVersion)
        assertEquals("Sigue sin liquidar: el resultado era desconocido", null, record.settledAt)
        assertEquals(op.payloadJson, db.syncOutboxDao().findById(op.id)!!.payloadJson)
    }

    /**
     * 3) UTF-8 invalido, corrupcion y truncado: nada se acepta ni se pierde en silencio.
     *
     * La sonda documenta el hallazgo real, comparando codigos numericos: SQLite conserva un
     * surrogate suelto sin alterarlo, asi que NO hay perdida silenciosa en esta ruta. El
     * rechazo se mantiene por paridad con el diario, que fallaba al codificar ese texto, y
     * para que evidencia malformada no llegue a una fila duradera.
     */
    @Test
    fun g24ParityMalformedTextCorruptionAndTruncationNeverPassSilently() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        val ledger = links.state.value.ledger
        // Sonda: que hace realmente SQLite con un surrogate suelto. Se compara por codigo
        // numerico, no por la cadena impresa, porque una consola puede falsear el caracter.
        val malformed = "fixture-\uD800"
        db.openHelper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS utf8_probe (v TEXT)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO utf8_probe (v) VALUES (?)", arrayOf(malformed))
        val roundTripped = db.openHelper.readableDatabase.query("SELECT v FROM utf8_probe").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
        val storedCode = roundTripped.last().code
        assertEquals("Longitud alterada: ${roundTripped.length}", malformed.length, roundTripped.length)
        assertEquals(
            "Codigo del ultimo caracter tras el viaje por SQLite: 0x" + storedCode.toString(16),
            0xD800,
            storedCode,
        )
        leaseIn(dir("parity-utf8")).use { lease ->
            // (a) Un surrogate suelto en el issuer no es UTF-8 valido. El diario fallaba al
            // codificarlo; la ruta Room lo rechaza antes de cualquier escritura duradera.
            assertEquals(
                PrepareOutcome.REJECTED,
                db.prepareDeliveryDispatch(lease, op.id, identity.copy(issuer = "fixture-\uD800"), ledger, now),
            )
            // (b) Un subject malformado se rechaza por malformado, antes de compararlo.
            assertEquals(
                PrepareOutcome.REJECTED,
                db.prepareDeliveryDispatch(lease, op.id, identity.copy(subject = "x|\uDFFF"), ledger, now),
            )
            // (c) Texto desmesurado: se rechaza en lugar de truncarse.
            assertEquals(
                PrepareOutcome.REJECTED,
                db.prepareDeliveryDispatch(lease, op.id, identity.copy(issuer = "a".repeat(9000)), ledger, now),
            )
            assertEquals(0, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
            assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
            // Con texto valido si se prepara.
            assertEquals(PrepareOutcome.PREPARED, db.prepareDeliveryDispatch(lease, op.id, identity, ledger, now))
        }
        // (d) Hash guardado corrompido/truncado: la correlacion falla cerrada, no acepta.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE delivery_dispatch_prepare SET payload_hash = ? WHERE operation_id = ?",
            arrayOf("a".repeat(10), op.id.toString()),
        )
        db.close()
        open()
        val before = db.deliveryDao().findById(id)!!
        assertEquals(
            AckCorrelationOutcome.PREPARE_MISMATCH,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, now),
        )
        assertEquals("Nada se aplica sobre evidencia corrupta", before, db.deliveryDao().findById(id))
        assertEquals(OutboxStatus.PROCESSING, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(0, count("delivery_held_receipts"))
    }

    /** 4) Concurrencia de dos emisores, lease, interrupcion y recuperacion. */
    @Test
    fun g24ParityTwoSendersCannotOwnTheSameDispatchAndRecoveryNeedsTheSameLock() = runBlocking {
        val (links, _, op, identity, _) = preparable()
        val ledger = links.state.value.ledger
        val directory = dir("parity-concurrency")
        val first = leaseIn(directory)
        try {
            // (a) Mientras el primero lo tiene, nadie mas adquiere el MISMO lock del SO.
            assertNull("Un segundo emisor no puede poseer el mismo lease", DeliveryDispatchLease.tryAcquire(directory))
            // (b) Dos intentos concurrentes de preparar el mismo intento: uno prepara, el otro
            // encuentra el registro; nunca dos filas ni dos reclamaciones.
            val outcomes = listOf(
                async(Dispatchers.IO) { db.prepareDeliveryDispatch(first, op.id, identity, ledger, now) },
                async(Dispatchers.IO) { db.prepareDeliveryDispatch(first, op.id, identity, ledger, now) },
            ).awaitAll()
            assertEquals(
                setOf(PrepareOutcome.PREPARED, PrepareOutcome.ALREADY_PREPARED),
                outcomes.toSet(),
            )
            assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(op.entityId).size)
            assertEquals(1, db.deliveryDispatchPrepareDao().countUnsettled())
        } finally { first.close() }
        // (c) Interrupcion: el primero desaparece sin liquidar. Un lease ajeno no puede resolverlo.
        leaseIn(dir("parity-foreign")).use { foreign ->
            assertEquals(0, db.settleAbandonedPreparedDispatches(foreign, op.accountId!!, farmId, now))
        }
        assertEquals(1, db.deliveryDispatchPrepareDao().countUnsettled())
        // (d) Recuperacion: solo quien vuelve a adquirir el MISMO lock lo liquida, y como
        // desconocido, conservando UUID y bytes para que decida la idempotencia del servidor.
        leaseIn(directory).use { second ->
            assertEquals(1, db.settleAbandonedPreparedDispatches(second, op.accountId!!, farmId, now.plusSeconds(60)))
        }
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals(DeliveryDispatchPrepareEntity.SETTLED_UNKNOWN, record.settledOutcome)
        assertEquals(op.payloadJson, db.syncOutboxDao().findById(op.id)!!.payloadJson)
        assertEquals(0, db.deliveryDispatchPrepareDao().countUnsettled())
        assertEquals("ok", integrity())
    }

    private fun integrity(): String =
        db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    // ---------------------------------------------------------------------------------------
    // Revision independiente de #774@7bde446e. Dos hallazgos, cada uno con su prueba.
    // ---------------------------------------------------------------------------------------

    /**
     * P1: OUTCOME_UNKNOWN no puede ser terminal. Significa "no sabemos si el servidor lo
     * aplico", asi que el MISMO intento debe seguir pudiendo recibir su ACK tardio, una sola
     * vez, y el outbox debe volver a ser reintentable con el mismo UUID, bytes y baseVersion.
     */
    @Test
    fun g24ParityAnUnknownOutcomeStillAcceptsItsLateAckExactlyOnce() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        val ledger = links.state.value.ledger
        val directory = dir("unknown-late-ack")
        leaseIn(directory).use { first ->
            assertEquals(PrepareOutcome.PREPARED, db.prepareDeliveryDispatch(first, op.id, identity, ledger, now))
        }
        // El emisor desaparece sin liquidar; otro vuelve a adquirir el MISMO lock.
        leaseIn(directory).use { second ->
            assertEquals(1, db.settleAbandonedPreparedDispatches(second, op.accountId!!, farmId, now.plusSeconds(30)))
        }
        // El intento sigue siendo el mismo y vuelve a ser elegible: nada se reescribe.
        val recovered = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.FAILED, recovered.status)
        assertEquals("OUTCOME_UNKNOWN", recovered.lastErrorCode)
        assertEquals(op.payloadJson, recovered.payloadJson)
        assertEquals(op.baseRemoteVersion, recovered.baseRemoteVersion)
        assertTrue(
            "El mismo UUID vuelve a estar disponible para el servidor",
            db.syncOutboxDao().listReadyForAccount(op.accountId!!, now.plusSeconds(60).toEpochMilli())
                .any { it.id == op.id },
        )
        // El ACK que el servidor si habia emitido llega tarde: debe aplicarse, una sola vez.
        assertEquals(
            AckCorrelationOutcome.APPLIED_SYNCED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, now.plusSeconds(90)),
        )
        val after = db.deliveryDao().findById(id)!!
        assertEquals(2_800_000L, after.netGrams)
        assertEquals(7L, after.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, after.metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        assertEquals(
            DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED,
            db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome,
        )
        // Sin duplicados: una devolucion repetida ya no cambia nada.
        assertEquals(
            AckCorrelationOutcome.ALREADY_SETTLED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, now.plusSeconds(120)),
        )
        assertEquals(1, db.deliveryDao().observeAllForWorkspace(workspaceId).first().size)
    }

    /** P1: y si no llega ACK, el MISMO intento se puede reintentar sin duplicar el PREPARE. */
    @Test
    fun g24ParityAnUnknownOutcomeCanBeRetriedWithTheSameOperationAndBytes() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        val ledger = links.state.value.ledger
        val directory = dir("unknown-retry")
        leaseIn(directory).use { first ->
            assertEquals(PrepareOutcome.PREPARED, db.prepareDeliveryDispatch(first, op.id, identity, ledger, now))
        }
        leaseIn(directory).use { second ->
            assertEquals(1, db.settleAbandonedPreparedDispatches(second, op.accountId!!, farmId, now.plusSeconds(30)))
            // Reintento del MISMO intento bajo un lease vivo: se reanuda, no se bloquea.
            assertEquals(
                PrepareOutcome.ALREADY_PREPARED,
                db.prepareDeliveryDispatch(second, op.id, identity, ledger, now.plusSeconds(40)),
            )
            val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
            assertEquals("Vuelve a estar en vuelo", null, record.settledAt)
            assertEquals(null, record.settledOutcome)
            assertEquals(deliveryPayloadHash(op.payloadJson!!, op.payloadVersion), record.payloadHash)
            assertEquals(op.baseRemoteVersion, record.baseRemoteVersion)
            assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
            val claimed = db.syncOutboxDao().findById(op.id)!!
            assertEquals(OutboxStatus.PROCESSING, claimed.status)
            assertEquals(second.claimMarker(), claimed.lastErrorCode)
            assertEquals(op.payloadJson, claimed.payloadJson)
        }
    }

    /**
     * P1/P2: el re-PREPARE compara TODOS los campos congelados. `remoteWorkspaceId` y
     * `consentTextVersion` se omitian, asi que un destino remoto distinto o una version de
     * texto distinta pasaban por "el mismo" y reclamaban el intento.
     */
    @Test
    fun g24ParityRePrepareRejectsAChangedRemoteWorkspaceOrConsentTextVersion() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        val ledger = links.state.value.ledger
        val remoteA = UUID.fromString("90000000-0000-0000-0000-00000000000a")
        val remoteB = UUID.fromString("90000000-0000-0000-0000-00000000000b")
        leaseIn(dir("immutable-remote")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARED,
                db.prepareDeliveryDispatch(lease, op.id, identity.copy(remoteWorkspaceId = remoteA), ledger, now),
            )
            // Mismo intento, otro destino remoto: no es la misma evidencia.
            assertEquals(
                PrepareOutcome.PREPARE_CONFLICT,
                db.prepareDeliveryDispatch(lease, op.id, identity.copy(remoteWorkspaceId = remoteB), ledger, now),
            )
            assertEquals(remoteA, db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.remoteWorkspaceId)
            assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        }
    }

    /** P1/P2: la misma regla para consentTextVersion, con el mismo consentOperationId. */
    @Test
    fun g24ParityRePrepareRejectsADifferentConsentTextVersion() = runBlocking {
        val (links, id, op, identity, _) = preparable()
        val ledger = links.state.value.ledger
        val consent = ledger!!.consents.single()
        // Registro previo identico salvo la version del texto del consentimiento.
        db.deliveryDispatchPrepareDao().insert(
            DeliveryDispatchPrepareEntity(
                operationId = op.id, deliveryId = id, farmId = farmId, accountId = op.accountId!!,
                issuer = "fixture-project", subject = links.subject(UUID.fromString(op.accountId!!)),
                localWorkspaceId = workspaceId, remoteWorkspaceId = null,
                consentOperationId = consent.operationId,
                consentTextVersion = consent.textVersion + 1,
                payloadVersion = op.payloadVersion,
                payloadHash = deliveryPayloadHash(op.payloadJson!!, op.payloadVersion)!!,
                baseRemoteVersion = op.baseRemoteVersion, leaseMarker = "previo", preparedAt = now,
            ),
        )
        leaseIn(dir("immutable-text")).use { lease ->
            assertEquals(
                PrepareOutcome.PREPARE_CONFLICT,
                db.prepareDeliveryDispatch(lease, op.id, identity, ledger, now),
            )
        }
        val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
        assertEquals("El original no se reemplaza", consent.textVersion + 1, record.consentTextVersion)
        assertEquals("previo", record.leaseMarker)
        assertEquals("El intento no se reclama", OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
    }

    /**
     * P1 de la revision final: DOS cierres consecutivos, tres generaciones de lease A->B->C.
     *
     * El re-PREPARE reclamaba el outbox con la marca de B pero dejaba `lease_marker` en A, asi
     * que la recuperacion de C buscaba por A, no actualizaba ninguna fila y el intento quedaba
     * `PROCESSING` bajo B para siempre: ni elegible ni reintentable. El PREPARE y el outbox
     * deben conservar el marcador de la ULTIMA adquisicion real.
     */
    @Test
    fun g24ParityTwoConsecutiveCrashesKeepTheLatestLeaseMarkerAndStayRecoverable() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        val ledger = links.state.value.ledger
        val directory = dir("abc-recovery")

        // Lease A: prepara y desaparece.
        val markerA = leaseIn(directory).use { a ->
            val marker = a.claimMarker()
            assertEquals(PrepareOutcome.PREPARED, db.prepareDeliveryDispatch(a, op.id, identity, ledger, now))
            assertEquals(marker, db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.leaseMarker)
            assertEquals(marker, db.syncOutboxDao().findById(op.id)!!.lastErrorCode)
            marker
        }

        // Lease B: recupera lo de A, reintenta el MISMO intento y desaparece tambien.
        val markerB = leaseIn(directory).use { b ->
            val marker = b.claimMarker()
            assertEquals(1, db.settleAbandonedPreparedDispatches(b, op.accountId!!, farmId, now.plusSeconds(30)))
            assertEquals(
                PrepareOutcome.ALREADY_PREPARED,
                db.prepareDeliveryDispatch(b, op.id, identity, ledger, now.plusSeconds(40)),
            )
            val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
            assertEquals("El PREPARE debe llevar el marcador del titular actual", marker, record.leaseMarker)
            assertNotEquals("Ya no es el emisor difunto", markerA, record.leaseMarker)
            assertEquals(marker, db.syncOutboxDao().findById(op.id)!!.lastErrorCode)
            marker
        }
        assertNotEquals("Cada adquisicion es una epoca distinta", markerA, markerB)

        // Lease C: segundo cierre consecutivo. Debe recuperar igual que el primero.
        leaseIn(directory).use { c ->
            assertEquals(1, db.settleAbandonedPreparedDispatches(c, op.accountId!!, farmId, now.plusSeconds(70)))
            val recovered = db.syncOutboxDao().findById(op.id)!!
            assertEquals(OutboxStatus.FAILED, recovered.status)
            assertEquals("OUTCOME_UNKNOWN", recovered.lastErrorCode)
            assertEquals("Mismo payload tras dos cierres", op.payloadJson, recovered.payloadJson)
            assertEquals(op.baseRemoteVersion, recovered.baseRemoteVersion)
            assertTrue(
                "El mismo UUID vuelve a estar disponible",
                db.syncOutboxDao().listReadyForAccount(op.accountId!!, now.plusSeconds(90).toEpochMilli())
                    .any { it.id == op.id },
            )
            // Identidad y consentimiento congelados: dos cierres no los tocan.
            val record = db.deliveryDispatchPrepareDao().findByOperation(op.id)!!
            assertEquals(DeliveryDispatchPrepareEntity.SETTLED_UNKNOWN, record.settledOutcome)
            assertEquals(id, record.deliveryId)
            assertEquals(farmId, record.farmId)
            assertEquals(op.accountId, record.accountId)
            assertEquals(links.subject(UUID.fromString(op.accountId!!)), record.subject)
            assertEquals(ledger!!.consents.single().operationId, record.consentOperationId)
            assertEquals(ledger.consents.single().textVersion, record.consentTextVersion)
            assertEquals(deliveryPayloadHash(op.payloadJson!!, op.payloadVersion), record.payloadHash)
            assertEquals(op.baseRemoteVersion, record.baseRemoteVersion)
            assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
        }

        // Y el ACK tardio de ese mismo intento sigue aplicandose, exactamente una vez.
        assertEquals(
            AckCorrelationOutcome.APPLIED_SYNCED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, now.plusSeconds(120)),
        )
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op.id)!!.status)
        val after = db.deliveryDao().findById(id)!!
        assertEquals(2_800_000L, after.netGrams)
        assertEquals(7L, after.metadata.remoteVersion)
        assertEquals(1, db.deliveryDao().observeAllForWorkspace(workspaceId).first().size)
        assertEquals(
            AckCorrelationOutcome.ALREADY_SETTLED,
            db.correlateAuthenticatedDeliveryAck(ack, identity, ledger, now.plusSeconds(150)),
        )
    }

    /**
     * Corolario: la recuperacion no pisa un estado que SI tiene resultado. Un intento bloqueado
     * por revocacion con su recibo retenido no se convierte en OUTCOME_UNKNOWN.
     */
    @Test
    fun g24ParityRecoveryNeverOverwritesABlockedOperationThatAlreadyHasItsReceipt() = runBlocking {
        val (links, id, op, identity, ack) = preparable()
        val ledger = links.state.value.ledger
        val directory = dir("abc-blocked")
        leaseIn(directory).use { a ->
            assertEquals(PrepareOutcome.PREPARED, db.prepareDeliveryDispatch(a, op.id, identity, ledger, now))
        }
        // Llega el ACK despues de revocar: queda retenido y la operacion BLOCKED.
        links.revokeConsentForFarm(farmId)
        assertEquals(
            AckCorrelationOutcome.HELD_AFTER_REVOCATION,
            db.correlateAuthenticatedDeliveryAck(ack, identity, links.state.value.ledger, now.plusSeconds(10)),
        )
        val before = db.syncOutboxDao().findById(op.id)!!
        leaseIn(directory).use { c ->
            // Nada que recuperar: su resultado no es desconocido, esta retenido como evidencia.
            assertEquals(0, db.settleAbandonedPreparedDispatches(c, op.accountId!!, farmId, now.plusSeconds(40)))
        }
        val still = db.syncOutboxDao().findById(op.id)!!
        assertEquals(OutboxStatus.BLOCKED, still.status)
        assertEquals(before.lastErrorCode, still.lastErrorCode)
        assertEquals(
            DeliveryDispatchPrepareEntity.SETTLED_HELD,
            db.deliveryDispatchPrepareDao().findByOperation(op.id)!!.settledOutcome,
        )
        assertEquals(DeliveryHeldReceiptEntity.HELD, db.deliveryHeldReceiptDao().findByOperation(op.id)!!.state)
        assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(id).size)
    }

    // ---------------------------------------------------------------------------------------
    // G2.4 WORKER. The transport is a fake, but every rule under test is the production one:
    // PREPARE before the request, the lease held across the request and the persistence of its
    // result, consent rechecked after the wait, and identity verified by the real repository.
    // ---------------------------------------------------------------------------------------

    /** A transport that answers the three contracted RPCs and can misbehave on demand. */
    private class FakeRpcTransport(
        private val onWrite: () -> DeliveryRpcResponse,
        private val onCheck: () -> DeliveryRpcResponse,
        private val onHead: () -> DeliveryRpcResponse = { DeliveryRpcResponse(200, """{"delivery":{"version":7},"head_payload_hash":null}""") },
    ) : DeliveryRpcTransport {
        val names = mutableListOf<String>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            check(accessToken.isNotBlank()) { "El transporte exige el token de la sesion verificada" }
            return when (name) {
                DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V2 -> onWrite()
                DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS -> onCheck()
                else -> onHead()
            }
        }
    }

    private fun receiptBody(fixture: PreparedFixture): String {
        val hash = deliveryPayloadHash(fixture.op.payloadJson!!, fixture.op.payloadVersion)!!
        return """{"confirmed_receipts":[{"operation_id":"${fixture.op.id}","entity_id":"${fixture.deliveryId}",
            "entity_type":"DELIVERY","remote_version":7,"payload_version":${fixture.op.payloadVersion},
            "payload_hash":"$hash"}]}"""
    }

    private fun writeBody(fixture: PreparedFixture) =
        """{"operation_id":"${fixture.op.id}","entity_id":"${fixture.deliveryId}","remote_version":7,
           "payload_version":${fixture.op.payloadVersion}}"""

    private fun workerWith(transport: DeliveryRpcTransport, links: LocalFarmLinkRepository) =
        DeliveryDispatchWorker(db, links, DeliveryRpcAdapter(transport)) { now }

    @Test
    fun g24WorkerALostResponseLeavesTheOutcomeUnknownAndRecoversByTheSameUuid() = runBlocking {
        val fixture = preparable()
        val directory = dir("worker-lost")
        val before = db.deliveryDao().findById(fixture.deliveryId)!!

        // The response is lost after the server may well have committed.
        val cut = FakeRpcTransport(
            onWrite = { throw java.io.IOException("respuesta perdida") },
            onCheck = { DeliveryRpcResponse(200, """{"confirmed_receipts":[]}""") },
        )
        leaseIn(directory).use { lease ->
            assertEquals(
                DispatchOutcome.OUTCOME_UNKNOWN,
                workerWith(cut, fixture.links).dispatch(fixture.op.id, lease),
            )
            // The durable PREPARE exists and is NOT settled: the lease still owns the intent.
            val prepared = db.deliveryDispatchPrepareDao().findByOperation(fixture.op.id)!!
            assertNull("Un resultado desconocido no se liquida solo", prepared.settledAt)
            assertEquals(lease.claimMarker(), prepared.leaseMarker)
            val op = db.syncOutboxDao().findById(fixture.op.id)!!
            assertEquals(OutboxStatus.PROCESSING, op.status)
            assertEquals("Mismos bytes tras perder la respuesta", fixture.op.payloadJson, op.payloadJson)
            assertEquals(fixture.op.id, op.id)
            assertEquals(SyncStatus.PENDING, db.deliveryDao().findById(fixture.deliveryId)!!.metadata.syncStatus)
        }

        // The server did have it. The SAME UUID recovers its receipt; no second operation exists.
        val serving = FakeRpcTransport(
            onWrite = { fail("La recuperacion no reenvia"); error("inalcanzable") },
            onCheck = { DeliveryRpcResponse(200, receiptBody(fixture)) },
        )
        leaseIn(directory).use { lease ->
            val worker = workerWith(serving, fixture.links)
            assertEquals(1, worker.settleInterrupted(farmId, lease))
            assertEquals(DispatchOutcome.SYNCED, worker.recoverLostResponse(fixture.op.id, lease))
            assertEquals(DispatchOutcome.SYNCED, worker.recoverLostResponse(fixture.op.id, lease))
        }
        assertEquals(DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS, serving.names.first())
        val after = db.deliveryDao().findById(fixture.deliveryId)!!
        assertEquals(SyncStatus.SYNCED, after.metadata.syncStatus)
        assertEquals("Los kilos no se tocan", before.netGrams, after.netGrams)
        assertEquals(7L, after.metadata.remoteVersion)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(fixture.op.id)!!.status)
        assertEquals(1, db.deliveryDispatchPrepareDao().listForDelivery(fixture.deliveryId).size)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, fixture.deliveryId).size)
    }

    @Test
    fun g24WorkerARevocationDuringTheRequestRetainsTheReceiptAndLeavesThePesadaUntouched() = runBlocking {
        val fixture = preparable()
        val before = db.deliveryDao().findById(fixture.deliveryId)!!
        // The farmer revokes while the request is in flight: after the wait, consent is gone.
        val transport = FakeRpcTransport(
            onWrite = { DeliveryRpcResponse(200, writeBody(fixture)) },
            onCheck = {
                runBlocking { fixture.links.revokeConsentForFarm(farmId) }
                DeliveryRpcResponse(200, receiptBody(fixture))
            },
        )
        leaseIn(dir("worker-revoked")).use { lease ->
            assertEquals(
                DispatchOutcome.HELD_AFTER_REVOCATION,
                workerWith(transport, fixture.links).dispatch(fixture.op.id, lease),
            )
        }
        val after = db.deliveryDao().findById(fixture.deliveryId)!!
        assertEquals("La pesada no se marca sincronizada", SyncStatus.PENDING, after.metadata.syncStatus)
        assertEquals(before.netGrams, after.netGrams)
        assertEquals(before.metadata.remoteVersion, after.metadata.remoteVersion)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(fixture.op.id)!!.status)
        // The receipt is kept as evidence, not discarded.
        assertEquals(
            DeliveryHeldReceiptEntity.HELD,
            db.deliveryHeldReceiptDao().findByOperation(fixture.op.id)!!.state,
        )
        assertEquals(
            DeliveryDispatchPrepareEntity.SETTLED_HELD,
            db.deliveryDispatchPrepareDao().findByOperation(fixture.op.id)!!.settledOutcome,
        )
    }

    @Test
    fun g24WorkerAnExpiredTokenNeverIssuesTheRequestAndWritesNothing() = runBlocking {
        // Same account and the SAME consent as a normal dispatch: the only thing wrong is that
        // the access token has expired by the time the worker runs. The clock is the repository's
        // own, so the expiry is observed exactly where production observes it.
        // Real time to start with: the simulated Auth API issues its expiry from the wall clock,
        // so a fixture date in the future would make the session born expired.
        var clock = Instant.now()
        val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
        val account = AccountRepository(api, MemoryAccountSessionStore()) { clock }
        account.login("expired@example.test", "password42", true)
        val userId = account.state.value.userId!!
        val ledgerFile = File(context.noBackupFilesDir, "g24-expired-${UUID.randomUUID()}.json")
        val store = AndroidFarmLinkLedger(ledgerFile)
        linkedFixtureCleanup.add {
            store.read()?.consents?.forEach {
                File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively()
            }
            listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new")).forEach { it.delete() }
        }
        val links = LocalFarmLinkRepository(context, db, account, "fixture-project", store)
        links.consent(userId, links.choices().filter { it.id == farmId }, true)
        val writer = OfflineFirstDeliveryRepository(
            db, FixedClock(now), RandomIds, TestDispatchers, writeScope = links,
        ) { ZoneOffset.UTC }
        val deliveryId = ok(writer.create(draft(2_800_000, north to null)))
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, deliveryId).single()
        assertEquals(userId, checkNotNull(account.state.value.userId))

        // Now the token is past its expiry, as the repository sees time.
        clock = clock.plusSeconds(86_400)

        val transport = FakeRpcTransport(
            onWrite = { fail("Un token caducado no envia nada"); error("inalcanzable") },
            onCheck = { fail("Un token caducado no lee nada"); error("inalcanzable") },
        )
        leaseIn(dir("worker-expired")).use { lease ->
            assertEquals(
                DispatchOutcome.NOT_AUTHORIZED,
                workerWith(transport, links).dispatch(op.id, lease),
            )
        }
        assertTrue("No se abrio ninguna peticion", transport.names.isEmpty())
        assertNull(
            "Sin autoridad vigente no hay PREPARE",
            db.deliveryDispatchPrepareDao().findByOperation(op.id),
        )
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(op.id)!!.status)
        assertEquals(SyncStatus.PENDING, db.deliveryDao().findById(deliveryId)!!.metadata.syncStatus)
    }

    @Test
    fun g24WorkerAnAccountChangeDuringTheRequestCommitsNothing() = runBlocking {
        val fixture = preparable()
        val before = db.deliveryDao().findById(fixture.deliveryId)!!
        val account = fixture.account
        // The session disappears while the request is in flight: the commit must be refused.
        val transport = FakeRpcTransport(
            onWrite = { DeliveryRpcResponse(200, writeBody(fixture)) },
            onCheck = {
                runBlocking { account.logout(false) }
                DeliveryRpcResponse(200, receiptBody(fixture))
            },
        )
        leaseIn(dir("worker-account-change")).use { lease ->
            assertEquals(
                DispatchOutcome.NOT_AUTHORIZED,
                workerWith(transport, fixture.links).dispatch(fixture.op.id, lease),
            )
        }
        val after = db.deliveryDao().findById(fixture.deliveryId)!!
        assertEquals("Un cambio de cuenta no confirma nada", SyncStatus.PENDING, after.metadata.syncStatus)
        assertEquals(before.netGrams, after.netGrams)
        assertEquals(before.metadata.remoteVersion, after.metadata.remoteVersion)
        assertNull(db.deliveryHeldReceiptDao().findByOperation(fixture.op.id))
        // The PREPARE written before the request survives, unsettled, under its original subject.
        val prepared = db.deliveryDispatchPrepareDao().findByOperation(fixture.op.id)!!
        assertNull(prepared.settledAt)
        assertEquals(fixture.identity.subject, prepared.subject)
    }

    @Test
    fun g24WorkerAReceiptForAnotherEntityTypeIsNeverAdoptedAsThisPesada() = runBlocking {
        // Review #774: the worker builds an AuthenticatedDeliveryAck, which carries no entity
        // type, so the DELIVERY guard of verifiedRecoveredDeliveryReceipt is NOT on this path.
        // Before the fix, a receipt for another entity sharing this operation UUID was adopted
        // as this Pesada's ACK and the delivery was marked SYNCED.
        val fixture = preparable()
        val before = db.deliveryDao().findById(fixture.deliveryId)!!
        val hash = deliveryPayloadHash(fixture.op.payloadJson!!, fixture.op.payloadVersion)!!

        // ONE lease for the whole loop: the same sender retrying. A fresh lease directory per
        // iteration would be a DIFFERENT sender, and the operation claimed by the first epoch
        // would correctly be refused as REJECTED, which would test the wrong thing.
        val directory = dir("worker-foreign")
        leaseIn(directory).use { lease ->
        for (type in SyncEntityType.entries.filter { it != SyncEntityType.DELIVERY }) {
            val transport = FakeRpcTransport(
                onWrite = { DeliveryRpcResponse(200, writeBody(fixture)) },
                onCheck = {
                    // Everything else matches: same operation, same delivery, same hash.
                    DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"${fixture.op.id}",
                        "entity_id":"${fixture.deliveryId}","entity_type":"$type","remote_version":7,
                        "payload_version":${fixture.op.payloadVersion},"payload_hash":"$hash"}]}""")
                },
            )
            assertEquals(
                "entity_type=$type no puede adoptarse como esta pesada",
                DispatchOutcome.CONFLICT,
                workerWith(transport, fixture.links).dispatch(fixture.op.id, lease),
            )
            val after = db.deliveryDao().findById(fixture.deliveryId)!!
            assertEquals("entity_type=$type", SyncStatus.PENDING, after.metadata.syncStatus)
            assertEquals("entity_type=$type", before.netGrams, after.netGrams)
            assertEquals("entity_type=$type", before.metadata.remoteVersion, after.metadata.remoteVersion)
            assertNull("entity_type=$type: nada se retiene como prueba",
                db.deliveryHeldReceiptDao().findByOperation(fixture.op.id))
            assertNull("entity_type=$type: el PREPARE sigue sin liquidar",
                db.deliveryDispatchPrepareDao().findByOperation(fixture.op.id)!!.settledAt)
            assertEquals("entity_type=$type", OutboxStatus.PROCESSING,
                db.syncOutboxDao().findById(fixture.op.id)!!.status)
        }

        // And the SAME sender, with a DELIVERY receipt, still applies it: the guard is specific
        // to the entity type and does not poison the operation for the legitimate receipt.
        val good = FakeRpcTransport(
            onWrite = { DeliveryRpcResponse(200, writeBody(fixture)) },
            onCheck = { DeliveryRpcResponse(200, receiptBody(fixture)) },
        )
        assertEquals(
            DispatchOutcome.SYNCED,
            workerWith(good, fixture.links).dispatch(fixture.op.id, lease),
        )
        }
        assertEquals(SyncStatus.SYNCED, db.deliveryDao().findById(fixture.deliveryId)!!.metadata.syncStatus)
        assertEquals(before.netGrams, db.deliveryDao().findById(fixture.deliveryId)!!.netGrams)
    }

    private companion object {
        const val DB = "delivery-contract-test.db"

        val TICKET = """
            S.C.A. Cooperativa San Isidro
            Ticket nº 004512
            Fecha: 18/11/2026
            Peso bruto: 12.340 kg
            Tara: 9.490 kg
            Peso neto: 2.850 kg
        """.trimIndent()
    }
}
