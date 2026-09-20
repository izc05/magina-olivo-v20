package com.isivoltpro.maginaolivo.domain.campaign

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class CampaignParcelSnapshot(
    val parcelId: UUID,
    val farmName: String,
    val parcelName: String,
    val managedAreaM2: Double?,
    val cadastralReference: String?,
    val geometryGeoJson: String?,
)

data class Campaign(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: CampaignStatus,
    val notes: String?,
    val snapshots: List<CampaignParcelSnapshot>,
    val version: Long,
)

data class NewCampaign(
    val farmId: UUID,
    val name: String,
    val startDate: LocalDate,
    val parcelIds: Set<UUID> = emptySet(),
    val notes: String? = null,
)

data class CampaignPreparationChanges(
    val name: String,
    val startDate: LocalDate,
    val parcelIds: Set<UUID>,
    val notes: String? = null,
)

interface CampaignRepository {
    fun observeForFarm(farmId: UUID): Flow<List<Campaign>>
    fun observe(id: UUID): Flow<Campaign?>
    suspend fun create(command: NewCampaign): AppResult<UUID>
    suspend fun updatePreparation(id: UUID, changes: CampaignPreparationChanges): AppResult<Unit>
    suspend fun activate(id: UUID): AppResult<Unit>
    suspend fun markHarvest(id: UUID): AppResult<Unit>
    suspend fun close(id: UUID, endDate: LocalDate): AppResult<Unit>
    suspend fun reopen(id: UUID): AppResult<Unit>
    suspend fun archivePreparation(id: UUID): AppResult<Unit>
}
