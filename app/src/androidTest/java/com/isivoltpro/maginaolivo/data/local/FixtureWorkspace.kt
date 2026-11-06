package com.isivoltpro.maginaolivo.data.local

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Integration #796 + #808 (owner decision 1): production resolves the active workspace ONLY through an injected
 * [WorkspaceRepository]; `ActiveWorkspaceScope` is fail-closed (#615). Fixtures that seed exactly one live workspace
 * inject this explicit TEST seam instead of relying on a production fallback.
 *
 * It resolves that single workspace and fails closed (`selection_required`) when the database holds none or several,
 * exactly as an absent or ambiguous selection does in production. It never exists in production code.
 */
internal class FixtureWorkspace(private val database: () -> MaginaOlivoDatabase) : WorkspaceRepository {
    override suspend fun ensureLocalWorkspace(): AppResult<UUID> = activeWorkspace()

    override suspend fun activeWorkspace(): AppResult<UUID> {
        val live = database().openHelper.readableDatabase.query("SELECT id FROM workspaces WHERE deleted_at IS NULL").use { c ->
            buildList { while (c.moveToNext()) add(UUID.fromString(c.getString(0))) }
        }
        return live.singleOrNull()?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
    }

    override fun observeActiveWorkspace(): Flow<AppResult<UUID>> =
        database().workspaceDao().observeFirstActive().map { activeWorkspace() }
}
