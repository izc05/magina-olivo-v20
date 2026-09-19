package com.isivoltpro.maginaolivo.domain.farm

import com.isivoltpro.maginaolivo.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

data class Farm(
    val id: UUID,
    val workspaceId: UUID,
    val name: String,
    val description: String?,
    val municipality: String?,
    val province: String?,
    val notes: String?,
    val archivedAt: Instant?,
    val version: Long,
)

data class NewFarm(
    val workspaceId: UUID,
    val name: String,
    val description: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val notes: String? = null,
)

interface FarmRepository {
    fun observeActive(workspaceId: UUID): Flow<List<Farm>>

    suspend fun create(command: NewFarm): AppResult<UUID>

    suspend fun archive(farmId: UUID): AppResult<Unit>
}
