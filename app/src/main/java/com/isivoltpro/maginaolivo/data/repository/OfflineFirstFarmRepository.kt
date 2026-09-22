package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppPreconditions
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmSummaryRow
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class OfflineFirstFarmRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : FarmRepository {
    override fun observeActive(workspaceId: UUID): Flow<List<Farm>> =
        database
            .farmDao()
            .observeActive(workspaceId)
            .map { farms -> farms.map { farm -> farm.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> =
        database
            .farmDao()
            .observeArchived(workspaceId)
            .map { farms -> farms.map { farm -> farm.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeById(farmId: UUID): Flow<Farm?> =
        database
            .farmDao()
            .observeSummaryById(farmId)
            .map { farm -> farm?.toDomain() }
            .flowOn(dispatchers.io)

    override suspend fun create(command: NewFarm): AppResult<UUID> {
        val name = AppPreconditions.nonBlank(command.name, "name")
        if (name is AppResult.Failure) return name
        val normalizedName = (name as AppResult.Success).value

        return withContext(dispatchers.io) {
            val farmId = idGenerator.newId()
            val operationId = idGenerator.newId()
            val now = clock.nowInstant()

            runCatching {
                database.withTransaction {
                    database.farmDao().upsert(
                        FarmEntity(
                            id = farmId,
                            workspaceId = command.workspaceId,
                            name = normalizedName,
                            description = command.description?.trim()?.ifEmpty { null },
                            municipality = command.municipality?.trim()?.ifEmpty { null },
                            province = command.province?.trim()?.ifEmpty { null },
                            notes = command.notes?.trim()?.ifEmpty { null },
                            coverDocumentId = command.coverDocumentId,
                            metadata = LocalMetadata(
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        ),
                    )
                    database.syncOutboxDao().insert(
                        SyncOutboxEntity(
                            id = operationId,
                            entityType = SyncEntityType.FARM,
                            entityId = farmId,
                            operation = OutboxOperation.CREATE,
                            payloadVersion = OUTBOX_PAYLOAD_VERSION,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                AppResult.Success(farmId)
            }.getOrElse { error ->
                AppResult.Failure(AppError.Storage(operation = "create_farm", cause = error))
            }
        }
    }

    override suspend fun update(
        farmId: UUID,
        changes: FarmChanges,
    ): AppResult<Unit> {
        val name = AppPreconditions.nonBlank(changes.name, "name")
        if (name is AppResult.Failure) return name
        val normalizedName = (name as AppResult.Success).value

        return withContext(dispatchers.io) {
            val now = clock.nowInstant()
            runCatching {
                database.withTransaction {
                    val current = database.farmDao().findById(farmId)
                        ?: return@withTransaction AppResult.Failure(
                            AppError.NotFound(resource = "farm"),
                        )
                    if (current.metadata.deletedAt != null) {
                        return@withTransaction AppResult.Failure(
                            AppError.Conflict(resource = "archived_farm"),
                        )
                    }

                    database.farmDao().upsert(
                        current.copy(
                            name = normalizedName,
                            description = changes.description.normalizedOrNull(),
                            municipality = changes.municipality.normalizedOrNull(),
                            province = changes.province.normalizedOrNull(),
                            notes = changes.notes.normalizedOrNull(),
                            coverDocumentId = changes.coverDocumentId,
                            metadata = current.metadata.copy(
                                updatedAt = now,
                                version = current.metadata.version + 1,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        ),
                    )
                    enqueue(
                        entityId = farmId,
                        operation = OutboxOperation.UPDATE,
                        baseRemoteVersion = current.metadata.remoteVersion,
                        now = now,
                    )
                    AppResult.Success(Unit)
                }
            }.getOrElse { error ->
                AppResult.Failure(AppError.Storage(operation = "update_farm", cause = error))
            }
        }
    }

    override suspend fun archive(farmId: UUID): AppResult<Unit> =
        withContext(dispatchers.io) {
            val now = clock.nowInstant()
            runCatching {
                database.withTransaction {
                    val current = database.farmDao().findById(farmId)
                        ?: return@withTransaction AppResult.Failure(
                            AppError.NotFound(resource = "farm"),
                        )

                    if (current.metadata.deletedAt != null) {
                        return@withTransaction AppResult.Success(Unit)
                    }

                    database.farmDao().upsert(
                        current.copy(
                            status = FarmStatus.ARCHIVED,
                            metadata = current.metadata.copy(
                                updatedAt = now,
                                deletedAt = now,
                                version = current.metadata.version + 1,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        ),
                    )
                    enqueue(
                        entityId = farmId,
                        operation = OutboxOperation.DELETE,
                        baseRemoteVersion = current.metadata.remoteVersion,
                        now = now,
                    )
                    AppResult.Success(Unit)
                }
            }.getOrElse { error ->
                AppResult.Failure(AppError.Storage(operation = "archive_farm", cause = error))
            }
        }

    override suspend fun restore(farmId: UUID): AppResult<Unit> =
        withContext(dispatchers.io) {
            val now = clock.nowInstant()
            runCatching {
                database.withTransaction {
                    val current = database.farmDao().findById(farmId)
                        ?: return@withTransaction AppResult.Failure(
                            AppError.NotFound(resource = "farm"),
                        )
                    if (current.metadata.deletedAt == null && current.status == FarmStatus.ACTIVE) {
                        return@withTransaction AppResult.Success(Unit)
                    }

                    database.farmDao().upsert(
                        current.copy(
                            status = FarmStatus.ACTIVE,
                            metadata = current.metadata.copy(
                                updatedAt = now,
                                deletedAt = null,
                                version = current.metadata.version + 1,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        ),
                    )
                    enqueue(
                        entityId = farmId,
                        operation = OutboxOperation.UPDATE,
                        baseRemoteVersion = current.metadata.remoteVersion,
                        now = now,
                    )
                    AppResult.Success(Unit)
                }
            }.getOrElse { error ->
                AppResult.Failure(AppError.Storage(operation = "restore_farm", cause = error))
            }
        }

    private suspend fun enqueue(
        entityId: UUID,
        operation: OutboxOperation,
        baseRemoteVersion: Long?,
        now: java.time.Instant,
    ) {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                id = idGenerator.newId(),
                entityType = SyncEntityType.FARM,
                entityId = entityId,
                operation = operation,
                payloadVersion = OUTBOX_PAYLOAD_VERSION,
                baseRemoteVersion = baseRemoteVersion,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.ifEmpty { null }

    private fun FarmSummaryRow.toDomain(): Farm =
        Farm(
            id = farm.id,
            workspaceId = farm.workspaceId,
            name = farm.name,
            description = farm.description,
            municipality = farm.municipality,
            province = farm.province,
            notes = farm.notes,
            coverDocumentId = farm.coverDocumentId,
            parcelCount = parcelCount,
            totalAreaM2 = totalAreaM2,
            activeCampaignName = activeCampaignName,
            archivedAt = farm.metadata.deletedAt,
            version = farm.metadata.version,
        )

    private companion object {
        const val OUTBOX_PAYLOAD_VERSION = 1
    }
}
