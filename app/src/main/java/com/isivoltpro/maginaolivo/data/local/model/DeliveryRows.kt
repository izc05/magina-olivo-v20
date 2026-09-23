package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryYieldAnalysisEntity

/** A Delivery with its origin Parcels and its analyses (tombstones included, filtered by the reader). */
data class DeliveryWithParcels(
    @Embedded val delivery: DeliveryEntity,
    @Relation(parentColumn = "id", entityColumn = "delivery_id")
    val parcels: List<DeliveryParcelEntity>,
    @Relation(parentColumn = "id", entityColumn = "delivery_id")
    val analyses: List<DeliveryYieldAnalysisEntity>,
)
