package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Server response only. A G2.5 receipt never populates this cache. */
@Entity(tableName = "workspace_authorization_cache", primaryKeys = ["issuer", "account_id"])
data class WorkspaceAuthorizationCacheEntity(
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "contract_json") val contractJson: String,
)
