package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalWorkspaceRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val regionalContext: RegionalContext,
    private val accountRepository: com.isivoltpro.maginaolivo.feature.account.AccountRepository? = null,
) : WorkspaceRepository {
    override fun currentOwnerId(): UUID? = accountRepository?.state?.value?.userId

    override fun observeLocalWorkspace(): Flow<UUID?> =
        accountRepository?.state
            ?.map { it.userId }
            ?.distinctUntilChanged()
            ?.map { (ensureLocalWorkspace() as? AppResult.Success)?.value }
            ?: flow { emit((ensureLocalWorkspace() as? AppResult.Success)?.value) }

    override suspend fun ensureLocalWorkspace() = withContext(dispatchers.io) {
        runCatching {
            database.withTransaction {
                val currentUserId = accountRepository?.state?.value?.userId
                if (currentUserId != null) {
                    database.workspaceDao().findActiveByOwner(currentUserId)?.let { existing ->
                        return@withTransaction AppResult.Success(existing.id)
                    }

                    val workspaceId = idGenerator.newId()
                    val operationId = idGenerator.newId()
                    val now = clock.nowInstant()
                    database.workspaceDao().upsert(
                        WorkspaceEntity(
                            id = workspaceId,
                            name = "Mi olivar",
                            ownerUserId = currentUserId,
                            countryCode = regionalContext.countryCode,
                            timezone = regionalContext.zoneId.id,
                            locale = regionalContext.locale.toLanguageTag(),
                            currency = regionalContext.currency.currencyCode,
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
                            entityType = SyncEntityType.WORKSPACE,
                            entityId = workspaceId,
                            operation = OutboxOperation.CREATE,
                            payloadVersion = 1,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    return@withTransaction AppResult.Success(workspaceId)
                }

                database.workspaceDao().findFirstActive()?.let { existing ->
                    return@withTransaction AppResult.Success(existing.id)
                }

                val workspaceId = idGenerator.newId()
                val ownerId = idGenerator.newId()
                val operationId = idGenerator.newId()
                val now = clock.nowInstant()
                database.workspaceDao().upsert(
                    WorkspaceEntity(
                        id = workspaceId,
                        name = "Mi olivar",
                        ownerUserId = ownerId,
                        countryCode = regionalContext.countryCode,
                        timezone = regionalContext.zoneId.id,
                        locale = regionalContext.locale.toLanguageTag(),
                        currency = regionalContext.currency.currencyCode,
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
                        entityType = SyncEntityType.WORKSPACE,
                        entityId = workspaceId,
                        operation = OutboxOperation.CREATE,
                        payloadVersion = 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                AppResult.Success(workspaceId)
            }
        }.getOrElse { error ->
            AppResult.Failure(
                AppError.Storage(operation = "ensure_local_workspace", cause = error),
            )
        }
    }
}
