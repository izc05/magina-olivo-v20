package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "labour_payments",
    foreignKeys = [
        ForeignKey(entity = WorkspaceEntity::class, parentColumns = ["id"], childColumns = ["workspace_id"]),
        ForeignKey(entity = WorkerEntity::class, parentColumns = ["id"], childColumns = ["worker_id"]),
        ForeignKey(entity = CampaignEntity::class, parentColumns = ["id"], childColumns = ["campaign_id"]),
    ],
    indices = [Index("workspace_id"), Index("worker_id"), Index(value = ["campaign_id", "payment_date"])],
)
data class LabourPaymentEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "worker_id") val workerId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID,
    @ColumnInfo(name = "payment_date") val paymentDate: LocalDate,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    val note: String?,
    @Embedded val metadata: LocalMetadata,
)
