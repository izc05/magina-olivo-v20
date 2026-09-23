package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentUploadState
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Phase 11 — the attachment aggregate, local first.
 *
 * Order of an attach: validate the owner, copy the binary into app storage, then write the
 * `documents` row and its single upload intent in one transaction. If that transaction
 * fails the copy is released, so no orphan file outlives a row that was never written.
 */
class OfflineFirstAttachmentRepository(
    private val database: MaginaOlivoDatabase,
    private val fileStore: AttachmentFileStore,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : AttachmentRepository {
    override fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>> =
        database.documentDao()
            .observeForOwner(owner.type.name, owner.id)
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Attachment?> =
        database.documentDao()
            .observeById(id)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)

    override suspend fun attach(
        owner: AttachmentOwner,
        sourceUri: String,
    ): AppResult<UUID> = withContext(dispatchers.io) {
        when (val resolved = resolveOwner(owner)) {
            is AppResult.Failure -> return@withContext resolved
            is AppResult.Success -> Unit
        }
        val attachmentId = idGenerator.newId()
        val stored = when (val copied = copyIn(sourceUri, attachmentId) { AttachmentKind.fromMimeType(it) != null }) {
            is AppResult.Failure -> return@withContext copied
            is AppResult.Success -> copied.value
        }
        val kind = AttachmentKind.fromMimeType(stored.mimeType) ?: AttachmentKind.PHOTO
        val now = clock.nowInstant()

        val result = runCatching {
            database.withTransaction {
                val workspaceId = when (val current = resolveOwner(owner)) {
                    is AppResult.Failure -> return@withTransaction current
                    is AppResult.Success -> current.value
                }
                database.documentDao().insert(
                    stored.toEntity(attachmentId, workspaceId, owner, kind, now),
                )
                enqueue(
                    attachmentId,
                    SyncEntityType.DOCUMENT,
                    OutboxOperation.UPLOAD_ATTACHMENT,
                    now,
                )
                AppResult.Success(attachmentId)
            }
        }.getOrElse { error ->
            AppResult.Failure(AppError.Storage(operation = "attach_document", cause = error))
        }
        if (result is AppResult.Failure) fileStore.delete(stored.localUri, attachmentId)
        result
    }

    override suspend fun remove(id: UUID): AppResult<Unit> = withContext(dispatchers.io) {
        val now = clock.nowInstant()
        var released: DocumentEntity? = null
        val result = runCatching {
            database.withTransaction {
                val document = database.documentDao().findById(id)
                    ?: return@withTransaction AppResult.Failure(AppError.NotFound("attachment"))
                if (document.metadata.deletedAt != null) return@withTransaction AppResult.Success(Unit)

                database.documentDao().update(
                    document.copy(
                        metadata = document.metadata.copy(
                            deletedAt = now,
                            updatedAt = now,
                            version = document.metadata.version + 1,
                            syncStatus = SyncStatus.PENDING,
                        ),
                    ),
                )
                // A tombstone replaces any upload still waiting: there is nothing left to send.
                database.syncOutboxDao().deletePendingForEntity(SyncEntityType.DOCUMENT, id)
                enqueue(id, SyncEntityType.DOCUMENT, OutboxOperation.DELETE, now)

                if (document.ownerType == AttachmentOwnerType.FARM.name) {
                    val farm = database.farmDao().findById(document.ownerId)
                    if (farm != null && farm.coverDocumentId == id) {
                        database.farmDao().upsert(
                            farm.copy(
                                coverDocumentId = null,
                                metadata = farm.metadata.copy(
                                    updatedAt = now,
                                    version = farm.metadata.version + 1,
                                    syncStatus = SyncStatus.PENDING,
                                ),
                            ),
                        )
                        enqueue(farm.id, SyncEntityType.FARM, OutboxOperation.UPDATE, now)
                    }
                }
                released = document
                AppResult.Success(Unit)
            }
        }.getOrElse { error ->
            AppResult.Failure(AppError.Storage(operation = "remove_document", cause = error))
        }
        // Only after the tombstone is durable: a failed transaction keeps the file.
        if (result is AppResult.Success) released?.let { fileStore.delete(it.localUri, it.id) }
        result
    }

    override suspend fun recordUploadFailure(
        id: UUID,
        errorCode: String,
        errorMessage: String?,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        val now = clock.nowInstant()
        runCatching {
            database.withTransaction {
                val document = database.documentDao().findById(id)
                    ?: return@withTransaction AppResult.Failure(AppError.NotFound("attachment"))
                if (document.metadata.deletedAt != null) {
                    return@withTransaction AppResult.Failure(AppError.Conflict("attachment"))
                }
                // Upload bookkeeping only: the local URI, the file and the version stay as they are.
                database.documentDao().update(
                    document.copy(
                        uploadStatus = AttachmentUploadState.FAILED.name,
                        metadata = document.metadata.copy(
                            updatedAt = now,
                            syncStatus = SyncStatus.FAILED,
                        ),
                    ),
                )
                database.syncOutboxDao().recordUploadFailure(
                    entityType = SyncEntityType.DOCUMENT,
                    entityId = id,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                    nowEpochMillis = now.toEpochMilli(),
                )
                AppResult.Success(Unit)
            }
        }.getOrElse { error ->
            AppResult.Failure(AppError.Storage(operation = "record_upload_failure", cause = error))
        }
    }

    private suspend fun enqueue(
        entityId: UUID,
        entityType: SyncEntityType,
        operation: OutboxOperation,
        now: Instant,
    ) {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                id = idGenerator.newId(),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadVersion = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun resolveOwner(owner: AttachmentOwner): AppResult<UUID> {
        val (workspaceId, deletedAt) = when (owner.type) {
            AttachmentOwnerType.FARM -> database.farmDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.PARCEL -> database.parcelDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.CAMPAIGN -> database.campaignDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.ACTIVITY -> database.activityDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.EXPENSE -> database.expenseDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.HARVEST -> database.harvestDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
            AttachmentOwnerType.DOCUMENT -> database.documentOcrDao().findById(owner.id)
                ?.let { it.workspaceId to it.metadata.deletedAt }
        } ?: return AppResult.Failure(AppError.NotFound(owner.type.name.lowercase()))
        if (deletedAt != null) {
            return AppResult.Failure(AppError.Validation(field = "owner", code = "archived_owner"))
        }
        return AppResult.Success(workspaceId)
    }

    private fun copyIn(
        sourceUri: String,
        attachmentId: UUID,
        accepts: (String) -> Boolean,
    ): AppResult<StoredAttachmentFile> =
        runCatching { AppResult.Success(fileStore.importFile(sourceUri, attachmentId, accepts)) }
            .getOrElse { error -> AppResult.Failure(error.toCopyError()) }

    private fun DocumentEntity.toDomain(): Attachment? {
        val type = AttachmentOwnerType.entries.firstOrNull { it.name == ownerType } ?: return null
        return Attachment(
            id = id,
            owner = AttachmentOwner(type, ownerId),
            kind = AttachmentKind.fromMimeType(mimeType) ?: AttachmentKind.PHOTO,
            mimeType = mimeType,
            displayName = displayName,
            sizeBytes = fileSizeBytes,
            sha256 = sha256,
            localUri = localUri,
            thumbnailUri = fileStore.thumbnailUri(id),
            uploadState = AttachmentUploadState.fromStored(uploadStatus),
            isAvailableLocally = fileStore.exists(localUri),
            createdAt = metadata.createdAt,
        )
    }
}

internal fun StoredAttachmentFile.toEntity(
    attachmentId: UUID,
    workspaceId: UUID,
    owner: AttachmentOwner,
    kind: AttachmentKind,
    now: Instant,
) = DocumentEntity(
    id = attachmentId,
    workspaceId = workspaceId,
    ownerType = owner.type.name,
    ownerId = owner.id,
    type = kind.name,
    mimeType = mimeType,
    displayName = displayName,
    fileSizeBytes = sizeBytes,
    sha256 = sha256,
    localUri = localUri,
    uploadStatus = AttachmentUploadState.PENDING.name,
    metadata = LocalMetadata(
        createdAt = now,
        updatedAt = now,
        syncStatus = SyncStatus.PENDING,
    ),
)

internal fun Throwable.toCopyError(): AppError = when (this) {
    is RejectedAttachmentException -> AppError.Validation(field = "file", code = code, cause = this)
    is SecurityException -> AppError.Permission(operation = "read_attachment_source", cause = this)
    else -> AppError.Storage(operation = "copy_attachment", cause = this)
}
