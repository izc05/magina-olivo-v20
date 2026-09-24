package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Phase 19E: equipment used on one Jornada ("2 vibradoras"). A type and a quantity are
 * enough; `machine_id` is set only when the farmer picks a registered Machine for its
 * history, and then the line is that one machine. No fake Machine assets are created.
 */
@Entity(
    tableName = "harvest_equipment",
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
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
        ),
    ],
    indices = [
        Index(value = ["harvest_id"]),
        Index(value = ["machine_id"]),
        Index(value = ["workspace_id"]),
    ],
)
data class HarvestEquipmentEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "harvest_id") val harvestId: UUID,
    /** TRACTOR, SHAKER, COMB, TRAILER, BLOWER or OTHER. */
    val type: String,
    /** The farmer's word for OTHER, or the Machine's name when one is chosen. */
    val label: String? = null,
    val quantity: Int,
    @ColumnInfo(name = "machine_id") val machineId: UUID? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)
