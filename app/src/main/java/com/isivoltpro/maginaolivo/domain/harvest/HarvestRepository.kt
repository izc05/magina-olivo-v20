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

    /**
     * Opens the Farm's Jornada for [date] before any Pesada (ROADMAP 19B: a Jornada links zero,
     * one or many Pesadas). Over the Campaign's Parcels with an unknown split, it awaits its
     * Pesadas ([Harvest.awaitingPesadas]: «Kg pendientes de pesada», out of every kilo total) and
     * its kilos become the sum of the Pesadas linked to it. Nothing is estimated.
     */
    suspend fun openJornada(farmId: UUID, date: LocalDate): AppResult<UUID>

    /** Only while the Campaign is still running: a closed Campaign's history is not rewritten. */
    suspend fun update(id: UUID, draft: HarvestDraft): AppResult<Unit>

    suspend fun delete(id: UUID): AppResult<Unit>
}

/** #457 conflict code: the day still has Pesadas; they are moved or corrected before it can go. */
const val HARVEST_HAS_DELIVERIES = "harvest_has_deliveries"
