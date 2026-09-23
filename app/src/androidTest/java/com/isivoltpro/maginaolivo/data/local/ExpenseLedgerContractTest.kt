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
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.JsonProposalCodec
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDocumentOcrRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
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
 * Phase 12 — Expense ledger, organizations and generic document OCR.
 *
 * Gate 12: no monetary double counting; organization reuse works across contexts; OCR
 * cannot auto-post money or silently confirm extracted values.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseLedgerContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000f1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000f1")
    private val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000f2")
    private val parcelA = UUID.fromString("30000000-0000-0000-0000-0000000000f1")
    private val otherParcel = UUID.fromString("30000000-0000-0000-0000-0000000000f2")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000000f1")
    private val date = LocalDate.parse("2026-03-10")
    private val now = Instant.parse("2026-09-23T08:00:00Z")
    private val sources = File(context.cacheDir, "camera")
    private val attachmentsRoot = File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY)

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var activities: OfflineFirstActivityRepository
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

    // ------------------------------------------------------------ no double counting

    @Test
    fun aPostedExpenseIsCountedOnceWhereverItIsSeen() = runBlocking {
        val activityId = activity(costMinor = null)
        val id = ok(expenses.create(draft(12_000, farmId = farmId, parcelId = parcelA, activityId = activityId)))

        val all = expenses.observeAll().first()
        assertEquals(12_000, ExpenseSummary.of(all).totalMinor)
        assertEquals(listOf(id), expenses.observeForActivity(activityId).first().map { it.id })
        assertEquals(1, all.size)
    }

    @Test
    fun anActivityCostIsOneLinkedExpenseAndNeverASecondNumber() = runBlocking {
        val activityId = activity(costMinor = 6_500)
        val linked = expenses.observeForActivity(activityId).first().single()
        assertEquals(6_500, linked.amountMinor)
        assertEquals(ExpenseOrigin.ACTIVITY_COST, linked.origin)
        assertEquals(ExpenseStatus.POSTED, linked.status)
        assertEquals(6_500, activities.observe(activityId).first()!!.costMinor)
        // The Activity table never carries its own amount (D2).
        assertNull(db.activityDao().findById(activityId)!!.costMinor)

        ok(activities.update(activityId, changes(costMinor = 8_000)))
        val edited = expenses.observeForActivity(activityId).first().single()
        assertEquals(linked.id, edited.id)
        assertEquals(8_000, edited.amountMinor)
        assertEquals(8_000, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)

        ok(activities.update(activityId, changes(costMinor = null)))
        assertTrue(expenses.observeForActivity(activityId).first().isEmpty())
        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
    }

    @Test
    fun anExtraExpenseOnTheSameActivityIsCountedOnceNextToItsCost() = runBlocking {
        val activityId = activity(costMinor = 6_500)
        val extra = ok(expenses.create(draft(2_000, farmId = farmId, activityId = activityId, concept = "Transporte")))

        ok(activities.update(activityId, changes(costMinor = 7_000)))

        assertEquals(9_000, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
        assertEquals(2_000, expenses.observe(extra).first()!!.amountMinor)
        assertEquals(2, expenses.observeForActivity(activityId).first().size)
    }

    @Test
    fun purchaseLinesDescribeTheExpenseAndNeverAddMoney() = runBlocking {
        val id = ok(
            expenses.create(
                draft(12_000, farmId = farmId).copy(
                    invoiceNumber = "F-2026-118",
                    lines = listOf(
                        PurchaseLine("Abono NPK", 10.0, "sacos", lineTotalMinor = 5_000),
                        PurchaseLine("Cobre", 2.0, "kg", lineTotalMinor = 7_000),
                    ),
                ),
            ),
        )

        val expense = expenses.observe(id).first()!!
        assertEquals(2, expense.lines.size)
        assertEquals("F-2026-118", expense.invoiceNumber)
        assertEquals(12_000, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)

        ok(expenses.update(id, draft(12_000, farmId = farmId).copy(lines = listOf(PurchaseLine("Abono NPK")))))
        assertEquals(1, expenses.observe(id).first()!!.lines.size)
        assertEquals(12_000, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
    }

    @Test
    fun aDeletedExpenseLeavesTheTotalsAndQueuesOneTombstone() = runBlocking {
        val id = ok(expenses.create(draft(4_000)))
        ok(expenses.delete(id))
        ok(expenses.delete(id))

        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
        assertNull(expenses.observe(id).first())
        assertEquals(
            listOf(OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.EXPENSE, id).map { it.operation },
        )
    }

    @Test
    fun anExpenseOnAFarmWithARunningCampaignCountsInThatCampaign() = runBlocking {
        val meta = LocalMetadata(now, now)
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "Campaña 2025/26", date.minusMonths(6), status = CampaignStatus.ACTIVE, metadata = meta),
        )
        val id = ok(expenses.create(draft(3_000, farmId = farmId)))
        assertEquals(campaignId, expenses.observe(id).first()!!.campaignId)
    }

    @Test
    fun relationsOutsideTheFarmAreRejectedWithoutWritingMoney() = runBlocking {
        val result = expenses.create(draft(3_000, farmId = farmId, parcelId = otherParcel))
        assertValidation("parcelId", result)
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    @Test
    fun theLedgerSurvivesARestart() = runBlocking {
        val id = ok(expenses.create(draft(5_500, farmId = farmId)))
        db.close()
        open()
        assertEquals(5_500, expenses.observe(id).first()!!.amountMinor)
        assertEquals(5_500, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
    }

    // ------------------------------------------------------------ organization reuse

    @Test
    fun anOrganizationWithTwoRolesIsOneRowReusedAcrossContexts() = runBlocking {
        val id = ok(organizations.create(OrganizationDraft("Cooperativa San Isidro", setOf(OrganizationRole.MILL, OrganizationRole.SUPPLIER))))

        assertEquals(listOf(id), organizations.observeWithAnyRole(setOf(OrganizationRole.SUPPLIER)).first().map { it.id })
        assertEquals(
            listOf(id),
            organizations.observeWithAnyRole(setOf(OrganizationRole.MILL, OrganizationRole.COOPERATIVE)).first().map { it.id },
        )
        assertEquals(
            listOf(id),
            organizations.observeWithAnyRole(setOf(OrganizationRole.MILL, OrganizationRole.SUPPLIER)).first().map { it.id },
        )
        val duplicate = organizations.create(OrganizationDraft("cooperativa san isidro", setOf(OrganizationRole.COOPERATIVE)))
        assertTrue(duplicate is AppResult.Failure && duplicate.error is AppError.Conflict)
        assertEquals(1, organizations.observeAll().first().size)

        val expenseId = ok(expenses.create(draft(1_000).copy(supplierOrganizationId = id)))
        val expense = expenses.observe(expenseId).first()!!
        assertEquals(id, expense.supplierOrganizationId)
        assertEquals("Cooperativa San Isidro", expense.supplierName)
    }

    // ---------------------------------------------------------------- OCR safety

    @Test
    fun aReviewedDocumentBecomesADraftThatIsNeverCountedUntilPosted() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.FERTILIZER_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))

        val extraction = documents.observe(documentId).first()!!
        assertEquals(OcrStatus.EXTRACTED, extraction.status)
        assertEquals(INVOICE, extraction.rawText)
        assertEquals(7_260, extraction.proposal?.totalMinor)
        // Reading a document writes no money at all.
        assertTrue(expenses.observeAll().first().isEmpty())

        val expenseId = ok(documents.createExpenseDraft(documentId, draft(7_260, category = ExpenseCategory.PRODUCTS)))
        val expense = expenses.observe(expenseId).first()!!
        assertEquals(ExpenseStatus.DRAFT, expense.status)
        assertEquals(ExpenseOrigin.DOCUMENT_OCR, expense.origin)
        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
        assertEquals(OcrStatus.CONFIRMED, documents.observe(documentId).first()!!.status)
        assertNotNull(documents.observe(documentId).first()!!.reviewedAt)
        // The original file now belongs to the expense and is still the same file.
        assertEquals(
            listOf(extraction.attachmentId),
            attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.EXPENSE, expenseId)).first().map { it.id },
        )

        ok(expenses.post(expenseId))
        assertEquals(7_260, ExpenseSummary.of(expenses.observeAll().first()).totalMinor)
    }

    @Test
    fun aConfirmedDocumentCannotCreateASecondExpense() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))
        ok(documents.createExpenseDraft(documentId, draft(7_260)))

        val second = documents.createExpenseDraft(documentId, draft(7_260))
        assertTrue(second is AppResult.Failure && second.error is AppError.Conflict)
        assertEquals(1, expenses.observeAll().first().size)
    }

    @Test
    fun aDraftWithoutAnAmountCannotBePosted() = runBlocking {
        engine.text = "Documento sin importes"
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_RECEIPT, source("ticket.jpg")))
        ok(documents.runExtraction(documentId))
        assertEquals(OcrStatus.NEEDS_REVIEW, documents.observe(documentId).first()!!.status)

        val expenseId = ok(documents.createExpenseDraft(documentId, draft(0)))
        assertValidation("amountMinor", expenses.post(expenseId))
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(expenseId).first()!!.status)
    }

    @Test
    fun aFailedReadingKeepsTheDocumentAndCanBeRetried() = runBlocking {
        engine.failure = IllegalStateException("model unavailable")
        val documentId = ok(documents.importDocument(DocumentType.IRRIGATION_INVOICE, source("riego.jpg")))
        assertTrue(documents.runExtraction(documentId) is AppResult.Failure)

        val failed = documents.observe(documentId).first()!!
        assertEquals(OcrStatus.FAILED, failed.status)
        assertTrue(attachments.observe(failed.attachmentId).first()!!.isAvailableLocally)
        assertTrue(expenses.observeAll().first().isEmpty())

        engine.failure = null
        engine.text = INVOICE
        ok(documents.runExtraction(documentId))
        assertEquals(OcrStatus.EXTRACTED, documents.observe(documentId).first()!!.status)
    }

    @Test
    fun aDiscardedDocumentLeavesNoMoneyAndNoFile() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))
        val attachmentId = documents.observe(documentId).first()!!.attachmentId

        ok(documents.discard(documentId))

        assertTrue(documents.observeOpen().first().isEmpty())
        assertNull(attachments.observe(attachmentId).first())
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        expenses = OfflineFirstExpenseRepository(db, workspaces, FixedClock(now), RandomIds, TestDispatchers)
        activities = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        organizations = OfflineFirstOrganizationRepository(db, workspaces, FixedClock(now), RandomIds, TestDispatchers)
        attachments = OfflineFirstAttachmentRepository(db, AndroidAttachmentFileStore(context), FixedClock(now), RandomIds, TestDispatchers)
        documents = OfflineFirstDocumentOcrRepository(
            db, attachments, workspaces, engine, JsonProposalCodec(), FixedClock(now), RandomIds, TestDispatchers,
        )
    }

    private suspend fun activity(costMinor: Long?): UUID = ok(
        activities.create(
            NewActivity(farmId, null, ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelA), costMinor = costMinor),
        ),
    )

    private fun changes(costMinor: Long?) =
        ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelA), costMinor = costMinor)

    private fun draft(
        amountMinor: Long,
        farmId: UUID? = null,
        parcelId: UUID? = null,
        activityId: UUID? = null,
        concept: String = "Compra de abono",
        category: ExpenseCategory = ExpenseCategory.PRODUCTS,
    ) = ExpenseDraft(
        expenseDate = date,
        concept = concept,
        category = category,
        amountMinor = amountMinor,
        farmId = farmId,
        parcelId = parcelId,
        activityId = activityId,
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
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca principal", metadata = meta))
        db.farmDao().upsert(FarmEntity(otherFarmId, workspaceId, "Finca vecina", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelA, workspaceId, "Parcela A", source = "MANUAL", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(otherParcel, workspaceId, "Parcela vecina", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelA, now, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, otherFarmId, otherParcel, now, metadata = meta),
        )
    }

    private class FakeEngine : OcrEngine {
        var text: String = ""
        var failure: Throwable? = null
        override val name: String = "fake-engine"

        override suspend fun recognize(localUri: String, mimeType: String): OcrText {
            failure?.let { throw it }
            return OcrText(text, name, "test")
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

    companion object {
        private const val DB = "expense-ledger-contract-test.db"

        private val INVOICE = """
            Suministros Agrícolas Mágina S.L.
            CIF: B23456789
            Factura nº F-2026-118
            Fecha: 10/03/2026
            Abono NPK 15-15-15  10 sacos
            Base imponible: 60,00 €
            IVA 21%: 12,60 €
            TOTAL: 72,60 €
        """.trimIndent()
    }
}
