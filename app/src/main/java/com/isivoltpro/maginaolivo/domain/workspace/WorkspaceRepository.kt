package com.isivoltpro.maginaolivo.domain.workspace

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface WorkspaceRepository {
    suspend fun ensureLocalWorkspace(): AppResult<UUID>
    fun observeLocalWorkspace(): Flow<UUID?> = flow {
        emit((ensureLocalWorkspace() as? AppResult.Success)?.value)
    }
    fun currentOwnerId(): UUID? = null
}
