package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "user_profiles",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["workspace_id"], unique = true)],
)
data class UserProfileEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "display_name") val displayName: String,
    val email: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "parcels",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "status"]),
        Index(value = ["workspace_id", "cadastral_reference"]),
    ],
)
data class ParcelEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "cadastral_reference") val cadastralReference: String? = null,
    @ColumnInfo(name = "cadastral_polygon") val cadastralPolygon: String? = null,
    @ColumnInfo(name = "cadastral_parcel") val cadastralParcel: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val source: String,
    @ColumnInfo(name = "geometry_geo_json") val geometryGeoJson: String? = null,
    @ColumnInfo(name = "cadastral_area_m2") val cadastralAreaM2: Double? = null,
    @ColumnInfo(name = "managed_area_m2") val managedAreaM2: Double? = null,
    val notes: String? = null,
    val status: RecordStatus = RecordStatus.ACTIVE,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "farm_parcel_memberships",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farm_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcel_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["farm_id", "parcel_id", "valid_from"], unique = true),
        Index(value = ["parcel_id", "valid_until"]),
        Index(value = ["workspace_id"]),
    ],
)
data class FarmParcelMembershipEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "valid_from") val validFrom: Instant,
    @ColumnInfo(name = "valid_until") val validUntil: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "campaigns",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farm_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "status"]),
        Index(value = ["farm_id", "start_date"]),
    ],
)
data class CampaignEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate? = null,
    val status: CampaignStatus,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "campaign_parcels",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaign_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcel_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farm_id_at_start"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["campaign_id", "parcel_id"], unique = true),
        Index(value = ["workspace_id"]),
        Index(value = ["parcel_id"]),
        Index(value = ["farm_id_at_start"]),
    ],
)
data class CampaignParcelSnapshotEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "farm_id_at_start") val farmIdAtStart: UUID,
    @ColumnInfo(name = "farm_name_at_start") val farmNameAtStart: String,
    @ColumnInfo(name = "parcel_name_at_start") val parcelNameAtStart: String,
    @ColumnInfo(name = "managed_area_m2_at_start") val managedAreaM2AtStart: Double? = null,
    @ColumnInfo(name = "cadastral_reference_at_start") val cadastralReferenceAtStart: String? = null,
    @ColumnInfo(name = "geometry_geo_json_snapshot") val geometryGeoJsonSnapshot: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "activity_date"]),
        Index(value = ["campaign_id", "activity_date"]),
        Index(value = ["farm_id", "activity_date"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID? = null,
    @ColumnInfo(name = "farm_id") val farmId: UUID? = null,
    @ColumnInfo(name = "activity_date") val activityDate: LocalDate,
    val type: String,
    val status: ActivityStatus,
    val description: String,
    val product: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    @ColumnInfo(name = "cost_minor") val costMinor: Long? = null,
    val currency: String? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "harvests",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "harvest_date"]),
        Index(value = ["campaign_id", "harvest_date"]),
        Index(value = ["farm_id", "harvest_date"]),
    ],
)
data class HarvestEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID? = null,
    @ColumnInfo(name = "farm_id") val farmId: UUID? = null,
    @ColumnInfo(name = "harvest_date") val harvestDate: LocalDate,
    @ColumnInfo(name = "weight_grams") val weightGrams: Long,
    val destination: String? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "expense_date"]),
        Index(value = ["campaign_id", "expense_date"]),
        Index(value = ["farm_id", "expense_date"]),
        Index(value = ["parcel_id", "expense_date"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID? = null,
    @ColumnInfo(name = "farm_id") val farmId: UUID? = null,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID? = null,
    @ColumnInfo(name = "expense_date") val expenseDate: LocalDate,
    val concept: String,
    val category: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    val provider: String? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "owner_type", "owner_id"]),
        Index(value = ["sha256"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: UUID,
    val type: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long? = null,
    val sha256: String? = null,
    @ColumnInfo(name = "local_uri") val localUri: String,
    @ColumnInfo(name = "remote_path") val remotePath: String? = null,
    @ColumnInfo(name = "upload_status") val uploadStatus: String,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "weather_cache",
    indices = [Index(value = ["workspace_id", "expires_at"])],
)
data class WeatherCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val source: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    @ColumnInfo(name = "expires_at") val expiresAt: Instant,
)

@Entity(
    tableName = "alerts",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "starts_at"]),
        Index(value = ["farm_id", "starts_at"]),
        Index(value = ["parcel_id", "starts_at"]),
    ],
)
data class AlertEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID? = null,
    @ColumnInfo(name = "parcel_id") val parcelId: UUID? = null,
    val kind: String,
    val severity: String,
    val title: String,
    val message: String,
    @ColumnInfo(name = "starts_at") val startsAt: Instant,
    @ColumnInfo(name = "ends_at") val endsAt: Instant? = null,
    @ColumnInfo(name = "acknowledged_at") val acknowledgedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)
