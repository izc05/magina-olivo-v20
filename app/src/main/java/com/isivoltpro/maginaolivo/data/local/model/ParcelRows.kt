package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import java.util.UUID

data class ParcelRow(
    @Embedded val parcel: ParcelEntity,
    @ColumnInfo(name = "current_farm_id") val currentFarmId: UUID?,
)
