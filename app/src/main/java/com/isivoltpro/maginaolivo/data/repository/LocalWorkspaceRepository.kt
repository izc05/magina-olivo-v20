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
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceChoice
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.WorkspaceSession
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalWorkspaceRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val regionalContext: RegionalContext,
    private val accountRepository: AccountRepository? = null,
    private val issuer: String = "",
    private val authorizationRemote: WorkspaceAuthorizationRemote? = null,
) : WorkspaceRepository {
    private val account: AccountRepository? = accountRepository
    private val lastRefreshAttemptNanos = java.util.concurrent.atomic.AtomicLong(0L)

    override fun currentOwnerId(): UUID? = account?.state?.value?.userId

    override fun observeLocalWorkspace(): Flow<UUID?> =
        if (account == null) {
            database.workspaceSelectionDao().observeAll().map { (activeWorkspace() as? AppResult.Success)?.value }
        } else {
            account.state
                .map { it.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (account.isConfigured && userId == null) {
                        flowOf(null)
                    } else {
                        observeActiveWorkspace().map { (it as? AppResult.Success)?.value }
                    }
                }
        }

    private suspend fun refreshIfDue() {
        if (account?.state?.value?.online != true || authorizationRemote == null) return
        val now = System.nanoTime()
        val last = lastRefreshAttemptNanos.get()
        if (last == 0L || now - last >= 60_000_000_000L) {
            if (lastRefreshAttemptNanos.compareAndSet(last, now)) refreshAuthorizations()
        }
    }

    override fun observeActiveWorkspace(): Flow<AppResult<UUID>> =
        if (account == null) database.workspaceSelectionDao().observeAll().map { activeWorkspace() }
        else combine(
            account.state,
            database.workspaceSelectionDao().observeAll(),
            database.workspaceAuthorizationCacheDao().observeAll(),
        ) { state, _, _ -> state.online }
            .map { online ->
                if (!online) lastRefreshAttemptNanos.set(0L)
                activeWorkspace()
            }

    override suspend fun refreshAuthorizations(): AppResult<Unit> = withContext(dispatchers.io) {
        lastRefreshAttemptNanos.set(System.nanoTime())
        safe("refresh_workspace_authorizations") {
            val owner = account ?: return@safe denied()
            val remote = authorizationRemote ?: return@safe denied()
            if (!owner.state.value.online) return@safe denied()
            owner.withServerVerifiedIdentity(
                remote = { session -> remote.list(session) },
                commit = { accountId, body ->
                    val contract = WorkspaceAuthorizationContract.parse(body)
                    if (!contract.validFor(issuer, accountId, clock.nowInstant())) return@withServerVerifiedIdentity denied<Unit>()
                    database.workspaceAuthorizationCacheDao().upsert(
                        WorkspaceAuthorizationCacheEntity(issuer, accountId.toString(), body),
                    )
                    AppResult.Success(Unit)
                },
            )
        }
    }

    override suspend fun activeWorkspace(): AppResult<UUID> = withContext(dispatchers.io) {
        safe("active_workspace") { resolve(false) }
    }

    override suspend fun ensureLocalWorkspace(): AppResult<UUID> = withContext(dispatchers.io) {
        safe("ensure_local_workspace") { resolve(true) }
    }

    override suspend fun availableWorkspaces(): AppResult<List<WorkspaceChoice>> = withContext(dispatchers.io) {
        safe("available_workspaces") {
            if (account?.state?.value?.online == true) refreshAuthorizations()
            AppResult.Success(when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
                is WorkspaceSession.Authenticated -> {
                    val auth = authorized(session.accountId)
                    if (auth.isNotEmpty()) {
                        auth.map { WorkspaceChoice(it.id, it.name, true) }
                    } else {
                        database.workspaceDao().findActiveByOwner(session.accountId)?.let {
                            listOf(WorkspaceChoice(it.id, it.name, false))
                        } ?: emptyList()
                    }
                }
                WorkspaceSession.SignedOut -> {
                    if (account?.isConfigured == true) emptyList()
                    else database.workspaceDao().listUnboundLocal().map { WorkspaceChoice(it.id, it.name, false) }
                }
                WorkspaceSession.Expired -> emptyList()
            })
        }
    }

    override suspend fun selectWorkspace(id: UUID): AppResult<Unit> = withContext(dispatchers.io) {
        safe("select_workspace") {
            if (account?.state?.value?.online == true) refreshAuthorizations()
            database.withTransaction {
                val scope = when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
                    is WorkspaceSession.Authenticated -> {
                        val auth = authorized(session.accountId)
                        val isOwned = database.workspaceDao().findActiveByOwner(session.accountId)?.id == id
                        if (auth.none { it.id == id } && !isOwned) return@withTransaction denied()
                        issuer to session.accountId.toString()
                    }
                    WorkspaceSession.SignedOut -> {
                        if (account?.isConfigured == true) return@withTransaction denied()
                        if (database.workspaceDao().listUnboundLocal().none { it.id == id }) return@withTransaction denied()
                        "" to ""
                    }
                    WorkspaceSession.Expired -> return@withTransaction denied()
                }
                database.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(scope.first, scope.second, id))
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun resolve(createLocal: Boolean): AppResult<UUID> {
        val currentUserId = account?.state?.value?.userId
        return when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
            is WorkspaceSession.Authenticated -> {
                val accountId = session.accountId
                val selected = database.workspaceSelectionDao().find(issuer, accountId.toString())?.workspaceId
                if (selected != null) {
                    refreshIfDue()
                    val auth = authorized(accountId)
                    val isOwned = database.workspaceDao().findActiveByOwner(accountId)?.id == selected
                    if (auth.any { it.id == selected } || isOwned) {
                        return AppResult.Success(selected)
                    }
                }

                // If not yet selected in workspaceSelectionDao, check existing owner workspace in Room
                database.withTransaction {
                    database.workspaceDao().findActiveByOwner(accountId)?.let { existing ->
                        database.workspaceSelectionDao().upsert(
                            WorkspaceSelectionEntity(issuer, accountId.toString(), existing.id),
                        )
                        return@withTransaction AppResult.Success(existing.id)
                    }

                    if (!createLocal) {
                        return@withTransaction AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
                    }

                    val workspaceId = idGenerator.newId()
                    val operationId = idGenerator.newId()
                    val now = clock.nowInstant()
                    database.workspaceDao().upsert(
                        WorkspaceEntity(
                            id = workspaceId,
                            name = "Mi olivar",
                            ownerUserId = accountId,
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
                    database.workspaceSelectionDao().upsert(
                        WorkspaceSelectionEntity(issuer, accountId.toString(), workspaceId),
                    )
                    AppResult.Success(workspaceId)
                }
            }
            WorkspaceSession.Expired -> denied()
            WorkspaceSession.SignedOut -> {
                if (account?.isConfigured == true) {
                    AppResult.Failure(AppError.Permission("No hay sesión activa"))
                } else if (currentUserId != null) {
                    // Simulated or fallback account with current userId
                    database.withTransaction {
                        database.workspaceDao().findActiveByOwner(currentUserId)?.let { existing ->
                            return@withTransaction AppResult.Success(existing.id)
                        }
                        if (!createLocal) return@withTransaction AppResult.Failure(AppError.NotFound("workspace"))
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
                        AppResult.Success(workspaceId)
                    }
                } else {
                    resolveLocal(createLocal)
                }
            }
        }
    }

    private suspend fun resolveLocal(createLocal: Boolean): AppResult<UUID> = database.withTransaction {
        if (account?.isConfigured == true) {
            return@withTransaction AppResult.Failure(AppError.Permission("No hay sesión activa"))
        }
        val local = database.workspaceDao().listUnboundLocal()
        val selected = database.workspaceSelectionDao().find("", "")?.workspaceId
        if (selected != null) {
            return@withTransaction local.find { it.id == selected }?.let { AppResult.Success(it.id) } ?: denied()
        }
        if (local.size == 1) return@withTransaction AppResult.Success(local.single().id)
        if (local.size > 1) return@withTransaction AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
        if (!createLocal) return@withTransaction AppResult.Failure(AppError.NotFound("workspace"))
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

    private suspend fun authorized(accountId: UUID): List<WorkspaceEntity> {
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, accountId.toString()) ?: return emptyList()
        val contract = WorkspaceAuthorizationContract.parse(cached.contractJson)
        val valid = if (account?.state?.value?.online == false) {
            contract.subject == accountId
        } else {
            contract.validFor(issuer, accountId, clock.nowInstant())
        }
        if (!valid) return emptyList()
        val available = if (account?.state?.value?.online == false) {
            contract.workspaces
        } else {
            contract.available(clock.nowInstant())
        }
        return available
            .map { it.id }
            .mapNotNull { id ->
                val workspace = database.workspaceDao().findById(id)
                if (workspace?.metadata?.deletedAt == null) workspace else null
            }
    }

    private fun <T> denied(): AppResult<T> = AppResult.Failure(AppError.Validation("workspaceId", "not_authorized"))

    private suspend fun <T> safe(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try { block() } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppResult.Failure(AppError.Storage(operation = operation, cause = error))
        }
}
