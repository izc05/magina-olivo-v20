package com.isivoltpro.maginaolivo.domain.workspace

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class WorkspaceChoice(val id: UUID, val name: String, val remote: Boolean)

interface WorkspaceRepository {
    suspend fun ensureLocalWorkspace(): AppResult<UUID>
    /** Read-only lookup. A missing selection never creates a workspace or an outbox intent. */
    suspend fun activeWorkspace(): AppResult<UUID> = ensureLocalWorkspace()
    fun observeActiveWorkspace(): Flow<AppResult<UUID>> = flow { emit(activeWorkspace()) }
    fun observeLocalWorkspace(): Flow<UUID?> =
        observeActiveWorkspace().map { (it as? AppResult.Success)?.value }
    fun currentOwnerId(): UUID? = null
    suspend fun availableWorkspaces(): AppResult<List<WorkspaceChoice>> =
        AppResult.Success(emptyList())
    suspend fun selectWorkspace(id: UUID): AppResult<Unit> =
        AppResult.Failure(AppError.Validation("workspaceId", "unsupported"))
    suspend fun refreshAuthorizations(): AppResult<Unit> =
        AppResult.Failure(AppError.Validation("workspaceId", "unsupported"))
}
