package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Typed agronomic detail tables.
 *
 * Each is one-to-one with its Activity, which is why `activity_id` is the primary key
 * rather than a separate identifier: an Activity can never carry two details of the same
 * type, and the database says so instead of the repository having to. They are aggregate
 * children of `activities` (`RC1-NORMATIVE-ADDENDUM` D5): they are written in the same
 * transaction as their header, share its version and queue no intent of their own.
 *
 * They carry the same `workspace_id` and metadata tail as every other table here, so the
 * sync layer never has to special-case them, and they cascade with their Activity.
 *
 * `product_id` is reserved and nullable with no foreign key: RC1 must be able to record a
 * fertilisation or a treatment with no Products module in existence (D8), so
 * `product_name` is the persisted historical text.
 */
@Entity(
    tableName = "pruning_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class PruningDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "pruning_type") val pruningType: String? = null,
    @ColumnInfo(name = "worker_count") val workerCount: Int? = null,
    val hours: Double? = null,
    @ColumnInfo(name = "residue_management") val residueManagement: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "fertilization_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class FertilizationDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "product_name") val productName: String? = null,
    @ColumnInfo(name = "product_id") val productId: UUID? = null,
    @ColumnInfo(name = "total_quantity") val totalQuantity: Double? = null,
    val unit: String? = null,
    @ColumnInfo(name = "dose_value") val doseValue: Double? = null,
    @ColumnInfo(name = "dose_unit") val doseUnit: String? = null,
    @ColumnInfo(name = "application_method") val applicationMethod: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "phytosanitary_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class PhytosanitaryDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "product_name") val productName: String? = null,
    @ColumnInfo(name = "product_id") val productId: UUID? = null,
    @ColumnInfo(name = "active_substance") val activeSubstance: String? = null,
    @ColumnInfo(name = "total_quantity") val totalQuantity: Double? = null,
    val unit: String? = null,
    @ColumnInfo(name = "dose_value") val doseValue: Double? = null,
    @ColumnInfo(name = "dose_unit") val doseUnit: String? = null,
    val reason: String? = null,
    @ColumnInfo(name = "equipment_text") val equipmentText: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "soil_work_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class SoilWorkDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "work_type") val workType: String? = null,
    val method: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "irrigation_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class IrrigationDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
    @ColumnInfo(name = "volume_m3") val volumeM3: Double? = null,
    @ColumnInfo(name = "sector_text") val sectorText: String? = null,
    @ColumnInfo(name = "system_text") val systemText: String? = null,
    @Embedded val metadata: LocalMetadata,
)

/**
 * Historical irrigation tariff snapshot (`DATA-MODEL-RC1.2-ADDENDUM` §5).
 *
 * A sibling of `irrigation_details`, one per Activity, kept as its own table because the
 * contract defines it as one. It stores an estimate, never an authoritative total: the
 * linked Expense remains the actual cost, and `linked_expense_id` stays null until the
 * Expense phase exists.
 */
@Entity(
    tableName = "irrigation_price_snapshots",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class IrrigationPriceSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "pricing_basis") val pricingBasis: IrrigationPricingBasis,
    @ColumnInfo(name = "unit_price_minor") val unitPriceMinor: Long? = null,
    val quantity: Double? = null,
    @ColumnInfo(name = "estimated_amount_minor") val estimatedAmountMinor: Long? = null,
    val currency: String = "EUR",
    @ColumnInfo(name = "price_date") val priceDate: LocalDate,
    @ColumnInfo(name = "linked_expense_id") val linkedExpenseId: UUID? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "maintenance_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class MaintenanceDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "maintenance_type") val maintenanceType: String? = null,
    @ColumnInfo(name = "asset_text") val assetText: String? = null,
    @Embedded val metadata: LocalMetadata,
)

/**
 * `location_geometry` is reserved for the map phase and is written as GeoJSON when that
 * phase arrives; nothing captures it yet, so it is created nullable and left alone.
 */
@Entity(
    tableName = "incident_details",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class IncidentDetailEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val category: String? = null,
    val severity: IncidentSeverity? = null,
    @ColumnInfo(name = "incident_status") val incidentStatus: IncidentState = IncidentState.OPEN,
    @ColumnInfo(name = "location_geometry") val locationGeometry: String? = null,
    @ColumnInfo(name = "action_taken") val actionTaken: String? = null,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)
