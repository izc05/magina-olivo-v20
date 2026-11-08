package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity

data class FarmSummaryRow(
    @Embedded val farm: FarmEntity,
    @ColumnInfo(name = "parcel_count") val parcelCount: Long,
    /** Worked area only; null when no parcel has it. Never mixed with the cadastral figure. */
    @ColumnInfo(name = "managed_area_m2") val managedAreaM2: Double?,
    @ColumnInfo(name = "managed_parcels") val managedParcels: Long = 0,
    @ColumnInfo(name = "cadastral_area_m2") val cadastralAreaM2: Double? = null,
    @ColumnInfo(name = "cadastral_parcels") val cadastralParcels: Long = 0,
    @ColumnInfo(name = "active_campaign_name") val activeCampaignName: String?,
    /** Sum of the olive trees the farmer gave per parcel (CR-004); null when none was given. */
    @ColumnInfo(name = "olive_tree_count") val oliveTreeCount: Long? = null,
    /** Parcels that carry a tree count; fewer than [parcelCount] means the sum is partial. */
    @ColumnInfo(name = "counted_parcels") val countedParcels: Long = 0,
)
