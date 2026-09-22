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
import com.isivoltpro.maginaolivo.domain.farm.FarmCoverRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class OfflineFirstFarmCoverRepository(
    private val database: MaginaOlivoDatabase,
    private val documentSource: PersistedDocumentSource,
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
        val persisted = runCatching { documentSource.retain(uri) }
            .getOrElse { error ->
                return@withContext AppResult.Failure(
                    if (error is IllegalArgumentException) {
                        AppError.Validation(field = "cover", code = "invalid_image_uri", cause = error)
                    } else {
                        AppError.Permission(operation = "retain_document_uri", cause = error)
                    },
                )
            }
        val now = clock.nowInstant()

        runCatching {
            database.withTransaction {
                val farm = database.farmDao().findById(farmId)
                    ?: return@withTransaction AppResult.Failure(AppError.NotFound("farm"))
                val documentId = idGenerator.newId()
                database.documentDao().insert(
                    DocumentEntity(
                        id = documentId,
                        workspaceId = farm.workspaceId,
                        ownerType = "FARM",
                        ownerId = farmId,
                        type = "COVER",
                        mimeType = persisted.mimeType,
                        displayName = persisted.displayName,
                        fileSizeBytes = persisted.sizeBytes,
                        localUri = persisted.uri,
                        uploadStatus = "PENDING",
                        metadata = LocalMetadata(
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING,
                        ),
                    ),
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
