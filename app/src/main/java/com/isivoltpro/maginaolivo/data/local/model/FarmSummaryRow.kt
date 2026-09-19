package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity

data class FarmSummaryRow(
    @Embedded val farm: FarmEntity,
    @ColumnInfo(name = "parcel_count") val parcelCount: Long,
    @ColumnInfo(name = "total_area_m2") val totalAreaM2: Double?,
    @ColumnInfo(name = "active_campaign_name") val activeCampaignName: String?,
)
