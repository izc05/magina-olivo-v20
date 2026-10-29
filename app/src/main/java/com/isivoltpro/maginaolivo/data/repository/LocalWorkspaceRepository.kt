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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalWorkspaceRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val regionalContext: RegionalContext,
    private val account: AccountRepository? = null,
    private val issuer: String = "",
    private val authorizationRemote: WorkspaceAuthorizationRemote? = null,
) : WorkspaceRepository {
    private val lastRefreshAttemptNanos = java.util.concurrent.atomic.AtomicLong(0L)

    private suspend fun refreshIfDue() {
        if (account?.state?.value?.online != true || authorizationRemote == null) return
        val now = System.nanoTime()
        val last = lastRefreshAttemptNanos.get()
        if (last == 0L || now - last >= 60_000_000_000L) {
            if (lastRefreshAttemptNanos.compareAndSet(last, now)) refreshAuthorizations()
        }
    }

    override fun observeActiveWorkspace(): Flow<AppResult<java.util.UUID>> =
        if (account == null) database.workspaceSelectionDao().observeAll().map { activeWorkspace() }
        else combine(account.state, database.workspaceSelectionDao().observeAll(),
            database.workspaceAuthorizationCacheDao().observeAll()) { _, _, _ -> Unit }
            .map { activeWorkspace() }

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
    override suspend fun activeWorkspace(): AppResult<java.util.UUID> = withContext(dispatchers.io) {
        safe("active_workspace") { resolve(false) }
    }

    override suspend fun ensureLocalWorkspace(): AppResult<java.util.UUID> = withContext(dispatchers.io) {
        safe("ensure_local_workspace") { resolve(true) }
    }

    override suspend fun availableWorkspaces(): AppResult<List<WorkspaceChoice>> = withContext(dispatchers.io) {
        safe("available_workspaces") {
            if (account?.state?.value?.online == true) refreshAuthorizations()
            AppResult.Success(when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
                is WorkspaceSession.Authenticated -> authorized(session.accountId)
                    .map { WorkspaceChoice(it.id, it.name, true) }
                WorkspaceSession.SignedOut -> database.workspaceDao().listUnboundLocal()
                    .map { WorkspaceChoice(it.id, it.name, false) }
                WorkspaceSession.Expired -> emptyList()
            })
        }
    }

    override suspend fun selectWorkspace(id: java.util.UUID): AppResult<Unit> = withContext(dispatchers.io) {
        safe("select_workspace") {
            if (account?.state?.value?.online == true) refreshAuthorizations()
            database.withTransaction {
                val scope = when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
                    is WorkspaceSession.Authenticated -> {
                        if (authorized(session.accountId).none { it.id == id }) return@withTransaction denied()
                        issuer to session.accountId.toString()
                    }
                    WorkspaceSession.SignedOut -> {
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

    private suspend fun resolve(createLocal: Boolean): AppResult<java.util.UUID> {
        return when (val session = account?.workspaceSession() ?: WorkspaceSession.SignedOut) {
            is WorkspaceSession.Authenticated -> {
                if (issuer.isBlank()) return denied()
                val selected = database.workspaceSelectionDao().find(issuer, session.accountId.toString())?.workspaceId
                    ?: return AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
                refreshIfDue()
                if (authorized(session.accountId).none { it.id == selected }) denied()
                else AppResult.Success(selected)
            }
            WorkspaceSession.Expired -> denied()
            WorkspaceSession.SignedOut -> resolveLocal(createLocal)
        }
    }

    private suspend fun resolveLocal(createLocal: Boolean): AppResult<java.util.UUID> = database.withTransaction {
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

    private suspend fun authorized(accountId: java.util.UUID): List<WorkspaceEntity> {
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, accountId.toString()) ?: return emptyList()
        val contract = WorkspaceAuthorizationContract.parse(cached.contractJson)
        if (!contract.validFor(issuer, accountId, clock.nowInstant())) return emptyList()
        return contract.available(clock.nowInstant())
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
