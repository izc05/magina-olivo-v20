package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A machine (`DATA-MODEL-RC1.1-ADDENDUM` §5): its own aggregate, archived rather than deleted. */
@Entity(
    tableName = "machines",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [Index(value = ["workspace_id", "status"])],
)
data class MachineEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val name: String,
    val category: String,
    val make: String? = null,
    val model: String? = null,
    @ColumnInfo(name = "registration_or_serial") val registrationOrSerial: String? = null,
    @ColumnInfo(name = "current_hours") val currentHours: Double? = null,
    val notes: String? = null,
    val status: String,
    @Embedded val metadata: LocalMetadata,
)

/**
 * A machine used by an Activity. A child of the Activity aggregate (D5): replaced with the
 * Activity in its transaction and synchronized with its one intent, never alone.
 */
@Entity(
    tableName = "activity_machines",
    primaryKeys = ["activity_id", "machine_id"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
        ),
    ],
    indices = [Index(value = ["machine_id"])],
)
data class ActivityMachineEntity(
    @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "machine_id") val machineId: UUID,
    @ColumnInfo(name = "start_hours") val startHours: Double? = null,
    @ColumnInfo(name = "end_hours") val endHours: Double? = null,
    @ColumnInfo(name = "usage_hours") val usageHours: Double? = null,
)
