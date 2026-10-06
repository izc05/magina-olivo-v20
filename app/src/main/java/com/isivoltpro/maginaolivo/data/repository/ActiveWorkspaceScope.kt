package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID

/**
 * #615: one boundary for operations initiated from the currently active agricultural experience.
 *
 * Production injects [WorkspaceRepository]. The database fallback keeps low-level repository tests
 * and explicit local workers working without pretending another Workspace is the visual session.
 */
internal class ActiveWorkspaceScope(
    private val database: MaginaOlivoDatabase,
    private val workspaces: WorkspaceRepository?,
) {
    suspend fun resolve(): AppResult<UUID> {
        if (workspaces != null) return workspaces.ensureLocalWorkspace()
        val active = database.workspaceDao().findFirstActive()
            ?: return AppResult.Failure(AppError.NotFound("workspace"))
        return AppResult.Success(active.id)
    }

    suspend fun mismatch(workspaceId: UUID): AppResult.Failure? =
        when (val active = resolve()) {
            is AppResult.Failure -> active
            is AppResult.Success ->
                if (active.value == workspaceId) null
                else AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))
        }
}
