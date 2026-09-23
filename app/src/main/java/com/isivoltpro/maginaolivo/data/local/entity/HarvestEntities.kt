package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One origin Parcel of a Harvest (`DATA-MODEL-RC1-FUTURE` §11). A child of the Harvest
 * aggregate (`RC1-NORMATIVE-ADDENDUM` D6): replaced with its Harvest, never synchronized
 * alone. `weight_grams` is only present when `allocation_mode` is `EXACT`; an
 * `UNALLOCATED` row says the Parcel contributed an unknown share, never zero.
 */
@Entity(
    tableName = "harvest_parcels",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
        ForeignKey(
            entity = HarvestEntity::class,
            parentColumns = ["id"],
            childColumns = ["harvest_id"],
        ),
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcel_id"],
        ),
    ],
    indices = [
        Index(value = ["harvest_id", "parcel_id"], unique = true),
        Index(value = ["workspace_id"]),
        Index(value = ["parcel_id"]),
    ],
)
data class HarvestParcelEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "harvest_id") val harvestId: UUID,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "campaign_parcel_id") val campaignParcelId: UUID? = null,
    @ColumnInfo(name = "parcel_name_at_harvest") val parcelNameAtHarvest: String,
    @ColumnInfo(name = "weight_grams") val weightGrams: Long? = null,
    @ColumnInfo(name = "allocation_mode") val allocationMode: String,
    @Embedded val metadata: LocalMetadata,
)
