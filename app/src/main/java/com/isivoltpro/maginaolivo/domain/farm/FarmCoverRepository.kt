package com.isivoltpro.maginaolivo.domain.farm

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface FarmCoverRepository {
    fun observeCoverUri(farmId: UUID): Flow<String?>

    suspend fun attachCover(
        farmId: UUID,
        uri: String,
    ): AppResult<Unit>
}
