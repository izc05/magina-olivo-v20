package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.farm.FarmCoverRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * The Farm cover is an ordinary Farm photo attachment that the Farm points at
 * (`DATA-MODEL-RC1.1-ADDENDUM` §1). Since Phase 11 the image is copied into app storage
 * first, so the cover no longer depends on the picked file or its permission grant. A
 * replaced cover stays in the Farm's attachments: the original remains available.
 */
class OfflineFirstFarmCoverRepository(
    private val database: MaginaOlivoDatabase,
    private val fileStore: AttachmentFileStore,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : FarmCoverRepository {
    override fun observeCoverUri(farmId: UUID): Flow<String?> =
        database.documentDao().observeFarmCoverUri(farmId).flowOn(dispatchers.io)

    override suspend fun attachCover(
        farmId: UUID,
        uri: String,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        val owner = AttachmentOwner(AttachmentOwnerType.FARM, farmId)
        val existing = database.farmDao().findById(farmId)
            ?: return@withContext AppResult.Failure(AppError.NotFound("farm"))
        if (existing.metadata.deletedAt != null) {
            return@withContext AppResult.Failure(AppError.Validation(field = "owner", code = "archived_owner"))
        }
        val documentId = idGenerator.newId()
        val stored = runCatching {
            fileStore.importFile(uri, documentId) { AttachmentKind.fromMimeType(it) == AttachmentKind.PHOTO }
        }.getOrElse { error ->
            return@withContext AppResult.Failure(
                if (error is RejectedAttachmentException) {
                    AppError.Validation(field = "cover", code = error.code, cause = error)
                } else {
                    error.toCopyError()
                },
            )
        }
        val now = clock.nowInstant()

        val result = runCatching {
            database.withTransaction {
                val farm = database.farmDao().findById(farmId)
                    ?: return@withTransaction AppResult.Failure(AppError.NotFound("farm"))
                database.documentDao().insert(
                    stored.toEntity(documentId, farm.workspaceId, owner, AttachmentKind.PHOTO, now),
                )
                database.farmDao().upsert(
                    farm.copy(
                        coverDocumentId = documentId,
                        metadata = farm.metadata.copy(
                            updatedAt = now,
                            version = farm.metadata.version + 1,
                            syncStatus = SyncStatus.PENDING,
                        ),
                    ),
                )
                enqueue(documentId, SyncEntityType.DOCUMENT, OutboxOperation.UPLOAD_ATTACHMENT, now)
                enqueue(farmId, SyncEntityType.FARM, OutboxOperation.UPDATE, now)
                AppResult.Success(Unit)
            }
        }.getOrElse { error ->
            AppResult.Failure(AppError.Storage(operation = "attach_farm_cover", cause = error))
        }
        if (result is AppResult.Failure) fileStore.delete(stored.localUri, documentId)
        result
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
}
