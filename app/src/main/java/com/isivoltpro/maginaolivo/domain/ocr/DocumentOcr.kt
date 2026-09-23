package com.isivoltpro.maginaolivo.domain.ocr

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Phase 12 — generic document OCR (`RC1.2-PRODUCT-LOCK` §5–§6,
 * `DATA-MODEL-RC1.2-ADDENDUM` §3–§4).
 *
 * capture/import → OCR → extracted draft → human review/correction → confirm → draft record
 *
 * The attachment is the immutable source, the raw text is preserved, and nothing an engine
 * read is trusted until a person has reviewed it. Confirming creates a DRAFT expense at most:
 * posting money is a second, separate human action on the Expense itself.
 */
enum class DocumentType {
    DELIVERY_TICKET,
    PURCHASE_INVOICE,
    PURCHASE_RECEIPT,
    PHYTOSANITARY_INVOICE,
    FERTILIZER_INVOICE,
    IRRIGATION_INVOICE,
    GENERIC_AGRICULTURAL_DOCUMENT,
}

enum class OcrStatus { PENDING, EXTRACTED, NEEDS_REVIEW, CONFIRMED, FAILED }

/** Every field is a proposal. None of them is a fact until a person confirms it. */
data class PurchaseProposal(
    val supplierName: String? = null,
    val supplierTaxId: String? = null,
    val invoiceNumber: String? = null,
    val invoiceDate: LocalDate? = null,
    val subtotalMinor: Long? = null,
    val taxMinor: Long? = null,
    val totalMinor: Long? = null,
    val currency: String? = null,
    val lines: List<PurchaseLine> = emptyList(),
) {
    val hasEssentials: Boolean get() = totalMinor != null && invoiceDate != null
}

data class DocumentExtraction(
    val id: UUID,
    val attachmentId: UUID,
    val documentType: DocumentType,
    val status: OcrStatus,
    val engine: String,
    val rawText: String?,
    val proposal: PurchaseProposal?,
    val expenseId: UUID?,
    /** For a `DELIVERY_TICKET`: what the ticket seems to say, never confirmed by itself. */
    val deliveryProposal: DeliveryTicketProposal? = null,
    val deliveryId: UUID? = null,
    val createdAt: Instant,
    val reviewedAt: Instant?,
)

data class OcrText(
    val text: String,
    val engine: String,
    val engineVersion: String? = null,
)

/** The on-device reader. It returns text; it never decides what the text means. */
interface OcrEngine {
    val name: String

    suspend fun recognize(localUri: String, mimeType: String): OcrText
}

interface DocumentOcrRepository {
    fun observeOpen(): Flow<List<DocumentExtraction>>

    fun observeRecent(): Flow<List<DocumentExtraction>>

    fun observe(id: UUID): Flow<DocumentExtraction?>

    /** Keeps the file as an attachment and records a PENDING extraction for it. */
    suspend fun importDocument(type: DocumentType, sourceUri: String): AppResult<UUID>

    /** Runs the engine; a failure is recorded, and the document is kept either way. */
    suspend fun runExtraction(id: UUID): AppResult<Unit>

    /**
     * The explicit reviewed command: what the person confirmed becomes a DRAFT expense,
     * never a posted one, and the document is linked to it. Runs at most once.
     */
    suspend fun createExpenseDraft(id: UUID, reviewed: ExpenseDraft): AppResult<UUID>

    /**
     * The explicit reviewed command for a weight ticket: the values the person confirmed —
     * not the engine's — become one Delivery, and the ticket file moves to it. Runs at most
     * once; afterwards the reading can neither be run again nor change the Delivery.
     */
    suspend fun confirmDeliveryTicket(id: UUID, reviewed: DeliveryDraft): AppResult<UUID>

    /** Reviewed and kept as a document, with no money attached. */
    suspend fun confirmWithoutExpense(id: UUID): AppResult<Unit>

    suspend fun discard(id: UUID): AppResult<Unit>
}
