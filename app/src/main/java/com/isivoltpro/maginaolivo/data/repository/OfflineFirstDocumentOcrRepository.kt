package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DocumentOcrExtractionEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.ocr.DeliveryTicketParser
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrEngine
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseDocumentParser
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Generic document OCR, local first.
 *
 * The extraction row is created before the engine runs, so a document the process died
 * reading is still there, PENDING, and can be read again. The file itself is an ordinary
 * attachment owned by the extraction until a person confirms what it is.
 */
class OfflineFirstDocumentOcrRepository(
    private val database: MaginaOlivoDatabase,
    private val attachments: AttachmentRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val engine: OcrEngine,
    private val proposalCodec: ProposalCodec,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : DocumentOcrRepository {
    private val writer = ExpenseLedgerWriter(database, idGenerator)
    private val deliveries = DeliveryWriter(database, idGenerator)

    override fun observeOpen(): Flow<List<DocumentExtraction>> =
        database.documentOcrDao().observeOpen().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeRecent(): Flow<List<DocumentExtraction>> =
        database.documentOcrDao().observeRecent().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<DocumentExtraction?> =
        database.documentOcrDao().observeById(id).map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun importDocument(type: DocumentType, sourceUri: String): AppResult<UUID> {
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        val extractionId = idGenerator.newId()
        val now = clock.nowInstant()
        // The row exists first, so the attachment has an owner to belong to.
        val created = transaction("create_extraction") {
            database.documentOcrDao().upsert(
                DocumentOcrExtractionEntity(
                    id = extractionId,
                    workspaceId = workspaceId,
                    attachmentId = extractionId,
                    documentType = type.name,
                    engine = engine.name,
                    status = OcrStatus.PENDING.name,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ),
            )
            AppResult.Success(Unit)
        }
        if (created is AppResult.Failure) return created

        val attachmentId = when (
            val attached = attachments.attach(AttachmentOwner(AttachmentOwnerType.DOCUMENT, extractionId), sourceUri)
        ) {
            is AppResult.Failure -> {
                transaction("drop_extraction") {
                    database.documentOcrDao().findById(extractionId)?.let { row ->
                        database.documentOcrDao().upsert(row.copy(metadata = row.metadata.copy(deletedAt = clock.nowInstant())))
                    }
                    AppResult.Success(Unit)
                }
                return attached
            }
            is AppResult.Success -> attached.value
        }
        return transaction("link_extraction") {
            val row = database.documentOcrDao().findById(extractionId)
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            database.documentOcrDao().upsert(row.copy(attachmentId = attachmentId))
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, extractionId, OutboxOperation.CREATE, clock.nowInstant())
            AppResult.Success(extractionId)
        }
    }

    override suspend fun runExtraction(id: UUID): AppResult<Unit> {
        val row = withContext(dispatchers.io) { database.documentOcrDao().findById(id) }
            ?: return AppResult.Failure(AppError.NotFound("extraction"))
        if (row.status == OcrStatus.CONFIRMED.name) return AppResult.Failure(AppError.Conflict("already_confirmed"))
        val attachment = attachments.observe(row.attachmentId).first()
            ?: return recordFailure(id, "attachment_missing")
        if (!attachment.isAvailableLocally) return recordFailure(id, "attachment_missing")

        val text = try {
            engine.recognize(attachment.localUri, attachment.mimeType)
        } catch (error: Throwable) {
            return recordFailure(id, error.javaClass.simpleName)
        }
        val (json, hasEssentials) = when (row.documentType) {
            DocumentType.GENERIC_AGRICULTURAL_DOCUMENT.name -> null to true
            DocumentType.DELIVERY_TICKET.name -> DeliveryTicketParser.parse(text.text).let { proposal ->
                proposalCodec.encodeDelivery(proposal) to (proposal.hasEssentials && !proposal.weightsDisagree)
            }
            else -> PurchaseDocumentParser.parse(text.text).let { proposal ->
                proposalCodec.encode(proposal) to proposal.hasEssentials
            }
        }
        // EXTRACTED still waits for a person: neither status writes a Delivery or money.
        val status = if (text.text.isBlank() || !hasEssentials) OcrStatus.NEEDS_REVIEW else OcrStatus.EXTRACTED
        return transaction("record_extraction") {
            val current = database.documentOcrDao().findById(id)
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            if (current.status == OcrStatus.CONFIRMED.name) {
                return@transaction AppResult.Failure(AppError.Conflict("already_confirmed"))
            }
            val now = clock.nowInstant()
            database.documentOcrDao().upsert(
                current.copy(
                    engine = text.engine,
                    engineVersion = text.engineVersion,
                    rawText = text.text,
                    extractedJson = json,
                    status = status.name,
                    metadata = current.metadata.next(now),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun createExpenseDraft(id: UUID, reviewed: ExpenseDraft): AppResult<UUID> {
        val result = transaction("create_expense_draft") {
            val current = database.documentOcrDao().findById(id)
                ?.takeIf { it.metadata.deletedAt == null }
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            if (current.documentType == DocumentType.DELIVERY_TICKET.name) {
                // A weight ticket records kilos delivered, not money spent.
                return@transaction AppResult.Failure(AppError.Validation("documentType", "delivery_ticket"))
            }
            if (current.status == OcrStatus.CONFIRMED.name) {
                return@transaction AppResult.Failure(AppError.Conflict("already_confirmed"))
            }
            val now = clock.nowInstant()
            // DRAFT, always: confirming what a document says is not the same as spending it.
            val expenseId = writer.insert(current.workspaceId, reviewed, ExpenseStatus.DRAFT, ExpenseOrigin.DOCUMENT_OCR, now)
            database.documentOcrDao().upsert(
                current.copy(
                    ownerType = AttachmentOwnerType.EXPENSE.name,
                    ownerId = expenseId,
                    status = OcrStatus.CONFIRMED.name,
                    reviewedAt = now,
                    confirmedAt = now,
                    metadata = current.metadata.next(now),
                ),
            )
            reOwnAttachment(current.attachmentId, AttachmentOwner(AttachmentOwnerType.EXPENSE, expenseId), now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, id, OutboxOperation.UPDATE, now)
            AppResult.Success(expenseId)
        }
        return result
    }

    override suspend fun confirmDeliveryTicket(id: UUID, reviewed: DeliveryDraft): AppResult<UUID> =
        transaction("confirm_delivery_ticket") {
            val current = database.documentOcrDao().findById(id)
                ?.takeIf { it.metadata.deletedAt == null }
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            if (current.documentType != DocumentType.DELIVERY_TICKET.name) {
                return@transaction AppResult.Failure(AppError.Validation("documentType", "not_a_delivery_ticket"))
            }
            if (current.status == OcrStatus.CONFIRMED.name) {
                return@transaction AppResult.Failure(AppError.Conflict("already_confirmed"))
            }
            val now = clock.nowInstant()
            // The reviewed values, never the engine's: the proposal stays as it was read.
            val delivery = deliveries.insert(reviewed, DeliverySource.TICKET_OCR, clock.today(zoneId()), now)
            database.documentOcrDao().upsert(
                current.copy(
                    ownerType = AttachmentOwnerType.DELIVERY.name,
                    ownerId = delivery.id,
                    status = OcrStatus.CONFIRMED.name,
                    reviewedAt = now,
                    confirmedAt = now,
                    metadata = current.metadata.next(now),
                ),
            )
            reOwnAttachment(current.attachmentId, AttachmentOwner(AttachmentOwnerType.DELIVERY, delivery.id), now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, id, OutboxOperation.UPDATE, now)
            AppResult.Success(delivery.id)
        }

    override suspend fun confirmWithoutExpense(id: UUID): AppResult<Unit> =
        transaction("confirm_document") {
            val current = database.documentOcrDao().findById(id)
                ?.takeIf { it.metadata.deletedAt == null }
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            if (current.status == OcrStatus.CONFIRMED.name) return@transaction AppResult.Success(Unit)
            val now = clock.nowInstant()
            database.documentOcrDao().upsert(
                current.copy(
                    ownerType = AttachmentOwnerType.DOCUMENT.name,
                    ownerId = id,
                    status = OcrStatus.CONFIRMED.name,
                    reviewedAt = now,
                    confirmedAt = now,
                    metadata = current.metadata.next(now),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }

    override suspend fun discard(id: UUID): AppResult<Unit> {
        var attachmentId: UUID? = null
        val result = transaction("discard_document") {
            val current = database.documentOcrDao().findById(id)
                ?: return@transaction AppResult.Failure(AppError.NotFound("extraction"))
            if (current.metadata.deletedAt != null) return@transaction AppResult.Success(Unit)
            if (current.status == OcrStatus.CONFIRMED.name && current.ownerType == AttachmentOwnerType.EXPENSE.name) {
                // Its file now belongs to an expense; discarding the reading must not take it away.
                return@transaction AppResult.Failure(AppError.Conflict("linked_to_expense"))
            }
            if (current.status == OcrStatus.CONFIRMED.name && current.ownerType == AttachmentOwnerType.DELIVERY.name) {
                return@transaction AppResult.Failure(AppError.Conflict("linked_to_delivery"))
            }
            val now = clock.nowInstant()
            database.documentOcrDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.DOCUMENT_EXTRACTION, id, OutboxOperation.DELETE, now)
            attachmentId = current.attachmentId
            AppResult.Success(Unit)
        }
        if (result is AppResult.Success) attachmentId?.let { attachments.remove(it) }
        return result
    }

    private suspend fun recordFailure(id: UUID, code: String): AppResult<Unit> {
        transaction("record_ocr_failure") {
            val current = database.documentOcrDao().findById(id) ?: return@transaction AppResult.Success(Unit)
            val now = clock.nowInstant()
            database.documentOcrDao().upsert(
                current.copy(status = OcrStatus.FAILED.name, confidenceJson = null, metadata = current.metadata.next(now)),
            )
            AppResult.Success(Unit)
        }
        return AppResult.Failure(AppError.Unknown(IllegalStateException(code)))
    }

    /** The document stays the same immutable file; only who it belongs to changes. */
    private suspend fun reOwnAttachment(attachmentId: UUID, owner: AttachmentOwner, now: Instant) {
        val document = database.documentDao().findById(attachmentId) ?: return
        database.documentDao().update(
            document.copy(
                ownerType = owner.type.name,
                ownerId = owner.id,
                metadata = document.metadata.copy(
                    updatedAt = now,
                    version = document.metadata.version + 1,
                    syncStatus = SyncStatus.PENDING,
                ),
            ),
        )
    }

    private suspend fun <T> transaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidExpense) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: InvalidDelivery) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: DeliveryConflict) {
                AppResult.Failure(AppError.Conflict(error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private fun DocumentOcrExtractionEntity.toDomain() = DocumentExtraction(
        id = id,
        attachmentId = attachmentId,
        documentType = DocumentType.entries.firstOrNull { it.name == documentType }
            ?: DocumentType.GENERIC_AGRICULTURAL_DOCUMENT,
        status = OcrStatus.entries.firstOrNull { it.name == status } ?: OcrStatus.NEEDS_REVIEW,
        engine = engine,
        rawText = rawText,
        proposal = extractedJson?.takeIf { documentType != DocumentType.DELIVERY_TICKET.name }?.let(proposalCodec::decode),
        expenseId = ownerId?.takeIf { ownerType == AttachmentOwnerType.EXPENSE.name },
        deliveryProposal = extractedJson?.takeIf { documentType == DocumentType.DELIVERY_TICKET.name }
            ?.let(proposalCodec::decodeDelivery),
        deliveryId = ownerId?.takeIf { ownerType == AttachmentOwnerType.DELIVERY.name },
        createdAt = metadata.createdAt,
        reviewedAt = reviewedAt,
    )

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
}
