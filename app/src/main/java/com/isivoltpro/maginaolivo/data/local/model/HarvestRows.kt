package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import java.time.LocalDate
import java.util.UUID

/** A Harvest read together with its origin Parcels: one aggregate (D6). */
data class HarvestWithParcels(
    @Embedded val harvest: HarvestEntity,
    @Relation(parentColumn = "id", entityColumn = "harvest_id")
    val parcels: List<HarvestParcelEntity>,
)

/** A Farm's running Campaign, as the Harvest form offers it. */
data class RunningCampaignRow(
    val campaignId: UUID,
    val campaignName: String,
    val campaignStatus: CampaignStatus,
    val campaignStart: LocalDate,
    val farmId: UUID,
    val farmName: String,
)

/** One Parcel of a Campaign, under the name it carries today. */
data class CampaignParcelRow(
    val campaignParcelId: UUID,
    val parcelId: UUID,
    val name: String,
)
