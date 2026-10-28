package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.util.UUID

/** Empty issuer/account form the local, unbound device scope. No owner is inferred. */
@Entity(tableName = "workspace_selections", primaryKeys = ["issuer", "account_id"])
data class WorkspaceSelectionEntity(
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
)
