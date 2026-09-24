package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity

data class FarmSummaryRow(
    @Embedded val farm: FarmEntity,
    @ColumnInfo(name = "parcel_count") val parcelCount: Long,
    @ColumnInfo(name = "total_area_m2") val totalAreaM2: Double?,
    @ColumnInfo(name = "active_campaign_name") val activeCampaignName: String?,
    /** Sum of the olive trees the farmer gave per parcel (CR-004); null when none was given. */
    @ColumnInfo(name = "olive_tree_count") val oliveTreeCount: Long? = null,
    /** Parcels that carry a tree count; fewer than [parcelCount] means the sum is partial. */
    @ColumnInfo(name = "counted_parcels") val countedParcels: Long = 0,
)
