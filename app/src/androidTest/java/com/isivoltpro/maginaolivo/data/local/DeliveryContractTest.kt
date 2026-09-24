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
    private val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun aDeliveryIsItsOwnRecordAndNeedNotMatchTheHarvest() = runBlocking {
        ok(harvests.create(HarvestDraft(farmId, day, 5_000_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        val id = ok(deliveries.create(draft(2_850_000, north to null, south to null)))

        val delivery = deliveries.observe(id).first()!!
        assertEquals(2_850_000L, delivery.netGrams)
        assertEquals(DeliverySource.MANUAL, delivery.source)
        assertEquals(1, harvests.observeAll().first().size)
        assertEquals(5_000_000L, harvests.observeAll().first().single().totalGrams)
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
    fun yieldCanArriveAfterTheCampaignClosesWhileTheDeliveryStaysHistory() = runBlocking {
        val id = ok(deliveries.create(draft(2_850_000, north to null)))
        closeCampaign()

        assertEquals(AppError.Conflict("closed_campaign"), (deliveries.update(id, draft(3_000_000, north to null)) as AppResult.Failure).error)
        assertEquals(AppError.Conflict("closed_campaign"), (deliveries.delete(id) as AppResult.Failure).error)
        ok(deliveries.recordYield(id, YieldDraft(null, 2_000, null)))

        val delivery = deliveries.observe(id).first()!!
        assertEquals(2_850_000L, delivery.netGrams)
        assertEquals(false, delivery.editable)
        assertEquals(2_000, delivery.analysis!!.fatYieldHundredths)
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
    fun aConfirmedTicketCanJoinAJornadaWhoseKilosFollowIt() = runBlocking {
        // Phase 19B: the Pesada read from a ticket joins the day's Jornada in the same review.
        val jornadaId = ok(harvests.create(HarvestDraft(farmId, day, 1_000_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        engine.text = TICKET
        val extractionId = ok(documents.importDocument(DocumentType.DELIVERY_TICKET, source("ticket.jpg")))
        ok(documents.runExtraction(extractionId))
        val deliveryId = ok(
            documents.confirmDeliveryTicket(extractionId, draft(2_850_000, north to null, south to null).copy(harvestId = jornadaId)),
        )
        assertEquals(jornadaId, deliveries.observe(deliveryId).first()!!.harvestId)
        assertEquals(2_850_000L, harvests.observe(jornadaId).first()!!.totalGrams)
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

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        val clock = FixedClock(now)
        deliveries = OfflineFirstDeliveryRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        organizations = OfflineFirstOrganizationRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        attachments = OfflineFirstAttachmentRepository(db, AndroidAttachmentFileStore(context), clock, RandomIds, TestDispatchers)
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
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", file).toString()
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
