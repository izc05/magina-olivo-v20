package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Phase 19D: a person the farmer names for recollection labour (an alias is enough). It is
 * reusable across Jornadas; it is not a payroll or HR record.
 */
@Entity(
    tableName = "workers",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class WorkerEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val name: String,
    @Embedded val metadata: LocalMetadata,
)

/**
 * Phase 19D: labour of one Jornada (Harvest). Either one named person (`worker_id`, quantity 1)
 * or a quick count ("5 jornales", no person). Money is never stored here: a labour cost is
 * an Expense, the only financial ledger.
 */
@Entity(
    tableName = "harvest_labour",
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
            entity = WorkerEntity::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
        ),
    ],
    indices = [
        Index(value = ["harvest_id"]),
        Index(value = ["worker_id"]),
        Index(value = ["workspace_id"]),
    ],
)
data class HarvestLabourEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "harvest_id") val harvestId: UUID,
    @ColumnInfo(name = "worker_id") val workerId: UUID? = null,
    /** The person's name when recorded, so the Jornada reads the same if they are renamed. */
    @ColumnInfo(name = "worker_name") val workerName: String? = null,
    val quantity: Int,
    /** FULL_DAY, HALF_DAY or HOURS. */
    val unit: String,
    /** Per person, only for HOURS. */
    val minutes: Int? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)
