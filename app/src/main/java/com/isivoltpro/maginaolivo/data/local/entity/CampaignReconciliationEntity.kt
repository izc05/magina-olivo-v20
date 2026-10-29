package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Immutable compared revisions plus the explicit decision; never a remote receipt. */
@Entity(tableName = "campaign_reconciliations", indices = [Index(value = ["issuer", "account_id", "workspace_id"])])
data class CampaignReconciliationEntity(
    @PrimaryKey @ColumnInfo(name = "original_operation_id") val originalOperationId: UUID,
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,
    @ColumnInfo(name = "campaign_id") val campaignId: UUID,
    val reason: String,
    @ColumnInfo(name = "local_payload_json") val localPayloadJson: String,
    @ColumnInfo(name = "remote_payload_json") val remotePayloadJson: String,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    @ColumnInfo(name = "resolved_operation_id") val resolvedOperationId: UUID? = null,
)
