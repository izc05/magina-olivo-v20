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
    val coverDocumentId: UUID?,
    val parcelCount: Long,
    val totalAreaM2: Double?,
    val activeCampaignName: String?,
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
    val coverDocumentId: UUID? = null,
)

data class FarmChanges(
    val name: String,
    val description: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val notes: String? = null,
    val coverDocumentId: UUID? = null,
)

interface FarmRepository {
    fun observeActive(workspaceId: UUID): Flow<List<Farm>>

    fun observeArchived(workspaceId: UUID): Flow<List<Farm>>

    fun observeById(farmId: UUID): Flow<Farm?>

    suspend fun create(command: NewFarm): AppResult<UUID>

    suspend fun update(
        farmId: UUID,
        changes: FarmChanges,
    ): AppResult<Unit>

    suspend fun archive(farmId: UUID): AppResult<Unit>

    suspend fun restore(farmId: UUID): AppResult<Unit>
}
