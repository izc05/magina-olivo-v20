package com.isivoltpro.maginaolivo.domain.workspace

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID

interface WorkspaceRepository {
    suspend fun ensureLocalWorkspace(): AppResult<UUID>
}
