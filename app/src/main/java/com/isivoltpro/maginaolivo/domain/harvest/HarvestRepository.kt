package com.isivoltpro.maginaolivo.domain.harvest

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class HarvestParcelOption(val parcelId: UUID, val name: String)

/** A Farm whose running Campaign can receive a Harvest, with that Campaign's Parcels. */
data class HarvestContext(
    val farmId: UUID,
    val farmName: String,
    val campaignId: UUID,
    val campaignName: String,
    val campaignStatus: CampaignStatus,
    val campaignStart: LocalDate,
    val parcels: List<HarvestParcelOption>,
)

interface HarvestRepository {
    fun observeAll(): Flow<List<Harvest>>

    fun observeForCampaign(campaignId: UUID): Flow<List<Harvest>>

    fun observe(id: UUID): Flow<Harvest?>

    /** Farms with an ACTIVE or HARVEST Campaign: the only places a Harvest can be recorded. */
    fun observeContexts(): Flow<List<HarvestContext>>

    /** Recorded against the Farm's running Campaign; its Parcels must be that Campaign's. */
    suspend fun create(draft: HarvestDraft): AppResult<UUID>

    /** Only while the Campaign is still running: a closed Campaign's history is not rewritten. */
    suspend fun update(id: UUID, draft: HarvestDraft): AppResult<Unit>

    suspend fun delete(id: UUID): AppResult<Unit>
}
