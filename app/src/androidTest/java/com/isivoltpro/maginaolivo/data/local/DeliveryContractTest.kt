package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            if (table == "sync_outbox") "SELECT COUNT(*) FROM $table" else "SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL",
        ).use { cursor ->
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
