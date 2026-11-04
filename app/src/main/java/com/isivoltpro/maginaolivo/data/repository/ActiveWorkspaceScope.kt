package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * #615: one boundary for operations initiated from the currently active agricultural experience.
 *
 * Production injects [WorkspaceRepository]. The low-level fallback is deliberately unavailable:
 * a global first-row query cannot prove which identity owns an agricultural workspace.
 * Integration #796 + #808 (owner decision 1): fail-closed, without a [WorkspaceRepository] nothing resolves
 * (selection_required) and nothing is observed (null). Tests inject their own workspace explicitly.
 */
internal class ActiveWorkspaceScope(
    private val database: MaginaOlivoDatabase,
    private val workspaces: WorkspaceRepository?,
) {
    suspend fun resolve(): AppResult<UUID> {
        return workspaces?.activeWorkspace()
            ?: AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
    }

    fun observe(): Flow<UUID?> = workspaces?.observeLocalWorkspace() ?: flowOf(null)

    suspend fun mismatch(workspaceId: UUID): AppResult.Failure? =
        when (val active = resolve()) {
            is AppResult.Failure -> active
            is AppResult.Success ->
                if (active.value == workspaceId) null
                else AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))
        }
}
