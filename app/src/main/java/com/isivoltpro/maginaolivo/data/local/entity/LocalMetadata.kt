package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant

data class LocalMetadata(
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
    val version: Long = 1,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: Long? = null,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Instant? = null,
)
