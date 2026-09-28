package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * CR-010 (A3, A4 — Room v18): a Farm's usual recollection prices, all optional. Only prices:
 * money is posted once, to the Expense ledger, with the price used kept on that entry.
 */
@Entity(
    tableName = "recollection_rates",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["workspace_id"]),
        Index(value = ["farm_id"], unique = true),
    ],
)
data class RecollectionRatesEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,
    val currency: String,
    @ColumnInfo(name = "full_day_minor") val fullDayMinor: Long? = null,
    @ColumnInfo(name = "hourly_minor") val hourlyMinor: Long? = null,
    /** `{"TRACTOR":6000,"SHAKER":3500}`: day price per equipment type; absent means unknown. */
    @ColumnInfo(name = "equipment_day_json") val equipmentDayJson: String,
    @Embedded val metadata: LocalMetadata,
)
