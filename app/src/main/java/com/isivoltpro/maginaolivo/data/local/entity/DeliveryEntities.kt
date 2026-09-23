package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * A Delivery to a cooperative or mill (`DATA-MODEL-RC1-FUTURE` §12). `net_grams` is the
 * delivered weight the farmer confirmed; OCR never writes here without that confirmation.
 */
@Entity(
    tableName = "deliveries",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "delivery_date"]),
        Index(value = ["campaign_id", "delivery_date"]),
    ],
)
data class DeliveryEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID,
    @ColumnInfo(name = "delivery_date") val deliveryDate: LocalDate,
    @ColumnInfo(name = "destination_organization_id") val destinationOrganizationId: UUID? = null,
    @ColumnInfo(name = "destination_name") val destinationName: String,
    @ColumnInfo(name = "net_grams") val netGrams: Long,
    @ColumnInfo(name = "gross_grams") val grossGrams: Long? = null,
    @ColumnInfo(name = "tare_grams") val tareGrams: Long? = null,
    @ColumnInfo(name = "delivery_number") val deliveryNumber: String? = null,
    @ColumnInfo(name = "ticket_number") val ticketNumber: String? = null,
    val source: String,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

/** One origin Parcel of a Delivery: a child of the Delivery aggregate, as for a Harvest. */
@Entity(
    tableName = "delivery_parcels",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
        ForeignKey(
            entity = DeliveryEntity::class,
            parentColumns = ["id"],
            childColumns = ["delivery_id"],
        ),
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcel_id"],
        ),
    ],
    indices = [
        Index(value = ["delivery_id", "parcel_id"], unique = true),
        Index(value = ["workspace_id"]),
        Index(value = ["parcel_id"]),
    ],
)
data class DeliveryParcelEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "delivery_id") val deliveryId: UUID,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "campaign_parcel_id") val campaignParcelId: UUID? = null,
    @ColumnInfo(name = "parcel_name_at_delivery") val parcelNameAtDelivery: String,
    @ColumnInfo(name = "weight_grams") val weightGrams: Long? = null,
    @ColumnInfo(name = "allocation_mode") val allocationMode: String,
    @Embedded val metadata: LocalMetadata,
)

/**
 * The later yield analysis of a Delivery (`DATA-MODEL-RC1.1-ADDENDUM` §11). Its own record
 * with its own version: recording or correcting it never changes the Delivery row.
 * Percentages are integer hundredths ("21,35 %" → 2135).
 */
@Entity(
    tableName = "delivery_yield_analyses",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
        ForeignKey(
            entity = DeliveryEntity::class,
            parentColumns = ["id"],
            childColumns = ["delivery_id"],
        ),
    ],
    indices = [
        Index(value = ["delivery_id"]),
        Index(value = ["workspace_id"]),
    ],
)
data class DeliveryYieldAnalysisEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "delivery_id") val deliveryId: UUID,
    @ColumnInfo(name = "analysis_date") val analysisDate: LocalDate? = null,
    @ColumnInfo(name = "fat_yield_hundredths") val fatYieldHundredths: Int? = null,
    @ColumnInfo(name = "industrial_yield_hundredths") val industrialYieldHundredths: Int? = null,
    @ColumnInfo(name = "source_attachment_id") val sourceAttachmentId: UUID? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)
